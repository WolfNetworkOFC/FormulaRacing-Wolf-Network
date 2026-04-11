package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Participant.Spectator;
import dev.EfraGroup.formulaRacing.Participant.Spectator.SpectatorMode;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.ScoreboardOwnershipCoordinator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class SpectatorManager {
    private final FormulaRacing plugin;
    private final DebugManager debug;
    private final Map<UUID, Spectator> spectators;
    private final Map<UUID, Events> spectatorToEvent;
    private final Map<Integer, Set<UUID>> eventToSpectators;
    private final Map<UUID, Heats> spectatorBoundHeat;
    private final Map<UUID, GameMode> previousGameModes;
    private BukkitTask followTask;
    private BukkitTask bindingTask;
    private BukkitTask proximityActionBarTask;
    private static final int FOLLOW_UPDATE_INTERVAL_TICKS = 5;
    private static final int BINDING_UPDATE_INTERVAL_TICKS = 10;
    private final boolean proximityActionBarEnabled;
    private final double proximityRadiusSquared;
    private final int proximityIntervalTicks;

    public Set<UUID> getSpectatorsInEvent(int eventId) {
        return (Set)this.eventToSpectators.getOrDefault(eventId, Collections.emptySet());
    }

    public SpectatorManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.debug = plugin.getDebugManager();
        this.spectators = new ConcurrentHashMap();
        this.spectatorToEvent = new ConcurrentHashMap();
        this.eventToSpectators = new ConcurrentHashMap();
        this.spectatorBoundHeat = new ConcurrentHashMap();
        this.previousGameModes = new ConcurrentHashMap();
        this.proximityActionBarEnabled = plugin.getConfig().getBoolean("spectator.actionbar-proximity.enabled", true);
        double radius = Math.max(1.0, plugin.getConfig().getDouble("spectator.actionbar-proximity.radius", 18.0));
        this.proximityRadiusSquared = radius * radius;
        this.proximityIntervalTicks = Math.max(1, plugin.getConfig().getInt("spectator.actionbar-proximity.interval-ticks", 5));
        this.startFollowTask();
        this.startBindingTask();
        if (this.proximityActionBarEnabled) {
            this.startProximityActionBarTask();
        }
    }

    public boolean addSpectator(Player player, Events event) {
        UUID playerId = player.getUniqueId();
        if (this.spectators.containsKey(playerId)) {
            return false;
        } else if (event.isActivelyRacing(playerId)) {
            return false;
        } else {
            Spectator spectator = new Spectator(playerId, player.getName());
            this.spectators.put(playerId, spectator);
            this.spectatorToEvent.put(playerId, event);
            this.previousGameModes.put(playerId, player.getGameMode());
            ((Set)this.eventToSpectators.computeIfAbsent(event.getId(), (k) -> ConcurrentHashMap.newKeySet())).add(playerId);
            event.addSpectator(playerId);
            this.setupSpectatorMode(player, spectator, event);
            this.syncBindingFor(playerId, player, event);
            this.plugin.sendMessage(player, "spectator_watching", new String[0]);
            this.plugin.sendMessage(player, "spectator_help_follow", new String[0]);
            this.plugin.sendMessage(player, "spectator_help_leave", new String[0]);
            this.debug.logSpectatorSystem(String.format("%s entrou como espectador no evento %s", player.getName(), event.getDisplayName()));
            return true;
        }
    }

    public boolean removeSpectator(Player player) {
        UUID playerId = player.getUniqueId();
        Spectator spectator = (Spectator)this.spectators.remove(playerId);
        if (spectator == null) {
            return false;
        } else {
            Events event = (Events)this.spectatorToEvent.remove(playerId);
            GameMode previousMode = (GameMode)this.previousGameModes.remove(playerId);
            Heats boundHeat = (Heats)this.spectatorBoundHeat.remove(playerId);
            if (boundHeat != null) {
                this.plugin.getRaceScoreboardManager().removeSpectator(player);
            }
            if (this.plugin.getRaceActionBarManager() != null) {
                this.plugin.getRaceActionBarManager().clearSpectatorTarget(player);
            }

            if (event != null) {
                Set<UUID> eventSpectators = (Set)this.eventToSpectators.get(event.getId());
                if (eventSpectators != null) {
                    eventSpectators.remove(playerId);
                    if (eventSpectators.isEmpty()) {
                        this.eventToSpectators.remove(event.getId());
                    }
                }

                event.removeSpectator(playerId);
            }

            this.restorePlayer(player, previousMode);
            long watchTime = spectator.getWatchTime() / 1000L;
            this.plugin.sendMessage(player, "spectator_time_watched", new String[]{"{time}", this.formatTime(watchTime)});
            this.debug.logSpectatorSystem(String.format("%s saiu do modo espectador", player.getName()));
            return true;
        }
    }

    public void joinAsSpectator(Player player, Events event) {
        this.addSpectator(player, event);
    }

    public void leaveSpectator(Player player) {
        this.removeSpectator(player);
    }

    private void setupSpectatorMode(Player player, Spectator spectator, Events event) {
        player.setGameMode(GameMode.SPECTATOR);
        Location spectatorLocation = this.getSpectatorLocation(event);
        if (spectatorLocation != null) {
            player.teleport(spectatorLocation);
        }

        String title = this.plugin.getTranslation("spectator_title_mode", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]);
        String subtitle = this.plugin.getTranslation("spectator_subtitle_mode", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]);
        player.sendTitle(title, subtitle, 10, 60, 20);
    }

    private void restorePlayer(Player player, GameMode previousMode) {
        if (previousMode == null) {
            previousMode = GameMode.SURVIVAL;
        }

        player.setGameMode(previousMode);
        player.setFlying(false);
        player.setAllowFlight(previousMode == GameMode.CREATIVE || previousMode == GameMode.SPECTATOR);
    }

    private Location getSpectatorLocation(Events event) {
        DatabaseManager db = this.plugin.getDatabaseManager();
        String trackName = event.getTrackNameWS();
        Location finishAll = db.getTrackFinishAll(trackName);
        if (finishAll != null) {
            return finishAll;
        } else {
            DatabaseManager.TrackData trackData = this.plugin.dm.getTrackData(trackName);
            return trackData != null && trackData.getSpawnLocation() != null ? trackData.getSpawnLocation() : null;
        }
    }

    public boolean followDriver(Player spectatorPlayer, String driverName) {
        UUID spectatorId = spectatorPlayer.getUniqueId();
        Spectator spectator = (Spectator)this.spectators.get(spectatorId);
        if (spectator == null) {
            return false;
        } else {
            Events event = (Events)this.spectatorToEvent.get(spectatorId);
            if (event == null) {
                return false;
            } else {
                Player driverPlayer = Bukkit.getPlayer(driverName);
                if (driverPlayer != null && driverPlayer.isOnline()) {
                    if (!event.isDriver(driverPlayer.getUniqueId())) {
                        this.plugin.sendMessage(spectatorPlayer, "spectator_driver_not_racing", new String[0]);
                        return false;
                    } else {
                        spectator.setFollowingDriverUUID(driverPlayer.getUniqueId());
                        spectator.setMode(SpectatorMode.FOLLOW_DRIVER);
                        spectatorPlayer.teleport(driverPlayer.getLocation());
                        this.plugin.sendMessage(spectatorPlayer, "spectator_following", new String[]{"{driver}", driverName});
                        this.plugin.sendMessage(spectatorPlayer, "spectator_help_unfollow", new String[0]);
                        return true;
                    }
                } else {
                    this.plugin.sendMessage(spectatorPlayer, "spectator_driver_not_found", new String[0]);
                    return false;
                }
            }
        }
    }

    public boolean unfollowDriver(Player spectatorPlayer) {
        UUID spectatorId = spectatorPlayer.getUniqueId();
        Spectator spectator = (Spectator)this.spectators.get(spectatorId);
        if (spectator == null) {
            return false;
        } else {
            spectator.setFollowingDriverUUID((UUID)null);
            spectator.setMode(SpectatorMode.FREE_CAM);
            this.plugin.sendMessage(spectatorPlayer, "spectator_unfollowed", new String[0]);
            return true;
        }
    }

    private void startFollowTask() {
        this.followTask = (new BukkitRunnable() {
            public void run() {
                for(Map.Entry<UUID, Spectator> entry : new ArrayList<>(SpectatorManager.this.spectators.entrySet())) {
                    Spectator spectator = (Spectator)entry.getValue();
                    if (spectator.getMode() == SpectatorMode.FOLLOW_DRIVER) {
                        UUID driverUUID = spectator.getFollowingDriverUUID();
                        if (driverUUID != null) {
                            Player spectatorPlayer = Bukkit.getPlayer((UUID)entry.getKey());
                            Player driverPlayer = Bukkit.getPlayer(driverUUID);
                            if (spectatorPlayer != null && spectatorPlayer.isOnline()) {
                                if (driverPlayer != null && driverPlayer.isOnline()) {
                                    Location driverLoc = driverPlayer.getLocation();
                                    Location spectatorLoc = driverLoc.clone();
                                    spectatorLoc.add(driverLoc.getDirection().multiply(-5));
                                    spectatorLoc.setY(spectatorLoc.getY() + (double)2.0F);
                                    spectatorLoc.setDirection(driverLoc.toVector().subtract(spectatorLoc.toVector()));
                                    spectatorPlayer.teleport(spectatorLoc);
                                } else {
                                    spectator.setFollowingDriverUUID((UUID)null);
                                    spectator.setMode(SpectatorMode.FREE_CAM);
                                    SpectatorManager.this.plugin.sendMessage(spectatorPlayer, "spectator_target_left", new String[0]);
                                }
                            }
                        }
                    }
                }

            }
        }).runTaskTimer(this.plugin, 0L, FOLLOW_UPDATE_INTERVAL_TICKS);
    }

    private void startBindingTask() {
        this.bindingTask = (new BukkitRunnable() {
            public void run() {
                for(Map.Entry<UUID, Events> entry : new ArrayList<>(SpectatorManager.this.spectatorToEvent.entrySet())) {
                    UUID spectatorId = (UUID)entry.getKey();
                    Events event = (Events)entry.getValue();
                    Player player = Bukkit.getPlayer(spectatorId);
                    if (player == null || !player.isOnline()) {
                        SpectatorManager.this.cleanupSpectatorState(spectatorId, event);
                        continue;
                    }

                    if (!SpectatorManager.this.isEventTrackable(event) || !event.isActive()) {
                        SpectatorManager.this.removeSpectator(player);
                        continue;
                    }

                    SpectatorManager.this.syncBindingFor(spectatorId, player, event);
                }

            }
        }).runTaskTimer(this.plugin, 0L, BINDING_UPDATE_INTERVAL_TICKS);
    }

    private void cleanupSpectatorState(UUID spectatorId, Events fallbackEvent) {
        this.spectators.remove(spectatorId);
        this.previousGameModes.remove(spectatorId);
        this.spectatorBoundHeat.remove(spectatorId);
        if (this.plugin.getRaceActionBarManager() != null) {
            this.plugin.getRaceActionBarManager().clearSpectatorTarget(spectatorId);
        }
        this.plugin.getScoreboardOwnershipCoordinator().clear(spectatorId);
        Events event = (Events)this.spectatorToEvent.remove(spectatorId);
        if (event == null) {
            event = fallbackEvent;
        }

        if (event != null) {
            Set<UUID> eventSpectators = (Set)this.eventToSpectators.get(event.getId());
            if (eventSpectators != null) {
                eventSpectators.remove(spectatorId);
                if (eventSpectators.isEmpty()) {
                    this.eventToSpectators.remove(event.getId());
                }
            }

            event.removeSpectator(spectatorId);
        }

    }

    public void handlePlayerDisconnect(UUID spectatorId) {
        if (this.spectators.containsKey(spectatorId)) {
            this.cleanupSpectatorState(spectatorId, (Events)this.spectatorToEvent.get(spectatorId));
        }
    }

    private boolean isEventTrackable(Events event) {
        return event != null && this.plugin.getRaceEventManager().getEventById(event.getId()).isPresent();
    }

    private void syncBindingFor(UUID spectatorId, Player player, Events event) {
        Heats targetHeat = this.resolveActiveHeat(event);
        Heats currentHeat = (Heats)this.spectatorBoundHeat.get(spectatorId);
        if (currentHeat == targetHeat) {
            if (targetHeat != null && !this.plugin.getScoreboardOwnershipCoordinator().isOwner(spectatorId, ScoreboardOwnershipCoordinator.Mode.RACE)) {
                this.plugin.getRaceScoreboardManager().addSpectator(player, targetHeat);
            }
            return;
        }

        if (currentHeat != null) {
            this.plugin.getRaceScoreboardManager().removeSpectator(player);
        }
        if (this.plugin.getRaceActionBarManager() != null) {
            this.plugin.getRaceActionBarManager().clearSpectatorTarget(player);
        }

        if (targetHeat != null) {
            this.plugin.getRaceScoreboardManager().addSpectator(player, targetHeat);
            this.spectatorBoundHeat.put(spectatorId, targetHeat);
        } else {
            this.spectatorBoundHeat.remove(spectatorId);
        }

    }

    private Heats resolveActiveHeat(Events event) {
        Optional<Heats> currentRoundHeat = event.getEventSchedule().getCurrentRound().flatMap((round) -> round.getActiveHeat());
        if (currentRoundHeat.isPresent()) {
            return (Heats)currentRoundHeat.get();
        } else {
            for(Rounds round : event.getEventSchedule().getRoundsOrdered()) {
                Optional<Heats> activeHeat = round.getActiveHeat();
                if (activeHeat.isPresent()) {
                    return (Heats)activeHeat.get();
                }
            }

            return null;
        }
    }

    private void startProximityActionBarTask() {
        this.proximityActionBarTask = (new BukkitRunnable() {
            public void run() {
                for (Map.Entry<UUID, Spectator> entry : new ArrayList<>(SpectatorManager.this.spectators.entrySet())) {
                    UUID spectatorId = (UUID)entry.getKey();
                    Player spectatorPlayer = Bukkit.getPlayer((UUID)spectatorId);
                    if (spectatorPlayer == null || !spectatorPlayer.isOnline()) {
                        continue;
                    }
                    if (SpectatorManager.this.plugin.getRaceActionBarManager() == null) {
                        continue;
                    }
                    Heats boundHeat = (Heats)SpectatorManager.this.spectatorBoundHeat.get(spectatorId);
                    if (boundHeat == null) {
                        SpectatorManager.this.plugin.getRaceActionBarManager().clearSpectatorTarget(spectatorPlayer);
                        continue;
                    }
                    Spectator spectator = (Spectator)entry.getValue();
                    UUID targetDriverId = SpectatorManager.this.resolveTargetDriverId(spectatorPlayer, spectator, boundHeat);
                    if (targetDriverId == null) {
                        SpectatorManager.this.plugin.getRaceActionBarManager().clearSpectatorTarget(spectatorPlayer);
                        continue;
                    }
                    SpectatorManager.this.plugin.getRaceActionBarManager().setSpectatorTarget(spectatorPlayer, boundHeat, targetDriverId);
                }
            }
        }).runTaskTimer(this.plugin, 0L, (long)this.proximityIntervalTicks);
    }

    private UUID resolveTargetDriverId(Player spectatorPlayer, Spectator spectator, Heats boundHeat) {
        if (spectator != null && spectator.getMode() == SpectatorMode.FOLLOW_DRIVER) {
            UUID followedDriverId = spectator.getFollowingDriverUUID();
            if (followedDriverId != null) {
                Driver followedDriver = boundHeat.getDriver(followedDriverId);
                if (followedDriver != null) {
                    Player followedPlayer = Bukkit.getPlayer((UUID)followedDriverId);
                    if (followedPlayer != null && followedPlayer.isOnline() && !followedDriver.isDnf()) {
                        return followedDriverId;
                    }
                }
            }
        }

        double bestDistance = Double.MAX_VALUE;
        UUID closestDriverId = null;
        for (Driver driver : boundHeat.getDrivers().values()) {
            if (driver == null || driver.isDnf()) {
                continue;
            }
            Player driverPlayer = Bukkit.getPlayer((UUID)driver.getUuid());
            if (driverPlayer == null || !driverPlayer.isOnline()) {
                continue;
            }
            if (driverPlayer.getWorld() != spectatorPlayer.getWorld()) {
                continue;
            }
            double distanceSquared = spectatorPlayer.getLocation().distanceSquared(driverPlayer.getLocation());
            if (distanceSquared > this.proximityRadiusSquared || distanceSquared >= bestDistance) {
                continue;
            }
            bestDistance = distanceSquared;
            closestDriverId = driver.getUuid();
        }
        return closestDriverId;
    }

    public void removeEventSpectators(Events event) {
        Set<UUID> eventSpectators = (Set)this.eventToSpectators.remove(event.getId());
        if (eventSpectators != null) {
            for(UUID spectatorId : eventSpectators) {
                Player player = Bukkit.getPlayer(spectatorId);
                if (player != null && player.isOnline()) {
                    this.removeSpectator(player);
                } else {
                    this.cleanupSpectatorState(spectatorId, event);
                }
            }

            this.debug.logSpectatorSystem(String.format("Removidos %d espectadores do evento %s", eventSpectators.size(), event.getDisplayName()));
        }
    }

    public Spectator getSpectator(UUID uuid) {
        return (Spectator)this.spectators.get(uuid);
    }

    public boolean isSpectator(UUID uuid) {
        return this.spectators.containsKey(uuid);
    }

    public Events getWatchingEvent(UUID spectatorId) {
        return (Events)this.spectatorToEvent.get(spectatorId);
    }

    public List<Spectator> getEventSpectators(Events event) {
        Set<UUID> spectatorIds = (Set)this.eventToSpectators.get(event.getId());
        if (spectatorIds == null) {
            return new ArrayList();
        } else {
            List<Spectator> result = new ArrayList();

            for(UUID id : spectatorIds) {
                Spectator spectator = (Spectator)this.spectators.get(id);
                if (spectator != null) {
                    result.add(spectator);
                }
            }

            return result;
        }
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60L;
        long secs = seconds % 60L;
        return minutes > 0L ? String.format("%dm %ds", minutes, secs) : String.format("%ds", secs);
    }

    public void shutdown() {
        if (this.followTask != null) {
            this.followTask.cancel();
        }

        if (this.bindingTask != null) {
            this.bindingTask.cancel();
        }
        if (this.proximityActionBarTask != null) {
            this.proximityActionBarTask.cancel();
        }

        for (UUID spectatorId : new java.util.ArrayList<>(this.spectators.keySet())) {
            Player player = Bukkit.getPlayer(spectatorId);
            if (player != null && player.isOnline()) {
                this.removeSpectator(player);
            }
        }

        this.spectators.clear();
        this.spectatorToEvent.clear();
        this.eventToSpectators.clear();
        this.spectatorBoundHeat.clear();
        this.previousGameModes.clear();

        this.debug.logSpectatorSystem("SpectatorManager desligado");
    }
}
