package dev.EfraGroup.formulaRacing.Ghost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia gravação, armazenamento e replay de voltas fantasma (ghost).
 * <p>
 * - Grava a posição do jogador a cada 2 ticks durante uma volta de Time Trial.
 * - Salva apenas quando a volta é um novo PB (Personal Best).
 * - Reproduz a melhor volta desenhando uma linha contínua de partículas DUST
 *   interpoladas entre os frames (sem corações, que dispersam e nunca formam
 *   uma linha) e sem entidades: nada de ArmorStand nem teleport assíncrono.
 * <p>
 * Otimizado para Folia: gravação usa runTaskTimerAtEntity, replay usa
 * runTaskTimer global com operações de entidade via runTaskAt (RegionScheduler).
 */
public class GhostManager {

    private final FormulaRacing plugin;
    private final Gson gson;
    private final File ghostsRootFolder;

    // --- Recording state ---
    private final Map<UUID, List<GhostFrame>> activeRecordings = new ConcurrentHashMap<>();
    private final Map<UUID, FRTask> recordingTasks = new ConcurrentHashMap<>();

    // --- In-memory ghost cache (playerUUID:trackNameWS -> frames) ---
    private final Map<String, List<GhostFrame>> ghostCache = new ConcurrentHashMap<>();

    // --- Replay state ---
    // Key is "uuid:pb" or "uuid:medal" so a player can have the PB line and a
    // medal line running simultaneously without cancelling each other.
    private final Map<String, GhostPlaybackSession> activeReplays = new ConcurrentHashMap<>();
    private FRTask replayTickTask;

    // Recording frequency: 2 ticks between captures (10 captures/sec) for smooth interpolation
    private static final long RECORD_INTERVAL_TICKS = 2L;

    // Playback frequency: same as recording interval for frame-perfect timing
    private static final long PLAYBACK_INTERVAL_TICKS = RECORD_INTERVAL_TICKS; // = 2 ticks

    // Minimum valid frames for a ghost to be saved
    private static final int MIN_FRAMES = 5;

    // PB line colour: a static DUST so the line is continuous and crisp instead
    // of drifting HEART particles, which scatter and never align into a line.
    private static final Particle.DustOptions PB_LINE_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 64, 64), 1.0f);

    // Spacing between interpolated line points, in blocks. Smaller = denser line.
    private static final double SEGMENT_POINT_SPACING = 0.35;

    // Cap the points drawn for one recorded segment (guards against lag gaps).
    private static final int MAX_POINTS_PER_SEGMENT = 48;

    // Segments longer than this are teleports/lag, not real driving line, so
    // they are not painted as a false straight line.
    private static final double MAX_SEGMENT_DISTANCE = 8.0;

    // Height of the line above the recorded (boat) position. Kept small so the
    // trail sits on the driving line instead of floating above it.
    private static final double LINE_Y_OFFSET = 0.2;

    // Amount to lift a point that fell inside a solid block, so the line does
    // not get swallowed by terrain.
    private static final double OCCLUSION_EPSILON = 0.05;

    // Viewer render radius for the line: points farther than this from the player
    // are not visible, so they are not sent (performance).
    private static final double MAX_LINE_DISTANCE_SQ = 64.0 * 64.0; // 64 blocks

    // Largest plausible gap between two recorded frames (2 ticks apart). Kept well
    // above real boat speeds so a fast lap is never rejected; only teleports/lag
    // spikes exceed it. Such a recording replays with wrong timing and a false
    // straight line, so it is discarded instead of saved.
    private static final double MAX_RECORD_FRAME_STEP = 16.0;

    public GhostManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.ghostsRootFolder = new File(plugin.getDataFolder(), "ghosts");
        if (!ghostsRootFolder.exists()) {
            ghostsRootFolder.mkdirs();
        }
        startReplayTickTask();
    }

    // ========================
    //  RECORDING
    // ========================

    public void startRecording(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        cancelRecording(player);

        List<GhostFrame> frames = Collections.synchronizedList(new ArrayList<>());
        activeRecordings.put(uuid, frames);

        FRTask task = SchedulerHelper.runTaskTimerAtEntity(plugin, player, () -> {
            if (!player.isOnline() || !activeRecordings.containsKey(uuid)) return;
            Location loc = resolveRecordLocation(player);
            if (loc != null && loc.getWorld() != null) {
                frames.add(new GhostFrame(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw()));
            }
        }, 0L, RECORD_INTERVAL_TICKS);

        recordingTasks.put(uuid, task);

        plugin.getDebugManager().logTimeTrialSystem(
                "[GHOST-REC] Recording started for " + player.getName());
    }

    /**
     * Location recorded for a ghost frame: the boat when the player is driving one,
     * otherwise the player. Recording the boat keeps the line on the same reference
     * the player races against, since the hull slides out from under the driver on drifts.
     *
     * <p>Falls back to the player when the vehicle cannot be read (not yet spawned, or
     * the lookup crosses a region thread on Folia).
     */
    private Location resolveRecordLocation(Player player) {
        if (!player.isInsideVehicle()) {
            return player.getLocation();
        }
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof org.bukkit.entity.Boat)) {
            return player.getLocation();
        }
        Location boatLoc = vehicle.getLocation();
        return boatLoc != null ? boatLoc : player.getLocation();
    }

    public List<GhostFrame> stopRecording(Player player) {
        if (player == null) return null;
        UUID uuid = player.getUniqueId();

        FRTask task = recordingTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();

        List<GhostFrame> frames = activeRecordings.remove(uuid);
        if (frames == null || frames.size() < MIN_FRAMES) {
            plugin.getDebugManager().logTimeTrialSystem(
                    "[GHOST-REC] Recording discarded for " + player.getName()
                            + " — too few frames (" + (frames == null ? 0 : frames.size()) + ")");
            return null;
        }

        plugin.getDebugManager().logTimeTrialSystem(
                "[GHOST-REC] Recording stopped for " + player.getName()
                        + " — " + frames.size() + " frames captured");

        List<GhostFrame> snapshot;
        synchronized (frames) {
            snapshot = new ArrayList<>(frames);
        }
        if (hasTeleportSpike(snapshot)) {
            plugin.getDebugManager().logTimeTrialSystem(
                    "[GHOST-REC] Recording discarded for " + player.getName()
                            + " — teleport/lag spike in frames");
            return null;
        }
        return snapshot;
    }

    public void cancelRecording(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        FRTask task = recordingTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
        activeRecordings.remove(uuid);
    }

    public void cancelRecording(UUID uuid) {
        FRTask task = recordingTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
        activeRecordings.remove(uuid);
    }

    // ========================
    //  STORAGE (Async JSON)
    // ========================

    private static String cacheKey(UUID playerUuid, String trackName) {
        return playerUuid.toString() + ":" + sanitizeFileName(trackName);
    }

    public void saveGhostAsync(UUID playerUuid, String trackName, List<GhostFrame> frames) {
        if (playerUuid == null || trackName == null || frames == null || frames.isEmpty()) return;

        // Central validation: whatever the source (server recording or client
        // upload), never persist a ghost whose path jumps impossibly.
        if (hasTeleportSpike(frames)) {
            plugin.getDebugManager().logTimeTrialSystem(
                    "[GHOST-SAVE] Ghost rejected for " + playerUuid + " on track " + trackName
                            + " — teleport/lag spike in frames");
            return;
        }

        ghostCache.put(cacheKey(playerUuid, trackName), frames);

        String safeTrackName = sanitizeFileName(trackName);
        File playerFolder = new File(ghostsRootFolder, playerUuid.toString());
        if (!playerFolder.exists()) playerFolder.mkdirs();

        File ghostFile = new File(playerFolder, safeTrackName + ".json");

        SchedulerHelper.runAsync(plugin, () -> {
            try (FileWriter writer = new FileWriter(ghostFile)) {
                gson.toJson(frames, writer);
                plugin.getDebugManager().logTimeTrialSystem(
                        "[GHOST-SAVE] Ghost saved for " + playerUuid + " on track " + trackName
                                + " (" + frames.size() + " frames, cached)");
            } catch (IOException e) {
                plugin.getDebugManager().logTimeTrialSystem(
                        "[GHOST-SAVE] Failed to save ghost for " + playerUuid + ": " + e.getMessage());
            }
        });
    }

    /**
     * True when two consecutive frames are impossibly far apart for a 2-tick
     * recording window — i.e. the recording contains a teleport/lag gap. Replaying
     * such frames advances by index at a fixed cadence, which would desync the
     * ghost from the real lap time and paint a false straight line, so the whole
     * recording is rejected.
     */
    private static boolean hasTeleportSpike(List<GhostFrame> frames) {
        if (frames == null || frames.size() < 2) {
            return false;
        }
        double maxSq = MAX_RECORD_FRAME_STEP * MAX_RECORD_FRAME_STEP;
        for (int i = 1; i < frames.size(); i++) {
            GhostFrame a = frames.get(i - 1);
            GhostFrame b = frames.get(i);
            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            double dz = b.getZ() - a.getZ();
            if (dx * dx + dy * dy + dz * dz > maxSq) {
                return true;
            }
        }
        return false;
    }

    public void loadGhostAsync(UUID playerUuid, String trackName, GhostLoadCallback callback) {
        if (playerUuid == null || trackName == null) {
            callback.onLoad(null);
            return;
        }

        String key = cacheKey(playerUuid, trackName);
        List<GhostFrame> cached = ghostCache.get(key);
        if (cached != null) {
            SchedulerHelper.runTask(plugin, () -> callback.onLoad(cached));
            plugin.getDebugManager().logTimeTrialSystem(
                    "[GHOST-LOAD] Ghost loaded from cache for " + playerUuid
                            + " on track " + trackName + " (" + cached.size() + " frames)");
            return;
        }

        String safeTrackName = sanitizeFileName(trackName);
        File ghostFile = new File(new File(ghostsRootFolder, playerUuid.toString()), safeTrackName + ".json");

        if (!ghostFile.exists()) {
            callback.onLoad(null);
            return;
        }

        SchedulerHelper.runAsync(plugin, () -> {
            try (FileReader reader = new FileReader(ghostFile)) {
                Type listType = new TypeToken<List<GhostFrame>>() {}.getType();
                List<GhostFrame> frames = gson.fromJson(reader, listType);
                if (frames != null) ghostCache.put(key, frames);
                SchedulerHelper.runTask(plugin, () -> callback.onLoad(frames));
                plugin.getDebugManager().logTimeTrialSystem(
                        "[GHOST-LOAD] Ghost loaded from disk for " + playerUuid
                                + " on track " + trackName
                                + " (" + (frames == null ? 0 : frames.size()) + " frames, cached)");
            } catch (IOException e) {
                plugin.getDebugManager().logTimeTrialSystem(
                        "[GHOST-LOAD] Failed to load ghost: " + e.getMessage());
                SchedulerHelper.runTask(plugin, () -> callback.onLoad(null));
            }
        });
    }

    public boolean hasGhost(UUID playerUuid, String trackName) {
        if (playerUuid == null || trackName == null) return false;
        return ghostCache.containsKey(cacheKey(playerUuid, trackName))
                || new File(new File(ghostsRootFolder, playerUuid.toString()),
                    sanitizeFileName(trackName) + ".json").exists();
    }

    public void deleteGhostAsync(UUID playerUuid, String trackName) {
        if (playerUuid == null || trackName == null) return;
        ghostCache.remove(cacheKey(playerUuid, trackName));

        String safeTrackName = sanitizeFileName(trackName);
        File ghostFile = new File(new File(ghostsRootFolder, playerUuid.toString()), safeTrackName + ".json");

        SchedulerHelper.runAsync(plugin, () -> {
            if (ghostFile.exists() && ghostFile.delete()) {
                plugin.getDebugManager().logTimeTrialSystem(
                        "[GHOST-DEL] Ghost deleted for " + playerUuid + " on " + trackName);
            }
        });
    }

    // ========================
    //  RENAME (track renamed)
    // ========================

    /**
     * Renomeia no disco todos os ghosts guardados para uma pista. Usa o mesmo
     * {@link #sanitizeFileName(String)} do caminho de gravação, pelo que o novo
     * ficheiro é exatamente o que o plugin procura depois. Também descarta as
     * entradas de cache antigas.
     */
    public synchronized void renameTrack(String oldTrackName, String newTrackName) {
        if (oldTrackName == null || newTrackName == null) return;
        String oldSafe = sanitizeFileName(oldTrackName);
        String newSafe = sanitizeFileName(newTrackName);
        if (oldSafe.equals(newSafe)) return;

        ghostCache.keySet().removeIf(key -> key.endsWith(":" + oldSafe));

        File[] playerFolders = ghostsRootFolder.listFiles(File::isDirectory);
        if (playerFolders == null) return;

        int renamed = 0;
        for (File playerFolder : playerFolders) {
            File oldGhost = new File(playerFolder, oldSafe + ".json");
            if (!oldGhost.exists()) continue;
            File newGhost = new File(playerFolder, newSafe + ".json");
            if (oldGhost.renameTo(newGhost)) {
                renamed++;
            } else {
                plugin.getDebugManager().logTimeTrialSystem(
                        "[GHOST-RENAME] Falha ao renomear ghost " + oldGhost.getAbsolutePath());
            }
        }
        plugin.getDebugManager().logTimeTrialSystem(
                "[GHOST-RENAME] " + renamed + " ghost(s) renomeado(s) '" + oldSafe + "' -> '" + newSafe + "'");
    }

    // ========================
    //  REPLAY (Interpolated Dust Line)
    // ========================

    public void startReplay(Player player, List<GhostFrame> frames) {
        startReplay(player, frames, null, null);
    }

    /**
     * Starts a replay for the given frames.
     *
     * @param particle custom particle type used only when {@code dust} is null and
     *                 the particle is not HEART; HEART is ignored (PB lines use DUST).
     * @param dust     colored dust for medal lines (e.g. diamond = blue); when null
     *                 the PB line uses {@link #PB_LINE_DUST}
     */
    public void startReplay(Player player, List<GhostFrame> frames, Particle particle, Particle.DustOptions dust) {
        if (player == null || !player.isOnline() || frames == null || frames.size() < MIN_FRAMES) return;

        UUID uuid = player.getUniqueId();
        String key = (dust != null || particle != null) ? uuid + ":medal" : uuid + ":pb";

        GhostPlaybackSession old = activeReplays.remove(key);
        if (old != null) old.stop();

        GhostPlaybackSession session = new GhostPlaybackSession(plugin, player, frames, particle, dust);
        activeReplays.put(key, session);

        plugin.getDebugManager().logTimeTrialSystem(
                "[GHOST-REPLAY] Replay started for " + player.getName()
                        + " (" + frames.size() + " frames" + (dust != null ? ", medal" : "") + ")");
    }

    public void stopReplay(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        stopReplay(uuid);
        plugin.getDebugManager().logTimeTrialSystem(
                "[GHOST-REPLAY] Replays stopped for " + player.getName());
    }

    public void stopReplay(UUID uuid) {
        Iterator<String> it = activeReplays.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (key.startsWith(uuid + ":")) {
                GhostPlaybackSession session = activeReplays.remove(key);
                if (session != null) session.stop();
            }
        }
    }

    /**
     * Global tick task that advances all active replays.
     * Runs every PLAYBACK_INTERVAL_TICKS (same as recording interval) on the global region scheduler.
     * Particle spawning is delegated to the correct region via runTaskAt.
     */
    private void startReplayTickTask() {
        replayTickTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            if (activeReplays.isEmpty()) return;

            Iterator<Map.Entry<String, GhostPlaybackSession>> it = activeReplays.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, GhostPlaybackSession> entry = it.next();
                GhostPlaybackSession session = entry.getValue();

                if (!session.tick()) {
                    it.remove();
                    plugin.getDebugManager().logTimeTrialSystem(
                            "[GHOST-REPLAY] Replay finished for " + entry.getKey());
                }
            }
        }, 0L, PLAYBACK_INTERVAL_TICKS);
    }

    // ========================
    //  CLEANUP
    // ========================

    public void cleanupPlayer(Player player) {
        if (player == null) return;
        cancelRecording(player);
        stopReplay(player);
    }

    public void cleanupPlayer(UUID uuid) {
        cancelRecording(uuid);
        stopReplay(uuid);
    }

    public void shutdown() {
        // Cancel all recording tasks
        for (FRTask task : recordingTasks.values()) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        recordingTasks.clear();
        activeRecordings.clear();

        // Stop all active replays
        for (GhostPlaybackSession session : activeReplays.values()) {
            session.stop();
        }
        if (replayTickTask != null && !replayTickTask.isCancelled()) {
            replayTickTask.cancel();
        }
        activeReplays.clear();

        ghostCache.clear();
    }

    // ========================
    //  INTERNAL
    // ========================

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    /**
     * A single ghost playback session.
     *
     * <p>Paints the recorded racing line as a continuous, owner-only DUST trail
     * interpolated between frames. It creates no entity and teleports nothing:
     * particles are sent straight from the region thread, so frames cannot be
     * reordered by an async teleport and there is no ArmorStand to track/clean up.
     */
    private static class GhostPlaybackSession {
        private final FormulaRacing plugin;
        private final Player player;
        private final List<GhostFrame> frames;
        private final World world;
        private final Particle particle;
        private final Particle.DustOptions dust;
        private volatile boolean stopped;
        private int frameIndex;
        private Location lastDrawn;

        GhostPlaybackSession(FormulaRacing plugin, Player player, List<GhostFrame> frames,
                             Particle particle, Particle.DustOptions dust) {
            this.plugin = plugin;
            this.player = player;
            this.frames = frames;
            this.world = player.getWorld();
            this.particle = particle;
            this.dust = dust;
            this.stopped = false;
            this.frameIndex = 0;
            this.lastDrawn = null;
        }

        /**
         * Advances one playback step: paints the line segment leading to the current
         * frame, visible only to the owner.
         *
         * @return true if playback is still active, false if finished
         */
        boolean tick() {
            if (stopped) return false;
            if (frameIndex >= frames.size()) {
                stop();
                return false;
            }

            GhostFrame frame = frames.get(frameIndex);
            Location targetLoc = new Location(world, frame.getX(), frame.getY() + LINE_Y_OFFSET, frame.getZ());
            final Location fromLoc = lastDrawn;

            // Player particle packets must run on the player's region thread under
            // Folia (getLocation/spawnParticle are player-entity access). Tasks for
            // the same region run in order, so frames stay ordered without teleport.
            SchedulerHelper.runTaskFor(plugin, player, () -> {
                if (stopped || !player.isOnline()) {
                    stop();
                    return;
                }
                Location viewerLoc = player.getLocation();
                Particle.DustOptions lineDust = resolveLineDust();
                if (fromLoc != null && fromLoc.getWorld() == targetLoc.getWorld()) {
                    drawSegment(viewerLoc, fromLoc, targetLoc, lineDust);
                } else {
                    spawnLinePoint(viewerLoc, targetLoc.getX(), targetLoc.getY(), targetLoc.getZ(), lineDust);
                }
            });

            lastDrawn = targetLoc;
            frameIndex++;
            return true;
        }

        /**
         * Colour for this line: medal dust when supplied, otherwise a static PB colour.
         * HEART is deliberately never used — it drifts and never forms a line. A
         * non-HEART custom particle (no dust) is returned as {@code null} so callers
         * fall back to spawning that particle type directly.
         */
        private Particle.DustOptions resolveLineDust() {
            if (dust != null) {
                return dust;
            }
            if (particle == null || particle == Particle.HEART) {
                return PB_LINE_DUST;
            }
            return null;
        }

        /**
         * Fills the gap between two consecutive frames with evenly spaced line points,
         * so a 2-tick recording cadence still reads as a continuous line. Segments long
         * enough to be lag/teleports are skipped instead of painted straight.
         */
        private void drawSegment(Location viewerLoc, Location from, Location to, Particle.DustOptions lineDust) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double dz = to.getZ() - from.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance <= 0.0 || distance > MAX_SEGMENT_DISTANCE) {
                spawnLinePoint(viewerLoc, to.getX(), to.getY(), to.getZ(), lineDust);
                return;
            }
            int points = (int) Math.ceil(distance / SEGMENT_POINT_SPACING);
            points = Math.min(points, MAX_POINTS_PER_SEGMENT);
            for (int i = 1; i <= points; i++) {
                double t = (double) i / points;
                spawnLinePoint(viewerLoc,
                        from.getX() + dx * t,
                        from.getY() + dy * t,
                        from.getZ() + dz * t,
                        lineDust);
            }
        }

        private void spawnLinePoint(Location viewerLoc, double x, double y, double z, Particle.DustOptions lineDust) {
            if (viewerLoc == null || viewerLoc.getWorld() != world) {
                return;
            }
            // Performance: only send points the viewer can actually see. Beyond the
            // render radius even forced packets are wasted work.
            double ddx = viewerLoc.getX() - x;
            double ddy = viewerLoc.getY() - y;
            double ddz = viewerLoc.getZ() - z;
            if (ddx * ddx + ddy * ddy + ddz * ddz > MAX_LINE_DISTANCE_SQ) {
                return;
            }
            double visibleY = resolveVisibleY(x, y, z);
            // force=true disables the ~32 block view-distance cull, so the line stays
            // visible from behind the player instead of vanishing at range.
            if (lineDust != null) {
                player.spawnParticle(Particle.DUST, x, visibleY, z,
                        1, 0.0, 0.0, 0.0, 0.0, lineDust, true);
            } else if (particle != null) {
                player.spawnParticle(particle, x, visibleY, z,
                        1, 0.0, 0.0, 0.0, 0.0, null, true);
            }
        }

        /**
         * Lifts a line point out of a solid block so the trail does not disappear
         * inside terrain (slopes, slabs, clipped ground). Points already above the
         * surface are left untouched.
         */
        private double resolveVisibleY(double x, double y, double z) {
            World w = world;
            if (w == null) return y;
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            if (by < w.getMinHeight() || by >= w.getMaxHeight()) return y;
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return y;
            org.bukkit.block.Block block = w.getBlockAt(bx, by, bz);
            if (!block.getType().isOccluding()) return y;
            double top = block.getBoundingBox().getMaxY();
            return Math.max(y, top + OCCLUSION_EPSILON);
        }

        void stop() {
            stopped = true;
        }
    }

    /**
     * Callback interface for async ghost loading.
     */
    public interface GhostLoadCallback {
        void onLoad(List<GhostFrame> frames);
    }
}
