package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * ☣️ INFECTION
 * One driver starts infected (red, slower, with particles).
 * When an infected touches a non-infected, they also become infected.
 * Infected drivers get progressively slower over time.
 * The last non-infected wins. If everyone is infected, the last to be
 * infected wins.
 */
public class InfectionSession implements SessionLogic {

    private static final int INFECTION_CHECK_TICKS = 10; // 0.5 segundos
    private static final int INFECTION_SPEED_DECAY_TICKS = 200; // 10 segundos
    private static final double INFECTION_RANGE = 3.0; // blocos
    private static final double INFECTION_RANGE_SQ = INFECTION_RANGE * INFECTION_RANGE;

    private final Set<UUID> infected = new HashSet<>();
    private final Map<UUID, Integer> infectionTime = new HashMap(); // ticks since infection
    private UUID patientZero = null;
    private FRTask checkTask;
    private FRTask decayTask;
    private int tickCounter = 0;

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);
        heat.startOfflineMonitoring();

        // Draw patient zero
        List<UUID> drivers = new ArrayList<>();
        for (Driver d : heat.getDrivers().values()) {
            drivers.add(d.getUuid());
            infectionTime.put(d.getUuid(), 0);
        }

        if (!drivers.isEmpty()) {
            patientZero = drivers.get(new Random().nextInt(drivers.size()));
            infected.add(patientZero);
        }

        startInfectionCheck(heat);
        startDecayTask(heat);

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.DARK_GREEN + "  ☣️ INFECTION MODE ☣️");
        broadcast(heat, "");
        broadcast(heat, ChatColor.RED + "  A driver is INFECTED!");
        broadcast(heat, ChatColor.YELLOW + "  Flee from infected or get contaminated!");
        broadcast(heat, ChatColor.GREEN + "  The last survivor wins!");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");

        // Announce patient zero
        if (patientZero != null) {
            Player pz = Bukkit.getPlayer(patientZero);
            if (pz != null) {
                broadcast(heat, ChatColor.DARK_RED + "☣ " + pz.getName() + " is PATIENT ZERO!");
                pz.sendMessage(ChatColor.DARK_RED + "☣ YOU ARE PATIENT ZERO! Infect everyone!");
            }
        }
    }

    private void startInfectionCheck(Heats heat) {
        checkTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                cleanup();
                return;
            }

            tickCounter++;

            // For each infected, check if they are near someone non-infected
            for (UUID infectedId : new HashSet<>(infected)) {
                Player infectedPlayer = Bukkit.getPlayer(infectedId);
                if (infectedPlayer == null || !infectedPlayer.isOnline()) continue;

                for (Driver driver : heat.getDrivers().values()) {
                    if (infected.contains(driver.getUuid())) continue;
                    if (driver.isFinished() || driver.isDnf()) continue;

                    Player target = Bukkit.getPlayer(driver.getUuid());
                    if (target == null || !target.isOnline()) continue;

                    // Check distance
                    if (infectedPlayer.getWorld().equals(target.getWorld()) &&
                            infectedPlayer.getLocation().distanceSquared(target.getLocation()) < INFECTION_RANGE_SQ) {
                        infectPlayer(heat, driver.getUuid());
                    }
                }
            }

            // Visual effects for infected
            if (tickCounter % 20 == 0) {
                for (UUID infId : infected) {
                    Player p = Bukkit.getPlayer(infId);
                    if (p != null && p.isOnline()) {
                        p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.05);
                    }
                }
            }

            // Check end of game
            long uninfected = heat.getDrivers().values().stream()
                    .filter(d -> !d.isFinished() && !d.isDnf() && !infected.contains(d.getUuid()))
                    .count();

            if (uninfected <= 1 && infected.size() > 1) {
                broadcast(heat, ChatColor.GREEN + "🏆 Infection Over! Only one survivor!");
            }
        }, 0L, INFECTION_CHECK_TICKS);
    }

    private void startDecayTask(Heats heat) {
        decayTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                cleanup();
                return;
            }

            // Increase infection time and apply progressive slowness
            for (UUID infId : infected) {
                int time = infectionTime.getOrDefault(infId, 0) + 1;
                infectionTime.put(infId, time);

                Player p = Bukkit.getPlayer(infId);
                if (p == null || !p.isOnline()) continue;

                // Progressive slowness based on infection time
                int slownessLevel = Math.min(4, time / 5); // A cada 5 ticks, aumenta
                if (slownessLevel > 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, slownessLevel - 1, false, false));
                }

                // Progressive visual effects
                if (time % 10 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                }
                if (time > 20 && time % 20 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 1, false, false));
                }
                if (time > 40 && time % 30 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));
                }
            }
        }, 0L, INFECTION_SPEED_DECAY_TICKS);
    }

    private void infectPlayer(Heats heat, UUID uuid) {
        infected.add(uuid);
        infectionTime.put(uuid, 0);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.DARK_RED + "☣ YOU HAVE BEEN INFECTED!");
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 2.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.ITEM_SLIME, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }

        if (player != null) {
            broadcast(heat, ChatColor.RED + "☣ " + player.getName() + " was INFECTED!");
        }

        // Update scoreboard
        heat.updateLivePositions();
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
        if (checkTask != null && !checkTask.isCancelled()) checkTask.cancel();
        if (decayTask != null && !decayTask.isCancelled()) decayTask.cancel();
        checkTask = null;
        decayTask = null;
        infected.clear();
        infectionTime.clear();
        patientZero = null;
        tickCounter = 0;
    }

    public boolean isInfected(UUID uuid) {
        return infected.contains(uuid);
    }

    public int getInfectedCount() {
        return infected.size();
    }

    public UUID getPatientZero() {
        return patientZero;
    }
}

