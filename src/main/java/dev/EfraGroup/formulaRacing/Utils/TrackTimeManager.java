package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager.TrackData;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public class TrackTimeManager {

    private final FormulaRacing plugin;
    private final DatabaseManager db;
    private final Map<UUID, Long> activeTimes = new ConcurrentHashMap<>();

    public TrackTimeManager(FormulaRacing plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void apply(Player player, String trackName) {
        if (player == null || !player.isOnline() || trackName == null) return;

        TrackData data = db.getTrackData(trackName);
        if (data == null) return;

        Long gameTime = data.getGameTime();
        if (gameTime == null) return;

        player.setPlayerTime(gameTime, false);
        activeTimes.put(player.getUniqueId(), gameTime);
    }

    public void reset(Player player) {
        if (player == null) return;
        player.resetPlayerTime();
        activeTimes.remove(player.getUniqueId());
    }

    public void reset(UUID uuid) {
        if (uuid == null) return;
        activeTimes.remove(uuid);
    }

    public boolean hasActiveTime(UUID uuid) {
        return activeTimes.containsKey(uuid);
    }

    public void cleanupOnQuit(UUID uuid) {
        Long time = activeTimes.remove(uuid);
    }

    public static Long parseGameTime(String input) {
        if (input == null) return null;
        String lower = input.toLowerCase();
        switch (lower) {
            case "day": return 1000L;
            case "noon": return 6000L;
            case "sunset": return 12000L;
            case "night": return 13000L;
            case "midnight": return 18000L;
            default:
                try {
                    long ticks = Long.parseLong(input);
                    if (ticks < 0 || ticks > 24000) return null;
                    return ticks;
                } catch (NumberFormatException e) {
                    return null;
                }
        }
    }
}
