package dev.EfraGroup.formulaRacing.Loneliness;

import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * VisibilityScope adapter for a {@link TimeTrialDuels.DuelState} instance.
 * CollisionMode is always DISABLED (each player in a duel races solo on the track).
 * When the duel has isLonely()==true the two participants are also hidden from
 * each other, matching the old behaviour.
 */
public final class DuelScope implements VisibilityScope {

    private final int duelId;
    private final Set<UUID> players;
    private final boolean lonely;

    public DuelScope(int duelId, Set<UUID> players, boolean lonely) {
        this.duelId = duelId;
        this.players = Collections.unmodifiableSet(players);
        this.lonely = lonely;
    }

    @Override
    public long getId() {
        return duelId;
    }

    @Override
    public Set<UUID> getParticipants() {
        return players;
    }

    @Override
    public CollisionMode getCollisionMode() {
        return CollisionMode.DISABLED;
    }

    @Override
    public boolean isIsolated() {
        return lonely;
    }
}
