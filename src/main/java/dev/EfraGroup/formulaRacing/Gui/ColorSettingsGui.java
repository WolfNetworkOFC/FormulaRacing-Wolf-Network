package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeDefaults;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.function.Consumer;

public class ColorSettingsGui extends BaseGui {

    public ColorSettingsGui(FormulaRacing plugin, Player player) {
        super(getTitle(plugin, player), 3);
        setButtons(plugin, player);
    }

    private static String getTitle(FormulaRacing plugin, Player player) {
        String langCode = plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
        return plugin.getTranslation("gui_color_title", langCode, new String[0]);
    }

    private void setButtons(FormulaRacing plugin, Player player) {
        String langCode = plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
        String currentColor1 = plugin.getDatabaseManager().getPlayerColor1(player.getUniqueId());
        int slot = 0;

        for (DyeColor dyeColor : DyeColor.values()) {
            Material dye = Material.valueOf(dyeColor.name() + "_DYE");
            String hex = String.format("#%02X%02X%02X",
                    dyeColor.getColor().getRed(),
                    dyeColor.getColor().getGreen(),
                    dyeColor.getColor().getBlue());

            String colorName = dyeColor.name().replace("_", " ").toLowerCase();
            String itemName = "§r" + hex + " " + capitalize(colorName);
            String lore = plugin.getTranslation("gui_color_lore", langCode, new String[0]);

            if (hex.equalsIgnoreCase(currentColor1)) {
                lore = plugin.getTranslation("gui_color_current", langCode, new String[]{hex});
            }

            final String selectedHex = hex;
            addButton(slot, dye, itemName, lore, event -> {
                Player p = (Player) event.getWhoClicked();
                plugin.getDatabaseManager().setPlayerColor1(p.getUniqueId(), selectedHex);
                FRThemeResolver.invalidate(p.getUniqueId());
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.2f, 1.0f);
                new SettingsMenu(plugin, p).show(p);
            });

            slot++;
        }

        addButton(22, Material.ARROW,
                plugin.getTranslation("gui_color_back_name", langCode, new String[0]),
                plugin.getTranslation("gui_color_back_lore", langCode, new String[0]),
                event -> {
                    Player p = (Player) event.getWhoClicked();
                    new SettingsMenu(plugin, p).show(p);
                });

        addBorder(plugin, player);
    }

    private void addBorder(FormulaRacing plugin, Player player) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            border.setItemMeta(meta);
        }
        GuiButton borderButton = new GuiButton(border, e -> {});

        for (int i = 0; i < 27; i++) {
            if (i < 16 || i > 20) {
                if (i != 22) {
                    setItem(borderButton, i);
                }
            }
        }
    }

    private void addButton(int slot, Material mat, String name, String lore, Consumer<InventoryClickEvent> action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(lore));
            item.setItemMeta(meta);
        }
        setItem(new GuiButton(item, action), slot);
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
