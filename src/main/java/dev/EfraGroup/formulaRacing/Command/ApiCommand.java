package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.Api.ApiManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("api")
@CommandPermission("formularacing.admin.api")
public class ApiCommand extends BaseCommand {

    @Default
    public void onDefault(CommandSender sender) {
        sender.sendMessage("§6§lFormulaRacing API §7- §eAvailable commands:");
        sender.sendMessage("§e/api status §7- Shows API status");
        sender.sendMessage("§e/api reload §7- Reloads API configuration");
        sender.sendMessage("§e/api info §7- Shows API info");
    }

    @Subcommand("status")
    public void onStatus(CommandSender sender) {
        ApiManager apiManager = FormulaRacing.getInstance().getApiManager();
        if (apiManager == null) {
            sender.sendMessage("§c§lERROR: §cAPI is not initialized!");
            return;
        }

        sender.sendMessage("§a§lAPI Status:");
        sender.sendMessage("§7Port: §f" + apiManager.getPort());
        sender.sendMessage("§7Status: §aOnline");
    }

    @Subcommand("reload")
    public void onReload(CommandSender sender) {
        ApiManager apiManager = FormulaRacing.getInstance().getApiManager();
        if (apiManager == null) {
            sender.sendMessage("§c§lERROR: §cAPI is not initialized!");
            return;
        }

        apiManager.reloadConfig();
        sender.sendMessage("§a§lSUCCESS: §aAPI configuration reloaded!");
    }

    @Subcommand("info")
    public void onInfo(CommandSender sender) {
        ApiManager apiManager = FormulaRacing.getInstance().getApiManager();
        if (apiManager == null) {
            sender.sendMessage("§c§lERROR: §cAPI is not initialized!");
            return;
        }

        sender.sendMessage("§6§lAPI Information:");
        sender.sendMessage("§7Plugin Version: §f" + FormulaRacing.getInstance().getDescription().getVersion());
        sender.sendMessage("§7Port: §f" + apiManager.getPort());
        sender.sendMessage("§7Available endpoints:");
        sender.sendMessage("§f  GET /api/v1/readonly/tracks");
        sender.sendMessage("§f  GET /api/v1/readonly/tracks/:trackname");
        sender.sendMessage("§f  GET /api/v1/readonly/players/:uuidorusername");
        sender.sendMessage("§f  GET /api/v1/readonly/players/:uuid/timetrials/:trackname");
        sender.sendMessage("§f  GET /api/v1/readonly/leaderboard/:trackname");
        sender.sendMessage("§f  GET /api/v1/readonly/status");
    }
}
