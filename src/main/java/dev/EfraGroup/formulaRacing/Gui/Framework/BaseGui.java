package dev.EfraGroup.formulaRacing.Gui.Framework;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class BaseGui implements InventoryHolder {
    protected final Inventory inventory;
    protected final String title;
    protected final List<GuiButton> buttons = new ArrayList();
    protected final FormulaRacing plugin;

    public BaseGui(String title, int rows) {
        this.title = title;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
        this.plugin = (FormulaRacing)FormulaRacing.getPlugin(FormulaRacing.class);
    }

    public void setItem(GuiButton button, int slot) {
        this.buttons.add(button);
        this.inventory.setItem(slot, button.getStack());
    }

    public void addItem(GuiButton button) {
        this.buttons.add(button);
        this.inventory.addItem(new ItemStack[]{button.getStack()});
    }

    public void removeItem(int slot) {
        ItemStack item = this.inventory.getItem(slot);
        if (item != null) {
            this.buttons.removeIf((b) -> b.getStack().isSimilar(item));
            this.inventory.setItem(slot, (ItemStack)null);
        }
    }

    public boolean handleButton(InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null) {
            return false;
        } else {
            for(GuiButton button : this.buttons) {
                if (button.getStack().isSimilar(currentItem)) {
                    button.run(event);
                    return true;
                }
            }

            return false;
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
