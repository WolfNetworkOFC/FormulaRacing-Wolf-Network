package dev.EfraGroup.formulaRacing.Loneliness;

import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * VisibilityScope adapter for a {@link Heats} instance.
 * Participants = drivers + spectators/streamers bound to the heat.
 */
public final class HeatScope implements VisibilityScope {

    private final Heats heat;
    private final Set<UUID> extraParticipants;

    /** Create a scope with only drivers as participants. */
    public HeatScope(Heats heat) {
        this(heat, Collections.emptySet());
    }

    /** Create a scope that also includes spectators/streamers. */
    public HeatScope(Heats heat, Set<UUID> extraParticipants) {
        this.heat = heat;
        this.extraParticipants = extraParticipants;
    }

    @Override
    public long getId() {
        return heat.getId();
    }

    @Override
    public Set<UUID> getParticipants() {
        Set<UUID> participants = new HashSet<>(heat.getDrivers().keySet());
        participants.addAll(extraParticipants);
        return participants;
    }

    @Override
    public CollisionMode getCollisionMode() {
        CollisionMode mode = heat.getCollisionMode();
        return mode != null ? mode : CollisionMode.HIGH;
    }

    @Override
    public boolean isIsolated() {
        return heat.isLonely();
    }

    @Override
    public Set<UUID> getFinishedParticipants() {
        Set<UUID> finished = new HashSet<>();
        for (Map.Entry<UUID, Driver> entry : heat.getDrivers().entrySet()) {
            Driver driver = entry.getValue();
            if (driver != null && driver.isFinished() && !driver.isDnf()) {
                finished.add(entry.getKey());
            }
        }
        return finished;
    }

    @Override
    public boolean isActiveRacer(UUID uuid) {
        Driver driver = heat.getDriver(uuid);
        return driver != null && !driver.isFinished() && !driver.isDnf();
    }
}
