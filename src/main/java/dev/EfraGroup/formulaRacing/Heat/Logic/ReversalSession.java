package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🔄 REVERSÃO
 * A cada 20 segundos, as posições são invertidas — quem estava em 1º vai para último
 * e vice-versa. Pontos são atribuídos baseado na posição a cada inversão.
 * Quem tiver mais pontos ao final vence.
 */
public class ReversalSession implements SessionLogic {

    private static final int REVERSAL_INTERVAL_TICKS = 400; // 20 segundos
    private static final int TOTAL_REVERSALS = 6; // 6 reversões = 2 minutos

    private FRTask reversalTask;
    private int reversalCount = 0;
    private boolean matchFinished = false;
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Integer> lastKnownPosition = new HashMap<>();

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();
        matchFinished = false;
        reversalCount = 0;

        // Inicializa pontos
        for (Driver d : heat.getDrivers().values()) {
            points.put(d.getUuid(), 0);
            lastKnownPosition.put(d.getUuid(), d.getPosition());
        }

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.AQUA + "  🔄 MODO REVERSÃO 🔄");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  A cada 20 segundos, a classificação inverte!");
        broadcast(heat, ChatColor.RED + "  1º lugar ↔ Último lugar");
        broadcast(heat, ChatColor.GREEN + "  Mais pontos ao final vence!");
        broadcast(heat, ChatColor.GRAY + "  " + TOTAL_REVERSALS + " reversões no total");
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

            // Obtém pilotos ativos ordenados por posição
            List<Driver> activeDrivers = heat.getDrivers().values().stream()
                .filter(d -> !d.isFinished() && !d.isDnf())
                .sorted(Comparator.comparingInt(Driver::getPosition))
                .collect(Collectors.toList());

            int totalActive = activeDrivers.size();
            if (totalActive == 0) return;

            // Inverte as posições: 1º vira último, último vira 1º
            List<Driver> reversed = new ArrayList<>(activeDrivers);
            Collections.reverse(reversed);

            // Atribui novas posições
            for (int i = 0; i < reversed.size(); i++) {
                Driver driver = reversed.get(i);
                int newPosition = i + 1;
                driver.setPosition(newPosition);
                lastKnownPosition.put(driver.getUuid(), newPosition);
            }

            // Atribui pontos baseado na NOVA posição (1º lugar = mais pontos)
            int maxPoints = totalActive;
            for (int i = 0; i < reversed.size(); i++) {
                Driver driver = reversed.get(i);
                int pts = maxPoints - i; // 1º = totalActive pts, último = 1 pt
                points.merge(driver.getUuid(), pts, Integer::sum);
            }

            // Anuncia reversão
            broadcast(heat, "");
            broadcast(heat, ChatColor.AQUA + "═══════════════════════════════");
            broadcast(heat, ChatColor.RED + "  🔄 REVERSÃO #" + reversalCount + " 🔄");
            broadcast(heat, ChatColor.YELLOW + "  Posições invertidas!");
            broadcast(heat, ChatColor.AQUA + "═══════════════════════════════");

            // Mostra nova classificação
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

                // Title para o novo líder
                if (pos == 1) {
                    TitleHelper.sendThemedTitle(p,
                        ChatColor.GOLD + "👑 LÍDER!",
                        ChatColor.YELLOW + "+" + pts + " pontos | Total: " + totalPts,
                        5, 60, 10);
                    p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.5f);
                } else {
                    TitleHelper.sendThemedTitle(p,
                        ChatColor.AQUA + "🔄 REVERSÃO!",
                        posColor + "#" + pos + ChatColor.GRAY + " | +" + pts + " pts",
                        5, 40, 5);
                }
            }
            broadcast(heat, "");

            // Atualiza scoreboard
            heat.updateLivePositions();

            // Verifica se acabaram as reversões
            if (reversalCount >= TOTAL_REVERSALS) {
                finishMatch(heat);
            }

        }, REVERSAL_INTERVAL_TICKS, REVERSAL_INTERVAL_TICKS);
    }

    private void finishMatch(Heats heat) {
        matchFinished = true;
        cleanup();

        // Encontra vencedor por pontos
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
        broadcast(heat, ChatColor.AQUA + "  🏆 RESULTADO FINAL — REVERSÃO 🏆");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");

        // Mostra ranking final
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            Player p = Bukkit.getPlayer(entry.getKey());
            String name = p != null ? p.getName() : "Unknown";
            int pts = entry.getValue();

            ChatColor color = rank == 1 ? ChatColor.GOLD :
                rank == 2 ? ChatColor.GRAY :
                rank == 3 ? ChatColor.DARK_RED : ChatColor.WHITE;

            broadcast(heat, color + "  #" + rank + " " + name + " — " + pts + " pontos");

            rank++;
        }

        broadcast(heat, "");

        if (winner != null) {
            Player winnerPlayer = Bukkit.getPlayer(winner);
            String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";

            broadcast(heat, ChatColor.GOLD + "🏆 " + winnerName + " venceu com " + bestPoints + " pontos!");

            if (winnerPlayer != null && winnerPlayer.isOnline()) {
                TitleHelper.sendThemedTitle(winnerPlayer,
                    ChatColor.GOLD + "🏆 VITÓRIA!",
                    ChatColor.YELLOW + "" + bestPoints + " pontos totais!",
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
