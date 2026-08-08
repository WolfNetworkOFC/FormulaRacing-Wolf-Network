package dev.EfraGroup.formulaRacing.Loneliness;

import dev.EfraGroup.formulaRacing.Heat.CollisionMode;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an active racing session that groups participants together for
 * visibility and collision purposes.
 * <p>
 * This is the core abstraction that replaces the old Domain-based approach.
 * Heat, Duel and TimeTrial sessions all implement this interface so the
 * LonelyController can apply a single, consistent policy.
 */
public interface VisibilityScope {

    /**
     * Unique identifier for this scope instance.
     */
    long getId();

    /**
     * All participants that should be visible to each other while inside this scope.
     * Includes drivers, duelers, spectators bound to the session, etc.
     */
    Set<UUID> getParticipants();

    /**
     * Collision mode to apply for players inside this scope.
     */
    CollisionMode getCollisionMode();

    /**
     * Whether this session is in "lonely" isolation mode (every participant is
     * hidden from every other participant regardless of CollisionMode).
     */
    boolean isIsolated();

    /**
     * Returns participant UUIDs who have finished their session and should be
     * hidden from active (non-finished) racers in the same scope.
     * <p>
     * Only applies to heat/race scopes. Defaults to empty for duel/timetrial/open-world.
     */
    default Set<UUID> getFinishedParticipants() {
        return Collections.emptySet();
    }

    /**
     * Returns true if the given UUID belongs to an active (non-finished) driver
     * in this scope (not a spectator, not a finished driver).
     */
    default boolean isActiveRacer(UUID uuid) {
        return false;
    }
}
