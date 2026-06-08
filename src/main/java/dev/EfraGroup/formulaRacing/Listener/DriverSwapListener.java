package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.Heat.DriverSwapHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class DriverSwapListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player clicker = event.getPlayer();
        if (!clicker.isSneaking()) return;
        if (target.getVehicle() == null) return;

        if (DriverSwapHandler.handleSwap(clicker, target)) {
            event.setCancelled(true);
        }
    }
}
