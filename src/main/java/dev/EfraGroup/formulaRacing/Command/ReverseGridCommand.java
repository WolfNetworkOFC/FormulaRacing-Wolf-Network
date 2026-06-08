package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

@CommandAlias("reversegrid|rg")
@CommandPermission("formularacing.admin")
public class ReverseGridCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public ReverseGridCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Subcommand("invert")
    @Description("Inverte o grid do heat atual")
    public void onInvert(CommandSender sender,
                         @Name("heatId") int heatId,
                         @Name("percentage") @Default("100") int percentage) {

        if (percentage < 1 || percentage > 100) {
            sender.sendMessage(ChatColor.RED + "A porcentagem deve estar entre 1 e 100!");
            return;
        }

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();

        if (heat.getHeatState() != HeatState.SETUP && heat.getHeatState() != HeatState.LOADED) {
            sender.sendMessage(ChatColor.RED + "O heat deve estar em SETUP ou LOADED para inverter o grid!");
            return;
        }

        heat.reverseGrid(percentage);

        sender.sendMessage(ChatColor.GREEN + "✓ Grid invertido com sucesso!");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
        sender.sendMessage(ChatColor.GRAY + "Porcentagem: " + percentage + "%");
        sender.sendMessage(ChatColor.GRAY + "Pilotos afetados: " + (heat.getDriverCount() * percentage / 100));
    }

    @Subcommand("invert full")
    @Description("Inverte o grid completo (100%)")
    public void onInvertFull(CommandSender sender,
                             @Name("heatId") int heatId) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();

        if (heat.getHeatState() != HeatState.SETUP && heat.getHeatState() != HeatState.LOADED) {
            sender.sendMessage(ChatColor.RED + "O heat deve estar em SETUP ou LOADED para inverter o grid!");
            return;
        }

        heat.reverseFullGrid();

        sender.sendMessage(ChatColor.GREEN + "✓ Grid completo invertido!");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
        sender.sendMessage(ChatColor.GRAY + "Pilotos afetados: " + heat.getDriverCount());
    }

    @Subcommand("restore")
    @Description("Restaura o grid para a ordem original")
    public void onRestore(CommandSender sender,
                         @Name("heatId") int heatId) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();

        if (heat.getHeatState() != HeatState.SETUP && heat.getHeatState() != HeatState.LOADED) {
            sender.sendMessage(ChatColor.RED + "O heat deve estar em SETUP ou LOADED para restaurar o grid!");
            return;
        }

        heat.restoreOriginalGrid();

        sender.sendMessage(ChatColor.GREEN + "✓ Grid restaurado para ordem original!");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
    }

    @Subcommand("status")
    @Description("Mostra o status do grid do heat")
    public void onStatus(CommandSender sender,
                         @Name("heatId") int heatId) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();

        sender.sendMessage(ChatColor.GOLD + "=== Status do Grid ===");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + ChatColor.WHITE + heat.getName());
        sender.sendMessage(ChatColor.GRAY + "Estado: " + ChatColor.WHITE + heat.getHeatState());
        sender.sendMessage(ChatColor.GRAY + "Grid Invertido: " + ChatColor.WHITE + (heat.isGridReversed() ? "Sim" : "Não"));
        sender.sendMessage(ChatColor.GRAY + "Total de Pilotos: " + ChatColor.WHITE + heat.getDriverCount());

        // Mostrar grid atual
        sender.sendMessage(ChatColor.GRAY + "Grid Atual:");
        int position = 1;
        for (dev.EfraGroup.formulaRacing.Participant.Driver driver : heat.getStartPositions()) {
            Player player = plugin.getServer().getPlayer(driver.getUuid());
            String playerName = player != null ? player.getName() : "Offline";
            sender.sendMessage(ChatColor.GRAY + "  " + position + ". " + ChatColor.WHITE + playerName +
                ChatColor.GRAY + " (Posição Original: " + driver.getStartPosition() + ")");
            position++;
        }
    }

    @Subcommand("toggle")
    @Description("Alterna o estado do grid reverso")
    public void onToggle(CommandSender sender,
                         @Name("heatId") int heatId) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();

        if (heat.getHeatState() != HeatState.SETUP && heat.getHeatState() != HeatState.LOADED) {
            sender.sendMessage(ChatColor.RED + "O heat deve estar em SETUP ou LOADED para alterar o grid!");
            return;
        }

        if (heat.isGridReversed()) {
            heat.restoreOriginalGrid();
            sender.sendMessage(ChatColor.GREEN + "✓ Grid restaurado para ordem original!");
        } else {
            heat.reverseFullGrid();
            sender.sendMessage(ChatColor.GREEN + "✓ Grid invertido!");
        }

        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
    }
}
