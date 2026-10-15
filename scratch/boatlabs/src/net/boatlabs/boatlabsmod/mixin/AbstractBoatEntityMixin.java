/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_10255
 *  net.minecraft.class_1297
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package net.boatlabs.boatlabsmod.mixin;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.ghost.GhostBoat;
import net.boatlabs.boatlabsmod.ghost.GhostPlayer;
import net.minecraft.class_10255;
import net.minecraft.class_1297;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={class_10255.class}, priority=2000)
public class AbstractBoatEntityMixin {
    @Inject(method={"collidesWith"}, at={@At(value="HEAD")}, cancellable=true)
    void boatlabsmod$disallowGhostCollision(class_1297 other, CallbackInfoReturnable<Boolean> cir) {
        if (other instanceof GhostBoat) {
            boolean useGhostCollisions = BoatlabsMod.ghostCollisionAllowed && BoatlabsMod.config.allowGhostCollision;
            cir.setReturnValue((Object)useGhostCollisions);
            return;
        }
        if (other instanceof GhostPlayer) {
            cir.setReturnValue((Object)false);
        }
    }
}

