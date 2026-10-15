/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_10017
 *  net.minecraft.class_1921
 *  net.minecraft.class_2960
 *  net.minecraft.class_583
 *  net.minecraft.class_881
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package net.boatlabs.boatlabsmod.mixin.renderer;

import net.minecraft.class_10017;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import net.minecraft.class_583;
import net.minecraft.class_881;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={class_881.class})
public class BoatEntityRendererMixin<T extends class_10017> {
    @Redirect(method={"getRenderLayer()Lnet/minecraft/client/render/RenderLayer;"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/render/entity/model/EntityModel;getLayer(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"))
    class_1921 boatlabsmod$returnEntityTranslucent(class_583<T> instance, class_2960 identifier) {
        return class_1921.method_23580((class_2960)identifier);
    }
}

