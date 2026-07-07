package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
import dev.EfraGroup.formulaRacing.Controllers.DailyRaceManager;
import dev.EfraGroup.formulaRacing.Controllers.DailyRaceManager.Phase;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

@CommandAlias("daily")
@CommandPermission("formularacing.admin.daily")
@Description("Daily Race commands")
public class DailyRaceCommand extends BaseCommand {

    private final DailyRaceManager dailyRaceManager;

    public DailyRaceCommand(FormulaRacing plugin) {
        this.dailyRaceManager = plugin.getDailyRaceManager();
    }

    @Default
    @CatchUnknown
    public void onDefault(CommandSender sender) {
        sendHelp(sender);
    }

    @Subcommand("help|ajuda|?")
    @Description("Shows daily command help")
    public void onHelp(CommandSender sender) {
        sendHelp(sender);
    }

    @Subcommand("force")
    @Description("Forces the start of a daily race")
    public void onForce(CommandSender sender) {
        if (dailyRaceManager.getPhase() != Phase.IDLE) {
            sender.sendMessage(ChatColor.RED + "A Daily Race is already in progress!");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Forcing the start of the Daily Race...");
        dailyRaceManager.forceStart();
    }

    @Subcommand("stop|end")
    @Description("Ends the current daily race")
    public void onStop(CommandSender sender) {
        dailyRaceManager.stopDaily();
        sender.sendMessage(ChatColor.GREEN + "Daily Race ended successfully!");
    }

    @Subcommand("status")
    @Description("Shows daily race status")
    public void onStatus(CommandSender sender) {
        Phase phase = dailyRaceManager.getPhase();
        sender.sendMessage(ChatColor.GOLD + "=== Daily Race Status ===");
        sender.sendMessage(ChatColor.GRAY + "Current phase: " + ChatColor.WHITE + phase.name());

        dailyRaceManager.getActiveDailyEvent().ifPresentOrElse(event -> {
            sender.sendMessage(ChatColor.GRAY + "Active Event: " + ChatColor.WHITE + event.getDisplayName());
            sender.sendMessage(ChatColor.GRAY + "Track: " + ChatColor.WHITE + event.getTrackNameWS());
            sender.sendMessage(ChatColor.GRAY + "Entrants: " + ChatColor.WHITE + event.getSubscriberCount());
        }, () -> sender.sendMessage(ChatColor.GRAY + "No active event at the moment."));
    }

    @Subcommand("reload")
    @Description("Reloads the configuration")
    public void onReload(CommandSender sender) {
        dailyRaceManager.reload();
        sender.sendMessage(ChatColor.GREEN + "Daily Race configuration reloaded!");
    }

    @Subcommand("skip")
    @Description("Skips the current stage of the daily race")
    public void onSkip(CommandSender sender) {
        if (dailyRaceManager.getPhase() == Phase.IDLE) {
            sender.sendMessage(ChatColor.RED + "There is no active Daily Race to skip stages.");
            return;
        }

        if (dailyRaceManager.skipPhase()) {
            sender.sendMessage(ChatColor.GREEN + "Stage skipped successfully!");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to skip stage.");
        }
    }

    @Subcommand("exclude add")
    @Description("Excludes a track from the Daily Race")
    @CommandCompletion("@tracks")
    public void onExcludeAdd(CommandSender sender, String trackName) {
        if (dailyRaceManager.addExcludedTrack(trackName)) {
            sender.sendMessage(ChatColor.GREEN + "Track " + ChatColor.WHITE + trackName + ChatColor.GREEN + " added to exclusion.");
        } else {
            sender.sendMessage(ChatColor.RED + "This track is already in the list or the name is invalid.");
        }
    }

    @Subcommand("exclude remove")
    @Description("Removes a track from the exclusion list")
    @CommandCompletion("@tracks")
    public void onExcludeRemove(CommandSender sender, String trackName) {
        if (dailyRaceManager.removeExcludedTrack(trackName)) {
            sender.sendMessage(ChatColor.GREEN + "Track " + ChatColor.WHITE + trackName + ChatColor.GREEN + " removed from exclusion.");
        } else {
            sender.sendMessage(ChatColor.RED + "This track is not in the exclusion list.");
        }
    }

    @Subcommand("exclude list")
    @Description("Lists excluded tracks")
    public void onExcludeList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Excluded Tracks ===");
        dailyRaceManager.getExcludedTracks().forEach(t ->
                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + t));
    }

    private void sendHelp(CommandSender sender) {
        CommandHelpService.sendHelp(sender, this, "/daily");
    }
}
