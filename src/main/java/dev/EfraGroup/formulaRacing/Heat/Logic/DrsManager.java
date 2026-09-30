package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DRS (Drag Reduction System) rebuilt on the TimingSystem (FrostHex) model.
 *
 * <p>The previous implementation compared a driver against whoever sat one
 * position ahead and granted permission on a gap window. This one works on
 * <em>passing times through the detection region</em>, which is what makes the
 * DRS chaining rule possible: being right behind a car that itself was granted
 * DRS keeps the next one eligible.</p>
 *
 * <ul>
 *   <li>{@code detect} — records the instant the driver crossed the region.
 *       Compares it against every other driver's crossing in the same region.</li>
 *   <li>{@code drs}    — consumes the armed permission and applies the boost.</li>
 *   <li>{@code end}    — closes the boost, restoring the track's own forward
 *       acceleration instead of the hard-coded vanilla 0.04.</li>
 * </ul>
 *
 * <p>Entries older than {@code maxDelta * 2} are pruned so a region map never
 * grows unbounded on a long event.</p>
 */
public class DrsManager {
    /** OpenBoatUtils SET_FORWARD_ACCELERATION. */
    private static final short PACKET_ID_SET_FORWARD_ACCELERATION = 11;
    private static final float VANILLA_FORWARD_ACCEL = 0.04f;

    private final RaceSession rs;
    private final FormulaRacing plugin;
    private final PacketSender ps;

    /** Players armed with DRS (permission granted, boost not yet applied). */
    private static final Map<UUID, Long> drsEnabledPlayers = new ConcurrentHashMap<>();
    /** regionId -> driver uuid -> instant they crossed the detect region. */
    private static final Map<Integer, Map<UUID, Long>> drsDetectRegionPasses = new ConcurrentHashMap<>();
    /** regionId -> driver uuid -> instant they were granted DRS in that region. */
    private static final Map<Integer, Map<UUID, Long>> drsEnabledInRegion = new ConcurrentHashMap<>();
    /** Players with the boost currently applied, mapped to their close-out task. */
    private static final Map<UUID, Long> activeDrsPlayers = new ConcurrentHashMap<>();
    /** Forward acceleration to restore when the boost ends. */
    private static final Map<UUID, Float> preDrsForwardAccel = new ConcurrentHashMap<>();

    private volatile boolean taskRunning = false;

    public DrsManager(RaceSession rs, FormulaRacing plugin, PacketSender ps) {
        this.rs = rs;
        this.plugin = plugin;
        this.ps = ps;
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    private int getDrsMinDelta() {
        return this.plugin.getConfig().getInt("drs.min-delta", 650);
    }

    private int getDrsMaxDelta() {
        return this.plugin.getConfig().getInt("drs.max-delta", 1150);
    }

    private int getDrsDuration() {
        return this.plugin.getConfig().getInt("drs.duration", 2000);
    }

    private double getDrsForwardAccel() {
        return this.plugin.getConfig().getDouble("drs.forward-accel", 0.06);
    }

    // ------------------------------------------------------------------
    // BossBar helpers
    // ------------------------------------------------------------------

    private void createBarForDriver(Driver driver, Player player) {
        if (driver.getDrsBossBar() == null) {
            BossBar bar = Bukkit.createBossBar("§9§lDRS", BarColor.BLUE, BarStyle.SOLID, new BarFlag[0]);
            bar.addPlayer(player);
            driver.setDrsBossBar(bar);
        } else if (!driver.getDrsBossBar().getPlayers().contains(player)) {
            driver.getDrsBossBar().addPlayer(player);
        }
    }

    private void destroyBossBar(Driver driver) {
        BossBar bar = driver.getDrsBossBar();
        if (bar != null) {
            bar.removeAll();
            driver.setDrsBossBar(null);
        }
    }

    // ------------------------------------------------------------------
    // Boost lifecycle
    // ------------------------------------------------------------------

    /**
     * Applies the DRS forward acceleration and schedules its removal after
     * {@code drs.duration} milliseconds.
     */
    public void applyDrsBoost(Player player, Heats heat, Driver driver) {
        if (player == null || !player.isOnline() || this.ps == null) {
            return;
        }
        if (isDrsActive(player.getUniqueId())) {
            return;
        }

        // Remember what the track itself asked for, so the boost can be undone
        // without assuming the value is vanilla.
        float originalAccel = getTrackForwardAccel(heat);
        preDrsForwardAccel.put(player.getUniqueId(), originalAccel);

        float boost = (float) Math.max(getDrsForwardAccel(), heat.getDrsdownpower());
        this.ps.sendBoatSetting(player, PACKET_ID_SET_FORWARD_ACCELERATION, new Object[]{boost});

        driver.setDrsActive(true);
        driver.setDrsPermission(false);
        disableDrs(player.getUniqueId());

        if (driver.getDrsBossBar() != null) {
            FRTheme theme = FRThemeResolver.resolveTheme(player);
            String title = LegacyComponentSerializer.legacySection().serialize(
                FRThemeParser.parseWithLegacy(
                    this.plugin.getTranslation("drs_activated",
                        this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId())), theme));
            driver.getDrsBossBar().setTitle(title);
            driver.getDrsBossBar().setColor(BarColor.GREEN);
        }

        player.sendMessage(this.plugin.getTranslation("drs_activated",
            this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId())));
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6F, 2.0F);

        int ticks = Math.max(1, getDrsDuration() / 50);
        SchedulerHelper.runTaskLater(this.plugin, () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online != null && online.isOnline()) {
                stopDrsBoost(online, driver, heat);
            }
        }, ticks);
    }

    /**
     * Removes the boost and restores the track's own forward acceleration.
     */
    public void stopDrsBoost(Player player, Driver driver, Heats heat) {
        if (player == null || !player.isOnline()) {
            return;
        }
        synchronized (driver) {
            if (!driver.isDrsActive()) {
                // Still drop the bookkeeping: a heat that ended mid-boost must
                // not leave a scheduled task or a stale accel behind.
                preDrsForwardAccel.remove(player.getUniqueId());
                activeDrsPlayers.remove(player.getUniqueId());
                return;
            }

            Float originalAccel = preDrsForwardAccel.remove(player.getUniqueId());
            activeDrsPlayers.remove(player.getUniqueId());

            if (originalAccel != null && this.ps != null) {
                this.ps.sendBoatSetting(player, PACKET_ID_SET_FORWARD_ACCELERATION, new Object[]{originalAccel});
            } else {
                resetToTrackSettings(player, heat);
            }

            driver.setDrsActive(false);
            driver.setDrsPermission(false);
            disableDrs(player.getUniqueId());

            destroyBossBar(driver);

            player.sendMessage(this.plugin.getTranslation("drs_finished",
                this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId())));
        }
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    /**
     * Called when a driver crosses a {@code detect} region.
     *
     * <p>Grants DRS when another driver crossed the same region between
     * {@code min-delta} and {@code max-delta} milliseconds ago. Chaining: if the
     * gap is smaller than {@code min-delta} but that other driver was itself
     * granted DRS within {@code max-delta}, this driver is eligible too.</p>
     */
    public void playerPassedDrsDetect(Player player, Driver driver, int regionId) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();

        Map<UUID, Long> regionPasses =
            drsDetectRegionPasses.computeIfAbsent(regionId, k -> new ConcurrentHashMap<>());
        Map<UUID, Long> enabledTimes =
            drsEnabledInRegion.computeIfAbsent(regionId, k -> new ConcurrentHashMap<>());

        int minDelta = getDrsMinDelta();
        int maxDelta = getDrsMaxDelta();

        boolean shouldEnableDrs = false;
        long closestDiff = Long.MAX_VALUE;

        for (Map.Entry<UUID, Long> entry : regionPasses.entrySet()) {
            UUID otherId = entry.getKey();
            if (otherId.equals(playerId)) {
                continue;
            }
            long diff = now - entry.getValue();

            if (diff >= minDelta && diff <= maxDelta) {
                shouldEnableDrs = true;
                closestDiff = Math.min(closestDiff, diff);
            } else if (diff > 0 && diff < minDelta) {
                // Chaining: the car in front is inside min-delta but was itself
                // granted DRS recently, so we chain onto its eligibility.
                Long otherEnabledAt = enabledTimes.get(otherId);
                if (otherEnabledAt != null && (now - otherEnabledAt) <= maxDelta) {
                    shouldEnableDrs = true;
                    closestDiff = Math.min(closestDiff, diff);
                }
            }
        }

        regionPasses.put(playerId, now);

        if (shouldEnableDrs && !driver.isDrsActive()) {
            enableDrs(playerId);
            enabledTimes.put(playerId, now);
            driver.setDrsPermission(true);

            if (driver.getDrsBossBar() == null) {
                createBarForDriver(driver, player);
            } else {
                driver.getDrsBossBar().setColor(BarColor.WHITE);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 2.0F);

            FRTheme theme = FRThemeResolver.resolveTheme(player);
            sendThemedMessage(player, theme, "&a[DRS] Available &7(gap &f" + closestDiff + "ms&7)");
        }
    }

    private void enableDrs(UUID playerId) {
        drsEnabledPlayers.putIfAbsent(playerId, System.currentTimeMillis());
    }

    private void disableDrs(UUID playerId) {
        drsEnabledPlayers.remove(playerId);
    }

    public boolean hasDrsEnabled(UUID playerId) {
        return drsEnabledPlayers.containsKey(playerId);
    }

    public boolean isDrsActive(UUID playerId) {
        return activeDrsPlayers.containsKey(playerId) || drsEnabledPlayers.containsKey(playerId);
    }

    // ------------------------------------------------------------------
    // Main loop
    // ------------------------------------------------------------------

    public void startDrsTask(final Heats heat) {
        final List<Heats.DrsRegion> regions =
            heat.getPlugin().getRaceEventManager().getDatabaseManager()
                .getDrsRegionsList(heat.getTrackNameWS());

        if (regions == null || regions.isEmpty()) {
            this.plugin.getLogger().info("§e[DRS] Nenhuma região DRS configurada para: §f"
                + heat.getTrackNameWS());
            return;
        }

        taskRunning = true;

        // Prune stale crossings periodically: a driver who fell out of the
        // heat, or a long event running many heats, would otherwise keep the
        // per-region maps growing forever. Runs off the global region so it
        // never blocks on an entity thread.
        SchedulerHelper.runAsyncTimer(this.plugin, () -> cleanupOldDetections(), 100L, 100L);

        SchedulerHelper.runTask(this.plugin, () -> {
            for (Driver driver : heat.getDrivers().values()) {
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    createBarForDriver(driver, player);
                }
            }
        });

        SchedulerHelper.runTaskTimer(heat.getPlugin(), (scheduledTask) -> {
            if (!taskRunning) {
                scheduledTask.cancel();
                return;
            }

            if (heat.getHeatState() != HeatState.RACING || !heat.isDrsEnabled()) {
                SchedulerHelper.runTask(this.plugin, () -> {
                    for (Driver d : heat.getDrivers().values()) {
                        Player online = Bukkit.getPlayer(d.getUuid());
                        if (online != null && online.isOnline()) {
                            // A heat that ends mid-boost must not strand the
                            // player at the boosted acceleration.
                            if (d.isDrsActive()) {
                                stopDrsBoost(online, d, heat);
                            } else {
                                destroyBossBar(d);
                            }
                        }
                    }
                });
                taskRunning = false;
                scheduledTask.cancel();
                return;
            }

            List<Driver> driversSnapshot;
            try {
                driversSnapshot = List.copyOf(heat.getDrivers().values());
            } catch (Exception e) {
                return;
            }

            for (Driver driver : driversSnapshot) {
                synchronized (driver) {
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player == null || !player.isOnline()) continue;

                    final Driver finalDriver = driver;
                    SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                        Player freshPlayer = Bukkit.getPlayer(finalDriver.getUuid());
                        if (freshPlayer == null || !freshPlayer.isOnline()) return;

                        Location loc = freshPlayer.getLocation();
                        if (loc == null || loc.getWorld() == null) return;

                        for (Heats.DrsRegion region : regions) {
                            String type = region.getType();
                            boolean inside = rs.isInside(loc, region.getMin(), region.getMax());

                            switch (type) {
                                case "detect" -> {
                                    if (inside && !finalDriver.hasDrsPermission()
                                            && !finalDriver.isDrsActive()) {
                                        playerPassedDrsDetect(freshPlayer, finalDriver, region.getId());
                                    }
                                }
                                case "drs" -> {
                                    if (inside && finalDriver.hasDrsPermission()
                                            && !finalDriver.isDrsActive()) {
                                        applyDrsBoost(freshPlayer, heat, finalDriver);
                                        FRTheme theme = FRThemeResolver.resolveTheme(freshPlayer);
                                        sendThemedMessage(freshPlayer, theme, "&a[DRS] Wing Open!");
                                    }
                                }
                                case "end" -> {
                                    if (inside && finalDriver.isDrsActive()) {
                                        stopDrsBoost(freshPlayer, finalDriver, heat);
                                        FRTheme theme = FRThemeResolver.resolveTheme(freshPlayer);
                                        sendThemedMessage(freshPlayer, theme, "&c[DRS] Wing Closed.");
                                    }
                                }
                                default -> {
                                }
                            }
                        }
                    });
                }
            }
        }, 0L, 2L);
    }

    /**
     * Stops the loop, restores any live boost and clears the boss bars.
     */
    public void stopDrsTask(Heats heat) {
        taskRunning = false;
        SchedulerHelper.runTask(this.plugin, () -> {
            if (heat != null && heat.getDrivers() != null) {
                for (Driver d : heat.getDrivers().values()) {
                    Player online = Bukkit.getPlayer(d.getUuid());
                    if (online != null && online.isOnline() && d.isDrsActive()) {
                        stopDrsBoost(online, d, heat);
                    }
                    destroyBossBar(d);
                }
            }
        });
    }

    /** Drops all state for a driver (quit, DQ, heat finish). */
    public void cleanupPlayer(UUID playerId) {
        drsEnabledPlayers.remove(playerId);
        preDrsForwardAccel.remove(playerId);
        activeDrsPlayers.remove(playerId);
        for (Map<UUID, Long> passes : drsDetectRegionPasses.values()) {
            passes.remove(playerId);
        }
        for (Map<UUID, Long> enabled : drsEnabledInRegion.values()) {
            enabled.remove(playerId);
        }
    }

    /**
     * Prunes crossings older than {@code max-delta * 2}. Run periodically so the
     * per-region maps do not grow across a long event.
     */
    public void cleanupOldDetections() {
        long cutoff = System.currentTimeMillis() - ((long) getDrsMaxDelta() * 2L);

        for (Map<UUID, Long> passes : drsDetectRegionPasses.values()) {
            passes.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        for (Map<UUID, Long> enabled : drsEnabledInRegion.values()) {
            enabled.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
    }

    // ------------------------------------------------------------------
    // Packet helpers
    // ------------------------------------------------------------------

    /**
     * Reads the forward acceleration configured for the track, falling back to
     * the vanilla value when the track has no boatutils row.
     */
    private float getTrackForwardAccel(Heats heat) {
        try {
            Map<String, Object> data = this.plugin.getDatabaseManager()
                .getBoatUtilsRaw(heat.getTrackNameWS());
            if (data != null) {
                Object raw = data.get("forwardAcceleration");
                if (raw instanceof Number n) {
                    return n.floatValue();
                }
            }
        } catch (Throwable ignored) {
        }
        return VANILLA_FORWARD_ACCEL;
    }

    /**
     * Re-applies the full boatutils settings for the track, which restores the
     * track's own forward acceleration as a side effect.
     */
    private void resetToTrackSettings(Player player, Heats heat) {
        if (this.ps != null && this.plugin.getPacketSender() != null) {
            this.plugin.getPacketSender().applyBoatUtilsToPlayer(player, heat.getTrackNameWS());
        }
    }

    private void sendThemedMessage(Player player, FRTheme theme, String rawMsg) {
        String themed = LegacyComponentSerializer.legacySection()
                .serialize(FRThemeParser.parseWithLegacy(rawMsg, theme));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(themed));
    }
}
