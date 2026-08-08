package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
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

        // Get the last track the player was on
        String lastTrack = plugin.getLastTimeTrialTrack(player.getUniqueId());
        if (lastTrack == null) {
            player.sendMessage("§cYou aren't in any Time Trial.");
            return true;
        }

        // Track spawn point
        Location spawn = mysql.getTrackSpawn(lastTrack);
        if (spawn == null) {
            player.sendMessage("§cCould not find the spawn point for track: " + lastTrack);
            return true;
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
        // Folia: teleportAsync é assíncrono — só spawnar o barco DEPOIS do teleport
        // concluir, no destino (senão o barco nasce na posição antiga e o teleport
        // falha com o player dentro de veículo).
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

        return true;
    }
}
