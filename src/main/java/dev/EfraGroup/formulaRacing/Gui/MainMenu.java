package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.QuickRaceManager;
import dev.EfraGroup.formulaRacing.Controllers.RaceVoteManager;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MainMenu extends BaseGui {
    private final FormulaRacing plugin;

    public MainMenu(FormulaRacing plugin, Player player) {
        super(plugin.getTranslation("gui_title_main_menu", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]), 4);
        this.plugin = plugin;
        this.setupContent(player);
    }

    private void setupContent(Player player) {
        String langCode = this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
        QuickRaceManager qrm = this.plugin.getQuickRaceManager();
        RaceVoteManager rvm = this.plugin.getRaceVoteManager();
        Material qrIcon;
        String qrName;
        List<String> qrLore;
        if (qrm.isQuickRaceActive()) {
            if (qrm.isQuickRaceRunning()) {
                qrIcon = Material.REDSTONE_BLOCK;
                qrName = this.plugin.getTranslation("gui_main_qr_running_name", langCode, new String[0]);
                qrLore = this.plugin.getTranslationList("gui_main_qr_running_lore", langCode, new String[0]);
            } else {
                qrIcon = Material.EMERALD_BLOCK;
                qrName = this.plugin.getTranslation("gui_main_qr_lobby_name", langCode, new String[0]);
                qrLore = this.plugin.getTranslationList("gui_main_qr_lobby_lore", langCode, new String[0]);
            }
        } else if (rvm.isProposalActive()) {
            qrIcon = Material.BOOK;
            qrName = this.plugin.getTranslation("gui_main_qr_voting_name", langCode, new String[0]);
            qrLore = this.plugin.getTranslationList("gui_main_qr_voting_lore", langCode, new String[0]);
        } else {
            qrIcon = Material.SUGAR;
            qrName = this.plugin.getTranslation("gui_main_qr_none_name", langCode, new String[0]);
            qrLore = this.plugin.getTranslationList("gui_main_qr_none_lore", langCode, new String[0]);
        }

        this.addButton(11, qrIcon, qrName, (List)qrLore, (event) -> {
            Player p = (Player)event.getWhoClicked();
            p.closeInventory();
            if (qrm.isQuickRaceActive()) {
                p.performCommand("race join");
            } else if (rvm.isProposalActive()) {
                p.performCommand("race vote");
            } else {
                (new QuickRaceConfigGui(this.plugin, p)).show(p);
            }

        });
        int trackCount = this.plugin.getDatabaseManager().getAllTracks().size();
        List<String> ttLore = new ArrayList();
        ttLore.add(this.plugin.getTranslation("gui_main_tt_lore_header", langCode, new String[0]));
        ttLore.add("");
        ttLore.add(this.plugin.getTranslation("gui_main_tt_lore_count", langCode, new String[]{"{count}", String.valueOf(trackCount)}));
        ttLore.add(this.plugin.getTranslation("gui_main_tt_lore_action", langCode, new String[0]));
        this.addButton(13, Material.FILLED_MAP, this.plugin.getTranslation("gui_main_tt_name", langCode, new String[0]), (List)ttLore, (event) -> {
            Player p = (Player)event.getWhoClicked();
            p.closeInventory();
            p.performCommand("tt");
        });
        this.addButton(15, Material.IRON_SWORD, this.plugin.getTranslation("gui_main_duels_name", langCode, new String[0]), this.plugin.getTranslation("gui_main_duels_lore", langCode, new String[0]), (event) -> {
            Player p = (Player)event.getWhoClicked();
            (new DuelPlayerSelectGui(this.plugin, p)).show(p);
        });
        this.addButton(22, Material.COMPARATOR, this.plugin.getTranslation("gui_main_settings_name", langCode, new String[0]), this.plugin.getTranslation("gui_main_settings_lore", langCode, new String[0]), (event) -> {
            Player p = (Player)event.getWhoClicked();
            (new SettingsMenu(this.plugin, p)).show(p);
        });
    }

    private void addButton(int slot, Material mat, String name, String lore, Consumer<InventoryClickEvent> action) {
        this.addButton(slot, mat, name, Collections.singletonList(lore), action);
    }

    private void addButton(int slot, Material mat, String name, List<String> lore, Consumer<InventoryClickEvent> action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        this.setItem(new GuiButton(item, action), slot);
    }
}
