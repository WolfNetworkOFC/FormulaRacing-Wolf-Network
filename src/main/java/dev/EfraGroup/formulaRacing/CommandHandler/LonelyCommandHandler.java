package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LonelyCommandHandler implements CommandExecutor {

    private final DatabaseManager databaseManager;

    public LonelyCommandHandler(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eUse: /lonely <true|false>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "true" -> {
                databaseManager.setLonelyModePlayer(player.getUniqueId(), true);
                player.sendMessage("§a✅ Lonely Mode ativado!");
            }
            case "false" -> {
                databaseManager.setLonelyModePlayer(player.getUniqueId(), false);
                player.sendMessage("§c❌ Lonely Mode desativado!");
            }
            default -> player.sendMessage("§cSubcomando inválido. Use: /lonely <true|false>");
        }
        return true;
    }
}
