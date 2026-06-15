package dev.EfraGroup.formulaRacing.Utils;

import org.bukkit.Bukkit;

public final class PlatformUtils {
    private static final boolean IS_FOLIA = isClassAvailable("io.papermc.paper.threadedregions.RegionScheduler");

    private PlatformUtils() {}

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
