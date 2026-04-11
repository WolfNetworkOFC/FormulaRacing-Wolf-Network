/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 *
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.vehicle.ChestRaft
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.level.Level
 */
package dev.EfraGroup.formulaRacing.Collisionless;

import com.google.common.base.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.ChestRaft;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class CollisionlessChestRaft
        extends ChestRaft {
    public CollisionlessChestRaft(EntityType<? extends ChestRaft> entityType, Level level, Supplier<Item> dropSupplier) {
        super(entityType, level, dropSupplier);
    }

    public boolean canCollideWith(Entity entity) {
        return false;
    }
}