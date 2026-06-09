package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.LightningRodManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("togglerods")
public class ToggleRodsCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public ToggleRodsCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Subcommand("toggle|t")
    @CommandPermission("formularacing.spectator")
    @Description("Alterna a visibilidade dos ratos de lightning")
    public void onToggle(Player player) {
        boolean currentToggle = LightningRodManager.isPlayerToggleEnabled(player.getUniqueId());
        LightningRodManager.setPlayerToggle(player.getUniqueId(), !currentToggle);
        
        String status = LightningRodManager.isPlayerToggleEnabled(player.getUniqueId()) ? "ativada" : "desativada";
        player.sendMessage(
            ChatColor.YELLOW + "[LightningRod] Sua visibilidade de rato foi " + status
        );
    }

    @Subcommand("global|admin")
    @CommandPermission("formularacing.admin")
    @Description("Alterna a visibilidade global de ratos (admin)")
    public void onGlobalToggle(CommandSender sender) {
        boolean currentState = LightningRodManager.isLightningRodsEnabled();
        LightningRodManager.setLightningRodsEnabled(!currentState);
        
        String status = LightningRodManager.isLightningRodsEnabled() ? "ativada" : "desativada";
        sender.sendMessage(
            ChatColor.YELLOW + "[LightningRod] Visibilidade global de ratos foi " + status
        );
        
        plugin.getLogger().info("[LightningRod] Global visibility " + status + " by " + sender.getName());
    }

    @Subcommand("status")
    @CommandPermission("formularacing.spectator")
    @Description("Mostra o status atual dos ratos")
    public void onStatus(Player player) {
        boolean globalEnabled = LightningRodManager.isLightningRodsEnabled();
        boolean personalToggle = LightningRodManager.isPlayerToggleEnabled(player.getUniqueId());
        
        player.sendMessage(ChatColor.YELLOW + "[LightningRod] Status:");
        player.sendMessage("  Global: " + (globalEnabled ? "§aativado" : "§cdesativado"));
        player.sendMessage("  Pessoal: " + (personalToggle ? "§aativado" : "§cdesativado"));
    }
}