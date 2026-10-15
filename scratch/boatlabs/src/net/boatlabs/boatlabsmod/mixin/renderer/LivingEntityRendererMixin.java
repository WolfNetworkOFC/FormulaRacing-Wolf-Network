/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_10042
 *  net.minecraft.class_10055
 *  net.minecraft.class_1309
 *  net.minecraft.class_4587
 *  net.minecraft.class_4588
 *  net.minecraft.class_583
 *  net.minecraft.class_922
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package net.boatlabs.boatlabsmod.mixin.renderer;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.accessor.TransparentFieldAccessor;
import net.boatlabs.boatlabsmod.ghost.GhostPlayer;
import net.minecraft.class_10042;
import net.minecraft.class_10055;
import net.minecraft.class_1309;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_583;
import net.minecraft.class_922;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={class_922.class})
public class LivingEntityRendererMixin<T extends class_1309, S extends class_10042> {
    @Redirect(method={"render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
    void boatlabsmod$renderPlayerTransparent(class_583<S> instance, class_4587 matrixStack, class_4588 vertexConsumer, int light, int overlay, int color, S livingEntityRenderState) {
        class_10055 playerRenderState;
        if (livingEntityRenderState instanceof class_10055 && TransparentFieldAccessor.get(playerRenderState = (class_10055)livingEntityRenderState).boatlabsmod$getTransparent()) {
            if (BoatlabsMod.config.ghostPlayerVisible) {
                instance.method_62100(matrixStack, vertexConsumer, light, overlay, (color &= 0xFFFFFF) + BoatlabsMod.config.ghostOpacityAsArgb());
            }
            return;
        }
        instance.method_62100(matrixStack, vertexConsumer, light, overlay, color);
    }

    @Inject(method={"updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V"}, at={@At(value="TAIL")})
    void boatlabsmod$updateTransparentRenderState(T livingEntity, S livingEntityRenderState, float f, CallbackInfo ci) {
        if (livingEntityRenderState instanceof class_10055) {
            class_10055 playerRenderState = (class_10055)livingEntityRenderState;
            TransparentFieldAccessor.get(playerRenderState).boatlabsmod$setTransparent(livingEntity instanceof GhostPlayer);
        }
    }

    @Inject(method={"hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z"}, at={@At(value="HEAD")}, cancellable=true)
    void boatlabsmod$disableGhostPlayerNameTag(T livingEntity, double d, CallbackInfoReturnable<Boolean> cir) {
        if (livingEntity instanceof GhostPlayer) {
            cir.cancel();
        }
    }
}

