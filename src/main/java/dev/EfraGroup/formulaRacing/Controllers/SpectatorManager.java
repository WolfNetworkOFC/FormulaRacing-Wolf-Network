//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Participant.Spectator;
import dev.EfraGroup.formulaRacing.Participant.Spectator.SpectatorMode;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private BukkitTask followTask;
    private static final int FOLLOW_UPDATE_INTERVAL = 5;

    public Set<UUID> getSpectatorsInEvent(int eventId) {
        return (Set)this.eventToSpectators.getOrDefault(eventId, Collections.emptySet());
    }

    public SpectatorManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.debug = plugin.getDebugManager();
        this.spectators = new ConcurrentHashMap();
        this.spectatorToEvent = new ConcurrentHashMap();
        this.eventToSpectators = new ConcurrentHashMap();
        this.startFollowTask();
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
            ((Set)this.eventToSpectators.computeIfAbsent(event.getId(), (k) -> ConcurrentHashMap.newKeySet())).add(playerId);
            this.setupSpectatorMode(player, spectator, event);
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
            if (event != null) {
                Set<UUID> eventSpectators = (Set)this.eventToSpectators.get(event.getId());
                if (eventSpectators != null) {
                    eventSpectators.remove(playerId);
                }
            }

            this.restorePlayer(player);
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
        GameMode previousGameMode = player.getGameMode();
        player.setGameMode(GameMode.SPECTATOR);
        Location spectatorLocation = this.getSpectatorLocation(event);
        if (spectatorLocation != null) {
            player.teleport(spectatorLocation);
        }

        String title = this.plugin.getTranslation("spectator_title_mode", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]);
        String subtitle = this.plugin.getTranslation("spectator_subtitle_mode", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]);
        player.sendTitle(title, subtitle, 10, 60, 20);
    }

    private void restorePlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
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
                for(Map.Entry<UUID, Spectator> entry : SpectatorManager.this.spectators.entrySet()) {
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
        }).runTaskTimer(this.plugin, 0L, 5L);
    }

    public void removeEventSpectators(Events event) {
        Set<UUID> eventSpectators = (Set)this.eventToSpectators.remove(event.getId());
        if (eventSpectators != null) {
            for(UUID spectatorId : eventSpectators) {
                Player player = Bukkit.getPlayer(spectatorId);
                if (player != null && player.isOnline()) {
                    this.removeSpectator(player);
                } else {
                    this.spectators.remove(spectatorId);
                    this.spectatorToEvent.remove(spectatorId);
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

        // Corrigido: Especificando o tipo <UUID> na criação da lista para evitar o erro de conversão
        for (UUID spectatorId : new java.util.ArrayList<>(this.spectators.keySet())) {
            Player player = Bukkit.getPlayer(spectatorId);
            if (player != null && player.isOnline()) {
                this.removeSpectator(player);
            }
        }

        this.spectators.clear();
        this.spectatorToEvent.clear();
        this.eventToSpectators.clear();

        // Simplificado o acesso ao log para ficar mais limpo
        this.debug.logSpectatorSystem("SpectatorManager desligado");
    }
}
