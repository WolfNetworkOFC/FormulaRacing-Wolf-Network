/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_2338
 *  net.minecraft.class_243
 */
package net.boatlabs.boatlabsmod.region;

import net.boatlabs.boatlabsmod.region.Region;
import net.boatlabs.boatlabsmod.region.RegionType;
import net.minecraft.class_2338;
import net.minecraft.class_243;

public record CuboidRegion(int id, int index, RegionType regionType, class_2338 minP, class_2338 maxP) implements Region
{
    @Override
    public boolean contains(class_243 pos) {
        return pos.field_1352 >= (double)this.minP.method_10263() && pos.field_1352 <= (double)(this.maxP.method_10263() + 1) && pos.field_1350 >= (double)this.minP.method_10260() && pos.field_1350 <= (double)(this.maxP.method_10260() + 1) && pos.field_1351 >= (double)this.minP.method_10264() && pos.field_1351 <= (double)(this.maxP.method_10264() + 1);
    }
}

