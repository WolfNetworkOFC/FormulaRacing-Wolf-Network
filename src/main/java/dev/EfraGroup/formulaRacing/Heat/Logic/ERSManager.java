package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

public class ERSManager {
    private final FormulaRacing plugin;
    private final Map<UUID, BossBar> ersBars = new HashMap<>();

    public ERSManager(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public void startERSTask(final Heats heat) {
        SchedulerHelper.runTaskTimer(this.plugin, (scheduledTask) -> {
            if (heat.getHeatState() != HeatState.RACING) {
                ERSManager.this.clearAllBars();
                scheduledTask.cancel();
            } else {
                for (Driver driver : heat.getDrivers().values()) {
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        ERSManager.this.updateERS(player, driver, heat);
                    } else {
                        // Limpeza automática se o player deslogar
                        ERSManager.this.removePlayer(driver.getUuid());
                    }
                }
            }
        }, 0L, 2L);
    }

    private void updateERS(Player player, Driver driver, Heats heat) {
        BossBar bar = this.ersBars.computeIfAbsent(player.getUniqueId(), (id) ->
                Bukkit.createBossBar("§7§lERS: 0%", BarColor.WHITE, BarStyle.SOLID, new BarFlag[0]));

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        double energy = driver.getErsEnergy();
        String mode = driver.getErsMode(); // "Disabled", "Recharging", "Deploy"

        if (mode.equalsIgnoreCase("Deploy")) {
            energy -= 0.6; // Gasto de bateria no modo Deploy
            if (energy <= 0.0) {
                energy = 0.0;
                driver.setErsMode("Disabled");
                this.applyErsPacket(player, 0.04F);
            }
        } else if (mode.equalsIgnoreCase("Recharging")) {
            energy += 0.4; // Recuperação rápida
            if (energy > 100.0) energy = 100.0;
        } else {
            // Disabled - Recuperação passiva lenta
            energy += 0.05;
            if (energy > 100.0) energy = 100.0;
        }

        driver.setErsEnergy(energy);
        bar.setProgress(energy / 100.0);
        this.updateBarAppearance(bar, mode, (int)energy);
    }

    private void updateBarAppearance(BossBar bar, String mode, int energy) {
        switch (mode.toUpperCase()) {
            case "DEPLOY":
                bar.setTitle("§b§lERS DEPLOY: " + energy + "% ⚡");
                bar.setColor(BarColor.BLUE);
                break;
            case "RECHARGING":
                bar.setTitle("§a§lERS RECHARGING: " + energy + "% ↻");
                bar.setColor(BarColor.GREEN);
                break;
            default:
                bar.setTitle("§7§lERS DISABLED: " + energy + "%");
                bar.setColor(BarColor.WHITE);
                break;
        }
    }

    public void cycleERSMode(Player player, Driver driver, Heats heat) {
        String currentMode = driver.getErsMode();

        if (currentMode.equalsIgnoreCase("Disabled")) {
            driver.setErsMode("Recharging");
            this.applyErsPacket(player, 0.037F); // Simula o "arrasto" da recarga
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0F, 1.0F);
        } else if (currentMode.equalsIgnoreCase("Recharging")) {
            if (driver.getErsEnergy() > 5.0) {
                driver.setErsMode("Deploy");
                this.applyErsPacket(player, (float)0.047F);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 2.0F);
            } else {
                driver.setErsMode("Disabled");
                this.applyErsPacket(player, 0.04F);
            }
        } else {
            driver.setErsMode("Disabled");
            this.applyErsPacket(player, 0.04F);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 2.0F);
        }
    }

    public void removePlayer(UUID uuid) {
        BossBar bar = this.ersBars.get(uuid);
        if (bar != null) {
            bar.removeAll();
            this.ersBars.remove(uuid);
        }

        // Resetar velocidade para o padrão se o player estiver online
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            this.applyErsPacket(player, 0.04F);
        }
    }

    private void applyErsPacket(Player player, float power) {
        if (this.plugin.getPacketSender() != null) {
            this.plugin.getPacketSender().sendBoatSetting(player, 11, new Object[]{power});
        }
    }

    public void clearAllBars() {
        this.ersBars.values().forEach(BossBar::removeAll);
        this.ersBars.clear();
    }
}