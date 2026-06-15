package dev.EfraGroup.formulaRacing.TimeTrial;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public class TimeTrialController {
    private final ConcurrentHashMap<UUID, TimeTrialSession> activeSessions = new ConcurrentHashMap();
    private final FormulaRacing plugin;

    public TimeTrialController(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public void startSession(Player player, String trackName) {
        this.plugin.checkAndWarnOBU(player, trackName);
        this.activeSessions.put(player.getUniqueId(), new TimeTrialSession(player.getUniqueId(), trackName));
    }

    public void startSession(Player player, String trackName, Instant startTime) {
        this.activeSessions.put(player.getUniqueId(), new TimeTrialSession(player.getUniqueId(), trackName, startTime));
    }

    public TimeTrialSession getSession(Player player) {
        return (TimeTrialSession)this.activeSessions.get(player.getUniqueId());
    }

    public TimeTrialSession getSession(UUID uuid) {
        return (TimeTrialSession)this.activeSessions.get(uuid);
    }

    public void endSession(Player player) {
        this.activeSessions.remove(player.getUniqueId());
    }

    public void endSession(UUID uuid) {
        this.activeSessions.remove(uuid);
    }

    public boolean hasActiveSession(Player player) {
        return this.activeSessions.containsKey(player.getUniqueId());
    }
}
