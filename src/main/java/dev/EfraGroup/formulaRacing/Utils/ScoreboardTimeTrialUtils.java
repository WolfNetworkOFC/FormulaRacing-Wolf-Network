package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.ScoreboardOwnershipCoordinator;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.provider.ScoreboardAdapter;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.style.TimingScoreboardStyle;
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
    private static final int MAX_LINES = 15;
    private final Map<UUID, String> playerTracks = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerTrackOwners = new ConcurrentHashMap<>();
    private final DatabaseManager mysql;
    private final ScoreboardAdapter adapter;
    private final ScoreboardOwnershipCoordinator ownershipCoordinator;
    private boolean running = false;
    private final Map<String, CachedLeaderboard> leaderboardCache = new ConcurrentHashMap<>();
    private final Map<String, String> trackOwnerCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> enabledCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastEnabledCheck = new ConcurrentHashMap<>();
    private static final long SETTINGS_TTL = 5000L;

    public ScoreboardTimeTrialUtils(FormulaRacing plugin, DatabaseManager mysql, ScoreboardAdapter adapter, ScoreboardOwnershipCoordinator ownershipCoordinator) {
        this.mysql = mysql;
        this.adapter = adapter;
        this.ownershipCoordinator = ownershipCoordinator;
    }

    public void startAutoUpdate() {
        if (this.running) {
            return;
        }
        this.running = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String trackName = ScoreboardTimeTrialUtils.this.playerTracks.get(player.getUniqueId());
                    if (trackName == null) {
                        continue;
                    }
                    ScoreboardTimeTrialUtils.this.show(player, trackName);
                }
            }
        }.runTaskTimer( FormulaRacing.getInstance(), 0L, 20L);
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
        this.ownershipCoordinator.release(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.TIME_TRIAL);
        this.adapter.delete(player);
    }

    public boolean show(Player player, String trackName) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (FormulaRacing.getInstance().getTimeTrialDuels() != null
                && FormulaRacing.getInstance().getTimeTrialDuels().isPlayerInActiveDuelCached(player.getUniqueId())) {
            return false;
        }

        long now = System.currentTimeMillis();
        boolean enabled;
        if (this.enabledCache.containsKey(player.getUniqueId()) && now - this.lastEnabledCheck.getOrDefault(player.getUniqueId(), 0L) < SETTINGS_TTL) {
            enabled = this.enabledCache.get(player.getUniqueId());
        } else {
            enabled = this.mysql.getTimeTrialScoreboard(player.getUniqueId());
            this.enabledCache.put(player.getUniqueId(), enabled);
            this.lastEnabledCheck.put(player.getUniqueId(), now);
        }

        if (!enabled) {
            this.ownershipCoordinator.release(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.TIME_TRIAL);
            this.adapter.delete(player);
            return false;
        }

        if (!this.ownershipCoordinator.acquire(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.TIME_TRIAL)) {
            return false;
        }
        if (!this.ownershipCoordinator.isOwner(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.TIME_TRIAL)) {
            return false;
        }

        this.adapter.create(player);
        try {
            this.updateBoard(player, trackName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateBoard(Player player, String trackName) {
        List<DatabaseManager.TrackRecord> allRecords;
        CachedLeaderboard cached = this.leaderboardCache.get(trackName);

        if (cached == null || cached.isExpired(10000L)) {
            allRecords = this.mysql.getTopTimes(trackName);
            if (allRecords == null) {
                allRecords = new ArrayList<>();
            }
            allRecords.sort(Comparator
                    .comparingInt((DatabaseManager.TrackRecord tr) -> tr.isFinished() ? 0 : 1)
                    .thenComparing(Comparator.comparingInt(DatabaseManager.TrackRecord::getCheckpointsReached).reversed())
                    .thenComparingDouble(DatabaseManager.TrackRecord::getTime));
            this.leaderboardCache.put(trackName, new CachedLeaderboard(allRecords));
        } else {
            allRecords = cached.records;
        }

        int myPos = -1;
        for (int i = 0; i < allRecords.size(); i++) {
            if (allRecords.get(i).getPlayerName().equals(player.getName())) {
                myPos = i;
                break;
            }
        }

        TranslationUtil tu = FormulaRacing.getInstance().getTranslationUtil();
        this.adapter.updateTitle(player, this.boldTitle(tu.getTranslated(player, "scoreboard_tt_title")));
        String separator = tu.getTranslated(player, "scoreboard_common_separator");
        String footer = "§ewolfnetwork.com.br";

        List<String> lines = new ArrayList<>();
        lines.add(separator);
        lines.add("§f§l" + tu.getTranslated(player, "scoreboard_tt_track", "{track}", trackName));

        String creator = this.playerTrackOwners.getOrDefault(player.getUniqueId(), this.trackOwnerCache.get(trackName));
        if (creator == null) {
            creator = this.mysql.getTrackOwner(trackName);
            if (creator != null) {
                this.trackOwnerCache.put(trackName, creator);
            }
        }

        if (creator != null) {
            lines.add("§f§l" + tu.getTranslated(player, "scoreboard_tt_by", "{creator}", creator));
        }

        lines.add(separator);
        lines.add("");
        lines.add("§e§l" + tu.getTranslated(player, "scoreboard_tt_leaderboard"));

        List<DatabaseManager.TrackRecord> neighbors = new ArrayList<>();
        DatabaseManager.TrackRecord firstPlace = null;
        boolean includeSeparator = false;
        int footerLines = 3;
        int availableForEntries = Math.max(3, MAX_LINES - lines.size() - footerLines);

        if (myPos == -1 || myPos < 5) {
            int limit = Math.min(allRecords.size(), availableForEntries);
            for (int i = 0; i < limit; i++) {
                neighbors.add(allRecords.get(i));
            }
        } else {
            boolean canShowLeaderAndStillCenter = availableForEntries >= 7;
            int neighborsLimit = availableForEntries;
            int minIndex = 0;
            if (canShowLeaderAndStillCenter) {
                firstPlace = allRecords.get(0);
                includeSeparator = true;
                neighborsLimit -= 2;
                minIndex = 1;
            }
            int start = Math.max(minIndex, myPos - neighborsLimit / 2);
            int end = Math.min(allRecords.size() - 1, start + neighborsLimit - 1);
            start = Math.max(minIndex, end - neighborsLimit + 1);
            for (int i = start; i <= end; i++) {
                if (canShowLeaderAndStillCenter && i == 0) {
                    continue;
                }
                neighbors.add(allRecords.get(i));
            }
        }

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
        this.adapter.updateLines(player, lines);
    }

    private String formatRecordLine(DatabaseManager.TrackRecord tr, int pos, String observerName, Player viewer) {
        boolean isMe = tr.getPlayerName().equals(observerName);
        String color;
        String bold = "";
        if (isMe) {
            color = "§e";
            bold = "§l";
        } else {
            switch (pos) {
                case 1 -> color = "§6";
                case 2 -> color = "§7";
                case 3 -> color = "§c";
                default -> color = "§f";
            }
        }

        String timeDisplay = tr.isFinished()
                ? "§b" + this.formatTime(tr.getTime())
                : "§6" + tr.getCheckpointsReached() + "CP §7(§f" + this.formatTime(tr.getTime()) + "§7)";
        String configured = FormulaRacing.getInstance().getConfig().getString("scoreboard.style.accent-marker", "┃");
        String accent = TimingScoreboardStyle.normalizeAccentMarker(configured);
        String marker = bold + accent + accent + "§r";
        String nameDisplay = isMe
                ? FormulaRacing.getInstance().getTranslationUtil().getTranslated(viewer, "scoreboard_tt_you")
                : tr.getPlayerName();
        nameDisplay = TimingScoreboardStyle.padRight(nameDisplay, 14);
        String rank = bold + color + pos + ".§r";
        String nameColor = isMe ? "§e§l" : "§f";
        return rank + " §7| " + timeDisplay + " " + marker + " " + nameColor + nameDisplay;
    }

    public String formatTime(double timeInSeconds) {
        long minutes = (long) (timeInSeconds / 60.0);
        long seconds = (long) (timeInSeconds % 60.0);
        long millis = (long) ((timeInSeconds - Math.floor(timeInSeconds)) * 1000.0);
        if (minutes > 0L) {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
        }
        return String.format("%d.%03d", seconds, millis);
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

    public void clearAll() {
        for (UUID uuid : this.playerTracks.keySet()) {
            this.ownershipCoordinator.release(uuid, ScoreboardOwnershipCoordinator.Mode.TIME_TRIAL);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                this.adapter.delete(player);
            }
        }
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
