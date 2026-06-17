package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

public class PTPManager {
    private final FormulaRacing plugin;
    private final Map<UUID, BossBar> ptpBars = new HashMap();

    public PTPManager(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public void startPTPTask(final Heats heat) {
        SchedulerHelper.runTaskTimer(this.plugin, (scheduledTask) -> {
            if (heat.getHeatState() != HeatState.RACING) {
                for(Driver driver : heat.getDrivers().values()) {
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        PTPManager.this.disablePTP(player, driver);
                    } else {
                        driver.setPtpActive(false);
                        driver.setPtpEnergy(0.0);
                    }
                }

                PTPManager.this.clearAllBars();
                scheduledTask.cancel();
            } else {
                for(Driver driver : heat.getDrivers().values()) {
                    if (driver.isFinished() || driver.isDnf()) {
                        Player finishedPlayer = Bukkit.getPlayer(driver.getUuid());
                        if (finishedPlayer != null && finishedPlayer.isOnline()) {
                            PTPManager.this.disablePTP(finishedPlayer, driver);
                        } else {
                            driver.setPtpActive(false);
                            driver.setPtpEnergy(0.0);
                        }
                        continue;
                    }

                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        PTPManager.this.updatePTP(player, driver, heat);
                    }
                }

            }
        }, 0L, 2L);
    }

    private void updatePTP(Player player, Driver driver, Heats heat) {
        FRTheme theme = FRThemeResolver.resolveTheme(player);
        String ptpPrefix = plugin.getTranslation("ptp_title_prefix", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()));
        BossBar bar = (BossBar)this.ptpBars.computeIfAbsent(player.getUniqueId(), (id) -> {
            String rawTitle = ptpPrefix + "0%";
            String title = LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawTitle, theme));
            return Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SOLID, new BarFlag[0]);
        });
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
        String rawTitle = driver.isPtpActive() ? ptpPrefix + (int)energy + "% ⚡" : ptpPrefix + (int)energy + "%";
        bar.setTitle(LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawTitle, theme)));
        BarColor newColor;
        if (driver.isPtpActive()) {
            newColor = BarColor.RED;
        } else if (energy >= 67) {
            newColor = BarColor.GREEN;
        } else if (energy >= 33) {
            newColor = BarColor.YELLOW;
        } else {
            newColor = BarColor.RED;
        }
        bar.setColor(newColor);
    }

    public void togglePTP(Player player, Driver driver, Heats heat) {
        if (driver.isFinished() || driver.isDnf()) {
            this.disablePTP(player, driver);
            return;
        }

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

    public void disablePTP(Player player, Driver driver) {
        driver.setPtpActive(false);
        driver.setPtpEnergy((double)0.0F);
        this.applyPtpPacket(player, 0.04F);
        BossBar bar = (BossBar)this.ptpBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
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
