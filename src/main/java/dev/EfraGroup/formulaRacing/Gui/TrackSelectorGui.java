//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TrackSelectorGui extends BaseGui {
    public TrackSelectorGui(FormulaRacing plugin, Player player, Consumer<String> onTrackSelected) {
        super(plugin.getTranslation("gui_title_track_selector", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]), 6);
        this.setupContent(plugin, player, onTrackSelected);
    }

    private void setupContent(FormulaRacing plugin, Player player, Consumer<String> callback) {
        DatabaseManager db = plugin.getDatabaseManager();
        Map<String, DatabaseManager.TrackData> tracks = db.getAllTracksWithData();
        String langCode = db.getPlayerLanguage(player.getUniqueId());
        int slot = 0;

        for(Map.Entry<String, DatabaseManager.TrackData> entry : tracks.entrySet()) {
            if (slot >= 54) {
                break;
            }

            String trackName = (String)entry.getKey();
            if (db.isTrackOpen(trackName)) {
                String iconName = db.getIcon(trackName);

                Material iconMat;
                try {
                    iconMat = Material.valueOf(iconName.toUpperCase());
                } catch (Exception var15) {
                    iconMat = Material.PAPER;
                }

                ItemStack item = new ItemStack(iconMat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§e" + trackName);
                    meta.setLore(plugin.getTranslationList("gui_track_lore_select", langCode, new String[0]));
                    item.setItemMeta(meta);
                }

                this.setItem(new GuiButton(item, (event) -> {
                    event.getWhoClicked().closeInventory();
                    callback.accept(trackName);
                }), slot++);
            }
        }

    }
}
