package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
import dev.EfraGroup.formulaRacing.Command.TimeTrialCommand;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Utils.SenderUtils;
import dev.EfraGroup.formulaRacing.Utils.TimeFormatter;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("track|t")
@Description("Gerenciamento de pistas")
public class TrackCommand extends BaseCommand {
    private final FormulaRacing plugin;
    private final DatabaseManager dbManager;

    public TrackCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.dbManager = plugin.getDatabaseManager();
    }

    private String formatTime(double timeInSeconds) {
        return TimeFormatter.formatTime(timeInSeconds);
    }

    /** Player's language when available, default language for the console. */
    private String lang(CommandSender sender) {
        Player player = SenderUtils.player(sender);
        return player != null ? this.dbManager.getPlayerLanguage(player.getUniqueId()) : "en_US";
    }

    @Default
    public void onDefault(CommandSender sender) {
        CommandHelpService.sendHelp(sender, this, "/track");
    }

    @CatchUnknown
    public void onUnknown(CommandSender sender) {
        this.onDefault(sender);
    }

    @Subcommand("help|ajuda|?")
    @Description("Mostra a ajuda do comando track")
    public void onHelp(CommandSender sender) {
        this.onDefault(sender);
    }

    @Subcommand("info")
    @CommandCompletion("@tracks")
    @Description("Mostra informacoes de uma pista")
    public void onInfo(CommandSender sender, String trackName) {
        if (!this.dbManager.isTrackExists(trackName)) {
            sender.sendMessage("§cPista '" + trackName + "' nao encontrada.");
            return;
        }
        String trackNameWS = trackName.replaceAll("\\s+", "").toLowerCase();
        String owner = this.dbManager.getTrackOwner(trackNameWS);
        sender.sendMessage("§6§l========== Track Info ==========");
        sender.sendMessage("§e Name: §f" + trackName);
        sender.sendMessage("§e Owner: §f" + (owner != null ? owner : "N/A"));
        List<String> tags = this.dbManager.getTrackTags(trackNameWS);
        if (!tags.isEmpty()) {
            sender.sendMessage("§e Tags: §f" + String.join("§e, §f", tags));
        }
        List<String> contribs = this.dbManager.getTrackContributors(trackNameWS);
        if (!contribs.isEmpty()) {
            StringBuilder cb = new StringBuilder("§e Contributors: §f");
            boolean first = true;
            for (String uuid : contribs) {
                if (!first) cb.append("§e, §f");
                first = false;
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid));
                cb.append(op.getName() != null ? op.getName() : uuid);
            }
            sender.sendMessage(cb.toString());
        }
        sender.sendMessage("§6§l================================");
    }

    @Subcommand("session")
    @CommandCompletion("@tracks")
    @Description("Inicia uma sessao de Time Trial em uma pista (atalho para /timetrial solo)")
    public void onSession(Player player, @Optional String trackName) {
        String[] args = (trackName == null || trackName.trim().isEmpty())
            ? new String[]{}
            : new String[]{trackName};
        new TimeTrialCommand(this.plugin).onSolo(player, args);
    }

    @Subcommand("times")
    @CommandCompletion("@tracks @nothing")
    @Description("Mostra os tempos de uma pista")
    public void onTimes(CommandSender sender, String[] args) {
        if (args.length == 0) {
            String langCode = this.lang(sender);
            sender.sendMessage(this.plugin.getDirectTranslation("track_usage", langCode));
        } else {
            int page = 1;

            String trackName;
            try {
                if (args.length > 1) {
                    page = Integer.parseInt(args[args.length - 1]);
                    trackName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 0, args.length - 1));
                } else {
                    trackName = args[0];
                }
            } catch (NumberFormatException var16) {
                trackName = String.join(" ", args);
            }

            String langCode = this.lang(sender);
            List<Map<String, Object>> times = this.dbManager.getAllTimesOnTrack(trackName, page);
            if (times.isEmpty()) {
                sender.sendMessage(this.plugin.getTranslation("track_no_times", langCode, new String[]{"{track}", trackName}));
            } else {
                sender.sendMessage(this.plugin.getTranslation("track_times_title", langCode, new String[]{"{track}", trackName, "{page}", String.valueOf(page)}));

                for(Map<String, Object> entry : times) {
                    int pos = (Integer)entry.get("pos");
                    String pname = (String)entry.get("player");
                    double time = (Double)entry.get("time");
                    int cp = (Integer)entry.get("checkpoints");
                    boolean finished = (Boolean)entry.get("finished");
                    String timeStr = formatTime(time);
                    String formatted = finished ? String.format("§e#%d §7» §a%s §8— §f%s §8(✓)", pos, pname, timeStr) : String.format("§e#%d §7» §a%s §8— §f%s §7(%dCP)", pos, pname, timeStr, cp);
                    sender.sendMessage(formatted);
                }

            }
        }
    }

    @Subcommand("mytimes")
    @CommandCompletion("@tracks @nothing")
    @Description("Mostra seus tempos em uma pista")
    public void onMyTimes(Player player, String[] args) {
        if (args.length == 0) {
            String langCode = this.lang(player);
            player.sendMessage(this.plugin.getDirectTranslation("track_usage", langCode));
        } else {
            int page = 1;

            String trackName;
            try {
                if (args.length > 1) {
                    page = Integer.parseInt(args[args.length - 1]);
                    trackName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 0, args.length - 1));
                } else {
                    trackName = args[0];
                }
            } catch (NumberFormatException var16) {
                trackName = String.join(" ", args);
            }

            String langCode = this.lang(player);
            List<Map<String, Object>> times = this.dbManager.getAllTimesOnTrackByPlayer(trackName, player.getName(), page);
            if (times.isEmpty()) {
                player.sendMessage(this.plugin.getTranslation("track_no_personal_times", langCode, new String[]{"{track}", trackName}));
            } else {
                player.sendMessage(this.plugin.getTranslation("track_mytimes_title", langCode, new String[]{"{track}", trackName, "{page}", String.valueOf(page)}));

                for(Map<String, Object> entry : times) {
                    int pos = (Integer)entry.get("pos");
                    double time = (Double)entry.get("time");
                    int cp = (Integer)entry.get("checkpoints");
                    boolean finished = (Boolean)entry.get("finished");
                    String date = (String)entry.get("date");
                    String timeStr = formatTime(time);
                    String formatted = finished ? String.format("§e#%d §8— §f%s §8(✓) §7[%s]", pos, timeStr, date) : String.format("§e#%d §8— §f%s §7(%dCP) §7[%s]", pos, timeStr, cp, date);
                    player.sendMessage(formatted);
                }

            }
        }
    }

    @Subcommand("deletebesttime")
    @CommandPermission("formularacing.admin")
    @CommandCompletion("@tracks @players")
    @Description("Deleta o melhor tempo de um jogador em uma pista")
    public void onDeleteBestTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUse: /track deletebesttime <pista> <jogador>");
        } else {
            String targetPlayer = args[args.length - 1];
            String trackName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 0, args.length - 1));
            String langCode = this.lang(sender);
            boolean success = this.dbManager.deletePlayerBestTimeOnTrack(trackName, targetPlayer);
            if (success) {
                sender.sendMessage(this.plugin.getTranslation("track_besttime_deleted", langCode, new String[]{"{player}", targetPlayer, "{track}", trackName}));
            } else {
                sender.sendMessage(this.plugin.getTranslation("track_besttime_not_found", langCode, new String[]{"{player}", targetPlayer, "{track}", trackName}));
            }

        }
    }

    @Subcommand("deletealltimes")
    @CommandPermission("formularacing.admin")
    @CommandCompletion("@tracks @players")
    @Description("Deleta todos os tempos de uma pista (opcionalmente de um jogador)")
    public void onDeleteAllTimes(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUse: /track deletealltimes <pista> [jogador]");
        } else {
            String targetPlayer = null;
            String fullPath = String.join(" ", args);
            String trackName;
            if (this.dbManager.isTrackExists(fullPath)) {
                trackName = fullPath;
            } else if (args.length > 1) {
                trackName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 0, args.length - 1));
                targetPlayer = args[args.length - 1];
            } else {
                trackName = args[0];
            }

            String langCode = this.lang(sender);
            boolean success = this.dbManager.deleteAllTimes(trackName, targetPlayer);
            if (success) {
                if (targetPlayer != null) {
                    sender.sendMessage(this.plugin.getTranslation("track_alltimes_deleted_player", langCode, new String[]{"{player}", targetPlayer, "{track}", trackName}));
                } else {
                    sender.sendMessage(this.plugin.getTranslation("track_alltimes_deleted", langCode, new String[]{"{track}", trackName}));
                }
            } else {
                sender.sendMessage(this.plugin.getTranslation("track_no_times", langCode, new String[]{"{track}", trackName}));
            }

        }
    }

    @Subcommand("deleteallplayertimes")
    @CommandPermission("formularacing.admin")
    @CommandCompletion("@players")
    @Description("Deleta todos os tempos de um jogador em todas as pistas")
    public void onDeleteAllPlayerTimes(CommandSender sender, String targetPlayer) {
        String langCode = this.lang(sender);
        boolean success = this.dbManager.deletePlayerAllTimes(targetPlayer);
        if (success) {
            sender.sendMessage(this.plugin.getTranslation("track_allplayertimes_deleted", langCode, new String[]{"{player}", targetPlayer}));
        } else {
            sender.sendMessage(this.plugin.getTranslation("track_player_no_times", langCode, new String[]{"{player}", targetPlayer}));
        }

    }
}
