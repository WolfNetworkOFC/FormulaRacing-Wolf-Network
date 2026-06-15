package dev.EfraGroup.formulaRacing.Event.Driver;

import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DriverNewLapEvent extends DriverEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Lap lap;

    public DriverNewLapEvent(Driver driver, Lap lap) {
        super(driver);
        this.lap = lap;
    }

    public Lap getLap() {
        return this.lap;
    }

    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
