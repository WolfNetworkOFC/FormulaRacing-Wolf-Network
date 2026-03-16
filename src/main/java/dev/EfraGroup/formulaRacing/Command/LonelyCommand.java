package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.entity.Player;

@CommandAlias("lonely")
@Description("Controlador de sessões solo e isolamento de pista.")
public class LonelyCommand extends BaseCommand {
    private final FormulaRacing plugin;

    public LonelyCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }
    @Default
    @CatchUnknown
    public void onDefault(Player player) {
   if (plugin.getLonelyController().isLonely(player.getUniqueId())) {
       plugin.getLonelyController().setLonelyMode(player, false);
   } else {
                plugin.getLonelyController().setLonelyMode(player, true);
            }
    }
}
