package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TimerUtils {

    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private boolean globalLoopRunning = false;
    private final Map<UUID, List<CheckpointData>> tempCheckpoints = new HashMap<>();

    // Jogadores ativos: UUID -> (TrackName -> PlayerTimerData)
    private final Map<UUID, Map<String, PlayerTimerData>> activeTimers = new ConcurrentHashMap<>();

    // Cache de avisos para evitar spam de logs
    private final Set<UUID> warnedPlayersNoCheckpoints = ConcurrentHashMap.newKeySet();

    // Timestamp do último warning por checkpoint (Key: "UUID:checkpointId")
    private final Map<String, Long> lastWarningTime = new ConcurrentHashMap<>();

    public TimerUtils(FormulaRacing plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    // ========================================================================================
    // NOVA CLASSE DE CACHE (A SOLUÇÃO PARA O VAZAMENTO DE 15GB)
    // Ela guarda os dados do banco na memória para não precisar consultar a cada tick.
    // ========================================================================================
    public static class RaceSessionCache {
        private final Object[] pbData;
        private final Map<Integer, Double> cpTimes;

        public RaceSessionCache(Object[] pbData, Map<Integer, Double> cpTimes) {
            this.pbData = pbData;
            this.cpTimes = cpTimes;
        }

        public Double getPbTime() {
            return (pbData != null && pbData.length > 0) ? (Double) pbData[0] : null;
        }

        public Map<Integer, Double> getCpTimes() {
            return cpTimes;
        }
    }

    // ========================================================================================
    // START TIMER OTIMIZADO (CONSULTA ÚNICA)
    // ========================================================================================
    public void startTimer(Player player, String trackName) {
        stopTimer(player, trackName);
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // 1. OTIMIZAÇÃO: Busca os dados do banco DE FORMA ASSÍNCRONA ANTES de começar a contar
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            // Pega o PB e os tempos dos Checkpoints uma única vez
            Object[] pb = databaseManager.getPlayerBestTime(playerName, trackName);
            Map<Integer, Double> cp = databaseManager.getCheckpointTimes(uuid, trackName);

            // Cria o objeto de cache
            RaceSessionCache cache = new RaceSessionCache(pb, cp);

            // 2. Volta para a thread principal para registrar o timer e iniciar o loop
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                long now = System.currentTimeMillis();
                long nowNano = System.nanoTime();
                int attemptId = (int) (now & 0xFFFFFFF);
                int totalCheckpoints = databaseManager.getCheckpointIds(trackName).size();

                PlayerTimerData data = new PlayerTimerData(now, nowNano, totalCheckpoints, attemptId);

                // INJETA O CACHE NO DADO DO JOGADOR
                data.setSessionCache(cache);

                activeTimers
                        .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                        .put(trackName, data);

                // Inicia o loop global se ainda não estiver rodando
                if (!globalLoopRunning) {
                    startGlobalLoop();
                }
            });
        });
    }

    // ========================================================================================
    // GLOBAL LOOP OTIMIZADO (ZERO DATABASE CALLS)
    // ========================================================================================
    public void startGlobalLoop() {
        if (globalLoopRunning) return;
        globalLoopRunning = true;

        new BukkitRunnable() {
            @Override
            public void run() {
                // OTIMIZAÇÃO DE CPU: Se não tiver ninguém correndo, desliga o loop automaticamente
                if (activeTimers.isEmpty()) {
                    globalLoopRunning = false;
                    this.cancel();
                    return;
                }

                for (Map.Entry<UUID, Map<String, PlayerTimerData>> playerEntry : activeTimers.entrySet()) {
                    Player player = Bukkit.getPlayer(playerEntry.getKey());
                    if (player == null || !player.isOnline()) continue;

                    for (Map.Entry<String, PlayerTimerData> trackEntry : playerEntry.getValue().entrySet()) {
                        String trackName = trackEntry.getKey();
                        PlayerTimerData data = trackEntry.getValue();

                        // LÓGICA DE MEMÓRIA: Lê o cache em vez de ir ao banco de dados
                        RaceSessionCache cache = data.getSessionCache();

                        // Atualiza o HUD instantaneamente (sem lag, sem travar thread)
                        if (cache != null) {
                            updateHUD(player, trackName, cache.getPbTime(), cache.getCpTimes());
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void stopTimer(Player player) {
        activeTimers.remove(player.getUniqueId());
    }

    public boolean isTimerRunning(Player player, String trackName) {
        Map<String, PlayerTimerData> map = activeTimers.get(player.getUniqueId());
        return map != null && map.containsKey(trackName);
    }

    public boolean isTimerRunning(Player player) {
        return activeTimers.containsKey(player.getUniqueId());
    }

    public String getActiveTrack(Player player) {
        Map<String, PlayerTimerData> map = activeTimers.get(player.getUniqueId());
        if (map != null && !map.isEmpty()) return map.keySet().iterator().next();
        return null;
    }

    /**
     * Recarrega o cache de PB e checkpoints após salvar um tempo.
     * Útil quando o jogador completa uma volta e inicia outra sem sair do servidor.
     */
    public void reloadCacheAsync(Player player, String trackName) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Object[] pb = databaseManager.getPlayerBestTime(playerName, trackName);
            Map<Integer, Double> cp = databaseManager.getCheckpointTimes(uuid, trackName);
            RaceSessionCache newCache = new RaceSessionCache(pb, cp);

            // Se conseguiu carregar checkpoints, limpa os warnings
            int cpCount = newCache.getCpTimes() != null ? newCache.getCpTimes().size() : 0;
            if (cpCount > 0) {
                warnedPlayersNoCheckpoints.remove(uuid);
                // Limpa warnings de checkpoints específicos
                lastWarningTime.entrySet().removeIf(entry -> entry.getKey().startsWith(uuid.toString() + ":"));
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerTimerData data = getTimerData(player, trackName);
                if (data != null) {
                    data.setSessionCache(newCache);
                }
            });
        });
    }

    /* ======================== ELAPSED ======================== */

    public double getPlayerElapsedTime(Player player, String trackName) {
        PlayerTimerData data = getTimerData(player, trackName);
        return (data != null) ? (System.nanoTime() - data.getStartNanoTime()) / 1_000_000_000.0 : 0.0;
    }

    public double getPlayerElapsedTime(Player player) {
        String track = getActiveTrack(player);
        if (track == null) return 0.0;
        return getPlayerElapsedTime(player, track);
    }

    public double getPlayerElapsedTimeUntilLastCheckpoint(Player player, String trackName) {
        PlayerTimerData data = getTimerData(player, trackName);
        if (data == null || data.getCheckpointTimes().isEmpty()) return 0.0;

        return data.getCheckpointTimes().get(data.getCheckpointTimes().size() - 1);
    }

    /* ======================== CHECKPOINT ======================== */

    public void addCheckpoint(Player player, int checkpointId) {
        String track = getActiveTrack(player);
        if (track == null) return;
        addCheckpoint(player, track, checkpointId);
    }

    public void addCheckpoint(Player player, String trackName, int checkpointId) {
        PlayerTimerData data = getTimerData(player, trackName);
        if (data == null) return;

        double elapsed = (System.currentTimeMillis() - data.getStartTime()) / 1000.0;
        data.addCheckpoint(checkpointId, elapsed);
    }

    /* ======================== RESET / PARTIAL ======================== */

    public void resetPlayerTimes(Player player, String trackName) {
        stopTimer(player, trackName);
        databaseManager.resetPlayerTimes(player.getUniqueId().toString(), trackName);
    }

    public void savePartialTime(Player player, String trackName) {
        PlayerTimerData data = getTimerData(player, trackName);
        if (data == null || data.getCheckpointTimes().isEmpty()) return;

        double partialTime = getPlayerElapsedTimeUntilLastCheckpoint(player, trackName);
        int checkpointsReached = data.getCheckpointsReached().size();

        databaseManager.savePartialTime(player.getUniqueId(), player.getName(), trackName, partialTime, checkpointsReached);
    }

    public void resetAllTrackTimes(String trackName) {
        databaseManager.resetAllTrackTimes(trackName);
    }

    /**
     * Atualiza o HUD do jogador sem realizar nenhuma consulta ao banco de dados.
     * * @param player O jogador.
     * @param trackName Nome da pista.
     * @param pbTime O melhor tempo do jogador (null se não tiver).
     * @param dbCheckpointTimes Mapa com os tempos de CP salvos no banco para calcular o delta.
     */
    public void updateHUD(Player player, String trackName, Double pbTime, Map<Integer, Double> dbCheckpointTimes) {
        PlayerTimerData data = getTimerData(player, trackName);
        if (data == null) return;

        UUID uuid = player.getUniqueId();
        // Cálculo de tempo usando nanoTime para precisão total
        double elapsedSeconds = (System.nanoTime() - data.getStartNanoTime()) / 1_000_000_000.0;

        int checkpointCount = data.getCheckpointsReached().size();
        int totalCheckpoints = data.getTotalCheckpoints();

        // 1. Busca o ÚLTIMO checkpoint registrado (o mais recente)
        List<CheckpointData> temp = getTempCheckpoints(uuid);
        CheckpointData lastCp = null;

        if (temp != null && !temp.isEmpty()) {
            // Percorre de trás para frente para pegar o checkpoint mais recente desta track
            for (int i = temp.size() - 1; i >= 0; i--) {
                CheckpointData cp = temp.get(i);
                if (cp.getTrack().equalsIgnoreCase(trackName)) {
                    lastCp = cp;
                    break;
                }
            }
        }

        String deltaStr = "";
        String deltaColor = "§e";

        // 2. CÁLCULO DO DELTA (compara o checkpoint atual com o PB do mesmo checkpoint)
        if (lastCp != null && dbCheckpointTimes != null && !dbCheckpointTimes.isEmpty()) {
            int cpId = lastCp.getId();

            if (dbCheckpointTimes.containsKey(cpId)) {
                double delta = Math.round((lastCp.getTime() - dbCheckpointTimes.get(cpId)) * 1000.0) / 1000.0;

                if (Math.abs(delta) < 0.0009) {
                    deltaStr = " +0.000";
                    deltaColor = "§e";
                } else if (delta < 0) {
                    deltaStr = String.format(" -%.3f", Math.abs(delta));
                    deltaColor = "§a"; // Verde se for mais rápido
                } else {
                    deltaStr = String.format(" +%.3f", delta);
                    deltaColor = "§c"; // Vermelho se for mais lento
                }
                data.setLastDelta(delta);
            }
        } else {
            return ;
            //#Isso tava flodando o console :crying a lot:
        }

        // 3. LOGICA DO PB (Usando o valor passado pelo parâmetro)
        boolean hasPB = (pbTime != null);
        boolean worstTime = false;

        if (hasPB && elapsedSeconds > pbTime) {
            worstTime = true; // Timer fica vermelho se ultrapassar o PB
        }

        // 4. FORMATAÇÃO E EXIBIÇÃO
        String timeStr = formatTime(elapsedSeconds, hasPB, worstTime);
        String hud = timeStr + " §7(CP " + checkpointCount + "/" + totalCheckpoints + ")";

        if (!deltaStr.isEmpty()) {
            hud += " " + deltaColor + " " + deltaStr.trim();
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
    }

    public void stopTimer(Player player, String trackName) {
        UUID uuid = player.getUniqueId();
        Map<String, PlayerTimerData> map = activeTimers.get(uuid);
        if (map == null) return;

        map.remove(trackName);
        if (map.isEmpty()) {
            activeTimers.remove(uuid);
            warnedPlayersNoCheckpoints.remove(uuid);
            // Limpa warnings de checkpoints específicos
            lastWarningTime.entrySet().removeIf(entry -> entry.getKey().startsWith(uuid.toString() + ":"));
        }
    }

    // Método para adicionar checkpoint temporário
    public void addTempCheckpoint(UUID player, int checkpointId, double time, String track) {
        tempCheckpoints.computeIfAbsent(player, k -> new ArrayList<>())
                .add(new CheckpointData(checkpointId, time, track));
    }

    // Método para pegar checkpoints temporários de um jogador
    public List<CheckpointData> getTempCheckpoints(UUID player) {
        return tempCheckpoints.getOrDefault(player, new ArrayList<>());
    }

    // Método para resetar sem salvar
    public void clearTempCheckpoints(UUID player) {
        tempCheckpoints.remove(player);
    }

    public void resetTempCheckpoints(UUID playerUUID) {
        List<CheckpointData> list = tempCheckpoints.get(playerUUID);
        if (list != null) {
            list.clear();
        }
    }


    public String formatTime(double seconds, boolean hasPB, boolean worstTime) {
        // Arredonda para o múltiplo mais próximo de 0.010 (10 ms)
        double rounded = Math.round(seconds * 100.0) / 100.0;

        int minutes = (int) (rounded / 60);
        double sec = rounded % 60;

        String color;
        if (!hasPB) color = "§a";
        else if (worstTime) color = "§c";
        else color = "§e";

        // Se for menor de 1 minuto → formato 00.000
        if (minutes == 0) {
            return String.format(color + "%05.3f", sec);
        }

        // Se for 1 minuto ou mais → formato M:SS.SSS
        return String.format(color + "%d:%06.3f", minutes, sec);
    }

    /* ======================== GETTER ======================== */

    public PlayerTimerData getTimerData(Player player, String trackName) {
        Map<String, PlayerTimerData> map = activeTimers.get(player.getUniqueId());
        if (map == null) return null;
        return map.get(trackName);
    }

    public PlayerTimerData getTimerData(Player player) {
        String track = getActiveTrack(player);
        if (track == null) return null;
        return getTimerData(player, track);
    }

    /* ======================== DATA CLASS ======================== */

    public static class PlayerTimerData {
        private final long startTime;
        private final long startNanoTime;
        private final int totalCheckpoints;
        private final int attemptId;

        private final List<Double> checkpointTimes = new ArrayList<>();
        private final Set<Integer> checkpointsReached = new HashSet<>();
        private Double lastDelta;

        private boolean finished = false;
        private double finalTime = 0.0;

        // CAMPO NOVO PARA O CACHE
        private RaceSessionCache sessionCache;

        public PlayerTimerData(long startTime, long startNanoTime, int totalCheckpoints, int attemptId) {
            this.startTime = startTime;
            this.startNanoTime = startNanoTime;
            this.totalCheckpoints = totalCheckpoints;
            this.attemptId = attemptId;
            this.lastDelta = Double.NaN;
        }

        // GETTERS E SETTERS DO CACHE
        public void setSessionCache(RaceSessionCache sessionCache) {
            this.sessionCache = sessionCache;
        }

        public RaceSessionCache getSessionCache() {
            return sessionCache;
        }

        public long getStartTime() { return startTime; }
        public long getStartNanoTime() { return startNanoTime; }
        public int getTotalCheckpoints() { return totalCheckpoints; }
        public List<Double> getCheckpointTimes() { return checkpointTimes; }
        public Set<Integer> getCheckpointsReached() { return checkpointsReached; }
        public Double getLastDelta() { return lastDelta; }
        public void setLastDelta(Double lastDelta) { this.lastDelta = lastDelta; }
        public int getAttemptId() { return attemptId; }

        public void addCheckpoint(int id, double elapsedTime) {
            checkpointsReached.add(id);
            checkpointTimes.add(elapsedTime);
        }

        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; }

        public double getFinalTime() { return finalTime; }
        public void setFinalTime(double finalTime) { this.finalTime = finalTime; }
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

        public int getId() { return id; }
        public double getTime() { return time; }
        public String getTrack() { return track; }
    }
}