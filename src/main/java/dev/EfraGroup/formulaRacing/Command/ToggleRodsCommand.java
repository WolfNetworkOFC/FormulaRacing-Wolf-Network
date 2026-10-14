package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.LightningRodManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("togglerods")
public class ToggleRodsCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public ToggleRodsCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    @Description("Alterna a visibilidade dos lightning rods")
    public void onToggle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ibrutils.togglerods")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(TextColor.color(0xFF5555)));
            return;
        }

        // Global toggle (admin only)
        if (args.length > 0 && args[0].equalsIgnoreCase("global")) {
            if (!sender.hasPermission("ibrutils.togglerods.global")) {
                sender.sendMessage(Component.text("You do not have permission to toggle global rod visibility.").color(TextColor.color(0xFF5555)));
                return;
            }

            LightningRodManager.setLightningRodsEnabled(!LightningRodManager.isLightningRodsEnabled());
            sender.sendMessage(Component.text("Global lightning rods are now " + (LightningRodManager.isLightningRodsEnabled() ? "enabled" : "disabled") + ".").color(TextColor.color(0x55FF55)));
            plugin.getLogger().info("[LightningRod] Global visibility toggled by " + sender.getName());
            return;
        }

        // Personal toggle
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can toggle their personal rod visibility.").color(TextColor.color(0xFF5555)));
            return;
        }

        boolean currentToggle = LightningRodManager.isPlayerToggleEnabled(player.getUniqueId());
        LightningRodManager.setPlayerToggle(player.getUniqueId(), !currentToggle);
        player.sendMessage(Component.text("Your lightning rod visibility is now " + (!currentToggle ? "enabled" : "disabled") + ".").color(TextColor.color(0x55FF55)));
    }
}