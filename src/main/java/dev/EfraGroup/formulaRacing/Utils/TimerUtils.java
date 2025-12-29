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

    public void startTimer(Player player, String trackName) {
        stopTimer(player, trackName); // Limpa resquícios
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        plugin.getLogger().info("§e[TIMER-DEBUG] §fTentando iniciar timer para: " + playerName + " na pista: " + trackName);

        // 1. REGISTRO IMEDIATO (Síncrono)
        long now = System.currentTimeMillis();
        long nowNano = System.nanoTime();
        int attemptId = (int) (now & 0xFFFFFFF);

        // Começamos com 0 e atualizamos quando o banco responder
        int initialCPs = 0;

        PlayerTimerData data = new PlayerTimerData(now, nowNano, initialCPs, attemptId);
        activeTimers.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(trackName, data);

        plugin.getLogger().info("§a[TIMER-DEBUG] §fRegistro síncrono concluído. activeTimers size: " + activeTimers.size());

        // Garante que o loop esteja rodando
        if (!globalLoopRunning) {
            plugin.getLogger().info("§b[TIMER-DEBUG] §fGlobal Loop não estava rodando. Iniciando agora...");
            startGlobalLoop();
        } else {
            plugin.getLogger().info("§b[TIMER-DEBUG] §fGlobal Loop já está operacional.");
        }

        // 2. CARREGAMENTO DE DADOS PESADOS (Assíncrono)
        plugin.getLogger().info("§d[TIMER-DEBUG] §fDisparando busca assíncrona de PB/Checkpoints...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Busca os dados do banco
                Object[] pb = databaseManager.getPlayerBestTime(playerName, trackName);
                Map<Integer, Double> cp = databaseManager.getCheckpointTimes(uuid, trackName);
                List<Integer> cpIds = databaseManager.getCheckpointIds(trackName);
                int realTotalCPs = (cpIds != null) ? cpIds.size() : 0;

                RaceSessionCache cache = new RaceSessionCache(pb, cp);

                // 3. ATUALIZA O OBJETO EXISTENTE NA THREAD PRINCIPAL
                Bukkit.getScheduler().runTask(plugin, () -> {
                    PlayerTimerData existingData = getTimerData(player, trackName);
                    if (existingData != null) {
                        existingData.setSessionCache(cache);
                        // IMPORTANTE: Adicione um setter para totalCheckpoints na sua classe PlayerTimerData se não tiver
                        // existingData.setTotalCheckpoints(realTotalCPs);

                        plugin.getLogger().info("§d[TIMER-DEBUG] §aCache injetado com sucesso! PB: " + (pb != null ? pb[0] : "Nenhum") + " | TotalCPs: " + realTotalCPs);
                    } else {
                        plugin.getLogger().warning("§c[TIMER-DEBUG] §fErro: O jogador parou o timer antes dos dados do banco chegarem.");
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("§c[TIMER-DEBUG] §fErro crítico na busca assíncrona: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    public void startGlobalLoop() {
        if (globalLoopRunning) return;
        globalLoopRunning = true;

        plugin.getLogger().info("§b[LOOP-DEBUG] Tentando iniciar BukkitRunnable...");

        new BukkitRunnable() {
            private int tickCounter = 0;

            @Override
            public void run() {
                tickCounter++;

                if (activeTimers.isEmpty()) {
                    plugin.getLogger().info("§c[LOOP-DEBUG] Loop encerrado: activeTimers está vazio.");
                    globalLoopRunning = false;
                    this.cancel();
                    return;
                }

                // Log de batida de coração a cada 2 segundos (40 ticks)
                if (tickCounter % 40 == 0) {
                    plugin.getLogger().info("§7[LOOP-DEBUG] Rodando... Ativos: " + activeTimers.size());
                }

                for (Map.Entry<UUID, Map<String, PlayerTimerData>> playerEntry : activeTimers.entrySet()) {
                    UUID uuid = playerEntry.getKey();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player == null || !player.isOnline()) {
                        if (tickCounter % 40 == 0) plugin.getLogger().warning("§c[LOOP-DEBUG] Player " + uuid + " não encontrado ou offline.");
                        continue;
                    }

                    for (Map.Entry<String, PlayerTimerData> trackEntry : playerEntry.getValue().entrySet()) {
                        String trackName = trackEntry.getKey();
                        PlayerTimerData data = trackEntry.getValue();
                        RaceSessionCache cache = data.getSessionCache();

                        // Se chegar aqui, o código ESTÁ tentando enviar a mensagem
                        if (tickCounter % 40 == 0) {
                            plugin.getLogger().info("§a[LOOP-DEBUG] Enviando HUD para " + player.getName() + " | Cache: " + (cache != null));
                        }

                        Double pb = (cache != null) ? cache.getPbTime() : null;
                        Map<Integer, Double> cpTimes = (cache != null) ? cache.getCpTimes() : null;

                        updateHUD(player, trackName, pb, cpTimes);
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

    public void updateHUD(Player player, String trackName, Double pbTime, Map<Integer, Double> dbCheckpointTimes) {
        PlayerTimerData data = getTimerData(player, trackName);
        if (data == null) return;

        UUID uuid = player.getUniqueId();
        double elapsedSeconds = (System.nanoTime() - data.getStartNanoTime()) / 1_000_000_000.0;

        int checkpointCount = data.getCheckpointsReached().size();
        int totalCheckpoints = data.getTotalCheckpoints();

        // 1. Busca o ÚLTIMO checkpoint registrado
        List<CheckpointData> temp = getTempCheckpoints(uuid);
        CheckpointData lastCp = null;

        if (temp != null && !temp.isEmpty()) {
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

        // 2. CÁLCULO DO DELTA (Sem o return fatal)
        if (lastCp != null && dbCheckpointTimes != null && !dbCheckpointTimes.isEmpty()) {
            int cpId = lastCp.getId();

            if (dbCheckpointTimes.containsKey(cpId)) {
                double delta = Math.round((lastCp.getTime() - dbCheckpointTimes.get(cpId)) * 1000.0) / 1000.0;

                if (Math.abs(delta) < 0.0009) {
                    deltaStr = " +0.000";
                    deltaColor = "§e";
                } else if (delta < 0) {
                    deltaStr = String.format(" -%.3f", Math.abs(delta));
                    deltaColor = "§a";
                } else {
                    deltaStr = String.format(" +%.3f", delta);
                    deltaColor = "§c";
                }
                data.setLastDelta(delta);
            }
        }
        // REMOVIDO: else { return; } -> Agora o código continua mesmo sem delta!

        // 3. LOGICA DO PB
        boolean hasPB = (pbTime != null);
        boolean worstTime = (hasPB && elapsedSeconds > pbTime);

        // 4. FORMATAÇÃO E EXIBIÇÃO
        String timeStr = formatTime(elapsedSeconds, hasPB, worstTime);

        // Se o totalCheckpoints ainda for 0 (banco não carregou), vamos mostrar algo amigável
        String cpStatus = (totalCheckpoints > 0) ? checkpointCount + "/" + totalCheckpoints : String.valueOf(checkpointCount);

        String hud = timeStr + " §7(CP " + cpStatus + ")";

        if (!deltaStr.isEmpty()) {
            hud += " " + deltaColor + deltaStr;
        }

        // DEBUG: Se você não ver nada no jogo, cheque se este log aparece no console
        // if (Bukkit.getCurrentTick() % 40 == 0) plugin.getLogger().info("HUD Enviado: " + hud);

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

            // Log para confirmar que o sistema limpou tudo
            plugin.getLogger().info("§7[TIMER-DEBUG] Cache totalmente limpo para " + player.getName());
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