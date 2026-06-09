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

import java.time.Instant;
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

        if (heat.isDrsEnabled()) {
            heat.setupDrs();
            plugin.getDRS().startDrsTask(heat);
        }
        if (heat.isPushtopass()) {
            plugin.getPTP().startPTPTask(heat);
        }
        if (heat.isErsEnabled()) {
            plugin.getERS().startERSTask(heat);
        }

        heat.setHeatState(HeatState.RACING);
        heat.setStartTime(Instant.now());
        long now = System.currentTimeMillis();

        for (Driver driver : heat.getDrivers().values()) {
            driver.setStartTime(now);
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                heat.clearTimeTrialActionBar(player);
                heat.stopTimeTrialTimer(player);
                if (plugin.getPacketSender() != null) {
                    plugin.getLonelyController().updatePlayersVisibility(player);
                    plugin.getPacketSender().applyBoatUtilsToPlayer(player, heat.getTrackNameWS());
                    heat.applyCollisionModeToPlayer(player);
                }
                plugin.getRaceActionBarManager().removePlayer(player);
                plugin.getRaceActionBarManager().addPlayer(player, heat);
            }
        }

        heat.getGridManager().unfreezePlayers();

        if (heat.getRound() != null && heat.getRound().getEvent() != null) {
            plugin.getRaceEventManager().getDatabaseManager().updateHeatTimes(heat.getId(), heat.getStartTime(), null);
        }

        heat.startOfflineMonitoring();

        this.secondsUntilNextElimination = eliminationIntervalSeconds;
        this.heatFinished = false;

        plugin.getDebugManager().logRaceSystem(
            "[ELIMINATION] Sessão de eliminação iniciada para Heat " + heat.getId()
        );

        startEliminationTimer(heat);
        startCountdownTask(heat);
    }

    private void startEliminationTimer(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();
        stopEliminationTimer();

        long intervalTicks = eliminationIntervalSeconds * 20L;

        eliminationTask = SchedulerHelper.runTaskTimer(plugin, () -> {
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

            if (remaining <= 1) {
                plugin.getDebugManager().logRaceSystem(
                    "[ELIMINATION] Apenas 1 piloto restante! Finalizando heat..."
                );
                finishHeatWithWinner(heat, activeDrivers);
                return;
            }

            if (remaining <= minimumDrivers) {
                plugin.getDebugManager().logRaceSystem(
                    "[ELIMINATION] Mínimo de pilotos atingido (" + minimumDrivers + "), finalizando heat..."
                );
                finishHeatWithWinner(heat, activeDrivers);
                return;
            }

            eliminateLastDriver(heat, activeDrivers);

            secondsUntilNextElimination = eliminationIntervalSeconds;

        }, intervalTicks, intervalTicks);

        plugin.getDebugManager().logRaceSystem(
            "[ELIMINATION] Timer de eliminação iniciado - Intervalo: " + eliminationIntervalSeconds + "s"
        );
    }

    private void startCountdownTask(Heats heat) {
        stopCountdownTask();

        countdownTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING || heatFinished) {
                stopCountdownTask();
                return;
            }

            if (secondsUntilNextElimination > 0) {
                secondsUntilNextElimination--;
            }

            sendStatusTitles(heat);

        }, 20L, 20L);
    }

    private void sendStatusTitles(Heats heat) {
        List<Driver> activeDrivers = getActiveDrivers(heat);
        int totalActive = activeDrivers.size();

        if (totalActive == 0) return;

        int dangerZone = Math.min(3, totalActive - minimumDrivers + 1);
        if (dangerZone < 1) dangerZone = 1;

        List<Driver> sorted = activeDrivers.stream()
            .sorted(Comparator.comparingInt(Driver::getPosition))
            .collect(Collectors.toList());

        int timer = secondsUntilNextElimination;
        String timerStr = formatTimer(timer);

        for (int i = 0; i < sorted.size(); i++) {
            Driver driver = sorted.get(i);
            boolean isInDanger = (totalActive - i) <= dangerZone;

            SchedulerHelper.runTaskFor(heat.getPlugin(), Bukkit.getPlayer(driver.getUuid()), (player) -> {
                if (player == null || !player.isOnline()) return;

                if (isInDanger) {
                    String title = ChatColor.RED + "⚠ EM PERIGO!";
                    String subtitle = ChatColor.GRAY + "Eliminação em " + ChatColor.RED + timerStr;
                    TitleHelper.sendThemedTitle(player, title, subtitle, 0, 25, 5);

                    if (timer <= 5 && timer > 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    }
                } else {
                    String title = ChatColor.GREEN + "✓ SEGURO";
                    String subtitle = ChatColor.GRAY + "Próxima eliminação: " + ChatColor.YELLOW + timerStr;
                    TitleHelper.sendThemedTitle(player, title, subtitle, 0, 25, 5);
                }
            });
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

        if (!activeDrivers.isEmpty()) {
            Driver winner = activeDrivers.get(0);
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

            if (winnerPlayer != null && winnerPlayer.isOnline()) {
                SchedulerHelper.runTaskFor(heat.getPlugin(), winnerPlayer, (p) -> {
                    TitleHelper.sendThemedTitle(p,
                        ChatColor.GOLD + "🏆 VITÓRIA!",
                        ChatColor.YELLOW + "Você venceu a eliminação!",
                        10, 100, 20);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                });
            }

            winner.setFinished(true);
            winner.setEndTime(System.currentTimeMillis());
        }

        SchedulerHelper.runTaskLater(heat.getPlugin(), () -> {
            heat.finishHeat();
        }, 60L);
    }

    private void eliminateLastDriver(Heats heat, List<Driver> activeDrivers) {
        FormulaRacing plugin = heat.getPlugin();

        if (activeDrivers.isEmpty()) {
            plugin.getDebugManager().logRaceSystem("[ELIMINATION] Nenhum piloto ativo para eliminar");
            return;
        }

        Driver driverToEliminate = activeDrivers.get(activeDrivers.size() - 1);
        eliminatedDrivers.add(driverToEliminate.getUuid());

        heat.handleDriverDNF(driverToEliminate, "Eliminated");

        announceElimination(heat, driverToEliminate, activeDrivers.size() - 1);
    }

    private void announceElimination(Heats heat, Driver eliminatedDriver, int remainingCount) {
        String driverName = "Unknown";

        Player player = Bukkit.getPlayer(eliminatedDriver.getUuid());
        if (player != null) {
            driverName = player.getName();
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.RED + "⚠ ELIMINAÇÃO ⚠");
        Bukkit.broadcastMessage(ChatColor.YELLOW + driverName + ChatColor.RED + " foi eliminado!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Pilotos restantes: " + ChatColor.GREEN + remainingCount);
        Bukkit.broadcastMessage("");

        if (player != null && player.isOnline()) {
            SchedulerHelper.runTaskFor(heat.getPlugin(), player, (p) -> {
                TitleHelper.sendThemedTitle(p,
                    ChatColor.RED + "✗ ELIMINADO!",
                    ChatColor.GRAY + "Posição final: #" + eliminatedDriver.getPosition(),
                    10, 80, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 0.5f);
            });
        }

        for (Driver d : heat.getDrivers().values()) {
            if (d.isDnf() || d.isFinished()) continue;
            if (d.getUuid().equals(eliminatedDriver.getUuid())) continue;

            Player p = Bukkit.getPlayer(d.getUuid());
            if (p == null || !p.isOnline()) continue;

            SchedulerHelper.runTaskFor(heat.getPlugin(), p, (remainingPlayer) -> {
                TitleHelper.sendThemedTitle(remainingPlayer,
                    ChatColor.YELLOW + "⚠ " + driverName + " eliminado!",
                    ChatColor.GRAY + String.valueOf(remainingCount) + " pilotos restantes",
                    5, 60, 10);
            });
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
