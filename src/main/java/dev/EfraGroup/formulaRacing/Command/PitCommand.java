package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Default;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.entity.Player;

@CommandAlias("pit")
@Description("Solicita uma parada nos boxes.")
public class PitCommand extends BaseCommand {
    private final FormulaRacing plugin;

    public PitCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    public void onPit(Player player) {
        plugin.getPitStopManager().startTestMinigame(player);
    }
}