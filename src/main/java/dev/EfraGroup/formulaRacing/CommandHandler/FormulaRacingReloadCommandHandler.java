package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FileManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FormulaRacingReloadCommandHandler implements CommandExecutor {

    private final FileManager fileManager;
    private final DatabaseManager mysql;
    private final FormulaRacing plugin;

    public FormulaRacingReloadCommandHandler(FileManager fileManager, DatabaseManager mysql, FormulaRacing plugin) {
        this.fileManager = fileManager;
        this.mysql = mysql;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String langCode = "en_US"; // Default for console
        if (sender instanceof Player) {
            langCode = mysql.getPlayerLanguage(((Player) sender).getUniqueId());
        }

        if (!sender.isOp()) {
            sender.sendMessage(plugin.getDirectTranslation("no_permission", langCode));
            return true;
        }

        long start = System.currentTimeMillis();

        fileManager.reloadConfig();

        long end = System.currentTimeMillis();
        long duration = end - start;

        sender.sendMessage(plugin.getDirectTranslation("reload_success", langCode));
        sender.sendMessage("§7Tempo de reload: §f" + duration + "ms");

        return true;
    }
}
