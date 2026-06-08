//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Gui.MainMenu;
import dev.EfraGroup.formulaRacing.Gui.SettingsMenu;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class HotbarController {
    private final FormulaRacing plugin;
    private final DatabaseManager dm;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> cooldowns = new HashMap();

    public HotbarController(FormulaRacing plugin, DatabaseManager dm) {
        this.plugin = plugin;
        this.dm = dm;
        this.itemKey = new NamespacedKey(plugin, "hotbar_item");
    }

    public void giveHotbarItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, this.createItem(Material.NETHER_STAR, this.plugin.getTranslationUtil().getTranslated(player, "hotbar_menu_name", new String[0]), "menu", this.plugin.getTranslationUtil().getTranslationList(player, "hotbar_menu_lore", new String[0])));
        player.getInventory().setItem(1, this.createItem(Material.FILLED_MAP, this.plugin.getTranslationUtil().getTranslated(player, "hotbar_tracks_name", new String[0]), "time_trial", this.plugin.getTranslationUtil().getTranslationList(player, "hotbar_tracks_lore", new String[0])));
        player.getInventory().setItem(2, this.createItem(Material.FIREWORK_ROCKET, this.plugin.getTranslationUtil().getTranslated(player, "hotbar_random_name", new String[0]), "random_track", this.plugin.getTranslationUtil().getTranslationList(player, "hotbar_random_lore", new String[0])));
        this.updateLonelyItem(player);
        player.getInventory().setItem(0, this.createItem(Material.DRAGON_BREATH, this.plugin.getTranslationUtil().getTranslated(player, "hotbar_reset_name", new String[0]), "reset", this.plugin.getTranslationUtil().getTranslationList(player, "hotbar_reset_lore", new String[0])));
        player.getInventory().setItem(7, this.createItem(Material.RED_BED, this.plugin.getTranslationUtil().getTranslated(player, "hotbar_spawn_name", new String[0]), "spawn", this.plugin.getTranslationUtil().getTranslationList(player, "hotbar_spawn_lore", new String[0])));
        player.getInventory().setItem(8, this.createItem(Material.COMPARATOR, this.plugin.getTranslationUtil().getTranslated(player, "hotbar_settings_name", new String[0]), "settings", this.plugin.getTranslationUtil().getTranslationList(player, "hotbar_settings_lore", new String[0])));

        try {
            player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        } catch (Exception var3) {
        }

    }

    public void updateLonelyItem(Player player) {
        boolean isLonely = this.dm.getLonelyModePlayer(player.getUniqueId());
        ItemStack item;
        if (isLonely) {
            item = this.createItem(Material.ENDER_PEARL, this.plugin.getTranslationUtil().getTranslated(player, "hotbar_visibility_off_name", new String[0]), "lonely_toggle", this.plugin.getTranslationUtil().getTranslationList(player, "hotbar_visibility_off_lore", new String[0]));
        } else {
            item = this.createItem(Material.ENDER_EYE, this.plugin.getTranslationUtil().getTranslated(player, "hotbar_visibility_on_name", new String[0]), "lonely_toggle", this.plugin.getTranslationUtil().getTranslationList(player, "hotbar_visibility_on_lore", new String[0]));
        }

        player.getInventory().setItem(4, item);
    }

    private ItemStack createItem(Material material, String name, String key, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            meta.setLore(lore.stream().map((l) -> ChatColor.translateAlternateColorCodes('&', l)).toList());
            meta.getPersistentDataContainer().set(this.itemKey, PersistentDataType.STRING, key);
            item.setItemMeta(meta);
        }

        return item;
    }

    public void handleInteraction(Player player, ItemStack item) {
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
            String key = (String)item.getItemMeta().getPersistentDataContainer().get(this.itemKey, PersistentDataType.STRING);
            if (key != null) {
                long now = System.currentTimeMillis();
                long lastClick = (Long)this.cooldowns.getOrDefault(player.getUniqueId(), 0L);
                if (now - lastClick >= 800L) {
                    this.cooldowns.put(player.getUniqueId(), now);
                    player.setCooldown(item.getType(), 16);
                    switch (key) {
                        case "menu":
                            (new MainMenu(this.plugin, player)).show(player);
                            break;
                        case "time_trial":
                            player.performCommand("tt");
                            break;
                        case "random_track":
                            player.performCommand("ttr");
                            break;
                        case "lonely_toggle":
                            boolean current = this.dm.getLonelyModePlayer(player.getUniqueId());
                            player.performCommand("lonely " + !current);
                            SchedulerHelper.runTaskLater(this.plugin, () -> this.updateLonelyItem(player), 15L);
                            break;
                        case "reset":
                            player.performCommand("reset");
                            break;
                        case "spawn":
                            player.performCommand("spawn");
                            break;
                        case "settings":
                            (new SettingsMenu(this.plugin, player)).show(player);
                    }

                }
            }
        }
    }

    public boolean isHotbarItem(ItemStack item) {
        return item != null && item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().has(this.itemKey, PersistentDataType.STRING) : false;
    }
}
