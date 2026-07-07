package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Controllers.HotbarController;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.BoatUtils.OpenBoatUtilsVersion;
import me.clip.placeholderapi.PlaceholderAPI;
import java.sql.SQLException;
import java.util.UUID;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PrefixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class JoinListener implements Listener {
    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final PacketSender packetSender;
    private final boolean hasLuckPerms;
    private final boolean hasTab;
    private final HotbarController hotbarController;

    public JoinListener(FormulaRacing plugin, DatabaseManager mysql, PacketSender packetSender, HotbarController hotbarController) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.packetSender = packetSender;
        this.hotbarController = hotbarController;
        this.hasLuckPerms = plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null;
        this.hasTab = plugin.getServer().getPluginManager().getPlugin("TAB") != null;
    }

    private String getPlayerRank(UUID uuid) {
        if (!this.hasLuckPerms) return "";

        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().getUser(uuid);
        if (user == null) return "";

        String primaryGroup = user.getPrimaryGroup();

        // Check for Default rank
        if (primaryGroup != null && primaryGroup.equalsIgnoreCase("default")) {
                if (isFloodgatePlayer(uuid)) {
                    return "%img_bedrock%";
                } else {
                    return "%img_java%";
                }

        }

        // Normal logic for other ranks (VIP, Staff, etc)
        CachedMetaData metaData = user.getCachedData().getMetaData();
        String prefix = metaData != null ? metaData.getPrefix() : null;

        if (prefix != null && !prefix.isEmpty()) {
            return ChatColor.translateAlternateColorCodes('&', prefix);
        } else {
            return primaryGroup != null ? primaryGroup : "";
        }
    }
    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        String rank = this.getPlayerRank(uuid);
        event.setJoinMessage(null);
        this.updatePlayerPrefix(player);
        SchedulerHelper.runAsync(this.plugin, () -> {
            try {
                boolean isFirstJoin = false;
                if (!this.mysql.playerExists(uuid)) {
                    this.mysql.insertPlayer(uuid, playerName);
                    isFirstJoin = true;
                }

                this.mysql.setSelectedEvent(uuid, null);
                if (this.plugin.getConfig().getBoolean("message-settings.join.enabled", true)) {
                    String msg;
                    if (isFirstJoin) {
                        msg = this.plugin.applyPapi(player, this.plugin.getConfig().getString("message-settings.join.first-join-message", "[+] {rank} {player}").replace("{player}", playerName).replace("{rank}", rank));

                        for(Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            String playerLang = this.mysql.getPlayerLanguage(onlinePlayer.getUniqueId());
                            String welcomeMsg = "§e" + playerName + " " + this.plugin.getDirectTranslation("welcome_first_time", playerLang).replace("{player}", playerName).replace("{rank}", rank);
                            welcomeMsg = this.plugin.applyPapi(player, welcomeMsg);
                            onlinePlayer.sendMessage(welcomeMsg);
                        }
                    } else {
                        msg = this.plugin.applyPapi(player, this.plugin.getConfig().getString("message-settings.join.message", "[+] {rank} {player}").replace("{player}", playerName).replace("{rank}", rank));
                    }

                    String finalMsg = msg;
                    SchedulerHelper.runTask(this.plugin, () -> Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', finalMsg)));
                }

                SchedulerHelper.runTaskLater(this.plugin, () -> {
                    if (player.isOnline()) {
                        this.plugin.getDailyRaceManager().notifyPlayerOfAllActiveEvents(player);
                    }

                }, 40L);
                this.plugin.getTranslationUtil().loadPlayerLanguage(uuid);
                SchedulerHelper.runTask(this.plugin, () -> {
                    try {
                        this.hotbarController.giveHotbarItems(player);
                    } catch (Exception var4) {
                        this.plugin.getDebugManager().logRaceSystem("Could not give hotbar items to " + playerName);
                    }

                });
            } catch (Exception e) {
                this.plugin.getDebugManager().logDatabaseOperation("Database error processing join: " + e.getMessage());
            }

        });
        this.plugin.getLonelyController().updatePlayersVisibility(player);

        if (FormulaRacing.hasOpenBoatUtilsMod(player)) {
            int version = FormulaRacing.getInstance().getOpenBoatUtilsVersion(uuid);
            OpenBoatUtilsVersion.setPlayerVersion(uuid, version);
        }
    }

    public boolean isFloodgatePlayer(UUID uuid) {
        // 1. Try to get the online player first (faster)
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        String playerName;

        if (player != null) {
            playerName = player.getName();
        } else {
            // 2. If the player is not online, look up server records
            playerName = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        }

        // 3. Check if the name is not null and starts with a dot
        if (playerName != null && playerName.startsWith(".")) {
            return true;
        }

        // 4. Fallback: If it doesn't start with a dot, check via Floodgate API to be sure
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        }

        return false;
    }

    public void updatePlayerPrefix(Player player) {
        if (!this.hasLuckPerms || !this.hasTab) return;

        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());

        if (user == null) return;

        TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(player.getUniqueId());
        if (tabPlayer == null) return;

        String primaryGroup = user.getPrimaryGroup();

        if (primaryGroup.equalsIgnoreCase("default")) {
            String prefix;
            if (isFloodgatePlayer(player.getUniqueId())){
                prefix = "%img_bedrock% ";
                TabAPI.getInstance().getTabListFormatManager().setPrefix(tabPlayer, "%img_bedrock% §r");

            } else{
                prefix = "%img_java% ";
                TabAPI.getInstance().getTabListFormatManager().setPrefix(tabPlayer, "%img_java% §r");
            }

            user.data().clear(node -> node instanceof PrefixNode);
            user.data().add(PrefixNode.builder(prefix, 100).build());
            luckPerms.getUserManager().saveUser(user);
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        String rank = this.getPlayerRank(uuid);
        this.plugin.getTimerUtils().stopTimer(player);
        this.plugin.getTimerUtils().clearTempCheckpoints(uuid);
        this.plugin.setLastTimeTrialTrack(uuid, null);
        
        this.plugin.getTranslationUtil().removePlayer(uuid);
        if (this.plugin.getRaceActionBarManager() != null) {
            this.plugin.getRaceActionBarManager().removePlayer(player);
        }

        if (this.plugin.getRaceScoreboardManager() != null) {
            this.plugin.getRaceScoreboardManager().removePlayer(player);
            this.plugin.getRaceScoreboardManager().removeSpectator(player);
        }

        if (this.plugin.getScoreboardTimeTrialUtils() != null) {
            this.plugin.getScoreboardTimeTrialUtils().clearPlayerTrack(player);
        }

        if (this.plugin.getRaceEventManager() != null) {
            this.plugin.getRaceEventManager().leaveEvent(player, false);
        }

        if (this.plugin.getSpectatorManager() != null) {
            this.plugin.getSpectatorManager().handlePlayerDisconnect(uuid);
        }

        if (this.plugin.getScoreboardOwnershipCoordinator() != null) {
            this.plugin.getScoreboardOwnershipCoordinator().clear(uuid);
        }

        this.plugin.getAPI().recoverPlayerBoatState(player);

        SchedulerHelper.runAsync(this.plugin, () -> {
            try {
                if (this.mysql.hasParty(uuid)) {
                    UUID owner = this.mysql.getOwner(uuid);
                    if (owner != null && owner.equals(uuid)) {
                        this.mysql.disbandParty(owner);
                    }
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logRaceSystem("Error processing party on quit: " + e.getMessage());
            }

        });
        if (this.plugin.getConfig().getBoolean("message-settings.quit.enabled", true)) {
            String msg = this.plugin.getConfig().getString("message-settings.quit.message", "[-] {rank} {player}").replace("{player}", playerName).replace("{rank}", rank);
            msg = this.plugin.applyPapi(player, msg);
            event.setQuitMessage(ChatColor.translateAlternateColorCodes('&', msg));
        } else {
            event.setQuitMessage((String)null);
        }

    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player player) {
            if (event.getSlot() == 36) {
                ItemStack currentBoots = player.getInventory().getBoots();
                if (currentBoots != null && currentBoots.getType() == Material.LEATHER_BOOTS) {
                    event.setCancelled(true);
                }
            }
        }

    }
}

