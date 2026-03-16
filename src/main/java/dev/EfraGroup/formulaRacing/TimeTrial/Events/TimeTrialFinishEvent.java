//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.TimeTrial.Events;

import dev.EfraGroup.formulaRacing.TimeTrial.TimeTrialSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TimeTrialFinishEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final TimeTrialSession session;
    private final long totalTimeMillis;
    private final boolean isPersonalBest;

    public TimeTrialFinishEvent(Player player, TimeTrialSession session, long totalTimeMillis, boolean isPersonalBest) {
        this.player = player;
        this.session = session;
        this.totalTimeMillis = totalTimeMillis;
        this.isPersonalBest = isPersonalBest;
    }

    public Player getPlayer() {
        return this.player;
    }

    public TimeTrialSession getSession() {
        return this.session;
    }

    public long getTotalTimeMillis() {
        return this.totalTimeMillis;
    }

    public boolean isPersonalBest() {
        return this.isPersonalBest;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
