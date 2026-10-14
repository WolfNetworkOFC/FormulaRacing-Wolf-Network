package dev.EfraGroup.formulaRacing.Collisionless;

import dev.EfraGroup.formulaRacing.NMSHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * NMS-backed boat spawner.
 *
 * <p>Instead of spawning a vanilla boat and flipping a Bukkit collidable flag
 * through reflection, this builds a {@link CollisionlessBoat} /
 * {@link CollisionlessChestBoat} (and the {@link CollisionlessRaft} /
 * {@link CollisionlessChestRaft} raft variants) — NMS subclasses that override
 * {@code canCollideWith}. That removes the server-side boat-vs-boat collision
 * check at the source, which is what causes the classic "boat lag" when many
 * boats share a track, and is far more reliable than the reflection flag.</p>
 *
 * <p>Compiled against a remapped CraftBukkit/Paper server jar
 * ({@code net.minecraft.*}). The Bukkit→NMS bridge ({@code getHandle()}) is
 * resolved reflectively so the code does not hard-code a {@code v1_21_Rx}
 * package and keeps compiling across server updates. If the NMS constructor
 * signature ever changes, {@link #spawnBoat} / {@link #spawnChestBoat} fall
 * back to a plain vanilla spawn, so the plugin never crashes (the boat just
 * keeps default collisions).</p>
 */
public class NMSHandlerImpl implements NMSHandler {

    private EntityType boatType = EntityType.OAK_BOAT;

    @Override
    public void setBoatType(String boatType) {
        try {
            this.boatType = EntityType.valueOf(boatType.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.boatType = EntityType.OAK_BOAT;
        }
    }

    @Override
    public Boat spawnBoat(Location location, boolean collidable) {
        if (this.boatType != null && isSupportedBoat(this.boatType)) {
            try {
                ServerLevel level = nmsLevel(location.getWorld());
                var supplier = (java.util.function.Supplier<Item>) () -> dropItemFor(this.boatType);
                net.minecraft.world.entity.vehicle.boat.AbstractBoat boat =
                        (this.boatType == EntityType.BAMBOO_RAFT)
                                ? new CollisionlessRaft(
                                        net.minecraft.world.entity.EntityTypes.BAMBOO_RAFT, level, supplier)
                                : new CollisionlessBoat(nmsBoatTypeFor(this.boatType), level, supplier);
                boat.snapTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
                setCollisionsEnabled(boat, collidable);
                level.addFreshEntity(boat, CreatureSpawnEvent.SpawnReason.COMMAND);
                return (Boat) boat.getBukkitEntity();
            } catch (Throwable t) {
                // Unexpected NMS change — fall through to the vanilla spawn below.
            }
        }
        // Last-resort fallback (unknown type or NMS failure). Bukkit no longer
        // exposes setCollidable on 26.2, so this spawn keeps default collisions.
        return (Boat) location.getWorld().spawnEntity(location, this.boatType);
    }

    @Override
    public ChestBoat spawnChestBoat(Location location, boolean collidable) {
        if (this.boatType != null && isSupportedBoat(this.boatType)) {
            try {
                ServerLevel level = nmsLevel(location.getWorld());
                var supplier = (java.util.function.Supplier<Item>) () -> dropItemFor(this.boatType);
                net.minecraft.world.entity.vehicle.boat.AbstractBoat boat =
                        (this.boatType == EntityType.BAMBOO_RAFT)
                                ? new CollisionlessChestRaft(
                                        net.minecraft.world.entity.EntityTypes.BAMBOO_CHEST_RAFT, level, supplier)
                                : new CollisionlessChestBoat(nmsChestTypeFor(this.boatType), level, supplier);
                boat.snapTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
                setCollisionsEnabled(boat, collidable);
                level.addFreshEntity(boat, CreatureSpawnEvent.SpawnReason.COMMAND);
                return (ChestBoat) boat.getBukkitEntity();
            } catch (Throwable t) {
                // Unexpected NMS change — fall through to the vanilla spawn below.
            }
        }
        return (ChestBoat) location.getWorld().spawnEntity(location, chestBukkitTypeFor(this.boatType));
    }

    /**
     * Spawns an OAK collisionless boat with the given collision state. Used by
     * the AI opponent system, which always rides a plain oak boat.
     */
    public static Boat spawnStaticBoat(Location location, boolean collidable) {
        NMSHandlerImpl impl = new NMSHandlerImpl();
        impl.setBoatType("OAK_BOAT");
        return impl.spawnBoat(location, collidable);
    }

    /**
     * Runtime collision toggle for an already-spawned Bukkit boat. Resolves the
     * NMS handle and, when it is one of our collisionless subclasses, flips the
     * flag that drives {@code canCollideWith}. Boats not spawned by us are left
     * untouched (Bukkit no longer exposes a generic {@code setCollidable} on
     * 26.2, and every FormulaRacing boat now goes through {@link #spawnBoat}).
     */
    public static void setCollidable(Entity entity, boolean collidable) {
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            setCollisionsEnabled((net.minecraft.world.entity.vehicle.boat.AbstractBoat) handle, collidable);
        } catch (Exception ignored) {
            // Not one of our boats, or reflection unavailable — nothing to do.
        }
    }

    // ---- helpers -----------------------------------------------------------

    /** Applies the collision flag to whichever collisionless subclass we built. */
    private static void setCollisionsEnabled(net.minecraft.world.entity.vehicle.boat.AbstractBoat boat, boolean enabled) {
        if (boat instanceof CollisionlessBoat cb) {
            cb.setCollisionsEnabled(enabled);
        } else if (boat instanceof CollisionlessChestBoat ccb) {
            ccb.setCollisionsEnabled(enabled);
        } else if (boat instanceof CollisionlessRaft cr) {
            cr.setCollisionsEnabled(enabled);
        } else if (boat instanceof CollisionlessChestRaft ccr) {
            ccr.setCollisionsEnabled(enabled);
        }
    }

    /** Reflectively resolves the NMS {@link ServerLevel} for a Bukkit world. */
    private ServerLevel nmsLevel(World world) throws Exception {
        Object handle = world.getClass().getMethod("getHandle").invoke(world);
        return (ServerLevel) handle;
    }

    /**
     * NMS entity type for a wood boat (not bamboo raft, which is a {@code Raft}).
     * Returns {@code null} for raft/unknown types.
     */
    private net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.vehicle.boat.Boat>
    nmsBoatTypeFor(EntityType type) {
        return switch (type) {
            case OAK_BOAT -> net.minecraft.world.entity.EntityTypes.OAK_BOAT;
            case SPRUCE_BOAT -> net.minecraft.world.entity.EntityTypes.SPRUCE_BOAT;
            case BIRCH_BOAT -> net.minecraft.world.entity.EntityTypes.BIRCH_BOAT;
            case JUNGLE_BOAT -> net.minecraft.world.entity.EntityTypes.JUNGLE_BOAT;
            case ACACIA_BOAT -> net.minecraft.world.entity.EntityTypes.ACACIA_BOAT;
            case DARK_OAK_BOAT -> net.minecraft.world.entity.EntityTypes.DARK_OAK_BOAT;
            case MANGROVE_BOAT -> net.minecraft.world.entity.EntityTypes.MANGROVE_BOAT;
            case CHERRY_BOAT -> net.minecraft.world.entity.EntityTypes.CHERRY_BOAT;
            default -> null; // BAMBOO_RAFT (Raft) and unknown
        };
    }

    /**
     * NMS entity type for a chest boat (not bamboo chest raft). Returns
     * {@code null} for raft/unknown types.
     */
    private net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.vehicle.boat.ChestBoat>
    nmsChestTypeFor(EntityType type) {
        return switch (type) {
            case OAK_BOAT -> net.minecraft.world.entity.EntityTypes.OAK_CHEST_BOAT;
            case SPRUCE_BOAT -> net.minecraft.world.entity.EntityTypes.SPRUCE_CHEST_BOAT;
            case BIRCH_BOAT -> net.minecraft.world.entity.EntityTypes.BIRCH_CHEST_BOAT;
            case JUNGLE_BOAT -> net.minecraft.world.entity.EntityTypes.JUNGLE_CHEST_BOAT;
            case ACACIA_BOAT -> net.minecraft.world.entity.EntityTypes.ACACIA_CHEST_BOAT;
            case DARK_OAK_BOAT -> net.minecraft.world.entity.EntityTypes.DARK_OAK_CHEST_BOAT;
            case MANGROVE_BOAT -> net.minecraft.world.entity.EntityTypes.MANGROVE_CHEST_BOAT;
            case CHERRY_BOAT -> net.minecraft.world.entity.EntityTypes.CHERRY_CHEST_BOAT;
            default -> null; // BAMBOO_CHEST_RAFT (ChestRaft) and unknown
        };
    }

    /** Whether we can build a collisionless NMS boat for this Bukkit type. */
    private static boolean isSupportedBoat(EntityType type) {
        return switch (type) {
            case OAK_BOAT, SPRUCE_BOAT, BIRCH_BOAT, JUNGLE_BOAT, ACACIA_BOAT,
                 DARK_OAK_BOAT, MANGROVE_BOAT, CHERRY_BOAT, BAMBOO_RAFT -> true;
            default -> false;
        };
    }

    private EntityType chestBukkitTypeFor(EntityType type) {
        return switch (type) {
            case OAK_BOAT -> EntityType.OAK_CHEST_BOAT;
            case SPRUCE_BOAT -> EntityType.SPRUCE_CHEST_BOAT;
            case BIRCH_BOAT -> EntityType.BIRCH_CHEST_BOAT;
            case JUNGLE_BOAT -> EntityType.JUNGLE_CHEST_BOAT;
            case ACACIA_BOAT -> EntityType.ACACIA_CHEST_BOAT;
            case DARK_OAK_BOAT -> EntityType.DARK_OAK_CHEST_BOAT;
            case MANGROVE_BOAT -> EntityType.MANGROVE_CHEST_BOAT;
            case CHERRY_BOAT -> EntityType.CHERRY_CHEST_BOAT;
            case BAMBOO_RAFT -> EntityType.BAMBOO_CHEST_RAFT;
            default -> EntityType.OAK_CHEST_BOAT;
        };
    }

    /** The item a boat of this wood type drops when broken. */
    private Item dropItemFor(EntityType type) {
        return switch (type) {
            case SPRUCE_BOAT -> Items.SPRUCE_BOAT;
            case BIRCH_BOAT -> Items.BIRCH_BOAT;
            case JUNGLE_BOAT -> Items.JUNGLE_BOAT;
            case ACACIA_BOAT -> Items.ACACIA_BOAT;
            case DARK_OAK_BOAT -> Items.DARK_OAK_BOAT;
            case MANGROVE_BOAT -> Items.MANGROVE_BOAT;
            case CHERRY_BOAT -> Items.CHERRY_BOAT;
            case BAMBOO_RAFT -> Items.BAMBOO_RAFT;
            default -> Items.OAK_BOAT;
        };
    }
}
