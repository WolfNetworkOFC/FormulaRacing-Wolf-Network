//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

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
import java.util.Map;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DrsManager {
    private final RaceSession rs;
    private final FormulaRacing plugin;
    private final PacketSender ps;

    public DrsManager(RaceSession rs, FormulaRacing plugin, PacketSender ps) {
        this.rs = rs;
        this.plugin = plugin;
        this.ps = ps;
    }

    public void startDrsTask(final Heats heat) {
        final Map<String, Location> regions = heat.getDrsRegions();
        final boolean hasFinishRegion = regions.containsKey("finishMin") && regions.get("finishMin") != null;
        this.plugin.getLogger().info("§e[DRS-Debug] Iniciando Task. Regioes carregadas: " + String.valueOf(regions.keySet()));
        (new BukkitRunnable() {
            public void run() {
                if (heat.getHeatState() != HeatState.RACING) {
                    heat.getDrivers().values().forEach((d) -> {
                        if (d.getDrsBossBar() != null) {
                            d.getDrsBossBar().removeAll();
                        }

                    });
                    this.cancel();
                } else {
                    for(Driver driver : heat.getDrivers().values()) {
                        Player player = Bukkit.getPlayer(driver.getUuid());
                        if (player != null && player.isOnline()) {
                            Location loc = player.getLocation();
                            if (DrsManager.this.rs.isInside(loc, (Location)regions.get("detectMin"), (Location)regions.get("detectMax")) && !driver.hasDrsPermission() && !driver.isDrsActive()) {
                                Driver target = DrsManager.this.rs.getDriverAhead(driver, heat);
                                if (target == null) {
                                    if (player.getTicksLived() % 20 == 0) {
                                        FRTheme theme = FRThemeResolver.resolveTheme(player);
                                        String rawMsg = "&2[DRS] Na zona de deteccao, mas sem piloto a frente.";
                                        String themed = LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawMsg, theme));
                                        player.spigot().sendMessage(ChatMessageType.CHAT, new TextComponent(themed));
                                    }
                                } else {
                                    double gapValue = DrsManager.this.rs.calculateGap(driver, target, heat);
                                    if (gapValue >= 0.1 && gapValue <= 1.3) {
                                        driver.setDrsPermission(true);
                                        DrsManager.this.showDrsAvailableBar(player, driver);
                                        Object[] var10002 = new Object[]{gapValue};
                                        FRTheme theme = FRThemeResolver.resolveTheme(player);
                                        String rawMsg = "&s[DRS] Permissao concedida! Gap: " + String.format("%.3f", var10002);
                                        String themed = LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawMsg, theme));
                                        player.spigot().sendMessage(ChatMessageType.CHAT, new TextComponent(themed));
                                    }
                                }
                            }

                            if (DrsManager.this.rs.isInside(loc, (Location)regions.get("startMin"), (Location)regions.get("startMax")) && !driver.isDrsActive() && driver.hasDrsPermission()) {
                                driver.setDrsPermission(false);
                                DrsManager.this.applyDrsBoost(player, heat, driver, hasFinishRegion);
                            }

                            if (driver.isDrsActive() && hasFinishRegion && DrsManager.this.rs.isInside(loc, (Location)regions.get("finishMin"), (Location)regions.get("finishMax"))) {
                                DrsManager.this.stopDrsBoost(player, driver, heat);
                            }
                        }
                    }

                }
            }
        }).runTaskTimer(heat.getPlugin(), 0L, 2L);
    }

    private void showDrsAvailableBar(Player player, Driver driver) {
        if (driver.getDrsBossBar() != null) {
            driver.getDrsBossBar().removeAll();
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
                Bukkit.getScheduler().runTaskLater(heat.getPlugin(), () -> {
                    if (player.isOnline()) {
                        this.stopDrsBoost(player, driver, heat);
                    }

                }, 140L);
            }

        }
    }

    public void stopDrsBoost(Player player, Driver driver, Heats heat) {
        heat.getPlugin().getPacketSender().sendBoatSetting(player, 11, new Object[]{0.04F});
        driver.setDrsActive(false);
        if (driver.getDrsBossBar() != null) {
            driver.getDrsBossBar().removeAll();
            driver.setDrsBossBar(null);
        }

        player.sendMessage(plugin.getTranslation("drs_finished", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId())));
    }
}
