package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

public class GridManager {
    private final FormulaRacing plugin;
    private final Heats heat;
    private final List<Location> gridPositions;
    private final Set<UUID> frozenPlayers;

    public GridManager(FormulaRacing plugin, Heats heat) {
        this.plugin = plugin;
        this.heat = heat;
        this.gridPositions = new ArrayList();
        this.frozenPlayers = new HashSet();
    }

    public boolean generateGrid() {
        String trackNameWS = this.heat.getTrackNameWS();
        if (trackNameWS != null && !trackNameWS.isEmpty()) {
            TrackIntegrationManager trackManager = this.plugin.getTrackIntegrationManager();
            int maxDrivers = this.heat.getMaxDrivers();
            this.gridPositions.clear();
            this.gridPositions.addAll(trackManager.generateGridPositions(trackNameWS, maxDrivers));
            if (this.gridPositions.isEmpty()) {
                this.plugin.getDebugManager().logRaceSystem("Could not generate grid for track: " + trackNameWS);
                return false;
            } else {
                this.heat.setMaxDrivers(this.gridPositions.size());
                DebugManager var10000 = this.plugin.getDebugManager();
                int var10001 = this.heat.getId();
                var10000.logRaceSystem("Grid generated for Heat " + var10001 + ": " + this.gridPositions.size() + " positions");
                return true;
            }
        } else {
            this.plugin.getDebugManager().logRaceSystem("Heat " + this.heat.getId() + " has no track configured!");
            return false;
        }
    }

    public int teleportDriversToGrid() {
        if (this.gridPositions.isEmpty() && !this.generateGrid()) {
            this.plugin.getDebugManager().logRaceSystem("Could not teleport drivers: grid not generated");
            return 0;
        } else {
            List<Driver> drivers = this.heat.getStartPositions();
            if (drivers.isEmpty()) {
                this.plugin.getDebugManager().logRaceSystem("No drivers to teleport in Heat " + this.heat.getId());
                return 0;
            } else {
                int teleported = 0;

                for(Driver driver : drivers) {
                    if (driver.getStartPosition() < 1) {
                        DebugManager var10000 = this.plugin.getDebugManager();
                        String var10001 = String.valueOf(driver.getUuid());
                        var10000.logRaceSystem("CRITICAL ERROR: Driver " + var10001 + " has invalid startPosition: " + driver.getStartPosition());
                        this.plugin.getDebugManager().logRaceSystem("Fixing to position 1...");
                        driver.setStartPosition(1);
                    }

                    int gridPosition = driver.getStartPosition() - 1;
                    if (gridPosition >= 0 && gridPosition < this.gridPositions.size()) {
                        Player player = this.plugin.getServer().getPlayer(driver.getUuid());
                        if (player != null && player.isOnline()) {
                            Location gridLoc = (Location)this.gridPositions.get(gridPosition);

                            SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                                this.plugin.getAPI().recoverPlayerBoatState(player);
                            });

                            this.spawnBoatWithTrackConfig(player, gridLoc, driver);
                            DebugManager var10000 = this.plugin.getDebugManager();
                            String var10001 = player.getName();
                            var10000.logRaceSystem("Driver " + var10001 + " teleported to P" + (gridPosition + 1));
                            ++teleported;
                        } else {
                            this.plugin.getDebugManager().logRaceSystem("Offline driver cannot be teleported: " + String.valueOf(driver.getUuid()));
                        }
                    } else {
                        DebugManager var8 = this.plugin.getDebugManager();
                        String var9 = String.valueOf(driver.getUuid());
                        var8.logRaceSystem("Invalid grid position for driver " + var9 + ": P" + (gridPosition + 1) + " (index: " + gridPosition + ")");
                    }
                }

                this.plugin.getDebugManager().logRaceSystem("Teleported " + teleported + "/" + drivers.size() + " drivers to grid");
                return teleported;
            }
        }
    }

    public void freezePlayers() {
        for(Driver driver : this.heat.getDrivers().values()) {
            Player player = this.plugin.getServer().getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                this.frozenPlayers.add(driver.getUuid());
                this.plugin.sendMessage(player, "race_waiting_start", new String[0]);
            }
        }

        this.plugin.getDebugManager().logRaceSystem("Drivers frozen on grid: " + this.frozenPlayers.size());
    }

    public void unfreezePlayers() {
        for(UUID playerUUID : this.frozenPlayers) {
            Player player = this.plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                this.plugin.getAPI().releaseBoat(player);
            }
        }

        this.frozenPlayers.clear();
        this.plugin.getDebugManager().logRaceSystem("Drivers released from grid");
    }

    public boolean teleportDriver(Driver driver) {
        if (this.gridPositions.isEmpty() && !this.generateGrid()) {
            return false;
        } else {
            int gridPosition = driver.getStartPosition() - 1;
            if (gridPosition >= 0 && gridPosition < this.gridPositions.size()) {
                Player player = this.plugin.getServer().getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    Location gridLoc = (Location)this.gridPositions.get(gridPosition);

                    SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                        this.plugin.getAPI().recoverPlayerBoatState(player);
                    });

                    this.spawnBoatWithTrackConfig(player, gridLoc, driver);
                    HeatState state = this.heat.getHeatState();
                    if (state == HeatState.LOADED || state == HeatState.STARTING) {
                        this.frozenPlayers.add(driver.getUuid());
                    }

                    DebugManager var10000 = this.plugin.getDebugManager();
                    String var10001 = player.getName();
                    var10000.logRaceSystem("Driver " + var10001 + " teleported to P" + (gridPosition + 1));
                    return true;
                } else {
                    return false;
                }
            } else {
                this.plugin.getDebugManager().logRaceSystem("Invalid grid position: " + gridPosition);
                return false;
            }
        }
    }

    public List<Location> getGridPositions() {
        return new ArrayList(this.gridPositions);
    }

    public int getGridSize() {
        return this.gridPositions.size();
    }

    public void clear() {
        for(Driver driver : this.heat.getDrivers().values()) {
            Player player = this.plugin.getServer().getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                this.plugin.getAPI().releaseBoat(player);
                this.plugin.getDebugManager().logRaceSystem("Boat released for " + player.getName() + " (clear)");
            }
        }

        this.unfreezePlayers();
        this.gridPositions.clear();
        this.frozenPlayers.clear();
    }

    private void removePlayerFromTimeTrial(Player player) {
        this.plugin.getScoreboardTimeTrialUtils().clearPlayerTrack(player);
        this.plugin.getDebugManager().logRaceSystem("Player " + player.getName() + " removed from time trial");
    }

    private void spawnBoatWithTrackConfig(final Player player, Location location, Driver driver) {
        final String trackNameWS = this.heat.getTrackNameWS();
        final Location gridLoc = location.clone();
        // Player-entity work must run on the player's region thread (Folia).
        SchedulerHelper.runTaskFor(this.plugin, player, () -> {
            if (player.isOnline()) {
                SchedulerHelper.teleport(player, gridLoc);
                GridManager.this.plugin.setLastTimeTrialTrack(player.getUniqueId(), trackNameWS);
                GridManager.this.plugin.getPacketSender().resetBoatUtilsToVanilla(player);
                GridManager.this.plugin.getPacketSender().applyBoatUtilsToPlayer(player, trackNameWS);
                HeatState state = GridManager.this.heat.getHeatState();
                boolean locked = state == HeatState.LOADED || state == HeatState.STARTING;
                boolean collidable = GridManager.this.heat.getCollisionMode() != CollisionMode.DISABLED;
                GridManager.this.plugin.getAPI().spawnBoatAt(player, gridLoc, false, locked, false, collidable);
                GridManager.this.plugin.getDebugManager().logRaceSystem("Boat spawned for " + player.getName() + " on grid (track: " + trackNameWS + ", locked: " + locked + ", collidable: " + collidable + ", yaw: " + gridLoc.getYaw() + ")");
            }
        }, 10L);
    }
}
