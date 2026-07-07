package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 🔥 HOT POTATO
 * One player receives the "potato" (glowing item). Every X seconds the potato
 * passes to the nearest player. Whoever holds the potato for too long
 * accumulates heat and explodes (DNF). The last survivor wins.
 */
public class HotPotatoSession implements SessionLogic {

    private static final int POTATO_INTERVAL_TICKS = 200; // 10 segundos
    private static final int MAX_HEAT = 100;
    private static final int HEAT_PER_TICK = 2;

    private final Map<UUID, Integer> heatLevels = new HashMap<>();
    private UUID currentPotatoHolder = null;
    private FRTask potatoTask;
    private FRTask heatTask;
    private int tickCounter = 0;

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();

        // Initialize heat for all drivers
        for (Driver driver : heat.getDrivers().values()) {
            heatLevels.put(driver.getUuid(), 0);
        }

        // Draw the first player with the potato
        List<UUID> drivers = new ArrayList<>(heatLevels.keySet());
        if (!drivers.isEmpty()) {
            currentPotatoHolder = drivers.get(new Random().nextInt(drivers.size()));
        }

        startHeatTask(heat);
        startPotatoTask(heat);

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.RED + "  🔥 HOT POTATO MODE 🔥");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  The potato will be passed between drivers!");
        broadcast(heat, ChatColor.RED + "  Whoever holds it too long EXPLODES!");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");

        announcePotatoHolder(heat);
    }

    private void startHeatTask(Heats heat) {
        heatTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                stopAll();
                return;
            }

            tickCounter++;

            // Increase heat for whoever has the potato
            if (currentPotatoHolder != null) {
                int currentHeat = heatLevels.getOrDefault(currentPotatoHolder, 0);
                currentHeat = Math.min(MAX_HEAT, currentHeat + HEAT_PER_TICK);
                heatLevels.put(currentPotatoHolder, currentHeat);

                // Efeitos visuais baseados no calor
                Player holder = Bukkit.getPlayer(currentPotatoHolder);
                if (holder != null && holder.isOnline()) {
                    if (currentHeat > 30) {
                        holder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
                    }
                    if (currentHeat > 60) {
                        holder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
                        holder.playSound(holder.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.5f);
                    }
                    if (currentHeat > 80) {
                        holder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, false));
                        holder.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                        // Fire particles
                        holder.getWorld().spawnParticle(Particle.FLAME, holder.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
                    }
                    if (currentHeat >= MAX_HEAT) {
                        // EXPLODE!
                        explodeDriver(heat, currentPotatoHolder);
                    }
                }
            }
        }, 0L, 10L); // A cada 0.5 segundos
    }

    private void startPotatoTask(Heats heat) {
        potatoTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                stopAll();
                return;
            }

            // Pass the potato to the nearest player
            passPotato(heat);
        }, POTATO_INTERVAL_TICKS, POTATO_INTERVAL_TICKS);
    }

    private void passPotato(Heats heat) {
        if (currentPotatoHolder == null) return;

        Player currentPlayer = Bukkit.getPlayer(currentPotatoHolder);
        if (currentPlayer == null || !currentPlayer.isOnline()) return;

        // Find the nearest player still in the race
        UUID nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Driver driver : heat.getDrivers().values()) {
            if (driver.getUuid().equals(currentPotatoHolder)) continue;
            if (driver.isFinished() || driver.isDnf()) continue;

            Player other = Bukkit.getPlayer(driver.getUuid());
            if (other == null || !other.isOnline()) continue;

            double dist = currentPlayer.getLocation().distanceSquared(other.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = driver.getUuid();
            }
        }

        if (nearest != null) {
            // Reset heat of the old holder
            heatLevels.put(currentPotatoHolder, 0);
            currentPotatoHolder = nearest;

            // Passing effect
            Player newHolder = Bukkit.getPlayer(nearest);
            if (newHolder != null) {
                newHolder.playSound(newHolder.getLocation(), Sound.ENTITY_ITEM_PICKUP, 2.0f, 0.5f);
                newHolder.getWorld().spawnParticle(Particle.LAVA, newHolder.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            }

            announcePotatoHolder(heat);
        }
    }

    private void explodeDriver(Heats heat, UUID uuid) {
        heatLevels.put(uuid, 0);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            SchedulerHelper.runTaskFor(heat.getPlugin(), player, () -> {
                player.getWorld().createExplosion(player.getLocation(), 0f, false, false);
                player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
            });
        }

        // Mark as DNF
        Driver driver = heat.getDriver(uuid);
        if (driver != null) {
            heat.handleDriverDNF(driver, "Exploded in Hot Potato!");
        }

        broadcast(heat, ChatColor.RED + "💥 " + (player != null ? player.getName() : "A driver") + " EXPLODED with the potato!");

        // Check if only one remains
        long remaining = heat.getDrivers().values().stream()
                .filter(d -> !d.isFinished() && !d.isDnf())
                .count();

        if (remaining <= 1) {
            // Pass the potato to null
            currentPotatoHolder = null;
        } else {
            // Draw new holder among the remaining
            List<UUID> remainingIds = new ArrayList<>();
            for (Driver d : heat.getDrivers().values()) {
                if (!d.isFinished() && !d.isDnf()) {
                    remainingIds.add(d.getUuid());
                }
            }
            if (!remainingIds.isEmpty()) {
                currentPotatoHolder = remainingIds.get(new Random().nextInt(remainingIds.size()));
                announcePotatoHolder(heat);
            }
        }
    }

    private void announcePotatoHolder(Heats heat) {
        if (currentPotatoHolder == null) return;
        Player holder = Bukkit.getPlayer(currentPotatoHolder);
        if (holder == null) return;

        broadcast(heat, ChatColor.RED + "🔥 " + holder.getName() + " has the HOT POTATO!");
        holder.sendMessage(ChatColor.RED + "⚠ YOU HAVE THE POTATO! Pass it to someone fast!");
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
        // In hot potato, laps don't matter — only surviving
        return true;
    }

    public void cleanup() {
        stopAll();
        heatLevels.clear();
        currentPotatoHolder = null;
        tickCounter = 0;
    }

    private void stopAll() {
        if (potatoTask != null && !potatoTask.isCancelled()) potatoTask.cancel();
        if (heatTask != null && !heatTask.isCancelled()) heatTask.cancel();
        potatoTask = null;
        heatTask = null;
    }

    public UUID getCurrentPotatoHolder() {
        return currentPotatoHolder;
    }

    public int getHeatLevel(UUID uuid) {
        return heatLevels.getOrDefault(uuid, 0);
    }
}

