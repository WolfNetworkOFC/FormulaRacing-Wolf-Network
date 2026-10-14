package dev.EfraGroup.formulaRacing.Collisionless;

import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * NMS {@link Boat} subclass with a runtime server-side collision toggle.
 *
 * <p>Vanilla boat-vs-boat collision is what produces the classic "boat lag"
 * when many boats share a track. Overriding {@link #canCollideWith(Entity)}
 * removes that check at the source, which is far more reliable than flipping
 * the Bukkit {@code setCollidable} flag through reflection.</p>
 *
 * <p>Compiled against a remapped CraftBukkit/Paper server jar
 * ({@code net.minecraft.*}). Note the package is {@code vehicle.boat} and the
 * entity-type constants live in {@code EntityTypes} (not {@code EntityType})
 * on 1.21.9+ / Paper 26.x.</p>
 */
public class CollisionlessBoat extends Boat {

    private boolean collisionsEnabled = true;

    public CollisionlessBoat(EntityType<? extends Boat> entityType, Level level, Supplier<Item> dropSupplier) {
        super(entityType, level, dropSupplier);
    }

    /** Enables/disables server-side collision for this boat. */
    public void setCollisionsEnabled(boolean enabled) {
        this.collisionsEnabled = enabled;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return this.collisionsEnabled && super.canCollideWith(entity);
    }
}

