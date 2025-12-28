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
            player.sendMessage(ChatColor.RED + "Aguarde meio segundo antes de clicar novamente!");
            return;
        }
        clickCooldown.put(player.getUniqueId(), now);

        String trackName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (!mysql.isTrackOpen(trackName)) {
            player.sendMessage(ChatColor.RED + "Esta pista foi fechada e não pode ser usada no momento!");
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
                        player.sendMessage("§aTempo parcial da pista anterior §e" + lastTrack + " §afoi salvo: §e" +
                                formatTime(currentTime) + " §acom §e" + checkpoints + " checkpoints");
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

                if (trackUsesBoatUtils && !playerHasBoatUtils) {
                    player.sendMessage("§cEsta pista usa configurações especiais de BoatUtils, mas você não está com o mod!");
                    player.sendMessage("§cUse o BoatUtils Mod para entrar nesta pista.");
                    return;
                }
                ps.sendBoatSetting(player, 0);
                ps.applyBoatUtilsToPlayer(player, trackName);

                player.teleport(loc);
                api.spawnBoat(player, true, false, false);
                plugin.setLastTimeTrialTrack(player.getUniqueId(), trackName);
                stt.setPlayerTrack(player, trackName);

                player.sendMessage("§eTeleportado para [§f" + trackName + "§e]");

                DatabaseManager.TrackData trackData = mysql.getTrackData(trackName);
                if (trackData != null) {
                    player.sendMessage(ChatColor.AQUA + "Dono: " + trackData.getOwnerName());
                    player.sendMessage(ChatColor.AQUA + "Mundo: " + trackData.getWorldName());
                }
            } else {
                player.sendMessage(ChatColor.RED + "Localização da pista não encontrada!");
            }

        }
    }
}
