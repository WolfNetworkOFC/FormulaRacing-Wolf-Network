/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Multimap
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.class_1268
 *  net.minecraft.class_1269
 *  net.minecraft.class_1657
 *  net.minecraft.class_1664
 *  net.minecraft.class_310
 *  net.minecraft.class_638
 *  net.minecraft.class_745
 *  net.minecraft.class_8685
 */
package net.boatlabs.boatlabsmod.ghost;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1657;
import net.minecraft.class_1664;
import net.minecraft.class_310;
import net.minecraft.class_638;
import net.minecraft.class_745;
import net.minecraft.class_8685;

public final class GhostPlayer
extends class_745 {
    public GhostPlayer(class_638 clientWorld) {
        super(clientWorld, GhostPlayer.createGameProfile());
    }

    public boolean method_7348(class_1664 modelPart) {
        return (modelPart.method_7430() & class_1664.field_7559.method_7430()) == 0;
    }

    public class_8685 method_52814() {
        return class_310.method_1551().field_1724.method_52814();
    }

    public class_1269 method_5688(class_1657 player, class_1268 hand) {
        return class_1269.field_5811;
    }

    static GameProfile createGameProfile() {
        GameProfile currentProfile = class_310.method_1551().method_53462();
        GameProfile profile = new GameProfile(UUID.randomUUID(), currentProfile.getName() + " Ghost");
        profile.getProperties().putAll((Multimap)currentProfile.getProperties());
        return profile;
    }
}

