package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 🪑 MUSICAL CHAIRS
 * Each round, the number of "chairs" (safe zones) decreases.
 * When the music stops, whoever is not in a safe zone is eliminated.
 * Safe zones are glowing areas that appear on the track.
 * The last survivor wins.
 */
public class MusicalChairSession implements SessionLogic {

    private static final int ROUND_INTERVAL_TICKS = 600; // 30 segundos
    private static final int MUSIC_STOP_WARNING_TICKS = 100; // 5 segundos antes

    private FRTask roundTask;
    private FRTask musicTask;
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
        broadcast(heat, ChatColor.LIGHT_PURPLE + "  🪑 MUSICAL CHAIRS MODE 🪑");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  The music will play and stop!");
        broadcast(heat, ChatColor.RED + "  When it stops, get into a SAFE ZONE!");
        broadcast(heat, ChatColor.GRAY + "  Safe zones: " + safeZones);
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

            // Phase 1: Music playing — players race around the track
            musicPlaying = true;
            broadcast(heat, ChatColor.LIGHT_PURPLE + "🎵 Music playing! Race around the track!");

            // Speed effect for everyone during the music
            for (Driver driver : heat.getDrivers().values()) {
                if (driver.isFinished() || driver.isDnf()) continue;
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ROUND_INTERVAL_TICKS, 1, false, false));
                    player.playSound(player.getLocation(), Sound.MUSIC_DISC_CAT, 1.0f, 1.0f);
                }
            }

            // Phase 2: Music stops — safe zones appear
            SchedulerHelper.runTaskLater(heat.getPlugin(), () -> {
                if (heat.getHeatState() != HeatState.RACING) return;

                musicPlaying = false;

                // Calculate safe zones (decreases each round)
                int activeDrivers = (int) heat.getDrivers().values().stream()
                        .filter(d -> !d.isFinished() && !d.isDnf())
                        .count();
                safeZones = Math.max(1, activeDrivers - round);
                if (safeZones < 1) safeZones = 1;

                broadcast(heat, ChatColor.RED + "🛑 MUSIC STOPPED! Run to the safe zones!");
                broadcast(heat, ChatColor.YELLOW + "Safe zones: " + safeZones + " | You have 5 seconds!");

                // Mark players in safe zones (based on position — zones are map areas)
                markSafePlayers(heat);

                // Som de alerta
                for (Driver driver : heat.getDrivers().values()) {
                    if (driver.isFinished() || driver.isDnf()) continue;
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f, 0.5f);
                    }
                }

                // After 5 seconds, eliminate those not in a safe zone
                SchedulerHelper.runTaskLater(heat.getPlugin(), () -> {
                    if (heat.getHeatState() != HeatState.RACING) return;
                    eliminateUnsafePlayers(heat);
                }, 100L); // 5 segundos

            }, ROUND_INTERVAL_TICKS - 100); // 25 seconds of music, 5 of reaction

        }, 40L, ROUND_INTERVAL_TICKS + 140); // Intervalo entre rodadas
    }

    private void markSafePlayers(Heats heat) {
        safePlayers.clear();

        // Safe zones are based on position — the N closest drivers
        // to random track points are safe
        List<Driver> activeDrivers = new ArrayList<>();
        for (Driver driver : heat.getDrivers().values()) {
            if (!driver.isFinished() && !driver.isDnf()) {
                activeDrivers.add(driver);
            }
        }

        // Draw safe zone positions in the world
        List<Location> safeLocations = new ArrayList<>();
        for (int i = 0; i < safeZones; i++) {
            Driver randomDriver = activeDrivers.get(random.nextInt(activeDrivers.size()));
            Player p = Bukkit.getPlayer(randomDriver.getUuid());
            if (p != null) {
                safeLocations.add(p.getLocation().clone());
            }
        }

        // Mark players near a safe zone (10 block radius)
        for (Driver driver : activeDrivers) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player == null || !player.isOnline()) continue;

            for (Location safeLoc : safeLocations) {
                if (player.getLocation().distanceSquared(safeLoc) < 100.0) { // 10 blocos
                    safePlayers.add(driver.getUuid());
                    player.sendMessage(ChatColor.GREEN + "✓ You are in a SAFE ZONE!");
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
                    player.sendMessage(ChatColor.RED + "✗ You were NOT in a safe zone!");
                }

                heat.handleDriverDNF(driver, "Eliminated in Musical Chairs");
                eliminated++;
            }
        }

        if (eliminated > 0) {
            broadcast(heat, ChatColor.RED + "⚠ " + eliminated + " driver(s) eliminated!");
        }

        safePlayers.clear();

        // Verifica se sobrou apenas um
        long remaining = heat.getDrivers().values().stream()
                .filter(d -> !d.isFinished() && !d.isDnf())
                .count();

        if (remaining <= 1) {
            broadcast(heat, ChatColor.GOLD + "🏆 Musical Chairs Over!");
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
