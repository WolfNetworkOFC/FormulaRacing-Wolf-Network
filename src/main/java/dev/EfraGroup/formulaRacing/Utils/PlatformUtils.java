package dev.EfraGroup.formulaRacing.Utils;

import org.bukkit.Bukkit;

public final class PlatformUtils {
    private static final boolean IS_FOLIA = isClassAvailable("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
    private static Boolean IS_FOLIA_RUNTIME = null;

    private PlatformUtils() {}

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static boolean isFoliaRuntime() {
        if (IS_FOLIA_RUNTIME == null) {
            if (!IS_FOLIA) {
                IS_FOLIA_RUNTIME = false;
            } else {
                try {
                    IS_FOLIA_RUNTIME = !Bukkit.isPrimaryThread();
                } catch (Exception e) {
                    IS_FOLIA_RUNTIME = true;
                }
            }
        }
        return IS_FOLIA_RUNTIME;
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
