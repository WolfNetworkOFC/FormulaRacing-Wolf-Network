package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TimeTrialCancelCommandHandler implements CommandExecutor {

    private final TimerUtils timerUtils;

    public TimeTrialCancelCommandHandler(TimerUtils timerUtils) {
        this.timerUtils = timerUtils;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }
        timerUtils.stopTimer(player);
        player.sendMessage("§aSeu time trial foi cancelado com sucesso.");
        return true;
    }
}
