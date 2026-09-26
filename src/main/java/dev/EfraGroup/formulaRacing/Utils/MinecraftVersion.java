package dev.EfraGroup.formulaRacing.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

public final class MinecraftVersion {

    private static final int SNAPSHOT_BIT = 1073741824;

    private static final Map<String, Integer> NAME_TO_PROTOCOL = new LinkedHashMap<>();
    private static final Map<Integer, String> PROTOCOL_TO_NAME = new LinkedHashMap<>();

    static {
        register("1.20.4", 765);
        register("1.20.6", 766);
        register("1.21.1", 767);
        register("1.21.4", 769);
        register("1.21.5", 770);
        register("1.21.6", 771);
        register("1.21.7", 772);
        register("1.21.9", 773);
        register("1.21.11", 774);
        register("26.1", 775);
        register("26.2", 776);
        register("26.3", 777);
    }

    private static void register(String name, int protocol) {
        NAME_TO_PROTOCOL.put(name, protocol);
        PROTOCOL_TO_NAME.putIfAbsent(protocol, name);
    }

    private MinecraftVersion() {
    }

    public static boolean isKnownVersion(String name) {
        return name != null && NAME_TO_PROTOCOL.containsKey(name.trim());
    }

    public static int getProtocol(String name) {
        if (name == null) {
            return -1;
        }
        return NAME_TO_PROTOCOL.getOrDefault(name.trim(), -1);
    }

    public static String getName(int protocol) {
        if (isSnapshot(protocol)) {
            return "Snapshot";
        }
        return PROTOCOL_TO_NAME.getOrDefault(protocol, "Unknown (" + protocol + ")");
    }

    public static List<String> knownVersionNames() {
        return new ArrayList<>(NAME_TO_PROTOCOL.keySet());
    }

    public static boolean isSnapshot(int protocol) {
        return protocol >= SNAPSHOT_BIT;
    }

    public static boolean meetsMinimum(Player player, String minVersionName) {
        return meetsMinimum(player.getProtocolVersion(), minVersionName);
    }

    public static boolean meetsMinimum(int currentProtocol, String minVersionName) {
        if (minVersionName == null || minVersionName.isBlank()) {
            return true;
        }
        int required = getProtocol(minVersionName);
        if (required == -1) {
            return true;
        }
        if (currentProtocol == -1) {
            return false;
        }
        if (isSnapshot(currentProtocol)) {
            return true;
        }
        return currentProtocol >= required;
    }
}
