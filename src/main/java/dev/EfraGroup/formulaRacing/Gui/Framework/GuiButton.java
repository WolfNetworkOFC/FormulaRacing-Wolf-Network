//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Gui.Framework;

import java.util.function.Consumer;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class GuiButton {
    private final ItemStack stack;
    private Consumer<InventoryClickEvent> action;

    public GuiButton(ItemStack stack) {
        this.stack = stack;
    }

    public GuiButton(ItemStack stack, Consumer<InventoryClickEvent> action) {
        this.stack = stack;
        this.action = action;
    }

    public ItemStack getStack() {
        return this.stack;
    }

    public Consumer<InventoryClickEvent> getAction() {
        return this.action;
    }

    public void setAction(Consumer<InventoryClickEvent> action) {
        this.action = action;
    }

    public void run(InventoryClickEvent event) {
        if (this.action != null) {
            this.action.accept(event);
        }

    }
}
