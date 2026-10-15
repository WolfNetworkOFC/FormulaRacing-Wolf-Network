/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_10004
 *  net.minecraft.class_10017
 *  net.minecraft.class_10255
 *  net.minecraft.class_10262
 *  net.minecraft.class_4587
 *  net.minecraft.class_4588
 *  net.minecraft.class_583
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.boatlabs.boatlabsmod.mixin.renderer;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.accessor.TransparentFieldAccessor;
import net.boatlabs.boatlabsmod.ghost.GhostBoat;
import net.minecraft.class_10004;
import net.minecraft.class_10017;
import net.minecraft.class_10255;
import net.minecraft.class_10262;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_583;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={class_10262.class})
public final class AbstractBoatEntityRendererMixin<S extends class_10017> {
    @Redirect(method={"render(Lnet/minecraft/client/render/entity/state/BoatEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"))
    void boatlabsmod$renderBoatTransparent(class_583<S> instance, class_4587 matrixStack, class_4588 vertexConsumer, int light, int overlay, class_10004 boatEntityRenderState) {
        if (TransparentFieldAccessor.get(boatEntityRenderState).boatlabsmod$getTransparent()) {
            instance.method_62100(matrixStack, vertexConsumer, light, overlay, 0xFFFFFF + BoatlabsMod.config.ghostOpacityAsArgb());
        } else {
            instance.method_60879(matrixStack, vertexConsumer, light, overlay);
        }
    }

    @Inject(method={"updateRenderState(Lnet/minecraft/entity/vehicle/AbstractBoatEntity;Lnet/minecraft/client/render/entity/state/BoatEntityRenderState;F)V"}, at={@At(value="TAIL")})
    void boatlabsmod$updateTransparentRenderState(class_10255 abstractBoatEntity, class_10004 boatEntityRenderState, float f, CallbackInfo ci) {
        TransparentFieldAccessor.get(boatEntityRenderState).boatlabsmod$setTransparent(abstractBoatEntity instanceof GhostBoat);
    }
}

