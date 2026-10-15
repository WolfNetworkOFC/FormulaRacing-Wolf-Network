/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.boatlabs.boatlabsmod.protocol.BoatlabsPacket
 *  net.boatlabs.boatlabsmod.protocol.acknowledgement.AcknowledgementPacket
 *  net.boatlabs.utils.debug.log.Blogger
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
 *  net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback
 *  net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer
 *  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
 *  net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
 *  net.minecraft.class_10209
 *  net.minecraft.class_8710
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package net.boatlabs.boatlabsmod;

import net.boatlabs.boatlabsmod.CommandBoatlabsMod;
import net.boatlabs.boatlabsmod.config.BoatlabsKeybind;
import net.boatlabs.boatlabsmod.config.BoatlabsModConfig;
import net.boatlabs.boatlabsmod.protocol.BoatlabsPacket;
import net.boatlabs.boatlabsmod.protocol.BoatlabsPayload;
import net.boatlabs.boatlabsmod.protocol.PacketHandler;
import net.boatlabs.boatlabsmod.protocol.acknowledgement.AcknowledgementPacket;
import net.boatlabs.boatlabsmod.timing.TimingHud;
import net.boatlabs.boatlabsmod.timing.TimingSession;
import net.boatlabs.utils.debug.log.Blogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.class_10209;
import net.minecraft.class_8710;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BoatlabsMod
implements ClientModInitializer {
    public static final String MOD_ID = "boatlabsmod";
    public static TimingSession timingSession;
    public static BoatlabsModConfig config;
    public static boolean ghostCollisionAllowed;

    public void onInitializeClient() {
        Blogger.init((Logger)LoggerFactory.getLogger((String)"Boatlabs Mod"));
        BoatlabsKeybind.init();
        PayloadTypeRegistry.playC2S().register(BoatlabsPayload.ID, BoatlabsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoatlabsPayload.ID, BoatlabsPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(BoatlabsPayload.ID, (payload, ctx) -> PacketHandler.onPacketReceived(payload));
        try {
            config = BoatlabsModConfig.load();
        }
        catch (Exception e) {
            Blogger.get().error("Invalid config", (Throwable)e);
            return;
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(CommandBoatlabsMod.create()));
        ClientPlayConnectionEvents.JOIN.register((clientPlayNetworkHandler, sender, client) -> {
            timingSession = null;
            ghostCollisionAllowed = false;
            BoatlabsMod.sendPacket(new AcknowledgementPacket(0));
        });
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (timingSession != null) {
                class_10209.method_64146().method_15405(MOD_ID);
                timingSession.renderRegions(ctx, BoatlabsMod.config.regionRenderMode);
                timingSession.renderRecording(ctx);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.field_1724 == null) {
                return;
            }
            BoatlabsKeybind.handleGlobalTick(client.field_1724);
            if (timingSession == null) {
                return;
            }
            timingSession.tick();
            BoatlabsKeybind.handleTimingSessionTick(client.field_1724, timingSession);
        });
        HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.attachLayerAfter(IdentifiedLayer.CROSSHAIR, TimingHud.ID, (ctx, tickCounter) -> {
            if (timingSession != null) {
                timingSession.renderTimingHud(ctx);
            }
        }));
    }

    public static void sendPacket(BoatlabsPacket<?> packet) {
        ClientPlayNetworking.send((class_8710)new BoatlabsPayload(packet));
    }
}

