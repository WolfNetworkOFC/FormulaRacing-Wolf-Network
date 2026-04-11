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
    public void onVoteRace(Player player, @Optional String trackName) {

        // Se já existe uma votação ativa
        if (voteManager.isProposalActive()) {
            voteManager.vote(player);
            return;
        }

        // Se não tem votação ativa e ele não passou a pista
        if (trackName == null || trackName.isEmpty()) {
            // Aqui você pode chamar o sendMessage do seu plugin
            player.sendMessage("§cUse: /voterace <pista>");
            return;
        }

        // Cria a proposta com 5 voltas e 1 pit (padrão)
        // Se quiser que o 30% funcione, certifique-se que o Manager está usando 
        // a lógica que fizemos antes!
        voteManager.propose(player, trackName, 5, 1);
    }
}