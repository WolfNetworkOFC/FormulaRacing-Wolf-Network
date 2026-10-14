package dev.EfraGroup.formulaRacing.Collisionless;

import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.Raft;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * NMS {@link Raft} subclass (bamboo raft) with a runtime server-side collision
 * toggle. Counterpart of {@link CollisionlessBoat} for the {@code Raft} family,
 * which is a sibling of {@code Boat} (both extend {@code AbstractBoat}) on
 * 1.21.9+ / Paper 26.x.
 */
public class CollisionlessRaft extends Raft {

    private boolean collisionsEnabled = true;

    public CollisionlessRaft(EntityType<? extends Raft> entityType, Level level, Supplier<Item> dropSupplier) {
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
