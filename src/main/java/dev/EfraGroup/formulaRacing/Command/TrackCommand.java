package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    @Default
    @CatchUnknown
    public void onDefault(Player player) {
        String langCode = this.dbManager.getPlayerLanguage(player.getUniqueId());
        player.sendMessage(this.plugin.getDirectTranslation("track_usage", langCode));
    }

    @Subcommand("times")
    @CommandCompletion("@tracks @nothing")
    @Description("Mostra os tempos de uma pista")
    public void onTimes(Player player, String[] args) {
        if (args.length == 0) {
            String langCode = this.dbManager.getPlayerLanguage(player.getUniqueId());
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

            String langCode = this.dbManager.getPlayerLanguage(player.getUniqueId());
            List<Map<String, Object>> times = this.dbManager.getAllTimesOnTrack(trackName, page);
            if (times.isEmpty()) {
                player.sendMessage(this.plugin.getTranslation("track_no_times", langCode, new String[]{"{track}", trackName}));
            } else {
                player.sendMessage(this.plugin.getTranslation("track_times_title", langCode, new String[]{"{track}", trackName, "{page}", String.valueOf(page)}));

                for(Map<String, Object> entry : times) {
                    int pos = (Integer)entry.get("pos");
                    String pname = (String)entry.get("player");
                    double time = (Double)entry.get("time");
                    int cp = (Integer)entry.get("checkpoints");
                    boolean finished = (Boolean)entry.get("finished");
                    String formatted = finished ? String.format("§e#%d §7» §a%s §8— §f%.3fs §8(✓)", pos, pname, time) : String.format("§e#%d §7» §a%s §8— §f%.3fs §7(%dCP)", pos, pname, time, cp);
                    player.sendMessage(formatted);
                }

            }
        }
    }

    @Subcommand("mytimes")
    @CommandCompletion("@tracks @nothing")
    @Description("Mostra seus tempos em uma pista")
    public void onMyTimes(Player player, String[] args) {
        if (args.length == 0) {
            String langCode = this.dbManager.getPlayerLanguage(player.getUniqueId());
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

            String langCode = this.dbManager.getPlayerLanguage(player.getUniqueId());
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
                    String formatted = finished ? String.format("§e#%d §8— §f%.3fs §8(✓) §7[%s]", pos, time, date) : String.format("§e#%d §8— §f%.3fs §7(%dCP) §7[%s]", pos, time, cp, date);
                    player.sendMessage(formatted);
                }

            }
        }
    }

    @Subcommand("deletebesttime")
    @CommandPermission("formularacing.admin")
    @CommandCompletion("@tracks @players")
    @Description("Deleta o melhor tempo de um jogador em uma pista")
    public void onDeleteBestTime(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUse: /track deletebesttime <pista> <jogador>");
        } else {
            String targetPlayer = args[args.length - 1];
            String trackName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 0, args.length - 1));
            String langCode = this.dbManager.getPlayerLanguage(player.getUniqueId());
            boolean success = this.dbManager.deletePlayerBestTimeOnTrack(trackName, targetPlayer);
            if (success) {
                player.sendMessage(this.plugin.getTranslation("track_besttime_deleted", langCode, new String[]{"{player}", targetPlayer, "{track}", trackName}));
            } else {
                player.sendMessage(this.plugin.getTranslation("track_besttime_not_found", langCode, new String[]{"{player}", targetPlayer, "{track}", trackName}));
            }

        }
    }

    @Subcommand("deletealltimes")
    @CommandPermission("formularacing.admin")
    @CommandCompletion("@tracks @players")
    @Description("Deleta todos os tempos de uma pista (opcionalmente de um jogador)")
    public void onDeleteAllTimes(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUse: /track deletealltimes <pista> [jogador]");
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

            String langCode = this.dbManager.getPlayerLanguage(player.getUniqueId());
            boolean success = this.dbManager.deleteAllTimes(trackName, targetPlayer);
            if (success) {
                if (targetPlayer != null) {
                    player.sendMessage(this.plugin.getTranslation("track_alltimes_deleted_player", langCode, new String[]{"{player}", targetPlayer, "{track}", trackName}));
                } else {
                    player.sendMessage(this.plugin.getTranslation("track_alltimes_deleted", langCode, new String[]{"{track}", trackName}));
                }
            } else {
                player.sendMessage(this.plugin.getTranslation("track_no_times", langCode, new String[]{"{track}", trackName}));
            }

        }
    }

    @Subcommand("deleteallplayertimes")
    @CommandPermission("formularacing.admin")
    @CommandCompletion("@players")
    @Description("Deleta todos os tempos de um jogador em todas as pistas")
    public void onDeleteAllPlayerTimes(Player player, String targetPlayer) {
        String langCode = this.dbManager.getPlayerLanguage(player.getUniqueId());
        boolean success = this.dbManager.deletePlayerAllTimes(targetPlayer);
        if (success) {
            player.sendMessage(this.plugin.getTranslation("track_allplayertimes_deleted", langCode, new String[]{"{player}", targetPlayer}));
        } else {
            player.sendMessage(this.plugin.getTranslation("track_player_no_times", langCode, new String[]{"{player}", targetPlayer}));
        }

    }
}
