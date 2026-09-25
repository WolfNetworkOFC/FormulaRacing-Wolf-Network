package dev.EfraGroup.formulaRacing.TimeTrial.Timing;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SoloTimingAttempt {

    public enum State {
        ARMED,
        RUNNING,
        FINISH_PENDING,
        FINALIZED,
        ABORTED
    }

    private static final long MAX_DURATION_NANOS = 86_400_000_000_000L;

    private final UUID runId;
    private final String trackName;
    private State state;
    private long lastSequence = -1L;
    private Long clientStartNanos;
    private Long clientFinishNanos;
    private long startNanos;
    private long finishNanos;
    private final CompletableFuture<OfficialTime> resolution = new CompletableFuture<>();

    public SoloTimingAttempt(UUID runId, String trackName) {
        this.runId = runId;
        this.trackName = trackName;
        this.state = State.ARMED;
    }

    public synchronized UUID getRunId() {
        return this.runId;
    }

    public synchronized String getTrackName() {
        return this.trackName;
    }

    public synchronized State getState() {
        return this.state;
    }

    public synchronized long getLastSequence() {
        return this.lastSequence;
    }

    public synchronized Long getClientStartNanos() {
        return this.clientStartNanos;
    }

    public synchronized Long getClientFinishNanos() {
        return this.clientFinishNanos;
    }

    public synchronized long getStartNanos() {
        return this.startNanos;
    }

    public synchronized long getFinishNanos() {
        return this.finishNanos;
    }

    public synchronized long getServerElapsedMillis() {
        return this.startNanos <= 0L
            ? 0L
            : Math.max(0L, this.finishNanos - this.startNanos) / 1_000_000L;
    }

    public synchronized CompletableFuture<OfficialTime> getResolution() {
        return this.resolution;
    }

    public synchronized boolean isActive() {
        return this.state == State.ARMED || this.state == State.RUNNING || this.state == State.FINISH_PENDING;
    }

    public synchronized boolean tryStart(long startNanos) {
        if (this.state != State.ARMED) {
            return false;
        }
        this.startNanos = startNanos;
        this.state = State.RUNNING;
        return true;
    }

    public synchronized boolean acceptClientStart(long sequence, long startNanos) {
        if (
            (this.state != State.ARMED && this.state != State.RUNNING)
                || sequence <= this.lastSequence
                || startNanos <= 0L
        ) {
            return false;
        }
        this.lastSequence = sequence;
        this.clientStartNanos = startNanos;
        return true;
    }

    public synchronized boolean acceptClientFinish(
        long sequence,
        long startNanos,
        long finishNanos
    ) {
        if (
            (this.state != State.ARMED
                && this.state != State.RUNNING
                && this.state != State.FINISH_PENDING)
                || this.clientStartNanos == null
                || sequence <= this.lastSequence
                || startNanos <= 0L
                || finishNanos <= startNanos
                || finishNanos - startNanos > MAX_DURATION_NANOS
        ) {
            return false;
        }
        if (this.clientStartNanos != null && Math.abs(this.clientStartNanos - startNanos) > 1_000_000L) {
            return false;
        }
        this.lastSequence = sequence;
        this.clientStartNanos = startNanos;
        this.clientFinishNanos = finishNanos;
        return true;
    }

    public synchronized boolean tryFinishPending(long finishNanos) {
        if (this.state != State.RUNNING) {
            return false;
        }
        this.finishNanos = finishNanos;
        this.state = State.FINISH_PENDING;
        return true;
    }

    public synchronized boolean tryFinalize() {
        if (this.state != State.FINISH_PENDING) {
            return false;
        }
        this.state = State.FINALIZED;
        return true;
    }

    public synchronized void abort() {
        if (this.state != State.FINALIZED) {
            this.state = State.ABORTED;
        }
    }
}
