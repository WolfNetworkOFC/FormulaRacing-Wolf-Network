//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Event;

import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DriverPassPitEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Driver driver;
    private final Lap lap;
    private final int totalPits;

    public DriverPassPitEvent(Driver driver, Lap lap, int totalPits) {
        this.driver = driver;
        this.lap = lap;
        this.totalPits = totalPits;
    }

    public Driver getDriver() {
        return this.driver;
    }

    public Lap getLap() {
        return this.lap;
    }

    public int getTotalPits() {
        return this.totalPits;
    }

    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
