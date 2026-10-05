package dev.EfraGroup.formulaRacing.WolfMod;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.EfraGroup.formulaRacing.AI.AIRacingLine;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Ghost.GhostFrame;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.TimeTrial.Timing.WolfTimingService;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class WolfMOD implements PluginMessageListener {

    private final FormulaRacing plugin;
    private WolfTimingService timingService;

    /** Partially reassembled client uploads, keyed by player. */
    private final java.util.Map<UUID, PendingUpload> pendingUploads = new java.util.concurrent.ConcurrentHashMap<>();

    public static final String GHOST_DATA_CHANNEL = "wolfnetwork:ghost_data";
    public static final String GHOST_UPLOAD_CHANNEL = "wolfnetwork:ghost_upload";
    public static final String CONFIG_CHANNEL = "wolfnetwork:settings";

    /** Frames per chunk the client is allowed to claim, mirroring its own chunk size. */
    private static final int GHOST_UPLOAD_MAX_CHUNK_FRAMES = 2048;

    /** Cadence used by {@code GhostManager} when recording frames. */
    private static final int GHOST_SAMPLE_INTERVAL_TICKS = 2;

    /**
     * Cap on frames in a personal best payload. Each frame costs 25 bytes (4 floats +
     * varint), so 2000 frames is roughly 50 KB — close to the practical plugin message
     * ceiling. Longer laps get thinned rather than dropped.
     */
    private static final int MAX_GHOST_FRAMES = 2000;

    public WolfMOD(FormulaRacing plugin) {
        this.plugin = plugin;
        registerChannels();
    }

    public void setTimingService(WolfTimingService timingService) {
        this.timingService = timingService;
    }

    public boolean hasChannel(Player player) {
        return player != null && player.isOnline();
    }

    private void registerChannels() {
        this.plugin.getServer().getMessenger().registerOutgoingPluginChannel(this.plugin, GHOST_DATA_CHANNEL);
        this.plugin.getServer().getMessenger().registerOutgoingPluginChannel(this.plugin, CONFIG_CHANNEL);
        this.plugin.getServer().getMessenger().registerIncomingPluginChannel(this.plugin, CONFIG_CHANNEL, this);
        // Client -> server lap uploads arrive on their own binary channel, chunked.
        this.plugin.getServer().getMessenger().registerIncomingPluginChannel(this.plugin, GHOST_UPLOAD_CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (GHOST_UPLOAD_CHANNEL.equals(channel)) {
            this.handleGhostUploadChunk(player, message);
            return;
        }
        if (!CONFIG_CHANNEL.equals(channel) || this.timingService == null || message == null) {
            return;
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(message));
            String key = readString(input);
            String value = readString(input);
            if (key != null && value != null) {
                this.timingService.handleIncoming(player, key, value);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /**
     * Reassembles a chunked client lap upload.
     *
     * <p>Chunks are buffered per player and flushed to the timing service once the final chunk
     * arrives. Frames arrive in order over a reliable channel, but the index is still checked so
     * a malformed or duplicated chunk cannot silently corrupt a trajectory.
     */
    private void handleGhostUploadChunk(Player player, byte[] message) {
        if (this.timingService == null || player == null || message == null) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            input.readByte();
            String trackName = readString(input);
            int chunkIndex = readVarInt(input);
            int chunkCount = readVarInt(input);
            int frameCount = readVarInt(input);
            if (trackName == null || chunkCount <= 0 || chunkIndex < 0 || chunkIndex >= chunkCount || frameCount < 0) {
                return;
            }
            if (frameCount > GHOST_UPLOAD_MAX_CHUNK_FRAMES) {
                return;
            }
            UUID uuid = player.getUniqueId();
            PendingUpload pending = this.pendingUploads.computeIfAbsent(uuid, ignored -> new PendingUpload());
            if (chunkIndex == 0) {
                pending.trackName = trackName;
                pending.chunkCount = chunkCount;
                pending.frames.clear();
            }
            // Any gap or repeat means the stream is out of order; drop it rather than splice
            // discontinuous frames into a trajectory that would replay wrongly.
            if (chunkIndex != pending.nextIndex || chunkCount != pending.chunkCount) {
                this.pendingUploads.remove(uuid);
                return;
            }
            for (int i = 0; i < frameCount; i++) {
                long tick = readVarLong(input);
                pending.frames.add(new double[]{
                        tick,
                        Float.intBitsToFloat(input.readInt()),
                        Float.intBitsToFloat(input.readInt()),
                        Float.intBitsToFloat(input.readInt()),
                        Float.intBitsToFloat(input.readInt())
                });
            }
            pending.nextIndex++;
            if (pending.nextIndex < chunkCount) {
                return;
            }
            List<double[]> frames = pending.frames;
            this.pendingUploads.remove(uuid);
            this.timingService.handleGhostUploadFrames(player, pending.trackName, frames);
        } catch (IOException | RuntimeException ignored) {
            this.pendingUploads.remove(player.getUniqueId());
        }
    }

    /** Frames buffered between the first and last chunk of a client upload. */
    private static final class PendingUpload {
        private String trackName;
        private int chunkCount;
        private int nextIndex;
        private final List<double[]> frames = new ArrayList<>();
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        return Integer.parseInt(readVarLong(input) + "");
    }

    private static long readVarLong(DataInputStream input) throws IOException {
        int value = 0;
        int shift = 0;
        for (int i = 0; i < 5; i++) {
            int current = input.readUnsignedByte();
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
        throw new IOException("VarInt too long");
    }

    /**
     * Sends the complete track racing line (AI ideal line) to the client as a
     * GhostDataPayload via the wolfnetwork:ghost_data channel.
     * The client renders it as a single ghost boat+player following the line.
     *
     * <p>Frames are indexed at a fixed 1-tick cadence and {@code lapTimeMs} is 0, so the
     * client drives playback from its own Timer rather than from real timestamps.
     */
    public void sendTrackLine(Player player, String trackName, AIRacingLine line) {
        if (player == null || !player.isOnline()) return;
        if (line == null || !line.isUsable()) return;

        List<Location> idealLine = line.getIdealLine();
        int frameCount = idealLine.size();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        // Version
        out.writeByte(1);
        // Track ID (VarInt-prefixed UTF-8 string, matching PacketByteBuf.readString)
        writeString(out, trackName);
        // Lap time (0 for ideal line — timing driven by client Timer)
        out.writeLong(0L);
        // Sample interval and frame count
        writeVarInt(out, 1); // 1 tick between frames
        writeVarInt(out, frameCount);

        // Encode each frame: tick, x, y, z, yaw, pitch
        for (int i = 0; i < frameCount; i++) {
            Location loc = idealLine.get(i);
            writeVarInt(out, i); // tick = frame index
            out.writeFloat((float) loc.getX());
            out.writeFloat((float) loc.getY());
            out.writeFloat((float) loc.getZ());

            // Compute yaw/pitch from direction to next point
            float yaw = 0f;
            float pitch = 0f;
            if (i + 1 < frameCount) {
                Location next = idealLine.get(i + 1);
                if (loc.getWorld() != null && loc.getWorld().equals(next.getWorld())) {
                    double dx = next.getX() - loc.getX();
                    double dz = next.getZ() - loc.getZ();
                    yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                    if (horizontalDist > 0) {
                        pitch = (float) Math.toDegrees(Math.atan2(-(next.getY() - loc.getY()), horizontalDist));
                    }
                }
            }
            out.writeFloat(yaw);
            out.writeFloat(pitch);
        }

        player.sendPluginMessage(plugin, GHOST_DATA_CHANNEL, out.toByteArray());
        plugin.getDebugManager().logTimeTrialSystem(
            "[WolfMOD] Sent track line '" + trackName + "' to " + player.getName()
                + " (" + frameCount + " frames)");
    }

    /**
     * Sends the player's own personal best lap to the client so the mod can render it
     * as a translucent boat + rider racing alongside them.
     *
     * <p>Unlike {@link #sendTrackLine}, {@code ticks[]} carries the real elapsed tick of
     * each frame. Ghosts are recorded every 2 ticks, so a client that assumed a 1-tick
     * cadence would replay the lap at double speed; the client indexes playback by
     * {@code ticks[]} against its own Timer, so real stamps are what keep the ghost in
     * step with the live run.
     *
     * <p>Long laps would exceed the plugin message size limit, so frames are thinned
     * evenly down to {@link #MAX_GHOST_FRAMES}, always keeping the first and last frame
     * so the ghost still starts on the line and still reaches the finish.
     *
     * @param frames recorded PB frames, oldest first; may be empty
     * @param lapMillis the PB lap time, or 0 when unknown
     */
    public void sendPersonalBest(Player player, String trackName, List<GhostFrame> frames, long lapMillis) {
        if (player == null || !player.isOnline() || trackName == null) return;
        if (frames == null || frames.size() < 2) return;

        List<GhostFrame> sampled = downsample(frames, MAX_GHOST_FRAMES);
        int frameCount = sampled.size();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(1);
        writeString(out, trackName);
        out.writeLong(Math.max(0L, lapMillis));
        // Nominal cadence: ghosts are recorded every 2 ticks, so the client interpolates
        // between real ticks[] stamps using this only as a fallback hint.
        writeVarInt(out, GHOST_SAMPLE_INTERVAL_TICKS);
        writeVarInt(out, frameCount);

        for (int i = 0; i < frameCount; i++) {
            GhostFrame frame = sampled.get(i);
            writeVarInt(out, i * GHOST_SAMPLE_INTERVAL_TICKS);
            out.writeFloat((float) frame.getX());
            out.writeFloat((float) frame.getY());
            out.writeFloat((float) frame.getZ());
            out.writeFloat(frame.getYaw());
            // Pitch is derived from the vertical slope to the next frame, matching how the
            // client applies it to the rider's head.
            out.writeFloat(pitchTowards(sampled, i));
        }

        byte[] payload = out.toByteArray();
        player.sendPluginMessage(plugin, GHOST_DATA_CHANNEL, payload);
        plugin.getDebugManager().logTimeTrialSystem(
                "[WolfMOD] Sent personal best '" + trackName + "' to " + player.getName()
                        + " (" + frameCount + "/" + frames.size() + " frames, "
                        + payload.length + " bytes)");
    }

    /**
     * Evenly thins {@code frames} to at most {@code maxFrames}, always preserving the
     * first and last entries. Returns the original list when it already fits.
     */
    private static List<GhostFrame> downsample(List<GhostFrame> frames, int maxFrames) {
        int size = frames.size();
        if (size <= maxFrames) {
            return frames;
        }
        List<GhostFrame> sampled = new ArrayList<>(maxFrames);
        double step = (size - 1.0D) / (maxFrames - 1.0D);
        int lastIndex = -1;
        for (int i = 0; i < maxFrames; i++) {
            int index = (i == maxFrames - 1) ? size - 1 : (int) Math.round(i * step);
            if (index == lastIndex) {
                continue;
            }
            lastIndex = index;
            sampled.add(frames.get(index));
        }
        return sampled;
    }

    /**
     * Pitch in degrees from frame {@code i} towards the next one, or 0 on the last frame.
     */
    private static float pitchTowards(List<GhostFrame> frames, int i) {
        if (i + 1 >= frames.size()) {
            return 0.0F;
        }
        GhostFrame current = frames.get(i);
        GhostFrame next = frames.get(i + 1);
        double dx = next.getX() - current.getX();
        double dz = next.getZ() - current.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.toDegrees(Math.atan2(-(next.getY() - current.getY()), horizontal));
    }

    /**
     * Sends a config key/command to the client via wolfnetwork:settings channel.
     * e.g. sendConfig(player, "ghost_start", "")
     */
    public void sendConfig(Player player, String key, String value) {
        if (player == null || !player.isOnline()) return;

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        writeString(out, key);
        writeString(out, value);
        player.sendPluginMessage(plugin, CONFIG_CHANNEL, out.toByteArray());
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = 0;
        int shift = 0;
        for (int i = 0; i < 5; i++) {
            int current = input.readUnsignedByte();
            length |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                if (length < 0 || length > 32767 || length > input.available()) {
                    return null;
                }
                byte[] bytes = new byte[length];
                input.readFully(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            }
            shift += 7;
        }
        return null;
    }

    public void sendGhostStart(Player player) {
        sendConfig(player, "2", "");        // Start Timer (needed by samplePose)
        sendConfig(player, "ghost_start", ""); // Start ghost playback
    }

    public void sendGhostStop(Player player) {
        sendConfig(player, "3", "");         // Stop Timer
        sendConfig(player, "ghost_stop", "");  // Stop ghost playback
    }

    public void sendGhostClear(Player player) {
        sendConfig(player, "ghost_clear", "");
    }

    /**
     * Drops any half-assembled upload for a leaving player. Without this a disconnect
     * mid-upload would leave the buffer in memory until the next lap on the same account.
     */
    public void cleanupPlayer(UUID uuid) {
        if (uuid != null) {
            this.pendingUploads.remove(uuid);
        }
    }

    /**
     * Shortcut to trigger the Fastest Lap animation on the client.
     */
    public void sendFastestLap(Player player, String playerName, String lapTime) {
        sendConfig(player, "4", playerName + "|" + lapTime);
    }

    /**
     * Shortcut to update the Timer state (Start/Stop).
     */
    public void setTimerState(Player player, boolean running) {
        sendConfig(player, running ? "2" : "3", "");
    }

    // --- Binary encoding helpers matching Minecraft's PacketByteBuf ---

    private static void writeVarInt(ByteArrayDataOutput out, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }

    private static void writeString(ByteArrayDataOutput out, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}
