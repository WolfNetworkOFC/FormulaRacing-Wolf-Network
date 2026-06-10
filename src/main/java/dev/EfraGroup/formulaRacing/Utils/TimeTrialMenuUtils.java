 /*
  * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
  *
  * Could not load the following classes:
  *  dev.EfraGroup.formulaRacing.Database.DatabaseManager
  *  org.bukkit.Bukkit
  *  org.bukkit.ChatColor
  *  org.bukkit.Location
  *  org.bukkit.Material
  *  org.bukkit.entity.Boat
  *  org.bukkit.entity.Entity
  *  org.bukkit.entity.Player
  *  org.bukkit.event.EventHandler
  *  org.bukkit.event.EventPriority
  *  org.bukkit.event.Listener
  *  org.bukkit.event.inventory.InventoryClickEvent
  *  org.bukkit.inventory.Inventory
  *  org.bukkit.inventory.ItemStack
  *  org.bukkit.inventory.meta.ItemMeta
  *  org.bukkit.plugin.Plugin
  */
 package dev.EfraGroup.formulaRacing.Utils;

 import dev.EfraGroup.formulaRacing.APIFormulaRacing;
 import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
 import dev.EfraGroup.formulaRacing.FormulaRacing;
 import dev.EfraGroup.formulaRacing.PacketSender;
 import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
 import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.Map;
 import java.util.UUID;
 import java.util.concurrent.ConcurrentHashMap;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

 public class TimeTrialMenuUtils
         implements Listener {
     private final FormulaRacing plugin;
     private final PacketSender ps;
     private final DatabaseManager mysql;
     private final APIFormulaRacing api;
     private final TimerUtils timerUtils;
     private final ScoreboardTimeTrialUtils stt;
     private final String INVENTORY_TITLE = String.valueOf(ChatColor.GREEN) + "Escolha uma pista";
     private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<UUID, Long>();

     public TimeTrialMenuUtils(FormulaRacing plugin, DatabaseManager mysql, APIFormulaRacing api, PacketSender ps, TimerUtils timerUtils, ScoreboardTimeTrialUtils stt) {
         this.plugin = plugin;
         this.mysql = mysql;
         this.api = api;
         this.ps = ps;
         this.timerUtils = timerUtils;
         this.stt = stt;
     }

     public void open(Player player) {
         // 1. Tipagem correta do Map para evitar casts manuais no loop
         Map<String, DatabaseManager.TrackData> tracksData = this.mysql.getAllTracksWithData();
         this.plugin.getDebugManager().logGuiSystem("DEBUG TT: Encontradas " + tracksData.size() + " pistas.");

         Inventory inv = Bukkit.createInventory(null, 54, this.INVENTORY_TITLE);
         int slot = 0;

         for (Map.Entry<String, DatabaseManager.TrackData> entry : tracksData.entrySet()) {
             // Trava de segurança para não ultrapassar o tamanho do inventário (54 slots)
             if (slot >= 54) break;

             String trackName = entry.getKey();
             DatabaseManager.TrackData trackData = entry.getValue();

             if (!this.mysql.isTrackOpen(trackName)) continue;

             String iconName = this.mysql.getIcon(trackName);
             Double worldRecordTime = this.mysql.getBestTime(trackName);
             Object[] playerBestData = this.mysql.getPlayerBestTime(player.getName(), trackName);

             Double playerBestTime = (playerBestData != null) ? (Double) playerBestData[0] : null;

             // 2. Tipagem correta da Leaderboard para o loop de posição
             List<DatabaseManager.PlayerTime> leaderboard = this.mysql.getLeaderboard(trackName);
             int playerPos = -1;
             for (int i = 0; i < leaderboard.size(); ++i) {
                 if (leaderboard.get(i).getPlayerName().equalsIgnoreCase(player.getName())) {
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

                 // CORREÇÃO: List de String, não de Object
                 List<String> loreList = new ArrayList<>();
                 loreList.add(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + trackData.getOwnerName());
                 loreList.add("");
                 loreList.add(ChatColor.YELLOW + "Your PB: " + ChatColor.WHITE + (playerBestTime != null ? this.formatTime(playerBestTime) : "(-)"));
                 loreList.add(ChatColor.YELLOW + "World Record: " + ChatColor.WHITE + (worldRecordTime != null ? this.formatTime(worldRecordTime) : "(-)"));
                 loreList.add(ChatColor.YELLOW + "Position: " + ChatColor.WHITE + (playerPos != -1 ? "#" + playerPos : "(-)"));

                 meta.setLore(loreList);
                 item.setItemMeta(meta);
             }
             inv.setItem(slot++, item);
         }
         player.openInventory(inv);
     }

     private String formatTime(double time) {
         int minutes = (int)(time / 60.0);
         double seconds = time % 60.0;
         if (minutes > 0) {
             return String.format("%d:%06.3f", minutes, seconds);
         }
         return String.format("%.3f", seconds);
     }

     @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=false)
     public void onInventoryClick(InventoryClickEvent event) {
         if (!event.getView().getTitle().equals(this.INVENTORY_TITLE)) {
             return;
         }
         event.setCancelled(true);
         ItemStack clicked = event.getCurrentItem();
         if (clicked == null || clicked.getType() == Material.AIR) {
             return;
         }
         if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
             return;
         }
         if (!event.isLeftClick()) {
             return;
         }
         Player player = (Player)event.getWhoClicked();
         UUID uuid = player.getUniqueId();
         String trackName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).trim();
         if (trackName.isEmpty()) {
             this.plugin.getDebugManager().logGuiSystem("Track name is empty after strip colors! Display name was: " + clicked.getItemMeta().getDisplayName());
             player.sendMessage("\u00a7cErro: Nome da pista inv\u00e1lido.");
             return;
         }
         if (!this.mysql.isTrackOpen(trackName)) {
             String langCode = this.mysql.getPlayerLanguage(uuid);
             player.sendMessage(this.plugin.getDirectTranslation("track_is_closed", langCode));
             return;
         }
         long now = System.currentTimeMillis();
         Long previousTimestamp = this.lastClickTime.putIfAbsent(uuid, now);
         if (previousTimestamp != null) {
             long diff = now - previousTimestamp;
             if (diff < 3000L) {
                 return;
             }
             this.lastClickTime.put(uuid, now);
         }
          player.closeInventory();
           SchedulerHelper.runTaskFor(this.plugin, player, () -> {
               try {
                   this.processTeleport(player, trackName);
               } catch (Exception e) {
                   this.plugin.getDebugManager().logGuiSystem("[ERROR] Error processing teleport: " + e.getMessage());
               }
           });
     }

     private void processTeleport(Player player, String trackName) {
         Location loc;
         String lastTrack = this.plugin.getLastTimeTrialTrack(player.getUniqueId());
         if (lastTrack != null && !lastTrack.equals(trackName)) {
             TimerUtils.PlayerTimerData data = this.timerUtils.getTimerData(player, lastTrack);
              if (data != null && data.getCheckpointsReached() > 0) {
                  boolean shouldSave;
                  double currentTime = this.timerUtils.getPlayerElapsedTime(player, lastTrack);
                  int checkpoints = data.getCheckpointsReached();
                 Object[] bestData = this.mysql.getPlayerBestTime(player.getUniqueId().toString(), lastTrack);
                 double bestTime = bestData != null ? (Double)bestData[0] : Double.MAX_VALUE;
                 int bestCheckpoints = bestData != null ? (Integer)bestData[1] : 0;
                 boolean finished = bestData != null ? (Boolean)bestData[2] : false;
                 boolean bl = shouldSave = !finished && (checkpoints > bestCheckpoints || checkpoints == bestCheckpoints && currentTime < bestTime);
                 if (shouldSave) {
                     this.mysql.savePartialTime(player.getUniqueId(), player.getName(), lastTrack, currentTime, checkpoints);
                     String langCode = this.mysql.getPlayerLanguage(player.getUniqueId());
                     String formattedTime = this.formatTime(currentTime);
                     player.sendMessage(this.plugin.getTranslation("partial_time_saved", langCode, "{track}", lastTrack, "{time}", formattedTime + " com " + checkpoints + " checkpoints"));
                 }
             }
             this.timerUtils.stopTimer(player, lastTrack);
         }
         if ((loc = this.mysql.getTrackSpawn(trackName)) != null) {
             boolean playerHasBoatUtils = FormulaRacing.hasOpenBoatUtilsMod(player);
             boolean trackUsesBoatUtils = this.mysql.trackHaveBoatUtils(trackName);
             String langCode = this.mysql.getPlayerLanguage(player.getUniqueId());
             if (trackUsesBoatUtils && !playerHasBoatUtils) {
                 this.plugin.sendMessage(player, "obu_mandatory_warning", "{track}", trackName);
             }
             this.api.recoverPlayerBoatState(player);
             this.ps.sendBoatSetting(player, 0, new Object[0]);
             this.ps.applyBoatUtilsToPlayer(player, trackName);
             SchedulerHelper.teleport(player, loc);
             this.api.spawnBoat(player, true, false, false);
             this.plugin.setLastTimeTrialTrack(player.getUniqueId(), trackName);
             DatabaseManager.TrackData trackData = this.mysql.getTrackData(trackName);
             String owner = trackData != null ? trackData.getOwnerName() : null;
             this.stt.setPlayerTrack(player, trackName, owner);
             String teleportMsg = this.plugin.getTranslation("timetrial_teleport", langCode, "{track}", trackName);
             player.sendMessage(teleportMsg);
             if (trackData != null) {
                 player.sendMessage(this.plugin.getTranslation("track_owner_info", langCode, "{owner}", trackData.getOwnerName()));
                 player.sendMessage(this.plugin.getTranslation("track_world_info", langCode, "{world}", trackData.getWorldName()));
             }
         } else {
             String langCode = this.mysql.getPlayerLanguage(player.getUniqueId());
             player.sendMessage(this.plugin.getDirectTranslation("track_location_not_found", langCode));
         }
     }
 }
