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
import dev.EfraGroup.formulaRacing.FormulaRacing;
import fr.mrmicky.fastboard.FastBoard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ScoreboardTimeTrialUtils {
    private final Map<UUID, String> playerTracks = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, String> playerTrackOwners = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<UUID, FastBoard>();
    private final DatabaseManager mysql;
    private boolean running = false;
    private final Map<String, CachedLeaderboard> leaderboardCache = new ConcurrentHashMap<String, CachedLeaderboard>();
    private final Map<String, String> trackOwnerCache = new ConcurrentHashMap<String, String>();
    private final Map<UUID, Boolean> enabledCache = new ConcurrentHashMap<UUID, Boolean>();
    private final Map<UUID, Long> lastEnabledCheck = new ConcurrentHashMap<UUID, Long>();
    private final long CACHE_TTL_MS = 10000L;
    private static final long SETTINGS_TTL = 5000L;

    public ScoreboardTimeTrialUtils(DatabaseManager mysql) {
        this.mysql = mysql;
    }

    public void startAutoUpdate() {
        if (this.running) {
            return;
        }
        this.running = true;
        new BukkitRunnable(){

            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String trackName = ScoreboardTimeTrialUtils.this.playerTracks.get(player.getUniqueId());
                    if (trackName == null) continue;
                    ScoreboardTimeTrialUtils.this.show(player, trackName);
                }
            }
        }.runTaskTimer((Plugin)FormulaRacing.getInstance(), 0L, 20L);
    }

    public void setPlayerTrack(Player player, String trackName, String ownerName) {
        this.playerTracks.put(player.getUniqueId(), trackName);
        if (ownerName != null) {
            this.playerTrackOwners.put(player.getUniqueId(), ownerName);
        }
        this.show(player, trackName);
    }

    public void clearPlayerTrack(Player player) {
        this.playerTracks.remove(player.getUniqueId());
        this.playerTrackOwners.remove(player.getUniqueId());
        FastBoard board = this.boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    public boolean show(Player player, String trackName) {
        boolean enabled;
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (FormulaRacing.getInstance().getTimeTrialDuels() != null && FormulaRacing.getInstance().getTimeTrialDuels().isPlayerInActiveDuelCached(player.getUniqueId())) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (this.enabledCache.containsKey(player.getUniqueId()) && now - this.lastEnabledCheck.getOrDefault(player.getUniqueId(), 0L) < 5000L) {
            enabled = this.enabledCache.get(player.getUniqueId());
        } else {
            enabled = this.mysql.getTimeTrialScoreboard(player.getUniqueId());
            this.enabledCache.put(player.getUniqueId(), enabled);
            this.lastEnabledCheck.put(player.getUniqueId(), now);
        }
        FastBoard board = this.boards.get(player.getUniqueId());
        if (!enabled) {
            if (board != null) {
                board.delete();
                this.boards.remove(player.getUniqueId());
            }
            return false;
        }
        if (board == null) {
            try {
                board = new FastBoard(player);
                this.boards.put(player.getUniqueId(), board);
            } catch (Exception e) {
                return false;
            }
        }
        try {
            this.updateBoard(player, board, trackName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateBoard(Player player, FastBoard board, String trackName) {
        List<DatabaseManager.TrackRecord> allRecords;
        CachedLeaderboard cached = leaderboardCache.get(trackName);

        // 1. Gerenciamento de Cache
        if (cached == null || cached.isExpired(10000L)) {
            allRecords = mysql.getTopTimes(trackName);
            if (allRecords == null) allRecords = new ArrayList<>();

            // Ordenação: Finalizados primeiro > Mais Checkpoints > Menor Tempo
            allRecords.sort(Comparator.comparingInt((DatabaseManager.TrackRecord tr) -> tr.isFinished() ? 0 : 1)
                    .thenComparing(Comparator.comparingInt(DatabaseManager.TrackRecord::getCheckpointsReached).reversed())
                    .thenComparingDouble(DatabaseManager.TrackRecord::getTime));

            leaderboardCache.put(trackName, new CachedLeaderboard(allRecords));
        } else {
            allRecords = cached.records;
        }

        // 2. Localizar posição do jogador
        int myPos = -1;
        for (int i = 0; i < allRecords.size(); i++) {
            if (allRecords.get(i).getPlayerName().equals(player.getName())) {
                myPos = i;
                break;
            }
        }

        // 3. Cabeçalho do Scoreboard
        TranslationUtil tu = FormulaRacing.getInstance().getTranslationUtil();
        board.updateTitle(tu.getTranslated(player, "scoreboard_tt_title"));
        String separator = tu.getTranslated(player, "scoreboard_common_separator");
        String footer = tu.getTranslated(player, "scoreboard_common_footer");

        List<String> lines = new ArrayList<>();
        lines.add(separator);
        lines.add(tu.getTranslated(player, "scoreboard_tt_track", "{track}", trackName));

        // Lógica do Criador da Pista
        String creator = playerTrackOwners.getOrDefault(player.getUniqueId(), trackOwnerCache.get(trackName));
        if (creator == null) {
            creator = mysql.getTrackOwner(trackName);
            if (creator != null) trackOwnerCache.put(trackName, creator);
        }

        if (creator != null) {
            lines.add(tu.getTranslated(player, "scoreboard_tt_by", "{creator}", creator));
        }

        lines.add(separator);
        lines.add("");
        lines.add(tu.getTranslated(player, "scoreboard_tt_leaderboard"));

        // 4. Lógica do Leaderboard Dinâmico (Top 5 ou Top 1 + Vizinhos)
        List<DatabaseManager.TrackRecord> neighbors = new ArrayList<>();
        DatabaseManager.TrackRecord firstPlace = null;
        boolean includeSeparator = false;

        if (myPos == -1 || myPos < 5) {
            // Caso o jogador não tenha tempo ou já esteja no Top 5
            int limit = Math.min(allRecords.size(), 5);
            for (int i = 0; i < limit; i++) {
                neighbors.add(allRecords.get(i));
            }
        } else {
            // Caso o jogador esteja longe: mostra o 1º lugar + separador + vizinhos ao redor dele
            firstPlace = allRecords.get(0);
            includeSeparator = true;

            int start = Math.max(1, myPos - 2);
            int end = Math.min(allRecords.size() - 1, myPos + 2);
            for (int i = start; i <= end; i++) {
                neighbors.add(allRecords.get(i));
            }
        }

        // 5. Renderização das linhas
        if (firstPlace != null) {
            lines.add(formatRecordLine(firstPlace, 1, player.getName(), player));
        }

        if (includeSeparator) {
            lines.add(separator);
        }

        for (DatabaseManager.TrackRecord tr : neighbors) {
            int actualPos = allRecords.indexOf(tr) + 1;
            lines.add(formatRecordLine(tr, actualPos, player.getName(), player));
        }

        lines.add("");
        lines.add(separator);
        lines.add(footer);

        board.updateLines(lines);
    }

    private String formatRecordLine(DatabaseManager.TrackRecord tr, int pos, String observerName, Player viewer) {
        String string;
        boolean isMe = tr.getPlayerName().equals(observerName);
        if (isMe) {
            string = "\u00a7f\u00a7l";
        } else {
            switch (pos) {
                case 1: {
                    string = "\u00a76";
                    break;
                }
                case 2: {
                    string = "\u00a77";
                    break;
                }
                case 3: {
                    string = "\u00a7c";
                    break;
                }
                default: {
                    string = "\u00a77";
                }
            }
        }
        String color = string;
        String timeDisplay = tr.isFinished() ? this.formatTime(tr.getTime()) : "\u00a77" + tr.getCheckpointsReached() + "CP(\u00a7f" + this.formatTime(tr.getTime()) + "\u00a77)";
        String marker = color + "§l┃┃§r";
        String nameDisplay = isMe ? FormulaRacing.getInstance().getTranslationUtil().getTranslated(viewer, "scoreboard_tt_you", new String[0]) : tr.getPlayerName();
        if (nameDisplay.length() > 10) {
            nameDisplay = nameDisplay.substring(0, 10);
        }
        String rank = color + pos + ".";
        return rank + " §8| §7" + timeDisplay + " " + marker + " §f" + nameDisplay;
    }

    public String formatTime(double timeInSeconds) {
        long minutes = (long)(timeInSeconds / 60.0);
        long seconds = (long)(timeInSeconds % 60.0);
        long millis = (long)((timeInSeconds - Math.floor(timeInSeconds)) * 1000.0);
        if (minutes > 0L) {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
        }
        return String.format("%d.%03d", seconds, millis);
    }

    public void clearAll() {
        for (FastBoard board : this.boards.values()) {
            board.delete();
        }
        this.boards.clear();
        this.playerTracks.clear();
        this.playerTrackOwners.clear();
    }

    private static class CachedLeaderboard {
        final List<DatabaseManager.TrackRecord> records;
        final long timestamp;

        CachedLeaderboard(List<DatabaseManager.TrackRecord> records) {
            this.records = records;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - this.timestamp > ttl;
        }
    }
}
