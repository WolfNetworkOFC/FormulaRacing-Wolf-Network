//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.RaceVoteManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

@CommandAlias("voterace")
@Description("Sistema de votação para Quick Races")
public class VoteRaceCommand extends BaseCommand {
    private final FormulaRacing plugin;
    private final RaceVoteManager voteManager;

    public VoteRaceCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.voteManager = plugin.getRaceVoteManager();
    }

    @Default
    @CatchUnknown
    public void onDefault(Player player) {
        if (this.voteManager.isProposalActive()) {
            this.voteManager.showProposalStatus(player);
        } else {
            this.sendHelp(player);
        }

    }

    @Subcommand("start|new|create")
    @Description("Inicia votação (DEPRECATED)")
    public void onStart(Player player) {
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "⚠ Comando obsoleto!");
        String var10001 = String.valueOf(ChatColor.GRAY);
        player.sendMessage(var10001 + "Use " + String.valueOf(ChatColor.WHITE) + "/race propose <pista> [voltas] [pits]" + String.valueOf(ChatColor.GRAY) + " para criar uma proposta.");
        var10001 = String.valueOf(ChatColor.GRAY);
        player.sendMessage(var10001 + "Exemplo: " + String.valueOf(ChatColor.WHITE) + "/race propose Monaco 5 1");
    }

    @Subcommand("vote|v")
    @Description("Vota na proposta ativa")
    public void onVote(Player player) {
        this.voteManager.vote(player);
    }

    @Subcommand("status|info|votes")
    @Description("Ver status da votação")
    public void onStatus(Player player) {
        this.voteManager.showProposalStatus(player);
    }

    @Subcommand("cancel|stop|end")
    @CommandPermission("formularacing.voterace.cancel")
    @Description("Cancela votação ativa")
    public void onCancel(Player player) {
        this.voteManager.cancelProposal(player);
    }

    private void sendHelp(Player player) {
        player.sendMessage("");
        String var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
        var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "    COMANDOS /VOTERACE");
        var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "/voterace" + String.valueOf(ChatColor.GRAY) + " - Ver proposta ativa");
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "/voterace vote" + String.valueOf(ChatColor.GRAY) + " - Votar na proposta");
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "/voterace status" + String.valueOf(ChatColor.GRAY) + " - Ver status da proposta");
        if (player.hasPermission("formularacing.voterace.cancel")) {
            player.sendMessage("");
            var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══ ADMIN ═══");
            var10001 = String.valueOf(ChatColor.YELLOW);
            player.sendMessage(var10001 + "/voterace cancel" + String.valueOf(ChatColor.GRAY) + " - Cancelar proposta ativa");
        }

        player.sendMessage("");
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "\ud83d\udca1 Para criar propostas, use:");
        player.sendMessage(String.valueOf(ChatColor.WHITE) + "/race propose <pista> [voltas] [pits]");
        player.sendMessage(String.valueOf(ChatColor.GRAY) + "Exemplo: /race propose Monaco 5 1");
        player.sendMessage("");
        player.sendMessage(String.valueOf(ChatColor.GRAY) + "Após a proposta atingir votos suficientes,");
        player.sendMessage(String.valueOf(ChatColor.GRAY) + "uma Quick Race será criada automaticamente!");
        var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
        player.sendMessage("");
    }
}
