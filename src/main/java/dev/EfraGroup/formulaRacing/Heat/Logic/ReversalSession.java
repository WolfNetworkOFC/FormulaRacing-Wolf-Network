package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🔄 REVERSAL
 * Every 20 seconds, positions are reversed — whoever was 1st goes to last
 * and vice versa. Points are awarded based on position at each reversal.
 * Whoever has the most points at the end wins.
 */
public class ReversalSession implements SessionLogic {

    private static final int REVERSAL_INTERVAL_TICKS = 400; // 20 segundos
    private static final int TOTAL_REVERSALS = 6; // 6 reversals = 2 minutes

    private FRTask reversalTask;
    private int reversalCount = 0;
    private boolean matchFinished = false;
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Integer> lastKnownPosition = new HashMap<>();

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);
        matchFinished = false;
        reversalCount = 0;

        // Initialize points
        for (Driver d : heat.getDrivers().values()) {
            points.put(d.getUuid(), 0);
            lastKnownPosition.put(d.getUuid(), d.getPosition());
        }

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.AQUA + "  🔄 REVERSAL MODE 🔄");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  Every 20 seconds, the standings invert!");
        broadcast(heat, ChatColor.RED + "  1st place ↔ Last place");
        broadcast(heat, ChatColor.GREEN + "  Most points at the end wins!");
        broadcast(heat, ChatColor.GRAY + "  " + TOTAL_REVERSALS + " reversals total");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");

        startReversalTask(heat);
    }

    private void startReversalTask(Heats heat) {
        reversalTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING || matchFinished) {
                cleanup();
                return;
            }

            reversalCount++;

            // Get active drivers sorted by position
            List<Driver> activeDrivers = heat.getDrivers().values().stream()
                .filter(d -> !d.isFinished() && !d.isDnf())
                .sorted(Comparator.comparingInt(Driver::getPosition))
                .collect(Collectors.toList());

            int totalActive = activeDrivers.size();
            if (totalActive == 0) return;

            // Reverse positions: 1st becomes last, last becomes 1st
            List<Driver> reversed = new ArrayList<>(activeDrivers);
            Collections.reverse(reversed);

            // Assign new positions
            for (int i = 0; i < reversed.size(); i++) {
                Driver driver = reversed.get(i);
                int newPosition = i + 1;
                driver.setPosition(newPosition);
                lastKnownPosition.put(driver.getUuid(), newPosition);
            }

            // Award points based on the NEW position (1st place = most points)
            int maxPoints = totalActive;
            for (int i = 0; i < reversed.size(); i++) {
                Driver driver = reversed.get(i);
                int pts = maxPoints - i; // 1st = totalActive pts, last = 1 pt
                points.merge(driver.getUuid(), pts, Integer::sum);
            }

            // Announce reversal
            broadcast(heat, "");
            broadcast(heat, ChatColor.AQUA + "═══════════════════════════════");
            broadcast(heat, ChatColor.RED + "  🔄 REVERSAL #" + reversalCount + " 🔄");
            broadcast(heat, ChatColor.YELLOW + "  Positions reversed!");
            broadcast(heat, ChatColor.AQUA + "═══════════════════════════════");

            // Show new standings
            for (int i = 0; i < reversed.size(); i++) {
                Driver driver = reversed.get(i);
                Player p = Bukkit.getPlayer(driver.getUuid());
                if (p == null || !p.isOnline()) continue;

                int pos = i + 1;
                int pts = maxPoints - i;
                int totalPts = points.getOrDefault(driver.getUuid(), 0);

                ChatColor posColor = pos == 1 ? ChatColor.GOLD :
                    pos <= 3 ? ChatColor.YELLOW : ChatColor.GRAY;

                p.sendMessage(posColor + "  #" + pos + " " + p.getName() +
                    ChatColor.GRAY + " (+" + pts + " pts = " + totalPts + " total)");

                // Title for the new leader
                if (pos == 1) {
                    TitleHelper.sendThemedTitle(p,
                        ChatColor.GOLD + "👑 LEADER!",
                        ChatColor.YELLOW + "+" + pts + " pontos | Total: " + totalPts,
                        5, 60, 10);
                    p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.5f);
                } else {
                    TitleHelper.sendThemedTitle(p,
                        ChatColor.AQUA + "🔄 REVERSAL!",
                        posColor + "#" + pos + ChatColor.GRAY + " | +" + pts + " pts",
                        5, 40, 5);
                }
            }
            broadcast(heat, "");

            // Update scoreboard
            heat.updateLivePositions();

            // Check if reversals are done
            if (reversalCount >= TOTAL_REVERSALS) {
                finishMatch(heat);
            }

        }, REVERSAL_INTERVAL_TICKS, REVERSAL_INTERVAL_TICKS);
    }

    private void finishMatch(Heats heat) {
        matchFinished = true;
        cleanup();

        // Find winner by points
        UUID winner = null;
        int bestPoints = -1;
        List<Map.Entry<UUID, Integer>> sorted = points.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .collect(Collectors.toList());

        if (!sorted.isEmpty()) {
            winner = sorted.get(0).getKey();
            bestPoints = sorted.get(0).getValue();
        }

        broadcast(heat, "");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.AQUA + "  🏆 FINAL RESULT — REVERSAL 🏆");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");

        // Show final ranking
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            Player p = Bukkit.getPlayer(entry.getKey());
            String name = p != null ? p.getName() : "Unknown";
            int pts = entry.getValue();

            ChatColor color = rank == 1 ? ChatColor.GOLD :
                rank == 2 ? ChatColor.GRAY :
                rank == 3 ? ChatColor.DARK_RED : ChatColor.WHITE;

            broadcast(heat, color + "  #" + rank + " " + name + " — " + pts + " points");

            rank++;
        }

        broadcast(heat, "");

        if (winner != null) {
            Player winnerPlayer = Bukkit.getPlayer(winner);
            String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";

            broadcast(heat, ChatColor.GOLD + "🏆 " + winnerName + " won with " + bestPoints + " points!");

            if (winnerPlayer != null && winnerPlayer.isOnline()) {
                TitleHelper.sendThemedTitle(winnerPlayer,
                    ChatColor.GOLD + "🏆 VICTORY!",
                    ChatColor.YELLOW + "" + bestPoints + " total points!",
                    10, 100, 20);
                winnerPlayer.playSound(winnerPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }

        SchedulerHelper.runTaskLater(heat.getPlugin(), () -> heat.finishHeat(), 80L);
    }

    private void broadcast(Heats heat, String message) {
        for (Driver driver : heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    @Override
    public boolean passLap(Heats heat, Driver driver) {
        return true;
    }

    public void cleanup() {
        if (reversalTask != null && !reversalTask.isCancelled()) reversalTask.cancel();
        reversalTask = null;
        reversalCount = 0;
        matchFinished = false;
        points.clear();
        lastKnownPosition.clear();
    }

    public int getPoints(UUID uuid) {
        return points.getOrDefault(uuid, 0);
    }

    public int getReversalCount() {
        return reversalCount;
    }

    public boolean isMatchFinished() {
        return matchFinished;
    }
}
