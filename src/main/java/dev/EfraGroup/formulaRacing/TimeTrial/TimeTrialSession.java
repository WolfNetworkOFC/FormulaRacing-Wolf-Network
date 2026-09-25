package dev.EfraGroup.formulaRacing.TimeTrial;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TimeTrialSession {
    private final UUID playerUUID;
    private final String trackName;
    private final Instant startTime;
    private final long startNanos;
    private final UUID runId;
    private final List<Double> checkpointTimes;
    private boolean valid = true;

    public TimeTrialSession(UUID playerUUID, String trackName) {
        this.playerUUID = playerUUID;
        this.trackName = trackName;
        this.startTime = Instant.now();
        this.startNanos = System.nanoTime();
        this.runId = UUID.randomUUID();
        this.checkpointTimes = Collections.synchronizedList(new ArrayList<>());
    }

    public TimeTrialSession(UUID playerUUID, String trackName, Instant startTime) {
        this(
            playerUUID,
            trackName,
            startTime,
            System.nanoTime(),
            UUID.randomUUID()
        );
    }

    public TimeTrialSession(
        UUID playerUUID,
        String trackName,
        Instant startTime,
        long startNanos,
        UUID runId
    ) {
        this.playerUUID = playerUUID;
        this.trackName = trackName;
        this.startTime = startTime;
        this.startNanos = startNanos;
        this.runId = runId == null ? UUID.randomUUID() : runId;
        this.checkpointTimes = Collections.synchronizedList(new ArrayList<>());
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public String getTrackName() {
        return this.trackName;
    }

    public Instant getStartTime() {
        return this.startTime;
    }

    public long getStartNanos() {
        return this.startNanos;
    }

    public UUID getRunId() {
        return this.runId;
    }

    public void addCheckpointTime(double time) {
        this.checkpointTimes.add(time);
    }

    public List<Double> getCheckpointTimes() {
        return Collections.unmodifiableList(this.checkpointTimes);
    }

    public int getCheckpointsPassed() {
        return this.checkpointTimes.size();
    }

    public void invalidate() {
        this.valid = false;
    }

    public boolean isValid() {
        return this.valid;
    }
}