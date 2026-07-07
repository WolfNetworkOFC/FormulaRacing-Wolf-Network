package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Round.EliminationRound;
import dev.EfraGroup.formulaRacing.Round.RoundState;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

@CommandAlias("elimination|elim")
@CommandPermission("formularacing.admin")
public class EliminationCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public EliminationCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Subcommand("create")
    @Description("Creates a new elimination round")
    public void onCreate(CommandSender sender,
                         @Name("eventId") int eventId,
                         @Name("roundNumber") int roundNumber,
                         @Name("interval") @Default("30") int intervalSeconds,
                         @Name("minDrivers") @Default("2") int minDrivers) {

        if (plugin.getRaceEventManager() == null) {
            sender.sendMessage(ChatColor.RED + "Event system not available!");
            return;
        }

        Optional<dev.EfraGroup.formulaRacing.Event.Events> eventOpt =
            plugin.getRaceEventManager().getEventById(eventId);

        if (eventOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Event #" + eventId + " not found!");
            return;
        }

        dev.EfraGroup.formulaRacing.Event.Events event = eventOpt.get();

        // Create elimination round
        EliminationRound eliminationRound = new EliminationRound(
            plugin,
            0,
            event,
            roundNumber,
            RoundType.ELIMINATION
        );

        eliminationRound.setEliminationIntervalSeconds(intervalSeconds);
        eliminationRound.setMinimumDrivers(minDrivers);

        // Add to event through EventSchedule
        event.getEventSchedule().getRounds().put(roundNumber, eliminationRound);

        // Save to database asynchronously
        plugin.getRaceEventManager().getDatabaseManager().createRound(
            event.getId(),
            roundNumber,
            RoundType.ELIMINATION
        ).thenAccept(roundId -> {
            eliminationRound.setId(roundId);
            sender.sendMessage(ChatColor.GREEN + "✓ Elimination round created and saved to the database!");
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "✗ Error saving round to database!");
            return null;
        });

        sender.sendMessage(ChatColor.GREEN + "✓ Elimination round created!");
        sender.sendMessage(ChatColor.GRAY + "Event: " + event.getDisplayName());
        sender.sendMessage(ChatColor.GRAY + "Round: R" + roundNumber + "E");
        sender.sendMessage(ChatColor.GRAY + "Interval: " + intervalSeconds + "s");
        sender.sendMessage(ChatColor.GRAY + "Minimum drivers: " + minDrivers);
    }

    @Subcommand("start")
    @Description("Starts an elimination round")
    public void onStart(CommandSender sender,
                        @Name("roundId") int roundId) {

        Optional<Rounds> roundOpt = findRoundById(roundId);

        if (roundOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Round #" + roundId + " not found!");
            return;
        }

        Rounds round = roundOpt.get();

        if (round.getRoundType() != RoundType.ELIMINATION) {
            sender.sendMessage(ChatColor.RED + "This round is not an elimination!");
            return;
        }

        if (round.getHeats().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "This round has no heats!");
            return;
        }

        if (round.start()) {
            sender.sendMessage(ChatColor.GREEN + "✓ Elimination round started!");
        } else {
            sender.sendMessage(ChatColor.RED + "✗ Failed to start elimination round!");
        }
    }

    private Optional<Rounds> findRoundById(int roundId) {
        for (dev.EfraGroup.formulaRacing.Event.Events event : plugin.getRaceEventManager().getActiveEvents()) {
            Optional<Rounds> roundOpt = event.getEventSchedule().getRounds().values()
                .stream()
                .filter(r -> r.getId() == roundId)
                .findFirst();
            if (roundOpt.isPresent()) {
                return roundOpt;
            }
        }
        return Optional.empty();
    }

    @Subcommand("stop")
    @Description("Para um round de eliminação")
    public void onStop(CommandSender sender,
                       @Name("roundId") int roundId) {

        Optional<Rounds> roundOpt = findRoundById(roundId);

        if (roundOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Round #" + roundId + " não encontrado!");
            return;
        }

        Rounds round = roundOpt.get();

        if (round.getRoundType() != RoundType.ELIMINATION) {
            sender.sendMessage(ChatColor.RED + "This round is not an elimination!");
            return;
        }

        // Parar todos os heats ativos
        round.getHeats().values().forEach(heat -> {
            if (heat.getHeatState() == HeatState.RACING) {
                heat.finishHeat();
            }
        });

        round.finishRound();

        sender.sendMessage(ChatColor.GREEN + "✓ Round de eliminação parado!");
    }

    @Subcommand("interval")
    @Description("Define o intervalo de eliminação")
    public void onInterval(CommandSender sender,
                           @Name("roundId") int roundId,
                           @Name("seconds") int seconds) {

        if (seconds < 5) {
            sender.sendMessage(ChatColor.RED + "O intervalo mínimo é 5 segundos!");
            return;
        }

        Optional<Rounds> roundOpt = findRoundById(roundId);

        if (roundOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Round #" + roundId + " não encontrado!");
            return;
        }

        Rounds round = roundOpt.get();

        if (!(round instanceof EliminationRound)) {
            sender.sendMessage(ChatColor.RED + "This round is not an elimination!");
            return;
        }

        EliminationRound eliminationRound = (EliminationRound) round;
        eliminationRound.setEliminationIntervalSeconds(seconds);

        sender.sendMessage(ChatColor.GREEN + "✓ Intervalo de eliminação definido para " + seconds + "s!");
    }

    @Subcommand("mindrivers")
    @Description("Define o mínimo de pilotos para parar eliminação")
    public void onMinDrivers(CommandSender sender,
                            @Name("roundId") int roundId,
                            @Name("minimum") int minimum) {

        if (minimum < 1) {
            sender.sendMessage(ChatColor.RED + "O mínimo deve ser pelo menos 1!");
            return;
        }

        Optional<Rounds> roundOpt = findRoundById(roundId);

        if (roundOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Round #" + roundId + " não encontrado!");
            return;
        }

        Rounds round = roundOpt.get();

        if (!(round instanceof EliminationRound)) {
            sender.sendMessage(ChatColor.RED + "This round is not an elimination!");
            return;
        }

        EliminationRound eliminationRound = (EliminationRound) round;
        eliminationRound.setMinimumDrivers(minimum);

        sender.sendMessage(ChatColor.GREEN + "✓ Mínimo de pilotos definido para " + minimum + "!");
    }

    @Subcommand("eliminate")
    @Description("Elimina manualmente um piloto")
    public void onEliminate(CommandSender sender,
                            @Name("roundId") int roundId,
                            @Name("player") Player player) {

        Optional<Rounds> roundOpt = findRoundById(roundId);

        if (roundOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Round #" + roundId + " não encontrado!");
            return;
        }

        Rounds round = roundOpt.get();

        if (round.getRoundType() != RoundType.ELIMINATION) {
            sender.sendMessage(ChatColor.RED + "This round is not an elimination!");
            return;
        }

        // Encontrar o heat ativo
        Optional<Heats> activeHeatOpt = round.getActiveHeat();

        if (activeHeatOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Nenhum heat ativo neste round!");
            return;
        }

        Heats heat = activeHeatOpt.get();
        dev.EfraGroup.formulaRacing.Participant.Driver driver = heat.getDriver(player.getUniqueId());

        if (driver == null) {
            sender.sendMessage(ChatColor.RED + "Este jogador não está participando do heat!");
            return;
        }

        if (driver.isDnf() || driver.isFinished()) {
            sender.sendMessage(ChatColor.RED + "Este jogador já foi eliminado!");
            return;
        }

        // Eliminar o piloto
        heat.handleDriverDNF(driver, "Manual elimination by admin");

        sender.sendMessage(ChatColor.GREEN + "✓ " + player.getName() + " foi eliminado manualmente!");
        player.sendMessage(ChatColor.RED + "Você foi eliminado por um administrador!");
    }

    @Subcommand("status")
    @Description("Mostra o status de um round de eliminação")
    public void onStatus(CommandSender sender,
                         @Name("roundId") int roundId) {

        Optional<Rounds> roundOpt = findRoundById(roundId);

        if (roundOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Round #" + roundId + " não encontrado!");
            return;
        }

        Rounds round = roundOpt.get();

        if (!(round instanceof EliminationRound)) {
            sender.sendMessage(ChatColor.RED + "This round is not an elimination!");
            return;
        }

        EliminationRound eliminationRound = (EliminationRound) round;

        sender.sendMessage(ChatColor.GOLD + "=== Status da Eliminação ===");
        sender.sendMessage(ChatColor.GRAY + "Round: " + ChatColor.WHITE + round.getName());
        sender.sendMessage(ChatColor.GRAY + "Estado: " + ChatColor.WHITE + round.getRoundState());
        sender.sendMessage(ChatColor.GRAY + "Intervalo: " + ChatColor.WHITE + eliminationRound.getEliminationIntervalSeconds() + "s");
        sender.sendMessage(ChatColor.GRAY + "Mínimo de pilotos: " + ChatColor.WHITE + eliminationRound.getMinimumDrivers());
        sender.sendMessage(ChatColor.GRAY + "Total de heats: " + ChatColor.WHITE + round.getHeats().size());

        // Mostrar status dos heats
        round.getHeats().values().forEach(heat -> {
            int activeDrivers = (int) heat.getDrivers().values().stream()
                .filter(d -> !d.isFinished() && !d.isDnf())
                .count();

            sender.sendMessage(ChatColor.GRAY + "  Heat " + heat.getHeatNumber() + ": " +
                ChatColor.WHITE + heat.getHeatState() +
                ChatColor.GRAY + " (" + activeDrivers + " pilotos)");
        });
    }

    @Subcommand("list")
    @Description("Lista todos os rounds de eliminação")
    public void onList(CommandSender sender) {

        if (plugin.getRaceEventManager() == null) {
            sender.sendMessage(ChatColor.RED + "Sistema de eventos não disponível!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Rounds de Eliminação ===");

        plugin.getRaceEventManager().getActiveEvents().forEach(event -> {
            event.getEventSchedule().getRounds().values().forEach(round -> {
                if (round.getRoundType() == RoundType.ELIMINATION) {
                    sender.sendMessage(ChatColor.GRAY + "  Round #" + round.getId() + ": " +
                        ChatColor.WHITE + round.getName() +
                        ChatColor.GRAY + " (" + round.getRoundState() + ")");
                }
            });
        });
    }
}
