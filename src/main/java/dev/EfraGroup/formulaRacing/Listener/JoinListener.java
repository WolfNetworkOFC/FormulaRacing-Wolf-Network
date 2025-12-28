package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // 1. Mensagem de Join Customizada (Corrigida e limpa)
        if (plugin.getConfig().getBoolean("message-when-enter", true)) {
            event.setJoinMessage("§8[§a+§8] §a" + playerName);
        } else {
            event.setJoinMessage(null);
        }

        // 2. Primeira vez no servidor
        if (!player.hasPlayedBefore()) {
            Bukkit.broadcastMessage("§e" + playerName + " entrou pela primeira vez no servidor!");
        }

        // 3. Processamento de Banco de Dados (Assíncrono)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!mysql.playerExists(uuid)) {
                    mysql.insertPlayer(uuid, playerName);
                }
                // Reseta o estado do evento selecionado ao entrar
                mysql.setSelectedEvent(uuid, null);
            } catch (Exception e) {
                plugin.getLogger().severe("Erro no banco ao processar join de " + playerName + ": " + e.getMessage());
            }
        });

        // 4. Equipamento (Bota de Couro)
        try {
            player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        } catch (Exception e) {
            plugin.getLogger().warning("Não foi possível entregar as botas para " + playerName);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // 1. LIMPEZA DE MEMÓRIA (Imediato - Main Thread)
        plugin.getTimerUtils().stopTimer(player);
        plugin.getTimerUtils().clearTempCheckpoints(uuid);

        if (player.isInsideVehicle()) {
            player.getVehicle().remove();
        }

        // 3. OPERAÇÕES DE BANCO DE DADOS (Assíncrono para evitar LAG)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Verifica se o jogador está em uma party e se é o dono
                if (mysql.hasParty(uuid)) {
                    UUID owner = mysql.getOwner(uuid);
                    if (owner != null && owner.equals(uuid)) {
                        mysql.disbandParty(owner);
                        // plugin.getLogger().info("Party de " + playerName + " dissolvida (saída do servidor).");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Erro ao processar party de " + playerName + " no quit: " + e.getMessage());
            }
        });

        // 4. MENSAGEM DE SAÍDA (Corrigida para bater com o estilo do Join)
        if (plugin.getConfig().getBoolean("message-when-enter", true)) {
            event.setQuitMessage("§8[§c-§8] §c" + playerName);
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
