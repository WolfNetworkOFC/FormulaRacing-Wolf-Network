package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

@CommandAlias("lonely")
@Description("Controlador de sessões solo e isolamento de pista.")
public class LonelyCommand extends BaseCommand {
    private final FormulaRacing plugin;

    public LonelyCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    public void onDefault(Player player, @Optional Boolean enabled) {
        boolean nextValue = enabled != null
                ? enabled
                : !this.plugin.getLonelyController().isLonely(player.getUniqueId());

        this.plugin.getLonelyController().setLonelyMode(player, nextValue);
        player.sendMessage(nextValue
                ? ChatColor.GREEN + "✓ Modo lonely ativado."
                : ChatColor.YELLOW + "✓ Modo lonely desativado.");
    }

    @CatchUnknown
    public void onUnknown(Player player) {
        CommandHelpService.sendHelp(player, this, "/lonely");
    }

    @Subcommand("help|ajuda|?")
    @Description("Mostra a ajuda do comando lonely")
    public void onHelp(Player player) {
        CommandHelpService.sendHelp(player, this, "/lonely");
    }

    @Subcommand("emergency")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("on|off|status")
    @Description("Ativa ou desativa fallback de emergência do sistema de visibilidade")
    public void onEmergency(Player sender, String action) {
        String normalized = action.toLowerCase();
        switch (normalized) {
            case "on" -> {
                this.plugin.getLonelyController().setEmergencyMode(true, sender.getName());
                sender.sendMessage(ChatColor.RED + "⚠ Emergency lonely mode ativado. Visibilidade padrão forçada.");
            }
            case "off" -> {
                this.plugin.getLonelyController().setEmergencyMode(false, sender.getName());
                sender.sendMessage(ChatColor.GREEN + "✓ Emergency lonely mode desativado.");
            }
            case "status" -> sender.sendMessage(ChatColor.AQUA + "Emergency mode: " + (this.plugin.getLonelyController().isEmergencyMode() ? ChatColor.RED + "ON" : ChatColor.GREEN + "OFF"));
            default -> sender.sendMessage(ChatColor.RED + "Uso: /lonely emergency <on|off|status>");
        }
    }

    @Subcommand("state")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@players")
    @Description("Mostra o estado atual do policy engine para um jogador")
    public void onState(Player sender, @Optional String targetName) {
        Player target = targetName == null ? sender : Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "✗ Jogador não encontrado ou offline.");
            return;
        }

        String state = this.plugin.getLonelyController().getPlayerStateDebug(target);
        sender.sendMessage(ChatColor.GRAY + "[LonelyState] " + ChatColor.WHITE + target.getName() + ChatColor.GRAY + " -> " + ChatColor.AQUA + state);
    }
}
