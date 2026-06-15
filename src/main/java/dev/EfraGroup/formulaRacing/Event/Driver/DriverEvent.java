package dev.EfraGroup.formulaRacing.Event.Driver;

import dev.EfraGroup.formulaRacing.Participant.Driver;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public abstract class DriverEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    protected final Driver driver;

    public DriverEvent(Driver driver) {
        this.driver = driver;
    }

    public Driver getDriver() {
        return this.driver;
    }

    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
