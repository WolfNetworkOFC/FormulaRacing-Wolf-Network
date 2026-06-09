package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.*;
import java.util.stream.Collectors;

public class EliminationSession implements SessionLogic {
    private int eliminationIntervalSeconds = 30;
    private int minimumDrivers = 2;
    private ScheduledTask eliminationTask;
    private ScheduledTask countdownTask;
    private int eliminationCount = 0;
    private int secondsUntilNextElimination = 0;
    private final List<UUID> eliminatedDrivers = new ArrayList<>();
    private boolean heatFinished = false;

    @Override
    public void start(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();

        heat.getGridManager().unfreezePlayers();

        this.secondsUntilNextElimination = eliminationIntervalSeconds;
        this.heatFinished = false;

        plugin.getDebugManager().logRaceSystem(
            "[ELIMINATION] Sessão de eliminação iniciada para Heat " + heat.getId()
        );

        plugin.getDebugManager().logRaceSystem(
            "[ELIMINATION] Agendando timer de eliminação (" + eliminationIntervalSeconds + "s) e countdown de titles..."
        );

        startEliminationTimer(heat);
        startCountdownTask(heat);

        plugin.getDebugManager().logRaceSystem(
            "[ELIMINATION] Timers agendados. heatState=" + heat.getHeatState() +
            " heatFinished=" + heatFinished + " drivers=" + heat.getDrivers().size()
        );
    }

    private void startEliminationTimer(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        stopEliminationTimer();

        long intervalTicks = eliminationIntervalSeconds * 20L;

        eliminationTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            heat.getPlugin().getDebugManager().logRaceSystem(
                "[ELIMINATION] Timer tick: state=" + heat.getHeatState() + " finished=" + heatFinished
            );

            if (heat.getHeatState() != HeatState.RACING || heatFinished) {
                stopEliminationTimer();
                return;
            }

            eliminationCount++;

            List<Driver> activeDrivers = getActiveDrivers(heat);
            int remaining = activeDrivers.size();

            plugin.getDebugManager().logRaceSystem(
                "[ELIMINATION] Ciclo #" + eliminationCount + " - Pilotos restantes: " + remaining
            );

            // Se só sobrou 1, finaliza o heat
            if (remaining <= 1) {
                plugin.getDebugManager().logRaceSystem(
                    "[ELIMINATION] Apenas 1 piloto restante! Finalizando heat..."
                );
                finishHeatWithWinner(heat, activeDrivers);
                return;
            }

            // Se chegou no mínimo, também finaliza
            if (remaining <= minimumDrivers) {
                plugin.getDebugManager().logRaceSystem(
                    "[ELIMINATION] Mínimo de pilotos atingido (" + minimumDrivers + "), finalizando heat..."
                );
                finishHeatWithWinner(heat, activeDrivers);
                return;
            }

            // Eliminar o último piloto
            eliminateLastDriver(heat, activeDrivers);

            // Resetar countdown
            secondsUntilNextElimination = eliminationIntervalSeconds;

        }, intervalTicks, intervalTicks);

        plugin.getDebugManager().logRaceSystem(
            "[ELIMINATION] Timer de eliminação iniciado - Intervalo: " + eliminationIntervalSeconds + "s"
        );
    }

    /**
     * Task que roda a cada segundo para atualizar o countdown e enviar titles.
     */
    private void startCountdownTask(Heats heat) {
        stopCountdownTask();

        countdownTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING || heatFinished) {
                heat.getPlugin().getDebugManager().logRaceSystem(
                    "[ELIMINATION] Countdown task parando: state=" + heat.getHeatState() + " finished=" + heatFinished
                );
                stopCountdownTask();
                return;
            }

            if (secondsUntilNextElimination > 0) {
                secondsUntilNextElimination--;
            }

            // Enviar titles para todos os pilotos ativos
            sendStatusTitles(heat);

        }, 20L, 20L); // A cada 1 segundo

        heat.getPlugin().getDebugManager().logRaceSystem(
            "[ELIMINATION] Countdown task agendada (20 ticks delay, 20 ticks period)"
        );
    }

    /**
     * Envia titles mostrando o timer e se o piloto está seguro ou em perigo.
     * Pilotos nas últimas posições (que serão eliminados) recebem "EM PERIGO".
     * Pilotos nas primeiras posições recebem "SEGURO".
     */
    private void sendStatusTitles(Heats heat) {
        List<Driver> activeDrivers = getActiveDrivers(heat);
        int totalActive = activeDrivers.size();

        heat.getPlugin().getDebugManager().logRaceSystem(
            "[ELIMINATION] sendStatusTitles: " + totalActive + " drivers ativos"
        );

        if (totalActive == 0) return;

        // Calcular quantos serão eliminados no próximo ciclo
        // (1 por ciclo, mas os que estão nas últimas posições estão em perigo)
        int dangerZone = Math.min(3, totalActive - minimumDrivers + 1);
        if (dangerZone < 1) dangerZone = 1;

        // Ordenar por posição (1º primeiro)
        List<Driver> sorted = activeDrivers.stream()
            .sorted(Comparator.comparingInt(Driver::getPosition))
            .collect(Collectors.toList());

        int timer = secondsUntilNextElimination;
        String timerStr = formatTimer(timer);

        for (int i = 0; i < sorted.size(); i++) {
            Driver driver = sorted.get(i);
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player == null || !player.isOnline()) continue;

            // Posição na lista ordenada (0 = primeiro)
            boolean isInDanger = (totalActive - i) <= dangerZone;

            if (isInDanger) {
                // EM PERIGO — últimas posições
                String title = ChatColor.RED + "⚠ EM PERIGO!";
                String subtitle = ChatColor.GRAY + "Eliminação em " + ChatColor.RED + timerStr;
                TitleHelper.sendThemedTitle(player, title, subtitle, 0, 25, 5);

                // Som de alerta nos últimos 5 segundos
                if (timer <= 5 && timer > 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                }
            } else {
                // SEGURO — primeiras posições
                String title = ChatColor.GREEN + "✓ SEGURO";
                String subtitle = ChatColor.GRAY + "Próxima eliminação: " + ChatColor.YELLOW + timerStr;
                TitleHelper.sendThemedTitle(player, title, subtitle, 0, 25, 5);
            }
        }
    }

    private String formatTimer(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        if (min > 0) {
            return min + "m " + sec + "s";
        }
        return sec + "s";
    }

    private void finishHeatWithWinner(Heats heat, List<Driver> activeDrivers) {
        heatFinished = true;
        stopEliminationTimer();
        stopCountdownTask();

        // Anunciar vencedor
        if (!activeDrivers.isEmpty()) {
            Driver winner = activeDrivers.get(0);
            // Ordenar por posição para garantir que o vencedor é o 1º
            activeDrivers.sort(Comparator.comparingInt(Driver::getPosition));
            winner = activeDrivers.get(0);

            String winnerName = "Unknown";
            Player winnerPlayer = Bukkit.getPlayer(winner.getUuid());
            if (winnerPlayer != null) {
                winnerName = winnerPlayer.getName();
            }

            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 VENCEDOR DA ELIMINAÇÃO 🏆");
            Bukkit.broadcastMessage(ChatColor.YELLOW + winnerName + ChatColor.GOLD + " venceu a eliminação!");
            Bukkit.broadcastMessage("");

            // Title de vitória para o vencedor
            if (winnerPlayer != null && winnerPlayer.isOnline()) {
                TitleHelper.sendThemedTitle(winnerPlayer,
                    ChatColor.GOLD + "🏆 VITÓRIA!",
                    ChatColor.YELLOW + "Você venceu a eliminação!",
                    10, 100, 20);
                winnerPlayer.playSound(winnerPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

            // Marcar vencedor como finished
            winner.setFinished(true);
            winner.setEndTime(System.currentTimeMillis());
        }

        // Finalizar o heat
        SchedulerHelper.runTaskLater(heat.getPlugin(), () -> {
            heat.finishHeat();
        }, 60L); // 3 segundos depois do anúncio
    }

    private void eliminateLastDriver(Heats heat, List<Driver> activeDrivers) {
        FormulaRacing plugin = heat.getPlugin();

        if (activeDrivers.isEmpty()) {
            plugin.getDebugManager().logRaceSystem("[ELIMINATION] Nenhum piloto ativo para eliminar");
            return;
        }

        // Eliminar o último (pior posição)
        Driver driverToEliminate = activeDrivers.get(activeDrivers.size() - 1);
        eliminatedDrivers.add(driverToEliminate.getUuid());

        plugin.getDebugManager().logRaceSystem(
            "[ELIMINATION] Eliminando piloto " + driverToEliminate.getUuid() +
            " (Posição: " + driverToEliminate.getPosition() + ")"
        );

        // Marcar como DNF
        heat.handleDriverDNF(driverToEliminate, "Eliminated");

        // Anunciar eliminação
        announceElimination(heat, driverToEliminate, activeDrivers.size() - 1);
    }

    private void announceElimination(Heats heat, Driver eliminatedDriver, int remainingCount) {
        FormulaRacing plugin = heat.getPlugin();
        String driverName = "Unknown";

        Player player = Bukkit.getPlayer(eliminatedDriver.getUuid());
        if (player != null) {
            driverName = player.getName();
        }

        // Broadcast global
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.RED + "⚠ ELIMINAÇÃO ⚠");
        Bukkit.broadcastMessage(ChatColor.YELLOW + driverName + ChatColor.RED + " foi eliminado!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Pilotos restantes: " + ChatColor.GREEN + remainingCount);
        Bukkit.broadcastMessage("");

        // Title para o eliminado
        if (player != null && player.isOnline()) {
            TitleHelper.sendThemedTitle(player,
                ChatColor.RED + "✗ ELIMINADO!",
                ChatColor.GRAY + "Posição final: #" + eliminatedDriver.getPosition(),
                10, 80, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 0.5f);
        }

        // Titles de atualização para os restantes
        for (Driver d : heat.getDrivers().values()) {
            if (d.isDnf() || d.isFinished()) continue;
            if (d.getUuid().equals(eliminatedDriver.getUuid())) continue;

            Player p = Bukkit.getPlayer(d.getUuid());
            if (p == null || !p.isOnline()) continue;

            TitleHelper.sendThemedTitle(p,
                ChatColor.YELLOW + "⚠ " + driverName + " eliminado!",
                ChatColor.GRAY + String.valueOf(remainingCount) + " pilotos restantes",
                5, 60, 10);
        }
    }

    private List<Driver> getActiveDrivers(Heats heat) {
        return heat.getDrivers().values().stream()
            .filter(d -> !d.isFinished() && !d.isDnf())
            .sorted(Comparator.comparingInt(Driver::getPosition))
            .collect(Collectors.toList());
    }

    private void stopEliminationTimer() {
        if (eliminationTask != null && !eliminationTask.isCancelled()) {
            eliminationTask.cancel();
            eliminationTask = null;
        }
    }

    private void stopCountdownTask() {
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    public boolean passLap(Heats heat, Driver driver) {
        return true;
    }

    public void setEliminationInterval(int seconds) {
        this.eliminationIntervalSeconds = seconds;
    }

    public void setMinimumDrivers(int minimum) {
        this.minimumDrivers = minimum;
    }

    public int getEliminationInterval() {
        return eliminationIntervalSeconds;
    }

    public int getMinimumDrivers() {
        return minimumDrivers;
    }

    public List<UUID> getEliminatedDrivers() {
        return new ArrayList<>(eliminatedDrivers);
    }

    public int getEliminationCount() {
        return eliminationCount;
    }

    public void cleanup() {
        stopEliminationTimer();
        stopCountdownTask();
        eliminatedDrivers.clear();
        eliminationCount = 0;
        secondsUntilNextElimination = 0;
        heatFinished = false;
        eliminationIntervalSeconds = 30;
        minimumDrivers = 2;
    }
}
