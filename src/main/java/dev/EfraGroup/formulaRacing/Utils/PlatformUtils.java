package dev.EfraGroup.formulaRacing.Utils;

public final class PlatformUtils {
    private static final boolean IS_FOLIA = isClassAvailable("io.papermc.paper.threadedregions.RegionizedServer");

    private PlatformUtils() {}

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static boolean isFoliaRuntime() {
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
