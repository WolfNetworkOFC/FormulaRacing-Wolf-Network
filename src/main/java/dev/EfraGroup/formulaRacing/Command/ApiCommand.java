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
        sender.sendMessage("§6§lFormulaRacing API §7- §eComandos disponíveis:");
        sender.sendMessage("§e/api status §7- Mostra o status da API");
        sender.sendMessage("§e/api reload §7- Recarrega a configuração da API");
        sender.sendMessage("§e/api info §7- Mostra informações da API");
    }

    @Subcommand("status")
    public void onStatus(CommandSender sender) {
        ApiManager apiManager = FormulaRacing.getInstance().getApiManager();
        if (apiManager == null) {
            sender.sendMessage("§c§lERRO: §cA API não está inicializada!");
            return;
        }

        sender.sendMessage("§a§lStatus da API:");
        sender.sendMessage("§7Porta: §f" + apiManager.getPort());
        sender.sendMessage("§7Status: §aOnline");
    }

    @Subcommand("reload")
    public void onReload(CommandSender sender) {
        ApiManager apiManager = FormulaRacing.getInstance().getApiManager();
        if (apiManager == null) {
            sender.sendMessage("§c§lERRO: §cA API não está inicializada!");
            return;
        }

        apiManager.reloadConfig();
        sender.sendMessage("§a§lSUCESSO: §aConfiguração da API recarregada!");
    }

    @Subcommand("info")
    public void onInfo(CommandSender sender) {
        ApiManager apiManager = FormulaRacing.getInstance().getApiManager();
        if (apiManager == null) {
            sender.sendMessage("§c§lERRO: §cA API não está inicializada!");
            return;
        }

        sender.sendMessage("§6§lInformações da API:");
        sender.sendMessage("§7Versão do Plugin: §f" + FormulaRacing.getInstance().getDescription().getVersion());
        sender.sendMessage("§7Porta: §f" + apiManager.getPort());
        sender.sendMessage("§7Endpoints disponíveis:");
        sender.sendMessage("§f  GET /api/v1/readonly/tracks");
        sender.sendMessage("§f  GET /api/v1/readonly/tracks/:trackname");
        sender.sendMessage("§f  GET /api/v1/readonly/players/:uuidorusername");
        sender.sendMessage("§f  GET /api/v1/readonly/players/:uuid/timetrials/:trackname");
        sender.sendMessage("§f  GET /api/v1/readonly/leaderboard/:trackname");
        sender.sendMessage("§f  GET /api/v1/readonly/status");
    }
}
