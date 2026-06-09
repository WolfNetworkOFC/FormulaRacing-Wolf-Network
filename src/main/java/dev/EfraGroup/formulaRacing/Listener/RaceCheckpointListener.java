//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Event.Driver.DriverPassCheckpointEvent;
import dev.EfraGroup.formulaRacing.Event.EventAnnouncements;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Participant.DriverLookup;
import dev.EfraGroup.formulaRacing.Utils.RegionMathUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
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
    private final DriverLookup driverLookup;
    private final Map<UUID, Long> lastCheckpointSkip =
        new ConcurrentHashMap<>();
    private static final long CHECKPOINT_SKIP_COOLDOWN_MS = 2000L;

    public void cleanupPlayer(UUID uuid) {
        this.lastCheckpointSkip.remove(uuid);
    }

    public void cleanupHeatPlayers(java.util.Collection<UUID> uuids) {
        for (UUID uuid : uuids) {
            this.lastCheckpointSkip.remove(uuid);
        }
    }

    public RaceCheckpointListener(FormulaRacing plugin) {
        this.plugin = plugin;
        this.raceEventManager = plugin.getRaceEventManager();
        this.trackManager = plugin.getTrackIntegrationManager();
        this.driverLookup = plugin.getDriverLookup();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!vehicle.getPassengers().isEmpty()) {
            Object var4 = vehicle.getPassengers().get(0);
            if (var4 instanceof Player) {
                Player player = (Player) var4;
                UUID playerUUID = player.getUniqueId();
                Heats currentHeat = this.driverLookup.getHeat(playerUUID);
                Driver driver =
                    currentHeat != null
                        ? this.driverLookup.getDriver(playerUUID)
                        : null;

                if (currentHeat != null && driver != null) {
                    HeatState heatState = currentHeat.getHeatState();
                    if (
                        heatState != HeatState.RACING &&
                        heatState != HeatState.PRACTICE &&
                        heatState != HeatState.QUALIFYING
                    ) {
                        return;
                    }
                    if (!driver.isFinished()) {
                        String trackNameWS = currentHeat.getTrackNameWS();
                        if (trackNameWS != null) {
                            Map<
                                Integer,
                                List<DatabaseManager.RegionData>
                            > checkpointsById =
                                this.trackManager.getCheckpointsById(
                                    trackNameWS
                                );
                            if (!checkpointsById.isEmpty()) {
                                int totalCheckpoints =
                                    this.trackManager.getCheckpointCount(
                                        trackNameWS
                                    );
                                Location from = event.getFrom();
                                Location to = event.getTo();
                                if (
                                    this.trackManager.hasLagStartRegion(
                                        trackNameWS
                                    ) &&
                                    !driver.hasPassedLagStart()
                                ) {
                                    List<
                                        DatabaseManager.RegionData
                                    > lagStartRegions =
                                        this.trackManager.getTrackRegionsByType(
                                            trackNameWS,
                                            "LAGSTART"
                                        );
                                    for (DatabaseManager.RegionData lagStartRegion : lagStartRegions) {
                                        if (
                                            RegionMathUtils.intersectsRegion(
                                                from,
                                                to,
                                                lagStartRegion
                                            )
                                        ) {
                                            driver.setLagStartPassed(true);
                                            this.plugin.getDebugManager().logRaceSystem(
                                                "§a[LAG] " +
                                                    player.getName() +
                                                    " passou LAGSTART"
                                            );
                                            break;
                                        }
                                    }
                                }
                                if (
                                    this.trackManager.hasLagEndRegion(
                                        trackNameWS
                                    ) &&
                                    !driver.hasPassedLagEnd()
                                ) {
                                    List<
                                        DatabaseManager.RegionData
                                    > lagEndRegions =
                                        this.trackManager.getTrackRegionsByType(
                                            trackNameWS,
                                            "LAGEND"
                                        );
                                    for (DatabaseManager.RegionData lagEndRegion : lagEndRegions) {
                                        if (
                                            RegionMathUtils.intersectsRegion(
                                                from,
                                                to,
                                                lagEndRegion
                                            )
                                        ) {
                                            driver.setLagEndPassed(true);
                                            this.plugin.getDebugManager().logRaceSystem(
                                                "§a[LAG] " +
                                                    player.getName() +
                                                    " passou LAGEND"
                                            );
                                            break;
                                        }
                                    }
                                }
                                int checkpointsReached =
                                    driver.getCheckpointsReached();
                                int nextExpectedZeroBased = checkpointsReached;
                                int nextExpectedOneBased =
                                    checkpointsReached + 1;

                                List<
                                    DatabaseManager.RegionData
                                > expectedRegions = new ArrayList<>();
                                if (nextExpectedZeroBased < totalCheckpoints) {
                                    List<
                                        DatabaseManager.RegionData
                                    > zeroBasedRegions = checkpointsById.get(
                                        nextExpectedZeroBased
                                    );
                                    if (zeroBasedRegions != null) {
                                        expectedRegions.addAll(
                                            zeroBasedRegions
                                        );
                                    }
                                }
                                if (nextExpectedOneBased <= totalCheckpoints) {
                                    List<
                                        DatabaseManager.RegionData
                                    > oneBasedRegions = checkpointsById.get(
                                        nextExpectedOneBased
                                    );
                                    if (oneBasedRegions != null) {
                                        expectedRegions.addAll(oneBasedRegions);
                                    }
                                }

                                for (DatabaseManager.RegionData expectedRegion : expectedRegions) {
                                    if (
                                        RegionMathUtils.isEnteringRegion(
                                            from,
                                            to,
                                            expectedRegion
                                        )
                                    ) {
                                        int matchedCheckpointId =
                                            expectedRegion.getId();
                                        this.handleCheckpointReached(
                                            driver,
                                            currentHeat,
                                            matchedCheckpointId,
                                            totalCheckpoints,
                                            player,
                                            from,
                                            to,
                                            expectedRegion
                                        );
                                        return;
                                    }
                                }

                                for (Map.Entry<
                                    Integer,
                                    List<DatabaseManager.RegionData>
                                > entry : checkpointsById.entrySet()) {
                                    int checkpointId = entry.getKey();
                                    if (
                                        checkpointId == nextExpectedZeroBased ||
                                        checkpointId == nextExpectedOneBased
                                    ) {
                                        continue;
                                    }

                                    List<
                                        DatabaseManager.RegionData
                                    > skipRegions = entry.getValue();
                                    if (skipRegions == null) continue;

                                    for (DatabaseManager.RegionData skipRegion : skipRegions) {
                                        if (
                                            RegionMathUtils.isEnteringRegion(
                                                from,
                                                to,
                                                skipRegion
                                            )
                                        ) {
                                            UUID playerUuid =
                                                player.getUniqueId();
                                            long now =
                                                System.currentTimeMillis();
                                            Long lastSkip =
                                                this.lastCheckpointSkip.get(
                                                    playerUuid
                                                );
                                            if (
                                                lastSkip != null &&
                                                now - lastSkip <
                                                CHECKPOINT_SKIP_COOLDOWN_MS
                                            ) {
                                                return;
                                            }
                                            this.handleCheckpointSkipped(
                                                driver,
                                                currentHeat,
                                                player,
                                                checkpointId,
                                                nextExpectedOneBased
                                            );
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleCheckpointReached(
        Driver driver,
        Heats heat,
        int checkpointId,
        int totalCheckpoints,
        Player player,
        Location from,
        Location to,
        DatabaseManager.RegionData checkpointRegion
    ) {
        double proportion = RegionMathUtils.calculateRegionEntryProportion(
            from,
            to,
            checkpointRegion
        );
        long tickDurationMs = 50L;
        long adjustmentMs = (long) (((double) 1.0F - proportion) *
            (double) tickDurationMs);
        long preciseTimeMs = System.currentTimeMillis() - adjustmentMs;
        driver.incrementCheckpoint();
        this.plugin.getDebugManager().logRaceSystem(
            String.format(
                "§e[CHECKPOINT] %s passou por CP%d - Progresso: %d/%d",
                player.getName(),
                checkpointId,
                driver.getCheckpointsReached(),
                totalCheckpoints
            )
        );
        DriverPassCheckpointEvent event = new DriverPassCheckpointEvent(
            driver,
            heat,
            checkpointId,
            totalCheckpoints,
            checkpointRegion,
            from.clone(),
            to.clone(),
            preciseTimeMs
        );
        Bukkit.getPluginManager().callEvent(event);
        this.updateDelta(driver, player);
        heat.updateLivePositions();
    }

    private void handleCheckpointSkipped(
        Driver driver,
        Heats heat,
        Player player,
        int detectedCheckpointId,
        int expectedCheckpointId
    ) {
        this.plugin.getDebugManager().logRaceSystem(
            String.format(
                "§c[CHECKPOINT SKIP] %s saltou CP%d (esperado CP%d) - Resetando",
                player.getName(),
                detectedCheckpointId,
                expectedCheckpointId
            )
        );
        this.lastCheckpointSkip.put(
            player.getUniqueId(),
            System.currentTimeMillis()
        );
        driver.resetLagFlags();
        if (
            this.trackManager.getNoResetOnFutureCheckpoint(
                heat.getTrackNameWS()
            )
        ) {
            this.plugin.sendMessage(
                player,
                "race_checkpoint_missed",
                new String[] {
                    "{expected}",
                    String.valueOf(expectedCheckpointId),
                }
            );
            return;
        }
        Map<Integer, List<DatabaseManager.RegionData>> checkpointsById =
            this.trackManager.getCheckpointsById(heat.getTrackNameWS());
        Location targetLoc;
        if (expectedCheckpointId == 0) {
            targetLoc = this.trackManager.getTrackSpawn(heat.getTrackNameWS());
            if (targetLoc != null) {
                targetLoc = new Location(
                    targetLoc.getWorld(),
                    targetLoc.getX(),
                    targetLoc.getY(),
                    targetLoc.getZ(),
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
                );
            }
        } else {
            int targetCheckpoint = expectedCheckpointId - 1;
            List<DatabaseManager.RegionData> targetRegions =
                checkpointsById.get(targetCheckpoint);
            if (targetRegions != null && !targetRegions.isEmpty()) {
                DatabaseManager.RegionData cp = targetRegions.get(0);
                double tpX = (cp.getMinX() + cp.getMaxX()) / 2.0;
                double tpZ = (cp.getMinZ() + cp.getMaxZ()) / 2.0;
                org.bukkit.World world = Bukkit.getWorld(cp.getWorld());
                int safeY =
                    world != null
                        ? world.getHighestBlockYAt((int) tpX, (int) tpZ) + 2
                        : (int) Math.max(cp.getMinY(), cp.getMaxY());
                double tpY = safeY;
                targetLoc = new Location(
                    world,
                    tpX,
                    tpY,
                    tpZ,
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
                );
            } else {
                targetLoc = this.trackManager.getTrackSpawn(
                    heat.getTrackNameWS()
                );
                if (targetLoc != null) {
                    targetLoc = new Location(
                        targetLoc.getWorld(),
                        targetLoc.getX(),
                        targetLoc.getY(),
                        targetLoc.getZ(),
                        player.getLocation().getYaw(),
                        player.getLocation().getPitch()
                    );
                }
            }
        }
        if (targetLoc == null) {
            return;
        }
        final Location finalTargetLoc = targetLoc;
        final Player finalPlayer = player;
        SchedulerHelper.runTaskFor(plugin, player, () -> {
            if (finalPlayer.isOnline()) {
                this.plugin.getAPI().recoverPlayerBoatState(finalPlayer);
                SchedulerHelper.teleport(plugin, finalPlayer, finalTargetLoc);
                finalPlayer.playSound(finalTargetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                this.plugin.getAPI().spawnBoat(finalPlayer, false, false, false);
            }
        }, 1L);
        this.plugin.sendMessage(
            player,
            "race_checkpoint_missed",
            new String[] { "{expected}", String.valueOf(expectedCheckpointId) }
        );
    }

    private void updateDelta(Driver driver, Player player) {
        Map<Integer, Long> bestCheckpoints = driver.getBestLapCheckpointTimes();
        if (bestCheckpoints != null && !bestCheckpoints.isEmpty()) {
            Lap currentLap = driver.getCurrentLap();
            if (currentLap != null) {
                int lastCheckpointId = driver.getCheckpointsReached();
                if (lastCheckpointId > 0) {
                    if (
                        lastCheckpointId !=
                        driver.getLastProcessedCheckpointId()
                    ) {
                        driver.setLastProcessedCheckpointId(lastCheckpointId);
                        if (!bestCheckpoints.containsKey(lastCheckpointId)) {
                            driver.setCachedDelta("");
                        } else {
                            Long currentTime =
                                currentLap.getRelativeCheckpointTime(
                                    lastCheckpointId
                                );
                            Long bestTime = (Long) bestCheckpoints.get(
                                lastCheckpointId
                            );
                            if (currentTime != null && bestTime != null) {
                                double deltaSeconds =
                                    (double) (currentTime - bestTime) /
                                    (double) 1000.0F;
                                String deltaStr;
                                if (Math.abs(deltaSeconds) < 0.001) {
                                    deltaStr = " §e±0.000";
                                } else if (deltaSeconds < (double) 0.0F) {
                                    deltaStr = String.format(
                                        " §a%.3f",
                                        deltaSeconds
                                    );
                                } else {
                                    deltaStr = String.format(
                                        " §c+%.3f",
                                        deltaSeconds
                                    );
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
            this.plugin.getDebugManager().logRaceSystem(
                "§c[LAP ERROR] " +
                    player.getName() +
                    " completou volta mas currentLap é null!"
            );
        } else if (driver.getLaps().isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem(
                "§c[LAP ERROR] " +
                    player.getName() +
                    " completou volta mas lista de laps está vazia!"
            );
        } else {
            Lap completedLap = (Lap) driver
                .getLaps()
                .get(driver.getLaps().size() - 1);
            if (heat.getId() > 0) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .createLap(
                        driver.getUuid(),
                        heat.getId(),
                        heat.getTrackNameWS(),
                        completedLap.getStartTime(),
                        completedLap.getLapEnd(),
                        completedLap.isPitted()
                    );
            }

            this.checkFastestLap(driver, completedLap, heat, player);
            String lapTime = this.formatTime(completedLap.getLapTime());
            int lapNumber = driver.getLapCount();
            String deltaStr = "";
            Lap personalBest = driver.getFastestLap();
            long comparisonTime = -1L;
            if (personalBest != null && personalBest.equals(completedLap)) {
                comparisonTime = driver
                    .getLaps()
                    .stream()
                    .filter(l -> l != completedLap && l.getLapEnd() > 0L)
                    .mapToLong(Lap::getLapTime)
                    .min()
                    .orElse(-1L);
            } else if (personalBest != null) {
                comparisonTime = personalBest.getLapTime();
            }

            if (comparisonTime > 0L) {
                long diff = completedLap.getLapTime() - comparisonTime;
                double diffSeconds = (double) diff / (double) 1000.0F;
                if (diffSeconds < (double) 0.0F) {
                    deltaStr = String.format("-%.3fs", Math.abs(diffSeconds));
                } else {
                    deltaStr = String.format("+%.3fs", diffSeconds);
                }
            }

            EventAnnouncements announcements =
                heat.getRound() != null && heat.getRound().getEvent() != null
                    ? heat.getRound().getEvent().getAnnouncements()
                    : this.plugin.getEventAnnouncements();
            announcements.broadcastLapTime(heat, driver, lapTime, deltaStr);
            if (
                heat.getHeatState() == HeatState.QUALIFYING ||
                heat.getHeatState() == HeatState.PRACTICE
            ) {
                boolean timeExpired = heat.getSessionTimeRemaining() <= 0L;
                boolean lapsComplete =
                    heat.getTotalLaps() != null &&
                    heat.getTotalLaps() > 0 &&
                    driver.getLapCount() >= heat.getTotalLaps();
                if (timeExpired || lapsComplete) {
                    if (timeExpired) {
                        this.plugin.sendMessage(
                            player,
                            "race_session_ended",
                            new String[0]
                        );
                    } else {
                        this.plugin.sendMessage(
                            player,
                            "race_laps_complete",
                            new String[0]
                        );
                    }

                    this.handleDriverFinished(driver, heat, player);
                    return;
                }
            }

            if (heat.getHeatState() == HeatState.RACING) {
                int completedLaps = driver.getLaps().size();
                int totalLaps =
                    heat.getTotalLaps() != null ? heat.getTotalLaps() : 0;
                if (totalLaps > 0 && completedLaps >= totalLaps) {
                    this.handleDriverFinished(driver, heat, player);
                    return;
                }
            }

            driver.newLap();
            if (
                heat.getHeatState() == HeatState.RACING &&
                heat.getTotalPits() > 0
            ) {
                int pitsRemaining = heat.getTotalPits() - driver.getPitstops();
                if (pitsRemaining > 0) {
                    this.plugin.sendMessage(
                        player,
                        "race_pitstops_remaining",
                        new String[] {
                            "{count}",
                            String.valueOf(pitsRemaining),
                        }
                    );
                } else {
                    this.plugin.sendMessage(
                        player,
                        "race_pitstops_complete",
                        new String[0]
                    );
                }
            }

            this.plugin.getDebugManager().logRaceSystem(
                String.format(
                    "§a[LAP] %s completou volta %d em %s",
                    player.getName(),
                    lapNumber,
                    lapTime
                )
            );
        }
    }

    private void checkFastestLap(
        Driver driver,
        Lap lap,
        Heats heat,
        Player player
    ) {
        Lap personalBest = driver.getFastestLap();
        if (
            personalBest != null &&
            lap.getLapTime() <= personalBest.getLapTime()
        ) {
            driver.setBestLapCheckpointTimes(lap.getRelativeCheckpointTimes());
            this.plugin.getDebugManager().logRaceSystem(
                player.getName() +
                    " atualizou checkpoints do PB para sistema de delta"
            );
        }

        UUID currentFastest = heat.getFastestLapUUID();
        EventAnnouncements announcements =
            heat.getRound() != null && heat.getRound().getEvent() != null
                ? heat.getRound().getEvent().getAnnouncements()
                : this.plugin.getEventAnnouncements();
        if (currentFastest == null) {
            heat.setFastestLapUUID(driver.getUuid());
            announcements.broadcastFastestLap(
                heat,
                driver,
                this.formatTime(lap.getLapTime())
            );
            if (heat.getId() > 0) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateHeatFastestLap(heat.getId(), driver.getUuid());
            }
        } else {
            Driver fastestDriver = heat.getDriver(currentFastest);
            if (fastestDriver != null) {
                if (currentFastest.equals(driver.getUuid())) {
                    long secondBestTime = driver
                        .getLaps()
                        .stream()
                        .filter(l -> l != lap && l.getLapEnd() > 0L)
                        .mapToLong(Lap::getLapTime)
                        .min()
                        .orElse(Long.MAX_VALUE);
                    if (lap.getLapTime() < secondBestTime) {
                        announcements.broadcastFastestLap(
                            heat,
                            driver,
                            this.formatTime(lap.getLapTime())
                        );
                        if (heat.getId() > 0) {
                            this.plugin.getRaceEventManager()
                                .getDatabaseManager()
                                .updateHeatFastestLap(
                                    heat.getId(),
                                    driver.getUuid()
                                );
                        }
                    }
                } else {
                    Lap fastestLap = fastestDriver.getFastestLap();
                    if (fastestLap != null) {
                        if (lap.getLapTime() < fastestLap.getLapTime()) {
                            heat.setFastestLapUUID(driver.getUuid());
                            announcements.broadcastFastestLap(
                                heat,
                                driver,
                                this.formatTime(lap.getLapTime())
                            );
                            if (heat.getId() > 0) {
                                this.plugin.getRaceEventManager()
                                    .getDatabaseManager()
                                    .updateHeatFastestLap(
                                        heat.getId(),
                                        driver.getUuid()
                                    );
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleDriverFinished(
        Driver driver,
        Heats heat,
        Player player
    ) {
        long finishTime =
            driver.getCurrentLap() != null &&
            driver.getCurrentLap().getLapEnd() > 0L
                ? driver.getCurrentLap().getLapEnd()
                : System.currentTimeMillis();
        HeatState sessionState = heat.getHeatState();
        if (this.plugin.getPTP() != null) {
            this.plugin.getPTP().disablePTP(player, driver);
        } else {
            driver.setPtpActive(false);
            driver.setPtpEnergy((double) 0.0F);
        }

        String trackNameWS = heat.getTrackNameWS();
        if (
            this.trackManager.hasLagStartRegion(trackNameWS) &&
            !driver.hasPassedLagStart()
        ) {
            this.plugin.getDebugManager().logRaceSystem(
                "§c[LAG] " +
                    player.getName() +
                    " tentou finish sem passar LAGSTART - DNF"
            );
            driver.setDnf(true);
            driver.setFinished(false);
            driver.setEndTime(finishTime);
            EventAnnouncements announcements =
                heat.getRound() != null && heat.getRound().getEvent() != null
                    ? heat.getRound().getEvent().getAnnouncements()
                    : this.plugin.getEventAnnouncements();
            announcements.broadcastDNF(
                heat,
                driver,
                "Lag detection: LAGSTART not passed"
            );
            if (heat.getId() > 0 && driver.getId() > 0) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateDriverTimes(
                        driver.getId(),
                        Instant.ofEpochMilli(driver.getStartTime()),
                        Instant.ofEpochMilli(finishTime)
                    );
            }
            if (player.isOnline()) {
                this.plugin.getRaceActionBarManager().removePlayer(player);
            }
            return;
        }
        if (
            this.trackManager.hasLagEndRegion(trackNameWS) &&
            !driver.hasPassedLagEnd()
        ) {
            this.plugin.getDebugManager().logRaceSystem(
                "§c[LAG] " +
                    player.getName() +
                    " tentou finish sem passar LAGEND - DNF"
            );
            driver.setDnf(true);
            driver.setFinished(false);
            driver.setEndTime(finishTime);
            EventAnnouncements announcements =
                heat.getRound() != null && heat.getRound().getEvent() != null
                    ? heat.getRound().getEvent().getAnnouncements()
                    : this.plugin.getEventAnnouncements();
            announcements.broadcastDNF(
                heat,
                driver,
                "Lag detection: LAGEND not passed"
            );
            if (heat.getId() > 0 && driver.getId() > 0) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateDriverTimes(
                        driver.getId(),
                        Instant.ofEpochMilli(driver.getStartTime()),
                        Instant.ofEpochMilli(finishTime)
                    );
            }
            if (player.isOnline()) {
                this.plugin.getRaceActionBarManager().removePlayer(player);
            }
            return;
        }

        driver.setEndTime(finishTime);
        if (
            heat.getTotalPits() > 0 &&
            driver.getPitstops() < heat.getTotalPits()
        ) {
            driver.setDnf(true);
            driver.setFinished(false);
            EventAnnouncements announcements =
                heat.getRound() != null && heat.getRound().getEvent() != null
                    ? heat.getRound().getEvent().getAnnouncements()
                    : this.plugin.getEventAnnouncements();
            int var10000 = driver.getPitstops();
            String dnfReason =
                "Pit stops: " + var10000 + "/" + heat.getTotalPits();
            announcements.broadcastDNF(heat, driver, dnfReason);
            if (heat.getId() > 0 && driver.getId() > 0) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateDriverTimes(
                        driver.getId(),
                        Instant.ofEpochMilli(driver.getStartTime()),
                        Instant.ofEpochMilli(finishTime)
                    );
            }

            if (player.isOnline()) {
                this.plugin.getRaceActionBarManager().removePlayer(player);
            }
        } else {
            int position =
                (int) heat
                    .getDrivers()
                    .values()
                    .stream()
                    .filter(Driver::isFinished)
                    .filter(d -> !d.isDnf())
                    .count() +
                1;
            driver.setFinished(true);
            driver.setPosition(position);
            EventAnnouncements announcements =
                heat.getRound() != null && heat.getRound().getEvent() != null
                    ? heat.getRound().getEvent().getAnnouncements()
                    : this.plugin.getEventAnnouncements();
            announcements.broadcastFinish(
                heat,
                driver,
                this.formatTime(driver.getTotalTime())
            );
            if (heat.getId() > 0 && driver.getId() > 0) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateDriverTimes(
                        driver.getId(),
                        Instant.ofEpochMilli(driver.getStartTime()),
                        Instant.ofEpochMilli(driver.getEndTime())
                    );
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateDriverPosition(driver.getId(), position);
            }

            if (player.isOnline()) {
                this.plugin.getRaceActionBarManager().removePlayer(player);
            }

            if (sessionState == HeatState.RACING) {
                this.plugin.getAPI().recoverPlayerBoatState(player);
                if (this.plugin.getPacketSender() != null) {
                    this.plugin.getPacketSender().resetBoatUtilsToVanilla(player);
                    boolean dbLonely = this.plugin.getDatabaseManager().getLonelyModePlayer(player.getUniqueId());
                    this.plugin.getLonelyController().setLonelyMode(player, dbLonely);
                }
                this.teleportToSpectatorArea(player, heat);
            }

            this.checkAllFinished(heat);
        }
    }

    private void checkAllFinished(Heats heat) {
        for (Driver driver : heat.getDrivers().values()) {
            if (!driver.isFinished() && !driver.isDnf()) {
                Player player = this.plugin.getServer().getPlayer(
                    driver.getUuid()
                );
                if (player == null || !player.isOnline()) {
                    this.plugin.getDebugManager().logRaceSystem(
                        "Driver " +
                            String.valueOf(driver.getUuid()) +
                            " está offline - marcando como DNF"
                    );
                    driver.setDnf(true);
                    driver.setPtpActive(false);
                    driver.setPtpEnergy((double) 0.0F);
                    Events event =
                        heat.getRound() != null
                            ? heat.getRound().getEvent()
                            : null;
                    EventAnnouncements announcements =
                        event != null
                            ? event.getAnnouncements()
                            : this.plugin.getEventAnnouncements();
                    announcements.broadcastDNF(heat, driver, "Disconnected");
                }
            }
        }

        boolean allFinished = heat
            .getDrivers()
            .values()
            .stream()
            .allMatch(driverx -> driverx.isFinished() || driverx.isDnf());
        if (allFinished) {
            this.plugin.getDebugManager().logRaceSystem(
                "Todos os pilotos finalizaram ou foram marcados como DNF no Heat " +
                    heat.getId()
            );
            SchedulerHelper.runTaskLater(
                this.plugin,
                () -> {
                    heat.finishHeat();
                    Events event =
                        heat.getRound() != null
                            ? heat.getRound().getEvent()
                            : null;
                    EventAnnouncements announcements =
                        event != null
                            ? event.getAnnouncements()
                            : this.plugin.getEventAnnouncements();
                    announcements.broadcastHeatComplete(heat);
                },
                100L
            );
        }
    }

    private String getPlayerName(UUID uuid) {
        Player player = this.plugin.getServer().getPlayer(uuid);
        return player != null
            ? player.getName()
            : this.plugin.getServer().getOfflinePlayer(uuid).getName();
    }

    private void teleportToSpectatorArea(Player player, Heats heat) {
        String trackNameWS = heat.getTrackNameWS();
        if (trackNameWS != null) {
            DatabaseManager db = this.plugin.getDatabaseManager();
            Driver driver = heat.getDriver(player.getUniqueId());
            if (driver != null) {
                Location finishPosLoc = db.getTrackFinishPos(
                    trackNameWS,
                    driver.getPosition()
                );
                if (finishPosLoc != null) {
                    SchedulerHelper.teleport(player, finishPosLoc);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
                    String title = "§6§l" + driver.getPosition() + "º LUGAR!";
                    TitleHelper.sendThemedTitle(
                        player,
                        "&s&lRACE FINISHED",
                        title,
                        10,
                        80,
                        20
                    );
                    return;
                }
            }

            Location finishAllLoc = db.getTrackFinishAll(trackNameWS);
            if (finishAllLoc != null) {
                SchedulerHelper.teleport(player, finishAllLoc);
                if (driver != null) {
                    String title = "§6§l" + driver.getPosition() + "º LUGAR!";
                    TitleHelper.sendThemedTitle(
                        player,
                        "&s&lRACE FINISHED",
                        title,
                        10,
                        80,
                        20
                    );
                }

                this.plugin.sendMessage(
                    player,
                    "race_teleport_stand",
                    new String[0]
                );
            }
        }
    }

    private String formatTime(long timeMs) {
        long minutes = timeMs / 60000L;
        long seconds = (timeMs % 60000L) / 1000L;
        long millis = timeMs % 1000L;
        return minutes > 0L
            ? String.format("%d:%02d.%03d", minutes, seconds, millis)
            : String.format("%d.%03d", seconds, millis);
    }
}
