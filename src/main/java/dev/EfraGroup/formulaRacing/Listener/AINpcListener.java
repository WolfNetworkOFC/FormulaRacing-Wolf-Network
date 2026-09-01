package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.AI.AIOpponentManager;
import dev.EfraGroup.formulaRacing.AI.FakePlayerNPC;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

/**
 * Keeps the AI fake-player NPCs visible for players who join, change worlds or
 * teleport after the NPCs were already spawned (the initial broadcast only
 * reaches players online at spawn time).
 */
public class AINpcListener implements Listener {

    private final FormulaRacing plugin;

    public AINpcListener(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        reshowFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reshowFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        reshowFor(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        AIOpponentManager aiManager = plugin.getAIOpponentManager();
        if (aiManager == null) {
            return;
        }
        for (AIOpponentManager.AIOpponent ai : aiManager.getAIOpponents().values()) {
            FakePlayerNPC npc = ai.getFakePlayer();
            if (npc != null) {
                npc.removeViewer(uuid);
            }
        }
    }

    private void reshowFor(Player player) {
        AIOpponentManager aiManager = plugin.getAIOpponentManager();
        if (aiManager == null) {
            return;
        }
        for (AIOpponentManager.AIOpponent ai : aiManager.getAIOpponents().values()) {
            FakePlayerNPC npc = ai.getFakePlayer();
            if (npc == null || !npc.isInWorld(player.getWorld())) {
                continue;
            }
            Entity boat = ai.getControlledEntity();
            if (boat == null || !boat.isValid()) {
                continue;
            }
            // Force a re-show: the client discards packet-spawned NPCs on world
            // change/teleport, but the server-side viewers set still has the player.
            // Removing them first lets showTo() re-send the spawn packets.
            npc.removeViewer(player.getUniqueId());
            npc.showTo(player);
        }
    }
}
