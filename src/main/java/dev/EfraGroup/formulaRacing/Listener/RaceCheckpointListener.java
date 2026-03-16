//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Event.EventAnnouncements;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public class RaceCheckpointListener implements Listener {
    private final FormulaRacing plugin;
    private final RaceEventManager raceEventManager;
    private final TrackIntegrationManager trackManager;

    public RaceCheckpointListener(FormulaRacing plugin) {
        this.plugin = plugin;
        this.raceEventManager = plugin.getRaceEventManager();
        this.trackManager = plugin.getTrackIntegrationManager();
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!vehicle.getPassengers().isEmpty()) {
            Object var4 = vehicle.getPassengers().get(0);
            if (var4 instanceof Player) {
                Player player = (Player)var4;
                UUID var14 = player.getUniqueId();
                Heats currentHeat = null;
                Driver driver = null;

                for(Events raceEvent : this.raceEventManager.getAllEvents()) {
                    for(Rounds round : raceEvent.getEventSchedule().getRounds().values()) {
                        Optional<Heats> activeHeatOpt = round.getActiveHeat();
                        if (!activeHeatOpt.isEmpty()) {
                            Heats heat = (Heats)activeHeatOpt.get();
                            if (heat.getHeatState() == HeatState.RACING || heat.getHeatState() == HeatState.PRACTICE || heat.getHeatState() == HeatState.QUALIFYING) {
                                Driver d = heat.getDriver(var14);
                                if (d != null) {
                                    currentHeat = heat;
                                    driver = d;
                                    break;
                                }
                            }
                        }
                    }

                    if (currentHeat != null) {
                        break;
                    }
                }

                if (currentHeat != null && driver != null) {
                    if (!driver.isFinished()) {
                        String trackNameWS = currentHeat.getTrackNameWS();
                        if (trackNameWS != null) {
                            List<DatabaseManager.RegionData> checkpoints = this.trackManager.getTrackCheckpoints(trackNameWS);
                            if (!checkpoints.isEmpty()) {
                                Location playerLoc = player.getLocation();
                                int totalCheckpoints = checkpoints.size();
                                int nextExpectedCheckpoint = driver.getCheckpointsReached();
                                if (nextExpectedCheckpoint < totalCheckpoints) {
                                    DatabaseManager.RegionData expectedCheckpoint = (DatabaseManager.RegionData)checkpoints.get(nextExpectedCheckpoint);
                                    if (this.trackManager.isPlayerInCheckpoint(playerLoc, expectedCheckpoint)) {
                                        this.handleCheckpointReached(driver, currentHeat, nextExpectedCheckpoint, totalCheckpoints, player);
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleCheckpointReached(Driver driver, Heats heat, int checkpointId, int totalCheckpoints, Player player) {
        driver.incrementCheckpoint();
        this.plugin.getDebugManager().logRaceSystem(String.format("§e[CHECKPOINT] %s passou por CP%d - Progresso: %d/%d", player.getName(), checkpointId, driver.getCheckpointsReached(), totalCheckpoints));
        this.updateDelta(driver, player);
        heat.updateLivePositions();
    }

    private void updateDelta(Driver driver, Player player) {
        Map<Integer, Long> bestCheckpoints = driver.getBestLapCheckpointTimes();
        if (bestCheckpoints != null && !bestCheckpoints.isEmpty()) {
            Lap currentLap = driver.getCurrentLap();
            if (currentLap != null) {
                int lastCheckpointId = driver.getCheckpointsReached();
                if (lastCheckpointId > 0) {
                    if (lastCheckpointId != driver.getLastProcessedCheckpointId()) {
                        driver.setLastProcessedCheckpointId(lastCheckpointId);
                        if (!bestCheckpoints.containsKey(lastCheckpointId)) {
                            driver.setCachedDelta("");
                        } else {
                            Long currentTime = currentLap.getRelativeCheckpointTime(lastCheckpointId);
                            Long bestTime = (Long)bestCheckpoints.get(lastCheckpointId);
                            if (currentTime != null && bestTime != null) {
                                double deltaSeconds = (double)(currentTime - bestTime) / (double)1000.0F;
                                String deltaStr;
                                if (Math.abs(deltaSeconds) < 0.001) {
                                    deltaStr = " §e±0.000";
                                } else if (deltaSeconds < (double)0.0F) {
                                    deltaStr = String.format(" §a%.3f", deltaSeconds);
                                } else {
                                    deltaStr = String.format(" §c+%.3f", deltaSeconds);
                                }

                                driver.setCachedDelta(deltaStr);
                            }
                        }
                    }
                }
            }
        } else {
            driver.setCachedDelta("");
        }
    }

    public void handleLapCompleted(Driver driver, Heats heat, Player player) {
        Lap currentLap = driver.getCurrentLap();
        if (currentLap == null) {
            this.plugin.getDebugManager().logRaceSystem("§c[LAP ERROR] " + player.getName() + " completou volta mas currentLap é null!");
        } else if (driver.getLaps().isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("§c[LAP ERROR] " + player.getName() + " completou volta mas lista de laps está vazia!");
        } else {
            Lap completedLap = (Lap)driver.getLaps().get(driver.getLaps().size() - 1);
            if (heat.getId() > 0) {
                this.plugin.getRaceEventManager().getDatabaseManager().createLap(driver.getUuid(), heat.getId(), heat.getTrackNameWS(), completedLap.getStartTime(), completedLap.getLapEnd(), completedLap.isPitted());
            }

            this.checkFastestLap(driver, completedLap, heat, player);
            String lapTime = this.formatTime(completedLap.getLapTime());
            int lapNumber = driver.getLapCount();
            String deltaStr = "";
            Lap personalBest = driver.getFastestLap();
            long comparisonTime = -1L;
            if (personalBest != null && personalBest.equals(completedLap)) {
                comparisonTime = driver.getLaps().stream().filter((l) -> l != completedLap && l.getLapEnd() > 0L).mapToLong(Lap::getLapTime).min().orElse(-1L);
            } else if (personalBest != null) {
                comparisonTime = personalBest.getLapTime();
            }

            if (comparisonTime > 0L) {
                long diff = completedLap.getLapTime() - comparisonTime;
                double diffSeconds = (double)diff / (double)1000.0F;
                if (diffSeconds < (double)0.0F) {
                    deltaStr = String.format("-%.3fs", Math.abs(diffSeconds));
                } else {
                    deltaStr = String.format("+%.3fs", diffSeconds);
                }
            }

            EventAnnouncements announcements = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getAnnouncements() : this.plugin.getEventAnnouncements();
            announcements.broadcastLapTime(heat, driver, lapTime, deltaStr);
            if (heat.getHeatState() == HeatState.QUALIFYING || heat.getHeatState() == HeatState.PRACTICE) {
                boolean timeExpired = heat.getSessionTimeRemaining() <= 0L;
                boolean lapsComplete = heat.getTotalLaps() != null && heat.getTotalLaps() > 0 && driver.getLapCount() >= heat.getTotalLaps();
                if (timeExpired || lapsComplete) {
                    if (timeExpired) {
                        this.plugin.sendMessage(player, "race_session_ended", new String[0]);
                    } else {
                        this.plugin.sendMessage(player, "race_laps_complete", new String[0]);
                    }

                    this.handleDriverFinished(driver, heat, player);
                    return;
                }
            }

            driver.newLap();
            if (heat.getHeatState() == HeatState.RACING && heat.getTotalPits() > 0) {
                int pitsRemaining = heat.getTotalPits() - driver.getPitstops();
                if (pitsRemaining > 0) {
                    this.plugin.sendMessage(player, "race_pitstops_remaining", new String[]{"{count}", String.valueOf(pitsRemaining)});
                } else {
                    this.plugin.sendMessage(player, "race_pitstops_complete", new String[0]);
                }
            }

            if (heat.getHeatState() == HeatState.RACING && driver.getLapCount() >= heat.getTotalLaps()) {
                this.handleDriverFinished(driver, heat, player);
            }

            this.plugin.getDebugManager().logRaceSystem(String.format("§a[LAP] %s completou volta %d em %s", player.getName(), lapNumber, lapTime));
        }
    }

    private void checkFastestLap(Driver driver, Lap lap, Heats heat, Player player) {
        Lap personalBest = driver.getFastestLap();
        if (personalBest != null && lap.getLapTime() <= personalBest.getLapTime()) {
            driver.setBestLapCheckpointTimes(lap.getRelativeCheckpointTimes());
            this.plugin.getDebugManager().logRaceSystem(player.getName() + " atualizou checkpoints do PB para sistema de delta");
        }

        UUID currentFastest = heat.getFastestLapUUID();
        EventAnnouncements announcements = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getAnnouncements() : this.plugin.getEventAnnouncements();
        if (currentFastest == null) {
            heat.setFastestLapUUID(driver.getUuid());
            announcements.broadcastFastestLap(heat, driver, this.formatTime(lap.getLapTime()));
            if (heat.getId() > 0) {
                this.plugin.getRaceEventManager().getDatabaseManager().updateHeatFastestLap(heat.getId(), driver.getUuid());
            }

        } else {
            Driver fastestDriver = heat.getDriver(currentFastest);
            if (fastestDriver != null) {
                if (currentFastest.equals(driver.getUuid())) {
                    long secondBestTime = driver.getLaps().stream().filter((l) -> l != lap && l.getLapEnd() > 0L).mapToLong(Lap::getLapTime).min().orElse(Long.MAX_VALUE);
                    if (lap.getLapTime() < secondBestTime) {
                        announcements.broadcastFastestLap(heat, driver, this.formatTime(lap.getLapTime()));
                        if (heat.getId() > 0) {
                            this.plugin.getRaceEventManager().getDatabaseManager().updateHeatFastestLap(heat.getId(), driver.getUuid());
                        }
                    }

                } else {
                    Lap fastestLap = fastestDriver.getFastestLap();
                    if (fastestLap != null) {
                        if (lap.getLapTime() < fastestLap.getLapTime()) {
                            heat.setFastestLapUUID(driver.getUuid());
                            announcements.broadcastFastestLap(heat, driver, this.formatTime(lap.getLapTime()));
                            if (heat.getId() > 0) {
                                this.plugin.getRaceEventManager().getDatabaseManager().updateHeatFastestLap(heat.getId(), driver.getUuid());
                            }
                        }

                    }
                }
            }
        }
    }

    private void handleDriverFinished(Driver driver, Heats heat, Player player) {
        long finishTime = System.currentTimeMillis();
        driver.setEndTime(finishTime);
        if (heat.getTotalPits() > 0 && driver.getPitstops() < heat.getTotalPits()) {
            driver.setDnf(true);
            driver.setFinished(false);
            EventAnnouncements announcements = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getAnnouncements() : this.plugin.getEventAnnouncements();
            int var10000 = driver.getPitstops();
            String dnfReason = "Pit stops: " + var10000 + "/" + heat.getTotalPits();
            announcements.broadcastDNF(heat, driver, dnfReason);
            if (heat.getId() > 0 && driver.getId() > 0) {
                this.plugin.getRaceEventManager().getDatabaseManager().updateDriverTimes(driver.getId(), Instant.ofEpochMilli(driver.getStartTime()), Instant.ofEpochMilli(finishTime));
            }

        } else {
            driver.setFinished(true);
            int position = (int)heat.getDrivers().values().stream().filter(Driver::isFinished).filter((d) -> !d.isDnf()).count();
            driver.setPosition(position);
            EventAnnouncements announcements = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getAnnouncements() : this.plugin.getEventAnnouncements();
            announcements.broadcastFinish(heat, driver, this.formatTime(driver.getTotalTime()));
            if (heat.getId() > 0 && driver.getId() > 0) {
                this.plugin.getRaceEventManager().getDatabaseManager().updateDriverTimes(driver.getId(), Instant.ofEpochMilli(driver.getStartTime()), Instant.ofEpochMilli(driver.getEndTime()));
                this.plugin.getRaceEventManager().getDatabaseManager().updateDriverPosition(driver.getId(), position);
            }

            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                if (player.isOnline()) {
                    if (player.getVehicle() != null) {
                        player.leaveVehicle();
                    }

                    this.plugin.getAPI().releaseBoat(player);
                    if (this.plugin.getPacketSender() != null) {
                        this.plugin.getPacketSender().resetBoatUtilsToVanilla(player);
                        boolean dbLonely = this.plugin.getDatabaseManager().getLonelyModePlayer(player.getUniqueId());
                        this.plugin.getLonelyController().setLonelyMode(player, dbLonely);
                    }

                    this.teleportToSpectatorArea(player, heat);
                }

            }, 60L);
            this.checkAllFinished(heat);
        }
    }

    private void checkAllFinished(Heats heat) {
        for(Driver driver : heat.getDrivers().values()) {
            if (!driver.isFinished() && !driver.isDnf()) {
                Player player = this.plugin.getServer().getPlayer(driver.getUuid());
                if (player == null || !player.isOnline()) {
                    this.plugin.getDebugManager().logRaceSystem("Driver " + String.valueOf(driver.getUuid()) + " está offline - marcando como DNF");
                    driver.setDnf(true);
                    Events event = heat.getRound() != null ? heat.getRound().getEvent() : null;
                    EventAnnouncements announcements = event != null ? event.getAnnouncements() : this.plugin.getEventAnnouncements();
                    announcements.broadcastDNF(heat, driver, "Disconnected");
                }
            }
        }

        boolean allFinished = heat.getDrivers().values().stream().allMatch((driverx) -> driverx.isFinished() || driverx.isDnf());
        if (allFinished) {
            this.plugin.getDebugManager().logRaceSystem("Todos os pilotos finalizaram ou foram marcados como DNF no Heat " + heat.getId());
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                heat.finishHeat();
                Events event = heat.getRound() != null ? heat.getRound().getEvent() : null;
                EventAnnouncements announcements = event != null ? event.getAnnouncements() : this.plugin.getEventAnnouncements();
                announcements.broadcastHeatComplete(heat);
            }, 100L);
        }

    }

    private String getPlayerName(UUID uuid) {
        Player player = this.plugin.getServer().getPlayer(uuid);
        return player != null ? player.getName() : this.plugin.getServer().getOfflinePlayer(uuid).getName();
    }

    private void teleportToSpectatorArea(Player player, Heats heat) {
        String trackNameWS = heat.getTrackNameWS();
        if (trackNameWS != null) {
            DatabaseManager db = this.plugin.getDatabaseManager();
            Driver driver = heat.getDriver(player.getUniqueId());
            if (driver != null) {
                Location finishPosLoc = db.getTrackFinishPos(trackNameWS, driver.getPosition());
                if (finishPosLoc != null) {
                    player.teleport(finishPosLoc);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
                    String title = "§6§l" + driver.getPosition() + "º LUGAR!";
                    player.sendTitle("§a§lRACE FINISHED", title, 10, 80, 20);
                    return;
                }
            }

            Location finishAllLoc = db.getTrackFinishAll(trackNameWS);
            if (finishAllLoc != null) {
                player.teleport(finishAllLoc);
                this.plugin.sendMessage(player, "race_teleport_stand", new String[0]);
            }
        }
    }

    private String formatTime(long timeMs) {
        long minutes = timeMs / 60000L;
        long seconds = timeMs % 60000L / 1000L;
        long millis = timeMs % 1000L;
        return minutes > 0L ? String.format("%d:%02d.%03d", minutes, seconds, millis) : String.format("%d.%03d", seconds, millis);
    }
}
