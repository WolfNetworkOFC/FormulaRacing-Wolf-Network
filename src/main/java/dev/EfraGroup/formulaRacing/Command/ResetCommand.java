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

        // Obtém última pista em que o jogador estava
        String lastTrack = plugin.getLastTimeTrialTrack(player.getUniqueId());
        if (lastTrack == null) {
            player.sendMessage("§cYou aren't in any Time Trial.");
            return true;
        }

        // Ponto de spawn da pista
        Location spawn = mysql.getTrackSpawn(lastTrack);
        if (spawn == null) {
            player.sendMessage("§cNão foi possível encontrar o ponto de spawn da pista: " + lastTrack);
            return true;
        }



        // =========================
        // Salva tempo parcial até último checkpoint (opcional)
        // =========================
        TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player, lastTrack);
        if (data != null) {
            int lastCheckpointIndex = data.getCheckpointsReached();
            if (lastCheckpointIndex > 0) {
                double elapsedTime = timerUtils.getPlayerElapsedTimeUntilLastCheckpoint(player, lastTrack);
                mysql.savePartialTime(player.getUniqueId(), player.getName(), lastTrack, elapsedTime, lastCheckpointIndex);

                player.sendMessage("§aSeu tempo parcial até o checkpoint §e" + lastCheckpointIndex +
                        " §afoi salvo: §e" + timerUtils.formatTime(elapsedTime, true, false));
                timerUtils.resetTempCheckpoints(player.getUniqueId());
            }
        }

        // =========================
        // Reseta timer
        // =========================
        timerUtils.stopTimer(player, lastTrack);

        // =========================
        // Teleporte e cria barco
        // =========================
        SchedulerHelper.teleport(player, spawn);
        api.spawnBoat(player, false, false, false);

        return true;
    }
}
