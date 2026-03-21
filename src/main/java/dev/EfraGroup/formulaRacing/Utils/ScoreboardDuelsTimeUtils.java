/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 *
 * Could not load the following classes:
 *  dev.EfraGroup.formulaRacing.Database.DatabaseManager
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 */
package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialDuelsAction;
import fr.mrmicky.fastboard.FastBoard;
import fr.mrmicky.fastboard.FastBoardBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ScoreboardDuelsTimeUtils {
    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final TimeTrialDuelsAction ttda;
    private TimeTrialDuels timeTrialDuels;
    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<UUID, FastBoard>();
    private final Map<UUID, DuelContext> duelContexts = new ConcurrentHashMap<UUID, DuelContext>();

    public ScoreboardDuelsTimeUtils(FormulaRacing plugin, DatabaseManager mysql, TimeTrialDuelsAction ttda, TimeTrialDuels timeTrialDuels) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.ttda = ttda;
        this.timeTrialDuels = timeTrialDuels;
        this.startAutoUpdateTask();
    }

    public void setTimeTrialDuels(TimeTrialDuels timeTrialDuels) {
        this.timeTrialDuels = timeTrialDuels;
    }

    private void startAutoUpdateTask() {
        new BukkitRunnable() {

            public void run() {
                for (UUID uuid : ScoreboardDuelsTimeUtils.this.boards.keySet()) {
                    Player player = Bukkit.getPlayer((UUID) uuid);
                    DuelContext ctx = ScoreboardDuelsTimeUtils.this.duelContexts.get(uuid);
                    if (player == null || !player.isOnline() || ctx == null) {
                        ScoreboardDuelsTimeUtils.this.removeBoard(uuid);
                        continue;
                    }
                    double elapsedSeconds = ScoreboardDuelsTimeUtils.this.ttda.getPlayerElapsedSeconds(player);
                    String formattedTime = ScoreboardDuelsTimeUtils.this.formatTime(elapsedSeconds);
                    ScoreboardDuelsTimeUtils.this.update(player, ctx.duelId, formattedTime, ctx.currentLap, ctx.totalLaps, ctx.trackName);
                }
            }
        }.runTaskTimer((Plugin) this.plugin, 0L, 2L);
    }

    public void applyDuelBoard(Player player, int duelId, int totalLaps, String trackName) {
        FastBoard board = new FastBoard(player);
        board.updateTitle(this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_title", new String[0]));
        this.boards.put(player.getUniqueId(), board);
        this.duelContexts.put(player.getUniqueId(), new DuelContext(duelId, totalLaps, trackName));
    }

    public void updatePlayerLap(Player player, int lap) {
        DuelContext ctx = this.duelContexts.get(player.getUniqueId());
        if (ctx != null) {
            ctx.currentLap = lap;
        }
    }

    public void update(Player player, int duelId, String currentFormattedTime, int lap, int totalLaps, String trackName) {
        FastBoard board = this.boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            // CORREÇÃO: Usar String em vez de Object
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

            // Variáveis finais para o lambda (efetivamente finais)
            final String finalPB = pbDisplay;
            final String finalPos = posDisplay;
            final String finalTimeRemaining = timeRemainingDisplay;

            // Volta para a Thread Principal (Sync) para atualizar a Scoreboard
            Bukkit.getScheduler().runTask(this.plugin, () ->
                    this.updateBoardLines(player, board, timeRemaining, finalPos, lap, totalLaps, currentFormattedTime, finalPB, finalTimeRemaining, trackName)
            );
        });
    }

    public void removeBoard(Player player) {
        this.removeBoard(player.getUniqueId());
    }

    private void removeBoard(UUID uuid) {
        FastBoard board = this.boards.remove(uuid);
        if (board != null) {
            board.delete();
        }
        this.duelContexts.remove(uuid);
    }

    public void clearAll() {
        this.boards.values().forEach(FastBoardBase::delete);
        this.boards.clear();
        this.duelContexts.clear();
    }

    private String formatPosition(int pos, String langCode) {
        String positionText;
        if (pos <= 0) {
            pos = 1;
        }
        return (switch (pos) {
            case 1 -> {
                positionText = this.plugin.getDirectTranslation("duel_position_1st", langCode);
                yield "\u00a7a\u00a7l";
            }
            case 2 -> {
                positionText = this.plugin.getDirectTranslation("duel_position_2nd", langCode);
                yield "\u00a7e\u00a7l";
            }
            case 3 -> {
                positionText = this.plugin.getDirectTranslation("duel_position_3rd", langCode);
                yield "\u00a76\u00a7l";
            }
            default -> {
                positionText = this.plugin.getTranslation("duel_position_nth", langCode, "{position}", String.valueOf(pos));
                yield "\u00a7f\u00a7l";
            }
        }) + positionText;
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

    private void updateBoardLines(Player player, FastBoard board, int timeRemaining, String finalPosDisplay, int lap, int totalLaps, String currentFormattedTime, String finalPB, String finalTimeRemaining, String trackName) {
        FastBoard currentBoard = this.boards.get(player.getUniqueId());

        // Verifica se o jogador ainda possui uma scoreboard ativa e se é a mesma instância
        if (currentBoard == null || currentBoard != board) {
            return;
        }

        // CORREÇÃO: Usando List<String> para compatibilidade com board.updateLines
        List<String> lines = new ArrayList<>();

        // Linha de separação padrão
        String separator = this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_common_separator");
        String footer = this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_common_footer");

        lines.add(this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_track") + "§f" + trackName);
        lines.add(separator);
        lines.add(this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_position") + finalPosDisplay);
        lines.add(this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_lap") + "§b" + lap + "§7/§b" + totalLaps);
        lines.add("§8| §7§l┃┃§r");
        lines.add(this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_time") + "§e" + currentFormattedTime);
        lines.add(this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_duel_record") + finalPB);

        // Se houver tempo restante (ex: contagem regressiva), adicionamos a linha
        if (timeRemaining >= 0) {
            lines.add(finalTimeRemaining);
        }

        lines.add("");
        lines.add(separator);
        lines.add(footer);

        // Atualiza as linhas da Scoreboard
        board.updateLines(lines);
    }

    private static class DuelContext {
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
