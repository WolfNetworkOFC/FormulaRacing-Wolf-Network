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
    private final long displayTimeMillis;
    private final int officialTicks;
    private final java.util.UUID runId;
    private final String timingSource;
    private final boolean isPersonalBest;

    public TimeTrialFinishEvent(Player player, TimeTrialSession session, long totalTimeMillis, boolean isPersonalBest) {
        this(player, session, totalTimeMillis, totalTimeMillis, (int) (totalTimeMillis / 50L), null, "SERVER", isPersonalBest);
    }

    public TimeTrialFinishEvent(
        Player player,
        TimeTrialSession session,
        long totalTimeMillis,
        long displayTimeMillis,
        int officialTicks,
        java.util.UUID runId,
        String timingSource,
        boolean isPersonalBest
    ) {
        this.player = player;
        this.session = session;
        this.totalTimeMillis = totalTimeMillis;
        this.displayTimeMillis = displayTimeMillis;
        this.officialTicks = officialTicks;
        this.runId = runId;
        this.timingSource = timingSource;
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

    public long getDisplayTimeMillis() {
        return this.displayTimeMillis;
    }

    public int getOfficialTicks() {
        return this.officialTicks;
    }

    public java.util.UUID getRunId() {
        return this.runId;
    }

    public String getTimingSource() {
        return this.timingSource;
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
