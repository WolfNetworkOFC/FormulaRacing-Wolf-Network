package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.EventsManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class EventCommandHandler implements CommandExecutor {

    private final EventsManager eventManager;
    private final FormulaRacing plugin;


    public EventCommandHandler(EventsManager eventManager, DatabaseManager dm, FormulaRacing plugin) {
        this.eventManager = eventManager;
        this.plugin = plugin;

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }


        if (args.length < 1) {
            player.sendMessage("§cUso: /event <create/select/set/delete/sign/reserve/signs>");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                return handleCreate(player, args);

            case "select":
                return handleSelect(player, args);

            case "set":
                if (args.length >= 2 && args[1].equalsIgnoreCase("track")) {
                    return handleSetTrack(player, args);
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("signs")) {
                    return handleSetSigns(player, args);
                }
                player.sendMessage("§cUso correto: /event set track <trackname> [eventname]");
                return true;

            case "broadcast":
                if (args.length < 3) {
                    player.sendMessage("§cUso correto: /event broadcast <clicktosign/clicktoreserve> <eventname>");
                    return true;
                }

                String action = args[1].toLowerCase();
                String eventName = args[2];

                switch (action) {
                    case "clicktosign":
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            handleClickToSign(p, eventName);
                        }
                        player.sendMessage("§aMensagem click-to-sign enviada para todos!");
                        return true;

                    case "clicktoreserve":
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            handleClickToReserve(p, eventName);
                        }
                        player.sendMessage("§aMensagem click-to-reserve enviada para todos!");
                        return true;

                    default:
                        player.sendMessage("§cAção inválida: " + action);
                        return true;
                }


            case "delete":
                return handleDelete(player, args);

            case "sign":
                return handleSign(player, args);

            case "reserve":
                return handleReserve(player, args);

            case "signs":
                return handleSigns(player, args);

            case "countdown":
                return handleCountdown(player, args);



            default:
                player.sendMessage("§cUso: /event <create/select/set/delete/sign/reserve/signs>");
                return true;
        }
    }

    public boolean handleCountdown(CommandSender sender, String[] args) {

        if (args.length < 3) {
            sender.sendMessage("§cUso correto: /event countdown <h/m/s> <tempo> <texto> [eventName]");
            return true;
        }

        // Tipo de tempo
        String type = args[1].toLowerCase();
        int time;
        try {
            time = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cTempo inválido: " + args[2]);
            return true;
        }

        // Texto do countdown
        StringBuilder textBuilder = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            textBuilder.append(args[i]).append(" ");
        }
        String countdownText = textBuilder.toString().trim();

        // TODO: se quiser pegar eventName separado, pode ajustar args

        long totalSeconds = switch (type) {
            case "h" -> TimeUnit.HOURS.toSeconds(time);
            case "m" -> TimeUnit.MINUTES.toSeconds(time);
            case "s" -> time;
            default -> {
                sender.sendMessage("§cTipo inválido! Use h/m/s");
                yield -1;
            }
        };

        if (totalSeconds <= 0) {
            sender.sendMessage("§cTempo inválido!");
            return true;
        }

        // Criar a BossBar
        BossBar bossBar = Bukkit.createBossBar(countdownText + " : " + totalSeconds + "s", BarColor.GREEN, BarStyle.SOLID);
        bossBar.setVisible(true);

        // Adicionar todos os jogadores
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f); // som inicial
        }

        // Task para atualizar a bossbar
        new BukkitRunnable() {
            long remaining = totalSeconds;

            @Override
            public void run() {

                if (remaining <= 0) {
                    bossBar.removeAll();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f); // som inicial
                    }
                    cancel();
                    return;
                }

                // Atualizar título
                bossBar.setTitle(countdownText + " : " + remaining + "s");

                // Atualizar progresso
                bossBar.setProgress((double) remaining / totalSeconds);

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // roda a cada 20 ticks = 1 segundo

        sender.sendMessage("§aCountdown iniciado: " + countdownText + " (" + totalSeconds + "s)");
        return true;
    }

    // -------------------------------------------------------------
    // /event create <nome> [pista]
    // -------------------------------------------------------------

    private boolean handleCreate(Player player, String[] args) {

        if (args.length < 2) {
            player.sendMessage("§cUso correto: /event create <nomeDoEvento> [pista]");
            return true;
        }

        String eventName = args[1];
        String trackName = (args.length >= 3) ? args[2] : null;


        eventManager.createEvent(player.getUniqueId(), eventName, trackName);
        eventManager.setSelectedEvent(player.getUniqueId(), trackName);

        player.sendMessage("§aEvento criado com sucesso!");
        player.sendMessage("§7Nome: §f" + eventName);

        if (trackName != null) {
            player.sendMessage("§7Pista: §f" + trackName);
        } else {
            player.sendMessage("§7Pista: §cNenhuma (adicione depois).");
        }

        return true;
    }

    // -------------------------------------------------------------
    // /event select <nome>
    // -------------------------------------------------------------

    private boolean handleSelect(Player player, String[] args) {

        if (args.length < 2) {
            player.sendMessage("§cUso correto: /event select <nomeDoEvento>");
            return true;
        }

        String eventName = args[1];

        if (!eventManager.getIfEventExistsByName(eventName)) {
            player.sendMessage("§cEsse evento não existe: §f" + eventName);
            return true;
        }

        eventManager.setSelectedEvent(player.getUniqueId(), eventName);

        player.sendMessage("§aVocê selecionou o evento: §f" + eventName);
        return true;
    }

    public boolean handleClickToSign(Player player, String eventName) {

        if (!eventManager.getIfEventExistsByName(eventName)) return false;

        UUID playerUUID = player.getUniqueId();
        Set<UUID> subscribers = eventManager.getSubscribers(eventName);
        Set<UUID> reserves = eventManager.getReserves(eventName);

        // Não envia se o jogador já estiver inscrito ou em reserve
        if (subscribers.contains(playerUUID) || reserves.contains(playerUUID)) return false;

        player.sendMessage(" "); // linha vazia acima

        TextComponent clickMessage = new TextComponent("§a--> Click to sign up to " + eventName + " <--");
        clickMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/event sign " + eventName));
        clickMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§b--> Sign up for §l" + eventName + "§r§b<--").create()));

        player.spigot().sendMessage(clickMessage);

        player.sendMessage(" "); // linha vazia abaixo
        return false;
    }

    // Mensagem clicável para entrar na reserva (/event reserve)
    public boolean handleClickToReserve(Player player, String eventName) {

        if (!eventManager.getIfEventExistsByName(eventName)) return false;

        UUID playerUUID = player.getUniqueId();
        Set<UUID> subscribers = eventManager.getSubscribers(eventName);
        Set<UUID> reserves = eventManager.getReserves(eventName);

        // Não envia se o jogador já estiver inscrito ou já estiver em reserve
        if (subscribers.contains(playerUUID) || reserves.contains(playerUUID)) return false;

        player.sendMessage(" "); // linha vazia acima

        TextComponent clickMessage = new TextComponent("§e--> Click to reserve a spot for " + eventName + " <--");
        clickMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/event reserve " + eventName));
        clickMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§b--> Reserve for §l" + eventName + "§r§b<--").create()));

        player.spigot().sendMessage(clickMessage);

        player.sendMessage(" "); // linha vazia abaixo
        return false;
    }

    // -------------------------------------------------------------
// /event set signs <true|false> [eventname]
// -------------------------------------------------------------
    private boolean handleSetSigns(Player player, String[] args) {

        if (args.length < 3) {
            player.sendMessage("§cUso correto: /event set signs <true|false> [eventname]");
            return true;
        }

        String value = args[2].toLowerCase();
        boolean openSign;

        if (value.equals("true") || value.equals("1")) {
            openSign = true;
        } else if (value.equals("false") || value.equals("0")) {
            openSign = false;
        } else {
            player.sendMessage("§cValor inválido: use true ou false");
            return true;
        }

        String eventName;
        if (args.length >= 4) {
            eventName = args[3];
        } else {
            // Pegar o evento selecionado pelo jogador
            eventName = eventManager.getSelectedEvent(player.getUniqueId());
            if (eventName == null) {
                player.sendMessage("§cVocê não selecionou nenhum evento.");
                player.sendMessage("§7Use: §f/event select <eventname>");
                return true;
            }
        }

        if (!eventManager.getIfEventExistsByName(eventName)) {
            player.sendMessage("§cEsse evento não existe: §f" + eventName);
            return true;
        }

        eventManager.setEventOpenSign(eventName, openSign);

        player.sendMessage("§aInscrições do evento §f" + eventName + " §aforam " + (openSign ? "ativadas" : "fechadas") + ".");
        return true;
    }



    // -------------------------------------------------------------
    // /event set track <trackname> [eventname]
    // -------------------------------------------------------------

    private boolean handleSetTrack(Player player, String[] args) {

        if (args.length < 3) {
            player.sendMessage("§cUso correto: /event set track <trackname> [eventname]");
            return true;
        }

        String trackName = args[2];
        String eventName;

        // Se passou o nome do evento:
        if (args.length >= 4) {
            eventName = args[3];
        } else {
            // Pegar o selectedEvent do jogador
            eventName = eventManager.getSelectedEvent(player.getUniqueId());

            if (eventName == null) {
                player.sendMessage("§cVocê não selecionou nenhum evento.");
                player.sendMessage("§7Use: §f/event select <eventname>");
                return true;
            }
        }

        // Verificar se o evento existe
        if (!eventManager.getIfEventExistsByName(eventName)) {
            player.sendMessage("§cEsse evento não existe: §f" + eventName);
            return true;
        }

        int id = eventManager.getEventIDByName(eventName);
        eventManager.setEventTrack(id, trackName);

        player.sendMessage("§aPista do evento atualizada!");
        player.sendMessage("§7Evento: §f" + eventName);
        player.sendMessage("§7Nova pista: §f" + trackName);

        return true;
    }
    // -------------------------------------------------------------
// /event delete <eventname>
// -------------------------------------------------------------
    private boolean handleDelete(Player player, String[] args) {

        String eventName;

        if (args.length >= 2) {
            eventName = args[1];
        } else {
            // Tentar usar o evento selecionado do jogador
            eventName = eventManager.getSelectedEvent(player.getUniqueId());

            if (eventName == null) {
                player.sendMessage("§cVocê não selecionou nenhum evento.");
                player.sendMessage("§7Use: §f/event select <eventname>");
                return true;
            }
        }

        // Verificar se o evento existe
        if (!eventManager.getIfEventExistsByName(eventName)) {
            player.sendMessage("§cEsse evento não existe: §f" + eventName);
            return true;
        }

        // Deletar o evento do banco de dados
        boolean deleted = eventManager.deleteEventByName(eventName);
        if (deleted) {
            player.sendMessage("§aEvento deletado com sucesso: §f" + eventName);
        } else {
            player.sendMessage("§cErro ao deletar o evento: §f" + eventName);
        }

        return true;
    }

    private boolean handleSign(Player player, String[] args) {

        if (args.length < 2) {
            player.sendMessage("§cUso correto: /event sign <eventname> [playername]");
            return true;
        }

        String eventName = args[1];
        Player targetPlayer = player; // default: quem executou

        if (args.length >= 3) {
            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null) {
                player.sendMessage("§cJogador não encontrado: §f" + args[2]);
                return true;
            }
        }

        // Verificar se o evento existe
        if (!eventManager.getIfEventExistsByName(eventName)) {
            player.sendMessage("§cEsse evento não existe: §f" + eventName);
            return true;
        }

        UUID targetUUID = targetPlayer.getUniqueId();
        Set<UUID> subscribers = eventManager.getSubscribers(eventName);

        boolean actionAdded;
        if (subscribers.contains(targetUUID)) {
            // Já está inscrito → remover (sempre permitido)
            eventManager.removeSubscriber(eventManager.getEventIDByName(eventName), targetUUID);
            actionAdded = false;
        } else {
            // Não está inscrito → verificar se inscrições estão abertas
            if (!eventManager.isEventOpenSign(eventName)) {
                player.sendMessage("§cAs inscrições para este evento estão fechadas.");
                return true;
            }
            eventManager.addSubscriber(eventManager.getEventIDByName(eventName), targetUUID);
            actionAdded = true;
        }

        if (actionAdded) {
            player.sendMessage("§aJogador " + targetPlayer.getName() + " inscrito no evento: §f" + eventName);
            if (!targetPlayer.equals(player)) {
                targetPlayer.sendMessage("§aVocê foi inscrito no evento: §f" + eventName);
            }
        } else {
            player.sendMessage("§cJogador " + targetPlayer.getName() + " foi removido do evento: §f" + eventName);
            if (!targetPlayer.equals(player)) {
                targetPlayer.sendMessage("§cVocê foi removido do evento: §f" + eventName);
            }
        }

        return true;
    }

    // ========================== /event reserve ==========================
    private boolean handleReserve(Player player, String[] args) {

        if (args.length < 2) {
            player.sendMessage("§cUso correto: /event reserve <eventname> [playername]");
            return true;
        }

        String eventName = args[1];
        Player targetPlayer = player; // default: quem executou

        if (args.length >= 3) {
            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null) {
                player.sendMessage("§cJogador não encontrado: §f" + args[2]);
                return true;
            }
        }

        // Verificar se o evento existe
        if (!eventManager.getIfEventExistsByName(eventName)) {
            player.sendMessage("§cEsse evento não existe: §f" + eventName);
            return true;
        }

        UUID targetUUID = targetPlayer.getUniqueId();
        Set<UUID> reserves = eventManager.getReserves(eventName);

        boolean actionAdded;
        if (reserves.contains(targetUUID)) {
            // Já está em reserves → remover (sempre permitido)
            eventManager.removeReserve(eventManager.getEventIDByName(eventName), targetUUID);
            actionAdded = false;
        } else {
            // Não está → verificar se inscrições estão abertas
            if (!eventManager.isEventOpenSign(eventName)) {
                player.sendMessage("§cAs reservas para este evento estão fechadas.");
                return true;
            }
            eventManager.addReserve(eventManager.getEventIDByName(eventName), targetUUID);
            actionAdded = true;
        }

        if (actionAdded) {
            player.sendMessage("§aJogador " + targetPlayer.getName() + " adicionado à lista de reservas do evento: §f" + eventName);
            if (!targetPlayer.equals(player)) {
                targetPlayer.sendMessage("§aVocê foi adicionado à lista de reservas do evento: §f" + eventName);
            }
        } else {
            player.sendMessage("§cJogador " + targetPlayer.getName() + " removido da lista de reservas do evento: §f" + eventName);
            if (!targetPlayer.equals(player)) {
                targetPlayer.sendMessage("§cVocê foi removido da lista de reservas do evento: §f" + eventName);
            }
        }

        return true;
    }



    // -------------------------------------------------------------
// /event signs <eventname>
// -------------------------------------------------------------
    private boolean handleSigns(Player player, String[] args) {

        if (args.length < 2) {
            player.sendMessage("§cUso correto: /event signs <eventname>");
            return true;
        }

        String eventName = args[1];

        // Verificar se o evento existe
        if (!eventManager.getIfEventExistsByName(eventName)) {
            player.sendMessage("§cEsse evento não existe: §f" + eventName);
            return true;
        }

        // Pegar inscritos e reservas
        Set<UUID> subscribers = eventManager.getSubscribers(eventName);
        Set<UUID> reserves = eventManager.getReserves(eventName);

        // ---------- Inscritos ----------
        player.sendMessage("§e--- Signs for " + eventName + " ---");
        if (subscribers.isEmpty()) {
            player.sendMessage("§7Nenhum jogador inscrito ainda.");
        } else {
            int i = 1;
            for (UUID uuid : subscribers) {
                Player p = Bukkit.getPlayer(uuid);
                String name = (p != null) ? p.getName() : uuid.toString();
                player.sendMessage("§f" + i + ": §a" + name);
                i++;
            }
        }

        // ---------- Reserves ----------
        if (!reserves.isEmpty()) {
            player.sendMessage("§e--- Reserves for " + eventName + " ---");
            int i = 1;
            for (UUID uuid : reserves) {
                Player p = Bukkit.getPlayer(uuid);
                String name = (p != null) ? p.getName() : uuid.toString();
                player.sendMessage("§f" + i + ": §a" + name);
                i++;
            }
        }

        return true;
    }
}
