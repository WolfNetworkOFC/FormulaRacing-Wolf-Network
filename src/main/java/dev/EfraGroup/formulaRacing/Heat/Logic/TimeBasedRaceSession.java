package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatConfig;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Time-based race session
 * Manages races where time determines the end, not the number of laps
 */
public class TimeBasedRaceSession extends RaceSession {

    private FRTask timeMonitorTask;
    private boolean lastLapAnnounced = false;

    public TimeBasedRaceSession(FormulaRacing plugin) {
        super(plugin);
    }

    @Override
    public void start(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        // Reset runtime states first
        config.reset();

        // Set to time mode
        config.setTimeBased(true);

        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();

        plugin.getDebugManager().logRaceSystem(
            "[TIME-BASED] Time-based session started for Heat " + heat.getId() +
            " - Limit: " + config.getTimeLimitSeconds() + "s"
        );

        // Start time monitoring
        startTimeMonitoring(heat);
    }

    private void startTimeMonitoring(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        stopTimeMonitoring();

        // Optimization: Check every second (20 ticks)
        timeMonitorTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                stopTimeMonitoring();
                return;
            }

            // Optimization: Calculate remaining time efficiently
            long remainingTime = getTimeRemaining(heat);

            // Announce time warnings
            announceTimeWarnings(heat, remainingTime);

            // Check if time is up
            if (remainingTime <= 0 && !config.isLastLapTriggered()) {
                triggerLastLap(heat);
            }

        }, 20L, 20L);

        plugin.getDebugManager().logRaceSystem(
            "[TIME-BASED] Time monitoring started"
        );
    }

    private long getTimeRemaining(Heats heat) {
        if (heat.getStartTime() == null) {
            return heat.getHeatConfig().getTimeLimitSeconds() * 1000L;
        }

        // Optimization: Direct remaining time calculation
        long elapsed = System.currentTimeMillis() - heat.getStartTime().toEpochMilli();
        long limitMs = (long) heat.getHeatConfig().getTimeLimitSeconds() * 1000L;
        return Math.max(0L, limitMs - elapsed);
    }

    private void announceTimeWarnings(Heats heat, long remainingMs) {
        FormulaRacing plugin = heat.getPlugin();
        long remainingSeconds = remainingMs / 1000L;

        // Announce at specific times
        if (remainingSeconds == 60 || remainingSeconds == 30 ||
            remainingSeconds == 10 || remainingSeconds == 5 ||
            (remainingSeconds <= 3 && remainingSeconds > 0)) {

            String message = ChatColor.YELLOW + "⏱ Remaining time: " + ChatColor.WHITE + remainingSeconds + "s";
            Bukkit.broadcastMessage(message);

            plugin.getDebugManager().logRaceSystem(
                "[TIME-BASED] Time warning: " + remainingSeconds + "s remaining"
            );
        }
    }

    private void triggerLastLap(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        config.setLastLapTriggered(true);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "⚠ LAST LAP ⚠");
        Bukkit.broadcastMessage(ChatColor.GRAY + "The leader must cross the finish line to end the race!");
        Bukkit.broadcastMessage("");

        plugin.getDebugManager().logRaceSystem(
            "[TIME-BASED] Last lap triggered - Leader must cross the line"
        );
    }

    public boolean passLap(Heats heat, Driver driver) {
        HeatConfig config = heat.getHeatConfig();

        // If not in time mode, use normal logic
        if (!config.isTimeBased()) {
            return super.passLap(heat, driver);
        }

        // Time logic: check if this is the leader and if last lap was triggered
        if (config.isLastLapTriggered() && !config.isRaceFinishedForAll()) {
            // Check if this is the leader
            Optional<Driver> leaderOpt = getLeader(heat);

            if (leaderOpt.isPresent() && leaderOpt.get().getUuid().equals(driver.getUuid())) {
                // Leader crossed the line - finish race for everyone
                finishRaceForAll(heat);
            }
        }

        return true;
    }

    private Optional<Driver> getLeader(Heats heat) {
        // Optimization: Use efficient stream to find the leader
        return heat.getDrivers().values().stream()
            .filter(d -> !d.isFinished() && !d.isDnf())
            .min((d1, d2) -> {
                // Quick comparison by lap
                int lapCompare = Integer.compare(d2.getLapCount(), d1.getLapCount());
                if (lapCompare != 0) return lapCompare;

                // Comparison by checkpoint
                int cpCompare = Integer.compare(d2.getCheckpointsReached(), d1.getCheckpointsReached());
                if (cpCompare != 0) return cpCompare;

                // Comparison by time (only if needed)
                Long time1 = d1.getAbsoluteTimeAtProgress(d1.getLapCount(), d1.getCheckpointsReached());
                Long time2 = d2.getAbsoluteTimeAtProgress(d2.getLapCount(), d2.getCheckpointsReached());

                if (time1 != null && time2 != null) {
                    return Long.compare(time1, time2);
                }

                return Long.compare(d1.getTotalTime(), d2.getTotalTime());
            });
    }

    private void finishRaceForAll(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        config.setRaceFinishedForAll(true);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GREEN + "🏁 RACE FINISHED 🏁");
        Bukkit.broadcastMessage(ChatColor.GRAY + "All drivers must cross the finish line!");
        Bukkit.broadcastMessage("");

        plugin.getDebugManager().logRaceSystem(
            "[TIME-BASED] Race finished for everyone - Drivers must cross the line"
        );

        // Finalize all drivers who completed the current lap
        heat.getDrivers().values().forEach(driver -> {
            if (!driver.isFinished() && !driver.isDnf()) {
                driver.setFinished(true);
                driver.setEndTime(System.currentTimeMillis());
            }
        });
        heat.updateLivePositions();
        heat.finishHeat();
    }

    private void stopTimeMonitoring() {
        if (timeMonitorTask != null && !timeMonitorTask.isCancelled()) {
            timeMonitorTask.cancel();
            timeMonitorTask = null;
        }
    }

    public void cleanup() {
        stopTimeMonitoring();
        lastLapAnnounced = false;
        timeMonitorTask = null;
    }
}
