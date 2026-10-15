/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_10004
 *  net.minecraft.class_10055
 */
package net.boatlabs.boatlabsmod.accessor;

import net.minecraft.class_10004;
import net.minecraft.class_10055;

public interface TransparentFieldAccessor {
    public void boatlabsmod$setTransparent(boolean var1);

    public boolean boatlabsmod$getTransparent();

    public static TransparentFieldAccessor get(class_10004 boatEntityRenderState) {
        return (TransparentFieldAccessor)boatEntityRenderState;
    }

    public static TransparentFieldAccessor get(class_10055 renderState) {
        return (TransparentFieldAccessor)renderState;
    }
}

