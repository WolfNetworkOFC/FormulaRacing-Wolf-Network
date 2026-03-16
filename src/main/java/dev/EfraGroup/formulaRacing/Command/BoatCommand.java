package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import org.bukkit.entity.Player;

@CommandAlias("boat") // Define o comando e um alias opcional
@Description("Spawna um barco de corrida na sua posição")
public class BoatCommand extends BaseCommand {

    private final APIFormulaRacing api;

    public BoatCommand(APIFormulaRacing api) {
        this.api = api;
    }

    @Default // Comando executado ao digitar apenas /boat
    public void onSpawnBoat(Player player) {
        api.spawnBoat(player, true, false, true);
        player.sendMessage("§a[FormulaRacing] Barco de corrida spawnado!");
    }
}