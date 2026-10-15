/*
 * Decompiled with CFR 0.152.
 */
package net.boatlabs.boatlabsmod.util;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static String formatAsTime(long timeInMillis) {
        long hours = timeInMillis / 1000L / 60L / 60L;
        long minutes = timeInMillis / 1000L / 60L % 60L;
        long seconds = timeInMillis / 1000L % 60L;
        if (hours == 0L) {
            if (minutes == 0L) {
                return String.format("%d.%03d", seconds, timeInMillis % 1000L);
            }
            return String.format("%d:%02d.%03d", minutes, seconds, timeInMillis % 1000L);
        }
        return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, timeInMillis % 1000L);
    }

    public static String formatAsTime2dp(long timeInMillis) {
        long hours = timeInMillis / 1000L / 60L / 60L;
        long minutes = timeInMillis / 1000L / 60L % 60L;
        long seconds = timeInMillis / 1000L % 60L;
        float millis = (float)timeInMillis % 1000.0f / 10.0f;
        if (hours == 0L) {
            if (minutes == 0L) {
                return String.format("%d.%02.0f", seconds, Float.valueOf(millis));
            }
            return String.format("%d:%02d.%02.0f", minutes, seconds, Float.valueOf(millis));
        }
        return String.format("%d:%02d:%02d.%02.0f", hours, minutes, seconds, Float.valueOf(millis));
    }

    public static long roundToTick(long time) {
        if (time % 50L == 0L) {
            return time;
        }
        long mapTime2 = time + 1L;
        if (mapTime2 % 50L == 0L) {
            return mapTime2;
        }
        long rest = time % 50L;
        if (rest < 25L) {
            return time - rest;
        }
        return time += 50L - rest;
    }

    public static int getToTicks(int millis) {
        int rest;
        int millis2;
        int time = millis % 50 == 0 ? millis : ((millis2 = millis + 1) % 50 == 0 ? millis2 : ((rest = millis % 50) < 25 ? millis - rest : millis + (50 - rest)));
        return time * 20 / 1000;
    }
}

