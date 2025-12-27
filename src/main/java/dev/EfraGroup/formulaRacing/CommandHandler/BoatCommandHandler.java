package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BoatCommandHandler implements CommandExecutor {
    public final APIFormulaRacing api;

    public BoatCommandHandler(APIFormulaRacing api) {
        this.api = api;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        // Spawna o barco na posição atual do jogador
        api.spawnBoat(player, true, false, true);

        return true;
    }
}
