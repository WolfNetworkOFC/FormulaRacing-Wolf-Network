//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.TimeTrial.Events;

import dev.EfraGroup.formulaRacing.TimeTrial.TimeTrialSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TimeTrialStartEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final TimeTrialSession session;
    private boolean cancelled;

    public TimeTrialStartEvent(Player player, TimeTrialSession session) {
        this.player = player;
        this.session = session;
    }

    public Player getPlayer() {
        return this.player;
    }

    public TimeTrialSession getSession() {
        return this.session;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
