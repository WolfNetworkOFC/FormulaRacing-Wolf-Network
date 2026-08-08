package dev.EfraGroup.formulaRacing.AI;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages racing line recording based on the heat timer.
 * Recording starts when the heat transitions to RACING and ends
 * when the heat finishes (FINISHED).
 */
public class AIRacingLineRecorder {

    private final FormulaRacing plugin;
    private final AIRacingLineManager racingLineManager;
    private final Map<UUID, RecordingSession> activeSessions;
    private FRTask cleanupTask;

    public AIRacingLineRecorder(FormulaRacing plugin, AIRacingLineManager racingLineManager) {
        this.plugin = plugin;
        this.racingLineManager = racingLineManager;
        this.activeSessions = new HashMap<>();
        startCleanupTask();
    }

    /**
     * Registers the player as ready to record in the next race.
     * The actual recording starts when the heat goes to RACING.
     */
    public boolean startRecording(Player player, String trackName) {
        UUID uuid = player.getUniqueId();
        if (activeSessions.containsKey(uuid)) {
            player.sendMessage("§cYou are already recording a racing line!");
            return false;
        }

        RecordingSession session = new RecordingSession(player, trackName);
        activeSessions.put(uuid, session);

        player.sendMessage("");
        player.sendMessage("§a═══════════════════════════════");
        player.sendMessage("§e  Recording Line Registered");
        player.sendMessage("");
        player.sendMessage("§f  Track: §b" + trackName);
        player.sendMessage("§f  Recording will start when the race begins");
        player.sendMessage("§f  and will end automatically upon completion");
        player.sendMessage("");
        player.sendMessage("§c  Use /ai record stop to cancel");
        player.sendMessage("§a═══════════════════════════════");

        plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Recording registration for " + player.getName() + " on track " + trackName);
        return true;
    }

    /**
     * Cancels the registered recording (before or during the race).
     */
    public boolean stopRecording(Player player) {
        UUID uuid = player.getUniqueId();
        RecordingSession session = activeSessions.remove(uuid);

        if (session == null) {
            player.sendMessage("§cYou are not recording any racing line!");
            return false;
        }

        session.cancel();
        player.sendMessage("§eRecording cancelled!");
        plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Recording cancelled for " + player.getName());
        return true;
    }

    /**
     * Called by the listener when a heat transitions to RACING.
     * Starts recording for all registered players who are in this heat.
     */
    public void onHeatRacing(Heats heat) {
        if (heat == null) return;

        for (RecordingSession session : activeSessions.values()) {
            if (session.isCancelled() || session.isCompleted()) continue;

            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) continue;

            // Checks if the player is in this heat
            if (heat.getDriver(player.getUniqueId()) == null) continue;

            session.startRecording();
        }
    }

    /**
     * Called by the listener when a heat transitions to FINISHED.
     * Finalizes and saves the recording for all registered players.
     */
    public void onHeatFinished(Heats heat) {
        if (heat == null) return;

        for (RecordingSession session : activeSessions.values()) {
            if (session.isCancelled() || session.isCompleted()) continue;
            if (!session.isRecording()) continue;

            Player player = session.getPlayer();
            if (player == null) continue;

            // Checks if the player was in this heat
            if (heat.getDriver(player.getUniqueId()) != null) {
                session.complete();
            }
        }
    }

    public boolean isRecording(UUID uuid) {
        RecordingSession session = activeSessions.get(uuid);
        return session != null && !session.isCancelled() && !session.isCompleted();
    }

    public RecordingSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    private void startCleanupTask() {
        cleanupTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            List<UUID> toRemove = new ArrayList<>();

            for (Map.Entry<UUID, RecordingSession> entry : activeSessions.entrySet()) {
                RecordingSession session = entry.getValue();
                Player player = session.getPlayer();
                if (player == null || !player.isOnline() || session.isInactiveTooLong()) {
                    toRemove.add(entry.getKey());
                    session.cancel();
                }
            }

            for (UUID uuid : toRemove) {
                activeSessions.remove(uuid);
            }
        }, 200L, 200L);
    }

    public void cleanup() {
        for (RecordingSession session : activeSessions.values()) {
            session.cancel();
        }
        activeSessions.clear();

        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
    }

    public class RecordingSession {
        private final Player player;
        private final String trackName;
        private final List<Location> recordedPoints;
        private final List<Location> brakingPoints;
        private final List<Location> accelerationPoints;
        private final long registerTime;
        private long lastUpdateTime;
        private long recordingStartTime;
        private FRTask recordingTask;
        private boolean recording;
        private boolean cancelled;
        private boolean completed;

        public RecordingSession(Player player, String trackName) {
            this.player = player;
            this.trackName = trackName == null ? "" : trackName.replace(" ", "").toLowerCase();
            this.recordedPoints = new ArrayList<>();
            this.brakingPoints = new ArrayList<>();
            this.accelerationPoints = new ArrayList<>();
            this.registerTime = System.currentTimeMillis();
            this.lastUpdateTime = registerTime;
            this.recordingStartTime = 0L;
            this.recording = false;
            this.cancelled = false;
            this.completed = false;
        }

        /**
         * Starts the actual recording (called when the heat goes to RACING).
         */
        public void startRecording() {
            if (recording || cancelled || completed) return;
            this.recording = true;
            this.recordingStartTime = System.currentTimeMillis();

            // Chat message for the player
            Player p = getPlayer();
            if (p != null && p.isOnline()) {
                p.sendMessage("");
                p.sendMessage("§a═══════════════════════════════");
                p.sendMessage("§e  ▶ Recording Started!");
                p.sendMessage("");
                p.sendMessage("§f  The race has started — recording racing line");
                p.sendMessage("§f  The line will be saved at the end of the race");
                p.sendMessage("§a═══════════════════════════════");
            }

            recordingTask = SchedulerHelper.runTaskTimerAtEntity(plugin, player, () -> {
                if (cancelled || completed || !recording) {
                    return;
                }

                Player currentPlayer = getPlayer();
                if (currentPlayer == null || !currentPlayer.isOnline()) {
                    return;
                }

                Location currentLoc = currentPlayer.getLocation();
                lastUpdateTime = System.currentTimeMillis();

                if (shouldAddPoint(currentLoc)) {
                    addPoint(currentLoc, currentPlayer);
                }
            }, 1L, 2L);

            plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Recording started for " + player.getName() + " on track " + trackName);
        }

        private boolean shouldAddPoint(Location newLoc) {
            if (newLoc == null || newLoc.getWorld() == null) {
                return false;
            }

            if (recordedPoints.isEmpty()) {
                return true;
            }

            Location lastPoint = recordedPoints.get(recordedPoints.size() - 1);
            if (!lastPoint.getWorld().equals(newLoc.getWorld())) {
                return true;
            }

            return lastPoint.distanceSquared(newLoc) >= 4.0;
        }

        private void addPoint(Location loc, Player currentPlayer) {
            recordedPoints.add(loc.clone());

            double speed = currentPlayer.getVelocity().length();
            if (speed < 0.3 && recordedPoints.size() > 10) {
                if (brakingPoints.isEmpty() || brakingPoints.get(brakingPoints.size() - 1).distanceSquared(loc) > 25.0) {
                    brakingPoints.add(loc.clone());
                }
            }

            if (speed > 0.6 && recordedPoints.size() > 10) {
                if (accelerationPoints.isEmpty() || accelerationPoints.get(accelerationPoints.size() - 1).distanceSquared(loc) > 25.0) {
                    accelerationPoints.add(loc.clone());
                }
            }
        }

        /**
         * Finalizes the recording and saves the line (called when the heat ends).
         */
        public void complete() {
            if (completed || cancelled) return;
            completed = true;
            recording = false;

            if (recordingTask != null && !recordingTask.isCancelled()) {
                recordingTask.cancel();
            }

            Player p = getPlayer();

            if (recordedPoints.size() < 5) {
                activeSessions.remove(player.getUniqueId());
                if (p != null && p.isOnline()) {
                    p.sendMessage("§cRecording discarded — too few points recorded.");
                }
                plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Recording discarded for " + player.getName() + " — too few points (" + recordedPoints.size() + ")");
                return;
            }

            AIRacingLine line = racingLineManager.getRacingLine(trackName);
            line.clear();

            int totalPoints = recordedPoints.size();
            for (int i = 0; i < totalPoints; i++) {
                Location loc = recordedPoints.get(i);
                line.addIdealLinePoint(loc, calculateSpeedForPoint(i, totalPoints));
            }

            for (Location brakePoint : brakingPoints) {
                line.addBrakingPoint(brakePoint);
            }
            for (Location accelPoint : accelerationPoints) {
                line.addAccelerationPoint(accelPoint);
            }

            // Save to file (incremental — only this track, async to avoid blocking the tick)
            String finalTrackName = trackName;
            SchedulerHelper.runAsync(plugin, () ->
                racingLineManager.saveRacingLine(finalTrackName, line)
            );

            activeSessions.remove(player.getUniqueId());

            // Chat message for the player
            if (p != null && p.isOnline()) {
                p.sendMessage("");
                p.sendMessage("§a═══════════════════════════════");
                p.sendMessage("§e  ■ Recording Finished!");
                p.sendMessage("");
                p.sendMessage("§f  Track: §b" + trackName);
                p.sendMessage("§f  Recorded points: §a" + recordedPoints.size());
                p.sendMessage("§f  Braking points: §c" + brakingPoints.size());
                p.sendMessage("§f  Acceleration points: §e" + accelerationPoints.size());
                p.sendMessage("§f  Line saved as §b" + trackName);
                p.sendMessage("§a═══════════════════════════════");
            }

            plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Line saved for " + trackName + " with " + recordedPoints.size() + " points");
        }

        private double calculateSpeedForPoint(int index, int totalPoints) {
            if (totalPoints <= 1) {
                return 0.6;
            }

            double position = (double) index / (double) (totalPoints - 1);
            return Math.max(0.35, Math.min(0.95, 0.65 + (0.25 * Math.sin(position * Math.PI * 2))));
        }

        public void cancel() {
            cancelled = true;
            recording = false;
            if (recordingTask != null && !recordingTask.isCancelled()) {
                recordingTask.cancel();
            }
        }

        public boolean isInactiveTooLong() {
            // 5 minutes without activity
            return System.currentTimeMillis() - lastUpdateTime > 300000L;
        }

        public Player getPlayer() {
            if (player != null && player.isOnline()) {
                return player;
            }
            return Bukkit.getPlayer(player.getUniqueId());
        }

        public String getTrackName() {
            return trackName;
        }

        public boolean isRecording() {
            return recording;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public boolean isCompleted() {
            return completed;
        }

        public List<Location> getRecordedPoints() {
            return new ArrayList<>(recordedPoints);
        }
    }
}
