//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Gui.Framework;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GuiListener implements Listener {
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player player) {
            BaseGui gui = GuiManager.getInstance().getOpenGui(player);
            if (gui != null) {
                if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof BaseGui) {
                    event.setCancelled(true);
                    gui.handleButton(event);
                } else if (event.isShiftClick() && event.getView().getTopInventory().getHolder() instanceof BaseGui) {
                    event.setCancelled(true);
                }
            }

        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player player) {
            BaseGui gui = GuiManager.getInstance().getOpenGui(player);
            if (gui != null && event.getView().getTopInventory().getHolder() instanceof BaseGui) {
                boolean involvesGui = event.getRawSlots().stream().anyMatch((slot) -> slot < event.getView().getTopInventory().getSize());
                if (involvesGui) {
                    event.setCancelled(true);
                }
            }

        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity var3 = event.getPlayer();
        if (var3 instanceof Player player) {
            BaseGui gui = GuiManager.getInstance().getOpenGui(player);
            if (gui != null && event.getInventory().getHolder() == gui) {
                GuiManager.getInstance().removeOpenGui(player);
            }

        }
    }
}
