//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.TimeTrial.Events;

import dev.EfraGroup.formulaRacing.TimeTrial.TimeTrialSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TimeTrialCheckpointEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final TimeTrialSession session;
    private final int checkpointId;
    private final long splitTimeMillis;

    public TimeTrialCheckpointEvent(Player player, TimeTrialSession session, int checkpointId, long splitTimeMillis) {
        this.player = player;
        this.session = session;
        this.checkpointId = checkpointId;
        this.splitTimeMillis = splitTimeMillis;
    }

    public Player getPlayer() {
        return this.player;
    }

    public TimeTrialSession getSession() {
        return this.session;
    }

    public int getCheckpointId() {
        return this.checkpointId;
    }

    public long getSplitTimeMillis() {
        return this.splitTimeMillis;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
