package dev.EfraGroup.formulaRacing.Gui.Framework;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class GuiManager {
    private final Map<UUID, BaseGui> openGuis = new HashMap();
    private final Map<UUID, StoredInventory> hiddenInventories = new HashMap();

    public GuiManager() {
    }

    public void setOpenGui(Player player, BaseGui gui) {
        this.openGuis.put(player.getUniqueId(), gui);
    }

    public BaseGui getOpenGui(Player player) {
        return this.openGuis.get(player.getUniqueId());
    }

    public void removeOpenGui(Player player) {
        this.openGuis.remove(player.getUniqueId());
    }

    /**
     * Snapshots and clears the player's inventory so GUI menus (like /settings)
     * don't show the player's items while they are open. The inventory is given
     * back by {@link #restoreInventory(Player)}. Calling this while an inventory
     * is already hidden is a no-op, so chained menus keep the same snapshot.
     */
    public void hideInventory(Player player) {
        this.hiddenInventories.computeIfAbsent(
            player.getUniqueId(),
            uuid -> new StoredInventory(player.getInventory(), player.getItemOnCursor())
        );
    }

    public boolean isInventoryHidden(Player player) {
        return this.hiddenInventories.containsKey(player.getUniqueId());
    }

    /**
     * Gives back a previously hidden inventory, if any.
     */
    public void restoreInventory(Player player) {
        StoredInventory stored = this.hiddenInventories.remove(player.getUniqueId());
        if (stored != null && player.isOnline()) {
            stored.restore(player);
        }
    }

    public void closeAll() {
        for (UUID uuid : new HashSet<>(this.openGuis.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.closeInventory();
            }
        }

        this.openGuis.clear();

        for (UUID uuid : new HashSet<>(this.hiddenInventories.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                this.restoreInventory(p);
            }
        }

        this.hiddenInventories.clear();
    }

    private static final class StoredInventory {
        private final ItemStack[] storageContents;
        private final ItemStack[] armorContents;
        private final ItemStack offHandItem;
        private final ItemStack cursorItem;

        private StoredInventory(PlayerInventory inventory, ItemStack cursorItem) {
            this.storageContents = cloneItems(inventory.getStorageContents());
            this.armorContents = cloneItems(inventory.getArmorContents());
            this.offHandItem = inventory.getItemInOffHand() == null ? null : inventory.getItemInOffHand().clone();
            this.cursorItem = cursorItem == null ? null : cursorItem.clone();
        }

        private void restore(Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setStorageContents(this.storageContents);
            inventory.setArmorContents(this.armorContents);
            inventory.setItemInOffHand(this.offHandItem);
            player.setItemOnCursor(this.cursorItem);
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] clone = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                clone[i] = items[i] == null ? null : items[i].clone();
            }
            return clone;
        }
    }
}
