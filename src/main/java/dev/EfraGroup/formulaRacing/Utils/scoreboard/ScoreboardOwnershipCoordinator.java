package dev.EfraGroup.formulaRacing.Utils.scoreboard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardOwnershipCoordinator {
    public enum Mode {
        TIME_TRIAL(1),
        DUEL(2),
        RACE(3);

        private final int priority;

        Mode(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return this.priority;
        }
    }

    private final Map<UUID, Mode> ownerByPlayer = new ConcurrentHashMap<>();
    private long acquireAttempts;
    private long acquireGranted;
    private long acquireDenied;
    private long ownershipTransitions;
    private long releaseCalls;
    private long clearCalls;

    public synchronized boolean acquire(UUID playerId, Mode mode) {
        this.acquireAttempts++;
        Mode current = this.ownerByPlayer.get(playerId);
        if (current == null || mode.getPriority() >= current.getPriority()) {
            if (current != null && current != mode) {
                this.ownershipTransitions++;
            }
            this.ownerByPlayer.put(playerId, mode);
            this.acquireGranted++;
            return true;
        }
        this.acquireDenied++;
        return false;
    }

    public synchronized boolean isOwner(UUID playerId, Mode mode) {
        return mode == this.ownerByPlayer.get(playerId);
    }

    public synchronized void release(UUID playerId, Mode mode) {
        this.releaseCalls++;
        Mode current = this.ownerByPlayer.get(playerId);
        if (current == mode) {
            this.ownerByPlayer.remove(playerId);
        }
    }

    public synchronized void clear(UUID playerId) {
        this.clearCalls++;
        this.ownerByPlayer.remove(playerId);
    }

    public synchronized String metricsSnapshot() {
        return "acquireAttempts=" + this.acquireAttempts
                + " acquireGranted=" + this.acquireGranted
                + " acquireDenied=" + this.acquireDenied
                + " ownershipTransitions=" + this.ownershipTransitions
                + " releaseCalls=" + this.releaseCalls
                + " clearCalls=" + this.clearCalls
                + " activeOwners=" + this.ownerByPlayer.size();
    }
}
