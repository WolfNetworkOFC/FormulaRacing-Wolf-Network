/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_10055
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package net.boatlabs.boatlabsmod.mixin.renderstate;

import net.boatlabs.boatlabsmod.accessor.TransparentFieldAccessor;
import net.minecraft.class_10055;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={class_10055.class})
public class PlayerEntityRenderStateMixin
implements TransparentFieldAccessor {
    @Unique
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

