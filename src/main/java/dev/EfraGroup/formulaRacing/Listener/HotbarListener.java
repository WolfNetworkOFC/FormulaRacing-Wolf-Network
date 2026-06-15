package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.HotbarController;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class HotbarListener implements Listener {
    private final FormulaRacing plugin;
    private final HotbarController controller;

    public HotbarListener(FormulaRacing plugin, HotbarController controller) {
        this.plugin = plugin;
        this.controller = controller;
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) {
            if (event.getAction() != Action.PHYSICAL) {
                if (event.getItem() != null && event.getItem().getType() != Material.AIR && this.controller.isHotbarItem(event.getItem())) {
                    event.setCancelled(true);
                    this.controller.handleInteraction(event.getPlayer(), event.getItem());
                }

            }
        }
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item != null && item.getType() != Material.AIR && this.controller.isHotbarItem(item)) {
                event.setCancelled(true);
                this.controller.handleInteraction(player, item);
            }

        }
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onVehicleDamage(EntityDamageByEntityEvent event) {
        Entity var3 = event.getDamager();
        if (var3 instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item != null && item.getType() != Material.AIR && this.controller.isHotbarItem(item) && event.getEntity() instanceof Vehicle) {
                event.setCancelled(true);
                this.controller.handleInteraction(player, item);
            }

        }
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            if (this.controller.isHotbarItem(event.getItemDrop().getItemStack())) {
                event.setCancelled(true);
                this.controller.handleInteraction(event.getPlayer(), event.getItemDrop().getItemStack());
            }

        }
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            if (this.controller.isHotbarItem(event.getOffHandItem())) {
                event.setCancelled(true);
                this.controller.handleInteraction(event.getPlayer(), event.getOffHandItem());
            } else {
                if (this.controller.isHotbarItem(event.getMainHandItem())) {
                    event.setCancelled(true);
                    this.controller.handleInteraction(event.getPlayer(), event.getMainHandItem());
                }

            }
        }
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onInventoryDrag(InventoryDragEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player player) {
            if (player.getGameMode() == GameMode.CREATIVE || player.hasPermission("formularacing.admin")) {
                return;
            }

            if (this.controller.isHotbarItem(event.getOldCursor())) {
                event.setCancelled(true);
                return;
            }

            for(int slot : event.getInventorySlots()) {
                if (this.controller.isHotbarItem(event.getView().getItem(slot))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player player) {
            if (player.getGameMode() == GameMode.CREATIVE || player.hasPermission("formularacing.admin")) {
                return;
            }

            if (this.controller.isHotbarItem(event.getCurrentItem())) {
                event.setCancelled(true);
            }

            if (event.getAction() == InventoryAction.HOTBAR_SWAP || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
                int slot = event.getHotbarButton();
                if (slot != -1 && this.controller.isHotbarItem(player.getInventory().getItem(slot))) {
                    event.setCancelled(true);
                }
            }
        }

    }
}
