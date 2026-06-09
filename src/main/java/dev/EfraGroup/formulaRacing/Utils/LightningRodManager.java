package dev.EfraGroup.formulaRacing.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LightningRodManager {

    private static boolean lightningRodsEnabled = true;
    private static final Map<UUID, Boolean> playerToggles = new HashMap<>();

    public static boolean isLightningRodsEnabled() {
        return lightningRodsEnabled;
    }

    public static void setLightningRodsEnabled(boolean enabled) {
        lightningRodsEnabled = enabled;
    }

    public static boolean isPlayerToggleEnabled(UUID playerId) {
        return playerToggles.getOrDefault(playerId, true);
    }

    public static void setPlayerToggle(UUID playerId, boolean enabled) {
        playerToggles.put(playerId, enabled);
    }

    public static boolean canPlayerSeeRods(UUID playerId) {
        return lightningRodsEnabled && isPlayerToggleEnabled(playerId);
    }

    public static void clearAllToggles() {
        playerToggles.clear();
    }
}