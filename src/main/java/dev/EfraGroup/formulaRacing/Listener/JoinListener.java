package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.UUID;

public class JoinListener implements Listener {

    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final PacketSender packetSender;

    public JoinListener(FormulaRacing plugin, DatabaseManager mysql, PacketSender packetSender) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.packetSender = packetSender;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Mensagem de join
        if (plugin.getConfig().getBoolean("message-when-enter", true)) {
            event.setJoinMessage(ChatColor.DARK_GRAY + "["
                    + ChatColor.GREEN + "+"
                    + ChatColor.DARK_GRAY + "] "
                    + ChatColor.GREEN + player.getName());
        } else {
            event.setJoinMessage(null); // desativa msg padrão
        }

    mysql.setSelectedEvent(player.getUniqueId(), null);

        // Primeira vez no servidor
        if (!player.hasPlayedBefore()) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " entrou pela primeira vez no servidor!");
        }

        // Inserir no banco de dados apenas se ainda não existir
        if (!mysql.playerExists(player.getUniqueId())) {
            boolean success = mysql.insertPlayer(player.getUniqueId(), player.getName());
            if (success) {
                plugin.getLogger().info("Jogador " + player.getName() + " inserido na tabela fr_players.");
            } else {
                plugin.getLogger().warning("Falha ao inserir jogador " + player.getName() + " na tabela fr_players.");
            }
        }

        // Checar e colocar bota de couro que não pode ser removida
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType() != Material.LEATHER_BOOTS) {
            ItemStack lockedBoots = new ItemStack(Material.LEATHER_BOOTS);
            ItemMeta meta = lockedBoots.getItemMeta();
            if (meta != null) {
                lockedBoots.setItemMeta(meta);
            }
            player.getInventory().setBoots(lockedBoots);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // =================================================================
        // 1. LIMPEZA DE MEMÓRIA (ESSENCIAL PARA EVITAR OS 15GB)
        // =================================================================

        // Remove o jogador do loop e deleta o Cache (RaceSessionCache)
        plugin.getTimerUtils().stopTimer(player);

        // Limpa os HashMaps temporários de Checkpoint
        plugin.getTimerUtils().clearTempCheckpoints(uuid);

        // (Opcional) Se ele sair no meio da corrida, deleta o barco para não lagar o mapa
        if (player.isInsideVehicle()) {
            player.getVehicle().remove();
        }
        try {
            // Verifica se o jogador tem party
            if (mysql.hasParty(uuid)) { // Usei a variavel uuid que criei ali em cima

                UUID owner = mysql.getOwner(uuid);

                // Se ele for o dono da party, dissolve
                if (owner != null && owner.equals(uuid)) {
                    mysql.disbandParty(owner);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Mensagem de saída
        if (plugin.getConfig().getBoolean("message-when-enter", true)) {
            event.setQuitMessage(ChatColor.DARK_GRAY + "["
                    + ChatColor.RED + "-"
                    + ChatColor.DARK_GRAY + "] "
                    + ChatColor.RED + player.getName());
        } else {
            event.setQuitMessage(null);
        }
    }


    // Impede que o jogador tire a bota
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (event.getSlot() == 36) { // slot de botas
                ItemStack currentBoots = player.getInventory().getBoots();
                if (currentBoots != null && currentBoots.getType() == Material.LEATHER_BOOTS) {
                    event.setCancelled(true); // impede retirar
                }
            }
        }
    }
}
