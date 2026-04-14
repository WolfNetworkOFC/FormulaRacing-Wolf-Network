/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 *
 * Could not load the following classes:
 *  dev.EfraGroup.formulaRacing.Database.DatabaseManager
 *  net.md_5.bungee.api.ChatMessageType
 *  net.md_5.bungee.api.chat.BaseComponent
 *  net.md_5.bungee.api.chat.TextComponent
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 */
package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TimerUtils {
    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private boolean globalLoopRunning = false;
    private final Map<UUID, List<CheckpointData>> tempCheckpoints = new ConcurrentHashMap<UUID, List<CheckpointData>>();
    private final Map<UUID, Map<String, PlayerTimerData>> activeTimers = new ConcurrentHashMap<UUID, Map<String, PlayerTimerData>>();
    private final Set<UUID> warnedPlayersNoCheckpoints = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastWarningTime = new ConcurrentHashMap<String, Long>();
    private final Map<String, Long> lastDebugLogTime = new ConcurrentHashMap<String, Long>();
    private static final long DEBUG_LOG_COOLDOWN_MS = 5000L;

    public TimerUtils(FormulaRacing plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void startTimer(Player player, String trackName) {
        this.startTimer(player, trackName, System.currentTimeMillis());
    }

    public void startTimer(Player player, String trackName, long startTime) {
        this.stopTimer(player, trackName);
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        long nowNano = System.nanoTime();
        long now = System.currentTimeMillis();
        long diff = now - startTime;
        long adjustedNano = nowNano - diff * 1000000L;
        int attemptId = (int)(startTime & 0xFFFFFFFL);
        int initialCPs = this.databaseManager.getCheckpointCount(trackName);
        PlayerTimerData data = new PlayerTimerData(startTime, adjustedNano, initialCPs, attemptId);
        this.activeTimers.computeIfAbsent(uuid, k -> new ConcurrentHashMap()).put(trackName, data);
        if (!this.globalLoopRunning) {
            this.startGlobalLoop();
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            try {
                Object[] pb = this.databaseManager.getPlayerBestTime(playerName, trackName);
                Map cp = this.databaseManager.getCheckpointTimes(uuid, trackName);
                List cpIds = this.databaseManager.getCheckpointIds(trackName);
                int realTotalCPs = cpIds != null ? cpIds.size() : 0;
                RaceSessionCache cache = new RaceSessionCache(pb, cp);
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    PlayerTimerData existingData = this.getTimerData(player, trackName);
                    if (existingData != null) {
                        existingData.setSessionCache(cache);
                        existingData.setTotalCheckpoints(realTotalCPs);
                    }
                });
            } catch (Exception e) {
                this.plugin.getDebugManager().logTimeTrialSystem("\u274c Erro ao carregar dados do timer: " + e.getMessage());
            }
        });
    }

    public void startGlobalLoop() {
        if (this.globalLoopRunning) {
            return;
        }
        this.globalLoopRunning = true;
        new BukkitRunnable(){

            public void run() {
                if (TimerUtils.this.activeTimers.isEmpty()) {
                    TimerUtils.this.globalLoopRunning = false;
                    this.cancel();
                    return;
                }
                Bukkit.getScheduler().runTaskAsynchronously(TimerUtils.this.plugin, () -> {
                    for (Map.Entry<UUID, Map<String, PlayerTimerData>> playerEntry : TimerUtils.this.activeTimers.entrySet()) {
                        UUID uuid = playerEntry.getKey();
                        Player player = Bukkit.getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;
                        for (Map.Entry<String, PlayerTimerData> trackEntry : playerEntry.getValue().entrySet()) {
                            boolean worstTime;
                            CheckpointData potentialCp;
                            String trackName = trackEntry.getKey();
                            PlayerTimerData data = trackEntry.getValue();
                            List<CheckpointData> tempCps = TimerUtils.this.tempCheckpoints.get(uuid);
                            CheckpointData lastCp = null;
                            if (tempCps != null && !tempCps.isEmpty() && (potentialCp = tempCps.get(tempCps.size() - 1)).getTrack().equalsIgnoreCase(trackName)) {
                                lastCp = potentialCp;
                            }
                            double elapsed = (double)(System.nanoTime() - data.getStartNanoTime()) / 1.0E9;
                            RaceSessionCache cache = data.getSessionCache();
                            Double pb = cache != null ? cache.getPbTime() : null;
                            Map<Integer, Double> cpTimes = cache != null ? cache.getCpTimes() : null;
                            StringBuilder sb = new StringBuilder(64);
                            boolean bl = worstTime = pb != null && elapsed > pb;
                            sb.append(pb == null ? "\u00a7a" : (worstTime ? "\u00a7c" : "\u00a7e"));
                            int minutes = (int)(elapsed / 60.0);
                            double sec = elapsed % 60.0;
                            if (minutes > 0) {
                                sb.append(minutes).append(":");
                                if (sec < 10.0) {
                                    sb.append("0");
                                }
                            }
                            long s1000 = (long)(sec * 1000.0);
                            sb.append(s1000 / 1000L).append(".");
                            long milli = s1000 % 1000L;
                            if (milli < 100L) {
                                sb.append("0");
                            }
                            if (milli < 10L) {
                                sb.append("0");
                            }
                            sb.append(milli);
                            sb.append(" \u00a77(").append(data.getCheckpointsReached()).append("/").append(data.getTotalCheckpoints()).append(")");
                            if (lastCp != null && cpTimes != null && cpTimes.containsKey(lastCp.getId())) {
                                double delta = lastCp.getTime() - cpTimes.get(lastCp.getId());
                                sb.append(delta < 0.0 ? " \u00a7a-" : " \u00a7c+");
                                double absDelta = Math.abs(delta);
                                long d1000 = (long)(absDelta * 1000.0);
                                sb.append(d1000 / 1000L).append(".");
                                long m = d1000 % 1000L;
                                if (m < 100L) {
                                    sb.append("0");
                                }
                                if (m < 10L) {
                                    sb.append("0");
                                }
                                sb.append(m);
                            }
                            String finalHud = sb.toString();
                            Bukkit.getScheduler().runTask((Plugin)TimerUtils.this.plugin, () -> {
                                if (player.isOnline()) {
                                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(finalHud));
                                }
                            });
                        }
                    }
                });
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    public void stopTimer(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, PlayerTimerData> map = this.activeTimers.get(uuid);
        this.plugin.getDebugManager().logTimeTrialSystem("[TimerUtils] stopTimer(Player) chamado para " + player.getName() + ", timers ativos: " + String.valueOf(map != null ? map.keySet() : "nenhum"));
        if (map != null) {
            for (String track : new HashSet<String>(map.keySet())) {
                this.stopTimer(player, track);
            }
        }
        this.activeTimers.remove(uuid);
        this.tempCheckpoints.remove(uuid);
        this.warnedPlayersNoCheckpoints.remove(uuid);
        this.lastWarningTime.entrySet().removeIf(entry -> ((String)entry.getKey()).startsWith(uuid.toString() + ":"));
        this.lastDebugLogTime.entrySet().removeIf(entry -> ((String)entry.getKey()).startsWith(uuid.toString() + ":"));
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
            this.plugin.getDebugManager().logTimeTrialSystem("[TimerUtils] Action bar limpa para " + player.getName());
        } catch (Exception ignored) {
            this.plugin.getDebugManager().logTimeTrialSystem("[TimerUtils] Erro ao limpar action bar para " + player.getName());
        }
    }

    public boolean isTimerRunning(Player player, String trackName) {
        Map<String, PlayerTimerData> map = this.activeTimers.get(player.getUniqueId());
        return map != null && map.containsKey(trackName);
    }

    public boolean isTimerRunning(Player player) {
        return this.activeTimers.containsKey(player.getUniqueId());
    }

    public String getActiveTrack(Player player) {
        Map<String, PlayerTimerData> map = this.activeTimers.get(player.getUniqueId());
        if (map != null && !map.isEmpty()) {
            return map.keySet().iterator().next();
        }
        return null;
    }

    public void reloadCacheAsync(Player player, String trackName) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            int cpCount;
            Map cp;
            Object[] pb = this.databaseManager.getPlayerBestTime(playerName, trackName);
            RaceSessionCache newCache = new RaceSessionCache(pb, cp = this.databaseManager.getCheckpointTimes(uuid, trackName));
            int n = cpCount = newCache.getCpTimes() != null ? newCache.getCpTimes().size() : 0;
            if (cpCount > 0) {
                this.warnedPlayersNoCheckpoints.remove(uuid);
                this.lastWarningTime.entrySet().removeIf(entry -> ((String)entry.getKey()).startsWith(uuid.toString() + ":"));
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                PlayerTimerData data = this.getTimerData(player, trackName);
                if (data != null) {
                    data.setSessionCache(newCache);
                }
            });
        });
    }

    public double getPlayerElapsedTime(Player player, String trackName) {
        PlayerTimerData data = this.getTimerData(player, trackName);
        return data != null ? (double)(System.nanoTime() - data.getStartNanoTime()) / 1.0E9 : 0.0;
    }

    public double getPlayerElapsedTime(Player player) {
        String track = this.getActiveTrack(player);
        if (track == null) {
            return 0.0;
        }
        return this.getPlayerElapsedTime(player, track);
    }

    public double getPlayerElapsedTimeUntilLastCheckpoint(Player player, String trackName) {
        PlayerTimerData data = this.getTimerData(player, trackName);
        if (data == null || data.getCheckpointTimes().isEmpty()) {
            return 0.0;
        }
        return data.getCheckpointTimes().get(data.getCheckpointTimes().size() - 1);
    }

    public void addCheckpoint(Player player, int checkpointId) {
        String track = this.getActiveTrack(player);
        if (track == null) {
            return;
        }
        this.addCheckpoint(player, track, checkpointId);
    }

    public void addCheckpoint(Player player, String trackName, int checkpointId) {
        PlayerTimerData data = this.getTimerData(player, trackName);
        if (data == null) {
            return;
        }
        double elapsed = (double)(System.currentTimeMillis() - data.getStartTime()) / 1000.0;
        data.addCheckpoint(checkpointId, elapsed);
    }

    public void resetPlayerTimes(Player player, String trackName) {
        this.stopTimer(player, trackName);
        this.databaseManager.resetPlayerTimes(player.getUniqueId().toString(), trackName);
    }

    public void savePartialTime(Player player, String trackName) {
        PlayerTimerData data = this.getTimerData(player, trackName);
        if (data == null || data.getCheckpointTimes().isEmpty()) {
            return;
        }
        double partialTime = this.getPlayerElapsedTimeUntilLastCheckpoint(player, trackName);
        int checkpointsReached = data.getCheckpointsReached();
        this.databaseManager.savePartialTime(player.getUniqueId(), player.getName(), trackName, partialTime, checkpointsReached);
    }

    public void resetAllTrackTimes(String trackName) {
        this.databaseManager.resetAllTrackTimes(trackName);
    }

    public void updateHUD(Player player, String trackName, Double pbTime, Map<Integer, Double> dbCheckpointTimes, int checkpointcount) {
        PlayerTimerData data = this.getTimerData(player, trackName);
        if (data == null) {
            return;
        }
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        double elapsedSeconds = (double)(System.nanoTime() - data.getStartNanoTime()) / 1.0E9;
        int checkpointCount = data.getCheckpointsReached();
        int totalCheckpoints = data.getTotalCheckpoints();
        if (totalCheckpoints == 0) {
            totalCheckpoints = checkpointcount;
        }
        List<CheckpointData> temp = this.getTempCheckpoints(uuid);
        CheckpointData lastCp = null;
        if (temp != null && !temp.isEmpty()) {
            for (int i = temp.size() - 1; i >= 0; --i) {
                CheckpointData cp = temp.get(i);
                if (!cp.getTrack().equalsIgnoreCase(trackName)) continue;
                lastCp = cp;
                break;
            }
        }
        String deltaStr = "";
        String deltaColor = "\u00a7e";
        if (lastCp != null && dbCheckpointTimes != null && !dbCheckpointTimes.isEmpty()) {
            int cpId = lastCp.getId();
            if (dbCheckpointTimes.containsKey(cpId)) {
                double delta = (double)Math.round((lastCp.getTime() - dbCheckpointTimes.get(cpId)) * 1000.0) / 1000.0;
                if (Math.abs(delta) < 9.0E-4) {
                    deltaStr = " +0.000";
                    deltaColor = "\u00a7e";
                } else if (delta < 0.0) {
                    deltaStr = String.format(" -%.3f", Math.abs(delta));
                    deltaColor = "\u00a7a";
                } else {
                    deltaStr = String.format(" +%.3f", delta);
                    deltaColor = "\u00a7c";
                }
                data.setLastDelta(delta);
                if (this.plugin.getDebugManager().isTimeTrialSystemEnabled()) {
                    String cacheKey = String.valueOf(uuid) + ":delta_calculado_cp" + cpId;
                    Long lastLog = this.lastDebugLogTime.get(cacheKey);
                    long now = System.currentTimeMillis();
                    if (lastLog == null || now - lastLog >= 5000L) {
                        this.lastDebugLogTime.put(cacheKey, now);
                        this.plugin.getDebugManager().logTimeTrialSystem("Delta calculado: Player=" + player.getName() + ", CP=" + cpId + ", Delta=" + String.format("%.3f", delta) + "s (pr\u00f3ximo log em 5s)");
                    }
                }
            } else if (this.plugin.getDebugManager().isTimeTrialSystemEnabled()) {
                this.plugin.getDebugManager().logTimeTrialSystem("Delta n\u00e3o calculado: Player=" + player.getName() + ", CP=" + cpId + " n\u00e3o encontrado no PB (PB tem " + dbCheckpointTimes.size() + " checkpoints)");
            }
        } else if (this.plugin.getDebugManager().isTimeTrialSystemEnabled()) {
            String reason = lastCp == null ? "Nenhum checkpoint tempor\u00e1rio" : (dbCheckpointTimes == null ? "dbCheckpointTimes \u00e9 null" : (dbCheckpointTimes.isEmpty() ? "dbCheckpointTimes est\u00e1 vazio" : "lastDelta \u00e9 NaN (primeira volta)"));
            String cacheKey = String.valueOf(uuid) + ":" + reason;
            Long lastLog = this.lastDebugLogTime.get(cacheKey);
            long now = System.currentTimeMillis();
            if (lastLog == null || now - lastLog >= 5000L) {
                this.lastDebugLogTime.put(cacheKey, now);
                this.plugin.getDebugManager().logTimeTrialSystem("Delta n\u00e3o calculado: Player=" + player.getName() + ", Raz\u00e3o=" + reason + " (pr\u00f3ximo log em 5s)");
            }
        }
        boolean hasPB = pbTime != null;
        boolean worstTime = hasPB && elapsedSeconds > pbTime;
        String timeStr = this.formatTime(elapsedSeconds, hasPB, worstTime);
        String hud = totalCheckpoints == 0 ? timeStr + " \u00a77(No CPs)" : timeStr + " \u00a77(CP " + checkpointCount + "/" + totalCheckpoints + ")";
        if (!deltaStr.isEmpty()) {
            hud = hud + " " + deltaColor + deltaStr;
        }
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(hud));
        } catch (Exception e) {
            this.plugin.getDebugManager().logTimeTrialSystem("[TimerUtils] Falha ao enviar HUD para " + player.getName() + ": " + e.getMessage());
        }
    }

    public void stopTimer(Player player, String trackName) {
        UUID uuid = player.getUniqueId();
        Map<String, PlayerTimerData> map = this.activeTimers.get(uuid);
        this.plugin.getDebugManager().logTimeTrialSystem("[TimerUtils] stopTimer(Player, String) chamado para " + player.getName() + " na pista " + trackName);
        if (map == null) {
            this.plugin.getDebugManager().logTimeTrialSystem("[TimerUtils] Map \u00e9 null para " + player.getName() + ", nada a fazer");
            return;
        }
        PlayerTimerData data = map.get(trackName);
        if (data != null) {
            data.setLastDelta(Double.NaN);
            this.plugin.getDebugManager().logTimeTrialSystem("[TimerUtils] Delta limpo para " + player.getName() + " na pista " + trackName);
        } else {
            this.plugin.getDebugManager().logTimeTrialSystem("[TimerUtils] Nenhum timer ativo para " + player.getName() + " na pista " + trackName);
        }
        List<CheckpointData> tempCps = this.tempCheckpoints.get(uuid);
        if (tempCps != null) {
            tempCps.removeIf(cp -> cp.getTrack().equalsIgnoreCase(trackName));
            if (tempCps.isEmpty()) {
                this.tempCheckpoints.remove(uuid);
            }
        }
        map.remove(trackName);
        if (map.isEmpty()) {
            this.activeTimers.remove(uuid);
            this.warnedPlayersNoCheckpoints.remove(uuid);
            String uuidStr = uuid.toString();
            this.lastWarningTime.entrySet().removeIf(entry -> ((String)entry.getKey()).startsWith(uuidStr + ":"));
            this.lastDebugLogTime.entrySet().removeIf(entry -> ((String)entry.getKey()).startsWith(uuidStr + ":"));
            this.plugin.getDebugManager().logTimeTrialSystem("[TimerUtils] Timer removido completamente de activeTimers para " + player.getName());
        }
    }

    public void addTempCheckpoint(UUID player, int checkpointId, double time, String track) {
        CheckpointData newCheckpoint = new CheckpointData(checkpointId, time, track);
        this.tempCheckpoints.computeIfAbsent(player, k -> Collections.synchronizedList(new ArrayList())).add(newCheckpoint);
        if (this.plugin.getDebugManager().isTimeTrialSystemEnabled()) {
            this.plugin.getDebugManager().logTimeTrialSystem("Checkpoint tempor\u00e1rio adicionado: Player=" + String.valueOf(player) + ", CP=" + checkpointId + ", Time=" + String.format("%.3f", time) + "s, Track=" + track);
        }
    }

    public List<CheckpointData> getTempCheckpoints(UUID player) {
        return this.tempCheckpoints.getOrDefault(player, new ArrayList());
    }

    public void clearTempCheckpoints(UUID player) {
        this.tempCheckpoints.remove(player);
    }

    public void resetTempCheckpoints(UUID playerUUID) {
        List<CheckpointData> list = this.tempCheckpoints.get(playerUUID);
        if (list != null) {
            list.clear();
        }
    }

    public String formatTime(double seconds, boolean hasPB, boolean worstTime) {
        double rounded = (double)Math.round(seconds * 100.0) / 100.0;
        int minutes = (int)(rounded / 60.0);
        double sec = rounded % 60.0;
        String color = !hasPB ? "\u00a7a" : (worstTime ? "\u00a7c" : "\u00a7e");
        if (minutes == 0) {
            return String.format(color + "%05.3f", sec);
        }
        return String.format(color + "%d:%06.3f", minutes, sec);
    }

    public PlayerTimerData getTimerData(Player player, String trackName) {
        Map<String, PlayerTimerData> map = this.activeTimers.get(player.getUniqueId());
        if (map == null) {
            return null;
        }
        return map.get(trackName);
    }

    public PlayerTimerData getTimerData(Player player) {
        String track = this.getActiveTrack(player);
        if (track == null) {
            return null;
        }
        return this.getTimerData(player, track);
    }

    public static class PlayerTimerData {
        private final long startTime;
        private final long startNanoTime;
        private int totalCheckpoints;
        private final int attemptId;
        private final List<Double> checkpointTimes = new ArrayList<Double>();
        private int checkpointsReached;
        private Double lastDelta;
        private boolean finished = false;
        private double finalTime = 0.0;
        private RaceSessionCache sessionCache;

        public PlayerTimerData(long startTime, long startNanoTime, int totalCheckpoints, int attemptId) {
            this.startTime = startTime;
            this.startNanoTime = startNanoTime;
            this.totalCheckpoints = totalCheckpoints;
            this.attemptId = attemptId;
            this.lastDelta = Double.NaN;
            this.checkpointsReached = 0;
        }

        public void setSessionCache(RaceSessionCache sessionCache) {
            this.sessionCache = sessionCache;
        }

        public RaceSessionCache getSessionCache() {
            return this.sessionCache;
        }

        public void setTotalCheckpoints(int totalCheckpoints) {
            this.totalCheckpoints = totalCheckpoints;
        }

        public long getStartTime() {
            return this.startTime;
        }

        public long getStartNanoTime() {
            return this.startNanoTime;
        }

        public int getTotalCheckpoints() {
            return this.totalCheckpoints;
        }

        public List<Double> getCheckpointTimes() {
            return this.checkpointTimes;
        }

        public int getCheckpointsReached() {
            return this.checkpointsReached;
        }

        public Double getLastDelta() {
            return this.lastDelta;
        }

        public void setLastDelta(Double lastDelta) {
            this.lastDelta = lastDelta;
        }

        public int getAttemptId() {
            return this.attemptId;
        }

        public void addCheckpoint(int id, double elapsedTime) {
            ++this.checkpointsReached;
            this.checkpointTimes.add(elapsedTime);
        }

        public boolean isFinished() {
            return this.finished;
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }

        public double getFinalTime() {
            return this.finalTime;
        }

        public void setFinalTime(double finalTime) {
            this.finalTime = finalTime;
        }
    }

    public static class CheckpointData {
        private final int id;
        private final double time;
        private final String track;

        public CheckpointData(int id, double time, String track) {
            this.id = id;
            this.time = time;
            this.track = track;
        }

        public int getId() {
            return this.id;
        }

        public double getTime() {
            return this.time;
        }

        public String getTrack() {
            return this.track;
        }
    }

    public static class RaceSessionCache {
        private final Object[] pbData;
        private final Map<Integer, Double> cpTimes;

        public RaceSessionCache(Object[] pbData, Map<Integer, Double> cpTimes) {
            this.pbData = pbData;
            this.cpTimes = cpTimes;
        }

        public Double getPbTime() {
            return this.pbData != null && this.pbData.length > 0 ? (Double)this.pbData[0] : null;
        }

        public Map<Integer, Double> getCpTimes() {
            return this.cpTimes;
        }
    }
}
