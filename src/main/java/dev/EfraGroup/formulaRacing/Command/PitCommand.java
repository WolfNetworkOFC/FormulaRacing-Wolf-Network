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
        // O ACF já valida se o sender é um Player automaticamente pelo tipo do parâmetro
        player.sendMessage("§a[FormulaRacing] §fVocê solicitou uma parada nos boxes!");

        handlePitStop(player);
    }

    private void handlePitStop(Player player) {
        plugin.getPitStopManager().startTestMinigame(player);
    }
}