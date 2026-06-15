package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class DuelPlayerSelectGui extends BaseGui {
    public DuelPlayerSelectGui(FormulaRacing plugin, Player viewer) {
        super(plugin.getTranslation("gui_title_duel_select", plugin.getDatabaseManager().getPlayerLanguage(viewer.getUniqueId()), new String[0]), 6);
        this.setupContent(plugin, viewer);
    }

    private void setupContent(FormulaRacing plugin, Player viewer) {
        List<Player> onlinePlayers = new ArrayList(Bukkit.getOnlinePlayers());
        onlinePlayers.remove(viewer);
        String langCode = plugin.getDatabaseManager().getPlayerLanguage(viewer.getUniqueId());
        if (onlinePlayers.isEmpty()) {
            this.setItem(new GuiButton(this.createItem(Material.BARRIER, plugin.getTranslation("gui_duel_none_online_name", langCode, new String[0]), plugin.getTranslation("gui_duel_none_online_lore", langCode, new String[0])), (event) -> {
            }), 22);
        } else {
            int slot = 0;

            for(Player target : onlinePlayers) {
                if (slot >= 54) {
                    break;
                }

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta)head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(target);
                    meta.setDisplayName("§e" + target.getName());
                    meta.setLore(plugin.getTranslationList("gui_duel_player_lore", langCode, new String[0]));
                    head.setItemMeta(meta);
                }

                this.setItem(new GuiButton(head, (event) -> {
                    viewer.closeInventory();
                    viewer.performCommand("duel " + target.getName());
                }), slot++);
            }

        }
    }

    private ItemStack createItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(lore));
            item.setItemMeta(meta);
        }

        return item;
    }
}
