package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Menu do Time Trial completamente REESCRITO do zero
 * Usa InventoryHolder pattern para identificação única
 * Implementa debounce agressivo de 500ms
 */
public class TimeTrialMenuUtilsV2 implements Listener {

    private final FormulaRacing plugin;
    private final PacketSender ps;
    private final DatabaseManager mysql;
    private final APIFormulaRacing api;
    private final TimerUtils timerUtils;
    private final ScoreboardTimeTrialUtils stt;

    // Holder customizado para identificar nosso inventário
    private static class TimeTrialMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // Debounce agressivo: guarda o último clique por jogador
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private final Map<UUID, String> lastClickedTrack = new HashMap<>();

    public TimeTrialMenuUtilsV2(FormulaRacing plugin, DatabaseManager mysql, APIFormulaRacing api,
                                PacketSender ps, TimerUtils timerUtils, ScoreboardTimeTrialUtils stt) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.api = api;
        this.ps = ps;
        this.timerUtils = timerUtils;
        this.stt = stt;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Map<String, DatabaseManager.TrackData> tracksData = mysql.getAllTracksWithData();

        // Obtém o idioma do jogador
        String langCode = mysql.getPlayerLanguage(player.getUniqueId());
        String menuTitle = ChatColor.translateAlternateColorCodes('&',
            plugin.getDirectTranslation("timetrial_menu_title", langCode));

        // Usa InventoryHolder customizado para identificação única
        Inventory inv = Bukkit.createInventory(new TimeTrialMenuHolder(), 54, menuTitle);

        int slot = 0;
        for (Map.Entry<String, DatabaseManager.TrackData> entry : tracksData.entrySet()) {
            String trackName = entry.getKey();
            DatabaseManager.TrackData trackData = entry.getValue();

            if (!mysql.isTrackOpen(trackName)) continue;

            String iconName = mysql.getIcon(trackName);
            Double worldRecordTime = mysql.getBestTime(trackName);

            // Buscar o PB do jogador
            Object[] playerBestData = mysql.getPlayerBestTime(player.getName(), trackName);
            Double playerBestTime = null;
            if (playerBestData != null) {
                playerBestTime = (Double) playerBestData[0];
            }

            // Posição no leaderboard
            List<DatabaseManager.PlayerTime> leaderboard = mysql.getLeaderboard(trackName);
            int playerPos = -1;
            for (int i = 0; i < leaderboard.size(); i++) {
                if (leaderboard.get(i).getPlayerName().equalsIgnoreCase(player.getName())) {
                    playerPos = i + 1;
                    break;
                }
            }

            // Cria o item
            Material iconMat;
            try {
                iconMat = Material.valueOf(iconName.toUpperCase());
            } catch (Exception e) {
                iconMat = Material.PAPER;
            }

            ItemStack item = new ItemStack(iconMat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.ITALIC + trackName);

                // Lore traduzida
                List<String> loreList = new ArrayList<>();
                loreList.add(ChatColor.translateAlternateColorCodes('&',
                    plugin.getTranslation("timetrial_menu_owner", langCode, "{owner}", trackData.getOwnerName())));
                loreList.add("");

                String pbTime = playerBestTime != null ? formatTime(playerBestTime) :
                    plugin.getDirectTranslation("timetrial_menu_no_time", langCode);
                loreList.add(ChatColor.translateAlternateColorCodes('&',
                    plugin.getTranslation("timetrial_menu_pb", langCode, "{time}", pbTime)));

                String wrTime = worldRecordTime != null ? formatTime(worldRecordTime) :
                    plugin.getDirectTranslation("timetrial_menu_no_time", langCode);
                loreList.add(ChatColor.translateAlternateColorCodes('&',
                    plugin.getTranslation("timetrial_menu_wr", langCode, "{time}", wrTime)));

                String position = playerPos != -1 ? "#" + playerPos :
                    plugin.getDirectTranslation("timetrial_menu_no_time", langCode);
                loreList.add(ChatColor.translateAlternateColorCodes('&',
                    plugin.getTranslation("timetrial_menu_position", langCode, "{position}", position)));

                meta.setLore(loreList);
                item.setItemMeta(meta);
            }

            inv.setItem(slot++, item);
        }

        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        // Verifica se é nosso inventário usando o holder
        if (!(event.getInventory().getHolder() instanceof TimeTrialMenuHolder)) {
            return;
        }

        // Cancela SEMPRE
        event.setCancelled(true);

        // Validações básicas
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.isLeftClick()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();

        // Extrai o nome da pista
        String displayName = clicked.getItemMeta().getDisplayName();
        if (displayName == null) return;

        String trackName = ChatColor.stripColor(displayName).trim();
        if (trackName.isEmpty()) return;

        // 🔒 DEBOUNCE AGRESSIVO: Bloqueia cliques duplicados
        synchronized (lastClickTime) {
            long now = System.currentTimeMillis();
            Long lastClick = lastClickTime.get(uuid);
            String lastTrack = lastClickedTrack.get(uuid);

            // Bloqueia se:
            // 1. Clicou na mesma pista há menos de 500ms
            // 2. Clicou em qualquer pista há menos de 100ms
            if (lastClick != null) {
                long diff = now - lastClick;

                if (trackName.equals(lastTrack) && diff < 500) {
                    // Mesmo track em menos de 500ms - BLOQUEIA
                    return;
                }

                if (diff < 100) {
                    // Qualquer clique em menos de 100ms - BLOQUEIA (evento duplicado)
                    return;
                }
            }

            // Atualiza o timestamp e track
            lastClickTime.put(uuid, now);
            lastClickedTrack.put(uuid, trackName);
        }

        // Valida se a pista está aberta
        if (!mysql.isTrackOpen(trackName)) {
            String langCode = mysql.getPlayerLanguage(uuid);
            player.sendMessage(plugin.getDirectTranslation("track_is_closed", langCode));
            return;
        }

        // Fecha o inventário
        player.closeInventory();

        // Executa o teleporte SÍNCRONO (sem delay) para evitar race conditions
        teleportToTrack(player, trackName);
    }

    private void teleportToTrack(Player player, String trackName) {
        UUID uuid = player.getUniqueId();

        // 🔹 Salva tempo parcial da pista anterior
        String lastTrack = plugin.getLastTimeTrialTrack(uuid);
        if (lastTrack != null && !lastTrack.equals(trackName)) {
            TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player, lastTrack);
            if (data != null && !data.getCheckpointsReached().isEmpty()) {
                double currentTime = timerUtils.getPlayerElapsedTime(player, lastTrack);
                int checkpoints = data.getCheckpointsReached().size();

                Object[] bestData = mysql.getPlayerBestTime(uuid.toString(), lastTrack);
                double bestTime = (bestData != null) ? (Double) bestData[0] : Double.MAX_VALUE;
                int bestCheckpoints = (bestData != null) ? (Integer) bestData[1] : 0;
                boolean finished = (bestData != null) ? (Boolean) bestData[2] : false;

                boolean shouldSave = !finished && (checkpoints > bestCheckpoints ||
                        (checkpoints == bestCheckpoints && currentTime < bestTime));

                if (shouldSave) {
                    mysql.savePartialTime(uuid, player.getName(), lastTrack, currentTime, checkpoints);
                    String langCode = mysql.getPlayerLanguage(uuid);
                    String formattedTime = formatTime(currentTime);
                    player.sendMessage(plugin.getTranslation("partial_time_saved", langCode,
                        "{track}", lastTrack, "{time}", formattedTime + " com " + checkpoints + " checkpoints"));
                }
            }
            timerUtils.stopTimer(player, lastTrack);
        }

        // 🔹 Teleporta para a nova pista
        Location loc = mysql.getTrackSpawn(trackName);
        if (loc == null) {
            String langCode = mysql.getPlayerLanguage(uuid);
            player.sendMessage(plugin.getDirectTranslation("track_location_not_found", langCode));
            return;
        }

        // Verifica BoatUtils
        boolean playerHasBoatUtils = FormulaRacing.hasOpenBoatUtilsMod(player);
        boolean trackUsesBoatUtils = mysql.trackHaveBoatUtils(trackName);
        String langCode = mysql.getPlayerLanguage(uuid);

        if (trackUsesBoatUtils && !playerHasBoatUtils) {
            player.sendMessage(plugin.getDirectTranslation("does_not_have_boatutils", langCode));
            player.sendMessage(plugin.getDirectTranslation("boatutils_required", langCode));
            return;
        }

        // 🚤 Remove o barco antigo ANTES de teleportar
        if (player.getVehicle() instanceof org.bukkit.entity.Boat oldBoat) {
            player.leaveVehicle();
            api.deleteBoat(oldBoat);
        }

        // Aplica configurações do BoatUtils
        ps.sendBoatSetting(player, 0);
        ps.applyBoatUtilsToPlayer(player, trackName);

        // Teleporta e spawna o barco
        player.teleport(loc);
        api.spawnBoat(player, true, false, false);
        plugin.setLastTimeTrialTrack(uuid, trackName);
        stt.setPlayerTrack(player, trackName);

        // Envia mensagens
        player.sendMessage(plugin.getTranslation("timetrial_teleport", langCode, "{track}", trackName));

        DatabaseManager.TrackData trackData = mysql.getTrackData(trackName);
        if (trackData != null) {
            player.sendMessage(plugin.getTranslation("track_owner_info", langCode, "{owner}", trackData.getOwnerName()));
            player.sendMessage(plugin.getTranslation("track_world_info", langCode, "{world}", trackData.getWorldName()));
        }
    }

    private String formatTime(double time) {
        int minutes = (int) (time / 60);
        double seconds = time % 60;
        if (minutes > 0) {
            return String.format("%d:%06.3f", minutes, seconds);
        } else {
            return String.format("%.3f", seconds);
        }
    }
}

