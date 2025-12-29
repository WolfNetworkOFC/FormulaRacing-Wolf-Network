package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TrackCommandHandler implements CommandExecutor {

    private final DatabaseManager dbManager;
    private final FormulaRacing plugin;

    public TrackCommandHandler(DatabaseManager dbManager, FormulaRacing plugin) {
        this.dbManager = dbManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        String langCode = dbManager.getPlayerLanguage(player.getUniqueId());

        if (args.length == 0) {
            player.sendMessage(plugin.getDirectTranslation("track_usage", langCode));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // ========================
            // 📜 /track times
            // ========================
            case "times": {
                if (args.length < 2) {
                    player.sendMessage(plugin.getDirectTranslation("track_times_usage", langCode));
                    return true;
                }

                String trackName = args[1];
                int page = 1;

                if (args.length >= 3) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.getDirectTranslation("invalid_number", langCode));
                        return true;
                    }
                }

                List<Map<String, Object>> times = dbManager.getAllTimesOnTrack(trackName, page);

                if (times.isEmpty()) {
                    player.sendMessage(plugin.getTranslation("track_no_times", langCode, "{track}", trackName));
                    return true;
                }

                player.sendMessage(plugin.getTranslation("track_times_title", langCode,
                    "{track}", trackName, "{page}", String.valueOf(page)));
                for (Map<String, Object> entry : times) {
                    int pos = (int) entry.get("pos");
                    String pname = (String) entry.get("player");
                    double time = (double) entry.get("time");
                    int cp = (int) entry.get("checkpoints");
                    boolean finished = (boolean) entry.get("finished");

                    String formatted = finished
                            ? String.format("§e#%d §7» §a%s §8— §f%.3fs §8(✓)", pos, pname, time)
                            : String.format("§e#%d §7» §a%s §8— §f%.3fs §7(%dCP)", pos, pname, time, cp);

                    player.sendMessage(formatted);
                }
                return true;
            }

            // ========================
            // 🧍 /track mytimes
            // ========================
            case "mytimes": {
                if (args.length < 2) {
                    player.sendMessage(plugin.getDirectTranslation("track_mytimes_usage", langCode));
                    return true;
                }

                String trackName = args[1];
                int page = 1;

                if (args.length >= 3) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.getDirectTranslation("invalid_number", langCode));
                        return true;
                    }
                }

                List<Map<String, Object>> times = dbManager.getAllTimesOnTrackByPlayer(trackName, player.getName(), page);

                if (times.isEmpty()) {
                    player.sendMessage(plugin.getTranslation("track_no_personal_times", langCode, "{track}", trackName));
                    return true;
                }

                player.sendMessage(plugin.getTranslation("track_mytimes_title", langCode,
                    "{track}", trackName, "{page}", String.valueOf(page)));
                for (Map<String, Object> entry : times) {
                    int pos = (int) entry.get("pos");
                    double time = (double) entry.get("time");
                    int cp = (int) entry.get("checkpoints");
                    boolean finished = (boolean) entry.get("finished");
                    String date = (String) entry.get("date");

                    String formatted = finished
                            ? String.format("§e#%d §8— §f%.3fs §8(✓) §7[%s]", pos, time, date)
                            : String.format("§e#%d §8— §f%.3fs §7(%dCP) §7[%s]", pos, time, cp, date);

                    player.sendMessage(formatted);
                }
                return true;
            }

            // ========================
            // ❌ /track deletebesttime <track> <player>
            // ========================
            case "deletebesttime": {
                if (!player.hasPermission("formularacing.admin")) {
                    player.sendMessage(plugin.getDirectTranslation("no_permission", langCode));
                    return true;
                }

                if (args.length < 3) {
                    player.sendMessage(plugin.getDirectTranslation("track_deletebesttime_usage", langCode));
                    return true;
                }

                String track = args[1];
                String targetPlayer = args[2];

                boolean success = dbManager.deletePlayerBestTimeOnTrack(track, targetPlayer);
                if (success) {
                    player.sendMessage(plugin.getTranslation("track_besttime_deleted", langCode,
                        "{player}", targetPlayer, "{track}", track));
                } else {
                    player.sendMessage(plugin.getTranslation("track_besttime_not_found", langCode,
                        "{player}", targetPlayer, "{track}", track));
                }
                return true;
            }

            // ========================
            // ❌ /track deletealltimes <track> [player]
            // ========================
            case "deletealltimes": {
                if (!player.hasPermission("formularacing.admin")) {
                    player.sendMessage(plugin.getDirectTranslation("no_permission", langCode));
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(plugin.getDirectTranslation("track_deletealltimes_usage", langCode));
                    return true;
                }

                String track = args[1];
                String targetPlayer = args.length >= 3 ? args[2] : null;
                boolean success = dbManager.deleteAllTimes(track, targetPlayer);

                if (success) {
                    if (targetPlayer != null)
                        player.sendMessage(plugin.getTranslation("track_alltimes_deleted_player", langCode,
                            "{player}", targetPlayer, "{track}", track));
                    else
                        player.sendMessage(plugin.getTranslation("track_alltimes_deleted", langCode,
                            "{track}", track));
                } else {
                    player.sendMessage(plugin.getTranslation("track_no_times", langCode, "{track}", track));
                }
                return true;
            }

            // ========================
            // ❌ /track deleteallplayertimes <player>
            // ========================
            case "deleteallplayertimes": {
                if (!player.hasPermission("formularacing.admin")) {
                    player.sendMessage(plugin.getDirectTranslation("no_permission", langCode));
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(plugin.getDirectTranslation("track_deleteallplayertimes_usage", langCode));
                    return true;
                }

                String targetPlayer = args[1];
                boolean success = dbManager.deletePlayerAllTimes(targetPlayer);

                if (success) {
                    player.sendMessage(plugin.getTranslation("track_allplayertimes_deleted", langCode,
                        "{player}", targetPlayer));
                } else {
                    player.sendMessage(plugin.getTranslation("track_player_no_times", langCode,
                        "{player}", targetPlayer));
                }
                return true;
            }

            default:
                player.sendMessage(plugin.getDirectTranslation("track_usage", langCode));
                return true;
        }
    }
}
