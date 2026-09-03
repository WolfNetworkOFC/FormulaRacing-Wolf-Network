package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetCommand implements CommandExecutor {

    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final TimerUtils timerUtils;
    private final APIFormulaRacing api;

    public ResetCommand(FormulaRacing plugin, DatabaseManager mysql, TimerUtils timerUtils, APIFormulaRacing api) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.timerUtils = timerUtils;
        this.api = api;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        // Verifica se está em um heat ativo
        Optional<Heats> heatOpt = this.plugin.getRaceEventManager().getPlayerActiveHeat(player.getUniqueId());
        if (heatOpt.isPresent()) {
            Heats heat = heatOpt.get();
            Driver driver = heat.getDriver(player.getUniqueId());

            if (driver != null) {
                // Se o heat NÃO permite reset OU é heat de corrida, manda pro checkpoint
                if (!heat.isCanReset() || heat.getHeatState().name().equals("RACING")) {
                    teleportToCheckpoint(player, heat, driver);
                    return true;
                }
            }
        }

        // Reset normal (volta pro inicio)
        resetToStart(player);
        return true;
    }

    private void teleportToCheckpoint(Player player, Heats heat, Driver driver) {
        String trackNameWS = heat.getTrackNameWS();
        int checkpointsReached = driver.getCheckpointsReached();
        Location targetLoc = null;

        if (checkpointsReached > 0) {
            List<DatabaseManager.RegionData> checkpointList =
                this.plugin.getTrackIntegrationManager().getCheckpointById(trackNameWS, checkpointsReached - 1);
            if (checkpointList != null && !checkpointList.isEmpty()) {
                DatabaseManager.RegionData cp = checkpointList.get(0);
                targetLoc = new Location(
                    Bukkit.getWorld(cp.getWorld()),
                    (cp.getMinX() + cp.getMaxX()) / 2.0,
                    cp.getMaxY() - 0.5,
                    (cp.getMinZ() + cp.getMaxZ()) / 2.0,
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
                );
            }
        }

        if (targetLoc == null) {
            targetLoc = this.plugin.getTrackIntegrationManager().getTrackSpawn(trackNameWS);
        }

        if (targetLoc == null) {
            this.plugin.sendMessage(player, "resetcp_no_spawn", new String[0]);
            return;
        }

        final Location finalLoc = targetLoc;
        SchedulerHelper.teleport(player, finalLoc);
        this.plugin.sendMessage(player, "resetcp_teleported", new String[0]);
    }

    private void resetToStart(Player player) {
        // Get the last track the player was on
        String lastTrack = plugin.getLastTimeTrialTrack(player.getUniqueId());
        if (lastTrack == null) {
            player.sendMessage("§cYou aren't in any Time Trial.");
            return;
        }

        // Track spawn point
        Location spawn = mysql.getTrackSpawn(lastTrack);
        if (spawn == null) {
            player.sendMessage("§cCould not find the spawn point for track: " + lastTrack);
            return;
        }

        // =========================
        // Save partial time up to last checkpoint (optional)
        // =========================
        TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player, lastTrack);
        if (data != null) {
            int lastCheckpointIndex = data.getCheckpointsReached();
            if (lastCheckpointIndex > 0) {
                double elapsedTime = timerUtils.getPlayerElapsedTimeUntilLastCheckpoint(player, lastTrack);
                mysql.savePartialTime(player.getUniqueId(), player.getName(), lastTrack, elapsedTime, lastCheckpointIndex);

                player.sendMessage("§aYour partial time up to checkpoint §e" + lastCheckpointIndex +
                        " §awas saved: §e" + timerUtils.formatTime(elapsedTime, true, false));
                timerUtils.resetTempCheckpoints(player.getUniqueId());
            }
        }

        // =========================
        // Reset timer
        // =========================
        timerUtils.stopTimer(player, lastTrack);

        // =========================
        // Teleport and create boat
        // =========================
        api.recoverPlayerBoatState(player);
        SchedulerHelper.teleportAsync(player, spawn).thenAccept(success -> {
            if (Boolean.TRUE.equals(success) && player.isOnline()) {
                api.spawnBoatAt(player, spawn, false, false, false);

                // Reapply boatutils settings (same as /tt)
                if (this.plugin.getPacketSender() != null) {
                    this.plugin.getPacketSender().resetBoatUtilsToVanilla(player);
                    this.plugin.getPacketSender().applyBoatUtilsToPlayer(player, lastTrack);
                }
            }
        });
    }
}
