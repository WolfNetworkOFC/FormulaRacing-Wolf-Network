package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

@CommandAlias("ghost|gh")
@Description("Ativa ghost manual de um piloto em corrida")
public class GhostCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public GhostCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@players")
    @CommandPermission("formularacing.event.admin")
    public void onGhost(Player sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "✗ Jogador não encontrado ou offline.");
            return;
        }

        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "✗ Você não pode aplicar ghost em si mesmo.");
            return;
        }

        UUID targetUuid = target.getUniqueId();
        if (this.plugin.getLonelyController().isGhosted(targetUuid)) {
            sender.sendMessage(ChatColor.RED + "✗ Este piloto já está ghostado.");
            return;
        }

        RaceEventManager eventManager = this.plugin.getRaceEventManager();
        Optional<Heats> heatOpt = eventManager.getPlayerActiveHeat(targetUuid);
        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "✗ O alvo não está em um heat ativo.");
            return;
        }

        Heats heat = heatOpt.get();
        HeatState state = heat.getHeatState();
        if (state != HeatState.RACING && state != HeatState.STARTING) {
            sender.sendMessage(ChatColor.RED + "✗ O comando só pode ser usado em RACING ou STARTING.");
            return;
        }

        if (!heat.isPlayerActivelyRacing(targetUuid)) {
            sender.sendMessage(ChatColor.RED + "✗ O alvo não está correndo ativamente neste heat.");
            return;
        }

        if (!this.plugin.getLonelyController().ghost(targetUuid)) {
            sender.sendMessage(ChatColor.RED + "✗ Não foi possível ativar o ghost deste piloto.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Ghost ativado para " + ChatColor.WHITE + target.getName() + ChatColor.GREEN + ".");
        target.sendMessage(ChatColor.YELLOW + "⚠ Você foi ghostado por um administrador durante a corrida.");
        this.plugin.getDebugManager().logRaceSystem("[GHOST] " + sender.getName() + " ativou ghost em " + target.getName() + " no heat " + heat.getName());
    }
}
