/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_2338
 *  net.minecraft.class_243
 */
package net.boatlabs.boatlabsmod.region;

import java.util.List;
import net.boatlabs.boatlabsmod.region.Region;
import net.boatlabs.boatlabsmod.region.RegionType;
import net.minecraft.class_2338;
import net.minecraft.class_243;

public record PolygonalRegion(int id, int index, RegionType regionType, class_2338 minP, class_2338 maxP, List<class_2338> points) implements Region
{
    @Override
    public boolean contains(class_243 pos) {
        if (pos.field_1351 > (double)(this.maxP.method_10264() + 1) || pos.field_1351 < (double)this.minP.method_10264()) {
            return false;
        }
        boolean inside = false;
        int pointCount = this.points.size();
        int xOld = this.points.get(pointCount - 1).method_10263();
        int zOld = this.points.get(pointCount - 1).method_10260();
        for (int i = 0; i < pointCount; ++i) {
            int z2;
            int z1;
            int x2;
            int x1;
            int xNew = this.points.get(i).method_10263();
            int zNew = this.points.get(i).method_10260();
            if ((double)xNew == pos.field_1352 && (double)zNew == pos.field_1350) {
                return true;
            }
            if (xNew > xOld) {
                x1 = xOld;
                x2 = xNew;
                z1 = zOld;
                z2 = zNew;
            } else {
                x1 = xNew;
                x2 = xOld;
                z1 = zNew;
                z2 = zOld;
            }
            if ((double)x1 <= pos.field_1352 && pos.field_1352 <= (double)x2) {
                long crossProduct = ((long)pos.field_1350 - (long)z1) * (long)(x2 - x1) - ((long)z2 - (long)z1) * (long)(pos.field_1352 - (double)x1);
                if (crossProduct == 0L) {
                    if ((double)z1 <= pos.field_1350 == pos.field_1350 <= (double)z2) {
                        return true;
                    }
                } else if (crossProduct < 0L && (double)x1 != pos.field_1352) {
                    inside = !inside;
                }
            }
            xOld = xNew;
            zOld = zNew;
        }
        return inside;
    }
}

