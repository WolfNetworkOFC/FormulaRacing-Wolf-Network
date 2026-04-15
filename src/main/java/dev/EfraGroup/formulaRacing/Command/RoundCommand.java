//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.Controllers.HeatDriverCommandService;
import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Event.EventState;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.ApiUtilities;
import dev.EfraGroup.formulaRacing.Utils.ClickableMessageUtil;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@CommandAlias("round")
public class RoundCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final RaceEventManager eventManager;
    private final DatabaseManager database;
    private final HeatDriverCommandService heatDriverService;

    public RoundCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.eventManager = plugin.getRaceEventManager();
        this.database = plugin.getDatabaseManager();
        this.heatDriverService = new HeatDriverCommandService(plugin);
    }

    @Default
    @Description("Mostra info do round atual")
    public void onDefault(Player player) {
        Optional<Events> eventOpt = this.database.getPlayerSelectedEvent(
            player.getUniqueId()
        );
        if (eventOpt.isEmpty()) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum evento selecionado! Use /event info <evento> primeiro."
            );
        } else {
            Events event = (Events) eventOpt.get();
            Optional<Rounds> roundOpt = event.getSchedule().getCurrentRound();
            if (roundOpt.isPresent()) {
                this.onInfo(player, (Rounds) roundOpt.get());
            } else {
                String var10001 = String.valueOf(ChatColor.YELLOW);
                player.sendMessage(
                    var10001 +
                        "Nenhuma rodada ativa no evento " +
                        event.getDisplayName()
                );
                ClickableMessageUtil.sendClickableLine(
                    player,
                    String.valueOf(ChatColor.GRAY) + "Use ",
                    "/round create <tipo>",
                    " para criar uma.",
                    "/round create ",
                    "§aClique para sugerir o comando de criação",
                    true
                );
            }
        }
    }

    @Subcommand("create|new")
    @CommandCompletion("PRACTICE|QUALIFICATION|FINAL @event")
    @CommandPermission("formularacing.event.admin")
    @Description("Cria um novo round no evento")
    public void onCreate(
        Player player,
        RoundType type,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = this.database.getPlayerSelectedEvent(
                player.getUniqueId()
            ).orElse(null);
        }

        if (event == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) + "✗ Nenhum evento selecionado!"
            );
        } else {
            int nextIndex = event.getSchedule().getRounds().size() + 1;
            this.eventManager.createRound(event, type, nextIndex).thenAccept(
                r -> {
                    if (r != null) {
                        player.sendMessage(
                            String.valueOf(ChatColor.GREEN) +
                                "✓ Round " +
                                nextIndex +
                                " (" +
                                String.valueOf(type) +
                                ") criado com sucesso!"
                        );
                    } else {
                        player.sendMessage(
                            String.valueOf(ChatColor.RED) +
                                "✗ Erro ao criar round (Falha no Banco de Dados)."
                        );
                    }
                }
            );
        }
    }

    @Subcommand("info|view")
    @CommandCompletion("@round")
    @Description("Mostra informações detalhadas de um round")
    public void onInfo(
        Player player,
        @co.aikar.commands.annotation.Optional Rounds round
    ) {
        if (round == null) {
            Events event = this.database.getPlayerSelectedEvent(
                player.getUniqueId()
            ).orElse(null);
            if (event != null) {
                round = event.getSchedule().getCurrentRound().orElse(null);
            }
        }

        if (round == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum round selecionado ou ativo!"
            );
            return;
        }

        boolean isAdmin = player.hasPermission("formularacing.event.admin");
        player.sendMessage("");
        TextComponent header = new TextComponent("");
        header.addExtra(
            ClickableMessageUtil.getRefreshButton(
                "/round info " + round.getId(),
                "Atualizar"
            )
        );
        header.addExtra(new TextComponent(" "));
        TextComponent title = new TextComponent(
            ("ROUND " + round.getRoundNumber()).toUpperCase()
        );
        title.setColor(ChatColor.GOLD);
        title.setBold(true);
        header.addExtra(title);
        String var10003 = String.valueOf(ChatColor.GRAY);
        header.addExtra(
            new TextComponent(var10003 + " (" + round.getType().name() + ")")
        );
        header.addExtra(new TextComponent(" "));
        header.addExtra(
            ClickableMessageUtil.getButton(
                "Event Info",
                ChatColor.BLUE,
                "/event info " + round.getEvent().getDisplayName(),
                "Voltar para o evento",
                Action.RUN_COMMAND
            )
        );
        player.spigot().sendMessage(header);
        String var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(
            var10001 +
                "  Estado: " +
                String.valueOf(ChatColor.WHITE) +
                String.valueOf(round.getState())
        );
        player.sendMessage("");
        String var10002 = String.valueOf(ChatColor.GOLD);
        TextComponent heatsHeader = new TextComponent(
            var10002 + String.valueOf(ChatColor.BOLD) + "  HEATS:"
        );
        if (isAdmin) {
            heatsHeader.addExtra(new TextComponent("   "));
            heatsHeader.addExtra(
                ClickableMessageUtil.getButton(
                    "+ Novo Heat",
                    ChatColor.GREEN,
                    "/heat create " +
                        round.getEvent().getDisplayName() +
                        " " +
                        round.getDisplayName(),
                    "Adicionar Heat",
                    Action.RUN_COMMAND
                )
            );
        }

        player.spigot().sendMessage(heatsHeader);
        if (round.getHeats().isEmpty()) {
            player.sendMessage(
                String.valueOf(ChatColor.GRAY) + "   (Nenhum heat criado)"
            );
        } else {
            for (Heats heat : round.getHeatsOrdered()) {
                String heatName = heat.getName();
                String status = heat.getHeatState().name();
                ChatColor statusColor = ChatColor.GRAY;
                if (heat.getHeatState() == HeatState.FINISHED) {
                    statusColor = ChatColor.GREEN;
                } else if (heat.getHeatState() == HeatState.RACING) {
                    statusColor = ChatColor.AQUA;
                }

                TextComponent line = new TextComponent(
                    "   " + heatName + " - "
                );
                TextComponent statusComp = new TextComponent(status);
                statusComp.setColor(statusColor);
                line.addExtra(statusComp);
                line.addExtra(new TextComponent("   "));
                line.addExtra(
                    ClickableMessageUtil.getButton(
                        "Info",
                        ChatColor.AQUA,
                        "/heat info " + heat.getId(),
                        "Ver detalhes do heat",
                        Action.RUN_COMMAND
                    )
                );
                if (isAdmin) {
                    line.addExtra(new TextComponent(" "));
                    line.addExtra(
                        ClickableMessageUtil.getButton(
                            "✖",
                            ChatColor.RED,
                            "/heat delete " + heat.getId(),
                            "Excluir heat",
                            Action.SUGGEST_COMMAND
                        )
                    );
                }

                player.spigot().sendMessage(line);
            }
        }

        if (
            round.getType() == RoundType.QUALIFICATION ||
            round.getType() == RoundType.PRACTICE
        ) {
            player.sendMessage("");
            ClickableMessageUtil.sendClickableLine(
                player,
                "  ",
                String.valueOf(ChatColor.GOLD) + "[VER RESULTADOS GERAIS]",
                "",
                "/round results " + round.getId(),
                "Ver ranking combinado",
                false
            );
        }

        player.sendMessage("");
    }

    @Subcommand("results")
    @CommandCompletion("@round")
    @CommandPermission("formularacing.event.results")
    @Description("Mostra os resultados de um round")
    public void onResults(
        Player player,
        @co.aikar.commands.annotation.Optional Rounds round
    ) {
        if (round == null) {
            Events event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
            if (event != null) {
                round = event.getSchedule().getCurrentRound().orElse(null);
            }
        }

        if (round == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum round selecionado ou ativo!"
            );
        } else {
            this.displayRoundRanking(player, round);
        }
    }

    private void displayRoundRanking(Player player, Rounds round) {
        player.sendMessage("");
        String var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(
            var10001 +
                String.valueOf(ChatColor.BOLD) +
                "═══════════════════════════════"
        );
        var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(
            var10001 +
                "    RESULTADOS: " +
                String.valueOf(ChatColor.WHITE) +
                round.getDisplayName()
        );
        var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(
            var10001 +
                String.valueOf(ChatColor.BOLD) +
                "═══════════════════════════════"
        );
        Map<UUID, Long> bestLaps = new HashMap();

        for (Heats heat : round.getHeats().values()) {
            for (Driver driver : heat.getDrivers().values()) {
                if (driver.getFastestLap() != null) {
                    long time = driver.getFastestLap().getLapTime();
                    if (
                        !bestLaps.containsKey(driver.getUuid()) ||
                        time < (Long) bestLaps.get(driver.getUuid())
                    ) {
                        bestLaps.put(driver.getUuid(), time);
                    }
                }
            }
        }

        if (bestLaps.isEmpty()) {
            player.sendMessage(
                String.valueOf(ChatColor.GRAY) +
                    "  Ainda não há tempos registrados."
            );
        } else {
            List<UUID> sortedDrivers = new ArrayList(bestLaps.keySet());
            Objects.requireNonNull(bestLaps);
            sortedDrivers.sort(Comparator.comparingLong(bestLaps::get));
            int pos = 1;

            for (UUID uuid : sortedDrivers) {
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                String time = ApiUtilities.formatRaceTime(
                    (Long) bestLaps.get(uuid)
                );
                String color =
                    pos <= 3
                        ? ChatColor.GREEN.toString()
                        : ChatColor.GRAY.toString();
                if (pos == 1) {
                    color = ChatColor.GOLD.toString();
                }

                player.sendMessage(
                    String.format(
                        "  " +
                            color +
                            "#%d " +
                            String.valueOf(ChatColor.WHITE) +
                            "%-12s " +
                            String.valueOf(ChatColor.YELLOW) +
                            "%s",
                        pos,
                        name,
                        time
                    )
                );
                ++pos;
                if (pos > 15) {
                    break;
                }
            }

            player.sendMessage("");
        }
    }

    @Subcommand("start")
    @CommandCompletion("@round")
    @CommandPermission("formularacing.event.admin")
    @Description("Inicia um round")
    public void onStart(
        Player player,
        @co.aikar.commands.annotation.Optional Rounds round
    ) {
        if (round == null) {
            Events event = this.database.getPlayerSelectedEvent(
                player.getUniqueId()
            ).orElse(null);
            if (event != null) {
                round = event.getSchedule().getCurrentRound().orElse(null);
            }
        }

        if (round == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum round selecionado ou ativo!"
            );
        } else {
            if (round.start()) {
                String var10001 = String.valueOf(ChatColor.GREEN);
                player.sendMessage(
                    var10001 +
                        "✓ Round " +
                        round.getDisplayName() +
                        " iniciado!"
                );
            } else {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) + "✗ Falha ao iniciar round."
                );
            }
        }
    }

    @Subcommand("finish|stop")
    @CommandCompletion("@round")
    @CommandPermission("formularacing.event.admin")
    @Description("Finaliza um round")
    public void onFinish(
        Player player,
        @co.aikar.commands.annotation.Optional Rounds round
    ) {
        if (round == null) {
            Events event = this.database.getPlayerSelectedEvent(
                player.getUniqueId()
            ).orElse(null);
            if (event != null) {
                round = event.getSchedule().getCurrentRound().orElse(null);
            }
        }

        if (round == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum round selecionado ou ativo!"
            );
        } else {
            round.finish();
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(
                var10001 + "✓ Round " + round.getDisplayName() + " finalizado!"
            );
        }
    }

    @Subcommand("delete|remove")
    @CommandCompletion("@round")
    @CommandPermission("formularacing.event.admin")
    @Description("Remove um round")
    public void onDelete(
        Player player,
        @co.aikar.commands.annotation.Optional Rounds round
    ) {
        if (round == null) {
            Events event = this.database.getPlayerSelectedEvent(
                player.getUniqueId()
            ).orElse(null);
            if (event != null) {
                round = event.getSchedule().getCurrentRound().orElse(null);
            }
        }

        if (round == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum round selecionado ou ativo!"
            );
        } else if (this.plugin.getRaceEventManager().removeRound(round)) {
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(
                var10001 + "✓ Round " + round.getDisplayName() + " removido."
            );
        } else {
            player.sendMessage(
                String.valueOf(ChatColor.RED) + "✗ Falha ao remover round."
            );
        }
    }

    @Subcommand("clear|removedrivers")
    @CommandCompletion("@round")
    @CommandPermission("formularacing.event.admin")
    @Description("Remove todos os pilotos de todos os heats do round")
    public void onClear(
        Player player,
        @co.aikar.commands.annotation.Optional Rounds round
    ) {
        if (round == null) {
            Events event = this.database.getPlayerSelectedEvent(
                player.getUniqueId()
            ).orElse(null);
            if (event != null) {
                round = event.getSchedule().getCurrentRound().orElse(null);
            }
        }

        if (round == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum round selecionado ou ativo!"
            );
        } else {
            for (Heats heat : round.getHeats().values()) {
                if (heat.getHeatState() != HeatState.SETUP) {
                    String var10001 = String.valueOf(ChatColor.RED);
                    player.sendMessage(
                        var10001 +
                            "✗ Não é possível remover pilotos do " +
                            heat.getName() +
                            " pois ele já foi iniciado ou finalizado."
                    );
                    return;
                }
            }

            for (Heats heat : round.getHeats().values()) {
                if (
                    !this.plugin.getRaceEventManager()
                        .getDatabaseManager()
                        .clearHeatDriversSync(heat.getId())
                ) {
                    String var10002 = String.valueOf(ChatColor.RED);
                    player.sendMessage(
                        var10002 +
                            "✗ Falha ao limpar pilotos do heat " +
                            heat.getName() +
                            " no banco de dados."
                    );
                    return;
                }

                heat.getDrivers().clear();
                heat.reorderGrid();
            }

            String var8 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(
                var8 +
                    "✓ Todos os pilotos foram removidos do round " +
                    round.getDisplayName()
            );
        }
    }

    @Subcommand("fill|fillheats")
    @CommandCompletion("@round random|sorted all|signed|reserves")
    @CommandPermission("formularacing.event.admin")
    @Description("Preenche heats do round automaticamente")
    public void onFill(
        Player player,
        @co.aikar.commands.annotation.Optional Rounds round,
        String sortMode,
        String groupMode
    ) {
        if (round == null) {
            Events event = this.database.getPlayerSelectedEvent(
                player.getUniqueId()
            ).orElse(null);
            if (event != null) {
                round = event.getSchedule().getCurrentRound().orElse(null);
            }
        }

        if (round == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum round selecionado ou ativo!"
            );
        } else {
            Events event = round.getEvent();
            if (event == null) {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Evento associado ao round não encontrado!"
                );
            } else if (event.getState() != EventState.SETUP) {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Evento já começou! Não é possível preencher heats."
                );
            } else {
                List<Heats> heats = new ArrayList(round.getHeats().values());
                if (heats.isEmpty()) {
                    String var46 = String.valueOf(ChatColor.RED);
                    player.sendMessage(
                        var46 +
                            "✗ Nenhum heat criado na rodada R" +
                            round.getRoundNumber() +
                            "!"
                    );
                    var46 = String.valueOf(ChatColor.GRAY);
                    player.sendMessage(
                        var46 +
                            "Use /heat create R" +
                            round.getRoundNumber() +
                            " para criar heats."
                    );
                } else {
                    for (Heats heat : heats) {
                        if (
                            heat.getHeatState() != HeatState.SETUP &&
                            heat.getHeatState() != HeatState.LOADED &&
                            heat.getHeatState() != HeatState.IDLE
                        ) {
                            String var47 = String.valueOf(ChatColor.RED);
                            player.sendMessage(
                                var47 +
                                    "✗ Não é possível preencher o heat " +
                                    heat.getName() +
                                    " no estado " +
                                    heat.getHeatState().name() +
                                    "."
                            );
                            return;
                        }
                    }

                    int totalCapacity = heats
                        .stream()
                        .mapToInt(Heats::getMaxDrivers)
                        .sum();
                    List<UUID> playersToAdd = new ArrayList();
                    List<UUID> excludedPlayers = new ArrayList();
                    int subscriberCount = event.getSubscriberCount();
                    int reserveCount = event.getReserveCount();
                    switch (groupMode.toLowerCase()) {
                        case "all":
                            playersToAdd.addAll(
                                event.getSubscribers().keySet()
                            );
                            int remainingSlots =
                                totalCapacity - subscriberCount;
                            if (remainingSlots > 0 && reserveCount > 0) {
                                List<UUID> reserves = new ArrayList(
                                    event.getReserves().keySet()
                                );
                                if (sortMode.equalsIgnoreCase("sorted")) {
                                    reserves = this.sortPlayersByRanking(
                                        reserves,
                                        event.getTrackNameWS()
                                    );
                                } else {
                                    Collections.shuffle(reserves);
                                }

                                int reservesToAdd = Math.min(
                                    remainingSlots,
                                    reserveCount
                                );

                                for (int i = 0; i < reservesToAdd; ++i) {
                                    playersToAdd.add((UUID) reserves.get(i));
                                }

                                for (
                                    int i = reservesToAdd;
                                    i < reserves.size();
                                    ++i
                                ) {
                                    excludedPlayers.add((UUID) reserves.get(i));
                                }
                            } else {
                                excludedPlayers.addAll(
                                    event.getReserves().keySet()
                                );
                            }
                            break;
                        case "signed":
                            playersToAdd.addAll(
                                event.getSubscribers().keySet()
                            );
                            break;
                        case "reserves":
                            playersToAdd.addAll(event.getReserves().keySet());
                    }

                    if (sortMode.equalsIgnoreCase("sorted")) {
                        playersToAdd = this.sortPlayersByRanking(
                            playersToAdd,
                            event.getTrackNameWS()
                        );
                    } else {
                        Collections.shuffle(playersToAdd);
                    }

                    int addedCount = 0;
                    Queue<UUID> playerQueue = new LinkedList(playersToAdd);

                    for (Heats heat : heats) {
                        int heatCapacity = heat.getMaxDrivers();
                        int currentDrivers = heat.getDriverCount();
                        int slotsAvailable = heatCapacity - currentDrivers;
                        String var10001 = String.valueOf(ChatColor.YELLOW);
                        player.sendMessage(
                            var10001 + "⚙ Preenchendo " + heat.getName() + "..."
                        );

                        for (
                            int i = 0;
                            i < slotsAvailable && !playerQueue.isEmpty();
                            ++i
                        ) {
                            UUID playerUUID = (UUID) playerQueue.poll();
                            Player targetPlayer =
                                this.plugin.getServer().getPlayer(playerUUID);
                            String playerName =
                                targetPlayer != null
                                    ? targetPlayer.getName()
                                    : this.plugin.getServer()
                                          .getOfflinePlayer(playerUUID)
                                          .getName();
                            HeatDriverCommandService.DriverMutationResult mutation =
                                this.heatDriverService.addDriverSync(
                                    heat,
                                    playerUUID,
                                    playerName,
                                    null
                                );
                            if (
                                mutation.getStatus() ==
                                HeatDriverCommandService.DriverMutationStatus.SUCCESS
                            ) {
                                ++addedCount;
                                if (
                                    targetPlayer != null &&
                                    targetPlayer.isOnline()
                                ) {
                                    heat.handleLateJoin(targetPlayer);
                                }

                                player.sendMessage(
                                    String.valueOf(ChatColor.GREEN) +
                                        "  ✓ " +
                                        playerName +
                                        " → P" +
                                        mutation.getFinalPosition()
                                );
                            } else {
                                var10001 = String.valueOf(ChatColor.RED);
                                player.sendMessage(
                                    var10001 +
                                        "  ✗ Falha ao adicionar " +
                                        playerName +
                                        " (" +
                                        this.describeFillFailure(
                                            mutation.getStatus()
                                        ) +
                                        ")"
                                );
                            }
                        }
                    }

                    if (!playerQueue.isEmpty() || !excludedPlayers.isEmpty()) {
                        player.sendMessage("");
                        player.sendMessage(
                            String.valueOf(ChatColor.YELLOW) +
                                "⚠ Jogadores que ficaram de fora:"
                        );

                        while (!playerQueue.isEmpty()) {
                            UUID uuid = (UUID) playerQueue.poll();
                            Player p = this.plugin.getServer().getPlayer(uuid);
                            String name =
                                p != null
                                    ? p.getName()
                                    : this.plugin.getServer()
                                          .getOfflinePlayer(uuid)
                                          .getName();
                            String var40 = String.valueOf(ChatColor.GRAY);
                            player.sendMessage(
                                var40 + "  - " + name + " (sem vaga)"
                            );
                        }

                        for (UUID uuid : excludedPlayers) {
                            Player p = this.plugin.getServer().getPlayer(uuid);
                            String name =
                                p != null
                                    ? p.getName()
                                    : this.plugin.getServer()
                                          .getOfflinePlayer(uuid)
                                          .getName();
                            String var41 = String.valueOf(ChatColor.GRAY);
                            player.sendMessage(
                                var41 + "  - " + name + " (reserva)"
                            );
                        }
                    }

                    player.sendMessage("");
                    String var42 = String.valueOf(ChatColor.GREEN);
                    player.sendMessage(
                        var42 + "✓ Heats preenchidos com sucesso!"
                    );
                    var42 = String.valueOf(ChatColor.GRAY);
                    player.sendMessage(
                        var42 +
                            "Modo: " +
                            String.valueOf(ChatColor.WHITE) +
                            (sortMode.equalsIgnoreCase("sorted")
                                ? "Ordenado por ranking"
                                : "Aleatório")
                    );
                    var42 = String.valueOf(ChatColor.GRAY);
                    player.sendMessage(
                        var42 +
                            "Pilotos adicionados: " +
                            String.valueOf(ChatColor.WHITE) +
                            addedCount
                    );
                    var42 = String.valueOf(ChatColor.GRAY);
                    player.sendMessage(
                        var42 +
                            "Capacidade total: " +
                            String.valueOf(ChatColor.WHITE) +
                            totalCapacity +
                            " vagas em " +
                            heats.size() +
                            " heat(s)"
                    );
                }
            }
        }
    }

    private List<UUID> sortPlayersByRanking(
        List<UUID> players,
        String trackName
    ) {
        if (trackName != null && !trackName.isEmpty()) {
            Map<UUID, Long> playerTimes = new HashMap();

            for (UUID uuid : players) {
                String sql =
                    "SELECT bestTime FROM fr_player_times WHERE uuid = ? AND LOWER(trackNameWS) = LOWER(?) AND finished = TRUE ORDER BY bestTime ASC LIMIT 1";

                try (
                    Connection conn = this.database.getOrConnect();
                    PreparedStatement stmt = conn.prepareStatement(sql);
                ) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, trackName);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            long bestTime = rs.getLong("bestTime");
                            if (bestTime > 0L) {
                                playerTimes.put(uuid, bestTime);
                            }
                        }
                    }
                } catch (SQLException e) {
                    DebugManager var10000 = this.plugin.getDebugManager();
                    String var10001 = String.valueOf(uuid);
                    var10000.logDatabaseOperation(
                        "Erro ao buscar tempo de " +
                            var10001 +
                            " na pista " +
                            trackName +
                            ": " +
                            e.getMessage()
                    );
                }
            }

            List<UUID> withTimes = new ArrayList();
            List<UUID> withoutTimes = new ArrayList();

            for (UUID uuid : players) {
                if (playerTimes.containsKey(uuid)) {
                    withTimes.add(uuid);
                } else {
                    withoutTimes.add(uuid);
                }
            }

            Objects.requireNonNull(playerTimes);
            withTimes.sort(Comparator.comparingLong(playerTimes::get));
            List<UUID> result = new ArrayList();
            result.addAll(withTimes);
            result.addAll(withoutTimes);
            return result;
        } else {
            return new ArrayList(players);
        }
    }

    private String describeFillFailure(
        HeatDriverCommandService.DriverMutationStatus status
    ) {
        return switch (status) {
            case ALREADY_IN_HEAT -> "já está no heat";
            case ALREADY_IN_ROUND -> "já está em outro heat do round";
            case HEAT_FULL -> "heat lotado";
            case INVALID_HEAT_STATE -> "estado do heat inválido";
            case CONFLICT -> "conflito de edição concorrente";
            case PERSISTENCE_ERROR -> "erro de persistência";
            case SYNC_ERROR -> "erro de sincronização";
            case INVALID_POSITION -> "posição inválida";
            default -> "falha desconhecida";
        };
    }
}
