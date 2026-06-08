package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.ScoreboardOwnershipCoordinator;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.provider.ScoreboardAdapter;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.style.TimingScoreboardStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ScoreboardDuelsTimeUtils {
    private static final int MAX_LINES = 15;
    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final TimeTrialDuelsAction ttda;
    private final ScoreboardAdapter adapter;
    private final ScoreboardOwnershipCoordinator ownershipCoordinator;
    private TimeTrialDuels timeTrialDuels;
    private final Map<UUID, DuelContext> duelContexts = new ConcurrentHashMap<>();

    public ScoreboardDuelsTimeUtils(
            FormulaRacing plugin,
            DatabaseManager mysql,
            TimeTrialDuelsAction ttda,
            TimeTrialDuels timeTrialDuels,
            ScoreboardAdapter adapter,
            ScoreboardOwnershipCoordinator ownershipCoordinator
    ) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.ttda = ttda;
        this.timeTrialDuels = timeTrialDuels;
        this.adapter = adapter;
        this.ownershipCoordinator = ownershipCoordinator;
        this.startAutoUpdateTask();
    }

    public void setTimeTrialDuels(TimeTrialDuels timeTrialDuels) {
        this.timeTrialDuels = timeTrialDuels;
    }

    private void startAutoUpdateTask() {
        SchedulerHelper.runTaskTimer(this.plugin, () -> {
            for (UUID uuid : ScoreboardDuelsTimeUtils.this.duelContexts.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                DuelContext ctx = ScoreboardDuelsTimeUtils.this.duelContexts.get(uuid);
                if (player == null || !player.isOnline() || ctx == null) {
                    ScoreboardDuelsTimeUtils.this.removeBoard(uuid);
                    continue;
                }
                if (!ScoreboardDuelsTimeUtils.this.ownershipCoordinator.isOwner(uuid, ScoreboardOwnershipCoordinator.Mode.DUEL)) {
                    if (!ScoreboardDuelsTimeUtils.this.ownershipCoordinator.acquire(uuid, ScoreboardOwnershipCoordinator.Mode.DUEL)) {
                        continue;
                    }
                    if (!ScoreboardDuelsTimeUtils.this.ownershipCoordinator.isOwner(uuid, ScoreboardOwnershipCoordinator.Mode.DUEL)) {
                        continue;
                    }
                    ScoreboardDuelsTimeUtils.this.adapter.create(player);
                    ScoreboardDuelsTimeUtils.this.adapter.updateTitle(player,
                            ScoreboardDuelsTimeUtils.this.boldTitle(
                                    ScoreboardDuelsTimeUtils.this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_title")
                            )
                    );
                }
                if (!ScoreboardDuelsTimeUtils.this.ownershipCoordinator.isOwner(uuid, ScoreboardOwnershipCoordinator.Mode.DUEL)) {
                    continue;
                }
                double elapsedSeconds = ScoreboardDuelsTimeUtils.this.ttda.getPlayerElapsedSeconds(player);
                String formattedTime = ScoreboardDuelsTimeUtils.this.formatTime(elapsedSeconds);
                ScoreboardDuelsTimeUtils.this.update(player, ctx.duelId, formattedTime, ctx.currentLap, ctx.totalLaps, ctx.trackName);
            }
        }, 0L, 2L);
    }

    public void applyDuelBoard(Player player, int duelId, int totalLaps, String trackName) {
        if (!this.ownershipCoordinator.acquire(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.DUEL)) {
            return;
        }
        this.adapter.create(player);
        this.adapter.updateTitle(player, this.boldTitle(this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_title")));
        this.duelContexts.put(player.getUniqueId(), new DuelContext(duelId, totalLaps, trackName));
    }

    public void updatePlayerLap(Player player, int lap) {
        DuelContext ctx = this.duelContexts.get(player.getUniqueId());
        if (ctx != null) {
            ctx.currentLap = lap;
        }
    }

    public void update(Player player, int duelId, String currentFormattedTime, int lap, int totalLaps, String trackName) {
        if (!this.ownershipCoordinator.isOwner(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.DUEL)) {
            return;
        }

        SchedulerHelper.runAsync(this.plugin, () -> {
            String posDisplay;
            String langCode = this.mysql.getPlayerLanguage(player.getUniqueId());
            Double bestLap = this.mysql.getPlayerBestLapTimeInDuel(player.getUniqueId(), duelId);

            String pbDisplay = "§7--:--.---";
            if (bestLap != null && bestLap > 0.0) {
                pbDisplay = "§f" + this.formatTime(bestLap);
            }

            if (bestLap == null || bestLap <= 0.0) {
                String waitingText = this.plugin.getDirectTranslation("duel_waiting", langCode);
                posDisplay = "§f§l" + waitingText;
            } else {
                int pos = this.timeTrialDuels.getPlayerPosition(duelId, player.getUniqueId());
                posDisplay = this.formatPosition(pos, langCode);
            }

            int timeRemaining = this.timeTrialDuels.getTimeRemaining(duelId);
            String timeRemainingDisplay = "";
            if (timeRemaining >= 0) {
                timeRemainingDisplay = this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_time_remaining")
                        + "§c" + this.formatTimeRemaining(timeRemaining);
            }

            String finalPB = pbDisplay;
            String finalPos = posDisplay;
            String finalTimeRemaining = timeRemainingDisplay;

            SchedulerHelper.runTask(this.plugin, () ->
                    this.updateBoardLines(player, timeRemaining, finalPos, lap, totalLaps, currentFormattedTime, finalPB, finalTimeRemaining, trackName)
            );
        });
    }

    public void removeBoard(Player player) {
        this.removeBoard(player.getUniqueId());
    }

    private void removeBoard(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            this.adapter.delete(player);
        }
        this.duelContexts.remove(uuid);
        this.ownershipCoordinator.release(uuid, ScoreboardOwnershipCoordinator.Mode.DUEL);
    }

    public void clearAll() {
        for (UUID uuid : this.duelContexts.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                this.adapter.delete(player);
            }
            this.ownershipCoordinator.release(uuid, ScoreboardOwnershipCoordinator.Mode.DUEL);
        }
        this.duelContexts.clear();
    }

    private String formatPosition(int pos, String langCode) {
        String positionText;
        if (pos <= 0) {
            pos = 1;
        }
        return switch (pos) {
            case 1 -> {
                positionText = this.plugin.getDirectTranslation("duel_position_1st", langCode);
                yield "§a§l" + positionText;
            }
            case 2 -> {
                positionText = this.plugin.getDirectTranslation("duel_position_2nd", langCode);
                yield "§e§l" + positionText;
            }
            case 3 -> {
                positionText = this.plugin.getDirectTranslation("duel_position_3rd", langCode);
                yield "§6§l" + positionText;
            }
            default -> {
                positionText = this.plugin.getTranslation("duel_position_nth", langCode, "{position}", String.valueOf(pos));
                yield "§f§l" + positionText;
            }
        };
    }

    private String formatTime(double seconds) {
        return this.formatTimeFromMillis((long) (seconds * 1000.0));
    }

    private String formatTimeFromMillis(long millis) {
        long minutes = millis / 60000L;
        long secs = millis % 60000L / 1000L;
        long ms = millis % 1000L;
        if (minutes > 0L) {
            return String.format("%d:%02d.%03d", minutes, secs, ms);
        }
        return String.format("%d.%03d", secs, ms);
    }

    private String formatTimeRemaining(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        if (minutes > 0) {
            return String.format("%dm %02ds", minutes, secs);
        }
        return String.format("%ds", secs);
    }

    private String boldTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "§l";
        }
        if (title.length() >= 2 && title.charAt(0) == '§') {
            return title.substring(0, 2) + "§l" + title.substring(2);
        }
        return "§l" + title;
    }

    private void updateBoardLines(Player player, int timeRemaining, String finalPosDisplay, int lap, int totalLaps, String currentFormattedTime, String finalPB, String finalTimeRemaining, String trackName) {
        if (!this.ownershipCoordinator.isOwner(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.DUEL)) {
            return;
        }

        List<String> lines = new ArrayList<>();
        String separator = this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_common_separator");
        String footer = this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_common_footer");
        String marker = TimingScoreboardStyle.normalizeAccentMarker(this.plugin.getConfig().getString("scoreboard.style.accent-marker", "┃"));

        lines.add("§f§l" + this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_track") + "§f" + trackName);
        lines.add(separator);
        lines.add("§f§l" + this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_position") + finalPosDisplay);
        lines.add("§f§l" + this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_lap") + "§b" + lap + "§7/§b" + totalLaps);
        lines.add("§7| §7§l" + marker + marker + "§r");
        lines.add("§f§l" + this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_time") + "§b§l" + currentFormattedTime);
        lines.add("§f§l" + this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_record") + finalPB);

        if (timeRemaining >= 0 && lines.size() < MAX_LINES - 3) {
            lines.add(finalTimeRemaining);
        }
        while (lines.size() > MAX_LINES - 3) {
            lines.remove(lines.size() - 1);
        }

        lines.add("");
        lines.add(separator);
        lines.add(footer);
        this.adapter.updateLines(player, lines);
    }

    public static class DuelContext {
        int duelId;
        int totalLaps;
        int currentLap = 0;
        String trackName;

        DuelContext(int duelId, int totalLaps, String trackName) {
            this.duelId = duelId;
            this.totalLaps = totalLaps;
            this.trackName = trackName;
        }
    }
}
