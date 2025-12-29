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
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TimeTrialMenuUtils implements Listener {

    private final FormulaRacing plugin;
    private final PacketSender ps;
    private final DatabaseManager mysql;
    private final APIFormulaRacing api;
    private final TimerUtils timerUtils;
    private final ScoreboardTimeTrialUtils stt;
    private final String INVENTORY_TITLE = ChatColor.GREEN + "Escolha uma pista";

    // 🕒 Controle de cliques: <UUID, último clique em ms>
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    public TimeTrialMenuUtils(FormulaRacing plugin, DatabaseManager mysql, APIFormulaRacing api, PacketSender ps, TimerUtils timerUtils, ScoreboardTimeTrialUtils stt) {
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
        Inventory inv = Bukkit.createInventory(null, 54, INVENTORY_TITLE);

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

            List<DatabaseManager.PlayerTime> leaderboard = mysql.getLeaderboard(trackName);
            int playerPos = -1;
            for (int i = 0; i < leaderboard.size(); i++) {
                String lbPlayerName = leaderboard.get(i).getPlayerName();
                if (lbPlayerName.equalsIgnoreCase(player.getName())) {
                    playerPos = i + 1;
                    break;
                }
            }

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
                List<String> loreList = new ArrayList<>();
                loreList.add(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + trackData.getOwnerName());
                loreList.add("");
                loreList.add(ChatColor.YELLOW + "Your PB: " + ChatColor.WHITE + (playerBestTime != null ? formatTime(playerBestTime) : "(-)"));
                loreList.add(ChatColor.YELLOW + "World Record: " + ChatColor.WHITE + (worldRecordTime != null ? formatTime(worldRecordTime) : "(-)"));
                loreList.add(ChatColor.YELLOW + "Position: " + ChatColor.WHITE + (playerPos != -1 ? "#" + playerPos : "(-)"));
                meta.setLore(loreList);
                item.setItemMeta(meta);
            }

            inv.setItem(slot++, item);
        }

        player.openInventory(inv);
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(INVENTORY_TITLE)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;

        Player player = (Player) event.getWhoClicked();

        // 🕒 Limite de um clique a cada 0.5s (500ms)
        long now = System.currentTimeMillis();
        long lastClick = clickCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastClick < 500) {
            String langCode = mysql.getPlayerLanguage(player.getUniqueId());
            player.sendMessage(plugin.getDirectTranslation("wait_before_click", langCode));
            return;
        }
        clickCooldown.put(player.getUniqueId(), now);

        // Remove todas as formatações de cor e itálico do nome da pista
        String trackName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).trim();

        // Validação: trackName não pode estar vazio
        if (trackName.isEmpty()) {
            plugin.getLogger().warning("Track name is empty after strip colors! Display name was: " + clicked.getItemMeta().getDisplayName());
            player.sendMessage("§cErro: Nome da pista inválido.");
            return;
        }

        // Fecha o inventário para evitar cliques duplicados
        player.closeInventory();

        if (!mysql.isTrackOpen(trackName)) {
            String langCode = mysql.getPlayerLanguage(player.getUniqueId());
            player.sendMessage(plugin.getDirectTranslation("track_is_closed", langCode));
            return;
        }

        if (event.isLeftClick()) {

            // 🔹 Salva tempo parcial da pista anterior antes de trocar
            String lastTrack = plugin.getLastTimeTrialTrack(player.getUniqueId());
            if (lastTrack != null && !lastTrack.equals(trackName)) {
                TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player, lastTrack);
                if (data != null && !data.getCheckpointsReached().isEmpty()) {
                    double currentTime = timerUtils.getPlayerElapsedTime(player, lastTrack);
                    int checkpoints = data.getCheckpointsReached().size();

                    Object[] bestData = mysql.getPlayerBestTime(player.getUniqueId().toString(), lastTrack);
                    double bestTime = (bestData != null) ? (Double) bestData[0] : Double.MAX_VALUE;
                    int bestCheckpoints = (bestData != null) ? (Integer) bestData[1] : 0;
                    boolean finished = (bestData != null) ? (Boolean) bestData[2] : false;

                    boolean shouldSave = !finished && (checkpoints > bestCheckpoints ||
                            (checkpoints == bestCheckpoints && currentTime < bestTime));

                    if (shouldSave) {
                        mysql.savePartialTime(player.getUniqueId(), player.getName(), lastTrack, currentTime, checkpoints);
                        String langCode = mysql.getPlayerLanguage(player.getUniqueId());
                        String formattedTime = formatTime(currentTime);
                        player.sendMessage(plugin.getTranslation("partial_time_saved", langCode,
                            "{track}", lastTrack, "{time}", formattedTime + " com " + checkpoints + " checkpoints"));
                    }
                }
                timerUtils.stopTimer(player, lastTrack);
            }

            // 🔹 Teleporta para a nova pista
            Location loc = mysql.getTrackSpawn(trackName);
            if (loc != null) {

                // 🚫 Impedir entrar em pista que usa BoatUtils se o jogador não tem o mod
                boolean playerHasBoatUtils = FormulaRacing.hasOpenBoatUtilsMod(player);
                boolean trackUsesBoatUtils = mysql.trackHaveBoatUtils(trackName);
                String langCode = mysql.getPlayerLanguage(player.getUniqueId());

                if (trackUsesBoatUtils && !playerHasBoatUtils) {
                    player.sendMessage(plugin.getDirectTranslation("does_not_have_boatutils", langCode));
                    player.sendMessage(plugin.getDirectTranslation("boatutils_required", langCode));
                    return;
                }

                // 🚤 Remove o barco antigo ANTES de teleportar para evitar barcos fantasmas
                if (player.getVehicle() instanceof org.bukkit.entity.Boat oldBoat) {
                    player.leaveVehicle(); // Força o jogador a sair do barco
                    api.deleteBoat(oldBoat); // Remove o barco antigo
                }

                ps.sendBoatSetting(player, 0);
                ps.applyBoatUtilsToPlayer(player, trackName);

                player.teleport(loc);
                api.spawnBoat(player, true, false, false);
                plugin.setLastTimeTrialTrack(player.getUniqueId(), trackName);
                stt.setPlayerTrack(player, trackName);

                // Envia mensagem de teleporte com placeholder
                String teleportMsg = plugin.getTranslation("timetrial_teleport", langCode, "{track}", trackName);
                plugin.getLogger().info("Sending teleport message to " + player.getName() + ": " + teleportMsg);
                player.sendMessage(teleportMsg);

                // Envia informações da pista
                DatabaseManager.TrackData trackData = mysql.getTrackData(trackName);
                if (trackData != null) {
                    player.sendMessage(plugin.getTranslation("track_owner_info", langCode, "{owner}", trackData.getOwnerName()));
                    player.sendMessage(plugin.getTranslation("track_world_info", langCode, "{world}", trackData.getWorldName()));
                }
            } else {
                String langCode = mysql.getPlayerLanguage(player.getUniqueId());
                player.sendMessage(plugin.getDirectTranslation("track_location_not_found", langCode));
            }

        }
    }
}
