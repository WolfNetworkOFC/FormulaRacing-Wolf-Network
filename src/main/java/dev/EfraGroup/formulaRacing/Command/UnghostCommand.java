package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

@CommandAlias("unghost|ugh")
@Description("Desativa ghost manual de um piloto")
public class UnghostCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public UnghostCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@players")
    @CommandPermission("formularacing.event.admin")
    public void onUnghost(Player sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "✗ Jogador não encontrado ou offline.");
            return;
        }

        UUID targetUuid = target.getUniqueId();
        if (!this.plugin.getLonelyController().isGhosted(targetUuid)) {
            sender.sendMessage(ChatColor.RED + "✗ Este piloto não está ghostado.");
            return;
        }

        if (!this.plugin.getLonelyController().unghost(targetUuid)) {
            sender.sendMessage(ChatColor.RED + "✗ Não foi possível remover o ghost deste piloto.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Ghost removido de " + ChatColor.WHITE + target.getName() + ChatColor.GREEN + ".");
        target.sendMessage(ChatColor.YELLOW + "⚠ Seu ghost foi removido por um administrador.");
        this.plugin.getDebugManager().logRaceSystem("[GHOST] " + sender.getName() + " removeu ghost de " + target.getName());
    }
}
