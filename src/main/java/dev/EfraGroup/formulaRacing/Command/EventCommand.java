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
import dev.EfraGroup.formulaRacing.Utils.SenderUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
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
    public void onDefault(CommandSender sender) {
        Optional<Events> eventOpt = this.selectedEvent(sender);
        if (eventOpt.isPresent()) {
            this.onInfo(sender, (Events) eventOpt.get());
        } else {
            this.plugin.sendMessage(
                sender,
                "event_none_selected_hint",
                new String[0]
            );
        }
    }

    @Subcommand("list|ls")
    @Description("Lista eventos (por padrão em setup/running). Flags: -finished, -event <nome>, -track <pista>")
    public void onList(CommandSender sender, String[] args) {
        ListFilter filter = ListFilter.parse(args);

        List<Events> events = this.eventManager.getAllEvents()
            .stream()
            .filter(e -> filter.includeFinished
                ? e.getState() == EventState.SETUP
                    || e.getState() == EventState.RUNNING
                    || e.getState() == EventState.FINISHED
                : e.isActive())
            .filter(e -> filter.eventName == null
                || (e.getDisplayName() != null
                    && e.getDisplayName().toLowerCase().contains(filter.eventName.toLowerCase())))
            .filter(e -> filter.trackName == null
                || (e.getTrackNameWS() != null
                    && e.getTrackNameWS().equalsIgnoreCase(filter.trackName)))
            .sorted(Comparator.comparingLong(Events::getDate))
            .toList();

        sender.sendMessage("");
        sender.sendMessage("§8§m                              §r §6§lEVENTOS §8§m                              ");
        sender.sendMessage("");

        if (events.isEmpty()) {
            sender.sendMessage("  §7Nenhum evento encontrado com esses filtros.");
            if (filter.hasAnyFilter()) {
                sender.sendMessage("  §8Filtros: " + filter.describe());
            }
            sender.sendMessage("");
            return;
        }

        sender.sendMessage("  §8Total: §f" + events.size() + " §8evento" + (events.size() > 1 ? "s" : ""));
        if (filter.hasAnyFilter()) {
            sender.sendMessage("  §8Filtros: §7" + filter.describe());
        }
        sender.sendMessage("");

        for (Events event : events) {
            String creatorName = event.getCreatorName();
            String dateStr = ApiUtilities.formatDate(event.getDate());
            String stateName = event.getState().name();

            // Cor baseada no estado
            String stateColor;
            switch (event.getState()) {
                case SETUP: stateColor = "§e"; break;    // Amarelo
                case RUNNING: stateColor = "§a"; break;   // Verde
                case FINISHED: stateColor = "§7"; break; // Cinza
                default: stateColor = "§f"; break;       // Branco
            }

            TextComponent message = new TextComponent("  §8• ");

            // Nome do evento (clicável)
            TextComponent nameBtn = new TextComponent(event.getDisplayName());
            nameBtn.setColor(ChatColor.AQUA);
            nameBtn.setBold(true);
            nameBtn.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/event info " + event.getDisplayName()));
            nameBtn.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new Content[] { new Text("§aClique para ver detalhes do evento") }));
            message.addExtra(nameBtn);

            // Estado
            message.addExtra(new TextComponent(" " + stateColor + "[" + stateName + "]"));

            // Pista (se houver)
            if (event.getTrackNameWS() != null && !event.getTrackNameWS().isEmpty()) {
                message.addExtra(new TextComponent(" §8| §b" + event.getTrackNameWS()));
            }

            // Data e criador
            message.addExtra(new TextComponent(" §8| §7" + dateStr));
            message.addExtra(new TextComponent(" §8por §f" + creatorName));

            sender.spigot().sendMessage(message);
        }

        sender.sendMessage("");
        sender.sendMessage("§8§m                                                                     ");
    }

    /** Parsed filter flags for {@code /event list}. */
    private static final class ListFilter {
        private boolean includeFinished;
        private String eventName;
        private String trackName;

        static ListFilter parse(String[] args) {
            ListFilter f = new ListFilter();
            if (args == null) {
                return f;
            }
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a == null) {
                    continue;
                }
                switch (a.toLowerCase()) {
                    case "-finished", "--finished", "-fin", "--fin" -> f.includeFinished = true;
                    case "-event", "--event" -> {
                        if (i + 1 < args.length) {
                            f.eventName = args[++i];
                        }
                    }
                    case "-track", "--track", "-pista", "--pista" -> {
                        if (i + 1 < args.length) {
                            f.trackName = args[++i];
                        }
                    }
                    default -> {
                        // Ignora tokens desconhecidos para não quebrar o comando.
                    }
                }
            }
            return f;
        }

        boolean hasAnyFilter() {
            return this.includeFinished || this.eventName != null || this.trackName != null;
        }

        String describe() {
            StringBuilder sb = new StringBuilder();
            if (this.eventName != null) {
                sb.append("evento contém '").append(this.eventName).append("'");
            }
            if (this.trackName != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("pista = '").append(this.trackName).append("'");
            }
            if (this.includeFinished) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("inclui finalizados");
            }
            return sb.toString();
        }
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
        CommandSender sender,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = this.selectedEvent(sender).orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                sender,
                "event_none_selected",
                new String[0]
            );
        } else {
            this.selectEventForPlayer(sender, event, false);
            this.showEventInfo(sender, event);
        }
    }

    @Subcommand("create|new")
    @CommandPermission("formularacing.event.admin")
    @Description("Cria um novo evento vazio")
    @CommandCompletion("@nothing @tracks")
    public void onCreate(CommandSender sender, String name, String trackNameWS) {
        this.eventManager.createEvent(
            this.ownerUuid(sender),
            name,
            trackNameWS
        ).thenAccept(event -> {
            SchedulerHelper.runTask(this.plugin, () -> {
                    if (event != null) {
                        this.plugin.sendMessage(
                            sender,
                            "event_created",
                            new String[] { "{event}", name }
                        );
                        this.selectEventForPlayer(sender, event, false);
                    } else {
                        this.plugin.sendMessage(
                            sender,
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
        CommandSender sender,
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
                sender.sendMessage(
                    "§e⚠️ A pista '" +
                        track +
                        "' não é um circuito fechado (Sprint/Parkour)."
                );
                sender.sendMessage(
                    "§e⚠️ Voltas ajustadas para 1 e Pit Stops desativados."
                );
            }
        }

        this.eventManager.createFullEvent(
            this.ownerUuid(sender),
            name,
            track,
            practiceTime,
            qualLaps,
            qualTime,
            finalLaps,
            pits
        ).thenAccept(event -> {
            SchedulerHelper.runTask(this.plugin, () -> {
                    if (event != null) {
                        this.plugin.sendMessage(
                            sender,
                            "event_full_created",
                            new String[] { "{event}", name }
                        );
                        this.selectEventForPlayer(sender, event, false);
                    } else {
                        this.plugin.sendMessage(
                            sender,
                            "event_full_error",
                            new String[0]
                        );
                    }
                });
        });
    }

    @Subcommand("clone")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@event @nothing")
    @Description("Clona a estrutura de um evento (rounds + heats + config)")
    public void onClone(CommandSender sender, Events source, String newName) {
        if (source == null) {
            this.plugin.sendMessage(sender, "event_none_selected", new String[0]);
            return;
        }
        if (newName == null || newName.trim().isEmpty()) {
            this.plugin.sendMessage(sender, "event_create_error", new String[0]);
            return;
        }
        String trimmed = newName.trim();
        if (trimmed.equalsIgnoreCase("QuickRace") || trimmed.equalsIgnoreCase("RaceLobby")) {
            this.plugin.sendMessage(sender, "event_create_error", new String[0]);
            return;
        }

        this.eventManager.cloneEvent(source, trimmed, this.ownerUuid(sender))
            .thenAccept(cloned -> {
                SchedulerHelper.runTask(this.plugin, () -> {
                    if (cloned != null) {
                        this.plugin.sendMessage(
                            sender,
                            "event_created",
                            new String[] { "{event}", trimmed }
                        );
                        this.selectEventForPlayer(sender, cloned, false);
                    } else {
                        this.plugin.sendMessage(
                            sender,
                            "event_create_error",
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
        CommandSender sender,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            this.plugin.sendMessage(
                sender,
                "event_none_selected",
                new String[0]
            );
            return;
        }

        if (this.eventManager.removeEvent(event.getId())) {
            String var10001 = String.valueOf(ChatColor.GREEN);
            sender.sendMessage(
                var10001 +
                    "✓ Evento removido com sucesso: " +
                    String.valueOf(ChatColor.GOLD) +
                    event.getDisplayName()
            );
        } else {
            this.plugin.sendMessage(
                sender,
                "event_delete_error",
                new String[0]
            );
        }
    }

    @Subcommand("finish|end|stop")
    @CommandCompletion("@event")
    @CommandPermission("formularacing.event.admin")
    @Description("Finaliza um evento")
    public void onFinish(
        CommandSender sender,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = this.selectedEvent(sender).orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                sender,
                "event_none_selected",
                new String[0]
            );
        } else {
            if (event.finish()) {
                this.plugin.sendMessage(
                    sender,
                    "event_finished",
                    new String[0]
                );
            } else {
                this.plugin.sendMessage(
                    sender,
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
            event = this.selectedEvent(player).orElse(null);
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
            case OBU_REQUIRED:
            case MC_VERSION_MISMATCH:
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
            event = this.selectedEvent(player).orElse(null);
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
            event = this.selectedEvent(player).orElse(null);
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
        CommandSender sender,
        @co.aikar.commands.annotation.Optional Events event
    ) {
        if (event == null) {
            event = this.selectedEvent(sender).orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                sender,
                "event_none_selected",
                new String[0]
            );
        } else if (event.getState() == EventState.FINISHED) {
            sender.sendMessage(
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

            String lang = this.lang(sender);
            this.plugin.sendMessage(
                sender,
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
            event = this.selectedEvent(player).orElse(null);
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
        CommandSender sender,
        @co.aikar.commands.annotation.Optional Events event,
        String[] trackArgs
    ) {
        if (event == null) {
            event = this.selectedEvent(sender).orElse(null);
        }

        if (event == null) {
            this.plugin.sendMessage(
                sender,
                "event_none_selected",
                new String[0]
            );
        } else if (trackArgs.length == 0) {
            this.plugin.sendMessage(
                sender,
                "event_usage_settrack",
                new String[0]
            );
        } else {
            String trackName = String.join(" ", trackArgs);
            if (event.setTrack(trackName)) {
                this.plugin.sendMessage(
                    sender,
                    "event_track_set",
                    new String[] { "{track}", trackName }
                );
            } else {
                this.plugin.sendMessage(
                    sender,
                    "event_create_error",
                    new String[0]
                );
            }
        }
    }

    @Subcommand("set signs")
    @CommandCompletion("open|closed")
    @CommandPermission("formularacing.event.admin")
    public void onSetSigns(CommandSender sender, String state) {
        Optional<Events> eventOpt = this.selectedEvent(sender);
        if (eventOpt.isEmpty()) {
            this.plugin.sendMessage(
                sender,
                "event_none_selected",
                new String[0]
            );
        } else {
            Events event = (Events) eventOpt.get();
            boolean open = state.equalsIgnoreCase("open");
            event.setOpenSign(open);
            if (open) {
                this.plugin.sendMessage(
                    sender,
                    "event_signs_open",
                    new String[] { "{event}", event.getDisplayName() }
                );
            } else {
                this.plugin.sendMessage(
                    sender,
                    "event_signs_closed",
                    new String[] { "{event}", event.getDisplayName() }
                );
            }

            this.showEventInfo(sender, event);
        }
    }

    /** Event selected by the player, or empty for the console. */
    private Optional<Events> selectedEvent(CommandSender sender) {
        Player player = SenderUtils.player(sender);
        return player != null
            ? this.database.getPlayerSelectedEvent(player.getUniqueId())
            : Optional.empty();
    }

    /** Owner UUID for an event created from the console. */
    private UUID ownerUuid(CommandSender sender) {
        Player player = SenderUtils.player(sender);
        return player != null ? player.getUniqueId() : new UUID(0L, 0L);
    }

    /** Player's language when available, default language for the console. */
    private String lang(CommandSender sender) {
        Player player = SenderUtils.player(sender);
        return player != null ? this.database.getPlayerLanguage(player.getUniqueId()) : "en_US";
    }

    private void selectEventForPlayer(
        CommandSender sender,
        Events event,
        boolean announceSelectedMessage
    ) {
        Player player = SenderUtils.player(sender);
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
                    sender,
                    "event_selected",
                    new String[] { "{event}", event.getDisplayName() }
                );
            }
        } else {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[EventCommand] Falha ao persistir selected_event_id para player " +
                    sender.getName() +
                    " (eventId=" +
                    event.getId() +
                    ", event=" +
                    event.getDisplayName() +
                    ")"
            );
            sender.sendMessage(
                String.valueOf(ChatColor.YELLOW) +
                    "⚠ Evento exibido, mas não foi possível confirmar a seleção automática."
            );
        }
    }

    private void showEventInfo(CommandSender sender, Events event) {
        boolean isAdmin = sender.hasPermission("formularacing.event.admin");
        sender.sendMessage("");
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
        sender.spigot().sendMessage(header);
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

        sender.spigot().sendMessage(trackRow);
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

        sender.spigot().sendMessage(signsRow);
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
        sender.spigot().sendMessage(driversRow);
        sender.sendMessage("");
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

        sender.spigot().sendMessage(roundsHeader);
        List<Rounds> rounds = event.getSchedule().getRoundsOrdered();
        if (rounds.isEmpty()) {
            sender.sendMessage(
                String.valueOf(ChatColor.GRAY) + "   (Nenhum round configurado)"
            );
        } else {
            String eventStructureColor = String.valueOf(ChatColor.GREEN);
            TextComponent eventStructure = new TextComponent(eventStructureColor + String.valueOf(ChatColor.BOLD));
            eventStructure.addExtra("EVENT STRUCTURE: " + rounds.size() + " rounds");
            sender.spigot().sendMessage(eventStructure);
            for (Rounds round : rounds) {
                int roundNumber = round.getRoundNumber();
                String roundTypeLabel = this.roundTypeLabel(round.getType());
                String roundTitle = "Round " + roundNumber + " \u2013 " + roundTypeLabel;
                TextComponent roundLine = new TextComponent("");
                roundLine.addExtra("|");
                roundLine.addExtra(" ");
                TextComponent roundTitlePart = new TextComponent(roundTitle);
                roundTitlePart.setColor(ChatColor.GREEN);
                roundTitlePart.setBold(false);
                roundLine.addExtra(roundTitlePart);
                roundLine.addExtra(":");
                sender.spigot().sendMessage(roundLine);
                for (Heats heat : round.getHeats().values()) {
                    String heatRef = this.heatRef(round, heat);
                TextComponent heatLine = new TextComponent("");
                    heatLine.addExtra("  ");
                    TextComponent heatRefComp = new TextComponent(heatRef);
                    heatRefComp.setColor(ChatColor.GRAY);
                    heatRefComp.setItalic(true);
                    heatLine.addExtra(heatRefComp);

                    sender.spigot().sendMessage(heatLine);
                }
            }
        }
        String footerColor = String.valueOf(ChatColor.GOLD);
        sender.sendMessage(
            footerColor +
                String.valueOf(ChatColor.BOLD) +
                "═══════════════════════════════"
        );
    }


    private String roundTypeLabel(RoundType type) {
        if (type == RoundType.QUALIFICATION) {
            return "Qualification";
        }
        if (type == RoundType.SPRINT_QUALIFICATION) {
            return "Sprint Qualification";
        }
        if (type == RoundType.SPRINT_RACE) {
            return "Sprint Race";
        }
        return "Practice";
    }


    private String heatRef(Rounds round, Heats heat) {
        int roundIndex = round.getRoundIndex();
        int heatNumber = heat.getHeatNumber();
        if (round.getType() == RoundType.QUALIFICATION) {
            return "R" + roundIndex + "Q" + heatNumber;
        }
        if (round.getType() == RoundType.SPRINT_QUALIFICATION) {
            return "R" + roundIndex + "SQ" + heatNumber;
        }
        if (round.getType() == RoundType.SPRINT_RACE) {
            return "R" + roundIndex + "S" + heatNumber;
        }
        return "R" + roundIndex + "P" + heatNumber;
    }
}
