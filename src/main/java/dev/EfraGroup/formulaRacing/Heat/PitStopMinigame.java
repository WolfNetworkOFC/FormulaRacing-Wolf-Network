package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Config.PitStopConfigManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PitStopMinigame {
    private final Player player;
    private final UUID playerId;
    private final Inventory inventory;
    private int totalClicks;
    private int misclicks;
    private final Set<Integer> targetSlots;
    private final Set<Integer> clickedSlots;
    private final int totalTargets;
    private final Runnable onComplete;
    private final long startTime;
    private boolean completed;
    private final FormulaRacing plugin;
    private final List<ItemStack> targetItems;
    private final ItemStack wrongItem;
    private final int inventorySize;
    private final int minTargets;
    private final int maxTargets;
    private final boolean shuffleOnError;
    private static final Random random = new Random();

    public PitStopMinigame(FormulaRacing plugin, Player player, PitStopConfigManager.PitConfig cfg, Runnable onComplete) {
        this.plugin = plugin;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.onComplete = onComplete;
        this.inventorySize = cfg.inventorySize();
        this.minTargets = cfg.minTargets();
        this.maxTargets = cfg.maxTargets();
        this.targetItems = cfg.targetItems();
        this.wrongItem = cfg.wrongItem();
        this.shuffleOnError = cfg.shuffleOnError();
        this.targetSlots = new HashSet();
        this.clickedSlots = new HashSet();
        this.startTime = System.currentTimeMillis();
        this.completed = false;
        this.totalClicks = 0;
        this.misclicks = 0;
        this.totalTargets = this.minTargets;
        this.inventory = Bukkit.createInventory((InventoryHolder)null, this.inventorySize, plugin.getTranslation("pit_minigame_title", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]));
        this.generateTargetSlots();
        this.fillInventory();
        player.openInventory(this.inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.5F);
    }

    private void generateTargetSlots() {
        List<Integer> availableSlots = new ArrayList();

        for(int i = 0; i < this.inventorySize; ++i) {
            availableSlots.add(i);
        }

        Collections.shuffle(availableSlots);

        for(int i = 0; i < this.totalTargets; ++i) {
            this.targetSlots.add((Integer)availableSlots.get(i));
        }

    }

    private void fillInventory() {
        this.inventory.clear();

        for(int i = 0; i < this.inventorySize; ++i) {
            if (this.targetSlots.contains(i)) {
                if (!this.clickedSlots.contains(i)) {
                    this.inventory.setItem(i, this.createTargetItem());
                } else {
                    this.inventory.setItem(i, this.createCompletedItem());
                }
            } else {
                this.inventory.setItem(i, this.createWrongItem());
            }
        }

    }

    private ItemStack createTargetItem() {
        ItemStack base = (ItemStack)this.targetItems.get(random.nextInt(this.targetItems.size()));
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = meta.hasDisplayName() ? meta.getDisplayName() : "";
            if (!displayName.contains("CLIQUE")) {
                meta.setDisplayName(this.plugin.getTranslation("pit_item_click_name", this.plugin.getDatabaseManager().getPlayerLanguage(this.player.getUniqueId()), new String[0]));
            }

            List<String> lore = (List<String>)(meta.hasLore() ? meta.getLore() : new ArrayList());
            if (lore == null) {
                lore = new ArrayList();
            }

            if (lore.isEmpty() || !((String)lore.get(0)).contains("Clique")) {
                lore.add(this.plugin.getTranslation("pit_item_click_lore1", this.plugin.getDatabaseManager().getPlayerLanguage(this.player.getUniqueId()), new String[0]));
                lore.add(this.plugin.getTranslation("pit_item_click_lore2", this.plugin.getDatabaseManager().getPlayerLanguage(this.player.getUniqueId()), new String[0]));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createWrongItem() {
        ItemStack item = this.wrongItem.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName()) {
                meta.setDisplayName("§7");
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createCompletedItem() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§l✓");
            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean handleClick(int slot) {
        if (this.completed) {
            return true;
        } else {
            ++this.totalClicks;
            if (this.targetSlots.contains(slot) && !this.clickedSlots.contains(slot)) {
                this.clickedSlots.add(slot);
                this.inventory.setItem(slot, this.createCompletedItem());
                this.player.playSound(this.player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
                if (this.clickedSlots.size() >= this.totalTargets) {
                    this.complete();
                    return true;
                }
            } else if (!this.targetSlots.contains(slot)) {
                ++this.misclicks;
                if (this.shuffleOnError) {
                    this.randomizeInventory();
                }

                this.player.playSound(this.player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.8F);
                this.player.playSound(this.player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.8F);
                this.plugin.sendMessage(this.player, "pit_error_longer", new String[0]);
            }

            return false;
        }
    }

    private void randomizeInventory() {
        this.targetSlots.clear();
        int remainingTargets = this.totalTargets - this.clickedSlots.size();
        new Random();
        List<Integer> availableSlots = new ArrayList();

        for(int i = 0; i < this.inventorySize; ++i) {
            if (!this.clickedSlots.contains(i)) {
                availableSlots.add(i);
            }
        }

        Collections.shuffle(availableSlots);
        this.targetSlots.addAll(this.clickedSlots);

        for(int i = 0; i < remainingTargets && i < availableSlots.size(); ++i) {
            this.targetSlots.add((Integer)availableSlots.get(i));
        }

        this.fillInventory();
        this.fillInventory();
    }

    private void complete() {
        this.completed = true;
        long duration = System.currentTimeMillis() - this.startTime;
        double seconds = (double)duration / (double)1000.0F;
        int accuracy = this.totalClicks == 0 ? 0 : Math.round((float)this.clickedSlots.size() / (float)this.totalClicks * 100.0F);
        this.player.closeInventory();
        this.player.closeInventory();
        this.player.playSound(this.player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0F, 1.0F);
        this.plugin.sendMessage(this.player, "pit_complete_msg", new String[]{"{time}", String.format(Locale.US, "%.2f", seconds), "{accuracy}", String.valueOf(accuracy), "{misclicks}", String.valueOf(this.misclicks)});
        if (this.onComplete != null) {
            this.onComplete.run();
        }

    }

    public void cancel() {
        if (!this.completed) {
            this.completed = true;
            this.player.closeInventory();
            this.plugin.sendMessage(this.player, "pit_cancel_msg", new String[0]);
        }

    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public long getDuration() {
        return System.currentTimeMillis() - this.startTime;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}
