//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class BoatSelectGui extends BaseGui {
    private final DatabaseManager db;
    private static final String INVENTORY_TITLE;
    private static final Map<UUID, Long> clickCooldown;
    private static final List<BoatType> BOAT_TYPES;

    public BoatSelectGui(DatabaseManager db, JavaPlugin plugin) {
        super(INVENTORY_TITLE, 3);
        this.db = db;
        this.setupContent();
    }

    private void setupContent() {
        for(BoatType boat : BOAT_TYPES) {
            ItemStack item = new ItemStack(boat.material());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String var10001 = String.valueOf(ChatColor.GREEN);
                meta.setDisplayName(var10001 + boat.displayName());
                List<String> lore = new ArrayList();
                lore.add(String.valueOf(ChatColor.GRAY) + "Clique para selecionar este barco.");
                var10001 = String.valueOf(ChatColor.DARK_GRAY);
                lore.add(var10001 + "ID: " + boat.id());
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            GuiButton button = new GuiButton(item, (event) -> {
                Player player = (Player)event.getWhoClicked();
                long now = System.currentTimeMillis();
                long last = (Long)clickCooldown.getOrDefault(player.getUniqueId(), 0L);
                if (now - last < 400L) {
                    player.sendMessage(String.valueOf(ChatColor.RED) + "Aguarde um instante antes de clicar novamente!");
                } else {
                    clickCooldown.put(player.getUniqueId(), now);
                    this.db.setPlayerBoatType(player.getUniqueId(), boat.id());
                    player.closeInventory();
                    String var10001 = String.valueOf(ChatColor.GREEN);
                    player.sendMessage(var10001 + "Você selecionou o barco " + String.valueOf(ChatColor.YELLOW) + boat.displayName() + String.valueOf(ChatColor.GREEN) + "!");
                }
            });
            this.addItem(button);
        }

    }

    static {
        INVENTORY_TITLE = "" + String.valueOf(ChatColor.AQUA);
        clickCooldown = new HashMap();
        BOAT_TYPES = List.of(new BoatType(1, "Oak Boat", Material.OAK_BOAT), new BoatType(2, "Birch Boat", Material.BIRCH_BOAT), new BoatType(3, "Spruce Boat", Material.SPRUCE_BOAT), new BoatType(4, "Jungle Boat", Material.JUNGLE_BOAT), new BoatType(5, "Acacia Boat", Material.ACACIA_BOAT), new BoatType(6, "Dark Oak Boat", Material.DARK_OAK_BOAT), new BoatType(7, "Mangrove Boat", Material.MANGROVE_BOAT), new BoatType(8, "Cherry Boat", Material.CHERRY_BOAT), new BoatType(9, "Bamboo Raft", Material.BAMBOO_RAFT), new BoatType(10, "Oak Chest Boat", Material.OAK_CHEST_BOAT), new BoatType(11, "Birch Chest Boat", Material.BIRCH_CHEST_BOAT), new BoatType(12, "Spruce Chest Boat", Material.SPRUCE_CHEST_BOAT), new BoatType(13, "Jungle Chest Boat", Material.JUNGLE_CHEST_BOAT), new BoatType(14, "Acacia Chest Boat", Material.ACACIA_CHEST_BOAT), new BoatType(15, "Dark Oak Chest Boat", Material.DARK_OAK_CHEST_BOAT), new BoatType(16, "Mangrove Chest Boat", Material.MANGROVE_CHEST_BOAT), new BoatType(17, "Cherry Chest Boat", Material.CHERRY_CHEST_BOAT), new BoatType(18, "Bamboo Chest Raft", Material.BAMBOO_CHEST_RAFT));
    }

    private static record BoatType(int id, String displayName, Material material) {
    }
}
