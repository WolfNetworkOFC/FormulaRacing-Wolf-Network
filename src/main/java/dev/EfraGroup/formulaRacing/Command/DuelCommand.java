package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.Controllers.QuickRaceManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialDuelsAction;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

@CommandAlias("duel|duelar")
public class DuelCommand extends BaseCommand implements Listener {

    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private final PacketSender packet;
    private final TimeTrialDuels timeTrialDuels;
    private final TimeTrialDuelsAction ttda;

    private final Map<UUID, String> searchingPlayers = new HashMap<>();
    private final String GUI_SETUP = "§8Configurar Duelo";
    private final String GUI_TRACKS = "§8Selecionar Pista";
    private final String GUI_DUEL = "§b§lDUEL • TIME TRIAL";

    private final NamespacedKey KEY_TARGET = new NamespacedKey("formula", "target");
    private final NamespacedKey KEY_TRACK = new NamespacedKey("formula", "track");
    private final NamespacedKey KEY_TIME = new NamespacedKey("formula", "time");
    private final NamespacedKey KEY_LAPS = new NamespacedKey("formula", "laps");
    private final NamespacedKey KEY_MODE = new NamespacedKey("formula", "mode");
    private final NamespacedKey KEY_LONELY = new NamespacedKey("formula", "lonely");
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    // Construtor
    public DuelCommand(FormulaRacing plugin, DatabaseManager databaseManager, TimeTrialDuels timeTrialDuels, TimeTrialDuelsAction ttda, PacketSender packetSender) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.timeTrialDuels = timeTrialDuels;
        this.ttda = ttda;
        this.packet = packetSender;
    }

    // 1. Comando Padrão: /duel [jogador]
    @Default
    @CommandCompletion("@players")
    public void onDefaultChallenge(Player player, String targetName) {
        if (targetName == null) {
            player.sendMessage("§cUse: /duel <jogador> ou /duel sair");
            playSound(player, Sound.ENTITY_VILLAGER_NO, 1.0f);
            return;
        }

        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            player.sendMessage("§cJogador inválido ou offline.");
            playSound(player, Sound.ENTITY_ITEM_BREAK, 1.0f);
            return;
        }

        if (target.equals(player)) {
            player.sendMessage("§cVocê não pode desafiar a si mesmo.");
            return;
        }

        openSetupGUI(player, target);
    }

    // 2. Subcomando: /duel accept [jogador]
    @Subcommand("accept")
    @CommandCompletion("@players")
    public void onAccept(Player player, String challengerName) {
        if (challengerName == null) {
            UUID lastChallengerUUID = pendingInvites.get(player.getUniqueId());
            if (lastChallengerUUID != null) {
                Player challenger = Bukkit.getPlayer(lastChallengerUUID);
                if (challenger != null) {
                    handleAccept(player, challenger.getName());
                    return;
                }
            }
            player.sendMessage("§cUse: /duel accept <nome>");
            return;
        }
        handleAccept(player, challengerName);
    }

    // 3. Subcomando: /duel deny
    @Subcommand("deny")
    public void onDeny(Player player) {
        player.sendMessage("§cConvite recusado.");
        pendingInvites.remove(player.getUniqueId());
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f);
    }

    // 4. Subcomando: /duel sair (quit/leave)
    @Subcommand("quit|leave|sair")
    public void onLeave(Player player) {
        handleLeave(player);
    }

    // =========================================================================
    // O RESTO DO SEU CÓDIGO (GUI, Eventos e Lógica de Duelo) FICA INTACTO ABAIXO
    // =========================================================================

    public void openSetupGUI(Player player, Player target) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_SETUP);

        String track = player.getPersistentDataContainer().getOrDefault(KEY_TRACK, PersistentDataType.STRING, "Nenhuma");
        int time = player.getPersistentDataContainer().getOrDefault(KEY_TIME, PersistentDataType.INTEGER, 60);
        int laps = player.getPersistentDataContainer().getOrDefault(KEY_LAPS, PersistentDataType.INTEGER, 3);
        String mode = player.getPersistentDataContainer().getOrDefault(KEY_MODE, PersistentDataType.STRING, "CORRIDA");

        int lonelyInt = player.getPersistentDataContainer().getOrDefault(KEY_LONELY, PersistentDataType.INTEGER, 0);
        String lonelyStatus = (lonelyInt == 1) ? "§aATIVADO" : "§cDESATIVADO";
        Material lonelyMaterial = (lonelyInt == 1) ? Material.ENDER_EYE : Material.ENDER_PEARL;

        inv.setItem(10, createItem(Material.MAP, "§b§lPista", "§7Selecionada: §f" + track, "", "§eClique para alterar"));

        String formattedTime = formatTime(time);
        inv.setItem(11, createItem(Material.CLOCK, "§e§lTempo Limite", "§7Atual: §f" + formattedTime, "", "§7Esq: §a+10s §8| §7Dir: §c-10s", "§7Shift: §f+/- 1min"));
        inv.setItem(12, createItem(Material.REPEATER, "§f§lVoltas", "§7Atual: §f" + laps, "", "§7Esq: §a+1 §8| §7Dir: §c-1"));
        inv.setItem(13, createItem(Material.COMPASS, "§d§lModo", "§7Atual: §f" + mode, "", "§eClique para alternar"));

        inv.setItem(14, createItem(lonelyMaterial, "§5§lModo Lonely",
                "§7Status: " + lonelyStatus,
                "",
                "§7Jogadores ficam invisíveis",
                "§7uns para os outros.",
                "", "§eClique para alternar"));

        inv.setItem(15, createItem(Material.LIME_CONCRETE, "§a§lENVIAR CONVITE", "§7Enviar para: §e" + target.getName()));
        inv.setItem(16, createItem(Material.BARRIER, "§c§lFECHAR"));

        player.getPersistentDataContainer().set(KEY_TARGET, PersistentDataType.STRING, target.getName());
        player.openInventory(inv);
        playSound(player, Sound.BLOCK_CHEST_OPEN, 0.8f);
    }

    public void openTrackSelector(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TRACKS);
        List<String> tracks = databaseManager.getAllTracks();
        int slot = 0;

        for (String trackName : tracks) {
            if (slot >= 45) break;
            String iconName = databaseManager.getIcon(trackName);
            Material material;
            try {
                material = (iconName != null) ? Material.valueOf(iconName.toUpperCase()) : Material.PAPER;
            } catch (IllegalArgumentException e) {
                material = Material.PAPER;
            }
            inv.setItem(slot++, createItem(material, "§b" + trackName, "§7Clique para selecionar esta pista"));
        }

        inv.setItem(49, createItem(Material.NAME_TAG, "§e§lPesquisar por Nome", "§7Clique para digitar o nome no chat"));
        inv.setItem(45, createItem(Material.ARROW, "§cVoltar", "§7Voltar para configuração"));

        player.openInventory(inv);
        playSound(player, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String title = e.getView().getTitle();

        if (title.equals(GUI_SETUP)) {
            e.setCancelled(true);
            handleSetupClick(player, e);
        } else if (title.equals(GUI_TRACKS)) {
            e.setCancelled(true);
            handleTrackClick(player, e);
        }
    }

    private void handleSetupClick(Player player, InventoryClickEvent e) {
        String targetName = player.getPersistentDataContainer().get(KEY_TARGET, PersistentDataType.STRING);
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { player.closeInventory(); return; }

        switch (e.getRawSlot()) {
            case 10 -> openTrackSelector(player);
            case 11 -> {
                int current = player.getPersistentDataContainer().getOrDefault(KEY_TIME, PersistentDataType.INTEGER, 60);
                int amount = e.isShiftClick() ? 60 : 10;
                current = e.isLeftClick() ? current + amount : Math.max(10, current - amount);
                player.getPersistentDataContainer().set(KEY_TIME, PersistentDataType.INTEGER, current);
                openSetupGUI(player, target);
                playSound(player, Sound.UI_BUTTON_CLICK, 1.2f);
            }
            case 12 -> {
                int current = player.getPersistentDataContainer().getOrDefault(KEY_LAPS, PersistentDataType.INTEGER, 3);
                current = e.isLeftClick() ? current + 1 : Math.max(1, current - 1);
                player.getPersistentDataContainer().set(KEY_LAPS, PersistentDataType.INTEGER, current);
                openSetupGUI(player, target);
                playSound(player, Sound.UI_BUTTON_CLICK, 1.0f);
            }
            case 13 -> {
                String mode = player.getPersistentDataContainer().getOrDefault(KEY_MODE, PersistentDataType.STRING, "CORRIDA");
                mode = mode.equals("CORRIDA") ? "TIME TRIAL" : "CORRIDA";
                player.getPersistentDataContainer().set(KEY_MODE, PersistentDataType.STRING, mode);
                openSetupGUI(player, target);
                playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f);
            }
            case 14 -> {
                int current = player.getPersistentDataContainer().getOrDefault(KEY_LONELY, PersistentDataType.INTEGER, 0);
                int next = (current == 0) ? 1 : 0;
                player.getPersistentDataContainer().set(KEY_LONELY, PersistentDataType.INTEGER, next);
                openSetupGUI(player, target);
                playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f);
            }
            case 15 -> {
                sendInvite(player, target);
                player.closeInventory();
                playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f);
            }
            case 16 -> {
                player.closeInventory();
                playSound(player, Sound.BLOCK_CHEST_CLOSE, 1.0f);
            }
        }
    }

    private void handleTrackClick(Player player, InventoryClickEvent e) {
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        int slot = e.getRawSlot();

        if (slot == 45) {
            String targetName = player.getPersistentDataContainer().get(KEY_TARGET, PersistentDataType.STRING);
            Player target = Bukkit.getPlayer(targetName);
            if (target != null) openSetupGUI(player, target);
            else player.closeInventory();
            playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1.0f);
            return;
        }

        if (slot == 49) {
            player.closeInventory();
            String targetName = player.getPersistentDataContainer().get(KEY_TARGET, PersistentDataType.STRING);
            searchingPlayers.put(player.getUniqueId(), targetName);
            player.sendMessage("§e§lPESQUISA §8» §fDigite o nome da pista no chat.");
            playSound(player, Sound.BLOCK_ANVIL_USE, 1.5f);
            return;
        }

        if (slot >= 45 && slot <= 53) return;

        String trackName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
        player.getPersistentDataContainer().set(KEY_TRACK, PersistentDataType.STRING, trackName);
        String targetName = player.getPersistentDataContainer().get(KEY_TARGET, PersistentDataType.STRING);
        Player target = Bukkit.getPlayer(targetName);
        if (target != null) openSetupGUI(player, target);
        else player.closeInventory();
        playSound(player, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (databaseManager.isPlayerInActiveDuel(player.getUniqueId())) {
            handleLeave(player);
        }
    }

    public void handleLeave(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!databaseManager.isPlayerInActiveDuel(playerUUID)) {
            player.sendMessage("§cVocê não está em um duelo ativo.");
            return;
        }

        int duelId = databaseManager.getActiveDuelId(playerUUID);
        if (duelId == -1) return;

        timeTrialDuels.removePlayerFromDuel(playerUUID, duelId);

        player.sendMessage("§7Você saiu do duelo.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
    }

    @EventHandler
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        if (!searchingPlayers.containsKey(player.getUniqueId())) return;

        e.setCancelled(true);
        String message = e.getMessage();
        String targetName = searchingPlayers.get(player.getUniqueId());

        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("cancelar")) {
            searchingPlayers.remove(player.getUniqueId());
            player.sendMessage("§cBusca cancelada.");
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f);
            Bukkit.getScheduler().runTask(plugin, () -> openTrackSelector(player));
            return;
        }

        List<String> allTracks = databaseManager.getAllTracks();
        String foundTrack = allTracks.stream()
                .filter(t -> t.toLowerCase().contains(message.toLowerCase()))
                .findFirst().orElse(null);

        if (foundTrack != null) {
            searchingPlayers.remove(player.getUniqueId());
            String finalTrack = foundTrack;
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.getPersistentDataContainer().set(KEY_TRACK, PersistentDataType.STRING, finalTrack);
                player.sendMessage("§aPista selecionada: §f" + finalTrack);
                playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.5f);
                Player target = Bukkit.getPlayer(targetName);
                if (target != null) openSetupGUI(player, target);
            });
        } else {
            player.sendMessage("§c§lERRO §8» §7Pista não encontrada.");
            playSound(player, Sound.ENTITY_VILLAGER_NO, 1.0f);
        }
    }

    private void sendInvite(Player challenger, Player target) {
        UUID targetUUID = target.getUniqueId();
        UUID challengerUUID = challenger.getUniqueId();

        pendingInvites.put(targetUUID, challengerUUID);
        plugin.getLogger().info("[DEBUG] Convite registrado: " + challenger.getName() + " -> " + target.getName());

        String track = challenger.getPersistentDataContainer().getOrDefault(KEY_TRACK, PersistentDataType.STRING, "Nenhuma");
        int laps = challenger.getPersistentDataContainer().getOrDefault(KEY_LAPS, PersistentDataType.INTEGER, 3);

        challenger.sendMessage("§a§lDUELO §8» §fConvite enviado para §e" + target.getName() + "§f!");

        TextComponent msg = new TextComponent("§e" + challenger.getName() + " §7te desafiou para um duelo!\n" +
                "§fPista: §b" + track + " §8| §fVoltas: §b" + laps + "\n");

        TextComponent accept = new TextComponent("§a§l[ACEITAR CONVITE]");
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept " + challenger.getName()));

        TextComponent space = new TextComponent("   ");
        TextComponent deny = new TextComponent("§c§l[RECUSAR]");
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel deny"));

        target.sendMessage(" ");
        target.spigot().sendMessage(msg);
        target.spigot().sendMessage(accept, space, deny);
        target.sendMessage("§8(Este convite expira em 60 segundos)");
        target.sendMessage(" ");

        playSound(target, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingInvites.containsKey(targetUUID) && pendingInvites.get(targetUUID).equals(challengerUUID)) {
                pendingInvites.remove(targetUUID);

                if (target.isOnline()) {
                    target.sendMessage("§c§lDUELO §8» §7O convite de §f" + challenger.getName() + " §7expirou.");
                }
                if (challenger.isOnline()) {
                    challenger.sendMessage("§c§lDUELO §8» §7Seu convite para §f" + target.getName() + " §7expirou.");
                }
                plugin.getLogger().info("[DEBUG] Convite expirado: " + challenger.getName() + " -> " + target.getName());
            }
        }, 20 * 60L);
    }

    private void handleAccept(Player responder, String challengerName) {
        plugin.getLogger().info("[DEBUG] " + responder.getName() + " tentou /duel accept " + challengerName);

        Player challenger = Bukkit.getPlayer(challengerName);

        if (challenger == null || !challenger.isOnline()) {
            responder.sendMessage("§c§lERRO §8» §7O desafiante §f" + challengerName + " §7está offline.");
            return;
        }

        UUID responderUUID = responder.getUniqueId();
        UUID challengerUUID = challenger.getUniqueId();

        if (!pendingInvites.containsKey(responderUUID)) {
            responder.sendMessage("§c§lERRO §8» §7Não há convites pendentes para você.");
            return;
        }

        UUID storedChallengerUUID = pendingInvites.get(responderUUID);
        if (!storedChallengerUUID.equals(challengerUUID)) {
            responder.sendMessage("§c§lERRO §8» §7O convite pendente não pertence a este desafiante.");
            return;
        }

        try {
            if (databaseManager.isPlayerInActiveDuel(responderUUID)) {
                responder.sendMessage("§c§lERRO §8» §7Você já está em um duelo!");
                return;
            }

            if (databaseManager.isPlayerInActiveDuel(challengerUUID)) {
                responder.sendMessage("§c§lERRO §8» §7O desafiante já entrou em outra corrida.");
                pendingInvites.remove(responderUUID);
                return;
            }
        } catch (Exception e) {
            responder.sendMessage("§c§lERRO §8» §7Falha ao consultar o banco de dados.");
            return;
        }

        pendingInvites.remove(responderUUID);

        String track = challenger.getPersistentDataContainer().getOrDefault(KEY_TRACK, PersistentDataType.STRING, "Nenhuma");
        int laps = challenger.getPersistentDataContainer().getOrDefault(KEY_LAPS, PersistentDataType.INTEGER, 3);
        int timeLimitSeconds = challenger.getPersistentDataContainer().getOrDefault(KEY_TIME, PersistentDataType.INTEGER, 60);

        int lonelyInt = challenger.getPersistentDataContainer().getOrDefault(KEY_LONELY, PersistentDataType.INTEGER, 0);
        boolean isLonely = (lonelyInt == 1);

        String mode = challenger.getPersistentDataContainer().getOrDefault(KEY_MODE, PersistentDataType.STRING, "CORRIDA");
        boolean isTimeTrialMode = mode.equalsIgnoreCase("TIME TRIAL") || mode.equalsIgnoreCase("TIME_TRIAL") || mode.toUpperCase().contains("TIME");

        if (track.equals("Nenhuma")) {
            responder.sendMessage("§c§lERRO §8» §7O desafiante não selecionou uma pista válida.");
            return;
        }

        try {
            playSound(responder, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
            playSound(challenger, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
            if (isTimeTrialMode) {
                timeTrialDuels.startDuelPreparation(challenger, responder, track, laps, timeLimitSeconds, isLonely, isTimeTrialMode);
            } else {
                // For race mode, use QuickRaceManager
                QuickRaceManager qrm = plugin.getQuickRaceManager();
                qrm.createDuelRace(challenger, responder, track, laps, 0); // pits = 0 for duels
            }
        } catch (Exception e) {
            e.printStackTrace();
            responder.sendMessage("§c§lERRO §8» §7Falha crítica ao iniciar o duelo.");
        }
    }

    private void openDuelConfirmGUI(Player p1, Player p2) {
        Inventory inv = Bukkit.createInventory(null, 45, GUI_DUEL);
        p1.openInventory(inv);
    }

    private void playSound(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}