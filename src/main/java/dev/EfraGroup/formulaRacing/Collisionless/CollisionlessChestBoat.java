package dev.EfraGroup.formulaRacing.Collisionless;

import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * NMS {@link ChestBoat} counterpart of {@link CollisionlessBoat}: a chest boat
 * whose server-side collision can be toggled at runtime via
 * {@link #canCollideWith(Entity)}.
 *
 * <p>Compiled against a remapped CraftBukkit/Paper server jar
 * ({@code net.minecraft.*}, package {@code vehicle.boat}).</p>
 */
public class CollisionlessChestBoat extends ChestBoat {

    private boolean collisionsEnabled = true;

    public CollisionlessChestBoat(EntityType<? extends ChestBoat> entityType, Level level, Supplier<Item> dropSupplier) {
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

