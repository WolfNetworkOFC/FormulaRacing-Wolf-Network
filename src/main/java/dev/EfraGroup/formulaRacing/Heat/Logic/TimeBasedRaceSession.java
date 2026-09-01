package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatConfig;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Endurance race session: time determines the race length, not laps.
 *
 * <p>When the clock runs out every driver must complete one more lap — the
 * FINAL LAP (endurance style, not F1 checkered-flag): each driver finishes
 * individually when they cross the line on their final lap, and the heat ends
 * when the last active driver has crossed (or DNF'd). The final-lap
 * progression itself lives in {@link Heats#passLap} (see
 * handleEnduranceFinalLap), because lap crossings are processed per call
 * through fresh session instances.
 */
public class TimeBasedRaceSession extends RaceSession {

    private FRTask timeMonitorTask;

    public TimeBasedRaceSession(FormulaRacing plugin) {
        super(plugin);
    }

    @Override
    public void start(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        // The endurance limit reuses the heat's own timeLimit (/heat set
        // timelimit) — the same config qualifying uses, persisted in the DB.
        Integer timeLimit = heat.getTimeLimit();
        if (timeLimit == null || timeLimit <= 0) {
            plugin.getDebugManager().logRaceSystem(
                "[TIME-BASED] Heat " + heat.getId() + " has no timelimit set — falling back to a normal laps race."
            );
            config.setTimeBased(false);
            super.start(heat);
            return;
        }

        // Reset only the RUNTIME flags — never the admin-configured limits.
        config.setLastLapTriggered(false);
        config.setRaceFinishedForAll(false);

        // Endurance: the lap counter no longer ends the race — time does.
        // totalLaps = 0 disables every lap-count based finish check. Runtime
        // only: the DB keeps the admin's original value for normal races.
        if (heat.getTotalLaps() == null || heat.getTotalLaps() > 0) {
            heat.setTotalLapsRuntime(0);
        }

        // Only this session may own the clock during a race: the generic
        // session timer hard-finishes heats on expiry, which would bypass
        // the final-lap rule.
        heat.stopSessionTimer();

        // Normal race start (state, DRS/PTP/ERS, grid release, boat utils).
        super.start(heat);

        plugin.getDebugManager().logRaceSystem(
            "[TIME-BASED] Endurance session started for Heat " + heat.getId() +
            " - Limit: " + timeLimit + "s"
        );

        startTimeMonitoring(heat);
    }

    private void startTimeMonitoring(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        stopTimeMonitoring();

        timeMonitorTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                stopTimeMonitoring();
                return;
            }

            long remainingTime = getTimeRemaining(heat);

            announceTimeWarnings(heat, remainingTime);

            if (remainingTime <= 0 && !config.isLastLapTriggered()) {
                triggerFinalLap(heat);
            }
        }, 20L, 20L);

        plugin.getDebugManager().logRaceSystem("[TIME-BASED] Time monitoring started");
    }

    private long getTimeRemaining(Heats heat) {
        Integer limitSeconds = heat.getTimeLimit();
        if (limitSeconds == null || limitSeconds <= 0) {
            return 0L;
        }
        if (heat.getStartTime() == null) {
            return limitSeconds * 1000L;
        }

        long elapsed = System.currentTimeMillis() - heat.getStartTime().toEpochMilli();
        return Math.max(0L, limitSeconds * 1000L - elapsed);
    }

    private void announceTimeWarnings(Heats heat, long remainingMs) {
        long remainingSeconds = remainingMs / 1000L;

        if (remainingSeconds == 300 || remainingSeconds == 60 || remainingSeconds == 30 ||
            remainingSeconds == 10 || remainingSeconds == 5 ||
            (remainingSeconds <= 3 && remainingSeconds > 0)) {
            for (Driver driver : heat.getDrivers().values()) {
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    heat.getPlugin().sendMessage(player, "endurance_time_warning",
                            new String[]{"{time}", String.valueOf(remainingSeconds)});
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.5F);
                }
            }
            heat.getPlugin().getDebugManager().logRaceSystem(
                "[TIME-BASED] Time warning: " + remainingSeconds + "s remaining"
            );
        }
    }

    /**
     * The clock ran out: everyone completes the lap they are on, then runs one
     * more — the final lap. Progression per driver is handled on their line
     * crossings (Heats.handleEnduranceFinalLap).
     */
    private void triggerFinalLap(Heats heat) {
        HeatConfig config = heat.getHeatConfig();
        config.setLastLapTriggered(true);

        for (Driver driver : heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                String langCode = heat.getPlugin().getDatabaseManager().getPlayerLanguage(player.getUniqueId());
                TitleHelper.sendThemedTitle(player,
                    heat.getPlugin().getTranslation("endurance_final_lap_title", langCode, new String[0]),
                    heat.getPlugin().getTranslation("endurance_final_lap_subtitle", langCode, new String[0]),
                    10, 70, 20);
                heat.getPlugin().sendMessage(player, "endurance_final_lap_chat", new String[0]);
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0F, 0.8F);
            }
        }

        heat.getPlugin().getDebugManager().logRaceSystem(
            "[TIME-BASED] Time is up - final lap triggered for all drivers"
        );
    }

    public void cleanup() {
        stopTimeMonitoring();
    }

    private void stopTimeMonitoring() {
        if (timeMonitorTask != null && !timeMonitorTask.isCancelled()) {
            timeMonitorTask.cancel();
            timeMonitorTask = null;
        }
    }
}
