//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

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
import org.bukkit.scheduler.BukkitRunnable;

public class PTPManager {
    private final FormulaRacing plugin;
    private final Map<UUID, BossBar> ptpBars = new HashMap();

    public PTPManager(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public void startPTPTask(final Heats heat) {
        (new BukkitRunnable() {
            public void run() {
                if (heat.getHeatState() != HeatState.RACING) {
                    PTPManager.this.clearAllBars();
                    this.cancel();
                } else {
                    for(Driver driver : heat.getDrivers().values()) {
                        Player player = Bukkit.getPlayer(driver.getUuid());
                        if (player != null && player.isOnline()) {
                            PTPManager.this.updatePTP(player, driver, heat);
                        }
                    }

                }
            }
        }).runTaskTimer(this.plugin, 0L, 2L);
    }

    private void updatePTP(Player player, Driver driver, Heats heat) {
        BossBar bar = (BossBar)this.ptpBars.computeIfAbsent(player.getUniqueId(), (id) -> Bukkit.createBossBar("§6§lPush To Pass: 0%", BarColor.YELLOW, BarStyle.SOLID, new BarFlag[0]));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        double energy = driver.getPtpEnergy();
        if (driver.isPtpActive()) {
            energy -= 0.7;
            if (energy <= (double)0.0F) {
                energy = (double)0.0F;
                this.togglePTP(player, driver, heat);
            }
        } else if (energy < (double)100.0F) {
            energy += 0.2;
            if (energy > (double)100.0F) {
                energy = (double)100.0F;
            }
        }

        driver.setPtpEnergy(energy);
        bar.setProgress(energy / (double)100.0F);
        bar.setTitle(driver.isPtpActive() ? "§6§lPush To Pass: " + (int)energy + "% ⚡" : "§6§lPush To Pass: " + (int)energy + "%");
        bar.setColor(driver.isPtpActive() ? BarColor.RED : BarColor.YELLOW);
    }

    public void togglePTP(Player player, Driver driver, Heats heat) {
        if (!driver.isPtpActive()) {
            driver.setPtpActive(true);
            this.applyPtpPacket(player, (float)heat.getpushtopasspower());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 2.0F);
        } else {
            driver.setPtpActive(false);
            this.applyPtpPacket(player, 0.04F);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 2.0F);
        }

    }

    private void applyPtpPacket(Player player, float power) {
        if (this.plugin.getPacketSender() != null) {
            this.plugin.getPacketSender().sendBoatSetting(player, 11, new Object[]{power});
        }

    }

    private void clearAllBars() {
        this.ptpBars.values().forEach(BossBar::removeAll);
        this.ptpBars.clear();
    }
}
