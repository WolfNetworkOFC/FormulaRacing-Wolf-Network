/*
 * Decompiled with CFR 0.152.
 */
package net.boatlabs.boatlabsmod.region;

import java.util.Optional;

public enum RegionType {
    START(0),
    END(1),
    CHECKPOINT(3);

    public final int id;

    private RegionType(int id) {
        this.id = id;
    }

    public static Optional<RegionType> fromId(int id) {
        for (RegionType type : RegionType.values()) {
            if (type.id != id) continue;
            return Optional.of(type);
        }
        return Optional.empty();
    }
}

