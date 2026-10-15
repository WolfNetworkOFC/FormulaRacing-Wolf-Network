/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.boatlabs.boatlabsmod.protocol.player.HonkPacket
 *  net.boatlabs.utils.helper.cooldown.SingleCooldown
 */
package net.boatlabs.boatlabsmod.honk;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.protocol.player.HonkPacket;
import net.boatlabs.utils.helper.cooldown.SingleCooldown;

public final class HonkManager {
    private static final SingleCooldown COOLDOWN = new SingleCooldown(100L);

    public static void tryHonk() {
        if (COOLDOWN.isInCooldown()) {
            return;
        }
        COOLDOWN.startCooldown();
        BoatlabsMod.sendPacket(new HonkPacket());
    }
}

