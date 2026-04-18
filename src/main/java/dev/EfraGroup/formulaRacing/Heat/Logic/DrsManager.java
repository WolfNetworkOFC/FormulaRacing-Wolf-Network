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
        // Carrega as regiões do banco (já filtradas por trackName)
        final Map<String, Location> regions = heat.getDrsRegions();

        // Verifica se as regiões essenciais existem para evitar erros de Null no Loop
        final boolean hasDetect = regions.containsKey("detectMin") && regions.containsKey("detectMax");
        final boolean hasStart = regions.containsKey("startMin") && regions.containsKey("startMax");
        final boolean hasFinish = regions.containsKey("finishMin") && regions.containsKey("finishMax");

        this.plugin.getLogger().info("§e[DRS-Debug] Task iniciada para a pista: §f" + heat.getTrackNameWS());

        new BukkitRunnable() {
            @Override
            public void run() {
                // Se a corrida acabou ou não está em estado de RACING, limpa as barras e cancela
                if (heat.getHeatState() != HeatState.RACING) {
                    heat.getDrivers().values().forEach(d -> {
                        if (d.getDrsBossBar() != null) d.getDrsBossBar().removeAll();
                    });
                    this.cancel();
                    return;
                }

                for (Driver driver : heat.getDrivers().values()) {
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player == null || !player.isOnline()) continue;

                    Location loc = player.getLocation();
                    FRTheme theme = FRThemeResolver.resolveTheme(player);

                    // 1. LÓGICA DE DETECÇÃO (Trigger de Permissão)
                    if (hasDetect && DrsManager.this.rs.isInside(loc, regions.get("detectMin"), regions.get("detectMax"))) {
                        // Só checa se ele já não tiver permissão ou DRS ativo para não spammar cálculo
                        if (!driver.hasDrsPermission() && !driver.isDrsActive()) {
                            Driver target = DrsManager.this.rs.getDriverAhead(driver, heat);

                            if (target != null) {
                                double gapValue = DrsManager.this.rs.calculateGap(driver, target, heat);

                                // Regra clássica: gap entre 0.1 e 1.0 (ajustado para 1.3 por latência)
                                if (gapValue >= 0.01 && gapValue <= 1.3) {
                                    driver.setDrsPermission(true);
                                    DrsManager.this.showDrsAvailableBar(player, driver);

                                    String msg = "&a[DRS] Permissão concedida! Gap: &f" + String.format("%.3f", gapValue) + "s";
                                    sendThemedMessage(player, theme, msg);
                                }
                            } else if (player.getTicksLived() % 40 == 0) { // Mensagem de aviso a cada 2 segundos
                                sendThemedMessage(player, theme, "&2[DRS] Na zona de detecção, mas sem alvo à frente.");
                            }
                        }
                    }

                    // 2. LÓGICA DE ATIVAÇÃO (Início do Boost)
                    if (hasStart && driver.hasDrsPermission() && !driver.isDrsActive()) {
                        if (DrsManager.this.rs.isInside(loc, regions.get("startMin"), regions.get("startMax"))) {
                            driver.setDrsPermission(false); // Consome a permissão
                            DrsManager.this.applyDrsBoost(player, heat, driver, hasFinish);
                            sendThemedMessage(player, theme, "&a[DRS] Asa Aberta! Ativando ERS/Boost...");
                        }
                    }

                    // 3. LÓGICA DE DESATIVAÇÃO (Fim da Reta)
                    if (hasFinish && driver.isDrsActive()) {
                        if (DrsManager.this.rs.isInside(loc, regions.get("finishMin"), regions.get("finishMax"))) {
                            DrsManager.this.stopDrsBoost(player, driver, heat);
                            sendThemedMessage(player, theme, "&c[DRS] Asa Fechada.");
                        }
                    }
                }
            }
        }.runTaskTimer(heat.getPlugin(), 0L, 2L);
    }

    // Método auxiliar para limpar o código de mensagens repetitivas
    private void sendThemedMessage(Player player, FRTheme theme, String rawMsg) {
        String themed = LegacyComponentSerializer.legacySection()
                .serialize(FRThemeParser.parseWithLegacy(rawMsg, theme));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(themed));
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
