package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
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

        // --- Lógica de Argumentos Dinâmicos ---

        int finalLaps;
        int finalPits;

        if (laps == null) {
            // Caso: /voterace <track>
            finalLaps = 3;
            finalPits = 0;
        } else if (pits == null) {
            // Caso: /voterace <track> <laps>
            finalLaps = laps;
            finalPits = 0;
        } else {
            // Caso: /voterace <track> <laps> <pits>
            finalLaps = laps;
            finalPits = pits;
        }

        // Envia a proposta para o manager com os valores calculados
        voteManager.propose(player, trackName, finalLaps, finalPits);

        // Opcional: Avisar no chat o que foi criado para confirmar
        player.sendMessage(String.format("§aVotação iniciada: %s (%d voltas, %d pits)", trackName, finalLaps, finalPits));
    }
}