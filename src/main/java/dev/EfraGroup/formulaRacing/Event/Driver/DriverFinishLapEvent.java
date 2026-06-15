package dev.EfraGroup.formulaRacing.Event.Driver;

import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DriverFinishLapEvent extends DriverEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Lap lap;
    private final boolean isFastestLap;

    public DriverFinishLapEvent(Driver driver, Lap lap, boolean isFastestLap) {
        super(driver);
        this.lap = lap;
        this.isFastestLap = isFastestLap;
    }

    public Lap getLap() {
        return this.lap;
    }

    public boolean isFastestLap() {
        return this.isFastestLap;
    }

    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
