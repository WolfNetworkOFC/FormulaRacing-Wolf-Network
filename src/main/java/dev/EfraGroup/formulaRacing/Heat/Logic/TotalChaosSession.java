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
 * 💥 CAOS TOTAL
 * A cada 10 segundos, TODOS os pilotos recebem um efeito aleatório:
 * velocidade, lentidão, pulo, cegueira, náusea, inversão, teletransporte,
 * explosão falsa, congelamento, etc.
 * Sobreviva ao caos! Último de pé vence.
 */
public class TotalChaosSession implements SessionLogic {

    private static final int CHAOS_INTERVAL_TICKS = 200; // 10 segundos

    private FRTask chaosTask;
    private int chaosRound = 0;
    private final Random random = new Random();

    private enum ChaosEffect {
        SPEED_BOOST("⚡ Velocidade Máxima!", PotionEffectType.SPEED, 3, 100),
        SLOWNESS("🐌 Lentidão!", PotionEffectType.SLOWNESS, 2, 100),
        JUMP_BOOST("🦘 Super Pulo!", PotionEffectType.JUMP_BOOST, 5, 100),
        BLINDNESS("👁️ Cegueira!", PotionEffectType.BLINDNESS, 0, 60),
        NAUSEA("🤢 Náusea!", PotionEffectType.NAUSEA, 0, 100),
        LEVITATION("☁️ Levitação!", PotionEffectType.LEVITATION, 1, 40),
        INVISIBILITY("👻 Invisibilidade!", PotionEffectType.INVISIBILITY, 0, 80),
        WEAKNESS("💪 Fraqueza!", PotionEffectType.WEAKNESS, 1, 100),
        HASTE("⛏️ Pressa!", PotionEffectType.HASTE, 2, 100),
        MINING_FATIGUE("😴 Fadiga!", PotionEffectType.MINING_FATIGUE, 2, 100),
        GLOWING("✨ Brilhante!", PotionEffectType.GLOWING, 0, 100),
        LUCK("🍀 Sorte!", PotionEffectType.LUCK, 1, 100),
        BAD_OMEN("☠️ Mau Presságio!", PotionEffectType.BAD_OMEN, 0, 100),
        DOLPHINS_GRACE("🐬 Graça do Golfinho!", PotionEffectType.DOLPHINS_GRACE, 0, 60),
        SLOW_FALLING("🪂 Queda Lenta!", PotionEffectType.SLOW_FALLING, 0, 80),
        CONDUIT_POWER("🌊 Poder do Mar!", PotionEffectType.CONDUIT_POWER, 0, 60),
        DARKNESS("🌑 Escuridão!", PotionEffectType.DARKNESS, 0, 60),
        FREEZE("🥶 Congelamento!", PotionEffectType.SLOWNESS, 255, 40);

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
        heat.startOfflineMonitoring();

        broadcast(heat, ChatColor.GOLD + "═══════════════════════════════");
        broadcast(heat, ChatColor.LIGHT_PURPLE + "  💥 MODO CAOS TOTAL 💥");
        broadcast(heat, "");
        broadcast(heat, ChatColor.YELLOW + "  Efeitos aleatórios a cada 10 segundos!");
        broadcast(heat, ChatColor.RED + "  Velocidade, cegueira, levitação, explosão...");
        broadcast(heat, ChatColor.GREEN + "  Sobreviva ao CAOS!");
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
            broadcast(heat, ChatColor.RED + "  💥 CAOS TOTAL — Rodada " + chaosRound + " 💥");
            broadcast(heat, ChatColor.LIGHT_PURPLE + "═══════════════════════════════");

            // Aplica efeito aleatório para CADA piloto
            for (Driver driver : heat.getDrivers().values()) {
                if (driver.isFinished() || driver.isDnf()) continue;

                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player == null || !player.isOnline()) continue;

                // Sorteia 1-3 efeitos para este piloto
                int numEffects = 1 + random.nextInt(3);
                List<ChaosEffect> applied = new ArrayList<>();

                for (int i = 0; i < numEffects; i++) {
                    ChaosEffect effect = ChaosEffect.values()[random.nextInt(ChaosEffect.values().length)];
                    applied.add(effect);

                    // Aplica o efeito
                    player.addPotionEffect(new PotionEffect(effect.effect, effect.duration, effect.amplifier, false, false));

                    // Efeitos especiais
                    applySpecialEffect(player, effect);
                }

                // Mensagem para o piloto
                StringBuilder msg = new StringBuilder(ChatColor.YELLOW + "Efeitos: ");
                for (int i = 0; i < applied.size(); i++) {
                    if (i > 0) msg.append(ChatColor.GRAY + ", ");
                    msg.append(ChatColor.RED).append(applied.get(i).displayName);
                }
                player.sendMessage(msg.toString());
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 0.5f + random.nextFloat());
            }

            // Efeito global especial a cada 3 rodadas
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
                // Congela o jogador no lugar
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
                // Troca de posições entre dois pilotos aleatórios
                broadcast(heat, ChatColor.LIGHT_PURPLE + "🌀 TROCA DE POSIÇÕES!");
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
                        p1.sendMessage(ChatColor.RED + "🌀 Você trocou de posição com " + p2.getName() + "!");
                        p2.sendMessage(ChatColor.RED + "🌀 Você trocou de posição com " + p1.getName() + "!");
                    }
                }
            }
            case 1 -> {
                // Todos recebem velocidade extrema por 3 segundos
                broadcast(heat, ChatColor.YELLOW + "⚡ TURBO COLETIVO!");
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
                // Todos ficam lentos por 3 segundos
                broadcast(heat, ChatColor.BLUE + "🐌 DESACELERAÇÃO COLETIVA!");
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
                // Troca de barcos (se possível)
                broadcast(heat, ChatColor.GREEN + "🔄 MISTURA DE BARCOS!");
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
