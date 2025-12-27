package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BoatSelectGui implements Listener {

    private final DatabaseManager db;
    private final JavaPlugin plugin;
    private final String INVENTORY_TITLE = ChatColor.AQUA + "Selecione seu barco";
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    // Lista de barcos disponíveis
    private static final List<BoatType> BOAT_TYPES = List.of(
            new BoatType(1, "Oak Boat", Material.OAK_BOAT),
            new BoatType(2, "Birch Boat", Material.BIRCH_BOAT),
            new BoatType(3, "Spruce Boat", Material.SPRUCE_BOAT),
            new BoatType(4, "Jungle Boat", Material.JUNGLE_BOAT),
            new BoatType(5, "Acacia Boat", Material.ACACIA_BOAT),
            new BoatType(6, "Dark Oak Boat", Material.DARK_OAK_BOAT),
            new BoatType(7, "Mangrove Boat", Material.MANGROVE_BOAT),
            new BoatType(8, "Cherry Boat", Material.CHERRY_BOAT),
            new BoatType(9, "Bamboo Raft", Material.BAMBOO_RAFT),
            new BoatType(10, "Oak Chest Boat", Material.OAK_CHEST_BOAT),
            new BoatType(11, "Birch Chest Boat", Material.BIRCH_CHEST_BOAT),
            new BoatType(12, "Spruce Chest Boat", Material.SPRUCE_CHEST_BOAT),
            new BoatType(13, "Jungle Chest Boat", Material.JUNGLE_CHEST_BOAT),
            new BoatType(14, "Acacia Chest Boat", Material.ACACIA_CHEST_BOAT),
            new BoatType(15, "Dark Oak Chest Boat", Material.DARK_OAK_CHEST_BOAT),
            new BoatType(16, "Mangrove Chest Boat", Material.MANGROVE_CHEST_BOAT),
            new BoatType(17, "Cherry Chest Boat", Material.CHERRY_CHEST_BOAT),
            new BoatType(18, "Bamboo Chest Raft", Material.BAMBOO_CHEST_RAFT)
    );

    public BoatSelectGui(DatabaseManager db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Abre o menu de seleção de barco */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, INVENTORY_TITLE);

        for (BoatType boat : BOAT_TYPES) {
            ItemStack item = new ItemStack(boat.material());
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + boat.displayName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Clique para selecionar este barco.");
                lore.add(ChatColor.DARK_GRAY + "ID: " + boat.id());
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            inv.addItem(item);
        }

        player.openInventory(inv);
    }

    /** Verifica se o inventário é o menu de barcos */
    private boolean isBoatInventory(Inventory inv, String title) {
        if (inv == null || title == null) return false;
        return ChatColor.stripColor(title).equalsIgnoreCase(ChatColor.stripColor(INVENTORY_TITLE));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Inventory inv = event.getView().getTopInventory();

        if (!isBoatInventory(inv, title)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;

        Player player = (Player) event.getWhoClicked();

        // 🕒 Evita spam de cliques
        long now = System.currentTimeMillis();
        long last = clickCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 400) {
            player.sendMessage(ChatColor.RED + "Aguarde um instante antes de clicar novamente!");
            return;
        }
        clickCooldown.put(player.getUniqueId(), now);

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        BOAT_TYPES.stream()
                .filter(boat -> boat.displayName().equalsIgnoreCase(displayName))
                .findFirst()
                .ifPresent(boat -> {
                    db.setPlayerBoatType(player.getUniqueId(), boat.id());
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "Você selecionou o barco " +
                            ChatColor.YELLOW + boat.displayName() + ChatColor.GREEN + "!");
                });
    }

    /** Estrutura para armazenar barcos */
    private record BoatType(int id, String displayName, Material material) {}
}
