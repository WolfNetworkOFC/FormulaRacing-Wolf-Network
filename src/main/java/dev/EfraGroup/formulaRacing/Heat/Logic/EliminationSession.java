package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.Event.Driver.DriverNewLapEvent;
import dev.EfraGroup.formulaRacing.Event.Events;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import dev.EfraGroup.formulaRacing.Utils.FRTask;

public class EliminationSession implements SessionLogic {
    private int eliminationIntervalSeconds = 30;
    private int minimumDrivers = 2;
    private FRTask eliminationTask;
    private FRTask countdownTask;
    private int eliminationCount = 0;
    private int secondsUntilNextElimination = 0;
    private final List<UUID> eliminatedDrivers = new ArrayList<>();
    private boolean heatFinished = false;

    @Override
    public void start(Heats heat) {
        FormulaRacing plugin = heat.getPlugin();

        // Read heat-level config (overrides round defaults)
        this.eliminationIntervalSeconds = heat.getEliminationIntervalSeconds();
        this.minimumDrivers = heat.getMinimumDrivers();

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
            if (driver.getCurrentLap() == null) {
                driver.newLap();
            }
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


        this.secondsUntilNextElimination = eliminationIntervalSeconds;
        this.heatFinished = false;

            plugin.getDebugManager().logRaceSystem(
                "[ELIMINATION] Elimination session started for Heat " + heat.getId()
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
                "[ELIMINATION] Cycle #" + eliminationCount + " - Remaining drivers: " + remaining
            );

            if (remaining <= 1) {
                plugin.getDebugManager().logRaceSystem(
                    "[ELIMINATION] Only 1 driver remaining! Finishing heat..."
                );
                finishHeatWithWinner(heat, activeDrivers);
                return;
            }

            if (remaining <= minimumDrivers) {
                plugin.getDebugManager().logRaceSystem(
                    "[ELIMINATION] Minimum drivers reached (" + minimumDrivers + "), finishing heat..."
                );
                finishHeatWithWinner(heat, activeDrivers);
                return;
            }

            eliminateLastDriver(heat, activeDrivers);

            secondsUntilNextElimination = eliminationIntervalSeconds;

        }, intervalTicks, intervalTicks);

        plugin.getDebugManager().logRaceSystem(
            "[ELIMINATION] Elimination timer started - Interval: " + eliminationIntervalSeconds + "s"
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

            Player driverPlayer = Bukkit.getPlayer(driver.getUuid());
            if (driverPlayer == null || !driverPlayer.isOnline()) continue;

            SchedulerHelper.runTaskFor(heat.getPlugin(), driverPlayer, (entity) -> {
                Player player = (Player) entity;

                if (isInDanger) {
                    String title = ChatColor.RED + "⚠ IN DANGER!";
                    String subtitle = ChatColor.GRAY + "Elimination in " + ChatColor.RED + timerStr;
                    TitleHelper.sendThemedTitle(player, title, subtitle, 0, 25, 5);

                    if (timer <= 5 && timer > 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    }
                } else {
                    String title = ChatColor.GREEN + "✓ SAFE";
                    String subtitle = ChatColor.GRAY + "Next elimination: " + ChatColor.YELLOW + timerStr;
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
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 ELIMINATION WINNER 🏆");
            Bukkit.broadcastMessage(ChatColor.YELLOW + winnerName + ChatColor.GOLD + " won the elimination!");
            Bukkit.broadcastMessage("");

            if (winnerPlayer != null && winnerPlayer.isOnline()) {
                SchedulerHelper.runTaskFor(heat.getPlugin(), winnerPlayer, (entity) -> {
                    Player p = (Player) entity;
                    TitleHelper.sendThemedTitle(p,
                        ChatColor.GOLD + "🏆 VICTORY!",
                        ChatColor.YELLOW + "You won the elimination!",
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
        Events event = heat.getRound() != null ? heat.getRound().getEvent() : null;

        if (activeDrivers.isEmpty()) {
            plugin.getDebugManager().logRaceSystem("[ELIMINATION] No active drivers to eliminate");
            return;
        }

        Driver driverToEliminate = activeDrivers.get(activeDrivers.size() - 1);
        eliminatedDrivers.add(driverToEliminate.getUuid());

        Player player = Bukkit.getPlayer(driverToEliminate.getUuid());
        if (player != null && player.isOnline()) {
            // Destroy the boat
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                vehicle.remove();
                plugin.getDebugManager().logRaceSystem("[ELIMINATION] Vehicle destroyed for " + player.getName());
            }

            // Transition to spectator mode
            if (event != null) {
                plugin.getSpectatorManager().addSpectator(player, event);
                plugin.getDebugManager().logRaceSystem("[ELIMINATION] " + player.getName() + " sent to spectator mode");
            }
        }

        heat.handleDriverDNF(driverToEliminate, "Eliminated");

        announceElimination(heat, driverToEliminate, activeDrivers.size() - 1);
    }

    private void announceElimination(Heats heat, Driver eliminatedDriver, int remainingCount) {
        Player player = Bukkit.getPlayer(eliminatedDriver.getUuid());
        String driverName = (player != null) ? player.getName() : "Unknown";

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.RED + "⚠ ELIMINATION ⚠");
        Bukkit.broadcastMessage(ChatColor.YELLOW + driverName + ChatColor.RED + " was eliminated!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Remaining drivers: " + ChatColor.GREEN + remainingCount);
        Bukkit.broadcastMessage("");

        if (player != null && player.isOnline()) {
            SchedulerHelper.runTaskFor(heat.getPlugin(), player, (entity) -> {
                Player p = (Player) entity;
                TitleHelper.sendThemedTitle(p,
                    ChatColor.RED + "✗ ELIMINATED!",
                    ChatColor.GRAY + "Final position: #" + eliminatedDriver.getPosition(),
                    10, 80, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 0.5f);
            });
        }

        for (Driver d : heat.getDrivers().values()) {
            if (d.isDnf() || d.isFinished()) continue;
            if (d.getUuid().equals(eliminatedDriver.getUuid())) continue;

            Player remainingPlayer = Bukkit.getPlayer(d.getUuid());
            if (remainingPlayer == null || !remainingPlayer.isOnline()) continue;

            SchedulerHelper.runTaskFor(heat.getPlugin(), remainingPlayer, (entity) -> {
                Player rp = (Player) entity;
                TitleHelper.sendThemedTitle(rp,
                    ChatColor.YELLOW + "⚠ " + driverName + " eliminated!",
                    ChatColor.GRAY + String.valueOf(remainingCount) + " drivers remaining",
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
        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player == null) {
            return false;
        }

        if (driver.getCurrentLap() == null) {
            driver.newLap();
            heat.updateLivePositions();
            Bukkit.getPluginManager().callEvent(new DriverNewLapEvent(driver, driver.getCurrentLap()));
            return true;
        }

        int totalCheckpoints = heat.getPlugin().getTrackIntegrationManager().getCheckpointCount(heat.getTrackNameWS());
        if (!driver.hasPassedAllCheckpoints(totalCheckpoints)) {
            return false;
        }

        driver.finishLap();
        driver.newLap();
        heat.updateLivePositions();
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
