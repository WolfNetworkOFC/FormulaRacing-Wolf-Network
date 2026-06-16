package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatConfig;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sessão de corrida baseada em tempo
 * Gerencia corridas onde o tempo determina o fim, não o número de voltas
 */
public class TimeBasedRaceSession extends RaceSession {

    private FRTask timeMonitorTask;
    private boolean lastLapAnnounced = false;

    public TimeBasedRaceSession(FormulaRacing plugin) {
        super(plugin);
    }

    @Override
    public void start(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        // Resetar estados de runtime primeiro
        config.reset();

        // Configurar para modo tempo
        config.setTimeBased(true);

        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();

        plugin.getDebugManager().logRaceSystem(
            "[TIME-BASED] Sessão baseada em tempo iniciada para Heat " + heat.getId() +
            " - Limite: " + config.getTimeLimitSeconds() + "s"
        );

        // Iniciar monitoramento de tempo
        startTimeMonitoring(heat);
    }

    private void startTimeMonitoring(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        stopTimeMonitoring();

        // Otimização: Verificar a cada segundo (20 ticks)
        timeMonitorTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                stopTimeMonitoring();
                return;
            }

            // Otimização: Calcular tempo restante de forma eficiente
            long remainingTime = getTimeRemaining(heat);

            // Anunciar avisos de tempo
            announceTimeWarnings(heat, remainingTime);

            // Verificar se o tempo acabou
            if (remainingTime <= 0 && !config.isLastLapTriggered()) {
                triggerLastLap(heat);
            }

        }, 20L, 20L);

        plugin.getDebugManager().logRaceSystem(
            "[TIME-BASED] Monitoramento de tempo iniciado"
        );
    }

    private long getTimeRemaining(Heats heat) {
        if (heat.getStartTime() == null) {
            return heat.getHeatConfig().getTimeLimitSeconds() * 1000L;
        }

        // Otimização: Cálculo direto de tempo restante
        long elapsed = System.currentTimeMillis() - heat.getStartTime().toEpochMilli();
        long limitMs = (long) heat.getHeatConfig().getTimeLimitSeconds() * 1000L;
        return Math.max(0L, limitMs - elapsed);
    }

    private void announceTimeWarnings(Heats heat, long remainingMs) {
        FormulaRacing plugin = heat.getPlugin();
        long remainingSeconds = remainingMs / 1000L;

        // Anunciar em tempos específicos
        if (remainingSeconds == 60 || remainingSeconds == 30 ||
            remainingSeconds == 10 || remainingSeconds == 5 ||
            (remainingSeconds <= 3 && remainingSeconds > 0)) {

            String message = ChatColor.YELLOW + "⏱ Tempo restante: " + ChatColor.WHITE + remainingSeconds + "s";
            Bukkit.broadcastMessage(message);

            plugin.getDebugManager().logRaceSystem(
                "[TIME-BASED] Aviso de tempo: " + remainingSeconds + "s restantes"
            );
        }
    }

    private void triggerLastLap(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        config.setLastLapTriggered(true);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "⚠ ÚLTIMA VOLTA ⚠");
        Bukkit.broadcastMessage(ChatColor.GRAY + "O líder deve cruzar a linha de chegada para finalizar a corrida!");
        Bukkit.broadcastMessage("");

        plugin.getDebugManager().logRaceSystem(
            "[TIME-BASED] Última volta acionada - Líder deve cruzar a linha"
        );
    }

    public boolean passLap(Heats heat, Driver driver) {
        HeatConfig config = heat.getHeatConfig();

        // Se não está em modo tempo, usar lógica normal
        if (!config.isTimeBased()) {
            return super.passLap(heat, driver);
        }

        // Lógica de tempo: verificar se é o líder e se última volta foi acionada
        if (config.isLastLapTriggered() && !config.isRaceFinishedForAll()) {
            // Verificar se este é o líder
            Optional<Driver> leaderOpt = getLeader(heat);

            if (leaderOpt.isPresent() && leaderOpt.get().getUuid().equals(driver.getUuid())) {
                // Líder cruzou a linha - finalizar corrida para todos
                finishRaceForAll(heat);
            }
        }

        return true;
    }

    private Optional<Driver> getLeader(Heats heat) {
        // Otimização: Usar stream eficiente para encontrar o líder
        return heat.getDrivers().values().stream()
            .filter(d -> !d.isFinished() && !d.isDnf())
            .min((d1, d2) -> {
                // Comparação rápida por volta
                int lapCompare = Integer.compare(d2.getLapCount(), d1.getLapCount());
                if (lapCompare != 0) return lapCompare;

                // Comparação por checkpoint
                int cpCompare = Integer.compare(d2.getCheckpointsReached(), d1.getCheckpointsReached());
                if (cpCompare != 0) return cpCompare;

                // Comparação por tempo (apenas se necessário)
                Long time1 = d1.getAbsoluteTimeAtProgress(d1.getLapCount(), d1.getCheckpointsReached());
                Long time2 = d2.getAbsoluteTimeAtProgress(d2.getLapCount(), d2.getCheckpointsReached());

                if (time1 != null && time2 != null) {
                    return Long.compare(time1, time2);
                }

                return Long.compare(d1.getTotalTime(), d2.getTotalTime());
            });
    }

    private void finishRaceForAll(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        HeatConfig config = heat.getHeatConfig();

        config.setRaceFinishedForAll(true);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GREEN + "🏁 CORRIDA FINALIZADA 🏁");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Todos os pilotos devem cruzar a linha de chegada!");
        Bukkit.broadcastMessage("");

        plugin.getDebugManager().logRaceSystem(
            "[TIME-BASED] Corrida finalizada para todos - Pilotos devem cruzar a linha"
        );

        // Finalizar todos os pilotos que já completaram a volta atual
        heat.getDrivers().values().forEach(driver -> {
            if (!driver.isFinished() && !driver.isDnf()) {
                driver.setFinished(true);
                driver.setEndTime(System.currentTimeMillis());
            }
        });
        heat.updateLivePositions();
        heat.finishHeat();
    }

    private void stopTimeMonitoring() {
        if (timeMonitorTask != null && !timeMonitorTask.isCancelled()) {
            timeMonitorTask.cancel();
            timeMonitorTask = null;
        }
    }

    public void cleanup() {
        stopTimeMonitoring();
        lastLapAnnounced = false;
        timeMonitorTask = null;
    }
}
