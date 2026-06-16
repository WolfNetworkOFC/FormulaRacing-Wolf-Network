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
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * 🥊 SUMO
 * Todos os pilotos em uma arena circular. Quem sai da arena é eliminado.
 * A arena diminui a cada 30 segundos. Empurrões são mais fortes.
 * Último dentro da arena vence.
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

        // Define o centro da arena baseado na posição do primeiro piloto
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

        // Teleporta todos para a arena
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
        broadcast(heat, ChatColor.RED + "  🥊 MODO SUMO 🥊");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  Empurre os outros para fora da arena!");
        broadcast(heat, ChatColor.RED + "  A arena vai ENCOLHER a cada 30 segundos!");
        broadcast(heat, ChatColor.GREEN + "  Último dentro VENCE!");
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

            broadcast(heat, ChatColor.RED + "🔻 Arena encolheu! Raio: " + String.format("%.0f", arenaRadius) + " blocos");

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

                // Se está fora da arena
                if (dist > arenaRadius) {
                    eliminatePlayer(heat, driver, "Caiu da arena!");
                    continue;
                }

                // Se está perto da borda, empurra para dentro
                if (dist > arenaRadius - 3) {
                    Vector push = arenaCenter.toVector().subtract(player.getLocation().toVector());
                    push.setY(0);
                    if (push.lengthSquared() > 0.001) {
                        push = push.normalize().multiply(0.3);
                        player.setVelocity(player.getVelocity().add(push));
                    }
                    player.sendMessage(ChatColor.RED + "⚠ Perto da borda!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                }
            }

            // Verifica se sobrou apenas um
            long remaining = heat.getDrivers().values().stream()
                    .filter(d -> !d.isFinished() && !d.isDnf())
                    .count();

            if (remaining <= 1) {
                broadcast(heat, ChatColor.GOLD + "🏆 Fim do Sumo!");
            }

        }, 0L, CHECK_INTERVAL_TICKS);
    }

    private void eliminatePlayer(Heats heat, Driver driver, String reason) {
        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.RED + "✗ Você foi eliminado do Sumo!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f);
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.05);
        }

        heat.handleDriverDNF(driver, reason);

        if (player != null) {
            broadcast(heat, ChatColor.RED + "🥊 " + player.getName() + " foi eliminado do Sumo!");
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
