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
    /** LAGSTART/LAGEND crossings, used to prove the player physically crossed the line. */
    private boolean passedLagStart;
    private boolean passedLagEnd;
    private long lagStartNanos;
    private long lagEndNanos;

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

    /**
     * Records a LAGSTART crossing. The instant is kept, not just the flag: a same-instant
     * pair of LAGSTART/LAGEND means the player never actually moved between them.
     */
    public void markLagStart(long crossingNanos) {
        this.passedLagStart = true;
        this.lagStartNanos = crossingNanos;
    }

    public void markLagEnd(long crossingNanos) {
        this.passedLagEnd = true;
        this.lagEndNanos = crossingNanos;
    }

    public boolean hasPassedLagStart() {
        return this.passedLagStart;
    }

    public boolean hasPassedLagEnd() {
        return this.passedLagEnd;
    }

    /**
     * True when both regions were stamped at the same instant, which cannot happen at
     * boat speed across distinct blocks. That is the teleport / not-really-moved case.
     * Returns false when only one was reached, since a missing region is already
     * handled by the presence checks.
     */
    public boolean lagRegionsShareInstant() {
        return this.passedLagStart && this.passedLagEnd && this.lagStartNanos == this.lagEndNanos;
    }
}