package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.integration.WolfLangIntegration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

@CommandAlias("formularacing")
public class FormulaRacingCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public FormulaRacingCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Subcommand("reload")
    @Description("Recarrega as traducoes e re-registra no WolfLang")
    @CommandPermission("formularacing.admin")
    public void onReload(CommandSender sender) {
        plugin.reloadLangCache();
        sender.sendMessage(ChatColor.GREEN + "Traducoes recarregadas"
            + (WolfLangIntegration.isEnabled()
                ? " e reintegradas ao WolfLang."
                : " (WolfLang nao ativo)."));
    }

    @Subcommand("version")
    @Description("Mostra a versao do FormulaRacing e do servidor")
    public void onVersion(CommandSender sender) {
        String pluginVersion = plugin.getDescription().getVersion();
        String folia = plugin.getServer().getVersion().toLowerCase().contains("folia") ? "sim" : "nao";

        sender.sendMessage("§6§lFormulaRacing §7- §eVersao");
        sender.sendMessage("§7Plugin: §f" + pluginVersion);
        sender.sendMessage("§7Servidor: §f" + Bukkit.getVersion());
        sender.sendMessage("§7API (Bukkit): §f" + Bukkit.getBukkitVersion());
        sender.sendMessage("§7Java: §f" + System.getProperty("java.version"));
        sender.sendMessage("§7Folia: §f" + folia);
        sender.sendMessage("§7Autores: §f" + String.join(", ", plugin.getDescription().getAuthors()));
    }
}
