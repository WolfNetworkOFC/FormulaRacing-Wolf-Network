//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Listener.RaceCheckpointListener;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Utils.RegionMathUtils;
import java.time.Instant;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PracticeSession implements SessionLogic {

    public void start(Heats heat) {
        heat
            .getPlugin()
            .getDebugManager()
            .logRaceSystem(
                "[PRACTICE DEBUG] Iniciando treino livre heat " + heat.getId()
            );
        if (heat.isDrsEnabled()) {
            heat.setupDrs();
            heat.getPlugin().getDRS().startDrsTask(heat);
        }

        if (heat.isPushtopass()) {
            heat.getPlugin().getPTP().startPTPTask(heat);
        }

        if (
            heat.getHeatState() == HeatState.LOADED ||
            heat.getHeatState() == HeatState.IDLE ||
            heat.getHeatState() == HeatState.SETUP ||
            heat.getHeatState() == HeatState.STARTING
        ) {
            heat.setHeatState(HeatState.PRACTICE);
            heat.setStartTime(Instant.now());

            for (Driver driver : heat.getDrivers().values()) {
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    heat.clearTimeTrialActionBar(player);
                    heat.stopTimeTrialTimer(player);
                    if (heat.getPlugin().getPacketSender() != null) {
                        heat
                            .getPlugin()
                            .getPacketSender()
                            .applyBoatUtilsToPlayer(
                                player,
                                heat.getTrackNameWS()
                            );
                        heat.applyCollisionModeToPlayer(player);
                    }

                    heat
                        .getPlugin()
                        .getRaceActionBarManager()
                        .removePlayer(player);
                    heat
                        .getPlugin()
                        .getRaceActionBarManager()
                        .addPlayer(player, heat);
                    heat
                        .getPlugin()
                        .getRaceScoreboardManager()
                        .removePlayer(player);
                    heat
                        .getPlugin()
                        .getRaceScoreboardManager()
                        .addPlayer(player, heat);
                }
            }

            heat.getGridManager().unfreezePlayers();
            heat.startSessionTimer();
            if (heat.getRound() != null && heat.getRound().getEvent() != null) {
                heat
                    .getPlugin()
                    .getRaceEventManager()
                    .getDatabaseManager()
                    .updateHeatTimes(
                        heat.getId(),
                        (Instant) null,
                        (Instant) null
                    );
            }

            heat
                .getPlugin()
                .getDebugManager()
                .logRaceSystem("✓ Heat " + heat.getId() + " em TREINO LIVRE!");
        }
    }

    private boolean completeLap(Heats heat, Driver driver, Player player) {
        RaceCheckpointListener checkpointListener = heat
            .getPlugin()
            .getRaceCheckpointListener();
        if (checkpointListener != null) {
            checkpointListener.handleLapCompleted(driver, heat, player);
            return true;
        } else {
            driver.newLap();
            return true;
        }
    }

    public boolean passLap(Heats heat, Driver driver) {
        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player == null) {
            return false;
        }
        if (driver.getCurrentLap() == null) {
            heat.getPlugin().getDebugManager().logRaceSystem(
                "[PRACTICE] " + player.getName() + " iniciando primeira volta"
            );
            driver.setStartTime(System.currentTimeMillis());
            driver.newLap();
            heat.updateLivePositions();
            return true;
        }
        int totalCheckpoints = heat.getPlugin()
            .getTrackIntegrationManager()
            .getCheckpointCount(heat.getTrackNameWS());
        boolean sessionExpired = heat.getSessionTimeRemaining() <= 0L;
        if (!sessionExpired && !driver.hasPassedAllCheckpoints(totalCheckpoints)) {
            heat.getPlugin().sendMessage(
                player, "timetrial_incomplete_lap",
                new String[]{"{count}", String.valueOf(driver.getCheckpointsReached()), "{total}", String.valueOf(totalCheckpoints)}
            );
            return false;
        }
        return completeLap(heat, driver, player);
    }

    public boolean passLap(
        Heats heat,
        Driver driver,
        Location from,
        Location to,
        RegionBox region
    ) {
        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player == null) {
            return false;
        }
        if (driver.getCurrentLap() == null) {
            heat.getPlugin().getDebugManager().logRaceSystem(
                "[PRACTICE] PRECISE START " + player.getName()
            );
            double proportion = RegionMathUtils.calculateRegionEntryProportion(from, to, region);
            long adjustmentMs = (long) ((1.0 - proportion) * 50.0);
            long preciseTime = System.currentTimeMillis() - adjustmentMs;
            driver.setStartTime(preciseTime);
            driver.newLap();
            if (driver.getCurrentLap() != null) {
                driver.getCurrentLap().setLapStart(preciseTime);
            }
            heat.updateLivePositions();
            return true;
        }
        int totalCheckpoints = heat.getPlugin()
            .getTrackIntegrationManager()
            .getCheckpointCount(heat.getTrackNameWS());
        boolean sessionExpired = heat.getSessionTimeRemaining() <= 0L;
        if (!sessionExpired && !driver.hasPassedAllCheckpoints(totalCheckpoints)) {
            heat.getPlugin().sendMessage(
                player, "timetrial_incomplete_lap",
                new String[]{"{count}", String.valueOf(driver.getCheckpointsReached()), "{total}", String.valueOf(totalCheckpoints)}
            );
            return false;
        }
        driver.finishLap(from, to, region);
        return completeLap(heat, driver, player);
    }
}
