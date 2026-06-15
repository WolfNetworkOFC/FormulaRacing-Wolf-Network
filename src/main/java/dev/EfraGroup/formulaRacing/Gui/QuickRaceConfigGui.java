package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class QuickRaceConfigGui extends BaseGui {
    private final Player player;
    private final NamespacedKey KEY_TRACK = new NamespacedKey("formula", "qr_track");
    private final NamespacedKey KEY_LAPS = new NamespacedKey("formula", "qr_laps");
    private final NamespacedKey KEY_PITS = new NamespacedKey("formula", "qr_pits");

    public QuickRaceConfigGui(FormulaRacing plugin, Player player) {
        super(plugin.getTranslation("gui_title_quick_race", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]), 3);
        this.player = player;
        this.setupContent();
    }

    private void setupContent() {
        String langCode = ((FormulaRacing)FormulaRacing.getPlugin(FormulaRacing.class)).getDatabaseManager().getPlayerLanguage(this.player.getUniqueId());
        FormulaRacing plugin = (FormulaRacing)FormulaRacing.getPlugin(FormulaRacing.class);
        String track = (String)this.player.getPersistentDataContainer().getOrDefault(this.KEY_TRACK, PersistentDataType.STRING, "Nenhuma");
        int laps = (Integer)this.player.getPersistentDataContainer().getOrDefault(this.KEY_LAPS, PersistentDataType.INTEGER, 3);
        int pits = (Integer)this.player.getPersistentDataContainer().getOrDefault(this.KEY_PITS, PersistentDataType.INTEGER, 0);
        List<String> trackLore = plugin.getTranslationList("gui_qr_track_lore_selected", langCode, new String[]{"{track}", track});
        trackLore.add("");
        trackLore.addAll(plugin.getTranslationList("gui_qr_track_lore_click", langCode, new String[0]));
        this.setItem(10, this.createItem(Material.MAP, plugin.getTranslation("gui_qr_track_name", langCode, new String[0]), trackLore), (event) -> (new TrackSelectorGui(plugin, this.player, (selectedTrack) -> {
            this.player.getPersistentDataContainer().set(this.KEY_TRACK, PersistentDataType.STRING, selectedTrack);
            if (!plugin.getDatabaseManager().isCircuit(selectedTrack)) {
                this.player.getPersistentDataContainer().set(this.KEY_LAPS, PersistentDataType.INTEGER, 1);
                this.player.getPersistentDataContainer().set(this.KEY_PITS, PersistentDataType.INTEGER, 0);
                this.player.sendMessage("§e" + selectedTrack + " não é um circuito! Voltas definidas para 1 e pits desativados.");
            }

            (new QuickRaceConfigGui(plugin, this.player)).show(this.player);
        })).show(this.player));
        List<String> lapsLore = plugin.getTranslationList("gui_qr_laps_lore_current", langCode, new String[]{"{count}", String.valueOf(laps)});
        lapsLore.add("");
        lapsLore.addAll(plugin.getTranslationList("gui_qr_laps_lore_left", langCode, new String[0]));
        lapsLore.addAll(plugin.getTranslationList("gui_qr_laps_lore_right", langCode, new String[0]));
        this.setItem(12, this.createItem(Material.REPEATER, plugin.getTranslation("gui_qr_laps_name", langCode, new String[0]), lapsLore), (event) -> {
            if (!plugin.getDatabaseManager().isCircuit(track)) {
                this.player.sendMessage("§cEsta pista não é um circuito fechado (Sprint/Parkour). Apenas 1 volta é permitida.");
                this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.5F);
            } else {
                int newLaps = laps;
                if (event.isLeftClick()) {
                    newLaps = laps + 1;
                } else if (event.isRightClick()) {
                    newLaps = Math.max(1, laps - 1);
                }

                int currentPits = (Integer)this.player.getPersistentDataContainer().getOrDefault(this.KEY_PITS, PersistentDataType.INTEGER, 0);
                if (currentPits >= newLaps && currentPits >= newLaps) {
                    this.player.getPersistentDataContainer().set(this.KEY_PITS, PersistentDataType.INTEGER, Math.max(0, newLaps - 1));
                }

                this.player.getPersistentDataContainer().set(this.KEY_LAPS, PersistentDataType.INTEGER, newLaps);
                this.refresh();
                this.player.playSound(this.player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
            }
        });
        List<String> pitsLore = plugin.getTranslationList("gui_qr_pits_lore_current", langCode, new String[]{"{count}", String.valueOf(pits)});
        pitsLore.add("");
        pitsLore.addAll(plugin.getTranslationList("gui_qr_laps_lore_left", langCode, new String[0]));
        pitsLore.addAll(plugin.getTranslationList("gui_qr_laps_lore_right", langCode, new String[0]));
        pitsLore.add("");
        pitsLore.addAll(plugin.getTranslationList("gui_qr_pits_lore_note", langCode, new String[0]));
        this.setItem(14, this.createItem(Material.IRON_HOE, plugin.getTranslation("gui_qr_pits_name", langCode, new String[0]), pitsLore), (event) -> {
            if (!plugin.getDatabaseManager().isCircuit(track)) {
                this.player.sendMessage("§cEsta pista não é um circuito fechado (Sprint/Parkour). Pit Stops não são permitidos.");
                this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.5F);
            } else {
                int newPits = pits;
                if (event.isLeftClick()) {
                    newPits = pits + 1;
                } else if (event.isRightClick()) {
                    newPits = Math.max(0, pits - 1);
                }

                int currentLaps = (Integer)this.player.getPersistentDataContainer().getOrDefault(this.KEY_LAPS, PersistentDataType.INTEGER, 3);
                if (newPits >= currentLaps) {
                    this.player.sendMessage(plugin.getTranslation("gui_qr_pits_error_laps", langCode, new String[0]));
                    this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.5F);
                } else {
                    this.player.getPersistentDataContainer().set(this.KEY_PITS, PersistentDataType.INTEGER, newPits);
                    this.refresh();
                    this.player.playSound(this.player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                }
            }
        });
        List<String> startLore = plugin.getTranslationList("gui_qr_start_lore_track", langCode, new String[]{"{track}", track});
        startLore.addAll(plugin.getTranslationList("gui_qr_start_lore_laps", langCode, new String[]{"{laps}", String.valueOf(laps)}));
        startLore.addAll(plugin.getTranslationList("gui_qr_start_lore_pits", langCode, new String[]{"{pits}", String.valueOf(pits)}));
        startLore.add("");
        startLore.addAll(plugin.getTranslationList("gui_qr_start_lore_action", langCode, new String[0]));
        this.setItem(16, this.createItem(Material.EMERALD_BLOCK, plugin.getTranslation("gui_qr_start_name", langCode, new String[0]), startLore), (event) -> {
            if (track.equals("Nenhuma")) {
                this.player.sendMessage(plugin.getTranslation("gui_qr_start_error_track", langCode, new String[0]));
                this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.5F);
            } else {
                this.player.closeInventory();
                this.player.performCommand("race propose " + track + " " + laps + " " + pits);
            }
        });
        this.setItem(26, this.createItem(Material.BARRIER, plugin.getTranslation("gui_close_name", langCode, new String[0])), (event) -> this.player.closeInventory());
    }

    private void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        super.setItem(new GuiButton(item, action), slot);
    }

    private void refresh() {
        (new QuickRaceConfigGui(this.plugin, this.player)).show(this.player);
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        return this.createItem(material, name, Arrays.asList(lore));
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }
}
