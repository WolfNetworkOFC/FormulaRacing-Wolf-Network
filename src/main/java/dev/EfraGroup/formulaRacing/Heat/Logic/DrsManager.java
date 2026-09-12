package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

public class DrsManager {
    private final RaceSession rs;
    private final FormulaRacing plugin;
    private final PacketSender ps;
    // Track DRS task per heat to allow proper cleanup
    private volatile boolean taskRunning = false;

    public DrsManager(RaceSession rs, FormulaRacing plugin, PacketSender ps) {
        this.rs = rs;
        this.plugin = plugin;
        this.ps = ps;
    }

    /**
     * Creates a BossBar for a driver. Must be called on main thread.
     */
    private void createBarForDriver(Driver driver, Player player) {
        if (driver.getDrsBossBar() == null) {
            BossBar bar = Bukkit.createBossBar("§9§lDRS", BarColor.BLUE, BarStyle.SOLID, new BarFlag[0]);
            bar.addPlayer(player);
            driver.setDrsBossBar(bar);
        } else if (!driver.getDrsBossBar().getPlayers().contains(player)) {
            driver.getDrsBossBar().addPlayer(player);
        }
    }

    /**
     * Safely destroys a driver's BossBar. Must be called on main thread.
     */
    private void destroyBossBar(Driver driver) {
        BossBar bar = driver.getDrsBossBar();
        if (bar != null) {
            bar.removeAll();
            driver.setDrsBossBar(null);
        }
    }

    public void startDrsTask(final Heats heat) {
        final List<Heats.DrsRegion> regions = heat.getPlugin().getRaceEventManager().getDatabaseManager().getDrsRegionsList(heat.getTrackNameWS());

        if (regions == null || regions.isEmpty()) {
            this.plugin.getLogger().info("§e[DRS-Debug] Nenhuma região DRS configurada para: §f" + heat.getTrackNameWS());
            return;
        }

        final boolean hasFinishRegion = regions.stream().anyMatch(r -> r.getType().equalsIgnoreCase("end"));

        this.plugin.getLogger().info("§e[DRS-Debug] Task started. Processing " + regions.size() + " regions for: §f" + heat.getTrackNameWS());

        taskRunning = true;

        // Create BossBars on main thread (required for Folia/Bukkit)
        SchedulerHelper.runTask(plugin, () -> {
            for (Driver driver : heat.getDrivers().values()) {
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    createBarForDriver(driver, player);
                }
            }
        });

        // DRS detection/activation loop - runs on global scheduler
        SchedulerHelper.runTaskTimer(heat.getPlugin(), (scheduledTask) -> {
            if (!taskRunning) {
                scheduledTask.cancel();
                return;
            }

            // Stop if heat is not RACING or DRS is disabled
            if (heat.getHeatState() != HeatState.RACING || !heat.isDrsEnabled()) {
                // Clean up all BossBars on main thread
                SchedulerHelper.runTask(plugin, () -> {
                    for (Driver d : heat.getDrivers().values()) {
                        destroyBossBar(d);
                    }
                });
                taskRunning = false;
                scheduledTask.cancel();
                return;
            }

            // Snapshot drivers to avoid concurrent modification
            List<Driver> driversSnapshot;
            try {
                driversSnapshot = List.copyOf(heat.getDrivers().values());
            } catch (Exception e) {
                // Heat drivers map was modified, skip this tick
                return;
            }

            for (Driver driver : driversSnapshot) {
                // Synchronize on driver to prevent race conditions
                synchronized (driver) {
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player == null || !player.isOnline()) continue;

                    // Get location on the player's region thread (thread-safe on Folia);
                    // dispatch the rest of the logic back there too to avoid cross-region reads.
                    final Driver finalDriver = driver;
                    SchedulerHelper.runTaskFor(plugin, player, () -> {
                        Player freshPlayer = Bukkit.getPlayer(finalDriver.getUuid());
                        if (freshPlayer == null || !freshPlayer.isOnline()) return;

                        Location loc = freshPlayer.getLocation();
                        if (loc == null || loc.getWorld() == null) return;

                        FRTheme theme = FRThemeResolver.resolveTheme(freshPlayer);

                        for (Heats.DrsRegion region : regions) {
                            String type = region.getType();

                            switch (type) {
                                case "detect" -> {
                                    if (rs.isInside(loc, region.getMin(), region.getMax())) {
                                        if (!finalDriver.hasDrsPermission() && !finalDriver.isDrsActive()) {
                                            Driver target = rs.getDriverAhead(finalDriver, heat);

                                            if (target != null) {
                                                double gapValue = rs.calculateGap(finalDriver, target, heat);
                                                if (gapValue >= 0.01 && gapValue <= 1.3) {
                                                    finalDriver.setDrsPermission(true);
                                                    showDrsAvailableBar(freshPlayer, finalDriver);
                                                    sendThemedMessage(freshPlayer, theme, "&a[DRS] Permission granted! Gap: &f" + String.format("%.3f", gapValue) + "s");
                                                }
                                            } else if (freshPlayer.getTicksLived() % 40 == 0) {
                                                sendThemedMessage(freshPlayer, theme, "&a[DRS] In detection zone, but no target ahead.");
                                            }
                                        }
                                    }
                                }

                                case "drs" -> {
                                    if (finalDriver.hasDrsPermission() && !finalDriver.isDrsActive()) {
                                        if (rs.isInside(loc, region.getMin(), region.getMax())) {
                                            finalDriver.setDrsPermission(false);
                                            applyDrsBoost(freshPlayer, heat, finalDriver, hasFinishRegion);
                                            sendThemedMessage(freshPlayer, theme, "&a[DRS] Wing Open!");
                                        }
                                    }
                                }

                                case "end" -> {
                                    if (finalDriver.isDrsActive()) {
                                        if (rs.isInside(loc, region.getMin(), region.getMax())) {
                                            stopDrsBoost(freshPlayer, finalDriver, heat);
                                            sendThemedMessage(freshPlayer, theme, "&c[DRS] Wing Closed.");
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            }
        }, 0L, 2L);
    }

    /**
     * Stops the DRS task and cleans up all BossBars. Must be called on main thread or dispatches to it.
     */
    public void stopDrsTask(Heats heat) {
        taskRunning = false;
        SchedulerHelper.runTask(plugin, () -> {
            if (heat != null && heat.getDrivers() != null) {
                for (Driver d : heat.getDrivers().values()) {
                    destroyBossBar(d);
                }
            }
        });
    }

    private void sendThemedMessage(Player player, FRTheme theme, String rawMsg) {
        String themed = LegacyComponentSerializer.legacySection()
                .serialize(FRThemeParser.parseWithLegacy(rawMsg, theme));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(themed));
    }

    private void showDrsAvailableBar(Player player, Driver driver) {
        // Destroy old bar first to prevent leak
        if (driver.getDrsBossBar() != null) {
            driver.getDrsBossBar().removeAll();
            driver.setDrsBossBar(null);
        }

        FRTheme theme = FRThemeResolver.resolveTheme(player);
        String rawTitle = plugin.getDirectTranslation("drs_available", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()));
        BossBar bar = Bukkit.createBossBar(LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawTitle, theme)), BarColor.BLUE, BarStyle.SOLID, new BarFlag[0]);
        bar.addPlayer(player);
        driver.setDrsBossBar(bar);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 2.0F);
    }

    public void applyDrsBoost(Player player, Heats heat, Driver driver, boolean useRegion) {
        if (heat.getPlugin().getPacketSender() != null) {
            // BossBar update on main thread
            if (driver.getDrsBossBar() != null) {
                FRTheme theme = FRThemeResolver.resolveTheme(player);
                String title = LegacyComponentSerializer.legacySection().serialize(
                    FRThemeParser.parseWithLegacy(plugin.getTranslation("drs_activated", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId())), theme));
                driver.getDrsBossBar().setTitle(title);
                driver.getDrsBossBar().setColor(BarColor.GREEN);
            }

            driver.setDrsActive(true);
            float drsPower = (float)heat.getDrsdownpower();
            heat.getPlugin().getPacketSender().sendBoatSetting(player, 11, new Object[]{drsPower});
            player.sendMessage(plugin.getTranslation("drs_activated", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId())));
            if (!useRegion) {
                SchedulerHelper.runTaskLater(heat.getPlugin(), () -> {
                    if (player.isOnline()) {
                        this.stopDrsBoost(player, driver, heat);
                    }
                }, 140L);
            }
        }
    }

    public void stopDrsBoost(Player player, Driver driver, Heats heat) {
        // Synchronize to prevent race conditions with detection
        synchronized (driver) {
            if (!driver.isDrsActive()) return; // Already stopped

            heat.getPlugin().getPacketSender().sendBoatSetting(player, 11, new Object[]{0.04F});
            driver.setDrsActive(false);
            driver.setDrsPermission(false);

            // Destroy BossBar properly
            if (driver.getDrsBossBar() != null) {
                driver.getDrsBossBar().removeAll();
                driver.setDrsBossBar(null);
            }

            player.sendMessage(plugin.getTranslation("drs_finished", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId())));
        }
    }
}

