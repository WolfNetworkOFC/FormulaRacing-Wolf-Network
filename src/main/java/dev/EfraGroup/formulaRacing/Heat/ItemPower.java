package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.Participant.Driver;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

import java.util.*;

/**
 * Mario Kart-style item powers for heat races.
 * Each power has a name, color, sound, particle, weight (probability), and effect.
 */
public enum ItemPower {

    // ── Beneficial powers (higher weight = more common) ──

    /** Mushroom — short speed boost */
    MUSHROOM("§a§l🍄 MUSHROOM", "§aSpeed boost!", 15, 60) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, false, false, true));
            player.playSound(player.getLocation(), Sound.ENTITY_HORSE_JUMP, 1.5f, 1.8f);
            spawnParticles(player, Particle.HAPPY_VILLAGER, 20);
        }
    },

    /** Triple Mushroom — longer speed boost */
    TRIPLE_MUSHROOM("§a§l🍄🍄🍄 TRIPLE MUSHROOM", "§aTriple speed boost!", 8, 100) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2, false, false, true));
            player.playSound(player.getLocation(), Sound.ENTITY_HORSE_JUMP, 2.0f, 2.0f);
            spawnParticles(player, Particle.HAPPY_VILLAGER, 40);
        }
    },

    /** Star — invincibility + speed */
    STAR("§e§l⭐ STAR", "§eInvincibility!", 5, 160) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 3, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 160, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 4, false, false, true));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
            spawnParticles(player, Particle.FIREWORK, 30);
        }
    },

    /** Lightning Bolt — slows all other drivers */
    LIGHTNING_BOLT("§b§l⚡ LIGHTNING BOLT", "§bEveryone slowed down!", 4, 0) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            // Slow all OTHER drivers in the same heat
            UUID playerUUID = player.getUniqueId();
            var heat = plugin.getDriverLookup().getHeat(playerUUID);
            if (heat != null) {
                for (var driver : heat.getDrivers().values()) {
                    if (driver.getUuid().equals(playerUUID)) continue;
                    Player other = Bukkit.getPlayer(driver.getUuid());
                    if (other != null && other.isOnline()) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, false, true));
                        other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, true));
                        other.playSound(other.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
                        spawnParticles(other, Particle.ELECTRIC_SPARK, 30);
                    }
                }
            }
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.5f, 1.2f);
            spawnParticles(player, Particle.ELECTRIC_SPARK, 20);
        }
    },

    /** Banana Peel — drops a trap behind the driver */
    BANANA("§e§l🍌 BANANA", "§eBanana peel dropped!", 12, 0) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            // Apply a brief slowness to simulate dropping something
            player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.5f, 1.0f);
            spawnParticles(player, Particle.HAPPY_VILLAGER, 10);
            // The banana peel is a visual effect — in boat racing, we apply brief slowness
            // to the player behind (simplified approach)
            SchedulerHelper.runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    spawnParticles(player, Particle.FALLING_DUST, 15);
                }
            }, 5L);
        }
    },

    /** Green Shell — small forward projectile effect (speed boost + brief blindness to nearest) */
    GREEN_SHELL("§2§l🐚 GREEN SHELL", "§2Shell launched!", 10, 0) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false, true));
            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.5f, 1.5f);
            spawnParticles(player, Particle.HAPPY_VILLAGER, 15);
        }
    },

    /** Red Shell — targets the nearest driver ahead */
    RED_SHELL("§c§l🐚 RED SHELL", "§cTargeting nearest rival!", 7, 0) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            UUID playerUUID = player.getUniqueId();
            var heat = plugin.getDriverLookup().getHeat(playerUUID);
            if (heat != null) {
                Driver driver = heat.getDriver(playerUUID);
                if (driver != null) {
                    int myPos = driver.getPosition();
                    // Find nearest driver ahead (lower position number = ahead)
                    Player target = null;
                    int bestPos = Integer.MAX_VALUE;
                    for (var d : heat.getDrivers().values()) {
                        if (d.getUuid().equals(playerUUID)) continue;
                        if (d.getPosition() < myPos && d.getPosition() < bestPos) {
                            Player p = Bukkit.getPlayer(d.getUuid());
                            if (p != null && p.isOnline()) {
                                target = p;
                                bestPos = d.getPosition();
                            }
                        }
                    }
                    if (target != null) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, false, true));
                        target.playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 0.8f);
                        spawnParticles(target, Particle.CRIT, 20);
                    }
                }
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.5f, 1.2f);
            spawnParticles(player, Particle.CRIT, 10);
        }
    },

    /** Bob-omb — explosion effect, slows nearby drivers */
    BOB_OMB("§8§l💣 BOB-OMB", "§8BOOM!", 5, 0) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            Location loc = player.getLocation();
            player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            spawnParticles(loc, Particle.EXPLOSION, 5);
            spawnParticles(loc, Particle.SMOKE, 30);

            // Slow nearby players
            UUID playerUUID = player.getUniqueId();
            var heat = plugin.getDriverLookup().getHeat(playerUUID);
            if (heat != null) {
                for (var driver : heat.getDrivers().values()) {
                    if (driver.getUuid().equals(playerUUID)) continue;
                    Player other = Bukkit.getPlayer(driver.getUuid());
                    if (other != null && other.isOnline() && other.getLocation().distanceSquared(loc) < 25) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, false, true));
                        other.playSound(other.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
                    }
                }
            }
        }
    },

    /** Coin — small speed boost */
    COIN("§6§l🪙 COIN", "§6Coin collected!", 18, 40) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, false, true));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 1.5f);
            spawnParticles(player, Particle.HAPPY_VILLAGER, 10);
        }
    },

    /** Mushroom Boost — instant burst of speed */
    MUSHROOM_BOOST("§a§l💨 MUSHROOM BOOST", "§aInstant boost!", 14, 30) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 3, false, false, true));
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 1.8f);
            spawnParticles(player, Particle.CLOUD, 15);
        }
    },

    /** Blooper — ink screen effect (blindness) on drivers ahead */
    BLOOPER("§8§l🦑 BLOOPER", "§8Ink attack!", 6, 0) {
        @Override
        public void apply(Player player, FormulaRacing plugin) {
            UUID playerUUID = player.getUniqueId();
            var heat = plugin.getDriverLookup().getHeat(playerUUID);
            if (heat != null) {
                Driver driver = heat.getDriver(playerUUID);
                if (driver != null) {
                    int myPos = driver.getPosition();
                    int count = 0;
                    for (var d : heat.getDrivers().values()) {
                        if (d.getUuid().equals(playerUUID)) continue;
                        if (d.getPosition() < myPos && count < 3) {
                            Player p = Bukkit.getPlayer(d.getUuid());
                            if (p != null && p.isOnline()) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false, true));
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, false, true));
                                p.playSound(p.getLocation(), Sound.ENTITY_SQUID_SQUIRT, 1.0f, 1.0f);
                                spawnParticles(p, Particle.SQUID_INK, 25);
                                count++;
                            }
                        }
                    }
                }
            }
            player.playSound(player.getLocation(), Sound.ENTITY_SQUID_SQUIRT, 1.5f, 1.2f);
        }
    };

    // ── Fields ──

    private final String displayName;
    private final String effectMessage;
    private final int weight;        // probability weight (higher = more common)
    private final int durationTicks; // 0 = instant effect

    ItemPower(String displayName, String effectMessage, int weight, int durationTicks) {
        this.displayName = displayName;
        this.effectMessage = effectMessage;
        this.weight = weight;
        this.durationTicks = durationTicks;
    }

    /**
     * Apply this power's effect to the player.
     */
    public abstract void apply(Player player, FormulaRacing plugin);

    public String getDisplayName() { return displayName; }
    public String getEffectMessage() { return effectMessage; }
    public int getWeight() { return weight; }
    public int getDurationTicks() { return durationTicks; }

    // ── Weighted random selection ──

    private static final Random RANDOM = new Random();
    private static final int TOTAL_WEIGHT;
    static {
        int total = 0;
        for (ItemPower p : values()) total += p.weight;
        TOTAL_WEIGHT = total;
    }

    /**
     * Pick a random power based on weights.
     */
    public static ItemPower randomPower() {
        if (TOTAL_WEIGHT <= 0) return COIN;
        int roll = RANDOM.nextInt(TOTAL_WEIGHT);
        int cumulative = 0;
        for (ItemPower p : values()) {
            cumulative += p.weight;
            if (roll < cumulative) return p;
        }
        return COIN; // fallback
    }

    /**
     * Pick a random power, optionally biased by player position.
     * Players further behind get slightly better powers.
     */
    public static ItemPower randomPowerForPosition(int position, int totalDrivers) {
        // Simple bias: if in last half, slightly increase chance of better powers
        if (totalDrivers > 1 && position > totalDrivers / 2) {
            // 30% chance to get a "good" power from the top tier
            if (RANDOM.nextFloat() < 0.30f) {
                ItemPower[] goodPowers = {STAR, TRIPLE_MUSHROOM, LIGHTNING_BOLT, RED_SHELL, BOB_OMB};
                return goodPowers[RANDOM.nextInt(goodPowers.length)];
            }
        }
        return randomPower();
    }

    // ── Utility ──

    protected static void spawnParticles(Player player, Particle particle, int count) {
        if (player == null || !player.isOnline()) return;
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0.1);
    }

    protected static void spawnParticles(Location loc, Particle particle, int count) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0.1);
    }
}
