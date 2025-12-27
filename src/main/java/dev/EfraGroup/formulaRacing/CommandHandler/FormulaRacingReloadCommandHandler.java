package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FileManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class FormulaRacingReloadCommandHandler implements CommandExecutor {

    private final FileManager fileManager;
    private final DatabaseManager mysql;

    public FormulaRacingReloadCommandHandler(FileManager fileManager, DatabaseManager mysql) {
        this.fileManager = fileManager;
        this.mysql = mysql;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cVocê não tem permissão para usar este comando.");
            return true;
        }

        long start = System.currentTimeMillis();

        fileManager.reloadConfig();

        long end = System.currentTimeMillis();
        long duration = end - start;

        sender.sendMessage("§a[FormulaRacing] Configuração recarregada com sucesso!");
        sender.sendMessage("§7Tempo de reload: §f" + duration + "ms");

        return true;
    }
}
