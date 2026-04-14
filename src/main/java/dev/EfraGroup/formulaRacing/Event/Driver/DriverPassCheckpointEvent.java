package dev.EfraGroup.formulaRacing.Event.Driver;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DriverPassCheckpointEvent extends DriverEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Heats heat;
    private final int checkpointIndex;
    private final int totalCheckpoints;
    private final DatabaseManager.RegionData checkpointRegion;
    private final Location from;
    private final Location to;
    private final long preciseTimeMs;

    public DriverPassCheckpointEvent(Driver driver, Heats heat, int checkpointIndex, int totalCheckpoints, DatabaseManager.RegionData checkpointRegion, Location from, Location to, long preciseTimeMs) {
        super(driver);
        this.heat = heat;
        this.checkpointIndex = checkpointIndex;
        this.totalCheckpoints = totalCheckpoints;
        this.checkpointRegion = checkpointRegion;
        this.from = from;
        this.to = to;
        this.preciseTimeMs = preciseTimeMs;
    }

    public Heats getHeat() {
        return this.heat;
    }

    public int getCheckpointIndex() {
        return this.checkpointIndex;
    }

    public int getTotalCheckpoints() {
        return this.totalCheckpoints;
    }

    public DatabaseManager.RegionData getCheckpointRegion() {
        return this.checkpointRegion;
    }

    public Location getFrom() {
        return this.from;
    }

    public Location getTo() {
        return this.to;
    }

    public long getPreciseTimeMs() {
        return this.preciseTimeMs;
    }

    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
