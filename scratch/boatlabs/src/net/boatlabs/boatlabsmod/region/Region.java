/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
 *  net.minecraft.class_2338
 *  net.minecraft.class_2382
 *  net.minecraft.class_243
 */
package net.boatlabs.boatlabsmod.region;

import net.boatlabs.boatlabsmod.region.CuboidRegion;
import net.boatlabs.boatlabsmod.region.PolygonalRegion;
import net.boatlabs.boatlabsmod.region.RegionType;
import net.boatlabs.boatlabsmod.region.render.RegionRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.class_2338;
import net.minecraft.class_2382;
import net.minecraft.class_243;

public sealed interface Region
permits CuboidRegion, PolygonalRegion {
    public int id();

    public int index();

    public class_2338 minP();

    public class_2338 maxP();

    public RegionType regionType();

    public boolean contains(class_243 var1);

    default public class_2338 center() {
        class_2338 sum = this.minP().method_10081((class_2382)this.maxP());
        return new class_2338(sum.method_10263() / 2, sum.method_10264() / 2, sum.method_10260() / 2);
    }

    default public void render(WorldRenderContext ctx) {
        RegionRenderer.render(this, ctx.matrixStack(), ctx.consumers(), ctx.camera());
    }
}

