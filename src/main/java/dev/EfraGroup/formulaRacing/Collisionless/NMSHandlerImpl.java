package dev.EfraGroup.formulaRacing.Collisionless;

import dev.EfraGroup.formulaRacing.NMSHandler;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Method;

public class NMSHandlerImpl implements NMSHandler {
    private EntityType boatType = EntityType.OAK_BOAT;

    private static boolean collidableWarning = false;

    private static void setCollidableIfPossible(Entity entity, boolean collidable) {
        try {
            Method setCollidable = Entity.class.getMethod("setCollidable", boolean.class);
            setCollidable.invoke(entity, collidable);
        } catch (Exception e) {
            try {
                Method getHandle = entity.getClass().getMethod("getHandle");
                Object nmsEntity = getHandle.invoke(entity);
                Method setCollidable = nmsEntity.getClass().getMethod("setCollidable", boolean.class);
                setCollidable.invoke(nmsEntity, collidable);
            } catch (Exception e2) {
                if (!collidableWarning) {
                    collidableWarning = true;
                    entity.getServer().getLogger().warning("[FormulaRacing] setCollidable not available");
                }
            }
        }
    }

    /**
     * Public helper so other systems (e.g. AI boats) can apply the same
     * server-side collidable state used for player boats.
     */
    public static void setCollidable(Entity entity, boolean collidable) {
        setCollidableIfPossible(entity, collidable);
    }

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
        Boat boat = (Boat) location.getWorld().spawnEntity(location, boatType);
        setCollidableIfPossible(boat, collidable);
        return boat;
    }

    @Override
    public ChestBoat spawnChestBoat(Location location, boolean collidable) {
        EntityType chestType = switch (this.boatType) {
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
        Boat boat = (Boat) location.getWorld().spawnEntity(location, chestType);
        setCollidableIfPossible(boat, collidable);
        return (ChestBoat) boat;
    }
}
