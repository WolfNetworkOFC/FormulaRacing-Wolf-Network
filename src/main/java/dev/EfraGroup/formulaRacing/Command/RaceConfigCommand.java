package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatConfig;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Optional;

@CommandAlias("raceconfig|rc")
@CommandPermission("formularacing.admin")
public class RaceConfigCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public RaceConfigCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Subcommand("timebased")
    @Description("Configura corrida baseada em tempo")
    public void onTimeBased(CommandSender sender,
                           @Name("heatId") int heatId,
                           @Name("enabled") boolean enabled,
                           @Name("timeLimit") @Default("600") int timeLimitSeconds) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();
        HeatConfig config = heat.getHeatConfig();

        config.setTimeBased(enabled);
        config.setTimeLimitSeconds(timeLimitSeconds);

        sender.sendMessage(ChatColor.GREEN + "✓ Configuração de corrida baseada em tempo atualizada!");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
        sender.sendMessage(ChatColor.GRAY + "Modo Tempo: " + ChatColor.WHITE + (enabled ? "Ativado" : "Desativado"));
        sender.sendMessage(ChatColor.GRAY + "Limite de Tempo: " + ChatColor.WHITE + timeLimitSeconds + "s");
    }

    @Subcommand("checkeredflag")
    @Description("Configura fluxo de bandeira quadriculada")
    public void onCheckeredFlag(CommandSender sender,
                               @Name("heatId") int heatId,
                               @Name("enabled") boolean enabled) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();
        HeatConfig config = heat.getHeatConfig();

        config.setEnableCheckeredFlagFlow(enabled);

        sender.sendMessage(ChatColor.GREEN + "✓ Configuração de bandeira quadriculada atualizada!");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
        sender.sendMessage(ChatColor.GRAY + "Fluxo Checkered Flag: " + ChatColor.WHITE + (enabled ? "Ativado" : "Desativado"));
    }

    @Subcommand("fuel")
    @Description("Configura sistema de combustível")
    public void onFuel(CommandSender sender,
                       @Name("heatId") int heatId,
                       @Name("enabled") boolean enabled,
                       @Name("startingFuel") @Default("100") double startingFuel,
                       @Name("consumption") @Default("0.45") double consumptionPerSecond) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);
        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();
        HeatConfig config = heat.getHeatConfig();
        config.setFuelSystemEnabled(enabled);
        config.setStartingFuel(startingFuel);
        config.setFuelConsumptionPerSecond(consumptionPerSecond);

        sender.sendMessage(ChatColor.GREEN + "✓ Configuração de combustível atualizada!");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
        sender.sendMessage(ChatColor.GRAY + "Combustível: " + ChatColor.WHITE + (enabled ? "Ativado" : "Desativado"));
        sender.sendMessage(ChatColor.GRAY + "Carga inicial: " + ChatColor.WHITE + config.getStartingFuel() + "%");
        sender.sendMessage(ChatColor.GRAY + "Consumo/s: " + ChatColor.WHITE + config.getFuelConsumptionPerSecond());
    }

    @Subcommand("status")
    @Description("Mostra o status da configuração do heat")
    public void onStatus(CommandSender sender,
                        @Name("heatId") int heatId) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();
        HeatConfig config = heat.getHeatConfig();

        sender.sendMessage(ChatColor.GOLD + "=== Configuração da Corrida ===");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + ChatColor.WHITE + heat.getName());
        sender.sendMessage(ChatColor.GRAY + "Estado: " + ChatColor.WHITE + heat.getHeatState());
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Modo de Corrida:");
        sender.sendMessage(ChatColor.GRAY + "  Baseado em Tempo: " + ChatColor.WHITE + (config.isTimeBased() ? "Sim" : "Não"));
        if (config.isTimeBased()) {
            sender.sendMessage(ChatColor.GRAY + "  Limite de Tempo: " + ChatColor.WHITE + config.getTimeLimitSeconds() + "s");
            sender.sendMessage(ChatColor.GRAY + "  Última Volta Acionada: " + ChatColor.WHITE + (config.isLastLapTriggered() ? "Sim" : "Não"));
        }
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Finalização:");
        sender.sendMessage(ChatColor.GRAY + "  Checkered Flag Flow: " + ChatColor.WHITE + (config.isEnableCheckeredFlagFlow() ? "Ativado" : "Desativado"));
        sender.sendMessage(ChatColor.GRAY + "  Corrida Finalizada: " + ChatColor.WHITE + (config.isRaceFinishedForAll() ? "Sim" : "Não"));
        sender.sendMessage(ChatColor.GRAY + "  Combustível: " + ChatColor.WHITE + (config.isFuelSystemEnabled() ? "Ativado" : "Desativado"));
        if (config.isFuelSystemEnabled()) {
            sender.sendMessage(ChatColor.GRAY + "  Carga inicial: " + ChatColor.WHITE + config.getStartingFuel() + "%");
            sender.sendMessage(ChatColor.GRAY + "  Consumo/s: " + ChatColor.WHITE + config.getFuelConsumptionPerSecond());
        }
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Configurações Básicas:");
        sender.sendMessage(ChatColor.GRAY + "  Voltas Totais: " + ChatColor.WHITE + heat.getTotalLaps());
        sender.sendMessage(ChatColor.GRAY + "  Pits Obrigatórios: " + ChatColor.WHITE + heat.getTotalPits());
        sender.sendMessage(ChatColor.GRAY + "  Pilotos: " + ChatColor.WHITE + heat.getDriverCount());
    }

    @Subcommand("reset")
    @Description("Reseta a configuração do heat")
    public void onReset(CommandSender sender,
                       @Name("heatId") int heatId) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();
        HeatConfig config = heat.getHeatConfig();

        config.reset();

        sender.sendMessage(ChatColor.GREEN + "✓ Configuração do heat resetada!");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
    }

    @Subcommand("laps")
    @Description("Define o número de voltas")
    public void onLaps(CommandSender sender,
                      @Name("heatId") int heatId,
                      @Name("laps") int laps) {

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();
        heat.setTotalLaps(laps);

        sender.sendMessage(ChatColor.GREEN + "✓ Número de voltas definido!");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
        sender.sendMessage(ChatColor.GRAY + "Voltas: " + ChatColor.WHITE + laps);
    }

    @Subcommand("pits")
    @Description("Define o número de pits obrigatórios")
    public void onPits(CommandSender sender,
                      @Name("heatId") int heatId,
                      @Name("pits") int pits) {

        if (pits < 0) {
            sender.sendMessage(ChatColor.RED + "O número de pits não pode ser negativo!");
            return;
        }

        Optional<Heats> heatOpt = plugin.getRaceEventManager().getHeat(heatId);

        if (heatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Heat #" + heatId + " não encontrado!");
            return;
        }

        Heats heat = heatOpt.get();
        heat.setTotalPits(pits);

        sender.sendMessage(ChatColor.GREEN + "✓ Número de pits obrigatórios definido!");
        sender.sendMessage(ChatColor.GRAY + "Heat: " + heat.getName());
        sender.sendMessage(ChatColor.GRAY + "Pits: " + ChatColor.WHITE + pits);
    }
}
