/*
 * Decompiled with CFR 0.152.
 */
package net.boatlabs.boatlabsmod.region;

public record RegionPassedTime(long timePrecise, long tickTimePassed) {
    public long silly() {
        return this.tickTimePassed % 50L;
    }
}

