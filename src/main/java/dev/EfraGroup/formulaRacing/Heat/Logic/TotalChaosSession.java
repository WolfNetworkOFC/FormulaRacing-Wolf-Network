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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 💥 TOTAL CHAOS
 * Every 10 seconds, ALL drivers receive a random effect:
 * speed, slowness, jump, blindness, nausea, inversion, teleport,
 * fake explosion, freezing, etc.
 * Survive the chaos! Last one standing wins.
 */
public class TotalChaosSession implements SessionLogic {

    private static final int CHAOS_INTERVAL_TICKS = 200; // 10 segundos

    private FRTask chaosTask;
    private int chaosRound = 0;
    private final Random random = new Random();

    private enum ChaosEffect {
        SPEED_BOOST("⚡ Max Speed!", PotionEffectType.SPEED, 3, 100),
        SLOWNESS("🐌 Slowness!", PotionEffectType.SLOWNESS, 2, 100),
        JUMP_BOOST("🦘 Super Jump!", PotionEffectType.JUMP_BOOST, 5, 100),
        BLINDNESS("👁️ Blindness!", PotionEffectType.BLINDNESS, 0, 60),
        NAUSEA("🤢 Nausea!", PotionEffectType.NAUSEA, 0, 100),
        LEVITATION("☁️ Levitation!", PotionEffectType.LEVITATION, 1, 40),
        INVISIBILITY("👻 Invisibility!", PotionEffectType.INVISIBILITY, 0, 80),
        WEAKNESS("💪 Weakness!", PotionEffectType.WEAKNESS, 1, 100),
        HASTE("⛏️ Haste!", PotionEffectType.HASTE, 2, 100),
        MINING_FATIGUE("😴 Fatigue!", PotionEffectType.MINING_FATIGUE, 2, 100),
        GLOWING("✨ Glowing!", PotionEffectType.GLOWING, 0, 100),
        LUCK("🍀 Luck!", PotionEffectType.LUCK, 1, 100),
        BAD_OMEN("☠️ Bad Omen!", PotionEffectType.BAD_OMEN, 0, 100),
        DOLPHINS_GRACE("🐬 Dolphin's Grace!", PotionEffectType.DOLPHINS_GRACE, 0, 60),
        SLOW_FALLING("🪂 Slow Falling!", PotionEffectType.SLOW_FALLING, 0, 80),
        CONDUIT_POWER("🌊 Conduit Power!", PotionEffectType.CONDUIT_POWER, 0, 60),
        DARKNESS("🌑 Darkness!", PotionEffectType.DARKNESS, 0, 60),
        FREEZE("🥶 Freeze!", PotionEffectType.SLOWNESS, 255, 40);

        final String displayName;
        final PotionEffectType effect;
        final int amplifier;
        final int duration;

        ChaosEffect(String displayName, PotionEffectType effect, int amplifier, int duration) {
            this.displayName = displayName;
            this.effect = effect;
            this.amplifier = amplifier;
            this.duration = duration;
        }
    }

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.LIGHT_PURPLE + "  💥 TOTAL CHAOS MODE 💥");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  Random effects every 10 seconds!");
        broadcast(heat, ChatColor.RED + "  Speed, blindness, levitation, explosion...");
        broadcast(heat, ChatColor.GREEN + "  Survive the CHAOS!");
        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");

        startChaosTask(heat);
    }

    private void startChaosTask(Heats heat) {
        chaosTask = SchedulerHelper.runTaskTimer(heat.getPlugin(), () -> {
            if (heat.getHeatState() != HeatState.RACING) {
                cleanup();
                return;
            }

            chaosRound++;

            broadcast(heat, ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            broadcast(heat, ChatColor.RED + "  💥 TOTAL CHAOS — Round " + chaosRound + " 💥");
            broadcast(heat, ChatColor.LIGHT_PURPLE + "═══════════════════════════════");

            // Apply random effect for EACH driver
            for (Driver driver : heat.getDrivers().values()) {
                if (driver.isFinished() || driver.isDnf()) continue;

                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player == null || !player.isOnline()) continue;

                // Draw 1-3 effects for this driver
                int numEffects = 1 + random.nextInt(3);
                List<ChaosEffect> applied = new ArrayList<>();

                for (int i = 0; i < numEffects; i++) {
                    ChaosEffect effect = ChaosEffect.values()[random.nextInt(ChaosEffect.values().length)];
                    applied.add(effect);

                    // Apply the effect
                    player.addPotionEffect(new PotionEffect(effect.effect, effect.duration, effect.amplifier, false, false));

                    // Special effects
                    applySpecialEffect(player, effect);
                }

                // Message for the driver
                StringBuilder msg = new StringBuilder(ChatColor.YELLOW + "Efeitos: ");
                for (int i = 0; i < applied.size(); i++) {
                    if (i > 0) msg.append(ChatColor.GRAY + ", ");
                    msg.append(ChatColor.RED).append(applied.get(i).displayName);
                }
                player.sendMessage(msg.toString());
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 0.5f + random.nextFloat());
            }

                // Special global effect every 3 rounds
            if (chaosRound % 3 == 0) {
                applyGlobalChaos(heat);
            }

        }, 40L, CHAOS_INTERVAL_TICKS);
    }

    private void applySpecialEffect(Player player, ChaosEffect effect) {
        switch (effect) {
            case LEVITATION:
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
                break;
            case BLINDNESS:
            case DARKNESS:
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.5f);
                break;
            case FREEZE:
                // Freezes the player in place
                player.setFreezeTicks(140);
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                break;
            case GLOWING:
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                break;
            default:
                break;
        }
    }

    private void applyGlobalChaos(Heats heat) {
        int globalType = random.nextInt(4);

        switch (globalType) {
            case 0 -> {
                // Swap positions between two random drivers
                broadcast(heat, ChatColor.LIGHT_PURPLE + "🌀 POSITION SWAP!");
                List<Player> activePlayers = new ArrayList<>();
                for (Driver d : heat.getDrivers().values()) {
                    if (!d.isFinished() && !d.isDnf()) {
                        Player p = Bukkit.getPlayer(d.getUuid());
                        if (p != null && p.isOnline()) activePlayers.add(p);
                    }
                }
                if (activePlayers.size() >= 2) {
                    Player p1 = activePlayers.get(random.nextInt(activePlayers.size()));
                    Player p2 = activePlayers.get(random.nextInt(activePlayers.size()));
                    if (p1 != p2) {
                        Location temp = p1.getLocation().clone();
                        SchedulerHelper.teleport(p1, p2.getLocation());
                        SchedulerHelper.teleport(p2, temp);
                        p1.sendMessage(ChatColor.RED + "🌀 You swapped positions with " + p2.getName() + "!");
                        p2.sendMessage(ChatColor.RED + "🌀 You swapped positions with " + p1.getName() + "!");
                    }
                }
            }
            case 1 -> {
                // Everyone gets extreme speed for 3 seconds
                broadcast(heat, ChatColor.YELLOW + "⚡ COLLECTIVE TURBO!");
                for (Driver d : heat.getDrivers().values()) {
                    if (d.isFinished() || d.isDnf()) continue;
                    Player p = Bukkit.getPlayer(d.getUuid());
                    if (p != null && p.isOnline()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 5, false, false));
                        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.5f);
                    }
                }
            }
            case 2 -> {
                // Everyone gets slow for 3 seconds
                broadcast(heat, ChatColor.BLUE + "🐌 COLLECTIVE DECELERATION!");
                for (Driver d : heat.getDrivers().values()) {
                    if (d.isFinished() || d.isDnf()) continue;
                    Player p = Bukkit.getPlayer(d.getUuid());
                    if (p != null && p.isOnline()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false));
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
                    }
                }
            }
            case 3 -> {
                // Boat swap (if possible)
                broadcast(heat, ChatColor.GREEN + "🔄 BOAT MIX!");
                for (Driver d : heat.getDrivers().values()) {
                    if (d.isFinished() || d.isDnf()) continue;
                    Player p = Bukkit.getPlayer(d.getUuid());
                    if (p != null && p.isOnline()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false));
                        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.5f);
                    }
                }
            }
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
        if (chaosTask != null && !chaosTask.isCancelled()) chaosTask.cancel();
        chaosTask = null;
        chaosRound = 0;
    }

    public int getChaosRound() {
        return chaosRound;
    }
}
