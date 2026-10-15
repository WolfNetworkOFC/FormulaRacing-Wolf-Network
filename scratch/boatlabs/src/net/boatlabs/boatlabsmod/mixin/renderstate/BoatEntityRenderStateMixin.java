/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_10004
 *  org.spongepowered.asm.mixin.Mixin
 */
package net.boatlabs.boatlabsmod.mixin.renderstate;

import net.boatlabs.boatlabsmod.accessor.TransparentFieldAccessor;
import net.minecraft.class_10004;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value={class_10004.class})
public class BoatEntityRenderStateMixin
implements TransparentFieldAccessor {
    boolean boatlabsmod$transparent;

    @Override
    public void boatlabsmod$setTransparent(boolean b) {
        this.boatlabsmod$transparent = b;
    }

    @Override
    public boolean boatlabsmod$getTransparent() {
        return this.boatlabsmod$transparent;
    }
}

