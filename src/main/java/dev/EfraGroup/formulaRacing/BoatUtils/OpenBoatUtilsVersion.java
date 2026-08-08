package dev.EfraGroup.formulaRacing.BoatUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OpenBoatUtilsVersion {

    public static final int MIN_SUPPORTED_VERSION = 1;
    public static final int RECOMMENDED_VERSION = 19;

    private static final Map<UUID, Integer> playerVersions = new HashMap<>();

    public static void setPlayerVersion(UUID uuid, int version) {
        playerVersions.put(uuid, version);
    }

    public static int getPlayerVersion(UUID uuid) {
        return playerVersions.getOrDefault(uuid, 0);
    }

    public static boolean hasMod(UUID uuid) {
        return playerVersions.containsKey(uuid);
    }

    public static boolean hasMinVersion(UUID uuid, int minVersion) {
        int playerVersion = playerVersions.getOrDefault(uuid, 0);
        return playerVersion >= minVersion;
    }

    public static void clearPlayer(UUID uuid) {
        playerVersions.remove(uuid);
    }

    public static String getVersionName(int version) {
        return switch (version) {
            case 0 -> "None";
            case 1 -> "0.1";
            case 2 -> "0.2";
            case 3 -> "0.3";
            case 4 -> "0.4";
            case 5 -> "0.4.1";
            case 6 -> "0.4.2";
            case 7 -> "0.4.3";
            case 8 -> "0.4.4";
            case 9 -> "0.4.5";
            case 10 -> "0.4.6";
            case 11 -> "0.4.7";
            case 12 -> "0.4.8";
            case 13 -> "0.4.9";
            case 14 -> "0.4.10";
            case 15 -> "0.5.0";
            case 16 -> "0.5.1";
            case 17 -> "0.6.0";
            case 18 -> "1.0.0";
            case 19 -> "1.0.1+";
            default -> "Unknown (" + version + ")";
        };
    }
}