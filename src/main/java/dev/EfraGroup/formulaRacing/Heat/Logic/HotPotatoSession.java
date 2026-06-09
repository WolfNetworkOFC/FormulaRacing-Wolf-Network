package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.*;

/**
 * 🔥 BATATA QUENTE
 * Um jogador recebe a "batata" (item brilhante). A cada X segundos a batata
 * passa para o jogador mais próximo. Quem segurar a batata por muito tempo
 * acumula calor e explode (DNF). O último sobrevivente vence.
 */
public class HotPotatoSession implements SessionLogic {

    private static final int POTATO_INTERVAL_TICKS = 200; // 10 segundos
    private static final int MAX_HEAT = 100;
    private static final int HEAT_PER_TICK = 2;

    private final Map<UUID, Integer> heatLevels = new HashMap<>();
    private UUID currentPotatoHolder = null;
    private ScheduledTask potatoTask;
    private ScheduledTask heatTask;
    private int tickCounter = 0;

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();

        // Inicializa calor de todos os pilotos
        for (Driver driver : heat.getDrivers().values()) {
            heatLevels.put(driver.getUuid(), 0);
        }

        // Sorteia o primeiro jogador com a batata
        List<UUID> drivers = new ArrayList<>(heatLevels.keySet());
        if (!drivers.isEmpty()) {
            currentPotatoHolder = drivers.get(new Random().nextInt(drivers.size()));
        }

        startHeatTask(heat);
        startPotatoTask(heat);

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.RED + "  🔥 MODO BATATA QUENTE 🔥");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  A batata será passada entre pilotos!");
        broadcast(heat, ChatColor.RED + "  Quem segurar por muito tempo EXPLODE!");
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

            // Aumenta calor de quem está com a batata
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
                        // Partículas de fogo
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

            // Passa a batata para o jogador mais próximo
            passPotato(heat);
        }, POTATO_INTERVAL_TICKS, POTATO_INTERVAL_TICKS);
    }

    private void passPotato(Heats heat) {
        if (currentPotatoHolder == null) return;

        Player currentPlayer = Bukkit.getPlayer(currentPotatoHolder);
        if (currentPlayer == null || !currentPlayer.isOnline()) return;

        // Encontra o jogador mais próximo que ainda está na corrida
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
            // Reseta calor do antigo holder
            heatLevels.put(currentPotatoHolder, 0);
            currentPotatoHolder = nearest;

            // Efeito de passagem
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

        // Marca como DNF
        Driver driver = heat.getDriver(uuid);
        if (driver != null) {
            heat.handleDriverDNF(driver, "Explodiu na Batata Quente!");
        }

        broadcast(heat, ChatColor.RED + "💥 " + (player != null ? player.getName() : "Um piloto") + " EXPLODIU com a batata!");

        // Verifica se sobrou apenas um
        long remaining = heat.getDrivers().values().stream()
                .filter(d -> !d.isFinished() && !d.isDnf())
                .count();

        if (remaining <= 1) {
            // Passa a batata para null
            currentPotatoHolder = null;
        } else {
            // Sorteia novo holder entre os restantes
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

        broadcast(heat, ChatColor.RED + "🔥 " + holder.getName() + " está com a BATATA QUENTE!");
        holder.sendMessage(ChatColor.RED + "⚠ VOCÊ ESTÁ COM A BATATA! Passe para alguém rápido!");
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
        // Em batata quente, voltas não importam — apenas sobreviver
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
