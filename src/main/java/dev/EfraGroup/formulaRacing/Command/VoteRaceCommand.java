package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.Controllers.RaceVoteManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.entity.Player;

public class VoteRaceCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final RaceVoteManager voteManager;

    public VoteRaceCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.voteManager = plugin.getRaceVoteManager();
    }

    @CommandAlias("voterace|vr")
    @Description("Inicia uma votação ou vota em uma corrida ativa.")
    @CommandPermission("formularacing.voterace")
    @CommandCompletion("@tracks laps pits")
    public void onVoteRace(Player player, @Optional String trackName, @Optional Integer laps, @Optional Integer pits) {

        // 1. Se já existe uma votação ativa, o comando serve apenas para votar "Sim"
        if (voteManager.isProposalActive()) {
            voteManager.vote(player);
            return;
        }

        // 2. Se não tem votação ativa e ele não passou a pista, mostra o uso correto
        if (trackName == null || trackName.isEmpty()) {
            player.sendMessage("§cUse: /voterace <pista> [voltas] [pits]");
            return;
        }

        // --- VALIDAÇÃO DA PISTA ---
        // Checamos no DatabaseManager se a pista existe antes de continuar
        if (plugin.getDatabaseManager().getTrackData(trackName) == null) {
            player.sendMessage("§c§lERRO: §7A pista §f" + trackName + " §7não existe no sistema!");
            return;
        }

        // --- Lógica de Argumentos Dinâmicos ---
        int finalLaps = (laps == null) ? 3 : laps;
        int finalPits = (pits == null) ? 0 : pits;

        // Envia a proposta para o manager com os valores calculados
        // O propose() agora só será chamado se a pista for válida
        boolean success = voteManager.propose(player, trackName, finalLaps, finalPits);

        if (success) {
            player.sendMessage(String.format("§a§lVOTAÇÃO INICIADA: §f%s §7(%d voltas, %d pits)", trackName, finalLaps, finalPits));
        }
    }
}