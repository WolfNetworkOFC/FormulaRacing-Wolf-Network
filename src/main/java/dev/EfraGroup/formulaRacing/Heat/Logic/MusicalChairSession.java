package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.*;

/**
 * 🪑 CADEIRAS MUSICAIS
 * A cada rodada, o número de "cadeiras" (safe zones) diminui.
 * Quando a música para, quem não estiver em uma safe zone é eliminado.
 * Safe zones são áreas brilhantes que aparecem na pista.
 * O último sobrevivente vence.
 */
public class MusicalChairSession implements SessionLogic {

    private static final int ROUND_INTERVAL_TICKS = 600; // 30 segundos
    private static final int MUSIC_STOP_WARNING_TICKS = 100; // 5 segundos antes

    private ScheduledTask roundTask;
    private ScheduledTask musicTask;
    private int round = 0;
    private int safeZones = 0;
    private boolean musicPlaying = true;
    private final Set<UUID> safePlayers = new HashSet<>();
    private final Random random = new Random();

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();

        int totalDrivers = heat.getDrivers().size();
        safeZones = Math.max(1, totalDrivers - 1);

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.LIGHT_PURPLE + "  🪑 MODO CADEIRAS MUSICAIS 🪑");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  A música vai tocar e parar!");
        broadcast(heat, ChatColor.RED + "  Quando parar, entre em uma ZONA SEGURA!");
        broadcast(heat, ChatColor.GRAY + "  Zonas seguras: " + safeZones);
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");

        startRoundTask(heat);
    }

    private void startRoundTask(Heats heat) {
        roundTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                cleanup();
                return;
            }

            round++;

            // Fase 1: Música tocando — jogadores correm pela pista
            musicPlaying = true;
            broadcast(heat, ChatColor.LIGHT_PURPLE + "🎵 Música tocando! Corram pela pista!");

            // Efeito de velocidade para todos durante a música
            for (Driver driver : heat.getDrivers().values()) {
                if (driver.isFinished() || driver.isDnf()) continue;
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ROUND_INTERVAL_TICKS, 1, false, false));
                    player.playSound(player.getLocation(), Sound.MUSIC_DISC_CAT, 1.0f, 1.0f);
                }
            }

            // Fase 2: Música para — zonas seguras aparecem
            SchedulerHelper.runTaskLater(heat.getPlugin(), () -> {
                if (heat.getHeatState() != HeatState.RACING) return;

                musicPlaying = false;

                // Calcula zonas seguras (diminui a cada rodada)
                int activeDrivers = (int) heat.getDrivers().values().stream()
                        .filter(d -> !d.isFinished() && !d.isDnf())
                        .count();
                safeZones = Math.max(1, activeDrivers - round);
                if (safeZones < 1) safeZones = 1;

                broadcast(heat, ChatColor.RED + "🛑 MÚSICA PAROU! Corram para as zonas seguras!");
                broadcast(heat, ChatColor.YELLOW + "Zonas seguras: " + safeZones + " | Vocês têm 5 segundos!");

                // Marca jogadores em safe zones (baseado na posição — zonas são áreas do mapa)
                markSafePlayers(heat);

                // Som de alerta
                for (Driver driver : heat.getDrivers().values()) {
                    if (driver.isFinished() || driver.isDnf()) continue;
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f, 0.5f);
                    }
                }

                // Após 5 segundos, elimina quem não está em zona segura
                SchedulerHelper.runTaskLater(heat.getPlugin(), () -> {
                    if (heat.getHeatState() != HeatState.RACING) return;
                    eliminateUnsafePlayers(heat);
                }, 100L); // 5 segundos

            }, ROUND_INTERVAL_TICKS - 100); // 25 segundos de música, 5 de reação

        }, 40L, ROUND_INTERVAL_TICKS + 140); // Intervalo entre rodadas
    }

    private void markSafePlayers(Heats heat) {
        safePlayers.clear();

        // Safe zones são baseadas na posição — os N primeiros pilotos mais próximos
        // de pontos aleatórios da pista ficam seguros
        List<Driver> activeDrivers = new ArrayList<>();
        for (Driver driver : heat.getDrivers().values()) {
            if (!driver.isFinished() && !driver.isDnf()) {
                activeDrivers.add(driver);
            }
        }

        // Sorteia posições de safe zones no mundo
        List<Location> safeLocations = new ArrayList<>();
        for (int i = 0; i < safeZones; i++) {
            Driver randomDriver = activeDrivers.get(random.nextInt(activeDrivers.size()));
            Player p = Bukkit.getPlayer(randomDriver.getUuid());
            if (p != null) {
                safeLocations.add(p.getLocation().clone());
            }
        }

        // Marca jogadores que estão perto de uma safe zone (raio de 10 blocos)
        for (Driver driver : activeDrivers) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player == null || !player.isOnline()) continue;

            for (Location safeLoc : safeLocations) {
                if (player.getLocation().distanceSquared(safeLoc) < 100.0) { // 10 blocos
                    safePlayers.add(driver.getUuid());
                    player.sendMessage(ChatColor.GREEN + "✓ Você está em uma ZONA SEGURA!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                    break;
                }
            }
        }
    }

    private void eliminateUnsafePlayers(Heats heat) {
        int eliminated = 0;

        for (Driver driver : heat.getDrivers().values()) {
            if (driver.isFinished() || driver.isDnf()) continue;

            if (!safePlayers.contains(driver.getUuid())) {
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
                    player.sendMessage(ChatColor.RED + "✗ Você NÃO estava em uma zona segura!");
                }

                heat.handleDriverDNF(driver, "Eliminado nas Cadeiras Musicais");
                eliminated++;
            }
        }

        if (eliminated > 0) {
            broadcast(heat, ChatColor.RED + "⚠ " + eliminated + " piloto(s) eliminado(s)!");
        }

        safePlayers.clear();

        // Verifica se sobrou apenas um
        long remaining = heat.getDrivers().values().stream()
                .filter(d -> !d.isFinished() && !d.isDnf())
                .count();

        if (remaining <= 1) {
            broadcast(heat, ChatColor.GOLD + "🏆 Fim das Cadeiras Musicais!");
        }
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
        if (roundTask != null && !roundTask.isCancelled()) roundTask.cancel();
        if (musicTask != null && !musicTask.isCancelled()) musicTask.cancel();
        roundTask = null;
        musicTask = null;
        safePlayers.clear();
        round = 0;
        safeZones = 0;
        musicPlaying = true;
    }

    public boolean isMusicPlaying() {
        return musicPlaying;
    }

    public int getRound() {
        return round;
    }

    public int getSafeZones() {
        return safeZones;
    }
}
