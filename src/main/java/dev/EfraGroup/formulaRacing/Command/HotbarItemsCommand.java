package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import org.bukkit.entity.Player;

@CommandAlias("hotbaritems")
public class HotbarItemsCommand extends BaseCommand {
    private final FormulaRacing plugin;

    public HotbarItemsCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    public void onCommand(Player player) {
        this.plugin.getHotbarController().giveHotbarItems(player);
    }
}
