/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1921
 *  net.minecraft.class_2338
 *  net.minecraft.class_259
 *  net.minecraft.class_265
 *  net.minecraft.class_4184
 *  net.minecraft.class_4587
 *  net.minecraft.class_4587$class_4665
 *  net.minecraft.class_4588
 *  net.minecraft.class_4597
 *  net.minecraft.class_9974
 *  org.joml.Matrix4f
 *  org.joml.Vector3f
 */
package net.boatlabs.boatlabsmod.region.render;

import java.util.Iterator;
import java.util.function.BiConsumer;
import net.boatlabs.boatlabsmod.region.PolygonalRegion;
import net.boatlabs.boatlabsmod.region.Region;
import net.boatlabs.boatlabsmod.region.RegionType;
import net.minecraft.class_1921;
import net.minecraft.class_2338;
import net.minecraft.class_259;
import net.minecraft.class_265;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4597;
import net.minecraft.class_9974;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class RegionRenderer {
    public static void render(Region region, class_4587 matrices, class_4597 vertexConsumers, class_4184 camera) {
        int alpha = 0x70000000;
        int regionColor = 0x70000000 + (switch (region.regionType()) {
            default -> throw new MatchException(null, null);
            case RegionType.START -> 65280;
            case RegionType.END -> 0xFFFF00;
            case RegionType.CHECKPOINT -> 255;
        });
        matrices.method_22903();
        matrices.method_61958(camera.method_19326().method_22882());
        class_4588 vertexConsumer = vertexConsumers.getBuffer(class_1921.method_23594());
        if (region instanceof PolygonalRegion) {
            PolygonalRegion polygonalRegion = (PolygonalRegion)region;
            Matrix4f matrix = matrices.method_23760().method_23761();
            class_4587.class_4665 entry = matrices.method_23760();
            float minY = region.minP().method_10264();
            float maxY = region.maxP().method_10264() + 1;
            BiConsumer<class_2338, class_2338> drawLine = (pos1, pos2) -> {
                Vector3f normal = new Vector3f((float)(pos2.method_10263() - pos1.method_10263()), (float)(pos2.method_10264() - pos1.method_10264()), (float)(pos2.method_10260() - pos1.method_10260())).normalize();
                vertexConsumer.method_22918(matrix, (float)pos1.method_10263(), (float)pos1.method_10264(), (float)pos1.method_10260()).method_39415(regionColor).method_61959(entry, normal);
                vertexConsumer.method_22918(matrix, (float)pos2.method_10263(), (float)pos2.method_10264(), (float)pos2.method_10260()).method_39415(regionColor).method_61959(entry, normal);
            };
            Iterator<class_2338> points = polygonalRegion.points().iterator();
            class_2338 previousPoint = points.next();
            while (points.hasNext()) {
                class_2338 point = points.next();
                drawLine.accept(previousPoint.method_33096((int)minY), point.method_33096((int)minY));
                drawLine.accept(previousPoint.method_33096((int)maxY), point.method_33096((int)maxY));
                drawLine.accept(previousPoint.method_33096((int)minY), previousPoint.method_33096((int)maxY));
                previousPoint = point;
            }
            class_2338 first = polygonalRegion.points().getFirst();
            drawLine.accept(previousPoint.method_33096((int)minY), first.method_33096((int)minY));
            drawLine.accept(previousPoint.method_33096((int)maxY), first.method_33096((int)maxY));
            drawLine.accept(previousPoint.method_33096((int)minY), previousPoint.method_33096((int)maxY));
        } else {
            float minX = region.minP().method_10263();
            float minY = region.minP().method_10264();
            float minZ = region.minP().method_10260();
            float maxX = region.maxP().method_10263() + 1;
            float maxY = region.maxP().method_10264() + 1;
            float maxZ = region.maxP().method_10260() + 1;
            class_9974.method_62296((class_4587)matrices, (class_4588)vertexConsumer, (class_265)class_259.method_1081((double)minX, (double)minY, (double)minZ, (double)maxX, (double)maxY, (double)maxZ), (double)0.0, (double)0.0, (double)0.0, (int)regionColor);
        }
        matrices.method_22909();
    }
}

