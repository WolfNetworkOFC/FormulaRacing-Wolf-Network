package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import java.time.Instant;
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
    private final Map<UUID, PtpData> playerData = new HashMap<>();
    private final Map<UUID, Long> toggleCooldowns = new HashMap<>();
    private static final long TOGGLE_COOLDOWN_MS = 500;
    private static final int MAX_USE_TIME_MS = 5000;
    private static final int FULL_CHARGE_TIME_MS = 15000;

    public PTPManager(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public void startPTPTask(final Heats heat) {
        SchedulerHelper.runTaskTimer(this.plugin, (scheduledTask) -> {
            if (heat.getHeatState() != HeatState.RACING) {
                for (Driver driver : heat.getDrivers().values()) {
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        disablePTP(player, driver);
                    } else {
                        driver.setPtpActive(false);
                        driver.setPtpEnergy(0.0);
                    }
                }
                clearAll();
                scheduledTask.cancel();
            } else {
                for (Driver driver : heat.getDrivers().values()) {
                    if (driver.isFinished() || driver.isDnf()) {
                        Player finishedPlayer = Bukkit.getPlayer(driver.getUuid());
                        if (finishedPlayer != null && finishedPlayer.isOnline()) {
                            disablePTP(finishedPlayer, driver);
                        } else {
                            driver.setPtpActive(false);
                            driver.setPtpEnergy(0.0);
                        }
                        continue;
                    }

                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        updatePTP(player, driver);
                    }
                }
            }
        }, 0L, 2L);
    }

    private void updatePTP(Player player, Driver driver) {
        UUID uuid = player.getUniqueId();
        PtpData data = playerData.computeIfAbsent(uuid, k -> new PtpData(player));

        data.updateCharge(driver);
        data.syncDriver(driver);
        data.updateBossBar(player, driver);
    }

    public void togglePTP(Player player, Driver driver, Heats heat) {
        if (driver.isFinished() || driver.isDnf()) {
            disablePTP(player, driver);
            return;
        }

        UUID uuid = player.getUniqueId();

        Long lastToggle = toggleCooldowns.get(uuid);
        long now = System.currentTimeMillis();
        if (lastToggle != null && (now - lastToggle) < TOGGLE_COOLDOWN_MS) {
            return;
        }
        toggleCooldowns.put(uuid, now);

        PtpData data = playerData.computeIfAbsent(uuid, k -> new PtpData(player));
        data.updateCharge(driver);

        if (!driver.isPtpActive()) {
            if (driver.getPtpEnergy() <= 0) {
                return;
            }
            driver.setPtpActive(true);
            data.active = true;
            data.lastUpdate = Instant.now();
            applyPtpPacket(player, (float) heat.getpushtopasspower());
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 2.0F);
        } else {
            driver.setPtpActive(false);
            data.active = false;
            data.lastUpdate = Instant.now();
            applyPtpPacket(player, 0.04F);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.5F);
        }
        data.updateBossBar(player, driver);
    }

    public void disablePTP(Player player, Driver driver) {
        driver.setPtpActive(false);
        driver.setPtpEnergy(0.0);
        applyPtpPacket(player, 0.04F);

        PtpData data = playerData.remove(player.getUniqueId());
        if (data != null) {
            data.bossBar.removeAll();
            data.chargePercent = 0;
            data.active = false;
        }
    }

    public void handlePitEntry(Player player, Driver driver) {
        if (driver.isPtpActive()) {
            driver.setPtpActive(false);
            applyPtpPacket(player, 0.04F);

            PtpData data = playerData.get(player.getUniqueId());
            if (data != null) {
                data.active = false;
                data.lastUpdate = Instant.now();
                data.updateBossBar(player, driver);
            }
        }
    }

    public void removePlayer(UUID uuid) {
        PtpData data = playerData.remove(uuid);
        toggleCooldowns.remove(uuid);
        if (data != null) {
            data.bossBar.removeAll();
        }
    }

    private void applyPtpPacket(Player player, float power) {
        if (this.plugin.getPacketSender() != null) {
            this.plugin.getPacketSender().sendBoatSetting(player, 11, new Object[]{power});
        }
    }

    private void clearAll() {
        playerData.values().forEach(d -> d.bossBar.removeAll());
        playerData.clear();
        toggleCooldowns.clear();
    }

    private class PtpData {
        private double chargePercent;
        private boolean active;
        private Instant lastUpdate;
        private BossBar bossBar;

        PtpData(Player player) {
            this.chargePercent = 0;
            this.active = false;
            this.lastUpdate = Instant.now();
            String prefix = plugin.getTranslation("ptp_title_prefix", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()));
            String rawTitle = prefix + "0%";
            FRTheme theme = FRThemeResolver.resolveTheme(player);
            String title = LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawTitle, theme));
            this.bossBar = Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SOLID, new BarFlag[0]);
            this.bossBar.setProgress(0.0);
            this.bossBar.addPlayer(player);
        }

        void updateCharge(Driver driver) {
            Instant now = Instant.now();
            long elapsed = now.toEpochMilli() - lastUpdate.toEpochMilli();
            if (elapsed <= 0) {
                return;
            }

            if (driver.isPtpActive()) {
                double drainRate = 100.0 / MAX_USE_TIME_MS;
                chargePercent -= drainRate * elapsed;
                if (chargePercent < 0) {
                    chargePercent = 0;
                }
            } else {
                double chargeRate = 100.0 / FULL_CHARGE_TIME_MS;
                double oldCharge = chargePercent;
                chargePercent += chargeRate * elapsed;
                if (chargePercent > 100) {
                    chargePercent = 100;
                }
                if (oldCharge < 100 && chargePercent >= 100) {
                    for (Player p : bossBar.getPlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.5F);
                    }
                }
            }

            lastUpdate = now;
            active = driver.isPtpActive();
        }

        void syncDriver(Driver driver) {
            driver.setPtpEnergy(chargePercent);
            driver.setPtpActive(active);
        }

        void updateBossBar(Player player, Driver driver) {
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, chargePercent / 100.0)));

            FRTheme theme = FRThemeResolver.resolveTheme(player);
            String prefix = plugin.getTranslation("ptp_title_prefix", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()));

            String rawTitle;
            if (active) {
                rawTitle = prefix + (int) chargePercent + "% ⚡";
                bossBar.setColor(BarColor.PINK);
            } else {
                rawTitle = prefix + (int) chargePercent + "%";
                if (chargePercent >= 67) {
                    bossBar.setColor(BarColor.GREEN);
                } else if (chargePercent >= 33) {
                    bossBar.setColor(BarColor.YELLOW);
                } else {
                    bossBar.setColor(BarColor.RED);
                }
            }
            bossBar.setTitle(LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawTitle, theme)));
        }
    }
}
