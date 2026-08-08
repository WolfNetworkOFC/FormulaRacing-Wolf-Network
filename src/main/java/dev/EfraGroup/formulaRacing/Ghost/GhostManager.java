package dev.EfraGroup.formulaRacing.Ghost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
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
 * - Reproduz a melhor volta com um ArmorStand invisível que percorre a linha
 *   gravada e spawna 1 partícula de coração por tick (movimento interpolado).
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
            Location loc = player.getLocation();
            if (loc != null && loc.getWorld() != null) {
                frames.add(new GhostFrame(loc.getX(), loc.getY(), loc.getZ()));
            }
        }, 0L, RECORD_INTERVAL_TICKS);

        recordingTasks.put(uuid, task);

        plugin.getDebugManager().logTimeTrialSystem(
                "[GHOST-REC] Recording started for " + player.getName());
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

        synchronized (frames) {
            return new ArrayList<>(frames);
        }
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
    //  REPLAY (ArmorStand + Heart Particles)
    // ========================

    public void startReplay(Player player, List<GhostFrame> frames) {
        startReplay(player, frames, null, null);
    }

    /**
     * Starts a replay for the given frames.
     *
     * @param particle particle type; when {@code dust} is provided this is ignored
     *                 (always DUST). When both are null, falls back to HEART.
     * @param dust     colored dust for medal lines (e.g. diamond = blue); null = hearts
     */
    public void startReplay(Player player, List<GhostFrame> frames, Particle particle, Particle.DustOptions dust) {
        if (player == null || !player.isOnline() || frames == null || frames.size() < MIN_FRAMES) return;

        UUID uuid = player.getUniqueId();
        String key = (dust != null || particle != null) ? uuid + ":medal" : uuid + ":pb";

        GhostPlaybackSession old = activeReplays.remove(key);
        if (old != null) old.removeStand();

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
                if (session != null) session.removeStand();
            }
        }
    }

    /**
     * Global tick task that advances all active replays.
     * Runs every PLAYBACK_INTERVAL_TICKS (same as recording interval) on the global region scheduler.
     * ArmorStand operations are delegated to the correct region via runTaskAt.
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

        // Remove all ghost armor stands and cancel replay
        for (GhostPlaybackSession session : activeReplays.values()) {
            session.removeStand();
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
     * Uses an invisible, no-gravity ArmorStand that is teleported frame-by-frame
     * along the recorded path at the same rate it was recorded.
     * Each tick spawns heart particles at the stand's position.
     */
    private static class GhostPlaybackSession {
        private final FormulaRacing plugin;
        private final Player player;
        private final List<GhostFrame> frames;
        private final World world;
        private final Particle particle;
        private final Particle.DustOptions dust;
        private volatile ArmorStand ghostStand;
        private volatile boolean standRemoved;
        private int frameIndex;

        GhostPlaybackSession(FormulaRacing plugin, Player player, List<GhostFrame> frames,
                             Particle particle, Particle.DustOptions dust) {
            this.plugin = plugin;
            this.player = player;
            this.frames = frames;
            this.world = player.getWorld();
            this.particle = particle;
            this.dust = dust;
            this.frameIndex = 0;
            this.ghostStand = null;
            this.standRemoved = false;
        }

        /**
         * Advances one playback step: teleports the armor stand to the current frame
         * position and spawns heart particles.
         *
         * @return true if playback is still active, false if finished
         */
        boolean tick() {
            if (standRemoved) return false;
            if (frameIndex >= frames.size()) {
                removeStand();
                return false;
            }

            GhostFrame frame = frames.get(frameIndex);
            Location targetLoc = new Location(world, frame.getX(), frame.getY() + 0.5, frame.getZ());

            // Schedule armor stand + particle on the correct region thread
            SchedulerHelper.runTaskAt(plugin, targetLoc, () -> {
                if (standRemoved || !player.isOnline()) {
                    removeStand();
                    return;
                }

                // Create armor stand on first tick
                if (ghostStand == null || !ghostStand.isValid()) {
                    if (world == null) return;
                    ghostStand = world.spawn(targetLoc, ArmorStand.class, stand -> {
                        stand.setInvisible(true);
                        stand.setGravity(false);
                        stand.setMarker(true);
                        stand.setSmall(true);
                        stand.setInvulnerable(true);
                    });
                }

                // Teleport stand to frame position (must use teleportAsync in Folia region threading)
                if (ghostStand != null && ghostStand.isValid()) {
                    ghostStand.teleportAsync(targetLoc);
                }

                // Spawn particles visible only to the owner.
                // Medal lines use colored dust; PB lines use heart particles.
                if (dust != null) {
                    player.spawnParticle(Particle.DUST,
                            targetLoc.getX(), targetLoc.getY(), targetLoc.getZ(), 1, dust);
                } else {
                    player.spawnParticle(particle != null ? particle : Particle.HEART,
                            targetLoc.getX(), targetLoc.getY(), targetLoc.getZ(),
                            2, 0.15, 0.15, 0.15, 0);
                }
            });

            frameIndex++;
            return true;
        }

        void removeStand() {
            standRemoved = true;
            ArmorStand stand = ghostStand;
            ghostStand = null;
            if (stand != null && stand.isValid()) {
                Location standLoc = stand.getLocation();
                SchedulerHelper.runTaskAt(plugin, standLoc, () -> {
                    if (stand.isValid()) stand.remove();
                });
            }
        }
    }

    /**
     * Callback interface for async ghost loading.
     */
    public interface GhostLoadCallback {
        void onLoad(List<GhostFrame> frames);
    }
}
