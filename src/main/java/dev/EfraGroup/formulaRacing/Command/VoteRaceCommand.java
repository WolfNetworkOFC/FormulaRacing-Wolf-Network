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
    @Description("Starts a vote or votes on an active race.")
    @CommandPermission("formularacing.voterace")
    @CommandCompletion("@tracks <laps> <pits>")
    public void onVoteRace(Player player, @Optional String trackName, @Optional Integer laps, @Optional Integer pits) {

        // 1. If a vote is already active, the command just votes "Yes"
        if (voteManager.isProposalActive()) {
            voteManager.vote(player);
            return;
        }

        // 2. No active vote and no track specified - show correct usage
        if (trackName == null || trackName.isEmpty()) {
            player.sendMessage("§cUse: /voterace <track> [laps] [pits]");
            return;
        }

        // --- TRACK VALIDATION ---
        // Check in DatabaseManager if the track exists before proceeding
        if (plugin.getDatabaseManager().getTrackData(trackName) == null) {
            player.sendMessage("§c§lERROR: §7The track §f" + trackName + " §7does not exist in the system!");
            return;
        }

        // --- Dynamic Argument Logic ---
        int finalLaps = (laps == null) ? 3 : laps;
        int finalPits = (pits == null) ? 0 : pits;

        // Send the proposal to the manager with the calculated values
        // propose() will only be called if the track is valid
        boolean success = voteManager.propose(player, trackName, finalLaps, finalPits);

        if (success) {
            player.sendMessage(String.format("§a§lVOTE STARTED: §f%s §7(%d laps, %d pits)", trackName, finalLaps, finalPits));
        }
    }
}
