package dev.EfraGroup.formulaRacing.WolfMod;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.EfraGroup.formulaRacing.AI.AIRacingLine;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.TimeTrial.Timing.WolfTimingService;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class WolfMOD implements PluginMessageListener {

    private final FormulaRacing plugin;
    private WolfTimingService timingService;

    public static final String GHOST_DATA_CHANNEL = "wolfnetwork:ghost_data";
    public static final String CONFIG_CHANNEL = "wolfnetwork:settings";

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
    }

    /**
     * Sends the complete track racing line (AI ideal line) to the client as a
     * GhostDataPayload via the wolfnetwork:ghost_data channel.
     * The client renders it as a single ghost boat+player following the line.
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

    @Override
    public void onPluginMessageReceived(
        String channel,
        Player player,
        byte[] message
    ) {
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
