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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
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

    // BrakePhase moved to its own file: dev.EfraGroup.formulaRacing.AI.BrakePhase
    // TrafficState moved to its own file: dev.EfraGroup.formulaRacing.AI.TrafficState

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
            max = surfaceMaxForSkippingNonSurface(block);
        }
        return max < 0.0D ? 1.0D : max;
    }

    /**
     * Fastest surface speed a boat can reach (blue ice, blocks/tick). Used as
     * the reference scale when converting an absolute recorded speed back into
     * the 0..1 fraction that marker derivation and previews expect.
     */
    public static double getBlueIceMaxSpeed() {
        return BLUE_ICE_MAX_SPEED;
    }

    /**
     * Checks the block under {@code block} and keeps going downward while the
     * block is a non-driving surface (snow, carpets, tall grass, flowers,
     * slabs' air gaps, etc.), until an ice/water/land block is found or the
     * build limit is reached. This avoids treating decorative layers on top of
     * ice as land.
     */
    private static double surfaceMaxForSkippingNonSurface(Block block) {
        if (block == null || block.getWorld() == null) {
            return -1.0D;
        }

        int maxDepth = 8;
        Block current = block.getRelative(BlockFace.DOWN);
        for (int i = 0; i < maxDepth; i++) {
            if (current == null || current.getWorld() == null) {
                return -1.0D;
            }

            Material type = current.getType();
            if (isNonSurface(type)) {
                current = current.getRelative(BlockFace.DOWN);
                continue;
            }

            return surfaceMaxFor(type);
        }

        // If we only found non-surface blocks for several blocks, fall back to a generic solid surface.
        return surfaceMaxFor(Material.STONE);
    }

    private static boolean isNonSurface(Material type) {
        if (type == null) {
            return false;
        }
        return type == Material.SNOW || type == Material.SNOW_BLOCK
                || type == Material.WHITE_CARPET || type.name().endsWith("_CARPET")
                || type == Material.SHORT_GRASS || type == Material.TALL_GRASS
                || type == Material.FERN || type == Material.LARGE_FERN
                || type == Material.DEAD_BUSH || type == Material.VINE
                || type == Material.LILY_PAD || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS
                || type.name().endsWith("_FLOWERS") || type.name().endsWith("_SAPLING");
    }

    private static double surfaceMaxFor(Material type) {
        if (type == null) {
            return -1.0D;
        }
        String name = type.name();
        if (name.endsWith("_SLAB") || name.endsWith("_STAIRS") || name.endsWith("_WALL")) {
            return LAND_MAX_SPEED;
        }
        if (name.contains("LAVA") || name.contains("MAGMA") || name.contains("FIRE")) {
            return LAND_MAX_SPEED; // not drivable, but avoid div-by-zero
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

    // AIDifficulty moved to its own file: dev.EfraGroup.formulaRacing.AI.AIDifficulty

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

        if (heat.getHeatState() == HeatState.RACING
                && heat.getCollisionMode() != CollisionMode.DISABLED) {
            checkAICollisions(opponentsInHeat, heat.getCollisionMode());
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

    private void checkAICollisions(List<AIOpponent> opponents, CollisionMode collisionMode) {
        final double collisionDistanceSquared = 4.0;
        // LOW is a light nudge; HIGH keeps the stronger contact used by the
        // original system. DISABLED never reaches this method.
        final double bumpForce = collisionMode == CollisionMode.LOW ? 0.15D : 0.35D;

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
}
