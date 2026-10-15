/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
 *  net.minecraft.class_2561
 *  net.minecraft.class_304
 *  net.minecraft.class_3675$class_307
 *  net.minecraft.class_746
 */
package net.boatlabs.boatlabsmod.config;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.honk.HonkManager;
import net.boatlabs.boatlabsmod.timing.TimingSession;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.class_2561;
import net.minecraft.class_304;
import net.minecraft.class_3675;
import net.minecraft.class_746;

public final class BoatlabsKeybind {
    public static final class_304 HONK = new class_304("key.boatlabsmod.honk", class_3675.class_307.field_1668, -1, "Boatlabs Mod");
    public static final class_304 TOGGLE_LINE = new class_304("key.boatlabsmod.toggle_line", class_3675.class_307.field_1668, -1, "Boatlabs Mod");
    public static final class_304 TOGGLE_GHOST = new class_304("key.boatlabsmod.toggle_ghost", class_3675.class_307.field_1668, -1, "Boatlabs Mod");

    public static void init() {
        KeyBindingHelper.registerKeyBinding((class_304)HONK);
        KeyBindingHelper.registerKeyBinding((class_304)TOGGLE_LINE);
        KeyBindingHelper.registerKeyBinding((class_304)TOGGLE_GHOST);
    }

    public static void handleGlobalTick(class_746 player) {
        if (HONK.method_1434()) {
            HonkManager.tryHonk();
            return;
        }
    }

    public static void handleTimingSessionTick(class_746 player, TimingSession timingSession) {
        if (TOGGLE_LINE.method_1436()) {
            BoatlabsMod.config.renderPersonalBestLine = !BoatlabsMod.config.renderPersonalBestLine;
            BoatlabsMod.config.saveGuarded();
            player.method_7353((class_2561)class_2561.method_43470((String)("Personal best line is now " + (BoatlabsMod.config.renderPersonalBestLine ? "visible" : "hidden"))).method_54663(8122880), false);
            return;
        }
        if (TOGGLE_GHOST.method_1436()) {
            BoatlabsMod.config.renderPersonalBestGhost = !BoatlabsMod.config.renderPersonalBestGhost;
            BoatlabsMod.config.saveGuarded();
            if (!BoatlabsMod.config.renderPersonalBestGhost) {
                timingSession.stopRecordingPlayback();
            }
            player.method_7353((class_2561)class_2561.method_43470((String)("Personal best ghost is now " + (BoatlabsMod.config.renderPersonalBestGhost ? "visible" : "hidden"))).method_54663(8122880), false);
        }
    }
}

