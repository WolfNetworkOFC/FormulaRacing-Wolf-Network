package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class RaceActionBarManager {
    private final FormulaRacing plugin;
    private final Map<UUID, Heats> playerHeats;
    private final Map<UUID, SpectatorTarget> spectatorTargets;
    private final Map<UUID, String> lastActionBarMessage;
    private final Map<UUID, Long> lastStaticUpdateAt;
    private final Map<String, Integer> trackCheckpointCountCache;
    private final int dynamicUpdateIntervalTicks;
    private final long staticUpdateIntervalMs;
    private final int progressBarLength;
    private final String progressStartColor;
    private final String progressMiddleColor;
    private final String progressEndColor;
    private final String progressEmptyColor;
    private final String progressBracketColor;
    private BukkitTask updateTask;

    public RaceActionBarManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.playerHeats = new HashMap<>();
        this.spectatorTargets = new HashMap<>();
        this.lastActionBarMessage = new HashMap<>();
        this.lastStaticUpdateAt = new HashMap<>();
        this.trackCheckpointCountCache = new ConcurrentHashMap<>();
        this.dynamicUpdateIntervalTicks = Math.max(1, plugin.getConfig().getInt("race-actionbar.dynamic-update-interval-ticks", 2));
        this.staticUpdateIntervalMs = Math.max(50L, plugin.getConfig().getLong("race-actionbar.static-update-interval-ms", 250L));
        this.progressBarLength = Math.max(5, plugin.getConfig().getInt("race-actionbar.progress-bar.length", 10));
        this.progressStartColor = this.colorOrDefault(plugin.getConfig().getString("race-actionbar.progress-bar.start-color", "&c"), "\u00a7c");
        this.progressMiddleColor = this.colorOrDefault(plugin.getConfig().getString("race-actionbar.progress-bar.middle-color", "&e"), "\u00a7e");
        this.progressEndColor = this.colorOrDefault(plugin.getConfig().getString("race-actionbar.progress-bar.end-color", "&a"), "\u00a7a");
        this.progressEmptyColor = this.colorOrDefault(plugin.getConfig().getString("race-actionbar.progress-bar.empty-color", "&8"), "\u00a78");
        this.progressBracketColor = this.colorOrDefault(plugin.getConfig().getString("race-actionbar.progress-bar.bracket-color", "&7"), "\u00a77");
        this.startAutoUpdate();
    }

    private String colorOrDefault(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String translated = ChatColor.translateAlternateColorCodes('&', raw);
        return translated == null || translated.isBlank() ? fallback : translated;
    }

    private void startAutoUpdate() {
        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, Heats> entry : RaceActionBarManager.this.playerHeats.entrySet()) {
                    UUID playerId = entry.getKey();
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        continue;
                    }
                    Heats heat = entry.getValue();
                    if (RaceActionBarManager.this.shouldSkipUpdate(playerId, heat.getHeatState(), now)) {
                        continue;
                    }
                    RaceActionBarManager.this.updateDriverActionBar(player, heat);
                }

                for (Map.Entry<UUID, SpectatorTarget> entry : RaceActionBarManager.this.spectatorTargets.entrySet()) {
                    UUID spectatorId = entry.getKey();
                    Player spectator = Bukkit.getPlayer(spectatorId);
                    if (spectator == null || !spectator.isOnline()) {
                        RaceActionBarManager.this.clearSpectatorTarget(spectatorId);
                        continue;
                    }
                    SpectatorTarget target = entry.getValue();
                    if (target == null || target.heat == null || target.driverId == null) {
                        RaceActionBarManager.this.clearSpectatorTarget(spectator);
                        continue;
                    }
                    Driver driver = target.heat.getDriver(target.driverId);
                    if (driver == null) {
                        RaceActionBarManager.this.clearSpectatorTarget(spectator);
                        continue;
                    }
                    if (RaceActionBarManager.this.shouldSkipUpdate(spectatorId, target.heat.getHeatState(), now)) {
                        continue;
                    }
                    RaceActionBarManager.this.updateActionBarForDriver(spectator, target.heat, driver);
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, this.dynamicUpdateIntervalTicks);
    }

    private boolean shouldSkipUpdate(UUID playerId, HeatState state, long now) {
        if (this.isDynamicState(state)) {
            this.lastStaticUpdateAt.remove(playerId);
            return false;
        }
        Long lastUpdate = this.lastStaticUpdateAt.get(playerId);
        if (lastUpdate != null && now - lastUpdate < this.staticUpdateIntervalMs) {
            return true;
        }
        this.lastStaticUpdateAt.put(playerId, now);
        return false;
    }

    private boolean isDynamicState(HeatState state) {
        return state == HeatState.PRACTICE || state == HeatState.QUALIFYING || state == HeatState.RACING;
    }

    public void addPlayer(Player player, Heats heat) {
        this.removePlayer(player);
        this.playerHeats.put(player.getUniqueId(), heat);
        this.updateDriverActionBar(player, heat);
        if (this.updateTask == null || this.updateTask.isCancelled()) {
            this.plugin.getDebugManager().logRaceSystem("[ACTION BAR DEBUG] ERRO: Task n\u00e3o est\u00e1 ativo ao adicionar jogador " + player.getName() + "!");
        }
    }

    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Heats heat = this.playerHeats.remove(playerId);
        this.spectatorTargets.remove(playerId);
        this.lastActionBarMessage.remove(playerId);
        this.lastStaticUpdateAt.remove(playerId);
        if (heat != null) {
            this.plugin.getDebugManager().logRaceSystem("[ActionBar] Removendo jogador " + player.getName() + " do heat " + heat.getId());
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
    }

    public void setSpectatorTarget(Player spectator, Heats heat, UUID driverId) {
        if (spectator == null || heat == null || driverId == null || !spectator.isOnline()) {
            return;
        }
        this.spectatorTargets.put(spectator.getUniqueId(), new SpectatorTarget(heat, driverId));
        this.lastStaticUpdateAt.remove(spectator.getUniqueId());
        Driver targetDriver = heat.getDriver(driverId);
        if (targetDriver != null) {
            this.updateActionBarForDriver(spectator, heat, targetDriver);
        }
    }

    public void clearSpectatorTarget(Player spectator) {
        if (spectator == null) {
            return;
        }
        this.clearSpectatorTarget(spectator.getUniqueId());
        if (spectator.isOnline()) {
            spectator.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
        }
    }

    public void clearSpectatorTarget(UUID spectatorId) {
        if (spectatorId == null) {
            return;
        }
        this.spectatorTargets.remove(spectatorId);
        this.lastActionBarMessage.remove(spectatorId);
        this.lastStaticUpdateAt.remove(spectatorId);
    }

    public void removeHeat(Heats heat) {
        for (Driver driver : heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player == null) {
                continue;
            }
            Heats currentHeat = this.playerHeats.get(player.getUniqueId());
            if (currentHeat != null && currentHeat.equals(heat)) {
                this.removePlayer(player);
            }
        }
        this.spectatorTargets.entrySet().removeIf(entry -> {
            SpectatorTarget target = entry.getValue();
            if (target == null || target.heat == null || !target.heat.equals(heat)) {
                return false;
            }
            UUID spectatorId = entry.getKey();
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                spectator.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
            }
            this.clearSpectatorTarget(spectatorId);
            return true;
        });
    }

    public void stopAll() {
        if (this.updateTask != null && !this.updateTask.isCancelled()) {
            this.updateTask.cancel();
            this.plugin.getDebugManager().logRaceSystem("[ACTION BAR DEBUG] Task de atualiza\u00e7\u00e3o cancelado");
        }
        for (UUID uuid : new ArrayList<>(this.playerHeats.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                this.removePlayer(player);
            }
        }
        for (UUID spectatorId : new ArrayList<>(this.spectatorTargets.keySet())) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                spectator.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
            }
            this.clearSpectatorTarget(spectatorId);
        }
        this.playerHeats.clear();
        this.spectatorTargets.clear();
        this.lastActionBarMessage.clear();
        this.lastStaticUpdateAt.clear();
    }

    private void updateDriverActionBar(Player player, Heats heat) {
        Driver driver = heat.getDriver(player.getUniqueId());
        if (driver == null) {
            return;
        }
        this.updateActionBarForDriver(player, heat, driver);
    }

    private void updateActionBarForDriver(Player viewer, Heats heat, Driver driver) {
        String message = this.buildActionBarMessage(heat, driver, viewer);
        this.sendActionBarIfChanged(viewer, message);
    }

    private void sendActionBarIfChanged(Player player, String message) {
        String previous = this.lastActionBarMessage.get(player.getUniqueId());
        if (message.equals(previous)) {
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(message));
        this.lastActionBarMessage.put(player.getUniqueId(), message);
    }

    private String buildActionBarMessage(Heats heat, Driver driver, Player viewer) {
        return switch (heat.getHeatState()) {
            case SETUP, IDLE -> this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_preparing", new String[0]);
            case PRACTICE -> this.buildPracticeMessage(heat, driver, viewer);
            case QUALIFYING -> this.buildQualifyingMessage(heat, driver, viewer);
            case LOADED -> this.buildLoadedMessage(heat, driver, viewer);
            case STARTING -> this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_lights_out_visual", new String[0]);
            case RACING -> this.buildRacingMessage(heat, driver, viewer);
            case FINISHED -> this.buildFinishedMessage(driver, viewer);
        };
    }

    private String buildPracticeMessage(Heats heat, Driver driver, Player viewer) {
        UUID creatorUuid;
        StringBuilder sb = new StringBuilder();
        long remaining = -1L;
        if (this.plugin.getDailyRaceManager() != null
                && heat.getRound() != null
                && heat.getRound().getEvent() != null
                && (creatorUuid = heat.getRound().getEvent().getCreatorUUID()) != null
                && creatorUuid.getMostSignificantBits() == 0L
                && creatorUuid.getLeastSignificantBits() == 0L) {
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
        sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_practice_lap", "{lap}", String.valueOf(currentLap)));

        if (driver.getCurrentLap() != null) {
            long lapElapsed = System.currentTimeMillis() - driver.getCurrentLap().getStartTime();
            sb.append(" \u00a78| \u00a7e\u23f1 ").append(this.formatLapTime(lapElapsed));
            String delta = driver.getCachedDelta();
            if (delta != null && !delta.isEmpty()) {
                sb.append(delta);
            }
        } else {
            sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_cross_line", new String[0]));
        }
        return sb.toString();
    }

    private String buildQualifyingMessage(Heats heat, Driver driver, Player viewer) {
        StringBuilder sb = new StringBuilder();
        long remaining = heat.getSessionTimeRemaining();
        if (remaining >= 0L) {
            sb.append("\u00a7c\u23f1 ").append(this.formatTimeShort(remaining));
        }
        if (sb.length() > 0) {
            sb.append(" \u00a78| ");
        }
        sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_qualifying_pos", "{pos}", String.valueOf(driver.getPosition())));
        int currentLap = driver.getLapCount() + 1;
        sb.append(" \u00a78| ").append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_practice_lap", "{lap}", String.valueOf(currentLap)));

        if (driver.getCurrentLap() != null) {
            long lapElapsed = System.currentTimeMillis() - driver.getCurrentLap().getStartTime();
            sb.append(" \u00a78| \u00a7e\u23f1 ").append(this.formatLapTime(lapElapsed));
            String delta = driver.getCachedDelta();
            if (delta != null && !delta.isEmpty()) {
                sb.append(delta);
            }
        } else {
            sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_cross_line", new String[0]));
        }
        return sb.toString();
    }

    private String buildLoadedMessage(Heats heat, Driver driver, Player viewer) {
        StringBuilder sb = new StringBuilder();
        sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_grid_pos", "{pos}", String.valueOf(driver.getStartPosition())));
        if (heat.getTotalLaps() > 0) {
            sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_info_laps", "{laps}", driver.getLaps().size() + "/" + heat.getTotalLaps()));
        }
        if (heat.getTotalPits() > 0) {
            sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_info_pits", "{pits}", driver.getPitstops() + "/" + heat.getTotalPits()));
        }
        sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_prepare_start", new String[0]));
        return sb.toString();
    }

    private String buildRacingMessage(Heats heat, Driver driver, Player viewer) {
        if (driver.isFinished()) {
            return this.buildFinishedMessage(driver, viewer);
        }

        StringBuilder sb = new StringBuilder();
        int position = driver.getPosition();
        sb.append(this.getPositionColor(position)).append("P").append(position);

        int totalLaps = heat.getTotalLaps();
        int currentLap = driver.getCurrentLap() == null ? 0 : Math.min(totalLaps, driver.getLapCount() + 1);
        sb.append(" \u00a78| ").append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_practice_lap", "{lap}", currentLap + "\u00a77/\u00a7f" + totalLaps));

        int totalCheckpoints = this.getTrackCheckpointCount(heat.getTrackNameWS());
        double lapsCompleted = driver.getLapCount();
        double currentLapProgress = 0.0;
        if (totalCheckpoints > 0 && driver.getCurrentLap() != null) {
            int checkpointsInCurrentLap = Math.min(driver.getCheckpointsReached(), totalCheckpoints);
            currentLapProgress = (double) checkpointsInCurrentLap / (double) totalCheckpoints;
        }
        double totalProgress = (lapsCompleted + currentLapProgress) / (double) totalLaps;
        totalProgress = Math.max(0.0, Math.min(1.0, totalProgress));
        sb.append(" ").append(this.buildProgressBar(totalProgress, this.progressBarLength));

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
                sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_info_pits", "{pits}", String.valueOf(pitsRemaining)));
            } else {
                sb.append(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_info_pits", "{pits}", "\u00a7a\u2713"));
            }
        }

        long elapsed = 0L;
        if (heat.getStartTime() != null) {
            elapsed = System.currentTimeMillis() - heat.getStartTime().toEpochMilli();
        }
        sb.append(" \u00a78| \u00a77").append(this.formatRaceElapsed(elapsed));
        return sb.toString();
    }

    private String buildFinishedMessage(Driver driver, Player viewer) {
        if (driver.isFinished()) {
            String posColor = this.getPositionColor(driver.getPosition());
            return String.format(
                    this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_finished_title", new String[0]) + "%sP%d \u00a78| \u00a77%s",
                    posColor,
                    driver.getPosition(),
                    this.formatTimeShort(driver.getTotalTime())
            );
        }
        if (driver.isDnf()) {
            return this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_dnf_title", new String[0]);
        }
        return this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_race_finished", new String[0]);
    }

    private String getPositionColor(int position) {
        return switch (position) {
            case 1 -> "\u00a76";
            case 2 -> "\u00a77";
            case 3 -> "\u00a7c";
            default -> "\u00a77";
        };
    }

    private String buildProgressBar(double progress, int length) {
        int filled = (int) (progress * (double) length);
        StringBuilder bar = new StringBuilder(this.progressBracketColor).append("[");
        for (int i = 0; i < length; ++i) {
            if (i < filled) {
                bar.append(this.getProgressColorBySegment(i, length)).append("\u258c");
            } else {
                bar.append(this.progressEmptyColor).append("\u258c");
            }
        }
        bar.append(this.progressBracketColor).append("]");
        return bar.toString();
    }

    private String getProgressColorBySegment(int segmentIndex, int length) {
        double segmentProgress = (double) (segmentIndex + 1) / (double) length;
        if (segmentProgress <= 0.33) {
            return this.progressStartColor;
        }
        if (segmentProgress <= 0.66) {
            return this.progressMiddleColor;
        }
        return this.progressEndColor;
    }

    private int getTrackCheckpointCount(String trackNameWS) {
        return this.trackCheckpointCountCache.computeIfAbsent(trackNameWS, track -> this.plugin.getTrackIntegrationManager().getCheckpointCount(track));
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

    private String formatRaceElapsed(long timeMs) {
        long minutes = timeMs / 60000L;
        long seconds = timeMs % 60000L / 1000L;
        long tenths = timeMs % 1000L / 100L;
        if (minutes > 0L) {
            return String.format("%d:%02d.%d", minutes, seconds, tenths);
        }
        return String.format("%d.%d", seconds, tenths);
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
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
        }
        for (UUID spectatorId : this.spectatorTargets.keySet()) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator == null || !spectator.isOnline()) {
                continue;
            }
            spectator.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(""));
        }
        this.playerHeats.clear();
        this.spectatorTargets.clear();
        this.lastActionBarMessage.clear();
        this.lastStaticUpdateAt.clear();
    }

    private static final class SpectatorTarget {
        private final Heats heat;
        private final UUID driverId;

        private SpectatorTarget(Heats heat, UUID driverId) {
            this.heat = heat;
            this.driverId = driverId;
        }
    }
}
