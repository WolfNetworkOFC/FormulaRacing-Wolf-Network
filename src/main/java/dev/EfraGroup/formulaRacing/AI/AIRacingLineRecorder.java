package dev.EfraGroup.formulaRacing.AI;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages racing line recording based on the heat timer.
 * Recording starts when the heat transitions to RACING and ends
 * when the heat finishes (FINISHED).
 */
public class AIRacingLineRecorder {

    private final FormulaRacing plugin;
    private final AIRacingLineManager racingLineManager;
    /**
     * Upper bound (blocks/tick) accepted as a plausible boat speed. Blue ice
     * tops out near 3.6; anything beyond 8 is a teleport/chunk-stall artifact
     * and must not become a pace target.
     */
    private static final double MAX_PLAUSIBLE_SPEED = 8.0D;
    private final Map<UUID, RecordingSession> activeSessions;
    private FRTask cleanupTask;

    public AIRacingLineRecorder(FormulaRacing plugin, AIRacingLineManager racingLineManager) {
        this.plugin = plugin;
        this.racingLineManager = racingLineManager;
        // Mutated from region threads (heat state changes) and the cleanup task;
        // a plain HashMap could corrupt or throw CME when complete() removes
        // entries while onHeatFinished() iterates.
        this.activeSessions = new ConcurrentHashMap<>();
        startCleanupTask();
    }

    /**
     * Registers the player as ready to record in the next race.
     * The actual recording starts when the heat goes to RACING.
     */
    public boolean startRecording(Player player, String trackName) {
        UUID uuid = player.getUniqueId();
        if (activeSessions.containsKey(uuid)) {
            player.sendMessage(tr(player, "ai_record_already"));
            return false;
        }

        RecordingSession session = new RecordingSession(player, trackName);
        activeSessions.put(uuid, session);

        player.sendMessage("");
        player.sendMessage(tr(player, "ai_separator_green"));
        player.sendMessage(tr(player, "ai_record_registered_title"));
        player.sendMessage("");
        player.sendMessage(tr(player, "ai_record_registered_track", "{track}", trackName));
        player.sendMessage(tr(player, "ai_record_registered_hint1"));
        player.sendMessage(tr(player, "ai_record_registered_hint2"));
        player.sendMessage("");
        player.sendMessage(tr(player, "ai_record_registered_cancel"));
        player.sendMessage(tr(player, "ai_separator_green"));

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
            player.sendMessage(tr(player, "ai_record_not_recording"));
            return false;
        }

        session.cancel();
        player.sendMessage(tr(player, "ai_record_cancelled"));
        plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Recording cancelled for " + player.getName());
        return true;
    }

    private String tr(Player player, String key, String... placeholders) {
        return plugin.getTranslationUtil().getTranslated(player, key, placeholders);
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
        private final Object recordingLock = new Object();
        private final List<Location> recordedPoints;
        private final List<Double> recordedSpeeds;
        private final long registerTime;
        private long lastUpdateTime;
        private long recordingStartTime;
        private FRTask recordingTask;
        // Read/written from different region threads and the cleanup task.
        private volatile boolean recording;
        private volatile boolean cancelled;
        private volatile boolean completed;

        public RecordingSession(Player player, String trackName) {
            this.player = player;
            this.trackName = trackName == null ? "" : trackName.replace(" ", "").toLowerCase();
            this.recordedPoints = new ArrayList<>();
            this.recordedSpeeds = new ArrayList<>();
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
                p.sendMessage(tr(p, "ai_separator_green"));
                p.sendMessage(tr(p, "ai_record_started_title"));
                p.sendMessage("");
                p.sendMessage(tr(p, "ai_record_started_hint1"));
                p.sendMessage(tr(p, "ai_record_started_hint2"));
                p.sendMessage(tr(p, "ai_separator_green"));
            }

            recordingTask = SchedulerHelper.runTaskTimerAtEntity(plugin, player, () -> {
                if (cancelled || completed || !recording) {
                    return;
                }

                Player currentPlayer = getPlayer();
                if (currentPlayer == null || !currentPlayer.isOnline()) {
                    return;
                }

                // Only record while actually riding a Boat: a driver who crashed,
                // respawned or is walking back would pollute the line with
                // off-track points — and walking speed (~0.2 b/t) normalized
                // against the land surface max (0.1) clamps to 1.0, teaching the
                // AI to take those sections at full speed.
                Entity vehicle = currentPlayer.getVehicle();
                if (!(vehicle instanceof Boat) || !vehicle.isValid()) {
                    return;
                }

                Location currentLoc = currentPlayer.getLocation();
                lastUpdateTime = System.currentTimeMillis();

                if (shouldAddPoint(currentLoc)) {
                    addPoint(currentLoc, currentPlayer);
                }
            }, 1L, 1L);

            plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Recording started for " + player.getName() + " on track " + trackName);
        }

        private boolean shouldAddPoint(Location newLoc) {
            synchronized (recordingLock) {
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

                // Sample every tick and keep points >= 1 block apart: at vanilla
                // ice speeds (~3.6 b/t) the old 2-tick / 2-block sampling left
                // points ~7 blocks apart, too coarse to represent corners.
                return lastPoint.distanceSquared(newLoc) >= 1.0;
            }
        }

        private void addPoint(Location loc, Player currentPlayer) {
            synchronized (recordingLock) {
                recordedPoints.add(loc.clone());

                // Measure the BOAT's velocity, not the passenger's: while riding, the
                // player's own deltaMovement is ~0 (the vehicle moves and repositions the
                // passenger), so recording player.getVelocity() would produce a line whose
                // speeds are all clamped to the 0.1 floor and the AI would crawl.
                double speed = currentPlayer.getVelocity().length();
                if (currentPlayer.getVehicle() != null && currentPlayer.getVehicle().isValid()) {
                    speed = currentPlayer.getVehicle().getVelocity().length();
                }
                recordedSpeeds.add(speed);
                // Braking/acceleration markers are NOT recorded per-tick: raw
                // single-tick speeds are too noisy. They are derived from the
                // smoothed absolute speeds when the recording completes
                // (AIRacingLineManager.deriveMarkersFor).
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

            List<Location> snapshotRecorded;
            List<Double> recordedSpeedsSnapshot;
            synchronized (recordingLock) {
                snapshotRecorded = new ArrayList<>(recordedPoints);
                recordedSpeedsSnapshot = new ArrayList<>(recordedSpeeds);
            }

            if (snapshotRecorded.size() < 5) {
                activeSessions.remove(player.getUniqueId());
                if (p != null && p.isOnline()) {
                    p.sendMessage(tr(p, "ai_record_discarded"));
                }
                plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Recording discarded for " + player.getName() + " — too few points (" + snapshotRecorded.size() + ")");
                return;
            }

            // Build the new line in a LOCAL instance and swap it into the
            // manager only when fully built: clearing the SHARED line mid-race
            // left every AI already running on this track without a usable line
            // (they park) until the rebuild finished.
            AIRacingLine line = new AIRacingLine(trackName);
            // Absolute blocks/tick (v3). The old code normalized each sample by
            // the surface it was recorded on, which is lossless on a MIXED track
            // but destroyed any pace profile on a single-surface track: on pure
            // blue ice the driver did ~3.6 b/t against a 3.6365 b/t max, so
            // every point became 1.0 and the saved line was perfectly flat —
            // the AI could only ever drive one constant speed. Storing the real
            // b/t keeps the driver's actual braking/acceleration points.
            line.setAbsoluteSpeeds(true);

            List<Double> pointSpeeds = smoothSpeeds(recordedSpeedsSnapshot);

            int totalPoints = snapshotRecorded.size();
            for (int i = 0; i < totalPoints; i++) {
                Location loc = snapshotRecorded.get(i);
                line.addIdealLinePoint(loc, pointSpeeds.get(i));
            }

            // The recording spans the WHOLE heat (grid + several laps + a
            // mid-track tail where the heat ended). Trim it to exactly one lap
            // (first → second crossing of the START region) so the line is a
            // closed loop: otherwise the AI's wrap-around target at the end of
            // the line jumps back to the grid and it drives backward.
            boolean closedLap = racingLineManager.trimLineToSingleLap(line, trackName);
            if (!closedLap || !line.isUsable()) {
                // Never replace a known-good racing line with an open recording.
                // getSteerTarget wraps the last point to the first one; on an open
                // line that makes the boat turn back toward the grid or leave the
                // circuit at the end of every lap.
                activeSessions.remove(player.getUniqueId());
                if (p != null && p.isOnline()) {
                    p.sendMessage(tr(p, "ai_record_discarded"));
                }
                plugin.getDebugManager().logRaceSystem(
                        "[AI-RECORDER] Recording discarded for " + player.getName()
                                + " — no complete closed lap was detected; previous line preserved.");
                return;
            }

            // Markers must match the trimmed, closed lap: derive them from the
            // smoothed speeds instead of trusting noisy per-tick samples.
            racingLineManager.deriveMarkersFor(line);

            // Swap the fully-built line into the manager atomically.
            racingLineManager.setRacingLine(trackName, line);

            // Save to file (incremental — only this track, async to avoid blocking the tick)
            String finalTrackName = trackName;
            SchedulerHelper.runAsync(plugin, () ->
                racingLineManager.saveRacingLine(finalTrackName, line)
            );

            activeSessions.remove(player.getUniqueId());

            // Chat message for the player
            if (p != null && p.isOnline()) {
                p.sendMessage("");
                p.sendMessage(tr(p, "ai_separator_green"));
                p.sendMessage(tr(p, "ai_record_finished_title"));
                p.sendMessage("");
                p.sendMessage(tr(p, "ai_record_finished_track", "{track}", trackName));
                p.sendMessage(tr(p, "ai_record_finished_points", "{count}", String.valueOf(line.getIdealLineSize())));
                p.sendMessage(tr(p, "ai_record_finished_braking", "{count}", String.valueOf(line.getBrakingPoints().size())));
                p.sendMessage(tr(p, "ai_record_finished_accel", "{count}", String.valueOf(line.getAccelerationPoints().size())));
                p.sendMessage(tr(p, "ai_record_finished_saved", "{track}", trackName));
                p.sendMessage(tr(p, "ai_separator_green"));
            }

            plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Line saved for " + trackName + " with " + snapshotRecorded.size() + " points");
        }

        /**
         * Applies a 3-point moving average to the raw recorded velocities
         * (blocks/tick) to remove per-tick spikes, and drops impossible samples
         * (a teleport, a chunk stall or a respawn records a huge jump that would
         * otherwise make the AI try to reach it).
         *
         * <p>Speeds are kept ABSOLUTE: dividing by the surface max flattened
         * every single-surface track into a constant 1.0, which is what made the
         * AI hold one speed everywhere. The surface max is still captured per
         * point at record time and is now only used by the runtime to cap the
         * target, not to scale the recording.
         */
        private List<Double> smoothSpeeds(List<Double> rawSpeeds) {
            List<Double> smoothed = new ArrayList<>(rawSpeeds.size());
            for (int i = 0; i < rawSpeeds.size(); i++) {
                int start = Math.max(0, i - 1);
                int end = Math.min(rawSpeeds.size() - 1, i + 1);
                double sum = 0.0;
                int samples = 0;
                for (int j = start; j <= end; j++) {
                    double v = rawSpeeds.get(j);
                    // A believable boat never exceeds ~8 b/t (blue ice tops out
                    // near 3.6); anything above is a recording glitch.
                    if (v >= 0.0 && v <= MAX_PLAUSIBLE_SPEED) {
                        sum += v;
                        samples++;
                    }
                }
                smoothed.add(samples == 0 ? 0.0 : sum / samples);
            }
            return smoothed;
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
            synchronized (recordingLock) {
                return new ArrayList<>(recordedPoints);
            }
        }
    }
}
