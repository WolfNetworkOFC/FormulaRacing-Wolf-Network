package dev.EfraGroup.formulaRacing.Loneliness;

import dev.EfraGroup.formulaRacing.Heat.CollisionMode;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * VisibilityScope adapter for a solo TimeTrial session.
 * <p>
 * TimeTrial is always CollisionMode.DISABLED — every player on the same track
 * is shown to the others (no isolation), but no boat-to-boat collisions occur.
 */
public final class TimeTrialScope implements VisibilityScope {

    private final String trackName;
    private final Set<UUID> participants;

    public TimeTrialScope(String trackName, Set<UUID> participants) {
        this.trackName = trackName;
        this.participants = Collections.unmodifiableSet(participants);
    }

    @Override
    public long getId() {
        return trackName.hashCode();
    }

    @Override
    public Set<UUID> getParticipants() {
        return participants;
    }

    @Override
    public CollisionMode getCollisionMode() {
        return CollisionMode.DISABLED;
    }

    @Override
    public boolean isIsolated() {
        return false;
    }
}
