/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1268
 *  net.minecraft.class_1269
 *  net.minecraft.class_1657
 *  net.minecraft.class_1690
 *  net.minecraft.class_1792
 *  net.minecraft.class_1792$class_1793
 *  net.minecraft.class_1937
 */
package net.boatlabs.boatlabsmod.ghost;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1657;
import net.minecraft.class_1690;
import net.minecraft.class_1792;
import net.minecraft.class_1937;

public final class GhostBoat
extends class_1690 {
    public GhostBoat(class_1937 world) {
        super(BoatlabsMod.config.ghostBoatType.entityType, world, () -> new class_1792(new class_1792.class_1793()));
    }

    public boolean method_64491(int paddle) {
        return true;
    }

    public class_1269 method_5688(class_1657 player, class_1268 hand) {
        return class_1269.field_5811;
    }
}

