//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import java.util.Collections;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SettingsMenu extends BaseGui {
    private final FormulaRacing plugin;
    private final DatabaseManager dm;

    public SettingsMenu(FormulaRacing plugin, Player player) {
        super(plugin.getTranslation("gui_title_settings", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]), 4);
        this.plugin = plugin;
        this.dm = plugin.getDatabaseManager();
    }

    public void show(Player player) {
        this.setupContent(player);
        super.show(player);
    }

    private void setupContent(Player player) {
        this.getInventory().clear();
        String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
        boolean sounds = true;
        this.addButton(11, Material.BOOK, this.plugin.getTranslation("gui_settings_lang_name", langCode, new String[0]), this.plugin.getTranslation("gui_settings_lang_lore", langCode, new String[0]), (event) -> {
            Player p = (Player)event.getWhoClicked();
            p.closeInventory();
            (new LanguageGui(this.plugin, p)).show(p);
        });
        boolean ttEnabled = this.dm.getTimeTrialEnabled(player.getUniqueId());
        String ttName = this.plugin.getTranslation("gui_settings_tt_name", langCode, new String[0]);
        this.addToggle(13, Material.CLOCK, ttName, ttEnabled, player, (event) -> {
            Player p = (Player)event.getWhoClicked();
            boolean newState = !this.dm.getTimeTrialEnabled(p.getUniqueId());
            this.dm.setTimeTrialEnabled(p.getUniqueId(), newState);
            this.setupContent(p);
            String stateStr = newState ? this.plugin.getTranslation("gui_settings_status_enabled", langCode, new String[0]) : this.plugin.getTranslation("gui_settings_status_disabled", langCode, new String[0]);
            p.sendMessage(this.plugin.getTranslation("gui_settings_tt_toggled", langCode, new String[]{"{state}", stateStr}));
        });
        this.addButton(15, Material.NOTE_BLOCK, this.plugin.getTranslation("gui_settings_sounds_name", langCode, new String[0]), this.plugin.getTranslation("gui_settings_sounds_lore", langCode, new String[0]), (event) -> {
            Player p = (Player)event.getWhoClicked();
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
            p.sendMessage(this.plugin.getTranslation("gui_settings_sounds_wip", langCode, new String[0]));
        });
        boolean compact = this.dm.getPlayerCompactMode(player.getUniqueId());
        String compactName = this.plugin.getTranslation("gui_settings_compact_name", langCode, new String[0]);
        this.addToggle(12, Material.MAP, compactName, compact, player, (event) -> {
            Player p = (Player)event.getWhoClicked();
            boolean newState = !this.dm.getPlayerCompactMode(p.getUniqueId());
            this.dm.setPlayerCompactMode(p.getUniqueId(), newState);
            this.setupContent(p);
            String stateStr = newState ? this.plugin.getTranslation("gui_settings_status_on", langCode, new String[0]) : this.plugin.getTranslation("gui_settings_status_off", langCode, new String[0]);
            p.sendMessage(this.plugin.getTranslation("gui_settings_compact_toggled", langCode, new String[]{"{state}", stateStr}));
        });
        this.addButton(14, Material.PAINTING, this.plugin.getTranslation("gui_settings_color_name", langCode, new String[0]), this.plugin.getTranslation("gui_settings_color_lore", langCode, new String[0]), (event) -> {
            Player p = (Player)event.getWhoClicked();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.2F, 1.0F);
            (new ColorSettingsGui(this.plugin, p)).show(p);
        });
        this.addButton(31, Material.ARROW, this.plugin.getTranslation("gui_settings_back_name", langCode, new String[0]), this.plugin.getTranslation("gui_settings_back_lore", langCode, new String[0]), (event) -> {
            Player p = (Player)event.getWhoClicked();
            (new MainMenu(this.plugin, p)).show(p);
        });
    }

    private void addButton(int slot, Material mat, String name, String lore, Consumer<InventoryClickEvent> action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(lore));
            item.setItemMeta(meta);
        }

        this.setItem(new GuiButton(item, action), slot);
    }

    private void addToggle(int slot, Material baseMat, String name, boolean state, Player player, Consumer<InventoryClickEvent> action) {
        String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
        String status = state ? this.plugin.getTranslation("gui_settings_status_on", langCode, new String[0]) : this.plugin.getTranslation("gui_settings_status_off", langCode, new String[0]);
        this.addButton(slot, baseMat, name, "Estado: " + status, action);
    }
}
