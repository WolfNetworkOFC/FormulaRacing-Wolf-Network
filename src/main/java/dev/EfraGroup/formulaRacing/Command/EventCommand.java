package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.Controllers.EventSignupService;
import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Event.EventState;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Subscriber;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.ApiUtilities;
import dev.EfraGroup.formulaRacing.Utils.ClickableMessageUtil;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Content;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("event")
public class EventCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final RaceEventManager eventManager;
    private final DatabaseManager database;
    private final EventSignupService signupService;

    public EventCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.eventManager = plugin.getRaceEventManager();
        this.database = plugin.getDatabaseManager();
        this.signupService = new EventSignupService(plugin);
    }

    @Default
    @Description(("Mostra info do evento atual"))
    public void onDefault(Player player) {
        Optional<Events> eventOpt = this.database.getPlayerSelectedEvent(
            player.getUniqueId()
        );
        if (eventOpt.isPresent()) {
            this.onInfo(player, (Events) eventOpt.get());
        } else {
            this.plugin.sendMessage(
                player,
                "event_none_selected_hint",
                new String[0]
            );
        }
    }

    @Subcommand("list|ls")
    @Description("Lista todos os eventos ativos")
    public void onList(CommandSender sender) {
        List<Events> activeEvents = this.eventManager.getAllEvents()
            .stream()
            .filter(Events::isActive)
            .sorted(Comparator.comparingLong(Events::getDate))
            .toList();
        sender.sendMessage("");
        this.plugin.sendMessage(
            sender instanceof Player ? (Player) sender : null,
            "event_list_header",
            new String[0]
        );
        sender.sendMessage("");
        if (activeEvents.isEmpty()) {
            this.plugin.sendMessage(
                sender instanceof Player ? (Player) sender : null,
                "event_list_empty",
                new String[0]
            );
        }

        for (Events event : activeEvents) {
            String creatorName = event.getCreatorName();
            String dateStr = ApiUtilities.formatDate(event.getDate());
            TextComponent message = new TextComponent("  ");
            TextComponent nameBtn = new TextComponent(event.getDisplayName());
            nameBtn.setColor(ChatColor.AQUA);
            nameBtn.setBold(true);
            nameBtn.setClickEvent(
                new ClickEvent(
                    Action.RUN_COMMAND,
                    "/event info " + event.getDisplayName()
                )
            );
            nameBtn.setHoverEvent(
                new HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new Content[] { new Text("§aClique para gerenciar") }
                )
            );
            message.addExtra(nameBtn);
            String var10003 = String.valueOf(ChatColor.GRAY);
            message.addExtra(
                new TextComponent(
                    var10003 + " (" + event.getState().name() + ")"
                )
            );
            message.addExtra(
                new TextComponent(String.valueOf(ChatColor.DARK_GRAY) + " - ")
            );
            var10003 = String.valueOf(ChatColor.YELLOW);
            message.addExtra(new TextComponent(var10003 + dateStr));
            message.addExtra(
                new TextComponent(String.valueOf(ChatColor.DARK_GRAY) + " > ")
            );
            var10003 = String.valueOf(ChatColor.WHITE);
            message.addExtra(new TextComponent(var10003 + creatorName));
            sender.spigot().sendMessage(message);
        }

        sender.sendMessage("");
    }

    @Subcommand("select")
    @CommandCompletion("@event")
    @Description("Seleciona um evento para gerenciar")
    public void onSelect(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
            return;
        }

        this.selectEventForPlayer(player, event, true);
        this.showEventInfo(player, event);
    }

    @Subcommand("info|view")
    @CommandCompletion("@event")
    @Description("Mostra informações do evento")
    public void onInfo(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
        } else {
            this.selectEventForPlayer(player, event, false);
            this.showEventInfo(player, event);
        }
    }

    @Subcommand("create|new")
    @CommandPermission("formularacing.event.admin")
    @Description("Cria um novo evento vazio")
    @CommandCompletion("@nothing @tracks")
    public void onCreate(Player player, String name, String trackNameWS) {
        this.eventManager.createEvent(
            player.getUniqueId(),
            name,
            trackNameWS
        ).thenAccept(event -> {
            this.plugin.getServer()
                .getScheduler()
                .runTask(this.plugin, () -> {
                    if (event != null) {
                        this.plugin.sendMessage(
                            player,
                            "event_created",
                            new String[] { "{event}", name }
                        );
                        this.selectEventForPlayer(player, event, false);
                    } else {
                        this.plugin.sendMessage(
                            player,
                            "event_create_error",
                            new String[0]
                        );
                    }
                });
        });
    }

    @Subcommand("createfull")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@nothing @tracks 15 0 10 5 0")
    @Description("Cria um evento completo (Treino + Qualy + Final)")
    public void onCreateFull(
        Player player,
        String name,
        String track,
        @Default("15") int practiceTime,
        @Default("0") int qualLaps,
        @Default("10") int qualTime,
        @Default("5") int finalLaps,
        @Default("0") int pits
    ) {
        if (!this.database.isCircuit(track)) {
            boolean changed = false;
            if (qualLaps > 1) {
                qualLaps = 1;
                changed = true;
            }

            if (finalLaps > 1) {
                finalLaps = 1;
                changed = true;
            }

            if (pits > 0) {
                pits = 0;
                changed = true;
            }

            if (changed) {
                player.sendMessage(
                    "§e⚠️ A pista '" +
                        track +
                        "' não é um circuito fechado (Sprint/Parkour)."
                );
                player.sendMessage(
                    "§e⚠️ Voltas ajustadas para 1 e Pit Stops desativados."
                );
            }
        }

        this.eventManager.createFullEvent(
            player.getUniqueId(),
            name,
            track,
            practiceTime,
            qualLaps,
            qualTime,
            finalLaps,
            pits
        ).thenAccept(event -> {
            this.plugin.getServer()
                .getScheduler()
                .runTask(this.plugin, () -> {
                    if (event != null) {
                        this.plugin.sendMessage(
                            player,
                            "event_full_created",
                            new String[] { "{event}", name }
                        );
                        this.selectEventForPlayer(player, event, false);
                    } else {
                        this.plugin.sendMessage(
                            player,
                            "event_full_error",
                            new String[0]
                        );
                    }
                });
        });
    }

    @Subcommand("delete|remove|del")
    @CommandCompletion("@event")
    @CommandPermission("formularacing.event.admin")
    @Description("Remove um evento")
    public void onDelete(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
            return;
        }

        if (this.eventManager.deleteEvent(event.getId())) {
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(
                var10001 +
                    "✓ Evento removido com sucesso: " +
                    String.valueOf(ChatColor.GOLD) +
                    event.getDisplayName()
            );
        } else {
            this.plugin.sendMessage(
                player,
                "event_delete_error",
                new String[0]
            );
        }
    }

    @Subcommand("start|begin")
    @CommandCompletion("@event")
    @CommandPermission("formularacing.event.admin")
    @Description("Inicia um evento")
    public void onStart(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
        } else {
            if (event.start()) {
                this.plugin.sendMessage(player, "event_started", new String[0]);
            } else {
                this.plugin.sendMessage(
                    player,
                    "event_start_error",
                    new String[0]
                );
            }
        }
    }

    @Subcommand("finish|end|stop")
    @CommandCompletion("@event")
    @CommandPermission("formularacing.event.admin")
    @Description("Finaliza um evento")
    public void onFinish(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
        } else {
            if (event.finish()) {
                this.plugin.sendMessage(
                    player,
                    "event_finished",
                    new String[0]
                );
            } else {
                this.plugin.sendMessage(
                    player,
                    "event_finish_error",
                    new String[0]
                );
            }
        }
    }

    @Subcommand("sign|join|register")
    @CommandCompletion("@event")
    @Description("Inscreve-se como piloto")
    public void onSign(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
            return;
        }

        if (!event.isOpenSign()) {
            this.plugin.sendMessage(
                    player,
                    "event_is_closed"
            );
            return;
        }

        EventSignupService.SignupResult result = this.signupService.signPlayer(
            player,
            event,
            true
        );
        boolean isActiveDaily =
            this.plugin.getDailyRaceManager() != null &&
            this.plugin.getDailyRaceManager()
                .getActiveDailyEvent()
                .isPresent() &&
            (
                (Events) this.plugin.getDailyRaceManager()
                    .getActiveDailyEvent()
                    .get()
            ).getId() ==
            event.getId();
        if (isActiveDaily) {
            this.plugin.getDailyRaceManager().handleDailySignup(
                player,
                event,
                result
            );
            return;
        }

        switch (result.getStatus()) {
            case ALREADY_SUBSCRIBED:
            case ALREADY_SUBSCRIBED_DAILY:
                this.plugin.sendMessage(
                    player,
                    "event_already_subscribed",
                    new String[0]
                );
                return;
            case SIGN_CLOSED:
                this.plugin.sendMessage(
                    player,
                    "event_sign_closed",
                    new String[0]
                );
                return;
            case FINISHED:
                this.plugin.sendMessage(
                    player,
                    "event_already_finished",
                    new String[0]
                );
                return;
            case ERROR:
                this.plugin.sendMessage(
                    player,
                    "event_sign_error",
                    new String[0]
                );
                return;
            case NO_EVENT:
                this.plugin.sendMessage(
                    player,
                    "event_none_selected",
                    new String[0]
                );
                return;
            case SIGNED:
                if (result.isMovedFromReserve()) {
                    this.plugin.sendMessage(
                        player,
                        "event_moved_from_reserve",
                        new String[0]
                    );
                }

                UUID playerUUID = player.getUniqueId();
                player.sendMessage("");
                this.plugin.sendMessage(player, "event_signed", new String[0]);
                String var10001 = String.valueOf(ChatColor.GRAY);
                player.sendMessage(
                    var10001 +
                        "Evento: " +
                        String.valueOf(ChatColor.WHITE) +
                        event.getDisplayName()
                );
                if (event.getTrackNameWS() != null) {
                    var10001 = String.valueOf(ChatColor.GRAY);
                    player.sendMessage(
                        var10001 +
                            "Pista: " +
                            String.valueOf(ChatColor.WHITE) +
                            event.getTrackNameWS()
                    );
                }

                var10001 = String.valueOf(ChatColor.GRAY);
                player.sendMessage(
                    var10001 +
                        "Inscritos: " +
                        String.valueOf(ChatColor.WHITE) +
                        event.getSubscriberCount()
                );
                player.sendMessage("");
                this.database.setPlayerSelectedEvent(playerUUID, event);
                var10001 = String.valueOf(ChatColor.GRAY);
                this.broadcastToAdmins(
                    var10001 +
                        "[Evento] " +
                        String.valueOf(ChatColor.GREEN) +
                        player.getName() +
                        " se inscreveu em " +
                        event.getDisplayName() +
                        String.valueOf(ChatColor.GRAY) +
                        " (" +
                        event.getSubscriberCount() +
                        " inscritos)"
                );
                return;
            default:
                this.plugin.sendMessage(
                    player,
                    "event_sign_error",
                    new String[0]
                );
        }
    }

    @Subcommand("reserve")
    @CommandCompletion("@event")
    @Description("Inscreve-se como reserva")
    public void onReserve(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
        } else {
            UUID playerUUID = player.getUniqueId();
            if (event.isReserve(playerUUID)) {
                if (event.getState() != EventState.SETUP) {
                    this.plugin.sendMessage(
                        player,
                        "event_already_started_quit_error",
                        new String[0]
                    );
                } else {
                    if (event.removeReserve(playerUUID)) {
                        this.plugin.sendMessage(
                            player,
                            "event_reserve_left",
                            new String[] { "{event}", event.getDisplayName() }
                        );
                    }
                }
            } else {
                if (event.addReserve(playerUUID)) {
                    this.plugin.sendMessage(
                        player,
                        "event_reserve_joined",
                        new String[] { "{event}", event.getDisplayName() }
                    );
                    this.database.setPlayerSelectedEvent(playerUUID, event);
                } else {
                    this.plugin.sendMessage(
                        player,
                        "event_reserve_error",
                        new String[0]
                    );
                }
            }
        }
    }

    @Subcommand("signs|subscribers")
    @CommandCompletion("@event")
    @Description("Lista inscritos e reservas do evento")
    public void onSigns(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
        } else {
            player.sendMessage("");
            String var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(
                var10001 +
                    String.valueOf(ChatColor.BOLD) +
                    "  INSCRITOS: " +
                    String.valueOf(ChatColor.WHITE) +
                    event.getDisplayName()
            );
            if (event.getSubscribers().isEmpty()) {
                player.sendMessage(
                    String.valueOf(ChatColor.GRAY) + "  (Nenhum inscrito)"
                );
            } else {
                int count = 1;

                for (Subscriber sub : event.getSubscribers().values()) {
                    String name = this.plugin.getServer()
                        .getOfflinePlayer(sub.getUuid())
                        .getName();
                    TextComponent line = new TextComponent(
                        "  " +
                            count +
                            ". " +
                            String.valueOf(ChatColor.AQUA) +
                            name
                    );
                    player.spigot().sendMessage(line);
                    ++count;
                }
            }

            if (!event.getReserves().isEmpty()) {
                player.sendMessage("");
                var10001 = String.valueOf(ChatColor.GOLD);
                player.sendMessage(
                    var10001 + String.valueOf(ChatColor.BOLD) + "  RESERVAS:"
                );
                int count = 1;

                for (Subscriber sub : event.getReserves().values()) {
                    String name = this.plugin.getServer()
                        .getOfflinePlayer(sub.getUuid())
                        .getName();
                    TextComponent line = new TextComponent(
                        "  " +
                            count +
                            ". " +
                            String.valueOf(ChatColor.YELLOW) +
                            name
                    );
                    player.spigot().sendMessage(line);
                    ++count;
                }
            }

            player.sendMessage("");
        }
    }

    @Subcommand("broadcast clicktosign")
    @CommandCompletion("@event")
    @CommandPermission("formularacing.event.admin")
    @Description("Envia mensagem clicável para inscrição")
    public void onBroadcast(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
        } else if (event.getState() == EventState.FINISHED) {
            player.sendMessage(
                String.valueOf(ChatColor.RED) +
                    "✗ Este evento já foi finalizado!"
            );
        } else {
            int sentCount = 0;

            for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
                UUID playerUUID = onlinePlayer.getUniqueId();
                if (
                    !event.isSubscriber(playerUUID) &&
                    (event.isOpenSign() ||
                        onlinePlayer.hasPermission("formularacing.event.admin"))
                ) {
                    String clickText =
                        this.plugin.getTranslationUtil().getTranslated(
                            onlinePlayer,
                            "event_click_to_sign",
                            new String[] { "{event}", event.getDisplayName() }
                        );
                    String hoverText =
                        this.plugin.getTranslationUtil().getTranslated(
                            onlinePlayer,
                            "event_click_to_sign_hover",
                            new String[] { "{event}", event.getDisplayName() }
                        );
                    ClickableMessageUtil.sendEventSignBroadcast(
                        onlinePlayer,
                        clickText,
                        hoverText,
                        event.getDisplayName()
                    );
                    ++sentCount;
                }
            }

            String lang = this.database.getPlayerLanguage(player.getUniqueId());
            this.plugin.sendMessage(
                player,
                "event_broadcast_sent",
                new String[] { "{count}", String.valueOf(sentCount) }
            );
        }
    }

    private void broadcastToAdmins(String message) {
        this.plugin.getServer()
            .getOnlinePlayers()
            .stream()
            .filter(p -> p.hasPermission("formularacing.event.admin"))
            .forEach(p -> p.sendMessage(message));
    }

    @Subcommand("spectate|watch")
    @CommandCompletion("@event")
    @Description("Entra como espectador")
    public void onSpectate(
        Player player,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
        } else if (
            this.plugin.getSpectatorManager().isSpectator(player.getUniqueId())
        ) {
            this.plugin.sendMessage(
                player,
                "spectate_already_spectator",
                new String[0]
            );
        } else if (event.getState() != EventState.RUNNING) {
            this.plugin.sendMessage(
                player,
                "spectate_not_running",
                new String[0]
            );
            this.plugin.sendMessage(
                player,
                "spectate_state_info",
                new String[] { "{state}", event.getState().name() }
            );
        } else if (event.isActivelyRacing(player.getUniqueId())) {
            this.plugin.sendMessage(
                player,
                "spectate_already_driver",
                new String[0]
            );
            this.plugin.sendMessage(
                player,
                "spectate_already_driver_desc",
                new String[0]
            );
        } else {
            if (
                !this.plugin.getSpectatorManager().addSpectator(player, event)
            ) {
                this.plugin.sendMessage(
                    player,
                    "event_spectator_error",
                    new String[0]
                );
            } else {
                this.plugin.sendMessage(
                    player,
                    "event_spectator_joined",
                    new String[] { "{event}", event.getDisplayName() }
                );
            }
        }
    }

    @Subcommand("quit|leave")
    @Description("Sai do evento atual")
    public void onQuit(Player player) {
        if (
            this.plugin.getSpectatorManager().isSpectator(player.getUniqueId())
        ) {
            Events watching =
                this.plugin.getSpectatorManager().getWatchingEvent(
                    player.getUniqueId()
                );
            if (this.plugin.getSpectatorManager().removeSpectator(player)) {
                this.plugin.sendMessage(
                    player,
                    "event_left",
                    new String[] {
                        "{event}",
                        watching != null ? watching.getDisplayName() : "-",
                    }
                );
            }

            return;
        }

        Optional<Events> eventOpt = this.eventManager.getPlayerEvent(
            player.getUniqueId()
        );
        if (eventOpt.isPresent()) {
            this.eventManager.removePlayerFromEvent(player.getUniqueId());
            this.plugin.sendMessage(
                player,
                "event_left",
                new String[] {
                    "{event}",
                    ((Events) eventOpt.get()).getDisplayName(),
                }
            );
        } else {
            this.plugin.sendMessage(
                player,
                "event_not_in_event",
                new String[0]
            );
        }
    }

    @Subcommand("settrack|track")
    @CommandCompletion("@event @tracks")
    @CommandPermission("formularacing.event.admin")
    @Description("Define a pista do evento")
    public void onSetTrack(
        Player player,
        @co.aikar.commands.annotation.Optional Events event,
        String[] trackArgs
    ) {
        if (event == null) {
            event = database
                .getPlayerSelectedEvent(player.getUniqueId())
                .orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
        } else if (trackArgs.length == 0) {
            this.plugin.sendMessage(
                player,
                "event_usage_settrack",
                new String[0]
            );
        } else {
            String trackName = String.join(" ", trackArgs);
            if (event.setTrack(trackName)) {
                this.plugin.sendMessage(
                    player,
                    "event_track_set",
                    new String[] { "{track}", trackName }
                );
            } else {
                this.plugin.sendMessage(
                    player,
                    "event_create_error",
                    new String[0]
                );
            }
        }
    }

    @Subcommand("set signs")
    @CommandCompletion("open|closed")
    @CommandPermission("formularacing.event.admin")
    public void onSetSigns(Player player, String state) {
        Optional<Events> eventOpt = this.database.getPlayerSelectedEvent(
            player.getUniqueId()
        );
        if (eventOpt.isEmpty()) {
            this.plugin.sendMessage(
                player,
                "event_none_selected",
                new String[0]
            );
        } else {
            Events event = (Events) eventOpt.get();
            boolean open = state.equalsIgnoreCase("open");
            event.setOpenSign(open);
            if (open) {
                this.plugin.sendMessage(
                    player,
                    "event_signs_open",
                    new String[] { "{event}", event.getDisplayName() }
                );
            } else {
                this.plugin.sendMessage(
                    player,
                    "event_signs_closed",
                    new String[] { "{event}", event.getDisplayName() }
                );
            }

            this.showEventInfo(player, event);
        }
    }

    private void selectEventForPlayer(
        Player player,
        Events event,
        boolean announceSelectedMessage
    ) {
        if (player == null || event == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        this.database.setPlayerSelectedEvent(playerId, event);

        boolean selected = this.database.getPlayerSelectedEvent(playerId)
            .map(e -> e.getId() == event.getId())
            .orElse(false);

        if (!selected) {
            this.database.setPlayerSelectedEvent(playerId, event);
            selected = this.database.getPlayerSelectedEvent(playerId)
                .map(e -> e.getId() == event.getId())
                .orElse(false);
        }

        if (selected) {
            if (announceSelectedMessage) {
                this.plugin.sendMessage(
                    player,
                    "event_selected",
                    new String[] { "{event}", event.getDisplayName() }
                );
            }
        } else {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[EventCommand] Falha ao persistir selected_event_id para player " +
                    player.getName() +
                    " (eventId=" +
                    event.getId() +
                    ", event=" +
                    event.getDisplayName() +
                    ")"
            );
            player.sendMessage(
                String.valueOf(ChatColor.YELLOW) +
                    "⚠ Evento exibido, mas não foi possível confirmar a seleção automática."
            );
        }
    }

    private void showEventInfo(Player player, Events event) {
        boolean isAdmin = player.hasPermission("formularacing.event.admin");
        player.sendMessage("");
        TextComponent header = new TextComponent("");
        header.addExtra(
            ClickableMessageUtil.getRefreshButton(
                "/event info " + event.getDisplayName(),
                "Atualizar"
            )
        );
        header.addExtra(new TextComponent(" "));
        TextComponent title = new TextComponent(
            event.getDisplayName().toUpperCase()
        );
        title.setColor(ChatColor.GOLD);
        title.setBold(true);
        header.addExtra(title);
        String var10003 = String.valueOf(ChatColor.GRAY);
        header.addExtra(
            new TextComponent(var10003 + " (" + event.getState().name() + ")")
        );
        var10003 = String.valueOf(ChatColor.DARK_GRAY);
        header.addExtra(
            new TextComponent(var10003 + " (#" + event.getId() + ")")
        );
        player.spigot().sendMessage(header);
        TextComponent trackRow = new TextComponent(
            String.valueOf(ChatColor.YELLOW) + "  Pista: "
        );
        String trackName = event.getTrackNameWS();
        if (trackName != null) {
            trackRow.addExtra(
                ClickableMessageUtil.getButton(
                    trackName,
                    ChatColor.WHITE,
                    "/track info " + trackName,
                    "Ver Pista",
                    Action.RUN_COMMAND
                )
            );
        } else {
            trackRow.addExtra(
                new TextComponent(
                    String.valueOf(ChatColor.RED) + "Não definida"
                )
            );
        }

        if (isAdmin) {
            trackRow.addExtra(new TextComponent(" "));
            trackRow.addExtra(
                ClickableMessageUtil.getEditButton(
                    "[Alterar]",
                    "/event settrack " + event.getDisplayName() + " ",
                    "Definir Pista"
                )
            );
        }

        player.spigot().sendMessage(trackRow);
        TextComponent signsRow = new TextComponent(
            String.valueOf(ChatColor.YELLOW) + "  Inscrições: "
        );
        if (isAdmin) {
            if (event.isOpenSign()) {
                signsRow.addExtra(
                    ClickableMessageUtil.getToggleButton(
                        "ABERTO",
                        ChatColor.GREEN,
                        "/event set signs closed",
                        "Clique para FECHAR"
                    )
                );
            } else {
                signsRow.addExtra(
                    ClickableMessageUtil.getToggleButton(
                        "FECHADO",
                        ChatColor.RED,
                        "/event set signs open",
                        "Clique para ABRIR"
                    )
                );
            }
        } else {
            signsRow.addExtra(
                new TextComponent(
                    event.isOpenSign()
                        ? String.valueOf(ChatColor.GREEN) + "ABERTO"
                        : String.valueOf(ChatColor.RED) + "FECHADO"
                )
            );
        }

        player.spigot().sendMessage(signsRow);
        TextComponent driversRow = new TextComponent(
            String.valueOf(ChatColor.YELLOW) + "  Pilotos: "
        );
        var10003 = String.valueOf(ChatColor.WHITE);
        driversRow.addExtra(
            new TextComponent(
                var10003 + event.getSubscriberCount() + " inscritos "
            )
        );
        if (event.getReserveCount() > 0) {
            var10003 = String.valueOf(ChatColor.GRAY);
            driversRow.addExtra(
                new TextComponent(
                    var10003 + "(+" + event.getReserveCount() + " reservas) "
                )
            );
        }

        driversRow.addExtra(
            ClickableMessageUtil.getButton(
                "Ver Lista",
                ChatColor.AQUA,
                "/event signs " + event.getDisplayName(),
                "Ver lista completa",
                Action.RUN_COMMAND
            )
        );
        player.spigot().sendMessage(driversRow);
        player.sendMessage("");
        String var10002 = String.valueOf(ChatColor.GOLD);
        TextComponent roundsHeader = new TextComponent(
            var10002 + String.valueOf(ChatColor.BOLD) + "  ROUNDS:"
        );
        if (isAdmin) {
            roundsHeader.addExtra(new TextComponent("   "));
            roundsHeader.addExtra(
                ClickableMessageUtil.getButton(
                    "+ Novo Round",
                    ChatColor.GREEN,
                    "/round create ",
                    "Criar novo round",
                    Action.SUGGEST_COMMAND
                )
            );
        }

        player.spigot().sendMessage(roundsHeader);
        List<Rounds> rounds = event.getSchedule().getRoundsOrdered();
        if (rounds.isEmpty()) {
            player.sendMessage(
                String.valueOf(ChatColor.GRAY) + "   (Nenhum round configurado)"
            );
        } else {
            for (Rounds round : rounds) {
                String roundName = "R" + round.getRoundNumber();
                boolean isCurrent = (Boolean) event
                    .getSchedule()
                    .getCurrentRound()
                    .map(r -> r.getId() == round.getId())
                    .orElse(false);
                TextComponent line = new TextComponent(
                    isCurrent
                        ? String.valueOf(ChatColor.YELLOW) + "  ➤ "
                        : "    "
                );
                TextComponent nameComp = new TextComponent(
                    round.getDisplayName()
                );
                nameComp.setColor(isCurrent ? ChatColor.AQUA : ChatColor.GRAY);
                line.addExtra(nameComp);
                line.addExtra(
                    new TextComponent(
                        String.valueOf(ChatColor.DARK_GRAY) + " - "
                    )
                );
                var10003 = String.valueOf(ChatColor.WHITE);
                line.addExtra(
                    new TextComponent(var10003 + round.getState().name())
                );
                line.addExtra(new TextComponent("   "));
                line.addExtra(
                    ClickableMessageUtil.getButton(
                        "Info",
                        ChatColor.BLUE,
                        "/round info " + round.getId(),
                        "Ver detalhes",
                        Action.RUN_COMMAND
                    )
                );
                player.spigot().sendMessage(line);
                if (isAdmin) {
                    for (Heats heat : round.getHeats().values()) {
                        var10002 = String.valueOf(ChatColor.GRAY);
                        TextComponent heatLine = new TextComponent(
                            var10002 + "    ↳ " + heat.getName()
                        );
                        int var10000 = round.getRoundIndex();
                        String heatRef =
                            "R" +
                            var10000 +
                            (round.getType() == RoundType.QUALIFICATION
                                ? "Q"
                                : "F") +
                            heat.getHeatNumber();
                        heatLine.addExtra(new TextComponent("  "));
                        TextComponent loadBtn = new TextComponent("[L]");
                        loadBtn.setColor(ChatColor.YELLOW);
                        loadBtn.setHoverEvent(
                            new HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new Content[] {
                                    new Text("§eCarregar Heat (Grid)"),
                                }
                            )
                        );
                        loadBtn.setClickEvent(
                            new ClickEvent(
                                Action.RUN_COMMAND,
                                "/heat load " + heatRef
                            )
                        );
                        heatLine.addExtra(loadBtn);
                        heatLine.addExtra(new TextComponent(" "));
                        TextComponent startHeatBtn = new TextComponent("[▶]");
                        startHeatBtn.setColor(ChatColor.GREEN);
                        startHeatBtn.setHoverEvent(
                            new HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new Content[] { new Text("§aIniciar Corrida") }
                            )
                        );
                        startHeatBtn.setClickEvent(
                            new ClickEvent(
                                Action.RUN_COMMAND,
                                "/heat start " + heatRef
                            )
                        );
                        heatLine.addExtra(startHeatBtn);
                        heatLine.addExtra(new TextComponent(" "));
                        TextComponent infoHeatBtn = new TextComponent("[>>]");
                        infoHeatBtn.setColor(ChatColor.GOLD);
                        infoHeatBtn.setHoverEvent(
                            new HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new Content[] {
                                    new Text("§6Ver Resultados/Análise"),
                                }
                            )
                        );
                        infoHeatBtn.setClickEvent(
                            new ClickEvent(
                                Action.RUN_COMMAND,
                                "/heat info " + heatRef
                            )
                        );
                        heatLine.addExtra(infoHeatBtn);
                        heatLine.addExtra(new TextComponent(" "));
                        TextComponent removeHeatBtn = new TextComponent("[X]");
                        removeHeatBtn.setColor(ChatColor.DARK_RED);
                        removeHeatBtn.setHoverEvent(
                            new HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new Content[] { new Text("§4Remover Heat") }
                            )
                        );
                        removeHeatBtn.setClickEvent(
                            new ClickEvent(
                                Action.RUN_COMMAND,
                                "/heat remove " + heatRef
                            )
                        );
                        heatLine.addExtra(removeHeatBtn);
                        player.spigot().sendMessage(heatLine);
                    }
                }
            }
        }

        if (isAdmin) {
            player.sendMessage("");
            TextComponent actions = new TextComponent("  AÇÕES: ");
            actions.setColor(ChatColor.GOLD);
            actions.setBold(true);
            TextComponent addRoundBtn = new TextComponent("[+ Round]");
            addRoundBtn.setColor(ChatColor.GREEN);
            addRoundBtn.setHoverEvent(
                new HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new Content[] { new Text("§aAdicionar novo round") }
                )
            );
            addRoundBtn.setClickEvent(
                new ClickEvent(Action.SUGGEST_COMMAND, "/round create ")
            );
            actions.addExtra(addRoundBtn);
            actions.addExtra(new TextComponent("  "));
            TextComponent broadcastBtn = new TextComponent("[Broadcast]");
            broadcastBtn.setColor(ChatColor.YELLOW);
            broadcastBtn.setHoverEvent(
                new HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new Content[] { new Text("§eAnunciar inscrições") }
                )
            );
            broadcastBtn.setClickEvent(
                new ClickEvent(
                    Action.RUN_COMMAND,
                    "/event broadcast clicktosign " + event.getDisplayName()
                )
            );
            actions.addExtra(broadcastBtn);
            actions.addExtra(new TextComponent("  "));
            TextComponent deleteBtn = new TextComponent("[Excluir Evento]");
            deleteBtn.setColor(ChatColor.RED);
            deleteBtn.setHoverEvent(
                new HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new Content[] {
                        new Text("§cRemover este evento permanentemente"),
                    }
                )
            );
            deleteBtn.setClickEvent(
                new ClickEvent(
                    Action.RUN_COMMAND,
                    "/event delete " + event.getDisplayName()
                )
            );
            actions.addExtra(deleteBtn);
            player.spigot().sendMessage(actions);
        }

        String var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(
            var10001 +
                String.valueOf(ChatColor.BOLD) +
                "═══════════════════════════════"
        );
    }
}
