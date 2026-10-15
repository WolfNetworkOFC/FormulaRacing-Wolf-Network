/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
 *  net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
 */
package net.boatlabs.boatlabsmod;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class CommandBoatlabsMod {
    static LiteralArgumentBuilder<FabricClientCommandSource> create() {
        return (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal((String)"boatlabsmod").then(ClientCommandManager.literal((String)"toggle_line").executes(ctx -> {
            BoatlabsMod.config.renderPersonalBestLine = !BoatlabsMod.config.renderPersonalBestLine;
            BoatlabsMod.config.saveGuarded();
            return 1;
        }))).then(ClientCommandManager.literal((String)"toggle_ghost").executes(ctx -> {
            BoatlabsMod.config.renderPersonalBestGhost = !BoatlabsMod.config.renderPersonalBestGhost;
            BoatlabsMod.config.saveGuarded();
            return 1;
        }));
    }
}

