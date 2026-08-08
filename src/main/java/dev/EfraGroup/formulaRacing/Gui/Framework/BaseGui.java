package dev.EfraGroup.formulaRacing.Gui.Framework;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class BaseGui implements InventoryHolder {
    protected final Inventory inventory;
    protected final String title;
    protected final Map<Integer, GuiButton> buttons = new HashMap();
    protected final FormulaRacing plugin;

    public BaseGui(String title, int rows) {
        this.title = title;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
        this.plugin = (FormulaRacing)FormulaRacing.getPlugin(FormulaRacing.class);
    }

    public void setItem(GuiButton button, int slot) {
        this.buttons.put(slot, button);
        this.inventory.setItem(slot, button.getStack());
    }

    public void addItem(GuiButton button) {
        int slot = this.inventory.firstEmpty();
        if (slot >= 0) {
            this.buttons.put(slot, button);
            this.inventory.setItem(slot, button.getStack());
        }
    }

    public void removeItem(int slot) {
        ItemStack item = this.inventory.getItem(slot);
        if (item != null) {
            this.buttons.remove(slot);
            this.inventory.setItem(slot, (ItemStack)null);
        }
    }

    public boolean handleButton(InventoryClickEvent event) {
        GuiButton button = this.buttons.get(event.getSlot());
        if (button == null) {
            return false;
        } else {
            button.run(event);
            return true;
        }
    }

    public void show(Player player) {
        this.plugin.getGuiManager().setOpenGui(player, this);
        player.openInventory(this.inventory);
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}
