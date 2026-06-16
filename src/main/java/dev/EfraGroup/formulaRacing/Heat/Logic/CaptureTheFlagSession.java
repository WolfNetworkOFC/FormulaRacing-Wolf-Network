package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 🚩 CAPTURE THE FLAG
 * Pilotos devem coletar bandeiras espalhadas pela pista (checkpoints específicos).
 * Cada bandeira coletada vale 1 ponto. Primeiro a coletar 3 bandeiras vence.
 * Se o tempo acabar (2 minutos), quem tem mais pontos vence.
 */
public class CaptureTheFlagSession implements SessionLogic {

    private static final int WIN_SCORE = 3;
    private static final int MATCH_TIMEOUT_TICKS = 2400; // 2 minutos
    private static final int FLAG_PARTICLE_TICKS = 10;

    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Set<Integer> flagCheckpointIds = new HashSet<>();
    private final Map<UUID, Long> stealCooldowns = new HashMap<>();
    private FRTask timeoutTask;
    private FRTask particleTask;
    private boolean matchFinished = false;
    private int matchTimeSeconds = 0;

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();
        matchFinished = false;
        matchTimeSeconds = 0;

        // Inicializa scores
        for (Driver d : heat.getDrivers().values()) {
            scores.put(d.getUuid(), 0);
        }

        // Define checkpoints de bandeira (a cada 3 checkpoints, aproximadamente)
        // Usa os checkpoints do meio da pista
        int totalCheckpoints = heat.getPlugin().getDatabaseManager().getCheckpointCount(heat.getTrackNameWS());
        if (totalCheckpoints > 0) {
            flagCheckpointIds.add(Math.max(1, totalCheckpoints / 3));
            flagCheckpointIds.add(Math.max(1, totalCheckpoints * 2 / 3));
            if (totalCheckpoints > 5) {
                flagCheckpointIds.add(Math.max(1, totalCheckpoints / 2));
            }
        } else {
            // Fallback: checkpoints 1 e 2
            flagCheckpointIds.add(1);
            flagCheckpointIds.add(2);
        }

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.GREEN + "  🚩 CAPTURE THE FLAG 🚩");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  Colete bandeiras nos checkpoints!");
        broadcast(heat, ChatColor.RED + "  Primeiro a " + WIN_SCORE + " bandeiras vence!");
        broadcast(heat, ChatColor.GRAY + "  Tempo limite: 2 minutos");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");

        startTimeoutTask(heat);
        startParticleTask(heat);
    }

    private void startTimeoutTask(Heats heat) {
        // Timer que conta segundos
        SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING || matchFinished) return;
            matchTimeSeconds++;
        }, 20L, 20L);

        // Timeout final
        timeoutTask = SchedulerHelper.runTaskLater(heat.getPlugin(), () -> {
            if (matchFinished) return;
            matchFinished = true;

            broadcast(heat, ChatColor.RED + "⏰ TEMPO ESGOTADO!");

            // Encontra quem tem mais pontos
            UUID best = null;
            int bestScore = -1;
            for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
                if (entry.getValue() > bestScore) {
                    bestScore = entry.getValue();
                    best = entry.getKey();
                }
            }

            if (best != null && bestScore > 0) {
                Player winner = Bukkit.getPlayer(best);
                String winnerName = winner != null ? winner.getName() : "Unknown";
                broadcast(heat, ChatColor.GOLD + "🏆 " + winnerName + " venceu com " + bestScore + " bandeiras!");

                if (winner != null && winner.isOnline()) {
                    TitleHelper.sendThemedTitle(winner,
                        ChatColor.GOLD + "🏆 VITÓRIA!",
                        ChatColor.YELLOW + "" + bestScore + " bandeiras capturadas!",
                        10, 100, 20);
                }
            } else {
                broadcast(heat, ChatColor.GRAY + "Nenhuma bandeira foi capturada!");
            }

            SchedulerHelper.runTaskLater(heat.getPlugin(), () -> heat.finishHeat(), 60L);
        }, MATCH_TIMEOUT_TICKS);
    }

    private void startParticleTask(Heats heat) {
        particleTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING || matchFinished) return;

            // Partículas de bandeira nos checkpoints de bandeira
            for (Driver d : heat.getDrivers().values()) {
                Player p = Bukkit.getPlayer(d.getUuid());
                if (p == null || !p.isOnline()) continue;

                // Mostra partículas de glow nos checkpoints próximos
                p.getWorld().spawnParticle(Particle.GLOW,
                    p.getLocation().add(0, 2, 0), 2, 1, 1, 1, 0.02);
            }
        }, 0L, FLAG_PARTICLE_TICKS);
    }

    /**
     * Chamado quando um driver passa por um checkpoint de bandeira.
     * Retorna true se o checkpoint foi de bandeira e foi coletado.
     */
    public boolean tryCaptureFlag(Heats heat, Driver driver, int checkpointId) {
        if (matchFinished) return false;
        if (!flagCheckpointIds.contains(checkpointId)) return false;

        UUID uuid = driver.getUuid();

        // Cooldown de 5 segundos entre capturas
        long now = System.currentTimeMillis();
        if (stealCooldowns.containsKey(uuid) && now - stealCooldowns.get(uuid) < 5000) {
            return false;
        }

        // Cooldown por checkpoint (não pode coletar o mesmo checkpoint repetidamente)
        stealCooldowns.put(uuid, now);

        int newScore = scores.getOrDefault(uuid, 0) + 1;
        scores.put(uuid, newScore);

        Player player = Bukkit.getPlayer(uuid);
        String playerName = player != null ? player.getName() : "Unknown";

        // Anuncia captura
        broadcast(heat, ChatColor.GREEN + "🚩 " + playerName + " capturou uma bandeira! (" + newScore + "/" + WIN_SCORE + ")");

        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.getWorld().spawnParticle(Particle.FIREWORK,
                player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

            // Efeito de brilho por ter bandeira
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));

            TitleHelper.sendThemedTitle(player,
                ChatColor.GREEN + "🚩 BANDEIRA!",
                ChatColor.YELLOW + "" + newScore + "/" + WIN_SCORE + " capturadas",
                5, 40, 10);
        }

        // Verifica vitória
        if (newScore >= WIN_SCORE) {
            finishMatch(heat, uuid);
        }

        return true;
    }

    private void finishMatch(Heats heat, UUID winnerUuid) {
        matchFinished = true;
        stopTasks();

        Player winner = Bukkit.getPlayer(winnerUuid);
        String winnerName = winner != null ? winner.getName() : "Unknown";

        broadcast(heat, "");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.GREEN + "  🏆 " + winnerName + " CAPTUREU " + WIN_SCORE + " BANDEIRAS! 🏆");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, "");

        if (winner != null && winner.isOnline()) {
            TitleHelper.sendThemedTitle(winner,
                ChatColor.GOLD + "🏆 VITÓRIA!",
                ChatColor.YELLOW + "" + WIN_SCORE + " bandeiras capturadas!",
                10, 100, 20);
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Marca todos como finished exceto o vencedor
        for (Driver d : heat.getDrivers().values()) {
            if (!d.getUuid().equals(winnerUuid)) {
                d.setFinished(true);
                d.setEndTime(System.currentTimeMillis());
            }
        }

        SchedulerHelper.runTaskLater(heat.getPlugin(), () -> heat.finishHeat(), 60L);
    }

    private void broadcast(Heats heat, String message) {
        for (Driver driver : heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    private void stopTasks() {
        if (timeoutTask != null && !timeoutTask.isCancelled()) timeoutTask.cancel();
        if (particleTask != null && !particleTask.isCancelled()) particleTask.cancel();
        timeoutTask = null;
        particleTask = null;
    }

    @Override
    public boolean passLap(Heats heat, Driver driver) {
        return true;
    }

    public void cleanup() {
        stopTasks();
        scores.clear();
        flagCheckpointIds.clear();
        stealCooldowns.clear();
        matchFinished = false;
        matchTimeSeconds = 0;
    }

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    public Map<UUID, Integer> getAllScores() {
        return new HashMap<>(scores);
    }

    public boolean isMatchFinished() {
        return matchFinished;
    }
}
