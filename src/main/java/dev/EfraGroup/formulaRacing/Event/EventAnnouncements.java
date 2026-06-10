//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Event;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.QualificationManager;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.Text;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import dev.EfraGroup.formulaRacing.Utils.TranslationUtil;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class EventAnnouncements {
    private final FormulaRacing plugin;
    private final TranslationUtil t;

    public EventAnnouncements(FormulaRacing plugin) {
        this.plugin = plugin;
        this.t = plugin.getTranslationUtil();
    }

    public void broadcastToHeat(Heats heat, String key, String... placeholders) {
        for(Driver driver : heat.getDrivers().values()) {
            Player p = Bukkit.getPlayer(driver.getUuid());
            if (p != null && p.isOnline()) {
                this.t.sendTranslated(p, key, placeholders);
            }
        }

        if (this.plugin.getSpectatorManager() != null) {
            Events event = heat.getRound() != null ? heat.getRound().getEvent() : null;
            if (event != null) {
                for(UUID specId : this.plugin.getSpectatorManager().getSpectatorsInEvent(event.getId())) {
                    Player p = Bukkit.getPlayer(specId);
                    if (p != null && p.isOnline()) {
                        this.t.sendTranslated(p, key, placeholders);
                    }
                }
            }
        }

    }

    public void broadcastToEvent(Events event, String key, String... placeholders) {
        for(UUID uuid : event.getSubscribers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                this.t.sendTranslated(p, key, placeholders);
            }
        }

        if (this.plugin.getSpectatorManager() != null) {
            for(UUID specId : this.plugin.getSpectatorManager().getSpectatorsInEvent(event.getId())) {
                Player p = Bukkit.getPlayer(specId);
                if (p != null && p.isOnline()) {
                    this.t.sendTranslated(p, key, placeholders);
                }
            }
        }

    }

    public void broadcastRawMessage(Events event, String message) {
        if (event.getSubscribers() != null) {
            for(UUID uuid : event.getSubscribers().keySet()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(message);
                }
            }
        }

        if (this.plugin.getSpectatorManager() != null) {
            for(UUID specId : this.plugin.getSpectatorManager().getSpectatorsInEvent(event.getId())) {
                Player p = Bukkit.getPlayer(specId);
                if (p != null && p.isOnline()) {
                    p.sendMessage(message);
                }
            }
        }

    }

    public void broadcastFinish(Heats heat, Driver driver, String formattedTime) {
        String name = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
        this.broadcastToHeat(heat, "event_broadcast_finish", "{player}", name, "{pos}", String.valueOf(driver.getPosition()), "{time}", formattedTime);
        Player p = Bukkit.getPlayer(driver.getUuid());
        if (p != null) {
            int position = Math.max(1, driver.getPosition());
            String title = this.t.getTranslated(p, "event_finish_title", new String[0]);
            String subtitleBase = this.t.getTranslated(p, "event_finish_subtitle", new String[]{"{pos}", String.valueOf(position)});
            if (title == null || title.isBlank()) {
                title = "&s&lFINALIZOU!";
            }
            if (subtitleBase == null || subtitleBase.isBlank()) {
                subtitleBase = "&fPosição " + position;
            }

            String subtitle = subtitleBase + " &8| &e" + formattedTime;
            if (position == 1) {
                title = "&6&l[P1] " + title;
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.4F);
            } else if (position == 2) {
                title = "&7&l[P2] " + title;
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
            } else if (position == 3) {
                title = "&6&l[P3] " + title;
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            } else {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }

            p.resetTitle();
            TitleHelper.sendThemedTitle(p, title, subtitle, 5, 80, 15);
        }

    }

    public void broadcastDNF(Heats heat, Driver driver, String reason) {
        String name = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
        this.broadcastToHeat(heat, "event_broadcast_dnf", "{player}", name, "{reason}", reason != null ? reason : "");
        Player p = Bukkit.getPlayer(driver.getUuid());
        if (p != null) {
            String title = this.t.getTranslated(p, "event_dnf_title", new String[0]);
            String subtitle = this.t.getTranslated(p, "event_dnf_subtitle", new String[0]);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            TitleHelper.sendThemedTitle(p, title, subtitle, 10, 70, 20);
        }

    }

    public void broadcastFastestLap(Heats heat, Driver driver, String formattedTime) {
        String name = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
        this.broadcastToHeat(heat, "event_broadcast_fastest_lap", "{player}", name, "{time}", formattedTime);
        Player p = Bukkit.getPlayer(driver.getUuid());
        if (p != null) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.5F);
        }

    }

    public void broadcastLapTime(Heats heat, Driver driver, String formattedTime, String delta) {
        String name = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
        String deltaToken;
        if (delta != null && !delta.isEmpty()) {
            if (delta.startsWith("-")) {
                deltaToken = "&s";
            } else {
                deltaToken = "&e";
            }
        } else {
            deltaToken = "&7";
            delta = "(-)";
        }

        String message = String.format("&w%s &7[Volta %d] &f%s %s%s", name, driver.getLapCount(), formattedTime, deltaToken, delta);

        for(Driver d : heat.getDrivers().values()) {
            Player p = Bukkit.getPlayer(d.getUuid());
            if (p != null && p.isOnline()) {
                FRTheme theme = FRThemeResolver.resolveTheme(p);
                Component comp = FRThemeParser.parseWithLegacy(message, theme);
                String legacy = LegacyComponentSerializer.legacySection().serialize(comp);
                p.sendMessage(legacy);
            }
        }

        // Also send to spectators watching this event
        if (this.plugin.getSpectatorManager() != null) {
            Events event = heat.getRound() != null ? heat.getRound().getEvent() : null;
            if (event != null) {
                for(UUID specId : this.plugin.getSpectatorManager().getSpectatorsInEvent(event.getId())) {
                    Player p = Bukkit.getPlayer(specId);
                    if (p != null && p.isOnline()) {
                        FRTheme theme = FRThemeResolver.resolveTheme(p);
                        Component comp = FRThemeParser.parseWithLegacy(message, theme);
                        String legacy = LegacyComponentSerializer.legacySection().serialize(comp);
                        p.sendMessage(legacy);
                    }
                }
            }
        }

    }

    public void broadcastPitEntry(Heats heat, Driver driver) {
        String name = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
        this.broadcastToHeat(heat, "event_broadcast_pit_entry", "{player}", name);
    }

    public void broadcastPitExit(Heats heat, Driver driver) {
        String name = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
        this.broadcastToHeat(heat, "event_broadcast_pit_exit", "{player}", name);
    }

    public void broadcastPitStop(Heats heat, Driver driver, int totalPits, String formattedDuration) {
        String name = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
        this.broadcastToHeat(heat, "event_broadcast_pit_stop", "{player}", name, "{pits}", String.valueOf(totalPits), "{time}", formattedDuration);
    }

    public void broadcastEventStart(Events event) {
        this.broadcastToEvent(event, "event_broadcast_started", "{event}", event.getDisplayName());
    }

    public void broadcastEventFinish(Events event) {
        this.broadcastToEvent(event, "event_broadcast_finished", "{event}", event.getDisplayName());
    }

    public void broadcastAdminFinish(Heats heat) {
        this.broadcastToHeat(heat, "event_broadcast_admin_finish");
    }

    public void broadcastHeatComplete(Heats heat) {
        this.broadcastToHeat(heat, "event_broadcast_heat_complete");
    }

    public void broadcastRaceStarting(Heats heat) {
        this.broadcastToHeat(heat, "event_broadcast_race_starting");
    }

    public void broadcastDriverJoin(Heats heat, String playerName, int current, int max) {
        this.broadcastToHeat(heat, "event_broadcast_driver_joined", "{player}", playerName, "{current}", String.valueOf(current), "{max}", String.valueOf(max));
    }

    public void broadcastDriverLeave(Heats heat, String playerName, int current, int max) {
        this.broadcastToHeat(heat, "event_broadcast_driver_left", "{player}", playerName, "{current}", String.valueOf(current), "{max}", String.valueOf(max));
    }

    public void broadcastQualificationResults(Events event, List<QualificationManager.QualificationResult> results) {
        this.broadcastToEvent(event, "event_broadcast_quali_results_header");

        for(int i = 0; i < Math.min(results.size(), 10); ++i) {
            QualificationManager.QualificationResult result = (QualificationManager.QualificationResult)results.get(i);
            String name = Bukkit.getOfflinePlayer(result.getDriverUUID()).getName();
            String time = this.formatTime(result.getBestLapTime());
            this.broadcastToEvent(event, "event_broadcast_quali_result_line", "{pos}", String.valueOf(i + 1), "{player}", name, "{time}", time);
        }

    }

    public void broadcastFinalStandings(Heats heat, List<Driver> results, HeatState previousState) {
        this.broadcastToHeat(heat, "event_broadcast_standings_header");

        for(Driver d : results) {
            String name = Bukkit.getOfflinePlayer(d.getUuid()).getName();
            String info = "";
            if (heat.isElimination()) {
                info = d.getEndTime() != null && d.getStartTime() != null
                    ? this.formatTime(d.getEndTime() - d.getStartTime())
                    : (d.getStartTime() != null ? this.formatTime(System.currentTimeMillis() - d.getStartTime()) : "---");
            } else if (previousState != HeatState.QUALIFYING && previousState != HeatState.PRACTICE) {
                if (d.getLapCount() < heat.getTotalLaps()) {
                    int var10000 = d.getLapCount();
                    info = "(" + var10000 + "/" + heat.getTotalLaps() + ")";
                } else if (d.getEndTime() != null && d.getStartTime() != null) {
                    info = this.formatTime(d.getEndTime() - d.getStartTime());
                } else {
                    info = "FIM";
                }
            } else {
                info = d.getFastestLap() != null ? this.formatTime(d.getFastestLap().getLapTime()) : "---";
            }

            this.broadcastToHeat(heat, "event_broadcast_standings_line", "{pos}", String.valueOf(d.getPosition()), "{player}", name, "{info}", info);
        }

        this.broadcastToHeat(heat, "event_broadcast_standings_footer");

        for(Driver d : heat.getDrivers().values()) {
            Player p = Bukkit.getPlayer(d.getUuid());
            if (p != null && p.isOnline()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1.0F);
            }
        }

    }

    public void broadcastSessionWarning(Heats heat, long seconds) {
        this.broadcastToHeat(heat, "event_broadcast_session_warning", "{time}", String.valueOf(seconds));

        for(Driver d : heat.getDrivers().values()) {
            Player p = Bukkit.getPlayer(d.getUuid());
            if (p != null && p.isOnline()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.0F);
            }
        }

    }

    public void broadcastSessionExpired(Heats heat, boolean canFinishLap) {
        if (canFinishLap) {
            this.broadcastToHeat(heat, "event_broadcast_session_expired_lap");

            for(Driver d : heat.getDrivers().values()) {
                Player p = Bukkit.getPlayer(d.getUuid());
                if (p != null && p.isOnline()) {
                    String title = this.t.getTranslated(p, "event_session_expired_title", new String[0]);
                    String subtitle = this.t.getTranslated(p, "event_session_expired_subtitle", new String[0]);
                    TitleHelper.sendThemedTitle(p, title, subtitle, 10, 40, 10);
                }
            }
        } else {
            this.broadcastToHeat(heat, "event_broadcast_session_expired");
        }

    }

    public void broadcastSessionCancelled(Heats heat) {
        this.broadcastToHeat(heat, "event_broadcast_session_cancelled");

        for(Driver d : heat.getDrivers().values()) {
            Player p = Bukkit.getPlayer(d.getUuid());
            if (p != null && p.isOnline()) {
                String title = this.t.getTranslated(p, "event_session_cancelled_title", new String[0]);
                TitleHelper.sendThemedTitle(p, title, "", 10, 60, 20);
            }
        }

    }

    public void broadcastPitStopPenalty(Heats heat, Driver driver, int missingPits) {
        String name = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
        this.broadcastToHeat(heat, "event_broadcast_pit_penalty", "{player}", name, "{missing}", String.valueOf(missingPits));
    }

    private String formatTime(long timeMs) {
        if (timeMs > 0L && timeMs != Long.MAX_VALUE) {
            long minutes = timeMs / 60000L;
            long seconds = timeMs % 60000L / 1000L;
            long millis = timeMs % 1000L;
            return minutes > 0L ? String.format("%d:%02d.%03d", minutes, seconds, millis) : String.format("%d.%03d", seconds, millis);
        } else {
            return "N/A";
        }
    }
}
