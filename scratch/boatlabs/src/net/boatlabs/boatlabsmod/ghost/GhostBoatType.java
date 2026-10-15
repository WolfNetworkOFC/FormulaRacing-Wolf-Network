/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.isxander.yacl3.api.NameableEnum
 *  net.minecraft.class_1299
 *  net.minecraft.class_1690
 *  net.minecraft.class_2561
 */
package net.boatlabs.boatlabsmod.ghost;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.class_1299;
import net.minecraft.class_1690;
import net.minecraft.class_2561;

public enum GhostBoatType implements NameableEnum
{
    BIRCH((class_2561)class_2561.method_43470((String)"Birch").method_54663(13483658), (class_1299<? extends class_1690>)class_1299.field_54420),
    SPRUCE((class_2561)class_2561.method_43470((String)"Spruce").method_54663(8809286), (class_1299<? extends class_1690>)class_1299.field_54416),
    JUNGLE((class_2561)class_2561.method_43470((String)"Jungle").method_54663(10517083), (class_1299<? extends class_1690>)class_1299.field_54412),
    ACACIA((class_2561)class_2561.method_43470((String)"Acacia").method_54663(13666907), (class_1299<? extends class_1690>)class_1299.field_54408),
    DARK_OAK((class_2561)class_2561.method_43470((String)"Dark Oak").method_54663(7033143), (class_1299<? extends class_1690>)class_1299.field_54406),
    MANGROVE((class_2561)class_2561.method_43470((String)"Mangrove").method_54663(8867658), (class_1299<? extends class_1690>)class_1299.field_54414),
    CHERRY((class_2561)class_2561.method_43470((String)"Cherry").method_54663(15188412), (class_1299<? extends class_1690>)class_1299.field_54422),
    PALE_OAK((class_2561)class_2561.method_43470((String)"Pale Oak").method_54663(15197410), (class_1299<? extends class_1690>)class_1299.field_54562);

    private final class_2561 displayName;
    public final class_1299<? extends class_1690> entityType;

    private GhostBoatType(class_2561 displayName, class_1299<? extends class_1690> entityType) {
        this.displayName = displayName;
        this.entityType = entityType;
    }

    public class_2561 getDisplayName() {
        return this.displayName;
    }
}

