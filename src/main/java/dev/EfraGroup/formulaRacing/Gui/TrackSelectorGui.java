package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
                DatabaseManager.TrackIconData iconData = db.getTrackIconData(trackName);
                // Name/lore on the same ItemMeta as the icon's block state (e.g. LIGHT
                // level) so the level isn't dropped by a second meta round-trip.
                ItemStack item = iconData.toItemStack(
                    "§e" + trackName,
                    plugin.getTranslationList("gui_track_lore_select", langCode, new String[0])
                );

                this.setItem(new GuiButton(item, (event) -> {
                    event.getWhoClicked().closeInventory();
                    callback.accept(trackName);
                }), slot++);
            }
        }

    }
}
