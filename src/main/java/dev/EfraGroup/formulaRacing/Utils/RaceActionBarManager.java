/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 *
 * Could not load the following classes:
 *  java.lang.MatchException
 *  net.md_5.bungee.api.ChatMessageType
 *  net.md_5.bungee.api.chat.BaseComponent
 *  net.md_5.bungee.api.chat.TextComponent
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 *  org.bukkit.scheduler.BukkitTask
 */
package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class RaceActionBarManager {
    private final FormulaRacing plugin;
    private final Map<UUID, Heats> playerHeats;
    private BukkitTask updateTask;
    private static final int UPDATE_INTERVAL_TICKS = 5;

    public RaceActionBarManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.playerHeats = new HashMap<UUID, Heats>();
        this.startAutoUpdate();
    }

    private void startAutoUpdate() {
        this.updateTask = new BukkitRunnable(){

            public void run() {
                for (Map.Entry<UUID, Heats> entry : RaceActionBarManager.this.playerHeats.entrySet()) {
                    Player player = Bukkit.getPlayer((UUID)entry.getKey());
                    if (player == null || !player.isOnline()) continue;
                    RaceActionBarManager.this.updateActionBar(player, entry.getValue());
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 5L);
    }

    public void addPlayer(Player player, Heats heat) {
        this.removePlayer(player);
        this.playerHeats.put(player.getUniqueId(), heat);
        this.updateActionBar(player, heat);
        if (this.updateTask == null || this.updateTask.isCancelled()) {
            this.plugin.getDebugManager().logRaceSystem("[ACTION BAR DEBUG] ERRO: Task n\u00e3o est\u00e1 ativo ao adicionar jogador " + player.getName() + "!");
        }
    }

    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        Heats heat = this.playerHeats.remove(player.getUniqueId());
        if (heat != null) {
            this.plugin.getDebugManager().logRaceSystem("[ActionBar] Removendo jogador " + player.getName() + " do heat " + heat.getId());
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
    }

    public void removeHeat(Heats heat) {
        for (Driver driver : heat.getDrivers().values()) {
            Heats currentHeat;
            Player player = Bukkit.getPlayer((UUID)driver.getUuid());
            if (player == null || (currentHeat = this.playerHeats.get(player.getUniqueId())) == null || !currentHeat.equals(heat)) continue;
            this.removePlayer(player);
        }
    }

    public void stopAll() {
        if (this.updateTask != null && !this.updateTask.isCancelled()) {
            this.updateTask.cancel();
            this.plugin.getDebugManager().logRaceSystem("[ACTION BAR DEBUG] Task de atualiza\u00e7\u00e3o cancelado");
        }
        for (UUID uuid : new ArrayList<UUID>(this.playerHeats.keySet())) {
            Player player = Bukkit.getPlayer((UUID)uuid);
            if (player == null) continue;
            this.removePlayer(player);
        }
        this.playerHeats.clear();
    }

    private void updateActionBar(Player player, Heats heat) {
        Driver driver = heat.getDriver(player.getUniqueId());
        if (driver == null) {
            return;
        }
        String message = this.buildActionBarMessage(heat, driver);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(message));
    }

    private String buildActionBarMessage(Heats heat, Driver driver) {
        return switch (heat.getHeatState()) {
            default -> throw new MatchException(null, null);
            case HeatState.SETUP, HeatState.IDLE -> this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_preparing", new String[0]);
            case HeatState.PRACTICE -> this.buildPracticeMessage(heat, driver);
            case HeatState.QUALIFYING -> this.buildQualifyingMessage(heat, driver);
            case HeatState.LOADED -> this.buildLoadedMessage(heat, driver);
            case HeatState.STARTING -> this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_lights_out_visual", new String[0]);
            case HeatState.RACING -> this.buildRacingMessage(heat, driver);
            case HeatState.FINISHED -> this.buildFinishedMessage(driver);
        };
    }

    private String buildPracticeMessage(Heats heat, Driver driver) {
        UUID creatorUuid;
        StringBuilder sb = new StringBuilder();
        long remaining = -1L;
        if (this.plugin.getDailyRaceManager() != null && heat.getRound() != null && heat.getRound().getEvent() != null && (creatorUuid = heat.getRound().getEvent().getCreatorUUID()) != null && creatorUuid.getMostSignificantBits() == 0L && creatorUuid.getLeastSignificantBits() == 0L) {
            remaining = this.plugin.getDailyRaceManager().getPracticeTimeRemaining();
        }
        if (remaining < 0L) {
            remaining = heat.getSessionTimeRemaining();
        }
        if (remaining >= 0L) {
            sb.append("\u00a7c\u23f1 ").append(this.formatTimeShort(remaining));
        }
        int currentLap = driver.getLapCount() + 1;
        if (sb.length() > 0) {
            sb.append(" \u00a78| ");
        }
        sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_practice_lap", "{lap}", String.valueOf(currentLap)));
        if (driver.getCurrentLap() != null) {
            long lapElapsed = System.currentTimeMillis() - driver.getCurrentLap().getStartTime();
            sb.append(" \u00a78| \u00a7e\u23f1 ").append(this.formatLapTime(lapElapsed));
            String delta = driver.getCachedDelta();
            if (delta != null && !delta.isEmpty()) {
                sb.append(delta);
            }
        } else {
            sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_cross_line", new String[0]));
        }
        return sb.toString();
    }

    private String buildQualifyingMessage(Heats heat, Driver driver) {
        StringBuilder sb = new StringBuilder();
        long remaining = heat.getSessionTimeRemaining();
        if (remaining >= 0L) {
            sb.append("\u00a7c\u23f1 ").append(this.formatTimeShort(remaining));
        }
        int position = driver.getPosition();
        if (sb.length() > 0) {
            sb.append(" \u00a78| ");
        }
        sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_qualifying_pos", "{pos}", String.valueOf(position)));
        int currentLap = driver.getLapCount() + 1;
        sb.append(" \u00a78| ").append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_practice_lap", "{lap}", String.valueOf(currentLap)));
        if (driver.getCurrentLap() != null) {
            long lapElapsed = System.currentTimeMillis() - driver.getCurrentLap().getStartTime();
            sb.append(" \u00a78| \u00a7e\u23f1 ").append(this.formatLapTime(lapElapsed));
            String delta = driver.getCachedDelta();
            if (delta != null && !delta.isEmpty()) {
                sb.append(delta);
            }
        } else {
            sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_cross_line", new String[0]));
        }
        return sb.toString();
    }

    private String formatLapTime(long timeMs) {
        long minutes = timeMs / 60000L;
        long seconds = timeMs % 60000L / 1000L;
        long millis = timeMs % 1000L;
        if (minutes > 0L) {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
        }
        return String.format("%d.%03d", seconds, millis);
    }

    private String buildLoadedMessage(Heats heat, Driver driver) {
        StringBuilder sb = new StringBuilder();
        sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_grid_pos", "{pos}", String.valueOf(driver.getStartPosition())));
        if (heat.getTotalLaps() > 0) {
            sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_info_laps", "{laps}", driver.getLaps().size() + "/" + heat.getTotalLaps()));
        }
        if (heat.getTotalPits() > 0) {
            sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_info_pits", "{pits}", driver.getPitstops() + "/" + heat.getTotalPits()));
        }
        sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_prepare_start", new String[0]));
        return sb.toString();
    }

    private String buildRacingMessage(Heats heat, Driver driver) {
        if (driver.isFinished()) {
            return this.buildFinishedMessage(driver);
        }
        StringBuilder sb = new StringBuilder();
        int position = driver.getPosition();
        String posColor = this.getPositionColor(position);
        sb.append(posColor).append("P").append(position);
        int totalLaps = heat.getTotalLaps();
        int currentLap = driver.getCurrentLap() == null ? 0 : Math.min(totalLaps, driver.getLapCount() + 1);
        sb.append(" \u00a78| ").append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_practice_lap", "{lap}", currentLap + "\u00a77/\u00a7f" + totalLaps));
        int totalCheckpoints = this.plugin.getTrackIntegrationManager().getTrackCheckpoints(heat.getTrackNameWS()).size();
        double lapsCompleted = driver.getLapCount();
        double currentLapProgress = 0.0;
        if (totalCheckpoints > 0 && driver.getCurrentLap() != null) {
            int checkpointsInCurrentLap = driver.getCheckpointsReached();
            checkpointsInCurrentLap = Math.min(checkpointsInCurrentLap, totalCheckpoints);
            currentLapProgress = (double)checkpointsInCurrentLap / (double)totalCheckpoints;
        }
        double totalProgress = (lapsCompleted + currentLapProgress) / (double)totalLaps;
        totalProgress = Math.max(0.0, Math.min(1.0, totalProgress));
        sb.append(" ").append(this.buildProgressBar(totalProgress, 10));
        if (driver.getCurrentLap() != null) {
            long lapElapsed = System.currentTimeMillis() - driver.getCurrentLap().getStartTime();
            sb.append(" \u00a78| \u00a7e\u23f1 ").append(this.formatLapTime(lapElapsed));
            String delta = driver.getCachedDelta();
            if (delta != null && !delta.isEmpty()) {
                sb.append(delta);
            }
        }
        if (heat.getTotalPits() > 0) {
            int pitsRemaining = heat.getTotalPits() - driver.getPitstops();
            if (pitsRemaining > 0) {
                sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_info_pits", "{pits}", String.valueOf(pitsRemaining)));
            } else {
                sb.append(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_info_pits", "{pits}", "\u00a7a\u2713"));
            }
        }
        long elapsed = 0L;
        if (heat.getStartTime() != null) {
            elapsed = System.currentTimeMillis() - heat.getStartTime().toEpochMilli();
        }
        sb.append(" \u00a78| \u00a77").append(this.formatTimeShort(elapsed));
        return sb.toString();
    }

    private String buildFinishedMessage(Driver driver) {
        if (driver.isFinished()) {
            String posColor = this.getPositionColor(driver.getPosition());
            return String.format(this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_finished_title", new String[0]) + "%sP%d \u00a78| \u00a77%s", posColor, driver.getPosition(), this.formatTimeShort(driver.getTotalTime()));
        }
        if (driver.isDnf()) {
            return this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_dnf_title", new String[0]);
        }
        return this.plugin.getTranslationUtil().getTranslated(Bukkit.getPlayer((UUID)driver.getUuid()), "actionbar_race_finished", new String[0]);
    }

    private String getPositionColor(int position) {
        return switch (position) {
            case 1 -> "\u00a7a";
            case 2 -> "\u00a7e";
            case 3 -> "\u00a76";
            default -> "\u00a7f";
        };
    }

    private String buildProgressBar(double progress, int length) {
        int filled = (int)(progress * (double)length);
        StringBuilder bar = new StringBuilder("\u00a77[");
        for (int i = 0; i < length; ++i) {
            if (i < filled) {
                if (progress < 0.33) {
                    bar.append("\u00a7a\u258c");
                    continue;
                }
                if (progress < 0.66) {
                    bar.append("\u00a7e\u258c");
                    continue;
                }
                bar.append("\u00a7c\u258c");
                continue;
            }
            bar.append("\u00a78\u258c");
        }
        bar.append("\u00a77]");
        return bar.toString();
    }

    private String formatTimeShort(long timeMs) {
        long minutes = timeMs / 60000L;
        long seconds = timeMs % 60000L / 1000L;
        if (minutes > 0L) {
            return String.format("%d:%02d", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    public void shutdown() {
        if (this.updateTask != null) {
            this.updateTask.cancel();
        }
        for (UUID playerId : this.playerHeats.keySet()) {
            Player player = Bukkit.getPlayer((UUID)playerId);
            if (player == null || !player.isOnline()) continue;
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
        }
        this.playerHeats.clear();
    }
}
