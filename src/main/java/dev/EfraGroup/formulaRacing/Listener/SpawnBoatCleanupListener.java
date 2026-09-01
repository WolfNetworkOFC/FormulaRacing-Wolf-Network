package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Ensures a player's boat is removed before they are teleported to spawn.
 * The /spawn command is handled by an external plugin, so we intercept it here and
 * delete the boat first — otherwise it gets orphaned at the track.
 * Also restores the default hotbar after teleport to spawn.
 */
public class SpawnBoatCleanupListener implements Listener {
    private final FormulaRacing plugin;

    public SpawnBoatCleanupListener(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onSpawnCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        String command = message.toLowerCase().split(" ")[0];
        if (command.equals("/spawn")) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            this.plugin.getAPI().removePlayerBoat(uuid);
            
            // Clean up time trial state
            if (this.plugin.getTimerUtils() != null) {
                this.plugin.getTimerUtils().cleanupPlayer(uuid);
            }
            if (this.plugin.getTimeTrialController() != null) {
                this.plugin.getTimeTrialController().endSession(player);
            }
            if (this.plugin.getScoreboardTimeTrialUtils() != null) {
                this.plugin.getScoreboardTimeTrialUtils().clearPlayerTrack(player);
            }
            if (this.plugin.getRaceActionBarManager() != null) {
                this.plugin.getRaceActionBarManager().removePlayer(player);
            }
            
            // Hide PB and medal ghost lines + stop recording when leaving to spawn
            if (this.plugin.getGhostManager() != null) {
                this.plugin.getGhostManager().cleanupPlayer(player);
            }
            if (this.plugin.getScoreboardOwnershipCoordinator() != null) {
                this.plugin.getScoreboardOwnershipCoordinator().clear(uuid);
            }
            
            // Clear cached track/duel data for this player
            this.plugin.clearLastTimeTrialTrack(uuid);
            this.plugin.clearLastDuelTrack(uuid);
            this.plugin.clearLastDuelLonelyStatus(uuid);
            
            // Reset player time back to world time
            this.plugin.resetTrackGameTime(player);
            
            // Restore default hotbar after the teleport completes.
            // Use runTaskFor to ensure we run on the player's entity region thread (Folia-safe).
            SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                if (player.isOnline() && this.plugin.getHotbarController() != null) {
                    this.plugin.getHotbarController().giveHotbarItems(player);
                }
            }, 1L);
        }
    }
}
