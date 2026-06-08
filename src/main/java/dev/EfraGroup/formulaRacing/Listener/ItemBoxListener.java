package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.ItemBoxManager;
import dev.EfraGroup.formulaRacing.Heat.ItemPower;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for player movement and detects when a driver passes through an Item Box.
 *
 * <p>Runs a lightweight check every tick for all active heat drivers.
 * When a player is within the radius of an active item box, they collect it
 * and receive a random Mario Kart-style power-up.</p>
 */
public class ItemBoxListener implements Listener {

    private final FormulaRacing plugin;
    private final ItemBoxManager itemBoxManager;

    // Throttle: only check every N ticks per player
    private static final int CHECK_INTERVAL = 3; // check every 3 ticks (~6 times/sec)

    // Track last check tick per player to throttle
    private final Map<UUID, Long> lastCheck = new ConcurrentHashMap<>();

    public ItemBoxListener(FormulaRacing plugin, ItemBoxManager itemBoxManager) {
        this.plugin = plugin;
        this.itemBoxManager = itemBoxManager;
    }

    /**
     * Main check — runs on player move events for efficiency.
     * We use PlayerMoveEvent instead of a global ticker to reduce overhead.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Only check players in active heats
        if (!plugin.getDriverLookup().isRacing(uuid)) return;

        // Throttle checks
        long now = player.getWorld().getGameTime();
        Long last = lastCheck.get(uuid);
        if (last != null && (now - last) < CHECK_INTERVAL) return;
        lastCheck.put(uuid, now);

        // Get the heat
        Heats heat = plugin.getDriverLookup().getHeat(uuid);
        if (heat == null) return;
        if (heat.getHeatState() != HeatState.RACING) return;

        // Check item box collection
        int totalDrivers = heat.getDriverCount();
        itemBoxManager.checkCollection(player, heat.getId(), totalDrivers);
    }

    /**
     * Cleanup when a player leaves.
     */
    public void cleanupPlayer(UUID uuid) {
        lastCheck.remove(uuid);
    }
}
