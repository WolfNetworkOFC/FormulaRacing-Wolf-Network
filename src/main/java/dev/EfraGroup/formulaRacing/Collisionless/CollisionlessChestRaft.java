package dev.EfraGroup.formulaRacing.Collisionless;

import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.ChestRaft;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * NMS {@link ChestRaft} subclass (bamboo chest raft) with a runtime server-side
 * collision toggle. Counterpart of {@link CollisionlessChestBoat} for the
 * {@code ChestRaft} family on 1.21.9+ / Paper 26.x.
 */
public class CollisionlessChestRaft extends ChestRaft {

    private boolean collisionsEnabled = true;

    public CollisionlessChestRaft(EntityType<? extends ChestRaft> entityType, Level level, Supplier<Item> dropSupplier) {
        super(entityType, level, dropSupplier);
    }

    /** Enables/disables server-side collision for this raft. */
    public void setCollisionsEnabled(boolean enabled) {
        this.collisionsEnabled = enabled;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return this.collisionsEnabled && super.canCollideWith(entity);
    }
}
