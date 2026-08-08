package dev.EfraGroup.formulaRacing.AI;

import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Utils.RegionMathUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI opponent system with its own physical entity.
 */
public class AIOpponentManager {

    private static final double DEFAULT_THROTTLE = 0.85;
    private static final double MAX_BOAT_SPEED = 1.6;

    private final FormulaRacing plugin;
    private final Map<UUID, AIOpponent> aiOpponents;
    private FRTask aiUpdateTask;
    private Integer activeHeatId;

    public AIOpponentManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.aiOpponents = new ConcurrentHashMap<>();
    }

    public enum AIDifficulty {
        EASY(0.8, 0.3, 0.5, 0.8, 0.4),
        MEDIUM(0.95, 0.15, 0.7, 0.9, 0.7),
        HARD(1.15, 0.05, 0.9, 0.95, 0.9);

        private final String name;
        private final double speedMultiplier;
        private final double errorRate;
        private final double lineAccuracy;
        private final double reactionTime;
        private final double aggressionLevel;

        AIDifficulty(double speedMultiplier, double errorRate, double lineAccuracy,
                     double reactionTime, double aggressionLevel) {
            this.name = name();
            this.speedMultiplier = speedMultiplier;
            this.errorRate = errorRate;
            this.lineAccuracy = lineAccuracy;
            this.reactionTime = reactionTime;
            this.aggressionLevel = aggressionLevel;
        }

        public String getName() { return name; }
        public double getSpeedMultiplier() { return speedMultiplier; }
        public double getErrorRate() { return errorRate; }
        public double getLineAccuracy() { return lineAccuracy; }
        public double getReactionTime() { return reactionTime; }
        public double getAggressionLevel() { return aggressionLevel; }
    }

    public AIOpponent createAIOpponent(Driver driver, String displayName, AIDifficulty difficulty) {
        driver.setCustomName(displayName);
        driver.setAiControlled(true);
        AIOpponent ai = new AIOpponent(driver, displayName, difficulty, plugin);
        aiOpponents.put(driver.getUuid(), ai);
        plugin.getDebugManager().logRaceSystem("[AI] Oponente criado: " + displayName + " (UUID: " + driver.getUuid() + ", startPos: " + driver.getStartPosition() + ")");
        return ai;
    }

    public void removeAIOpponent(UUID uuid) {
        AIOpponent ai = aiOpponents.remove(uuid);
        if (ai != null) {
            ai.despawnEntity();
        }
    }

    public AIOpponent getAIOpponent(UUID uuid) {
        return aiOpponents.get(uuid);
    }

    public Map<UUID, AIOpponent> getAIOpponents() {
        return aiOpponents;
    }

    public boolean isAIOpponent(UUID uuid) {
        return aiOpponents.containsKey(uuid);
    }

    public Entity getControlledEntity(UUID uuid) {
        AIOpponent ai = aiOpponents.get(uuid);
        return ai != null ? ai.getControlledEntity() : null;
    }

    public void startAIForHeat(Heats heat) {
        if (heat == null) {
            stopAI();
            return;
        }

        if (aiUpdateTask != null && !aiUpdateTask.isCancelled() && activeHeatId != null && activeHeatId == heat.getId()) {
            return;
        }

        stopAI();
        activeHeatId = heat.getId();
        aiUpdateTask = SchedulerHelper.runTaskTimer(plugin, () -> updateAI(heat), 0L, 2L);
    }

    public void stopAI() {
        if (aiUpdateTask != null && !aiUpdateTask.isCancelled()) {
            aiUpdateTask.cancel();
        }
        aiUpdateTask = null;
        activeHeatId = null;
        for (AIOpponent ai : aiOpponents.values()) {
            ai.despawnEntity();
        }
    }

    public void stopAIForHeat(int heatId) {
        if (activeHeatId != null && activeHeatId == heatId) {
            stopAI();
        } else {
            for (AIOpponent ai : aiOpponents.values()) {
                if (ai.getDriver().getHeatId() == heatId) {
                    ai.despawnEntity();
                }
            }
        }
    }

    private void updateAI(Heats heat) {
        if (heat == null) {
            return;
        }

        AIRacingLine line = plugin.getAIRacingLineManager().getRacingLineIfExists(heat.getTrackNameWS()).orElse(null);
        for (AIOpponent ai : aiOpponents.values()) {
            if (heat.getDriver(ai.getDriver().getUuid()) != null) {
                ai.ensureSpawned(heat);
                if (heat.getHeatState() == HeatState.RACING) {
                    ai.update(heat, line);
                }
            }
        }

        if (heat.getHeatState() == HeatState.RACING) {
            checkAICollisions(heat);
        }
    }

    private void checkAICollisions(Heats heat) {
        final double collisionDistanceSquared = 4.0;
        final double bumpForce = 0.18;
        AIOpponent[] opponents = aiOpponents.values().toArray(new AIOpponent[0]);

        for (int i = 0; i < opponents.length; i++) {
            for (int j = i + 1; j < opponents.length; j++) {
                AIOpponent ai1 = opponents[i];
                AIOpponent ai2 = opponents[j];

                if (heat.getDriver(ai1.getDriver().getUuid()) == null || heat.getDriver(ai2.getDriver().getUuid()) == null) {
                    continue;
                }

                Entity entity1 = ai1.getControlledEntity();
                Entity entity2 = ai2.getControlledEntity();
                if (entity1 == null || entity2 == null || !entity1.isValid() || !entity2.isValid()) {
                    continue;
                }
                if (!entity1.getWorld().equals(entity2.getWorld())) {
                    continue;
                }

                // getLocation(Location) is thread-safe on Folia (copies into the arg).
                Location loc1 = entity1.getLocation(new Location(null, 0, 0, 0));
                Location loc2 = entity2.getLocation(new Location(null, 0, 0, 0));
                if (loc1.distanceSquared(loc2) < collisionDistanceSquared) {
                    // Resolve the push on each entity's own region thread.
                    applyCollision(entity1, entity2, loc1, loc2, bumpForce);
                }
            }
        }
    }

    private void applyCollision(Entity entity1, Entity entity2, Location loc1, Location loc2, double force) {
        Vector direction = loc2.toVector().subtract(loc1.toVector());
        direction.setY(0.0);

        if (direction.lengthSquared() < 0.0001) {
            direction = new Vector(
                    ThreadLocalRandom.current().nextDouble(-1.0, 1.0),
                    0.0,
                    ThreadLocalRandom.current().nextDouble(-1.0, 1.0)
            );
        }

        Vector push = direction.normalize().multiply(force);
        // Each setVelocity runs on its own entity's region thread (Folia).
        SchedulerHelper.runTaskFor(plugin, entity1, () -> {
            if (entity1.isValid()) {
                entity1.setVelocity(entity1.getVelocity().subtract(push));
            }
        });
        SchedulerHelper.runTaskFor(plugin, entity2, () -> {
            if (entity2.isValid()) {
                entity2.setVelocity(entity2.getVelocity().add(push));
            }
        });
    }

    public void clearAll() {
        stopAI();
        aiOpponents.clear();
    }

    public void notifyLapCompleted(UUID uuid) {
        AIOpponent ai = aiOpponents.get(uuid);
        if (ai != null) {
            ai.completeLap();
        }
    }

    public void notifyLapStarted(UUID uuid) {
        AIOpponent ai = aiOpponents.get(uuid);
        if (ai != null) {
            ai.startNewLap();
        }
    }

    public Collection<AIOpponent> findByDisplayName(String name) {
        return aiOpponents.values().stream()
                .filter(ai -> ai.getDisplayName().equalsIgnoreCase(name))
                .toList();
    }

    public static class AIOpponent {
        private final Driver driver;
        private final String displayName;
        private final FormulaRacing plugin;
        private AIDifficulty difficulty;
        private int currentLineIndex;
        private double currentSpeed;
        private int mistakesMade;
        private long lastMistakeTime;
        private boolean isMakingMistake;
        private long mistakeStartTime;
        private double bestLapTime;
        private double currentLapTime;
        private long lapStartTime;
        private int lapsCompleted;
        private double learningProgress;
        private double learnedSpeedMultiplier;
        private double learnedLineAccuracy;
        private double learnedErrorRate;
        private UUID boatUuid;
        private Location lastKnownLocation;
        private long lastStartEndCrossTime;

        public AIOpponent(Driver driver, String displayName, AIDifficulty difficulty, FormulaRacing plugin) {
            this.driver = driver;
            this.displayName = displayName;
            this.difficulty = difficulty;
            this.plugin = plugin;
            this.currentLineIndex = -1;
            this.currentSpeed = 0.0;
            this.mistakesMade = 0;
            this.lastMistakeTime = 0L;
            this.isMakingMistake = false;
            this.bestLapTime = Double.MAX_VALUE;
            this.currentLapTime = 0.0;
            this.lapStartTime = 0L;
            this.lapsCompleted = 0;
            this.learningProgress = 0.0;
            this.boatUuid = null;
            this.lastKnownLocation = null;
            this.lastStartEndCrossTime = 0L;
            resetLearnedValues();
        }

        public void ensureSpawned(Heats heat) {
            if (!driver.isAiControlled()) {
                return;
            }
            Entity existing = getControlledEntity();
            if (existing != null && existing.isValid()) {
                return;
            }

            Location spawn = resolveSpawnLocation(heat);
            if (spawn == null || spawn.getWorld() == null) {
                plugin.getDebugManager().logRaceSystem("[AI] Spawn failed for " + displayName + ": spawn or world is null");
                return;
            }

            // Entity creation must run on the world's region thread (Folia).
            // Boat is an interface, so we spawn the concrete OAK_BOAT entity type.
            final Location finalSpawn = spawn.clone();
            SchedulerHelper.runTaskAtLocation(plugin, finalSpawn, () -> {
                Boat boat = (Boat) finalSpawn.getWorld().spawnEntity(finalSpawn, EntityType.OAK_BOAT);
                boat.setCustomName(displayName);
                boat.setCustomNameVisible(true);
                boat.setInvulnerable(true);
                boat.setGravity(true);
                boat.setPersistent(false);
                boat.setSilent(true);
                boat.setRotation(finalSpawn.getYaw(), finalSpawn.getPitch());
                boatUuid = boat.getUniqueId();
                lastKnownLocation = boat.getLocation().clone();
            });
        }

        public void despawnEntity() {
            Entity entity = getControlledEntity();
            if (entity != null) {
                // Entity.remove() must run on the owning region thread (Folia).
                SchedulerHelper.runTaskFor(plugin, entity, entity::remove);
            }
            boatUuid = null;
            lastKnownLocation = null;
        }

        public Entity getControlledEntity() {
            if (boatUuid != null) {
                Entity entity = Bukkit.getEntity(boatUuid);
                if (entity != null && entity.isValid()) {
                    return entity;
                }
            }
            Player onlinePlayer = Bukkit.getPlayer(driver.getUuid());
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                return onlinePlayer.getVehicle() != null ? onlinePlayer.getVehicle() : onlinePlayer;
            }
            return null;
        }

        public void update(Heats heat, AIRacingLine line) {
            // All entity access (getLocation/setVelocity/setRotation) must run on the
            // boat's region thread on Folia, so dispatch the whole tick there.
            Entity controlledEntity = getControlledEntity();
            if (controlledEntity == null || !controlledEntity.isValid()) {
                return;
            }
            SchedulerHelper.runTaskFor(plugin, controlledEntity, () -> {
                if (!controlledEntity.isValid()) {
                    return;
                }
                updateLapTime();
                checkForMistake();

                Location currentLoc = controlledEntity.getLocation();
                calculateSpeed(heat, line, currentLoc);
                moveBoat(heat, line, controlledEntity, currentLoc);

                Location newLoc = controlledEntity.getLocation();
                processTrackProgress(heat, currentLoc, newLoc);
                lastKnownLocation = newLoc.clone();
            });
        }

        private void updateLapTime() {
            if (lapStartTime > 0L) {
                currentLapTime = (System.currentTimeMillis() - lapStartTime) / 1000.0;
            }
        }

        public void startNewLap() {
            lapStartTime = System.currentTimeMillis();
            currentLapTime = 0.0;
        }

        public void completeLap() {
            if (lapStartTime <= 0L) {
                return;
            }

            double lapTime = (System.currentTimeMillis() - lapStartTime) / 1000.0;
            double previousBest = bestLapTime;

            if (lapTime < bestLapTime) {
                bestLapTime = lapTime;
            }

            lapsCompleted++;
            applyLearning(lapTime, previousBest);
            startNewLap();
        }

        private void applyLearning(double lapTime, double previousBest) {
            if (lapsCompleted < 2 || previousBest == Double.MAX_VALUE || previousBest <= 0.0 || lapTime <= 0.0) {
                return;
            }

            double improvement = (previousBest - lapTime) / previousBest;
            if (improvement > 0.0) {
                learningProgress = Math.min(1.0, learningProgress + (improvement * 2.0));
            } else {
                learningProgress = Math.max(0.0, learningProgress - 0.03);
            }

            adjustDifficultyBasedOnLearning();
        }

        private void adjustDifficultyBasedOnLearning() {
            double speedBonus = learningProgress * 0.12;
            double accuracyBonus = learningProgress * 0.15;
            learnedSpeedMultiplier = Math.min(1.0, difficulty.getSpeedMultiplier() + speedBonus);
            learnedLineAccuracy = Math.min(1.0, difficulty.getLineAccuracy() + accuracyBonus);
            learnedErrorRate = Math.max(0.01, difficulty.getErrorRate() - (learningProgress * 0.08));
        }

        private void checkForMistake() {
            long currentTime = System.currentTimeMillis();

            if (isMakingMistake) {
                long duration = currentTime - mistakeStartTime;
                long maxDuration = (long) (2800 - (learnedLineAccuracy * 1600));
                if (duration > maxDuration) {
                    isMakingMistake = false;
                }
                return;
            }

            if (currentTime - lastMistakeTime < 10000L) {
                return;
            }

            if (ThreadLocalRandom.current().nextDouble() < learnedErrorRate) {
                isMakingMistake = true;
                mistakeStartTime = currentTime;
                lastMistakeTime = currentTime;
                mistakesMade++;
            }
        }

        private void calculateSpeed(Heats heat, AIRacingLine line, Location currentLoc) {
            double desiredSpeed = DEFAULT_THROTTLE * learnedSpeedMultiplier;

            if (line != null && line.isUsable() && currentLoc != null) {
                int closestIndex = resolveLineIndex(line, currentLoc);
                desiredSpeed = line.getIdealSpeedAtIndex(closestIndex) * learnedSpeedMultiplier;

                if (line.isNearBrakingPoint(currentLoc, 4.0)) {
                    desiredSpeed *= 0.72;
                }
                if (line.isNearAccelerationPoint(currentLoc, 4.0)) {
                    desiredSpeed *= 1.06;
                }
            }

            desiredSpeed *= calculateRealismSpeedFactor(heat);

            if (isMakingMistake) {
                desiredSpeed *= 0.55;
            }

            double speedVariation = (1.0 - learnedLineAccuracy) * 0.08;
            desiredSpeed += ThreadLocalRandom.current().nextDouble(-speedVariation, speedVariation);
            desiredSpeed = Math.max(0.12, Math.min(1.0, desiredSpeed));

            double response = 0.18 + (difficulty.getReactionTime() * 0.42);
            currentSpeed += (desiredSpeed - currentSpeed) * response;
            currentSpeed = Math.max(0.08, Math.min(1.0, currentSpeed));
        }

        private double calculateRealismSpeedFactor(Heats heat) {
            double factor = 1.0D;

            if (heat.getrealistc() && driver.getTireCompound() != null) {
                factor *= driver.getTireCompound().getGripMultiplier(driver.getTireWear());
            }

            if (heat.getHeatConfig().isFuelSystemEnabled() && driver.getFuelCapacity() > 0.0D) {
                double fuelRatio = driver.getFuelLevel() / driver.getFuelCapacity();
                factor *= 0.82D + (fuelRatio * 0.18D);
                if (driver.getFuelLevel() <= 0.0D) {
                    factor *= 0.20D;
                }
            }

            return factor;
        }

        private void moveBoat(Heats heat, AIRacingLine line, Entity controlledEntity, Location currentLoc) {
            Vector movement;

            if (line != null && line.isUsable()) {
                int index = resolveLineIndex(line, currentLoc);
                int lookAhead = Math.max(1, (int) Math.round(2 + (difficulty.getReactionTime() * 4)));
                Location target = line.getPointAtWrapped(index + lookAhead);
                if (target == null || target.getWorld() == null || !target.getWorld().equals(currentLoc.getWorld())) {
                    return;
                }

                Vector offset = target.toVector().subtract(currentLoc.toVector());
                offset.setY(0.0);
                if (offset.lengthSquared() < 0.0001) {
                    return;
                }

                Vector idealDirection = offset.normalize();
                Vector correctedDirection = applySteeringVariance(idealDirection);
                movement = correctedDirection.multiply(currentSpeed * MAX_BOAT_SPEED);

                Vector vertical = controlledEntity.getVelocity().clone();
                movement.setY(Math.max(-0.08, Math.min(0.08, vertical.getY())));
                controlledEntity.setVelocity(movement);

                float yaw = (float) Math.toDegrees(Math.atan2(-correctedDirection.getX(), correctedDirection.getZ()));
                controlledEntity.setRotation(yaw, currentLoc.getPitch());
                currentLineIndex = index;
            } else {
                Vector forward = currentLoc.getDirection().setY(0.0);
                if (forward.lengthSquared() < 0.0001) {
                    return;
                }
                movement = applySteeringVariance(forward.normalize()).multiply(currentSpeed * 0.45);
                controlledEntity.setVelocity(movement);
            }
        }

        private void processTrackProgress(Heats heat, Location from, Location to) {
            if (from == null || to == null || !from.getWorld().equals(to.getWorld()) || driver.isFinished() || driver.isDnf()) {
                return;
            }

            TrackIntegrationManager trackManager = heat.getPlugin().getTrackIntegrationManager();
            String trackNameWS = heat.getTrackNameWS();
            updateLagMarkers(trackManager, trackNameWS, from, to);
            processCheckpointProgress(trackManager, trackNameWS, heat, from, to);
            processLapProgress(trackManager, trackNameWS, heat, from, to);
        }

        private void updateLagMarkers(TrackIntegrationManager trackManager, String trackNameWS, Location from, Location to) {
            if (trackManager.hasLagStartRegion(trackNameWS) && !driver.hasPassedLagStart()) {
                for (DatabaseManager.RegionData region : trackManager.getTrackRegionsByType(trackNameWS, "LAGSTART")) {
                    if (RegionMathUtils.intersectsRegion(from, to, region)) {
                        driver.setLagStartPassed(true);
                        break;
                    }
                }
            }

            if (trackManager.hasLagEndRegion(trackNameWS) && !driver.hasPassedLagEnd()) {
                for (DatabaseManager.RegionData region : trackManager.getTrackRegionsByType(trackNameWS, "LAGEND")) {
                    if (RegionMathUtils.intersectsRegion(from, to, region)) {
                        driver.setLagEndPassed(true);
                        break;
                    }
                }
            }
        }

        private void processCheckpointProgress(TrackIntegrationManager trackManager, String trackNameWS, Heats heat, Location from, Location to) {
            Map<Integer, List<DatabaseManager.RegionData>> checkpointsById = trackManager.getCheckpointsById(trackNameWS);
            if (checkpointsById.isEmpty()) {
                return;
            }

            int totalCheckpoints = trackManager.getCheckpointCount(trackNameWS);
            int checkpointsReached = driver.getCheckpointsReached();
            int nextExpectedZeroBased = checkpointsReached;
            int nextExpectedOneBased = checkpointsReached + 1;

            DatabaseManager.RegionData matchedRegion = findMatchingCheckpointRegion(checkpointsById.get(nextExpectedZeroBased), from, to);
            if (matchedRegion == null && nextExpectedOneBased <= totalCheckpoints) {
                matchedRegion = findMatchingCheckpointRegion(checkpointsById.get(nextExpectedOneBased), from, to);
            }

            if (matchedRegion != null) {
                driver.incrementCheckpoint();
                driver.setLastCheckpointTime(System.currentTimeMillis());
                heat.markPositionsDirty();
            }
        }

        private void processLapProgress(TrackIntegrationManager trackManager, String trackNameWS, Heats heat, Location from, Location to) {
            long now = System.currentTimeMillis();
            if (now - lastStartEndCrossTime < 2000L) {
                return;
            }

            List<DatabaseManager.RegionData> regions = trackManager.getTrackRegionsByType(trackNameWS, "START");
            if (regions.isEmpty()) {
                regions = trackManager.getTrackRegionsByType(trackNameWS, "END");
            } else {
                regions = new java.util.ArrayList<>(regions);
                regions.addAll(trackManager.getTrackRegionsByType(trackNameWS, "END"));
            }

            for (DatabaseManager.RegionData region : regions) {
                if (!RegionMathUtils.intersectsRegion(from, to, region)) {
                    continue;
                }

                if (trackManager.hasLagStartRegion(trackNameWS) && !driver.hasPassedLagStart()) {
                    return;
                }
                if (trackManager.hasLagEndRegion(trackNameWS) && !driver.hasPassedLagEnd()) {
                    return;
                }

                lastStartEndCrossTime = now;
                driver.setResetCount(0);
                heat.passLap(driver, from, to, toRegionBox(from.getWorld(), region));
                heat.markPositionsDirty();
                return;
            }
        }

        private DatabaseManager.RegionData findMatchingCheckpointRegion(List<DatabaseManager.RegionData> regions, Location from, Location to) {
            if (regions == null) {
                return null;
            }

            for (DatabaseManager.RegionData region : regions) {
                if (RegionMathUtils.isEnteringRegion(from, to, region)) {
                    return region;
                }
            }
            return null;
        }

        private RegionBox toRegionBox(World world, DatabaseManager.RegionData regionData) {
            Location min = new Location(world, regionData.getMinX(), regionData.getMinY(), regionData.getMinZ());
            Location max = new Location(world, regionData.getMaxX(), regionData.getMaxY(), regionData.getMaxZ());
            return new RegionBox(min, max);
        }

        private Vector applySteeringVariance(Vector baseDirection) {
            double varianceDegrees = isMakingMistake
                    ? ThreadLocalRandom.current().nextDouble(-35.0, 35.0)
                    : ThreadLocalRandom.current().nextDouble(-1.0, 1.0) * (1.0 - learnedLineAccuracy) * 18.0;

            double radians = Math.toRadians(varianceDegrees);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);

            double x = (baseDirection.getX() * cos) - (baseDirection.getZ() * sin);
            double z = (baseDirection.getX() * sin) + (baseDirection.getZ() * cos);
            return new Vector(x, 0.0, z).normalize();
        }

        private int resolveLineIndex(AIRacingLine line, Location currentLoc) {
            int closestIndex = line.getClosestIdealLineIndex(currentLoc);
            if (closestIndex < 0) {
                return currentLineIndex >= 0 ? currentLineIndex : 0;
            }

            if (currentLineIndex < 0) {
                currentLineIndex = closestIndex;
                return currentLineIndex;
            }

            int pointCount = Math.max(1, line.getIdealLineSize());
            int directDelta = Math.floorMod(closestIndex - currentLineIndex, pointCount);
            if (directDelta <= 8) {
                currentLineIndex = closestIndex;
            } else {
                currentLineIndex = line.advanceIndex(currentLineIndex, 1);
            }
            return currentLineIndex;
        }

        private Location resolveSpawnLocation(Heats heat) {
            if (heat.getGridManager().getGridPositions().isEmpty()) {
                heat.getGridManager().generateGrid();
            }

            List<Location> gridPositions = heat.getGridManager().getGridPositions();
            int gridIndex = Math.max(0, driver.getStartPosition() - 1);
            if (gridIndex < gridPositions.size()) {
                return gridPositions.get(gridIndex).clone();
            }
            return heat.getPlugin().getTrackIntegrationManager().getTrackSpawn(heat.getTrackNameWS());
        }

        private void resetLearnedValues() {
            learnedSpeedMultiplier = difficulty.getSpeedMultiplier();
            learnedLineAccuracy = difficulty.getLineAccuracy();
            learnedErrorRate = difficulty.getErrorRate();
        }

        public Driver getDriver() {
            return driver;
        }

        public String getDisplayName() {
            return displayName;
        }

        public AIDifficulty getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(AIDifficulty difficulty) {
            this.difficulty = difficulty;
            resetLearnedValues();
        }

        public double getCurrentSpeed() {
            return currentSpeed;
        }

        public int getMistakesMade() {
            return mistakesMade;
        }

        public boolean isMakingMistake() {
            return isMakingMistake;
        }

        public double getBestLapTime() {
            return bestLapTime;
        }

        public double getCurrentLapTime() {
            return currentLapTime;
        }

        public int getLapsCompleted() {
            return lapsCompleted;
        }

        public double getLearningProgress() {
            return learningProgress;
        }
    }
}
