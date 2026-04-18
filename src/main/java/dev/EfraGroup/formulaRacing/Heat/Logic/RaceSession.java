//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Event.Driver.DriverNewLapEvent;
import dev.EfraGroup.formulaRacing.Heat.DriverFinishUtils;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Listener.RaceCheckpointListener;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.time.Instant;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class RaceSession implements SessionLogic {
    private final FormulaRacing plugin;

    public RaceSession(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public void start(Heats heat) {
        heat.getPlugin().getDebugManager().logRaceSystem("[RACE DEBUG] Iniciando corrida heat " + heat.getId());
        if (heat.isDrsEnabled()) {
            heat.setupDrs();
            heat.getPlugin().getDRS().startDrsTask(heat);
        }

        if (heat.isPushtopass()) {
            heat.getPlugin().getPTP().startPTPTask(heat);
        }

        if (heat.isErsEnabled()) {
            heat.getPlugin().getERS().startERSTask(heat);
        }

        if (heat.getHeatState() == HeatState.STARTING || heat.getHeatState() == HeatState.LOADED || heat.getHeatState() == HeatState.PRACTICE) {
            heat.setHeatState(HeatState.RACING);
            heat.setStartTime(Instant.now());
            long now = System.currentTimeMillis();

            for(Driver driver : heat.getDrivers().values()) {
                driver.setStartTime(now);
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    heat.clearTimeTrialActionBar(player);
                    heat.stopTimeTrialTimer(player);
                    if (heat.getPlugin().getPacketSender() != null) {
                        heat.getPlugin().getLonelyController().updatePlayersVisibility(player);
                        heat.getPlugin().getPacketSender().applyBoatUtilsToPlayer(player, heat.getTrackNameWS());
                        heat.applyCollisionModeToPlayer(player);
                    }

                    heat.getPlugin().getRaceActionBarManager().removePlayer(player);
                    heat.getPlugin().getRaceActionBarManager().addPlayer(player, heat);
                }
            }

            heat.getGridManager().unfreezePlayers();
            if (heat.getRound() != null && heat.getRound().getEvent() != null) {
                heat.getPlugin().getRaceEventManager().getDatabaseManager().updateHeatTimes(heat.getId(), heat.getStartTime(), (Instant)null);
            }

            heat.startOfflineMonitoring();
            DebugManager var10000 = heat.getPlugin().getDebugManager();
            int var10001 = heat.getId();
            var10000.logRaceSystem("✓ Heat " + var10001 + " iniciado! LARGADA! DRS Enable: " + heat.isDrsEnabled());
        }
    }

    public double calculateGap(Driver current, Driver ahead, Heats heat) {
        int currentLaps = current.getLapCount();
        int currentCP = current.getCheckpointsReached();
        Long currentTime = current.getAbsoluteTimeAtProgress(currentLaps, currentCP);
        Long aheadTime = ahead.getAbsoluteTimeAtProgress(currentLaps, currentCP);
        double finalGap = 99.9;
        if (currentTime != null && aheadTime != null) {
            finalGap = (double)(currentTime - aheadTime) / (double)1000.0F;
            this.plugin.getLogger().info("§7[Gap-Debug] Estático: " + finalGap + "s (Baseado no CP " + currentCP + ")");
        } else {
            this.plugin.getLogger().warning("§c[Gap-Debug] Falha no Estático: currentTime=" + currentTime + ", aheadTime=" + aheadTime);
        }

        if (heat.getPlugin().getTrackIntegrationManager() != null) {
            int totalCheckpoints = heat.getPlugin().getTrackIntegrationManager().getCheckpointCount(heat.getTrackNameWS());
            if (totalCheckpoints > 0) {
                int nextCP = currentCP + 1;
                int nextLapIdx = currentLaps;
                if (nextCP > totalCheckpoints) {
                    nextCP = 1;
                    nextLapIdx = currentLaps + 1;
                }

                Long aheadTimeAtNext = ahead.getAbsoluteTimeAtProgress(nextLapIdx, nextCP);
                if (aheadTimeAtNext != null) {
                    long now = System.currentTimeMillis();
                    double liveDiff = (double)(now - aheadTimeAtNext) / (double)1000.0F;
                    if (liveDiff > finalGap || finalGap == 99.9) {
                        finalGap = liveDiff;
                    }
                }
            }
        }

        if (finalGap < (double)0.0F) {
        }

        return finalGap;
    }

    Driver getDriverAhead(Driver current, Heats heat) {
        int targetPosition = current.getPosition() - 1;
        if (targetPosition < 1) {
            return null;
        } else {
            for(Driver potentialAhead : heat.getDrivers().values()) {
                if (potentialAhead.getPosition() == targetPosition) {
                    return potentialAhead;
                }
            }

            return null;
        }
    }

    public boolean isInside(Location loc, Location min, Location max) {
        if (min != null && max != null) {
            return loc.getX() >= Math.min(min.getX(), max.getX()) && loc.getX() <= Math.max(min.getX(), max.getX()) && loc.getZ() >= Math.min(min.getZ(), max.getZ()) && loc.getZ() <= Math.max(min.getZ(), max.getZ());
        } else {
            return false;
        }
    }

    public boolean passLap(Heats heat, Driver driver) {
        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player == null) {
            return false;
        } else {
            if (heat.getPlugin().getDebugManager().isRaceSystemVerboseEnabled()) {
                heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] passLap chamado para " + player.getName());
            }

            if (driver.getCurrentLap() == null) {
                heat.getPlugin().getDebugManager().logRaceSystem("[RACE] " + player.getName() + " iniciando primeira volta");
                driver.newLap();
                heat.updateLivePositions();
                Bukkit.getPluginManager().callEvent(new DriverNewLapEvent(driver, driver.getCurrentLap()));
                return true;
            } else {
                int totalCheckpoints = heat.getPlugin().getTrackIntegrationManager().getCheckpointCount(heat.getTrackNameWS());
                if (!driver.hasPassedAllCheckpoints(totalCheckpoints)) {
                    if (heat.getPlugin().getDebugManager().isRaceSystemVerboseEnabled()) {
                        heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] " + player.getName() + " NÃO passou por todos os checkpoints!");
                    }

                    heat.getPlugin().sendMessage(player, "timetrial_incomplete_lap", new String[]{"{count}", String.valueOf(driver.getCheckpointsReached()), "{total}", String.valueOf(totalCheckpoints)});
                    return false;
                } else {
                    if (heat.getPlugin().getDebugManager().isRaceSystemVerboseEnabled()) {
                        heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] " + player.getName() + " passou por todos os checkpoints!");
                    }

                    RaceCheckpointListener checkpointListener = heat.getPlugin().getRaceCheckpointListener();
                    if (checkpointListener != null) {
                        if (heat.getPlugin().getDebugManager().isRaceSystemVerboseEnabled()) {
                            heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] Chamando handleLapCompleted...");
                        }

                        driver.finishLap();
                        checkpointListener.handleLapCompleted(driver, heat, player);
                        return true;
                    }

                    if (heat.getTotalLaps() != null && heat.getTotalLaps() > 0 && driver.getLapCount() >= heat.getTotalLaps()) {
                        heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] " + player.getName() + " FINALIZOU a corrida!");
                        boolean finished = DriverFinishUtils.finishDriver(driver, heat, heat.getPlugin());
                        if (finished && heat.getDrivers().values().stream().allMatch((d) -> d.isFinished() || d.isDnf())) {
                            heat.finishHeat();
                        }

                        return true;
                    } else {
                        heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] Iniciando nova volta para " + player.getName());
                        driver.newLap();
                        Bukkit.getPluginManager().callEvent(new DriverNewLapEvent(driver, driver.getCurrentLap()));
                        return true;
                    }
                }
            }
        }
    }

    public boolean passLap(Heats heat, Driver driver, Location from, Location to, RegionBox region) {
        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player == null) {
            return false;
        } else {
            heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] passLap(com localização) chamado para " + player.getName());
            if (driver.getCurrentLap() == null) {
                heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] Primeira volta - inicializando");
                driver.newLap();
                heat.updateLivePositions();
                Bukkit.getPluginManager().callEvent(new DriverNewLapEvent(driver, driver.getCurrentLap()));
                return true;
            } else {
                int totalCheckpoints = heat.getPlugin().getTrackIntegrationManager().getCheckpointCount(heat.getTrackNameWS());
                if (!driver.hasPassedAllCheckpoints(totalCheckpoints)) {
                    heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] " + player.getName() + " NÃO passou por todos os checkpoints!");
                    heat.getPlugin().sendMessage(player, "timetrial_incomplete_lap", new String[]{"{count}", String.valueOf(driver.getCheckpointsReached()), "{total}", String.valueOf(totalCheckpoints)});
                    return false;
                } else {
                    driver.finishLap(from, to, region);
                    RaceCheckpointListener checkpointListener = heat.getPlugin().getRaceCheckpointListener();
                    if (checkpointListener != null) {
                        heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] Chamando handleLapCompleted...");
                        checkpointListener.handleLapCompleted(driver, heat, player);
                        if (!driver.isFinished()) {
                            Bukkit.getPluginManager().callEvent(new DriverNewLapEvent(driver, driver.getCurrentLap()));
                        }

                        return true;
                    } else if (heat.getTotalLaps() != null && heat.getTotalLaps() > 0 && driver.getLapCount() >= heat.getTotalLaps()) {
                        heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] " + player.getName() + " FINALIZOU a corrida!");
                        driver.setFinished(true);
                        heat.updateLivePositions();
                        if (heat.getDrivers().values().stream().allMatch((d) -> d.isFinished() || d.isDnf())) {
                            heat.finishHeat();
                        }

                        return true;
                    } else {
                        heat.getPlugin().getDebugManager().logRaceSystem("[RACE LAP DEBUG] Iniciando nova volta para " + player.getName());
                        driver.newLap();
                        Bukkit.getPluginManager().callEvent(new DriverNewLapEvent(driver, driver.getCurrentLap()));
                        return true;
                    }
                }
            }
        }
    }
}
