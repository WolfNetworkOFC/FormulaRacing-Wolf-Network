package dev.EfraGroup.formulaRacing.AI;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gerencia a gravação de linhas de corrida baseada no timer do heat.
 * A gravação inicia quando o heat transiciona para RACING e finaliza
 * quando o heat termina (FINISHED).
 */
public class AIRacingLineRecorder {

    private final FormulaRacing plugin;
    private final AIRacingLineManager racingLineManager;
    private final Map<UUID, RecordingSession> activeSessions;
    private FRTask cleanupTask;

    public AIRacingLineRecorder(FormulaRacing plugin, AIRacingLineManager racingLineManager) {
        this.plugin = plugin;
        this.racingLineManager = racingLineManager;
        this.activeSessions = new HashMap<>();
        startCleanupTask();
    }

    /**
     * Registrador: marca o jogador como pronto para gravar na próxima corrida.
     * A gravação real começa quando o heat vai para RACING.
     */
    public boolean startRecording(Player player, String trackName) {
        UUID uuid = player.getUniqueId();
        if (activeSessions.containsKey(uuid)) {
            player.sendMessage("§cVocê já está gravando uma linha de corrida!");
            return false;
        }

        RecordingSession session = new RecordingSession(player, trackName);
        activeSessions.put(uuid, session);

        player.sendMessage("");
        player.sendMessage("§a═══════════════════════════════");
        player.sendMessage("§e  Gravação de Linha Registrada");
        player.sendMessage("");
        player.sendMessage("§f  Pista: §b" + trackName);
        player.sendMessage("§f  A gravação iniciará quando a corrida começar");
        player.sendMessage("§f  e terminará automaticamente ao final");
        player.sendMessage("");
        player.sendMessage("§c  Use /ai record stop para cancelar");
        player.sendMessage("§a═══════════════════════════════");

        plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Registro de gravação para " + player.getName() + " na pista " + trackName);
        return true;
    }

    /**
     * Cancela a gravação registrada (antes ou durante a corrida).
     */
    public boolean stopRecording(Player player) {
        UUID uuid = player.getUniqueId();
        RecordingSession session = activeSessions.remove(uuid);

        if (session == null) {
            player.sendMessage("§cVocê não está gravando nenhuma linha de corrida!");
            return false;
        }

        session.cancel();
        player.sendMessage("§eGravação cancelada!");
        plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Gravação cancelada para " + player.getName());
        return true;
    }

    /**
     * Chamado pelo listener quando um heat transiciona para RACING.
     * Inicia a gravação para todos os jogadores registrados que estão nesse heat.
     */
    public void onHeatRacing(Heats heat) {
        if (heat == null) return;

        for (RecordingSession session : activeSessions.values()) {
            if (session.isCancelled() || session.isCompleted()) continue;

            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) continue;

            // Verifica se o jogador está nesse heat
            if (heat.getDriver(player.getUniqueId()) == null) continue;

            session.startRecording();
        }
    }

    /**
     * Chamado pelo listener quando um heat transiciona para FINISHED.
     * Finaliza e salva a gravação de todos os jogadores registrados.
     */
    public void onHeatFinished(Heats heat) {
        if (heat == null) return;

        for (RecordingSession session : activeSessions.values()) {
            if (session.isCancelled() || session.isCompleted()) continue;
            if (!session.isRecording()) continue;

            Player player = session.getPlayer();
            if (player == null) continue;

            // Verifica se o jogador estava nesse heat
            if (heat.getDriver(player.getUniqueId()) != null) {
                session.complete();
            }
        }
    }

    public boolean isRecording(UUID uuid) {
        RecordingSession session = activeSessions.get(uuid);
        return session != null && !session.isCancelled() && !session.isCompleted();
    }

    public RecordingSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    private void startCleanupTask() {
        cleanupTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            List<UUID> toRemove = new ArrayList<>();

            for (Map.Entry<UUID, RecordingSession> entry : activeSessions.entrySet()) {
                RecordingSession session = entry.getValue();
                Player player = session.getPlayer();
                if (player == null || !player.isOnline() || session.isInactiveTooLong()) {
                    toRemove.add(entry.getKey());
                    session.cancel();
                }
            }

            for (UUID uuid : toRemove) {
                activeSessions.remove(uuid);
            }
        }, 200L, 200L);
    }

    public void cleanup() {
        for (RecordingSession session : activeSessions.values()) {
            session.cancel();
        }
        activeSessions.clear();

        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
    }

    public class RecordingSession {
        private final Player player;
        private final String trackName;
        private final List<Location> recordedPoints;
        private final List<Location> brakingPoints;
        private final List<Location> accelerationPoints;
        private final long registerTime;
        private long lastUpdateTime;
        private long recordingStartTime;
        private FRTask recordingTask;
        private boolean recording;
        private boolean cancelled;
        private boolean completed;

        public RecordingSession(Player player, String trackName) {
            this.player = player;
            this.trackName = trackName == null ? "" : trackName.replace(" ", "").toLowerCase();
            this.recordedPoints = new ArrayList<>();
            this.brakingPoints = new ArrayList<>();
            this.accelerationPoints = new ArrayList<>();
            this.registerTime = System.currentTimeMillis();
            this.lastUpdateTime = registerTime;
            this.recordingStartTime = 0L;
            this.recording = false;
            this.cancelled = false;
            this.completed = false;
        }

        /**
         * Inicia a gravação real (chamado quando o heat vai para RACING).
         */
        public void startRecording() {
            if (recording || cancelled || completed) return;
            this.recording = true;
            this.recordingStartTime = System.currentTimeMillis();

            // Mensagem no chat para o jogador
            Player p = getPlayer();
            if (p != null && p.isOnline()) {
                p.sendMessage("");
                p.sendMessage("§a═══════════════════════════════");
                p.sendMessage("§e  ▶ Gravação Iniciada!");
                p.sendMessage("");
                p.sendMessage("§f  A corrida começou — gravando linha de corrida");
                p.sendMessage("§f  A linha será salva ao final da corrida");
                p.sendMessage("§a═══════════════════════════════");
            }

            recordingTask = SchedulerHelper.runTaskTimer(plugin, () -> {
                if (cancelled || completed || !recording) {
                    return;
                }

                Player currentPlayer = getPlayer();
                if (currentPlayer == null || !currentPlayer.isOnline()) {
                    return;
                }

                Location currentLoc = currentPlayer.getLocation();
                lastUpdateTime = System.currentTimeMillis();

                if (shouldAddPoint(currentLoc)) {
                    addPoint(currentLoc, currentPlayer);
                }
            }, 1L, 2L);

            plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Gravação iniciada para " + player.getName() + " na pista " + trackName);
        }

        private boolean shouldAddPoint(Location newLoc) {
            if (newLoc == null || newLoc.getWorld() == null) {
                return false;
            }

            if (recordedPoints.isEmpty()) {
                return true;
            }

            Location lastPoint = recordedPoints.get(recordedPoints.size() - 1);
            if (!lastPoint.getWorld().equals(newLoc.getWorld())) {
                return true;
            }

            return lastPoint.distanceSquared(newLoc) >= 4.0;
        }

        private void addPoint(Location loc, Player currentPlayer) {
            recordedPoints.add(loc.clone());

            double speed = currentPlayer.getVelocity().length();
            if (speed < 0.3 && recordedPoints.size() > 10) {
                if (brakingPoints.isEmpty() || brakingPoints.get(brakingPoints.size() - 1).distanceSquared(loc) > 25.0) {
                    brakingPoints.add(loc.clone());
                }
            }

            if (speed > 0.6 && recordedPoints.size() > 10) {
                if (accelerationPoints.isEmpty() || accelerationPoints.get(accelerationPoints.size() - 1).distanceSquared(loc) > 25.0) {
                    accelerationPoints.add(loc.clone());
                }
            }
        }

        /**
         * Finaliza a gravação e salva a linha (chamado quando o heat termina).
         */
        public void complete() {
            if (completed || cancelled) return;
            completed = true;
            recording = false;

            if (recordingTask != null && !recordingTask.isCancelled()) {
                recordingTask.cancel();
            }

            Player p = getPlayer();

            if (recordedPoints.size() < 5) {
                activeSessions.remove(player.getUniqueId());
                if (p != null && p.isOnline()) {
                    p.sendMessage("§cGravação descartada — poucos pontos registrados.");
                }
                plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Gravação descartada para " + player.getName() + " — poucos pontos (" + recordedPoints.size() + ")");
                return;
            }

            AIRacingLine line = racingLineManager.getRacingLine(trackName);
            line.clear();

            int totalPoints = recordedPoints.size();
            for (int i = 0; i < totalPoints; i++) {
                Location loc = recordedPoints.get(i);
                line.addIdealLinePoint(loc, calculateSpeedForPoint(i, totalPoints));
            }

            for (Location brakePoint : brakingPoints) {
                line.addBrakingPoint(brakePoint);
            }
            for (Location accelPoint : accelerationPoints) {
                line.addAccelerationPoint(accelPoint);
            }

            // Salvar no arquivo
            racingLineManager.saveAllRacingLines();

            activeSessions.remove(player.getUniqueId());

            // Mensagem no chat para o jogador
            if (p != null && p.isOnline()) {
                p.sendMessage("");
                p.sendMessage("§a═══════════════════════════════");
                p.sendMessage("§e  ■ Gravação Finalizada!");
                p.sendMessage("");
                p.sendMessage("§f  Pista: §b" + trackName);
                p.sendMessage("§f  Pontos gravados: §a" + recordedPoints.size());
                p.sendMessage("§f  Pontos de frenagem: §c" + brakingPoints.size());
                p.sendMessage("§f  Pontos de aceleração: §e" + accelerationPoints.size());
                p.sendMessage("§f  Linha salva em §b" + trackName);
                p.sendMessage("§a═══════════════════════════════");
            }

            plugin.getDebugManager().logRaceSystem("[AI-RECORDER] Linha salva para " + trackName + " com " + recordedPoints.size() + " pontos");
        }

        private double calculateSpeedForPoint(int index, int totalPoints) {
            if (totalPoints <= 1) {
                return 0.6;
            }

            double position = (double) index / (double) (totalPoints - 1);
            return Math.max(0.35, Math.min(0.95, 0.65 + (0.25 * Math.sin(position * Math.PI * 2))));
        }

        public void cancel() {
            cancelled = true;
            recording = false;
            if (recordingTask != null && !recordingTask.isCancelled()) {
                recordingTask.cancel();
            }
        }

        public boolean isInactiveTooLong() {
            // 5 minutos sem atividade
            return System.currentTimeMillis() - lastUpdateTime > 300000L;
        }

        public Player getPlayer() {
            if (player != null && player.isOnline()) {
                return player;
            }
            return Bukkit.getPlayer(player.getUniqueId());
        }

        public String getTrackName() {
            return trackName;
        }

        public boolean isRecording() {
            return recording;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public boolean isCompleted() {
            return completed;
        }

        public List<Location> getRecordedPoints() {
            return new ArrayList<>(recordedPoints);
        }
    }
}
