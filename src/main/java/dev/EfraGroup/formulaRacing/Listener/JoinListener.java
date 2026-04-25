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

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
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
        if (!this.hasLuckPerms) return "";

        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().getUser(uuid);
        if (user == null) return "";

        String primaryGroup = user.getPrimaryGroup();

        // Checagem para o rank Default
        if (primaryGroup != null && primaryGroup.equalsIgnoreCase("default")) {
                if (isFloodgatePlayer(uuid)) {
                    return ":bedrock:";
                } else {
                    return ":java:  ";
                }

        }

        // Lógica normal para outros cargos (VIP, Staff, etc)
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
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
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

    public boolean isFloodgatePlayer(UUID uuid) {
        // 1. Tenta pegar o jogador online primeiro (mais rápido)
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        String playerName;

        if (player != null) {
            playerName = player.getName();
        } else {
            // 2. Se o jogador não estiver online, busca nos registros do servidor
            playerName = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        }

        // 3. Verifica se o nome não é nulo e se começa com o ponto
        if (playerName != null && playerName.startsWith(".")) {
            return true;
        }

        // 4. Fallback: Se não começar com ponto, verificamos via API do Floodgate para garantir
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        }

        return false;
    }

    public void updatePlayerPrefix(Player player) {
        // 1. Obter a instância do LuckPerms
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());

        if (user == null) return;

        // 2. Obter o jogador na API do TAB
        TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(player.getUniqueId());
        if (tabPlayer == null) return;

        // 3. Verificar se o grupo primário é "default"
        String primaryGroup = user.getPrimaryGroup();

        if (primaryGroup.equalsIgnoreCase("default")) {
            if (isFloodgatePlayer(player.getUniqueId())){
                TabAPI.getInstance().getTabListFormatManager().setPrefix(tabPlayer, "%img_bedrock% §r");

            } else{
                TabAPI.getInstance().getTabListFormatManager().setPrefix(tabPlayer, "%img_java% §r");
            }
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
            this.plugin.getRaceEventManager().leaveEvent(player);
        }

        if (this.plugin.getSpectatorManager() != null) {
            this.plugin.getSpectatorManager().handlePlayerDisconnect(uuid);
        }

        if (this.plugin.getScoreboardOwnershipCoordinator() != null) {
            this.plugin.getScoreboardOwnershipCoordinator().clear(uuid);
        }

        this.plugin.getAPI().recoverPlayerBoatState(player);
        if (player.isInsideVehicle() && player.getVehicle() != null) {
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
