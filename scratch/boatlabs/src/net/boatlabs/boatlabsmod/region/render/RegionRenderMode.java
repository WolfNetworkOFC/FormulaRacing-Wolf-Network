/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.isxander.yacl3.api.NameableEnum
 *  net.minecraft.class_2561
 */
package net.boatlabs.boatlabsmod.region.render;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.class_2561;

public enum RegionRenderMode implements NameableEnum
{
    NONE("None", "No track regions are visible"),
    NEXT("Next", "Only the next track region is visible"),
    ALL("All", "All track regions are visible");

    public final String name;
    public final String behaviour;

    private RegionRenderMode(String name, String behaviour) {
        this.name = name;
        this.behaviour = behaviour;
    }

    public class_2561 getDisplayName() {
        return class_2561.method_43470((String)this.name);
    }
}

