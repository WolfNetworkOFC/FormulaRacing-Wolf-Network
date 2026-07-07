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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * 🥊 SUMO
 * All drivers in a circular arena. Anyone who leaves the arena is eliminated.
 * The arena shrinks every 30 seconds. Pushes are stronger.
 * Last one inside the arena wins.
 */
public class SumoSession implements SessionLogic {

    private static final int ARENA_SHRINK_TICKS = 600; // 30 segundos
    private static final int CHECK_INTERVAL_TICKS = 10; // 0.5 segundos

    private FRTask arenaTask;
    private FRTask checkTask;
    private Location arenaCenter;
    private double arenaRadius = 20.0;
    private double minRadius = 5.0;
    private int round = 0;
    private final Random random = new Random();

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();

        // Define the arena center based on the first driver's position
        for (Driver driver : heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                arenaCenter = player.getLocation().clone();
                arenaCenter.setY(player.getLocation().getY());
                break;
            }
        }

        if (arenaCenter == null) {
            arenaCenter = new Location(Bukkit.getWorlds().get(0), 0, 64, 0);
        }

        // Teleport everyone to the arena
        for (Driver driver : heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = random.nextDouble() * (arenaRadius - 3);
                Location spawn = arenaCenter.clone().add(
                        Math.cos(angle) * dist, 0, Math.sin(angle) * dist
                );
                spawn.setYaw((float) Math.toDegrees(angle) + 180);
                SchedulerHelper.teleport(player, spawn);
            }
        }

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.RED + "  🥊 SUMO MODE 🥊");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  Push others out of the arena!");
        broadcast(heat, ChatColor.RED + "  The arena will SHRINK every 30 seconds!");
        broadcast(heat, ChatColor.GREEN + "  Last one inside WINS!");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");

        startArenaShrink(heat);
        startBoundaryCheck(heat);
    }

    private void startArenaShrink(Heats heat) {
        arenaTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                cleanup();
                return;
            }

            round++;
            arenaRadius = Math.max(minRadius, arenaRadius - 2.0);

            broadcast(heat, ChatColor.RED + "🔻 Arena shrunk! Radius: " + String.format("%.0f", arenaRadius) + " blocks");

            // Efeito visual da borda
            for (int i = 0; i < 360; i += 5) {
                double angle = Math.toRadians(i);
                Location edge = arenaCenter.clone().add(
                        Math.cos(angle) * arenaRadius, 0, Math.sin(angle) * arenaRadius
                );
                edge.getWorld().spawnParticle(Particle.FLAME, edge, 1, 0, 0, 0, 0);
            }

            // Som de alerta
            for (Driver driver : heat.getDrivers().values()) {
                if (driver.isFinished() || driver.isDnf()) continue;
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
                }
            }

        }, ARENA_SHRINK_TICKS, ARENA_SHRINK_TICKS);
    }

    private void startBoundaryCheck(Heats heat) {
        checkTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                cleanup();
                return;
            }

            for (Driver driver : heat.getDrivers().values()) {
                if (driver.isFinished() || driver.isDnf()) continue;

                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player == null || !player.isOnline()) continue;

                double dist = player.getLocation().distance(arenaCenter);

                // If outside the arena
                if (dist > arenaRadius) {
                    eliminatePlayer(heat, driver, "Fell out of the arena!");
                    continue;
                }

                // If near the edge, push inward
                if (dist > arenaRadius - 3) {
                    Vector push = arenaCenter.toVector().subtract(player.getLocation().toVector());
                    push.setY(0);
                    if (push.lengthSquared() > 0.001) {
                        push = push.normalize().multiply(0.3);
                        player.setVelocity(player.getVelocity().add(push));
                    }
                    player.sendMessage(ChatColor.RED + "⚠ Near the edge!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                }
            }

            // Check if only one remains
            long remaining = heat.getDrivers().values().stream()
                    .filter(d -> !d.isFinished() && !d.isDnf())
                    .count();

            if (remaining <= 1) {
                broadcast(heat, ChatColor.GOLD + "🏆 Sumo Over!");
            }

        }, 0L, CHECK_INTERVAL_TICKS);
    }

    private void eliminatePlayer(Heats heat, Driver driver, String reason) {
        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.RED + "✗ You were eliminated from Sumo!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f);
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.05);
        }

        heat.handleDriverDNF(driver, reason);

        if (player != null) {
            broadcast(heat, ChatColor.RED + "🥊 " + player.getName() + " was eliminated from Sumo!");
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
        if (arenaTask != null && !arenaTask.isCancelled()) arenaTask.cancel();
        if (checkTask != null && !checkTask.isCancelled()) checkTask.cancel();
        arenaTask = null;
        checkTask = null;
        arenaRadius = 20.0;
        round = 0;
    }

    public Location getArenaCenter() {
        return arenaCenter;
    }

    public double getArenaRadius() {
        return arenaRadius;
    }
}
