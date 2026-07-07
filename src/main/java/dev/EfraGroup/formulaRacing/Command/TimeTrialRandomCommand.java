package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Default;
import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@CommandAlias("timetrialrandom|ttr|timetrialr|ttrandom")
@Description("Joins a random Time Trial")
public class TimeTrialRandomCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final APIFormulaRacing api;
    private final PacketSender packetsender;
    private final TimerUtils timerUtils;
    private final ScoreboardTimeTrialUtils stt;
    private final Random random = new Random();

    public TimeTrialRandomCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.mysql = plugin.getDatabaseManager();
        this.packetsender = plugin.getPacketSender();
        this.timerUtils = plugin.getTimerUtils();
        this.api = plugin.getAPI();
        this.stt = plugin.getScoreboardTimeTrialUtils();
    }

    @Default
    public void onRandom(Player player) {

        // ⛔ ACTIVE DUEL BLOCK
        if (mysql.isPlayerInActiveDuel(player.getUniqueId())) {
            player.sendMessage("§c§lERROR §8» §7You cannot use the random command while in an active §b§lDUEL§7!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        List<String> availableTracks = mysql.getAllTracks();
        if (availableTracks == null || availableTracks.isEmpty()) {
            player.sendMessage("§cNo tracks available at the moment.");
            return;
        }

        boolean hasBoatUtils = FormulaRacing.hasOpenBoatUtilsMod(player);

        // Filter compatible tracks
        List<String> validTracks = availableTracks.stream()
                .filter(mysql::isTrackOpen)
                .filter(trackName -> hasBoatUtils || !mysql.trackHaveBoatUtils(trackName))
                .collect(Collectors.toList());

        if (validTracks.isEmpty()) {
            player.sendMessage("§cNo compatible tracks available for you at the moment.");
            return;
        }

        // Random selection
        String trackName = validTracks.get(random.nextInt(validTracks.size()));

        // Package and Scoreboard settings
        packetsender.sendBoatSetting(player, 0);
        packetsender.applyBoatUtilsToPlayer(player, trackName);

        Location loc = mysql.getTrackSpawn(trackName);
        if (loc == null) {
            player.sendMessage("§cThe selected track has no spawn set.");
            return;
        }

        // Clear previous session
        timerUtils.stopTimer(player);
        if (plugin.getTimeTrialController() != null) {
            plugin.getTimeTrialController().endSession(player);
        }

        stt.setPlayerTrack(player, trackName, mysql.getTrackOwner(trackName));
        stt.show(player, trackName);

        // 🚤 Vehicle Management
        api.recoverPlayerBoatState(player);

        // Teleport and Messages
        SchedulerHelper.teleportAsync(player, loc).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                api.spawnBoatAt(player, loc, false, false, false);
            }
        });
        String langCode = mysql.getPlayerLanguage(player.getUniqueId());
        player.sendMessage(plugin.getTranslation("timetrial_teleport", langCode, "{track}", trackName));

        // Spawn new boat and persist
        plugin.setLastTimeTrialTrack(player.getUniqueId(), trackName);
    }
}
