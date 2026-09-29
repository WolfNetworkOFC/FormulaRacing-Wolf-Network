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

    /** Yaw error (deg) inside which the AI releases the turn keys. */
    private static final double STEER_DEADZONE_DEG = 5.0D;
    /** Horiz speed / desired above which the AI simply lifts the throttle (coast). */
    private static final double COAST_OVERSPEED_RATIO = 1.12D;
    /** Horiz speed / desired above which the AI starts the ice-boat turnaround brake. */
    private static final double BRAKE_OVERSPEED_RATIO = 1.45D;
    /** Yaw error to the reverse of velocity that ends the 180° turnaround. */
    private static final double TURNAROUND_DONE_DEG = 40.0D;
    /** Yaw error to the racing line that ends recovery after a brake. */
    private static final double RECOVERY_DONE_DEG = 45.0D;
    private static final int MAX_TURNAROUND_TICKS = 35;
    private static final int MAX_BRAKING_TICKS = 50;
    private static final int MAX_RECOVERY_TICKS = 60;

    /**
     * Ice-boat brake phases. Real ice racing does not use S (barely any grip);
     * drivers spin ~180° against the velocity vector and hold W so thrust
     * scrubs speed, then realign with the racing line.
     */
    public enum BrakePhase {
        NONE,
        TURN_AROUND,
        BRAKING,
        RECOVERY
    }

    /**
     * Returns how fast (blocks/tick) a boat can realistically travel on the
     * surface below {@code loc}, so the AI drives like a player: ice is fast
     * (low friction, velocity builds), water is drag-limited, land is slow.
     *
     * <p>If the block directly under the boat is snow or another non-driving
     * block, the AI should still detect ice or water beneath it, because snow
     * layers on top of ice do not change how a boat actually moves.
     */
    public static double getSurfaceMaxSpeed(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return 1.0D;
        }

        Block block = loc.getBlock();
        double max = surfaceMaxFor(block.getType());
        if (max < 0.0D) {
            max = surfaceMaxForSkippingSnow(block);
        }
        return max < 0.0D ? 1.0D : max;
    }

    /**
     * Checks the block under {@code block} and keeps going downward while the
     * block is snow, until an ice/water/land block is found or the build limit
     * is reached. This avoids treating snow layers on top of ice as land.
     */
    private static double surfaceMaxForSkippingSnow(Block block) {
        if (block == null || block.getWorld() == null) {
            return -1.0D;
        }

        int maxDepth = 5;
        Block current = block.getRelative(BlockFace.DOWN);
        for (int i = 0; i < maxDepth; i++) {
            if (current == null || current.getWorld() == null) {
                return -1.0D;
            }

            Material type = current.getType();
            if (type == Material.SNOW || type == Material.SNOW_BLOCK) {
                current = current.getRelative(BlockFace.DOWN);
                continue;
            }

            return surfaceMaxFor(type);
        }

        // If we only found snow for several blocks, fall back to a generic solid surface.
        return surfaceMaxFor(Material.STONE);
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
        // 1-tick period: input injection must run every tick like a real client,
        // otherwise deltaRotation/thrust are applied at half vanilla rate.
        heatTasks.put(heat.getId(), SchedulerHelper.runTaskTimer(plugin, () -> updateAI(heat), 0L, 1L));
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
        // Despawn AND unregister the opponents of this heat: only despawning
        // left the AIOpponent (and its Driver) in the map forever after every
        // heat, leaking memory until plugin disable and polluting
        // findByDisplayName/isAIOpponent with stale entries.
        for (Map.Entry<UUID, AIOpponent> entry : aiOpponents.entrySet()) {
            AIOpponent ai = entry.getValue();
            if (ai.getDriver().getHeatId() == heatId) {
                ai.despawnEntity();
                aiOpponents.remove(entry.getKey(), ai);
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
        final double bumpForce = 0.35; // Increased from 0.18 for more noticeable collisions

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
        /** Current ice-brake phase (see {@link BrakePhase}). */
        private BrakePhase brakePhase = BrakePhase.NONE;
        /** Ticks spent in the current brake phase. */
        private int brakeTicks;
        /** Reverse-of-velocity yaw captured when a brake starts (stable scrub target). */
        private double brakeAnchorYaw;
        /**
         * Held steering corruption (deg). Unlike the old per-call white noise,
         * this stays constant between refreshes so inputs read as a human
         * misjudging a corner, not as per-tick jitter.
         */
        private double steeringBiasDeg;
        private long lastBiasUpdateMs;

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
            this.steeringBiasDeg = 0.0D;
            this.lastBiasUpdateMs = 0L;
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
            // this guard, the 1-tick update loop schedules another boat before the
            // first one exists, leaking orphan boats that are never tracked.
            if (spawnPending) {
                return;
            }

            Location spawn = resolveSpawnLocation(heat);
            if (spawn == null || spawn.getWorld() == null) {
                plugin.getDebugManager().logRaceSystem("[AI] Spawn failed for " + displayName + ": spawn or world is null");
                return;
            }

            // Sem chunk carregada não há entity viva para conduzir, e spawnar aqui
            // recriaria o barco em cada tick em que a região carrega — o que
            // multiplicava milhares de barcos órfãos sempre que o jogador se
            // afastava. O barco legítimo continua a existir no mundo, por isso o
            // boatUuid é mantido intacto: quando a chunk volta a estar carregada,
            // getControlledEntity() volta a encontrá-lo e o AI retoma sem respawn.
            World spawnWorld = spawn.getWorld();
            if (!spawnWorld.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) {
                return;
            }

            // A chunk está carregada mas a entity não existe (barco destruído ou
            // removido por outro plugin). A referência antiga aponta para algo morto,
            // por isso é abandonada antes de criar a substituição.
            boatUuid = null;

            // Entity creation AND the block reads of the safe-Y search must run on
            // the world's region thread (Folia). Boat is an interface, so we spawn
            // the concrete OAK_BOAT entity type.
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
                    // Block reads (safe-Y search) are only legal here, on the region
                    // thread that owns this chunk — never on the global scheduler.
                    Location safeSpawn = findSafeSpawnLocation(finalSpawn);
                    Boat boat = (Boat) safeSpawn.getWorld().spawnEntity(safeSpawn, EntityType.OAK_BOAT);
                    boat.customName(Component.text(displayName));
                    boat.setCustomNameVisible(true);
                    boat.setInvulnerable(true);
                    boat.setGravity(true);
                    boat.setPersistent(false);
                    boat.setSilent(true);
                    boat.setRotation(safeSpawn.getYaw(), safeSpawn.getPitch());
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
            // Cap speed multiplier at 1.3 to prevent AI from becoming impossible
            learnedSpeedMultiplier = Math.min(1.3, difficulty.getSpeedMultiplier() + speedBonus);
            learnedLineAccuracy = Math.min(1.0, difficulty.getLineAccuracy() + accuracyBonus);
            learnedErrorRate = Math.max(0.01, difficulty.getErrorRate() - (learningProgress * 0.08));
        }

        private void checkForMistake() {
            long currentTime = System.currentTimeMillis();

            boolean wasMistake = isMakingMistake;
            if (isMakingMistake) {
                long duration = currentTime - mistakeStartTime;
                long maxDuration = (long) (2800 - (learnedLineAccuracy * 1600));
                if (duration > maxDuration) {
                    isMakingMistake = false;
                }
            } else if (currentTime - lastMistakeTime >= 10000L
                    && ThreadLocalRandom.current().nextDouble() < learnedErrorRate) {
                isMakingMistake = true;
                mistakeStartTime = currentTime;
                lastMistakeTime = currentTime;
                mistakesMade++;
            }

            // Refresh the held steering bias: immediately when the mistake state
            // flips, otherwise rarely enough that it reads as a stable error.
            long biasInterval = isMakingMistake ? 250L : 1200L;
            if (wasMistake != isMakingMistake || currentTime - lastBiasUpdateMs >= biasInterval) {
                lastBiasUpdateMs = currentTime;
                steeringBiasDeg = isMakingMistake
                        ? ThreadLocalRandom.current().nextDouble(-35.0, 35.0)
                        : ThreadLocalRandom.current().nextDouble(-1.0, 1.0)
                                * (1.0 - learnedLineAccuracy) * 18.0;
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
            if (!(controlledEntity instanceof Boat boat)) {
                return;
            }

            Vector velocity = controlledEntity.getVelocity();
            double horizSpeed = Math.sqrt((velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ()));
            Vector velDir = boatVelocityDir(controlledEntity, currentLoc);
            double velYaw = Math.toDegrees(Math.atan2(-velDir.getX(), velDir.getZ()));

            double yawErr;
            double desiredBt;
            if (line != null && line.isUsable()) {
                int index = resolvedIndex;
                // Look ~0.3s of travel ahead (clamped) so the AI starts turning
                // before the apex — same distance-based lookahead as before, but
                // it now only feeds the yaw error (velocity is never lerped).
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

                Vector heading = velDir;
                double headingChange = Math.toDegrees(Math.acos(clampDot(heading.dot(idealDirection))));
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

                // Shorter arc target through the apex so the boat does not cut
                // the chord of a tight corner (pure-pursuit refinement).
                if (effectiveTurn > 35.0D) {
                    double shrink = Math.max(0.35D, 1.0D - ((effectiveTurn - 35.0D) / 145.0D));
                    double shortDistance = Math.max(6.0D, lookAheadBlocks * shrink);
                    if (effectiveTurn > 90.0D) {
                        shortDistance = Math.min(shortDistance, 8.0D);
                    }
                    Location shortTarget = getSteerTarget(line, index, shortDistance);
                    if (shortTarget != null && shortTarget.getWorld() != null
                            && shortTarget.getWorld().equals(currentLoc.getWorld())) {
                        Vector shortOffset = shortTarget.toVector().subtract(currentLoc.toVector()).setY(0.0);
                        if (shortOffset.lengthSquared() > 0.0001) {
                            idealDirection = shortOffset.normalize();
                        }
                    }
                }

                double targetYaw = Math.toDegrees(Math.atan2(-idealDirection.getX(), idealDirection.getZ()));
                yawErr = wrapDegrees(targetYaw + steeringBiasDeg - currentLoc.getYaw());

                double surfaceMax = getSurfaceMaxSpeed(currentLoc);
                desiredBt = currentSpeed * surfaceMax * cornerFactor;

                if (line.isNearBrakingPoint(currentLoc, 6.0D)
                        && horizSpeed > desiredBt * BRAKE_OVERSPEED_RATIO) {
                    maybeBeginBrake(velYaw, horizSpeed, desiredBt);
                }
                currentLineIndex = index;
            } else {
                // No line: hold heading, still obey throttle/brake logic.
                yawErr = 0.0D;
                desiredBt = currentSpeed * getSurfaceMaxSpeed(currentLoc);
            }

            updateBrakePhase(currentLoc, velYaw, horizSpeed, desiredBt, yawErr);

            boolean left = false;
            boolean right = false;
            boolean up = false;
            boolean down = false;

            double steerErr;
            if (brakePhase == BrakePhase.NONE || brakePhase == BrakePhase.RECOVERY) {
                // Normal line following (RECOVERY re-aims at the racing line).
                steerErr = yawErr;
            } else {
                // TURN_AROUND / BRAKING: point against the velocity vector.
                steerErr = wrapDegrees(brakeAnchorYaw + steeringBiasDeg * 0.5D - currentLoc.getYaw());
            }

            // Proportional steering. The old code was bang-bang and widened its
            // deadzone by the boat's current turn rate, so a held key kept
            // widening the band it was already inside: the AI kept steering past
            // the target, flipped sign, and circled the racing line.
            //
            // Instead, pick a turn rate from the error (small error -> small
            // correction, no oscillation; big corner -> real turn rate), and only
            // hold a key when the turn it produces cannot overshoot the target.
            double absErr = Math.abs(steerErr);
            double turnScale = Math.max(1.0D, Math.min(AIBoatController.MAX_TURN_SCALE, absErr / 25.0D));
            double turnThisTick = AIBoatController.TURN_PER_TICK_DEG * turnScale;
            // Release the key once the remaining error is within what this tick's
            // turn would consume, plus a small margin for the boat's inertia.
            double releaseBelow = turnThisTick + STEER_DEADZONE_DEG;
            if (steerErr > releaseBelow) {
                right = true;
            } else if (steerErr < -releaseBelow) {
                left = true;
            }

            if (brakePhase != BrakePhase.NONE) {
                // Ice brake: hold W so yaw-aligned thrust scrubs speed. Never S —
                // backward input barely slows a boat on ice.
                up = true;
            } else if (horizSpeed < desiredBt * 0.98D || horizSpeed < 0.05D) {
                up = true;
            } else if (horizSpeed > desiredBt * COAST_OVERSPEED_RATIO) {
                // Lift throttle first (natural ice coast); hard overspeed
                // already triggered the brake state machine above.
                up = false;
            } else {
                up = true;
            }

            AIBoatController.drive(boat, left, right, up, down, turnScale);
        }

        private void maybeBeginBrake(double velYaw, double horizSpeed, double desiredBt) {
            if (brakePhase != BrakePhase.NONE) {
                return;
            }
            if (horizSpeed > desiredBt * BRAKE_OVERSPEED_RATIO) {
                beginBrake(velYaw);
            }
        }

        private void beginBrake(double velYaw) {
            brakePhase = BrakePhase.TURN_AROUND;
            brakeTicks = 0;
            brakeAnchorYaw = velYaw + 180.0D;
        }

        private void updateBrakePhase(Location currentLoc, double velYaw, double horizSpeed,
                                      double desiredBt, double lineYawErr) {
            if (brakePhase == BrakePhase.NONE) {
                // Opportunistic brake when massively overspeed for a slow target.
                if (horizSpeed > desiredBt * BRAKE_OVERSPEED_RATIO
                        && desiredBt < getSurfaceMaxSpeed(currentLoc) * 0.55D) {
                    beginBrake(velYaw);
                }
                return;
            }

            brakeTicks++;
            switch (brakePhase) {
                case TURN_AROUND -> {
                    double reverseYaw = velYaw + 180.0D;
                    double errToReverse = Math.abs(wrapDegrees(reverseYaw - currentLoc.getYaw()));
                    if (errToReverse <= TURNAROUND_DONE_DEG || brakeTicks >= MAX_TURNAROUND_TICKS) {
                        brakeAnchorYaw = reverseYaw;
                        brakePhase = BrakePhase.BRAKING;
                        brakeTicks = 0;
                    }
                }
                case BRAKING -> {
                    // Keep scrubbing against travel; refresh anchor so a
                    // deflected velocity vector is still opposed.
                    brakeAnchorYaw = velYaw + 180.0D;
                    boolean slowEnough = horizSpeed <= Math.max(desiredBt * 1.08D, desiredBt + 0.05D);
                    if (slowEnough || brakeTicks >= MAX_BRAKING_TICKS) {
                        brakePhase = BrakePhase.RECOVERY;
                        brakeTicks = 0;
                    }
                }
                case RECOVERY -> {
                    if (Math.abs(lineYawErr) <= RECOVERY_DONE_DEG || brakeTicks >= MAX_RECOVERY_TICKS) {
                        brakePhase = BrakePhase.NONE;
                        brakeTicks = 0;
                    }
                }
                case NONE -> {
                }
            }
        }

        private static double wrapDegrees(double deg) {
            deg %= 360.0D;
            if (deg >= 180.0D) {
                deg -= 360.0D;
            }
            if (deg < -180.0D) {
                deg += 360.0D;
            }
            return deg;
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

        /**
         * Checks whether a block is safe for boat spawn. This must be called on the
         * region thread for the block's world, because Folia forbids reading
         * {@code Block} from unrelated scheduler threads.
         */
        private boolean isSafeSpawnLocation(Location loc) {
            if (loc == null || loc.getWorld() == null) {
                return false;
            }

            Block block = loc.getBlock();
            Material type = block.getType();
            return type == Material.WATER || type == Material.ICE ||
                   type == Material.BLUE_ICE || type == Material.PACKED_ICE ||
                   type == Material.FROSTED_ICE || type.isAir();
        }

        /**
         * Finds a nearby safe spawn by testing multiple Y offsets. MUST be called
         * on the region thread that owns this location's chunk — Folia forbids
         * block reads ({@link #isSafeSpawnLocation}) from any other thread. Callers
         * schedule this inside runTaskAtLocation; it never blocks and falls back
         * to the original location when no offset is safe.
         */
        private Location findSafeSpawnLocation(Location original) {
            if (original == null || original.getWorld() == null) {
                return original;
            }

            // Prefer the configured position first, then ABOVE it (falling onto
            // the surface is harmless), and only then below. Scanning bottom-up
            // matched deep water BELOW the surface first and boats appeared
            // submerged ("under the map") on water tracks.
            int[] yOffsets = {0, 1, 2, -1, -2};
            for (int y : yOffsets) {
                Location candidate = original.clone().add(0, y, 0);
                if (isSafeSpawnLocation(candidate)) {
                    return candidate;
                }
            }
            return original;
        }

        /**
         * Resolves the raw spawn candidate for this AI driver. Grid positions are
         * preferred, falling back to the track spawn. This performs NO block/world
         * access: it runs on the global scheduler thread, where Folia forbids
         * reading blocks — the safe-Y search happens later, on the region thread
         * (see {@link #findSafeSpawnLocation}).
         */
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
