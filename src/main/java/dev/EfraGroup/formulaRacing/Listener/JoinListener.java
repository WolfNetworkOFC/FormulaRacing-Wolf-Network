//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Controllers.HotbarController;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import java.sql.SQLException;
import java.util.UUID;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
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
    private final HotbarController hotbarController;

    public JoinListener(FormulaRacing plugin, DatabaseManager mysql, PacketSender packetSender, HotbarController hotbarController) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.packetSender = packetSender;
        this.hotbarController = hotbarController;
        this.hasLuckPerms = plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null;
    }

    private String getPlayerRank(UUID uuid) {
        if (!this.hasLuckPerms) {
            return "";
        } else {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(uuid);
            if (user == null) {
                return "";
            } else {
                CachedMetaData metaData = user.getCachedData().getMetaData();
                String prefix = metaData != null ? metaData.getPrefix() : null;
                if (prefix != null && !prefix.isEmpty()) {
                    return ChatColor.translateAlternateColorCodes('&', prefix);
                } else {
                    String primaryGroup = user.getPrimaryGroup();
                    return primaryGroup != null ? primaryGroup : "";
                }
            }
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
        event.setJoinMessage((String)null);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                boolean isFirstJoin = false;
                if (!this.mysql.playerExists(uuid)) {
                    this.mysql.insertPlayer(uuid, playerName);
                    isFirstJoin = true;
                }

                this.mysql.setSelectedEvent(uuid, (String)null);
                if (this.plugin.getConfig().getBoolean("message-settings.join.enabled", true)) {
                    String msg;
                    if (isFirstJoin) {
                        msg = this.plugin.getConfig().getString("message-settings.join.first-join-message", "[+] {rank} {player}").replace("{player}", playerName).replace("{rank}", rank);

                        for(Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            String playerLang = this.mysql.getPlayerLanguage(onlinePlayer.getUniqueId());
                            String welcomeMsg = "§e" + playerName + " " + this.plugin.getDirectTranslation("welcome_first_time", playerLang).replace("{player}", playerName).replace("{rank}", rank);
                            onlinePlayer.sendMessage(welcomeMsg);
                        }
                    } else {
                        msg = this.plugin.getConfig().getString("message-settings.join.message", "[+] {rank} {player}").replace("{player}", playerName).replace("{rank}", rank);
                    }

                    Bukkit.getScheduler().runTask(this.plugin, () -> Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg)));
                }

                boolean lonelyActive = this.mysql.getLonelyModePlayer(uuid);
                if (lonelyActive) {
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        this.plugin.getLonelyController().setLonelyMode(player, true);
                        this.plugin.getDebugManager().logRaceSystem("Modo lonely (DB) aplicado ao jogador " + playerName + " ao entrar.");
                    });
                }

                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                    if (player.isOnline()) {
                        this.plugin.getDailyRaceManager().notifyPlayerOfAllActiveEvents(player);
                    }

                }, 40L);
                this.plugin.getTranslationUtil().loadPlayerLanguage(uuid);
                Bukkit.getScheduler().runTask(this.plugin, () -> {
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
        this.plugin.getTranslationUtil().removePlayer(uuid);
        if (this.plugin.getRaceActionBarManager() != null) {
            this.plugin.getRaceActionBarManager().removePlayer(player);
        }

        if (this.plugin.getRaceScoreboardManager() != null) {
            this.plugin.getRaceScoreboardManager().removePlayer(player);
        }

        if (this.plugin.getScoreboardTimeTrialUtils() != null) {
            this.plugin.getScoreboardTimeTrialUtils().clearPlayerTrack(player);
        }

        if (this.plugin.getRaceEventManager() != null) {
            this.plugin.getRaceEventManager().leaveEvent(player);
        }

        if (this.plugin.getSpectatorManager() != null) {
            this.plugin.getSpectatorManager().handlePlayerDisconnect(uuid);
        }

        if (player.isInsideVehicle()) {
            player.getVehicle().remove();
        }

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
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
