/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_2561
 *  net.minecraft.class_329
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.boatlabs.boatlabsmod.mixin;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.minecraft.class_2561;
import net.minecraft.class_329;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={class_329.class})
public final class InGameHudMixin {
    @Inject(method={"setOverlayMessage"}, at={@At(value="HEAD")}, cancellable=true)
    void setOverlayMessage(class_2561 message, boolean tinted, CallbackInfo ci) {
        if (BoatlabsMod.timingSession != null && BoatlabsMod.config.useTimeTrialTimer && BoatlabsMod.timingSession.track.timingAllowed) {
            ci.cancel();
        }
    }
}

