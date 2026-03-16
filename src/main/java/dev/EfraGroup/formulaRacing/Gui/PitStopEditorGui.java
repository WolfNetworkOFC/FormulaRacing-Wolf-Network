//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Config.PitStopConfigManager;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PitStopEditorGui extends BaseGui {
    private final String trackName;
    private final PitStopConfigManager configManager;
    private PitStopConfigManager.PitConfig currentConfig;
    private final Player player;

    public PitStopEditorGui(FormulaRacing plugin, String trackName, Player player) {
        super(plugin.getTranslation("gui_pit_editor_title", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[]{"{track}", trackName == null ? "DEFAULT" : trackName}), 6);
        this.trackName = trackName;
        this.configManager = plugin.getPitStopManager().getPitConfigManager();
        this.currentConfig = this.configManager.getConfig(trackName);
        this.player = player;
        this.setupContent();
    }

    private void setupContent() {
        this.inventory.clear();
        this.buttons.clear();
        FormulaRacing plugin = (FormulaRacing)FormulaRacing.getPlugin(FormulaRacing.class);
        String langCode = plugin.getDatabaseManager().getPlayerLanguage(this.player.getUniqueId());
        this.addButton(10, this.createConfigIcon(Material.CHEST, plugin.getTranslation("gui_pit_editor_size_name", langCode, new String[]{"{size}", String.valueOf(this.currentConfig.inventorySize())}), String.valueOf(this.currentConfig.inventorySize()), plugin.getTranslation("gui_pit_editor_size_desc", langCode, new String[0]), plugin.getTranslation("gui_pit_editor_size_action", langCode, new String[0])), (e) -> {
            int next = this.currentConfig.inventorySize() + 9;
            if (next > 54) {
                next = 27;
            }

            this.configManager.updateConfig(this.trackName, "inventory_size", String.valueOf(next));
            this.refresh();
        });
        this.addButton(12, this.createConfigIcon(Material.TARGET, plugin.getTranslation("gui_pit_editor_min_targets_name", langCode, new String[]{"{value}", String.valueOf(this.currentConfig.minTargets())}), String.valueOf(this.currentConfig.minTargets()), plugin.getTranslation("gui_pit_editor_target_desc", langCode, new String[0]), plugin.getTranslation("gui_pit_editor_target_action", langCode, new String[0])), (e) -> {
            int val = this.currentConfig.minTargets();
            if (e.isRightClick()) {
                val = Math.max(1, val - 1);
            } else {
                val = Math.min(this.currentConfig.inventorySize(), val + 1);
            }

            this.configManager.updateConfig(this.trackName, "min_targets", String.valueOf(val));
            this.refresh();
        });
        this.addButton(13, this.createConfigIcon(Material.SPECTRAL_ARROW, plugin.getTranslation("gui_pit_editor_max_targets_name", langCode, new String[]{"{value}", String.valueOf(this.currentConfig.maxTargets())}), String.valueOf(this.currentConfig.maxTargets()), plugin.getTranslation("gui_pit_editor_target_desc", langCode, new String[0]), plugin.getTranslation("gui_pit_editor_target_action", langCode, new String[0])), (e) -> {
            int val = this.currentConfig.maxTargets();
            if (e.isRightClick()) {
                val = Math.max(this.currentConfig.minTargets(), val - 1);
            } else {
                val = Math.min(this.currentConfig.inventorySize(), val + 1);
            }

            this.configManager.updateConfig(this.trackName, "max_targets", String.valueOf(val));
            this.refresh();
        });
        String status = this.currentConfig.shuffleOnError() ? plugin.getTranslation("gui_pit_editor_status_on", langCode, new String[0]) : plugin.getTranslation("gui_pit_editor_status_off", langCode, new String[0]);
        this.addButton(16, this.createConfigIcon(Material.PISTON, plugin.getTranslation("gui_pit_editor_shuffle_name", langCode, new String[]{"{status}", status}), status, plugin.getTranslation("gui_pit_editor_shuffle_desc", langCode, new String[0]), plugin.getTranslation("gui_pit_editor_shuffle_action", langCode, new String[0])), (e) -> {
            this.configManager.updateConfig(this.trackName, "shuffle_on_error", String.valueOf(!this.currentConfig.shuffleOnError()));
            this.refresh();
        });
        ItemStack wrongDisplay = this.currentConfig.wrongItem().clone();
        ItemMeta wMeta = wrongDisplay.getItemMeta();
        wMeta.setDisplayName(plugin.getTranslation("gui_pit_editor_wrong_item_name", langCode, new String[0]));
        wMeta.setLore(plugin.getTranslationList("gui_pit_editor_wrong_item_lore", langCode, new String[0]));
        wrongDisplay.setItemMeta(wMeta);
        this.setItem(new GuiButton(wrongDisplay, (e) -> {
            Player p = (Player)e.getWhoClicked();
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand != null && hand.getType() != Material.AIR) {
                this.configManager.setWrongItem(this.trackName, hand.clone());
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 2.0F);
                this.refresh();
            } else {
                p.sendMessage(plugin.getTranslation("gui_pit_editor_error_hold_item", langCode, new String[0]));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            }
        }), 28);
        this.addButton(30, Material.EMERALD, plugin.getTranslation("gui_pit_editor_add_target_name", langCode, new String[0]), plugin.getTranslationList("gui_pit_editor_add_target_lore", langCode, new String[0]), (e) -> {
            Player p = (Player)e.getWhoClicked();
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand != null && hand.getType() != Material.AIR) {
                for(ItemStack existing : this.currentConfig.targetItems()) {
                    if (existing.isSimilar(hand)) {
                        p.sendMessage(plugin.getTranslation("gui_pit_editor_error_duplicate", langCode, new String[0]));
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                        return;
                    }
                }

                List<ItemStack> current = new ArrayList(this.currentConfig.targetItems());
                current.add(hand.clone());
                this.configManager.setTargetItems(this.trackName, current);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 2.0F);
                this.refresh();
            } else {
                p.sendMessage(plugin.getTranslation("gui_pit_editor_error_hold_item", langCode, new String[0]));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            }
        });
        int slot = 31;

        for(ItemStack target : this.currentConfig.targetItems()) {
            if (slot > 53) {
                break;
            }

            ItemStack display = target.clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = (List<String>)(meta.hasLore() ? meta.getLore() : new ArrayList());
            lore.add("");
            lore.add(plugin.getTranslation("gui_pit_editor_remove_target_lore", langCode, new String[0]));
            meta.setLore(lore);
            display.setItemMeta(meta);
            int finalIndex = slot - 31;
            this.setItem(new GuiButton(display, (e) -> {
                List<ItemStack> current = new ArrayList(this.currentConfig.targetItems());

                for(int i = 0; i < current.size(); ++i) {
                    if (((ItemStack)current.get(i)).isSimilar(target)) {
                        current.remove(i);
                        break;
                    }
                }

                this.configManager.setTargetItems(this.trackName, current);
                ((Player)e.getWhoClicked()).playSound(e.getWhoClicked().getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                this.refresh();
            }), slot);
            ++slot;
        }

    }

    private void refresh() {
        this.currentConfig = this.configManager.getConfig(this.trackName);
        this.setupContent();
    }

    private ItemStack createConfigIcon(Material mat, String name, String value, String desc, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Arrays.asList(ChatColor.translateAlternateColorCodes('&', desc), "", ChatColor.translateAlternateColorCodes('&', action)));
        item.setItemMeta(meta);
        return item;
    }

    private void addButton(int slot, Material mat, String name, List<String> lore, Consumer<InventoryClickEvent> action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> colored = new ArrayList();
        if (lore != null) {
            for(String l : lore) {
                colored.add(ChatColor.translateAlternateColorCodes('&', l));
            }
        }

        meta.setLore(colored);
        item.setItemMeta(meta);
        this.setItem(new GuiButton(item, action), slot);
    }

    private void addButton(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        this.setItem(new GuiButton(item, action), slot);
    }
}
