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
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.ApiUtilities;
import dev.EfraGroup.formulaRacing.Utils.ClickableMessageUtil;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@CommandAlias("heat")
public class HeatCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final RaceEventManager eventManager;
    private final DatabaseManager database;
    private final HeatDriverCommandService heatDriverService;

    public HeatCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.eventManager = plugin.getRaceEventManager();
        this.database = plugin.getDatabaseManager();
        this.heatDriverService = new HeatDriverCommandService(plugin);
    }

    @Default
    @Description("Mostra info do heat atual")
    public void onDefault(Player player) {
        Heats heat = this.resolveHeat(player, (Heats) null);
        if (heat != null) {
            this.onInfo(player, heat);
        } else {
            Events event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
            if (event != null) {
                Rounds round = event
                    .getSchedule()
                    .getCurrentRound()
                    .orElse(null);
                if (round != null) {
                    String var10001 = String.valueOf(ChatColor.YELLOW);
                    player.sendMessage(
                        var10001 +
                            "Nenhum heat ativo no round " +
                            round.getDisplayName()
                    );
                } else {
                    String var5 = String.valueOf(ChatColor.RED);
                    player.sendMessage(
                        var5 +
                            "✗ Nenhuma rodada ativa no evento " +
                            event.getDisplayName()
                    );
                }
            } else {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Nenhum evento selecionado!"
                );
            }
        }
    }

    private Heats resolveHeat(Player player, Heats argumentHeat) {
        if (argumentHeat != null) {
            return argumentHeat;
        } else {
            Optional<Integer> selectedId = this.database.getPlayerSelectedHeat(
                player.getUniqueId()
            );
            if (selectedId.isPresent()) {
                Optional<Heats> heat = this.eventManager.getHeat(
                    selectedId.get()
                );
                if (heat.isPresent()) {
                    return heat.get();
                }
            }

            Events event = this.database.getPlayerSelectedEvent(
                player.getUniqueId()
            ).orElse(null);
            if (event != null) {
                Rounds round = event
                    .getSchedule()
                    .getCurrentRound()
                    .orElse(null);
                if (round != null) {
                    return round.getCurrentHeat().orElse(null);
                }
            }

            return null;
        }
    }

    private Integer resolveHeatIdFromCode(String code, Events event) {
        if (event == null) {
            return null;
        } else if (!code.matches("(?i)R\\d+[QFEHP]\\d+")) {
            return null;
        } else {
            try {
                String upper = code.toUpperCase();
                String separator = upper.contains("Q")
                    ? "Q"
                    : (upper.contains("F")
                        ? "F"
                        : (upper.contains("E")
                            ? "E"
                            : (upper.contains("H") ? "H" : "P")));
                String[] parts = upper.split(separator);
                int roundIdx = Integer.parseInt(parts[0].substring(1)) - 1;
                int heatNum = Integer.parseInt(parts[1]);
                List<Rounds> roundsList = event
                    .getEventSchedule()
                    .getRoundsList();
                if (roundIdx >= 0 && roundIdx < roundsList.size()) {
                    Rounds round = (Rounds) roundsList.get(roundIdx);
                    Heats heat = (Heats) round.getHeats().get(heatNum);
                    if (heat != null) {
                        return heat.getId();
                    }
                }
            } catch (Exception var11) {}

            return null;
        }
    }

    @Subcommand("select")
    @CommandCompletion("@heat_codes")
    @CommandPermission("formularacing.event.admin")
    @Description("Seleciona um heat específico para focar os comandos")
    public void onSelect(Player player, String heatCodeOrId) {
        if (
            !heatCodeOrId.equalsIgnoreCase("off") &&
            !heatCodeOrId.equalsIgnoreCase("none")
        ) {
            Integer heatId;
            try {
                heatId = Integer.parseInt(heatCodeOrId);
            } catch (NumberFormatException var6) {
                Events event = database
                    .getPlayerSelectedEvent(player.getUniqueId())
                    .orElse(null);
                if (event == null) {
                    player.sendMessage(
                        String.valueOf(ChatColor.RED) +
                            "✗ Selecione um evento primeiro para usar códigos de heat (ex: R1F1)."
                    );
                    return;
                }

                heatId = this.resolveHeatIdFromCode(heatCodeOrId, event);
            }

            if (heatId == null) {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Heat não encontrado ou código inválido."
                );
            } else {
                Optional<Heats> heat = this.eventManager.getHeat(heatId);
                if (heat.isEmpty()) {
                    String var7 = String.valueOf(ChatColor.RED);
                    player.sendMessage(
                        var7 + "✗ Heat com ID " + heatId + " não encontrado."
                    );
                } else {
                    this.database.setSelectedHeat(player.getUniqueId(), heatId);
                    String var10001 = String.valueOf(ChatColor.GREEN);
                    player.sendMessage(
                        var10001 +
                            "✓ Heat selecionado: " +
                            String.valueOf(ChatColor.WHITE) +
                            ((Heats) heat.get()).getName() +
                            String.valueOf(ChatColor.GRAY) +
                            " (ID: " +
                            heatId +
                            ")"
                    );
                }
            }
        } else {
            this.database.setSelectedHeat(player.getUniqueId(), (Integer) null);
            player.sendMessage(
                String.valueOf(ChatColor.YELLOW) +
                    "Seleção de heat desativada. Comandos usarão o heat ativo."
            );
        }
    }

    @Subcommand("create|new")
    @CommandCompletion("@round")
    @CommandPermission("formularacing.event.admin")
    @Description("Cria um novo heat no round")
    public void onCreate(
        Player player,
        @co.aikar.commands.annotation.Optional String roundOrHeatRef
    ) {
        Rounds round = this.resolveRoundReference(player, roundOrHeatRef);

        if (round == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum round selecionado ou ativo!"
            );
            player.sendMessage(
                String.valueOf(ChatColor.GRAY) +
                    "Use /heat create R1 ou /heat create R1Q1 (ou selecione um evento com round atual)."
            );
            return;
        }

        int nextNumber = round.getHeats().size() + 1;
        this.eventManager.createHeat(round, nextNumber).thenAccept(heat -> {
            if (heat != null) {
                String var10001 = String.valueOf(ChatColor.GREEN);
                player.sendMessage(
                    var10001 +
                        "✓ Heat " +
                        nextNumber +
                        " criado com sucesso no " +
                        round.getDisplayName() +
                        "!"
                );
            } else {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Erro ao criar heat no banco de dados."
                );
            }
        });
    }

    private Rounds resolveRoundReference(Player player, String roundOrHeatRef) {
        Events selectedEvent = this.database.getPlayerSelectedEvent(
            player.getUniqueId()
        ).orElse(null);

        if (roundOrHeatRef == null || roundOrHeatRef.isBlank()) {
            if (selectedEvent != null) {
                return selectedEvent
                    .getSchedule()
                    .getCurrentRound()
                    .orElse(null);
            }
            return null;
        }

        String ref = roundOrHeatRef.toUpperCase();

        if (ref.matches("R\\d+[QFEHP]\\d+")) {
            int qPos = ref.indexOf('Q');
            int hPos = ref.indexOf('H');
            int fPos = ref.indexOf('F');
            int ePos = ref.indexOf('E');
            int pPos = ref.indexOf('P');
            int splitPos = qPos >= 0 ? qPos : (hPos >= 0 ? hPos : (fPos >= 0 ? fPos : (ePos >= 0 ? ePos : pPos)));
            if (splitPos > 1) {
                ref = ref.substring(0, splitPos);
            }
        }

        if (!ref.matches("R\\d+")) {
            return null;
        }

        int roundIdx;
        try {
            roundIdx = Integer.parseInt(ref.substring(1));
        } catch (NumberFormatException ex) {
            return null;
        }

        if (selectedEvent != null) {
            return selectedEvent.getSchedule().getRound(roundIdx).orElse(null);
        }

        return this.eventManager.getAllEvents()
            .stream()
            .filter(Events::isActive)
            .map(e -> e.getSchedule().getRound(roundIdx).orElse(null))
            .filter(r -> r != null)
            .findFirst()
            .orElse(null);
    }

    @Subcommand("info|view")
    @CommandCompletion("@heat")
    @Description("Mostra informações detalhadas de um heat")
    public void onInfo(Player player, Heats heat) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo! Use /heat select <id|code>"
            );
        } else {
            boolean isAdmin = player.hasPermission("formularacing.event.admin");
            player.sendMessage("");
            TextComponent header = new TextComponent("");
            header.addExtra(
                ClickableMessageUtil.getRefreshButton(
                    "/heat info " + heat.getId(),
                    "Atualizar"
                )
            );
            header.addExtra(new TextComponent(" "));
            TextComponent title = new TextComponent(
                heat.getName().toUpperCase()
            );
            title.setColor(ChatColor.GOLD);
            title.setBold(true);
            header.addExtra(title);
            String var10003 = String.valueOf(ChatColor.GRAY);
            header.addExtra(
                new TextComponent(
                    var10003 + " (" + heat.getHeatState().name() + ")"
                )
            );
            header.addExtra(new TextComponent(" "));
            header.addExtra(
                ClickableMessageUtil.getButton(
                    "Ver Round",
                    ChatColor.BLUE,
                    "/round info " + heat.getRound().getId(),
                    "Voltar ao Round",
                    Action.RUN_COMMAND
                )
            );
            player.spigot().sendMessage(header);
            if (isAdmin) {
                TextComponent controls = new TextComponent("  ");
                controls.addExtra(
                    ClickableMessageUtil.getButton(
                        "CARREGAR",
                        ChatColor.YELLOW,
                        "/heat load " + heat.getName(),
                        "Carregar Grid e Pista",
                        Action.RUN_COMMAND
                    )
                );
                controls.addExtra(new TextComponent(" "));
                controls.addExtra(
                    ClickableMessageUtil.getButton(
                        "INICIAR",
                        ChatColor.GREEN,
                        "/heat start " + heat.getName(),
                        "Iniciar Contagem",
                        Action.RUN_COMMAND
                    )
                );
                controls.addExtra(new TextComponent(" "));
                controls.addExtra(
                    ClickableMessageUtil.getButton(
                        "FINALIZAR",
                        ChatColor.RED,
                        "/heat finish " + heat.getName(),
                        "Forçar Finalização",
                        Action.RUN_COMMAND
                    )
                );
                controls.addExtra(new TextComponent(" "));
                controls.addExtra(
                    ClickableMessageUtil.getButton(
                        "RESET",
                        ChatColor.GRAY,
                        "/heat reset " + heat.getName(),
                        "Resetar Heat",
                        Action.RUN_COMMAND
                    )
                );
                player.spigot().sendMessage(controls);
                player.sendMessage("");
            }

            TextComponent trackRow = new TextComponent(
                String.valueOf(ChatColor.YELLOW) + "  Pista: "
            );
            if (heat.getTrackNameWS() != null) {
                var10003 = String.valueOf(ChatColor.WHITE);
                trackRow.addExtra(
                    new TextComponent(var10003 + heat.getTrackNameWS())
                );
            } else {
                trackRow.addExtra(
                    new TextComponent(
                        String.valueOf(ChatColor.RED) + "Não definida"
                    )
                );
            }

            player.spigot().sendMessage(trackRow);
            TextComponent configRow = new TextComponent("  ");
            var10003 = String.valueOf(heat.getTotalLaps());
            String var10004 = heat.getName();
            configRow.addExtra(
                this.formattedSetting(
                    "Voltas",
                    var10003,
                    "/heat set laps " + var10004 + " ",
                    isAdmin
                )
            );
            var10003 = String.valueOf(ChatColor.DARK_GRAY);
            configRow.addExtra(new TextComponent(var10003 + " | "));
            var10003 = String.valueOf(heat.getTotalPits());
            var10004 = heat.getName();
            configRow.addExtra(
                this.formattedSetting(
                    "Pits",
                    var10003,
                    "/heat set pits " + var10004 + " ",
                    isAdmin
                )
            );
            var10003 = String.valueOf(ChatColor.DARK_GRAY);
            configRow.addExtra(new TextComponent(var10003 + " | "));
            Integer var30 = heat.getTimeLimit();
            String var31 = var30 + "s";
            var10004 = heat.getName();
            configRow.addExtra(
                this.formattedSetting(
                    "Tempo",
                    var31,
                    "/heat set timelimit " + var10004 + " ",
                    isAdmin
                )
            );
            var31 = String.valueOf(ChatColor.DARK_GRAY);
            configRow.addExtra(new TextComponent(var31 + " | "));
            Integer var33 = heat.getStartDelay();
            String var34 = var33 + "s";
            var10004 = heat.getName();
            configRow.addExtra(
                this.formattedSetting(
                    "Delay",
                    var34,
                    "/heat set startdelay " + var10004 + " ",
                    isAdmin
                )
            );
            player.spigot().sendMessage(configRow);
            TextComponent configRow2 = new TextComponent("  ");
            var34 = heat.getCollisionMode().name();
            var10004 = heat.getName();
            configRow2.addExtra(
                this.formattedSetting(
                    "Colisão",
                    var34,
                    "/heat set collision " + var10004 + " ",
                    isAdmin
                )
            );
            var34 = String.valueOf(ChatColor.DARK_GRAY);
            configRow2.addExtra(new TextComponent(var34 + " | "));
            var34 = String.valueOf(heat.getMaxDrivers());
            var10004 = heat.getName();
            configRow2.addExtra(
                this.formattedSetting(
                    "Max Pilotos",
                    var34,
                    "/heat set maxdrivers " + var10004 + " ",
                    isAdmin
                )
            );
            TextComponent configRow3 = new TextComponent("  ");
            var34 = heat.isDrsEnabled() ? "ON" : "OFF";
            var10004 = heat.getName();
            configRow3.addExtra(
                this.formattedSetting(
                    "DRS",
                    var34,
                    "/heat set drs " + var10004 + " ",
                    isAdmin
                )
            );
            var34 = String.valueOf(ChatColor.DARK_GRAY);
            configRow3.addExtra(new TextComponent(var34 + " | "));
            var34 = heat.isPushtopass() ? "ON" : "OFF";
            var10004 = heat.getName();
            configRow3.addExtra(
                this.formattedSetting(
                    "P2P",
                    var34,
                    "/heat set pushtopass " + var10004 + " ",
                    isAdmin
                )
            );
            var34 = String.valueOf(ChatColor.DARK_GRAY);
            configRow3.addExtra(new TextComponent(var34 + " | "));
            int var42 = heat.getDeltaGhosting();
            String var43 = var42 + "s";
            var10004 = heat.getName();
            configRow3.addExtra(
                this.formattedSetting(
                    "Ghost",
                    var43,
                    "/heat set ghosting " + var10004 + " ",
                    isAdmin
                )
            );
            var43 = String.valueOf(ChatColor.DARK_GRAY);
            configRow3.addExtra(new TextComponent(var43 + " | "));
            var43 = heat.getrealistc() ? "ON" : "OFF";
            var10004 = heat.getName();
            configRow3.addExtra(
                this.formattedSetting(
                    "Realista",
                    var43,
                    "/heat set realistic " + var10004 + " ",
                    isAdmin
                )
            );
            var43 = String.valueOf(ChatColor.DARK_GRAY);
            configRow3.addExtra(new TextComponent(var43 + " | "));
            var43 = heat.isErsEnabled() ? "ON" : "OFF";
            configRow3.addExtra(
                this.formattedSetting(
                    "ERS",
                    var43,
                    "/heat set ers " + heat.getName() + " ",
                    isAdmin
                )
            );
            TextComponent configRow4 = new TextComponent("  ");
            boolean hasP2P = heat.isPushtopass();
            boolean hasDRS = heat.isDrsEnabled();
            String var50;
            if (hasP2P) {
                double var46 = heat.getpushtopasspower();
                String var47 = var46 + "x";
                var10004 = heat.getName();
                configRow4.addExtra(
                    this.formattedSetting(
                        "P2P Pwr",
                        var47,
                        "/heat set p2ppower " + var10004 + " ",
                        isAdmin
                    )
                );
            }
            if (hasDRS) {
                var34 = String.valueOf(ChatColor.DARK_GRAY);
                configRow4.addExtra(new TextComponent(var34 + " | "));
                double var49 = heat.getDrsdownpower();
                var50 = var49 + "x";
                var10004 = heat.getName();
                configRow4.addExtra(
                    this.formattedSetting(
                        "DRS Pwr",
                        var50,
                        "/heat set drspower " + var10004 + " ",
                        isAdmin
                    )
                );
            }
            if (hasP2P || hasDRS) {
                var50 = String.valueOf(ChatColor.DARK_GRAY);
                configRow4.addExtra(new TextComponent(var50 + " | "));
            }
            var50 = heat.getDriverSwap() ? "ON" : "OFF";
            var10004 = heat.getName();
            configRow4.addExtra(
                this.formattedSetting(
                    "Swap",
                    var50,
                    "/heat set swap " + var10004 + " ",
                    isAdmin
                )
            );
            var50 = String.valueOf(ChatColor.DARK_GRAY);
            configRow4.addExtra(new TextComponent(var50 + " | "));
            configRow4.addExtra(
                this.formattedSetting(
                    "Grid Rev",
                    heat.getreversegrid() ? "ON" : "OFF",
                    "/heat set reversegrid " + heat.getName() + " ",
                    isAdmin
                )
            );
            player.spigot().sendMessage(configRow2);
            player.spigot().sendMessage(configRow3);
            player.spigot().sendMessage(configRow4);
            if (heat.getRound() != null && heat.getRound().getType() == RoundType.ELIMINATION) {
                TextComponent elimRow = new TextComponent("  ");
                String elimIntervalStr = heat.getEliminationIntervalSeconds() + "s";
                elimRow.addExtra(
                    this.formattedSetting(
                        "Elim Intervalo",
                        elimIntervalStr,
                        "/heat set eliminterval " + heat.getName() + " ",
                        isAdmin
                    )
                );
                elimRow.addExtra(new TextComponent(String.valueOf(ChatColor.DARK_GRAY) + " | "));
                String minDriversStr = String.valueOf(heat.getMinimumDrivers());
                elimRow.addExtra(
                    this.formattedSetting(
                        "Min Pilotos",
                        minDriversStr,
                        "/heat set mindrivers " + heat.getName() + " ",
                        isAdmin
                    )
                );
                player.spigot().sendMessage(elimRow);
            }
            player.sendMessage("");
            List<Driver> displayDrivers;
            String tableTitle;
            if (heat.getHeatState() == HeatState.FINISHED) {
                String var10000 = String.valueOf(ChatColor.GREEN);
                tableTitle =
                    var10000 +
                    String.valueOf(ChatColor.BOLD) +
                    "  \ud83c\udfc1 RESULTADO FINAL:";
                displayDrivers = new ArrayList(heat.getDrivers().values());
                displayDrivers.sort(
                    Comparator.comparingInt(Driver::getPosition)
                );
            } else if (heat.getHeatState() == HeatState.RACING) {
                String var23 = String.valueOf(ChatColor.AQUA);
                tableTitle =
                    var23 +
                    String.valueOf(ChatColor.BOLD) +
                    "  \ud83d\udcca CLASSIFICAÇÃO AO VIVO:";
                heat.updateLivePositions();
                displayDrivers = heat.getLivePositions();
            } else {
                String var24 = String.valueOf(ChatColor.YELLOW);
                tableTitle =
                    var24 +
                    String.valueOf(ChatColor.BOLD) +
                    "  \ud83d\udccb GRID DE LARGADA:";
                displayDrivers = new ArrayList(heat.getDrivers().values());
                displayDrivers.sort(
                    Comparator.comparingInt(Driver::getStartPosition)
                );
            }

            player.sendMessage(tableTitle);
            if (displayDrivers.isEmpty()) {
                player.sendMessage(
                    String.valueOf(ChatColor.GRAY) + "    (Nenhum piloto)"
                );
            } else {
                for (Driver driver : displayDrivers) {
                    String playerName = Bukkit.getOfflinePlayer(
                        driver.getUuid()
                    ).getName();
                    if (playerName == null) {
                        playerName = "Unknown";
                    }

                    int pos = driver.getPosition();
                    if (
                        heat.getHeatState() != HeatState.FINISHED &&
                        heat.getHeatState() != HeatState.RACING
                    ) {
                        pos = driver.getStartPosition();
                    }

                    TextComponent line = new TextComponent("  ");
                    TextComponent posComp = new TextComponent(
                        String.format("#%02d ", pos)
                    );
                    posComp.setColor(ChatColor.GRAY);
                    line.addExtra(posComp);
                    TextComponent nameComp = new TextComponent(
                        String.format("%-14s", playerName)
                    );
                    nameComp.setColor(
                        driver.isDnf() ? ChatColor.RED : ChatColor.WHITE
                    );
                    line.addExtra(nameComp);
                    if (heat.getHeatState() == HeatState.FINISHED) {
                        if (driver.isDnf()) {
                            line.addExtra(
                                new TextComponent(
                                    String.valueOf(ChatColor.RED) + "DNF "
                                )
                            );
                        } else {
                            var50 = String.valueOf(ChatColor.WHITE);
                            line.addExtra(
                                new TextComponent(
                                    var50 +
                                        ApiUtilities.formatRaceTime(
                                            driver.getTotalTime()
                                        ) +
                                        " "
                                )
                            );
                        }
                    } else if (heat.getHeatState() == HeatState.RACING) {
                        var50 = String.valueOf(ChatColor.GRAY);
                        line.addExtra(
                            new TextComponent(
                                var50 + "V" + (driver.getLapCount() + 1) + " "
                            )
                        );
                    }

                    if (driver.getFastestLap() != null) {
                        var50 = String.valueOf(ChatColor.DARK_GRAY);
                        line.addExtra(
                            new TextComponent(
                                var50 +
                                    "[" +
                                    ApiUtilities.formatRaceTime(
                                        driver.getFastestLap().getLapTime()
                                    ) +
                                    "] "
                            )
                        );
                    }

                    if (isAdmin) {
                        line.addExtra(
                            ClickableMessageUtil.getButton(
                                "✖",
                                ChatColor.RED,
                                "/heat removedriver " +
                                    heat.getName() +
                                    " " +
                                    playerName,
                                "Remover piloto",
                                Action.SUGGEST_COMMAND
                            )
                        );
                        line.addExtra(new TextComponent(" "));
                        line.addExtra(
                            ClickableMessageUtil.getButton(
                                "↕",
                                ChatColor.YELLOW,
                                "/heat set driverposition " +
                                    heat.getName() +
                                    " " +
                                    playerName +
                                    " ",
                                "Mover grid pos",
                                Action.SUGGEST_COMMAND
                            )
                        );
                    }

                    player.spigot().sendMessage(line);
                }
            }

            if (isAdmin && heat.getHeatState() == HeatState.SETUP) {
                player.sendMessage("");
                TextComponent addBtns = new TextComponent("  Adicionar: ");
                addBtns.addExtra(
                    ClickableMessageUtil.getButton(
                        "Piloto",
                        ChatColor.GREEN,
                        "/heat adddriver " + heat.getName() + " ",
                        "Adicionar piloto",
                        Action.SUGGEST_COMMAND
                    )
                );
                addBtns.addExtra(new TextComponent(" "));
                addBtns.addExtra(
                    ClickableMessageUtil.getButton(
                        "Equipe",
                        ChatColor.AQUA,
                        "/heat addteam " + heat.getName() + " ",
                        "Adicionar equipe",
                        Action.SUGGEST_COMMAND
                    )
                );
                player.spigot().sendMessage(addBtns);
            }

            player.sendMessage("");
            String var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(
                var10001 +
                    String.valueOf(ChatColor.BOLD) +
                    "═══════════════════════════════"
            );
        }
    }

    private TextComponent formattedSetting(
        String label,
        String value,
        String editCommand,
        boolean isAdmin
    ) {
        String var10002 = String.valueOf(ChatColor.YELLOW);
        TextComponent tc = new TextComponent(var10002 + label + ": ");
        if (isAdmin) {
            tc.addExtra(
                ClickableMessageUtil.getEditButton(
                    value,
                    editCommand,
                    "Clique para editar " + label
                )
            );
        } else {
            String var10003 = String.valueOf(ChatColor.WHITE);
            tc.addExtra(new TextComponent(var10003 + value));
        }

        return tc;
    }

    @Subcommand("set realistic")
    @CommandCompletion("@heats true|false")
    @CommandPermission("formularacing.admin")
    public void onSetRealistic(Player player, Heats heat, boolean val) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.setrealistc(val);
        player.sendMessage(
            "§a[Config] Modo Realista " +
                (val ? "§2ATIVADO" : "§cDESATIVADO") +
                " §apara o heat §f" +
                heat.getId()
        );
    }

    @Subcommand("set reversegridenabled")
    @CommandCompletion("@heat true|false")
    @CommandPermission("formularacing.admin")
    public void onSetReverseGridEnabled(
        Player player,
        Heats heat,
        boolean val
    ) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.setreversegrid(val);
        player.sendMessage(
            "§a[Config] Grid Invertido " +
                (val ? "§2ATIVADO" : "§cDESATIVADO") +
                " §apara o heat §f" +
                heat.getId()
        );
    }

    @Subcommand("set swap")
    @CommandCompletion("@heats true|false")
    @CommandPermission("formularacing.admin")
    public void onSetDriverSwap(Player player, Heats heat, boolean val) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.setDriverSwap(val);
        player.sendMessage(
            "§a[Config] Driver Swap " +
                (val ? "§2ATIVADO" : "§cDESATIVADO") +
                " §apara o heat §f" +
                heat.getId()
        );
    }

    @Subcommand("set p2ppower")
    @CommandCompletion("@heats")
    @CommandPermission("formularacing.admin")
    public void onSetP2PPower(Player player, Heats heat, double power) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.setpushtopasspower(power);
        player.sendMessage(
            "§a[Config] Poder do P2P definido para: §f" +
                power +
                "x §ano heat §f" +
                heat.getId()
        );
    }

    @Subcommand("set drspower")
    @CommandCompletion("@heats")
    @CommandPermission("formularacing.admin")
    public void onSetDRSPower(Player player, Heats heat, double power) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.setDrsdownpower(power);
        player.sendMessage(
            "§a[Config] Poder do DRS definido para: §f" +
                power +
                "x §ano heat §f" +
                heat.getId()
        );
    }

    @Subcommand("set drsdowntime")
    @CommandCompletion("@heats")
    @CommandPermission("formularacing.admin")
    public void onSetDRSTime(Player player, Heats heat, double seconds) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.setDrsdowntime(seconds);
        player.sendMessage(
            "§a[Config] Tempo de DRS definido para: §f" +
                seconds +
                "s §ano heat §f" +
                heat.getId()
        );
    }

    @Subcommand("set ers")
    @CommandCompletion("@heats")
    @CommandPermission("formularacing.admin")
    public void onSetErs(Player player, Heats heat, boolean seconds) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                    ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.setErsEnabled(seconds);
        player.sendMessage(
                "§a[Config] Ers definido como §f" +
                        seconds +
                        "s §ano heat §f" +
                        heat.getId()
        );
    }

    @Subcommand("set deltaghosting")
    @CommandCompletion("@heat <seconds>")
    @CommandPermission("formularacing.admin")
    public void onSetGhosting(Player player, Heats heat, int seconds) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.setDeltaghosting(seconds);
        player.sendMessage(
            "§a[Config] Delta Ghosting definido para: §f" +
                seconds +
                "s §ano heat §f" +
                heat.getId()
        );
    }

    @Subcommand("load")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.event.admin")
    @Description("Carrega um heat (prepara para largada)")
    public void onLoad(Player player, Heats heat) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else {
            heat.loadHeat();
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(
                var10001 + "✓ Heat " + heat.getName() + " carregado!"
            );
            this.displaySortedDrivers(player, heat);
        }
    }

    private void displaySortedDrivers(Player player, Heats heat) {
        Rounds currentRound = heat.getRound();
        if (currentRound != null) {
            Events event = currentRound.getEvent();
            if (event != null) {
                RoundType currentType = currentRound.getType();
                RoundType targetType;
                if (currentType == RoundType.QUALIFICATION) {
                    targetType = RoundType.PRACTICE;
                } else if (currentType == RoundType.FINAL) {
                    targetType = RoundType.QUALIFICATION;
                } else {
                    targetType = null;
                }

                if (targetType != null) {
                    Map<UUID, Long> bestLaps = new HashMap();
                    List<Rounds> targetRounds = event
                        .getSchedule()
                        .getRoundsList()
                        .stream()
                        .filter(r -> r.getType() == targetType)
                        .toList();

                    for (UUID driverUUID : heat.getDrivers().keySet()) {
                        long bestTime = Long.MAX_VALUE;

                        for (Rounds round : targetRounds) {
                            for (Heats h : round.getHeats().values()) {
                                Driver d = h.getDriver(driverUUID);
                                if (d != null && d.getFastestLap() != null) {
                                    long lapTime = d
                                        .getFastestLap()
                                        .getLapTime();
                                    if (lapTime < bestTime) {
                                        bestTime = lapTime;
                                    }
                                }
                            }
                        }

                        if (bestTime != Long.MAX_VALUE) {
                            bestLaps.put(driverUUID, bestTime);
                        }
                    }

                    if (!bestLaps.isEmpty()) {
                        List<UUID> sortedDrivers = new ArrayList(
                            heat.getDrivers().keySet()
                        );
                        sortedDrivers.sort((u1, u2) -> {
                            long t1 = (Long) bestLaps.getOrDefault(
                                u1,
                                Long.MAX_VALUE
                            );
                            long t2 = (Long) bestLaps.getOrDefault(
                                u2,
                                Long.MAX_VALUE
                            );
                            return Long.compare(t1, t2);
                        });
                        player.sendMessage("");
                        String var10001 = String.valueOf(ChatColor.GOLD);
                        player.sendMessage(
                            var10001 + "═══════════════════════════════"
                        );
                        String sessionName =
                            targetType == RoundType.PRACTICE
                                ? "TREINO LIVRE"
                                : "QUALIFICATÓRIA";
                        var10001 = String.valueOf(ChatColor.YELLOW);
                        player.sendMessage(
                            var10001 +
                                "  ORDEM DE LARGADA (BASEADO EM " +
                                sessionName +
                                "):"
                        );
                        player.sendMessage("");
                        int pos = 1;

                        for (UUID uuid : sortedDrivers) {
                            String name = Bukkit.getOfflinePlayer(
                                uuid
                            ).getName();
                            Long time = (Long) bestLaps.get(uuid);
                            String timeStr =
                                time != null
                                    ? ApiUtilities.formatRaceTime(time)
                                    : "---";
                            var10001 = String.valueOf(ChatColor.GRAY);
                            player.sendMessage(
                                var10001 +
                                    "  " +
                                    pos +
                                    ". " +
                                    String.valueOf(ChatColor.WHITE) +
                                    name +
                                    String.valueOf(ChatColor.AQUA) +
                                    " - " +
                                    timeStr
                            );
                            ++pos;
                        }

                        player.sendMessage(
                            String.valueOf(ChatColor.GOLD) +
                                "═══════════════════════════════"
                        );
                    }
                }
            }
        }
    }

    @Subcommand("start")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.event.admin")
    @Description("Inicia a contagem regressiva do heat")
    public void onStart(
        Player player,
        Heats heat,
        @Default("5") Integer seconds
    ) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else if (
            this.plugin.getReadyCheckManager().isReadyCheckActive(heat.getId())
        ) {
            player.sendMessage(
                String.valueOf(ChatColor.YELLOW) +
                    "⚠ Há um Ready Check ativo para este heat!"
            );
            player.sendMessage(
                String.valueOf(ChatColor.GRAY) +
                    "Use /heat readycheck para ver quem ainda não confirmou."
            );
            ClickableMessageUtil.sendClickableLine(
                player,
                String.valueOf(ChatColor.GRAY) + "Deseja forçar o início? ",
                String.valueOf(ChatColor.RED) + "[FORÇAR INÍCIO]",
                "",
                "/heat start " + heat.getId() + " " + seconds + " force",
                "§cClique para ignorar o Ready Check e iniciar",
                false
            );
        } else {
            if (heat.startCountdown(seconds)) {
                player.sendMessage(
                    String.valueOf(ChatColor.GREEN) +
                        "✓ Contagem regressiva de " +
                        seconds +
                        "s do heat " +
                        heat.getName() +
                        " iniciada!"
                );
            } else {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Falha ao iniciar contagem do heat."
                );
            }
        }
    }

    @Subcommand("start")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.event.admin")
    @Description("Inicia a contagem regressiva do heat (forçando)")
    public void onStartForce(
        Player player,
        Heats heat,
        Integer seconds,
        String force
    ) {
        if (force.equalsIgnoreCase("force")) {
            heat = this.resolveHeat(player, heat);
            if (heat == null) {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Nenhum heat selecionado ou ativo!"
                );
            } else {
                if (heat.startCountdown(seconds)) {
                    this.plugin.getReadyCheckManager().stopReadyCheck(
                        heat.getId()
                    );
                    String var10001 = String.valueOf(ChatColor.GREEN);
                    player.sendMessage(
                        var10001 +
                            "✓ Contagem regressiva do heat " +
                            heat.getName() +
                            " iniciada (FORÇADO)!"
                    );
                } else {
                    player.sendMessage(
                        String.valueOf(ChatColor.RED) +
                            "✗ Falha ao iniciar contagem do heat."
                    );
                }
            }
        }
    }

    @Subcommand("readycheck end")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.event.admin")
    @Description("Finaliza o Ready Check do heat")
    public void onReadyCheckEnd(Player player, Heats heat) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else {
            this.plugin.getReadyCheckManager().stopReadyCheck(heat.getId());
            String var10001 = String.valueOf(ChatColor.YELLOW);
            player.sendMessage(
                var10001 +
                    "✓ Ready Check do heat " +
                    heat.getName() +
                    " finalizado."
            );
        }
    }

    @Subcommand("set timelimit")
    @CommandCompletion("@heat <h/m/s>")
    @CommandPermission("formularacing.event.admin")
    @Description("Define o limite de tempo do heat")
    public void onSetTimeLimit(Player player, Heats heat, String time) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else {
            Integer timeMillis = ApiUtilities.parseDurationToMillis(time);
            if (timeMillis == null) {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Formato de tempo inválido! Use algo como 5m, 10m..."
                );
            } else {
                int timeSeconds = timeMillis / 1000;
                heat.setTimeLimit(timeSeconds);
                this.eventManager.getDatabaseManager().heatSet(
                    heat.getId(),
                    "timeLimit",
                    String.valueOf(timeSeconds)
                );
                String var10001 = String.valueOf(ChatColor.GREEN);
                player.sendMessage(
                    var10001 +
                        "✓ Limite de tempo do " +
                        String.valueOf(ChatColor.WHITE) +
                        heat.getName() +
                        String.valueOf(ChatColor.GREEN) +
                        " definido para " +
                        String.valueOf(ChatColor.WHITE) +
                        timeSeconds +
                        "s"
                );
            }
        }
    }

    @Subcommand("set collision")
    @CommandCompletion("@heat high|low|disabled")
    @CommandPermission("formularacing.event.admin")
    @Description("Define o modo de colisão do heat")
    public void onSetCollision(Player player, Heats heat, String mode) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else {
            try {
                CollisionMode collisionMode = CollisionMode.valueOf(
                    mode.toUpperCase()
                );
                heat.setCollisionMode(collisionMode);
                this.eventManager.getDatabaseManager().heatSet(
                    heat.getId(),
                    "collisionMode",
                    collisionMode.name()
                );
                String var10001 = String.valueOf(ChatColor.GREEN);
                player.sendMessage(
                    var10001 +
                        "✓ Modo de colisão do " +
                        String.valueOf(ChatColor.WHITE) +
                        heat.getName() +
                        String.valueOf(ChatColor.GREEN) +
                        " definido para " +
                        String.valueOf(ChatColor.WHITE) +
                        collisionMode.name()
                );
            } catch (IllegalArgumentException var5) {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Modo de colisão inválido! Use: high, low, disabled"
                );
            }
        }
    }

    @Subcommand("set startdelay")
    @CommandCompletion("@heat <h/m/s>")
    @CommandPermission("formularacing.event.admin")
    @Description("Define o delay antes do início da corrida")
    public void onSetStartDelay(Player player, Heats heat, String startDelay) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else {
            Integer delayMillis = ApiUtilities.parseDurationToMillis(
                startDelay
            );
            if (delayMillis == null) {
                player.sendMessage(
                    String.valueOf(ChatColor.RED) +
                        "✗ Formato de tempo inválido! Use algo como 5s, 10s..."
                );
            } else {
                int delaySeconds = delayMillis / 1000;
                heat.setStartDelay(delaySeconds);
                this.eventManager.getDatabaseManager().heatSet(
                    heat.getId(),
                    "startDelay",
                    String.valueOf(delaySeconds)
                );
                String var10001 = String.valueOf(ChatColor.GREEN);
                player.sendMessage(
                    var10001 +
                        "✓ Delay de início do " +
                        String.valueOf(ChatColor.WHITE) +
                        heat.getName() +
                        String.valueOf(ChatColor.GREEN) +
                        " definido para " +
                        String.valueOf(ChatColor.WHITE) +
                        delaySeconds +
                        "s"
                );
            }
        }
    }

    @Subcommand("sort")
    @CommandCompletion("@heat random|@heat")
    @CommandPermission("formularacing.event.admin")
    @Description(
        "Ordena as posições do grid com base em um heat anterior ou aleatoriamente"
    )
    public void onSort(Player player, Heats targetHeat, String source) {
        // Usa o método utilitário que criamos antes para resolver o heat atual se for nulo
        if (targetHeat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat de destino selecionado ou ativo!"
            );
            return;
        }

        List<UUID> sortedDrivers = new ArrayList<>(
            targetHeat.getDrivers().keySet()
        );
        String criteriaName;

        // 1. Lógica para Ordenação Aleatória
        if (source.equalsIgnoreCase("random")) {
            Collections.shuffle(sortedDrivers);
            criteriaName = "ALEATÓRIO";
        }
        // 2. Lógica para Ordenação baseada em outro Heat (Fonte)
        else {
            Heats sourceHeat = findSourceHeat(player, targetHeat, source);

            if (sourceHeat == null) {
                player.sendMessage(
                    ChatColor.RED +
                        "✗ Heat de origem '" +
                        source +
                        "' não encontrado!"
                );
                return;
            }

            // Determina se usa Posição (para Finais) ou Melhor Volta (Qualificação)
            boolean usePosition = (sourceHeat.getRound() != null &&
                sourceHeat.getRound().getType() == RoundType.FINAL);

            sortedDrivers.sort((u1, u2) -> {
                Driver d1 = sourceHeat.getDriver(u1);
                Driver d2 = sourceHeat.getDriver(u2);

                if (usePosition) {
                    int p1 = (d1 != null) ? d1.getPosition() : 999;
                    int p2 = (d2 != null) ? d2.getPosition() : 999;
                    return Integer.compare(p1, p2);
                } else {
                    long t1 = (d1 != null && d1.getFastestLap() != null)
                        ? d1.getFastestLap().getLapTime()
                        : Long.MAX_VALUE;
                    long t2 = (d2 != null && d2.getFastestLap() != null)
                        ? d2.getFastestLap().getLapTime()
                        : Long.MAX_VALUE;
                    return Long.compare(t1, t2);
                }
            });

            String typeDesc = usePosition ? "POSIÇÃO FINAL" : "MELHOR VOLTA";
            criteriaName = source.toUpperCase() + " (" + typeDesc + ")";
        }

        // 3. Aplicação das novas posições
        int pos = 1;
        Map<UUID, Integer> newPositions = new HashMap<>();

        for (UUID uuid : sortedDrivers) {
            Driver d = targetHeat.getDriver(uuid);
            if (d != null) {
                d.setStartPosition(pos);
                d.setPosition(pos);
                newPositions.put(uuid, pos);
                pos++;
            }
        }

        // Atualiza Banco de Dados e informa o jogador
        this.eventManager.getDatabaseManager().updateHeatGridPositions(
            targetHeat.getId(),
            newPositions
        );

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(
            ChatColor.YELLOW +
                "  GRID ORDENADO POR: " +
                ChatColor.WHITE +
                criteriaName
        );
        player.sendMessage("");

        for (UUID uuid : sortedDrivers) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            Driver d = targetHeat.getDriver(uuid);
            if (d != null) {
                player.sendMessage(
                    ChatColor.GRAY +
                        "  " +
                        d.getStartPosition() +
                        ". " +
                        ChatColor.WHITE +
                        name
                );
            }
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");

        // 4. Se o Heat já estiver carregado, reordena fisicamente os barcos/jogadores
        if (
            targetHeat.getHeatState() == HeatState.LOADED ||
            targetHeat.getHeatState() == HeatState.STARTING
        ) {
            targetHeat.reorderGrid();
            player.sendMessage(
                ChatColor.GREEN + "✓ Pilotos re-posicionados no grid!"
            );
        }
    }

    /**
     * Método auxiliar para encontrar o Heat de origem baseado na String (ID ou Formato R1Q1)
     */
    private Heats findSourceHeat(
        Player player,
        Heats targetHeat,
        String source
    ) {
        // Tenta formato R1Q1, R1F1, etc.
        if (source.toUpperCase().matches("R\\d+[QFEHP]\\d+")) {
            try {
                String separator = source
                    .toUpperCase()
                    .replaceAll("[^QFEHP]", "");
                String[] parts = source.toUpperCase().split(separator);
                int roundIdx = Integer.parseInt(parts[0].substring(1)) - 1; // Índices costumam ser 0-based
                int heatIdx = Integer.parseInt(parts[1]) - 1;

                Events event = (targetHeat.getRound() != null)
                    ? targetHeat.getRound().getEvent()
                    : null;
                if (event != null) {
                    Rounds round = event
                        .getEventSchedule()
                        .getRounds()
                        .get(roundIdx);
                    if (round != null) {
                        return round.getHeats().get(heatIdx);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Tenta por ID numérico direto
        try {
            int heatId = Integer.parseInt(source);
            return eventManager.getHeat(heatId).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Subcommand("readycheck start")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.event.admin")
    @Description("Inicia um check de prontidão para os pilotos")
    public void onReadyCheck(Player player, Heats heat) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else {
            this.plugin.getReadyCheckManager().startReadyCheck(heat, player);
        }
    }

    @Subcommand("readycheck cancel|stop")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.event.admin")
    @Description("Cancela o check de prontidão atual")
    public void onReadyCheckCancel(Player player, Heats heat) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else if (
            !this.plugin.getReadyCheckManager().isReadyCheckActive(heat.getId())
        ) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Não há um Ready Check ativo para este heat."
            );
        } else {
            this.plugin.getReadyCheckManager().stopReadyCheck(heat.getId());
            String var10001 = String.valueOf(ChatColor.YELLOW);
            player.sendMessage(
                var10001 +
                    "⚠ Ready Check cancelado para o heat " +
                    heat.getName()
            );
        }
    }

    @Subcommand("set drs")
    @CommandCompletion("@heat true|false")
    @CommandPermission("formularacing.event.admin")
    public void onSetDrs(Player player, Heats heat, Boolean drs) {
        String lang = this.database.getPlayerLanguage(player.getUniqueId());
        if (heat == null) {
            Events selected = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
            if (selected == null) {
                player.sendMessage(
                    "§c[!] Você precisa especificar um Heat ou selecionar um Evento primeiro."
                );
                return;
            }

            selected
                .getSchedule()
                .getRoundsCollection()
                .forEach(round ->
                    round
                        .getHeats()
                        .values()
                        .forEach(h -> h.setDrsEnabled(drs))
                );
            String var10001 = drs ? "§2LIGADO" : "§cDESLIGADO";
            player.sendMessage(
                "§a[DRS] Status definido como " +
                    var10001 +
                    " §apara TODO o evento: §f" +
                    String.valueOf(selected)
            );
        } else {
            heat.setDrsEnabled(drs);
            String var6 = drs ? "§2LIGADO" : "§cDESLIGADO";
            player.sendMessage(
                "§a[DRS] Status definido como " +
                    var6 +
                    " §apara o Heat: §f" +
                    heat.getHeatNumber()
            );
        }

        player.playSound(
            player.getLocation(),
            Sound.BLOCK_NOTE_BLOCK_CHIME,
            1.0F,
            1.2F
        );
    }

    @Subcommand("set pushtopass|set p2p")
    @CommandCompletion("@heat true|false")
    @CommandPermission("formularacing.event.admin")
    public void onSetPushToPass(Player player, Heats heat, Boolean enabled) {
        if (heat == null) {
            Events selected = this.database.getPlayerSelectedEvent(
                player.getUniqueId()
            ).orElse(null);
            if (selected == null) {
                player.sendMessage(
                    "§c[!] Você precisa especificar um Heat ou selecionar um Evento primeiro."
                );
                return;
            }

            selected
                .getSchedule()
                .getRoundsCollection()
                .forEach(round ->
                    round
                        .getHeats()
                        .values()
                        .forEach(h -> h.setPushtopass(enabled))
                );
            String status = enabled ? "§2LIGADO" : "§cDESLIGADO";
            player.sendMessage(
                "§a[P2P] Status definido como " +
                    status +
                    " §apara TODO o evento: §f" +
                    selected
            );
        } else {
            heat.setPushtopass(enabled);
            String status = enabled ? "§2LIGADO" : "§cDESLIGADO";
            player.sendMessage(
                "§a[P2P] Status definido como " +
                    status +
                    " §apara o Heat: §f" +
                    heat.getHeatNumber()
            );
        }

        player.playSound(
            player.getLocation(),
            Sound.BLOCK_NOTE_BLOCK_CHIME,
            1.0F,
            1.2F
        );
    }

    @Subcommand("set driverposition")
    @CommandCompletion("@heat @players <[+/-]pos>")
    @CommandPermission("formularacing.event.admin")
    @Description("Define a posição de largada de um piloto")
    public void onSetDriverPosition(
        Player player,
        Heats heat,
        String targetPlayerName,
        String positionStr
    ) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else {
            Player target = Bukkit.getPlayer(targetPlayerName);
            if (target == null) {
                String var14 = String.valueOf(ChatColor.RED);
                player.sendMessage(
                    var14 + "✗ Jogador não encontrado: " + targetPlayerName
                );
            } else {
                Driver driver = heat.getDriver(target.getUniqueId());
                if (driver == null) {
                    String var13 = String.valueOf(ChatColor.RED);
                    player.sendMessage(
                        var13 +
                            "✗ O jogador " +
                            target.getName() +
                            " não está no heat " +
                            heat.getName()
                    );
                } else if (
                    heat.getHeatState() != HeatState.RACING &&
                    heat.getHeatState() != HeatState.STARTING
                ) {
                    int newPos;
                    try {
                        if (positionStr.startsWith("+")) {
                            newPos =
                                driver.getStartPosition() +
                                Integer.parseInt(positionStr.substring(1));
                        } else if (positionStr.startsWith("-")) {
                            newPos =
                                driver.getStartPosition() -
                                Integer.parseInt(positionStr.substring(1));
                        } else {
                            newPos = Integer.parseInt(positionStr);
                        }
                    } catch (NumberFormatException var9) {
                        String var10001 = String.valueOf(ChatColor.RED);
                        player.sendMessage(
                            var10001 + "✗ Posição inválida: " + positionStr
                        );
                        return;
                    }

                    if (heat.setDriverPosition(driver, newPos)) {
                        String var11 = String.valueOf(ChatColor.GREEN);
                        player.sendMessage(
                            var11 +
                                "✓ Posição de " +
                                target.getName() +
                                " definida para P" +
                                newPos
                        );
                        if (heat.getHeatState() == HeatState.LOADED) {
                            player.sendMessage(
                                String.valueOf(ChatColor.YELLOW) +
                                    "⚠ O grid foi atualizado. Pilotos re-teleportados."
                            );
                        }
                    } else {
                        String var12 = String.valueOf(ChatColor.RED);
                        player.sendMessage(
                            var12 +
                                "✗ Falha ao definir posição. Verifique se o valor está entre 1 e " +
                                heat.getDrivers().size()
                        );
                    }
                } else {
                    player.sendMessage(
                        String.valueOf(ChatColor.RED) +
                            "✗ Não é possível alterar a posição com o heat em andamento!"
                    );
                }
            }
        }
    }

    @Subcommand("set laps")
    @CommandCompletion("@heat <laps>")
    @CommandPermission("formularacing.event.admin")
    @Description("Define o número de voltas do heat")
    public void onSetLaps(Player player, Heats heat, int laps) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else if (laps < 1) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ O número de voltas deve ser pelo menos 1."
            );
        } else {
            String trackName = heat.getTrackNameWS();
            if (
                trackName != null &&
                !this.database.isCircuit(trackName) &&
                laps > 1
            ) {
                player.sendMessage(
                    "§e⚠️ A pista '" +
                        trackName +
                        "' não é um circuito fechado (Sprint/Parkour)."
                );
                player.sendMessage("§e⚠️ Forçando 1 volta.");
                laps = 1;
            }

            heat.setTotalLaps(laps);
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(
                var10001 +
                    "✓ Voltas do heat " +
                    heat.getName() +
                    " definidas para " +
                    laps
            );
        }
    }

    @Subcommand("set pits")
    @CommandCompletion("@heat <pits>")
    @CommandPermission("formularacing.event.admin")
    @Description("Define o número de pit stops obrigatórios")
    public void onSetPits(Player player, Heats heat, int pits) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Nenhum heat selecionado ou ativo!"
            );
        } else if (pits < 0) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ O número de pits não pode ser negativo."
            );
        } else {
            String trackName = heat.getTrackNameWS();
            if (
                trackName != null &&
                !this.database.isCircuit(trackName) &&
                pits > 0
            ) {
                player.sendMessage(
                    "§e⚠️ A pista '" +
                        trackName +
                        "' não é um circuito fechado (Sprint/Parkour)."
                );
                player.sendMessage("§e⚠️ Forçando 0 pits.");
                pits = 0;
            }

            heat.setTotalPits(pits);
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(
                var10001 +
                    "✓ Pit stops obrigatórios do heat " +
                    heat.getName() +
                    " definidos para " +
                    pits
            );
        }
    }

    @Subcommand("set maxdrivers")
    @CommandCompletion("@heat <max>")
    @CommandPermission("formularacing.event.admin")
    @Description("Define o número máximo de pilotos no heat")
    public void onSetMaxDrivers(Player player, Heats heat, int max) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        if (max < 1) {
            player.sendMessage(
                ChatColor.RED + "✗ O máximo de pilotos deve ser pelo menos 1."
            );
            return;
        }

        heat.setMaxDrivers(max);
        player.sendMessage(
            ChatColor.GREEN +
                "✓ Máximo de pilotos do heat " +
                heat.getName() +
                " definido para " +
                max
        );
    }

    @Subcommand("set eliminterval")
    @CommandCompletion("@heat <seconds>")
    @CommandPermission("formularacing.event.admin")
    @Description("Define o intervalo de eliminação em segundos (apenas heats de eliminação)")
    public void onSetElimInterval(Player player, Heats heat, int seconds) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!");
            return;
        }
        if (heat.getRound() == null || heat.getRound().getType() != RoundType.ELIMINATION) {
            player.sendMessage(ChatColor.RED + "✗ Esta configuração só é válida para heats de eliminação!");
            return;
        }
        if (seconds < 5) {
            player.sendMessage(ChatColor.RED + "✗ O intervalo mínimo é de 5 segundos.");
            return;
        }
        heat.setEliminationIntervalSeconds(seconds);
        this.eventManager.getDatabaseManager().heatSet(heat.getId(), "eliminationInterval", String.valueOf(seconds));
        player.sendMessage(ChatColor.GREEN + "✓ Intervalo de eliminação do " + ChatColor.WHITE + heat.getName() + ChatColor.GREEN + " definido para " + ChatColor.WHITE + seconds + "s");
    }

    @Subcommand("set mindrivers")
    @CommandCompletion("@heat <minimum>")
    @CommandPermission("formularacing.event.admin")
    @Description("Define o número mínimo de pilotos antes de encerrar a eliminação")
    public void onSetMinDrivers(Player player, Heats heat, int minimum) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!");
            return;
        }
        if (heat.getRound() == null || heat.getRound().getType() != RoundType.ELIMINATION) {
            player.sendMessage(ChatColor.RED + "✗ Esta configuração só é válida para heats de eliminação!");
            return;
        }
        if (minimum < 1) {
            player.sendMessage(ChatColor.RED + "✗ O mínimo de pilotos deve ser pelo menos 1.");
            return;
        }
        heat.setMinimumDrivers(minimum);
        this.eventManager.getDatabaseManager().heatSet(heat.getId(), "minimumDrivers", String.valueOf(minimum));
        player.sendMessage(ChatColor.GREEN + "✓ Mínimo de pilotos do " + ChatColor.WHITE + heat.getName() + ChatColor.GREEN + " definido para " + ChatColor.WHITE + minimum);
    }

    @Subcommand("set lonely")
    @CommandCompletion("@heat true|false")
    @CommandPermission("formularacing.event.admin")
    @Description("Ativa ou desativa o modo solitário (ghost) no heat")
    public void onSetLonely(Player player, Heats heat, boolean lonely) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.setLonely(lonely);
        player.sendMessage(
            ChatColor.GREEN +
                "✓ Modo solitário do heat " +
                heat.getName() +
                " definido para " +
                (lonely ? "ATIVADO" : "DESATIVADO")
        );
    }

    @Subcommand("set reversegrid")
    @CommandCompletion("@heat @range:1-100")
    @CommandPermission("formularacing.event.admin")
    @Description("Inverte o grid de largada do heat")
    public void onReverseGrid(
        Player player,
        Heats heat,
        @Default("100") Integer percentage
    ) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.reverseGrid(percentage);
        player.sendMessage(
            ChatColor.GREEN +
                "✓ Grid do heat " +
                heat.getName() +
                " invertido (" +
                percentage +
                "%)."
        );

        if (heat.getHeatState() == HeatState.LOADED) {
            player.sendMessage(
                ChatColor.YELLOW +
                    "⚠ O grid foi atualizado. Pilotos re-teleportados."
            );
        }
    }

    @Subcommand("finish|stop")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.event.admin")
    @Description("Finaliza um heat")
    public void onFinish(Player player, Heats heat) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.finishHeat();
        player.sendMessage(
            ChatColor.GREEN + "✓ Heat " + heat.getName() + " finalizado!"
        );
    }

    @Subcommand("reset")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.event.admin")
    @Description("Reseta um heat IMEDIATAMENTE")
    public void onReset(Player player, Heats heat) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        heat.resetHeat();
        player.sendMessage(
            ChatColor.YELLOW +
                "⚠ Heat " +
                heat.getName() +
                " RESETADO para estado inicial!"
        );
    }

    @Subcommand("delete|remove")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.event.admin")
    public void onDelete(Player player, Heats heat) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        if (this.eventManager.removeHeat(heat)) {
            player.sendMessage(
                ChatColor.GREEN + "✓ Heat " + heat.getName() + " removido."
            );
        } else {
            player.sendMessage(ChatColor.RED + "✗ Falha ao remover heat.");
        }
    }

    @Subcommand("adddriver|join")
    @CommandCompletion("@heat @players")
    @CommandPermission("formularacing.event.admin")
    public void onAddDriver(Player player, Heats heat, String targetName) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        final Heats resolvedHeat = heat;
        final Player onlineTarget = Bukkit.getPlayerExact(targetName);
        OfflinePlayer offlinePlayer =
            onlineTarget != null
                ? onlineTarget
                : Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = offlinePlayer.getUniqueId();
        final String resolvedTargetName =
            onlineTarget != null ? onlineTarget.getName() : targetName;

        this.heatDriverService.addDriver(
                resolvedHeat,
                targetUuid,
                resolvedTargetName,
                null
            )
            .thenAccept(result -> {
                SchedulerHelper.runTask(this.plugin, () -> {
                        if (
                            result.getStatus() ==
                            HeatDriverCommandService.DriverMutationStatus.SUCCESS
                        ) {
                            if (
                                onlineTarget != null && onlineTarget.isOnline()
                            ) {
                                resolvedHeat.handleLateJoin(onlineTarget);
                            }

                            player.sendMessage(
                                ChatColor.GREEN +
                                    "✓ Piloto " +
                                    resolvedTargetName +
                                    " adicionado ao heat " +
                                    resolvedHeat.getName() +
                                    " na posição " +
                                    result.getFinalPosition()
                            );
                            return;
                        }

                        player.sendMessage(
                            this.translateDriverMutationFailure(
                                result.getStatus(),
                                resolvedHeat,
                                resolvedTargetName
                            )
                        );
                    });
            })
            .exceptionally(exception -> {
                SchedulerHelper.runTask(this.plugin, () ->
                        player.sendMessage(
                            ChatColor.RED +
                                "✗ Falha inesperada ao adicionar piloto."
                        )
                    );
                return null;
            });
    }

    @Subcommand("adddriver|join")
    @CommandCompletion("@heat @players @range:1-100")
    @CommandPermission("formularacing.event.admin")
    public void onAddDriverAtPosition(
        Player player,
        Heats heat,
        String targetName,
        Integer position
    ) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        final Heats resolvedHeat = heat;
        final Player onlineTarget = Bukkit.getPlayerExact(targetName);
        OfflinePlayer offlinePlayer =
            onlineTarget != null
                ? onlineTarget
                : Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = offlinePlayer.getUniqueId();
        final String resolvedTargetName =
            onlineTarget != null ? onlineTarget.getName() : targetName;
        final Integer targetPosition = position;

        this.heatDriverService.addDriver(
                resolvedHeat,
                targetUuid,
                resolvedTargetName,
                targetPosition
            )
            .thenAccept(result -> {
                SchedulerHelper.runTask(this.plugin, () -> {
                        if (
                            result.getStatus() ==
                            HeatDriverCommandService.DriverMutationStatus.SUCCESS
                        ) {
                            if (
                                onlineTarget != null && onlineTarget.isOnline()
                            ) {
                                resolvedHeat.handleLateJoin(onlineTarget);
                            }

                            player.sendMessage(
                                ChatColor.GREEN +
                                    "✓ Piloto " +
                                    resolvedTargetName +
                                    " adicionado ao heat " +
                                    resolvedHeat.getName() +
                                    " na posição " +
                                    result.getFinalPosition()
                            );
                            return;
                        }

                        player.sendMessage(
                            this.translateDriverMutationFailure(
                                result.getStatus(),
                                resolvedHeat,
                                resolvedTargetName
                            )
                        );
                    });
            })
            .exceptionally(exception -> {
                SchedulerHelper.runTask(this.plugin, () ->
                        player.sendMessage(
                            ChatColor.RED +
                                "✗ Falha inesperada ao adicionar piloto."
                        )
                    );
                return null;
            });
    }

    @Subcommand("removedriver|leave")
    @CommandCompletion("@heat @players")
    @CommandPermission("formularacing.event.admin")
    public void onRemoveDriver(Player player, Heats heat, String targetName) {
        heat = this.resolveHeat(player, heat);
        if (heat == null) {
            player.sendMessage(
                ChatColor.RED + "✗ Nenhum heat selecionado ou ativo!"
            );
            return;
        }

        final Heats resolvedHeat = heat;
        final Player onlineTarget = Bukkit.getPlayerExact(targetName);
        OfflinePlayer offlinePlayer =
            onlineTarget != null
                ? onlineTarget
                : Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = offlinePlayer.getUniqueId();
        final String resolvedTargetName =
            onlineTarget != null ? onlineTarget.getName() : targetName;

        this.heatDriverService.removeDriver(
                resolvedHeat,
                targetUuid,
                resolvedTargetName
            )
            .thenAccept(result -> {
                SchedulerHelper.runTask(this.plugin, () -> {
                        if (
                            result.getStatus() ==
                            HeatDriverCommandService.DriverMutationStatus.SUCCESS
                        ) {
                            if (
                                onlineTarget != null && onlineTarget.isOnline()
                            ) {
                                resolvedHeat.handleLateLeave(onlineTarget);
                            }

                            player.sendMessage(
                                ChatColor.GREEN +
                                    "✓ Piloto " +
                                    resolvedTargetName +
                                    " removido do heat " +
                                    resolvedHeat.getName()
                            );
                            return;
                        }

                        player.sendMessage(
                            this.translateDriverMutationFailure(
                                result.getStatus(),
                                resolvedHeat,
                                resolvedTargetName
                            )
                        );
                    });
            })
            .exceptionally(exception -> {
                SchedulerHelper.runTask(this.plugin, () ->
                        player.sendMessage(
                            ChatColor.RED +
                                "✗ Falha inesperada ao remover piloto."
                        )
                    );
                return null;
            });
    }

    private String translateDriverMutationFailure(
        HeatDriverCommandService.DriverMutationStatus status,
        Heats heat,
        String targetName
    ) {
        switch (status) {
            case INVALID_CONTEXT:
                return (
                    ChatColor.RED +
                    "✗ Contexto inválido para executar o comando."
                );
            case INVALID_HEAT_STATE:
                return (
                    ChatColor.RED +
                    "✗ Heat " +
                    heat.getName() +
                    " não está em estado editável."
                );
            case ALREADY_IN_HEAT:
                return (
                    ChatColor.RED +
                    "✗ Piloto " +
                    targetName +
                    " já está neste heat."
                );
            case ALREADY_IN_ROUND:
                return (
                    ChatColor.RED +
                    "✗ Piloto " +
                    targetName +
                    " já está em outro heat deste round."
                );
            case HEAT_FULL:
                return (
                    ChatColor.RED + "✗ Heat " + heat.getName() + " está lotado."
                );
            case INVALID_POSITION:
                return (
                    ChatColor.RED + "✗ Posição inválida para inserção no grid."
                );
            case NOT_IN_HEAT:
                return (
                    ChatColor.RED +
                    "✗ Piloto " +
                    targetName +
                    " não está no heat."
                );
            case CONFLICT:
                return (
                    ChatColor.YELLOW +
                    "⚠ Outro admin está editando este round agora. Tente novamente em instantes."
                );
            case PERSISTENCE_ERROR:
                return (
                    ChatColor.RED +
                    "✗ Falha ao persistir alteração no banco. Nenhuma mudança parcial foi confirmada."
                );
            case SYNC_ERROR:
                return (
                    ChatColor.RED +
                    "✗ Alteração persistida, mas falhou sincronização de runtime. Recarregue o heat e tente novamente."
                );
            default:
                return ChatColor.RED + "✗ Falha ao executar operação no heat.";
        }
    }
}
