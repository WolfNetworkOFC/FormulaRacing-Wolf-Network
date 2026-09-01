package dev.EfraGroup.formulaRacing.AI;

import dev.EfraGroup.formulaRacing.Collisionless.NMSHandlerImpl;
import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Utils.RegionMathUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
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

    /** Minimum steering lookahead (blocks) so the target is always ahead of the boat. */
    private static final double MIN_LOOKAHEAD_BLOCKS = 10.0D;
    /** Maximum steering lookahead (blocks) — no point planning a whole straight ahead. */
    private static final double MAX_LOOKAHEAD_BLOCKS = 30.0D;

    /**
     * Maximum speed (blocks/tick) the AI targets per surface, matching the
     * terminal speeds of a ridden vanilla Minecraft boat (Java Edition).
     *
     * <p>Measured values from the Minecraft Wiki (blocks/second), converted to
     * blocks/tick (÷20):
     * <ul>
     *   <li>Blue ice: 72.73 m/s → 3.6365 b/t</li>
     *   <li>Packed ice: 40 m/s → 2.0 b/t</li>
     *   <li>Regular/frosted ice: 40 m/s → 2.0 b/t (same tier as packed ice)</li>
     *   <li>Water: 8 m/s → 0.4 b/t</li>
     *   <li>Solid ground: 2 m/s → 0.1 b/t (boats crawl on land)</li>
     * </ul>
     * The runtime target is {@code currentSpeed (0..1) * surfaceMaxSpeed}.
     */
    private static final double BLUE_ICE_MAX_SPEED = 72.73D / 20.0D; // 3.6365 b/t
    private static final double PACKED_ICE_MAX_SPEED = 40.0D / 20.0D; // 2.0 b/t
    private static final double ICE_MAX_SPEED = 40.0D / 20.0D;        // 2.0 b/t
    private static final double WATER_MAX_SPEED = 8.0D / 20.0D;       // 0.4 b/t
    private static final double LAND_MAX_SPEED = 2.0D / 20.0D;        // 0.1 b/t

    /**
     * Velocity lerp per update while accelerating. Tuned so a boat's ramp-up
     * from standstill to terminal speed matches vanilla (~3-4.7 s): combined
     * with the speed lerp in {@link #calculateSpeed} this yields roughly a
     * 4-second build-up to top speed, instead of the old ~1 s.
     */
    private static final double ACCEL_STEER = 0.10D;
    /** Velocity lerp per update while braking (decisive corner entry). */
    private static final double BRAKE_STEER = 0.60D;

    /**
     * Returns how fast (blocks/tick) a boat can realistically travel on the
     * surface below {@code loc}, so the AI drives like a player: ice is fast
     * (low friction, velocity builds), water is drag-limited, land is slow.
     */
    public static double getSurfaceMaxSpeed(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return 1.0D;
        }
        Block block = loc.getBlock();
        double max = surfaceMaxFor(block.getType());
        if (max < 0.0D) {
            max = surfaceMaxFor(block.getRelative(BlockFace.DOWN).getType());
        }
        return max < 0.0D ? 1.0D : max;
    }

    private static double surfaceMaxFor(Material type) {
        if (type == null) {
            return -1.0D;
        }
        switch (type) {
            case BLUE_ICE:
                return BLUE_ICE_MAX_SPEED;
            case PACKED_ICE:
                return PACKED_ICE_MAX_SPEED;
            case ICE:
            case FROSTED_ICE:
                return ICE_MAX_SPEED;
            case WATER:
            case KELP:
            case KELP_PLANT:
            case SEAGRASS:
            case TALL_SEAGRASS:
                return WATER_MAX_SPEED;
            case AIR:
            case CAVE_AIR:
            case VOID_AIR:
                return -1.0D; // not a surface — check the block below
            default:
                return LAND_MAX_SPEED; // solid ground — vanilla boats crawl (~2 m/s)
        }
    }

    private final FormulaRacing plugin;
    private final Map<UUID, AIOpponent> aiOpponents;
    /** One update task per active heat, so multiple heats with AI can run simultaneously. */
    private final Map<Integer, FRTask> heatTasks;

    public AIOpponentManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.aiOpponents = new ConcurrentHashMap<>();
        this.heatTasks = new ConcurrentHashMap<>();
    }

    public enum AIDifficulty {
        EASY(0.8, 0.3, 0.5, 0.8, 0.4),
        MEDIUM(0.95, 0.15, 0.7, 0.9, 0.7),
        HARD(1.15, 0.05, 0.9, 0.95, 0.9);

        private final double speedMultiplier;
        private final double errorRate;
        private final double lineAccuracy;
        private final double reactionTime;
        private final double aggressionLevel;

        AIDifficulty(double speedMultiplier, double errorRate, double lineAccuracy,
                     double reactionTime, double aggressionLevel) {
            this.speedMultiplier = speedMultiplier;
            this.errorRate = errorRate;
            this.lineAccuracy = lineAccuracy;
            this.reactionTime = reactionTime;
            this.aggressionLevel = aggressionLevel;
        }

        public String getName() { return name(); }
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
            return;
        }

        if (heatTasks.containsKey(heat.getId())) {
            return;
        }

        // No automatic racing-line generation here: tracks without a recorded
        // line simply have no line (no .bin file is ever created silently).
        // A basic line can still be generated manually (/ai) if wanted.
        heatTasks.put(heat.getId(), SchedulerHelper.runTaskTimer(plugin, () -> updateAI(heat), 0L, 2L));
    }

    public void stopAI() {
        for (FRTask task : heatTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        heatTasks.clear();
        for (AIOpponent ai : aiOpponents.values()) {
            ai.despawnEntity();
        }
    }

    public void stopAIForHeat(int heatId) {
        FRTask task = heatTasks.remove(heatId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        for (AIOpponent ai : aiOpponents.values()) {
            if (ai.getDriver().getHeatId() == heatId) {
                ai.despawnEntity();
            }
        }
    }

    private void updateAI(Heats heat) {
        if (heat == null) {
            return;
        }

        AIRacingLine line = plugin.getAIRacingLineManager().getRacingLineIfExists(heat.getTrackNameWS()).orElse(null);
        List<AIOpponent> opponentsInHeat = opponentsInHeat(heat);
        for (AIOpponent ai : opponentsInHeat) {
            ai.ensureSpawned(heat);
            if (heat.getHeatState() == HeatState.RACING) {
                ai.update(heat, line);
            }
        }

        if (heat.getHeatState() == HeatState.RACING) {
            checkAICollisions(opponentsInHeat);
        }
    }

    /** Opponents whose driver belongs to the given heat (snapshot list, safe to iterate). */
    private List<AIOpponent> opponentsInHeat(Heats heat) {
        List<AIOpponent> result = new ArrayList<>();
        for (AIOpponent ai : aiOpponents.values()) {
            if (heat.getDriver(ai.getDriver().getUuid()) != null) {
                result.add(ai);
            }
        }
        return result;
    }

    private void checkAICollisions(List<AIOpponent> opponents) {
        final double collisionDistanceSquared = 4.0;
        final double bumpForce = 0.18;

        for (int i = 0; i < opponents.size(); i++) {
            for (int j = i + 1; j < opponents.size(); j++) {
                AIOpponent ai1 = opponents.get(i);
                AIOpponent ai2 = opponents.get(j);

                Entity entity1 = ai1.getControlledEntity();
                Entity entity2 = ai2.getControlledEntity();
                if (entity1 == null || entity2 == null || !entity1.isValid() || !entity2.isValid()) {
                    continue;
                }

                // getLocation(Location) is thread-safe on Folia (copies into the arg);
                // compare worlds through the copies instead of touching the entities.
                Location loc1 = entity1.getLocation(new Location(null, 0, 0, 0));
                Location loc2 = entity2.getLocation(new Location(null, 0, 0, 0));
                if (loc1.getWorld() == null || !loc1.getWorld().equals(loc2.getWorld())) {
                    continue;
                }
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
        private static final int LINE_SEARCH_WINDOW = 32;
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
        /** Written on region threads, read from the update loop — must be volatile. */
        private volatile UUID boatUuid;
        private volatile Location lastKnownLocation;
        private long lastStartEndCrossTime;
        /** True while a spawn task is scheduled but has not run yet — guards against duplicate boats. */
        private volatile boolean spawnPending;
        /** Bumped on despawn to invalidate spawn tasks that are still scheduled. */
        private volatile int spawnGeneration;
        /** Client-side fake player (NPC) riding the boat, if it was created successfully. */
        private FakePlayerNPC fakePlayer;

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
            // The spawn task only executes on the region thread next tick; without
            // this guard, the 2-tick update loop schedules another boat before the
            // first one exists, leaking orphan boats that are never tracked.
            if (spawnPending) {
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
            final CollisionMode heatCollisionMode = heat != null ? heat.getCollisionMode() : CollisionMode.HIGH;
            final int generation = spawnGeneration;
            spawnPending = true;
            SchedulerHelper.runTaskAtLocation(plugin, finalSpawn, () -> {
                try {
                    // The heat may have ended while this spawn was scheduled:
                    // despawnEntity() bumps the generation, and spawning here
                    // anyway would leak an orphan boat nobody tracks.
                    if (generation != spawnGeneration) {
                        return;
                    }
                    Boat boat = (Boat) finalSpawn.getWorld().spawnEntity(finalSpawn, EntityType.OAK_BOAT);
                    boat.customName(Component.text(displayName));
                    boat.setCustomNameVisible(true);
                    boat.setInvulnerable(true);
                    boat.setGravity(true);
                    boat.setPersistent(false);
                    boat.setSilent(true);
                    boat.setRotation(finalSpawn.getYaw(), finalSpawn.getPitch());
                    // Match the same server-side collision rule used for player boats
                    // (Heats/GridManager spawn player boats with collidable = collisionMode != DISABLED).
                    // This keeps AI boats colliding with players when the heat has collisions enabled.
                    NMSHandlerImpl.setCollidable(boat, heatCollisionMode != CollisionMode.DISABLED);
                    boatUuid = boat.getUniqueId();
                    lastKnownLocation = boat.getLocation().clone();

                    // Spawn a client-side fake player (NPC) sitting inside the boat.
                    // If the boat was respawned, refresh the NPC with the new entity id.
                    if (fakePlayer == null || fakePlayer.getBoatEntityId() != boat.getEntityId()) {
                        if (fakePlayer != null) {
                            fakePlayer.broadcastHide(boat.getWorld());
                        }
                        fakePlayer = new FakePlayerNPC(plugin, displayName, boat.getWorld(), boat.getEntityId(), boat.getUniqueId());
                        fakePlayer.broadcastShow(boat.getWorld());
                    }
                } finally {
                    // Only the task that still owns the current generation may
                    // clear the flag — an invalidated (older) spawn clearing it
                    // would let ensureSpawned schedule a duplicate boat while
                    // the newer spawn is still in flight.
                    if (generation == spawnGeneration) {
                        spawnPending = false;
                    }
                }
            });
        }

        public void despawnEntity() {
            // Invalidate any spawn that is still scheduled so it does not create
            // an orphan boat after the heat ended.
            spawnGeneration++;
            spawnPending = false;
            Entity entity = getControlledEntity();
            if (entity != null) {
                // Entity.remove() must run on the owning region thread (Folia).
                SchedulerHelper.runTaskFor(plugin, entity, entity::remove);
            }
            // Remove the fake player NPC from every client that can see it.
            if (fakePlayer != null) {
                World npcWorld = lastKnownLocation != null ? lastKnownLocation.getWorld() : null;
                fakePlayer.broadcastHide(npcWorld);
                fakePlayer = null;
            }
            boatUuid = null;
            lastKnownLocation = null;
        }

        public FakePlayerNPC getFakePlayer() {
            return fakePlayer;
        }

        public Entity getControlledEntity() {
            if (boatUuid != null) {
                Entity entity = Bukkit.getEntity(boatUuid);
                if (entity != null && entity.isValid()) {
                    return entity;
                }
            }
            // AI drivers are never real online players — no player fallback here.
            return null;
        }

        public void update(Heats heat, AIRacingLine line) {
            Entity controlledEntity = getControlledEntity();
            if (controlledEntity == null) {
                return;
            }
            // All entity access (isValid/getLocation/setVelocity/setRotation) must
            // run on the boat's region thread on Folia, so dispatch the whole tick
            // there — including the validity check itself.
            SchedulerHelper.runTaskFor(plugin, controlledEntity, () -> {
                if (!controlledEntity.isValid()) {
                    return;
                }
                // No recorded racing line for this track: the AI stays PARKED —
                // no fallback driving (it would just run off the track).
                if (line == null || !line.isUsable()) {
                    return;
                }
                updateLapTime();
                checkForMistake();

                Location currentLoc = controlledEntity.getLocation();
                int resolvedIndex = currentLineIndex >= 0 ? currentLineIndex : 0;
                resolvedIndex = resolveLineIndex(line, currentLoc);
                calculateSpeed(heat, line, currentLoc, resolvedIndex);
                moveBoat(heat, line, controlledEntity, currentLoc, resolvedIndex);

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
            learnedSpeedMultiplier = difficulty.getSpeedMultiplier() + speedBonus;
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

        private void calculateSpeed(Heats heat, AIRacingLine line, Location currentLoc, int resolvedIndex) {
            double desiredSpeed = DEFAULT_THROTTLE * learnedSpeedMultiplier;

            if (line != null && line.isUsable() && currentLoc != null) {
                int closestIndex = resolvedIndex;
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
            // Keep a meaningful floor: braking/mistake/realism multipliers stacking on top
            // of low line speeds used to crush the AI to a crawl (~1 block/s). 0.12 is a
            // balance: high enough to stop the crawl, low enough that genuinely-slow
            // recorded corners (hairpins at ~0.15) are still driven near the recorded pace.
            desiredSpeed = Math.max(0.12, Math.min(1.0, desiredSpeed));

            double response = 0.18 + (difficulty.getReactionTime() * 0.42);
            currentSpeed += (desiredSpeed - currentSpeed) * response;
            currentSpeed = Math.max(0.10, Math.min(1.0, currentSpeed));
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

            // AI drivers do not manage tires/fuel (no pit stops), so the realism
            // penalties must not be able to starve them into a crawl. Keep a 0.65
            // floor so tire/fuel effects still have a visible influence on pace.
            return Math.max(0.65D, factor);
        }

        /**
         * Returns the line point roughly {@code distance} blocks ahead of
         * {@code startIndex}, walking the line and accumulating segment lengths.
         * Recorded points are ~2 blocks apart, so a fixed point-count lookahead of
         * 2-3 was shorter than the ~7 blocks a vanilla ice boat travels per 2-tick
         * update — the steering target ended up BEHIND the boat and corners were
         * cut. A distance-based target (scaled by current speed) keeps the target
         * properly ahead of the boat.
         */
        private Location getSteerTarget(AIRacingLine line, int startIndex, double distance) {
            Location prev = line.getPointAtWrapped(startIndex);
            Vector prevVec = prev.toVector().setY(0.0D);
            double accumulated = 0.0D;
            int count = line.getIdealLineSize();
            for (int i = 0; i < count; i++) {
                Location next = line.getPointAtWrapped(startIndex + i + 1);
                Vector nextVec = next.toVector().setY(0.0D);
                // Horizontal-only distance: the steering math is 2D, so Y noise in a
                // recorded line (boat bob, spawn offset) must not inflate the lookahead.
                accumulated += prevVec.distance(nextVec);
                if (accumulated >= distance) {
                    return next;
                }
                prevVec = nextVec;
            }
            // Line shorter than the requested distance: aim a fixed few points
            // ahead (wrapped) so the direction still points forward.
            return line.getPointAtWrapped(startIndex + 3);
        }

        private void moveBoat(Heats heat, AIRacingLine line, Entity controlledEntity, Location currentLoc, int resolvedIndex) {
            if (line != null && line.isUsable()) {
                int index = resolvedIndex;
                // Distance-based lookahead scaled by the boat's actual speed: at
                // vanilla ice speeds (~3.6 blocks/tick) the boat covers ~7 blocks
                // per 2-tick update, so a fixed 2-3 point (~4-6 blocks) lookahead
                // aimed BEHIND the boat and every corner was cut. Look ~0.3s of
                // travel ahead (clamped) so the AI starts turning before the apex.
                Vector vel = controlledEntity.getVelocity();
                double horizSpeed = Math.sqrt((vel.getX() * vel.getX()) + (vel.getZ() * vel.getZ()));
                double lookAheadBlocks = Math.max(MIN_LOOKAHEAD_BLOCKS,
                        Math.min(MAX_LOOKAHEAD_BLOCKS, horizSpeed * 6.0D));
                Location target = getSteerTarget(line, index, lookAheadBlocks);
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
                // Corner-aware speed: like a player, the AI eases off on tighter
                // turns. Without this, the lerp keeps the velocity magnitude while
                // only rotating the direction, so on ice the AI would blow through
                // corners at full speed. The factor is 1.0 below 25° of turn and
                // eases toward 0.35 for a full 180°.
                // Two signals are combined:
                //  - bendAhead: how much the racing line bends between the current
                //    lookahead target and one lookahead further — anticipates a
                //    corner BEFORE the boat reaches it (real drivers brake into
                //    the apex, not at it);
                //  - headingChange: angle between the travel direction (velocity,
                //    drift-aware) and the target — catches the moment the boat is
                //    already turning.
                Vector heading = boatVelocityDir(controlledEntity, currentLoc);
                double headingChange = Math.toDegrees(Math.acos(clampDot(heading.dot(correctedDirection))));
                double bendAhead = 0.0D;
                Location furtherTarget = getSteerTarget(line, index, lookAheadBlocks * 2.0D);
                if (furtherTarget != null && furtherTarget.getWorld() != null
                        && furtherTarget.getWorld().equals(currentLoc.getWorld())) {
                    Vector segDir = furtherTarget.toVector().subtract(target.toVector()).setY(0.0);
                    if (segDir.lengthSquared() > 0.0001) {
                        bendAhead = Math.toDegrees(Math.acos(clampDot(idealDirection.dot(segDir.normalize()))));
                    }
                }
                double effectiveTurn = Math.max(headingChange, bendAhead);
                double cornerFactor = 1.0D;
                if (effectiveTurn > 25.0D) {
                    cornerFactor = Math.max(0.35, 1.0D - ((effectiveTurn - 25.0D) / 155.0D) * 0.65D);
                }

                // Corner steering: a long straight-line target across a bend aims at
                // the CHORD and cuts the inside of the corner. Keep the long lookahead
                // for speed planning (braking early) but steer toward a SHORTER point
                // so the boat follows the arc through the apex — like a driver who
                // brakes early (long vision) and turns in late (short lookahead).
                if (effectiveTurn > 35.0D) {
                    double shrink = Math.max(0.35D, 1.0D - ((effectiveTurn - 35.0D) / 145.0D));
                    double shortDistance = Math.max(6.0D, lookAheadBlocks * shrink);
                    // Hairpins (>90°): even the shrunken target can sit past the apex
                    // at speed (e.g. 30 * 0.5 = 15 blocks across a hairpin), which cuts
                    // the chord. Clamp to a near-apex distance so the arc is followed
                    // immediately instead of only after the corner braking slows the boat.
                    if (effectiveTurn > 90.0D) {
                        shortDistance = Math.min(shortDistance, 8.0D);
                    }
                    Location shortTarget = getSteerTarget(line, index, shortDistance);
                    if (shortTarget != null && shortTarget.getWorld() != null
                            && shortTarget.getWorld().equals(currentLoc.getWorld())) {
                        Vector shortOffset = shortTarget.toVector().subtract(currentLoc.toVector()).setY(0.0);
                        if (shortOffset.lengthSquared() > 0.0001) {
                            correctedDirection = applySteeringVariance(shortOffset.normalize());
                        }
                    }
                }

                applyThrottle(controlledEntity, correctedDirection, currentSpeed * cornerFactor);

                float yaw = (float) Math.toDegrees(Math.atan2(-correctedDirection.getX(), correctedDirection.getZ()));
                controlledEntity.setRotation(yaw, currentLoc.getPitch());
                currentLineIndex = index;
            } else {
                Vector forward = currentLoc.getDirection().setY(0.0);
                if (forward.lengthSquared() < 0.0001) {
                    return;
                }
                applyThrottle(controlledEntity, applySteeringVariance(forward.normalize()), currentSpeed);
            }
        }

        private static double clampDot(double dot) {
            return Math.max(-1.0, Math.min(1.0, dot));
        }

        /**
         * Horizontal direction the boat is actually travelling in. Prefers the
         * velocity (drift-aware) and falls back to the yaw direction when the
         * boat is barely moving (e.g. first tick at spawn).
         */
        private Vector boatVelocityDir(Entity boat, Location currentLoc) {
            Vector velocity = boat.getVelocity();
            if (velocity != null) {
                Vector horizontal = velocity.clone().setY(0.0);
                if (horizontal.lengthSquared() > 0.0025) {
                    return horizontal.normalize();
                }
            }
            return currentLoc.getDirection().setY(0.0).normalize();
        }

        /**
         * Accelerates/brakes the boat toward its target speed on the current
         * surface, mimicking a player's boat: on ice the low friction lets the
         * velocity build up to ice-boat speeds, while on water drag keeps it
         * slow. The current horizontal velocity is lerped toward
         * {@code direction * speed * surfaceMaxSpeed} so straights reach full
         * ice speed and corners/braking actively pull the velocity down.
         * The lerp is asymmetric — smooth on the throttle (the AI doesn't snap
         * from 0 to the ~73 blocks/s vanilla ice top speed in a couple of ticks)
         * and decisive on the brakes (corner entry) — just like a real driver.
         */
        private void applyThrottle(Entity boat, Vector direction, double speed) {
            double surfaceMax = getSurfaceMaxSpeed(boat.getLocation());
            double targetSpeed = speed * surfaceMax;
            Vector current = boat.getVelocity();
            double currentHorizSpeed = Math.sqrt((current.getX() * current.getX()) + (current.getZ() * current.getZ()));
            double steer = currentHorizSpeed < targetSpeed ? ACCEL_STEER : BRAKE_STEER;
            double newX = current.getX() + ((direction.getX() * targetSpeed) - current.getX()) * steer;
            double newZ = current.getZ() + ((direction.getZ() * targetSpeed) - current.getZ()) * steer;
            double newY = Math.max(-0.08, Math.min(0.08, current.getY()));
            boat.setVelocity(new Vector(newX, newY, newZ));
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

            // Checkpoint ids may have gaps / not be sequential: map the lap
            // ordinal to the REAL id of the next expected checkpoint.
            List<Integer> orderedCheckpointIds = trackManager.getOrderedCheckpointIds(trackNameWS);
            int checkpointsReached = driver.getCheckpointsReached();
            Integer expectedId = checkpointsReached < orderedCheckpointIds.size()
                    ? orderedCheckpointIds.get(checkpointsReached) : null;

            DatabaseManager.RegionData matchedRegion = findMatchingCheckpointRegion(
                    expectedId != null ? checkpointsById.get(expectedId) : null, from, to);

            if (matchedRegion != null) {
                // Heat/driver state is shared across threads on Folia — mutate it
                // on the global scheduler, not on the boat's region thread.
                SchedulerHelper.runTask(plugin, () -> {
                    driver.incrementCheckpoint();
                    driver.setLastCheckpointTime(System.currentTimeMillis());
                    heat.markPositionsDirty();
                });
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
                final Location fromCopy = from.clone();
                final Location toCopy = to.clone();
                final RegionBox regionBox = toRegionBox(from.getWorld(), region);
                // Heat/driver state is shared across threads on Folia — mutate it
                // on the global scheduler, not on the boat's region thread.
                SchedulerHelper.runTask(plugin, () -> {
                    driver.setResetCount(0);
                    heat.passLap(driver, fromCopy, toCopy, regionBox);
                    heat.markPositionsDirty();
                });
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
            int closestIndex = line.getClosestIdealLineIndex(currentLoc, currentLineIndex, LINE_SEARCH_WINDOW);
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
                // Far ahead of the tracked index (e.g. after a reset/teleport or a
                // long straight): catch up proportionally instead of creeping 1 point
                // per update — creeping left the AI steering at stale points behind
                // its real position. Capped so a wrong (overlapping-section) closest
                // match can't make the index leap wildly.
                currentLineIndex = line.advanceIndex(currentLineIndex, Math.max(1, Math.min(16, directDelta / 2)));
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
