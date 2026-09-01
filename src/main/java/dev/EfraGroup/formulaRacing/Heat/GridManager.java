package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

public class GridManager {
    private final FormulaRacing plugin;
    private final Heats heat;
    private final List<Location> gridPositions;
    // Read by region threads (jump-start flag) while the global thread
    // mutates it on freeze/unfreeze — must be a concurrent set.
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    /** Jump-start penalties (F1 start): ticks a driver stays anchored after lights out. */
    private final java.util.Map<UUID, Long> jumpStartPenalties = new java.util.concurrent.ConcurrentHashMap<>();

    public GridManager(FormulaRacing plugin, Heats heat) {
        this.plugin = plugin;
        this.heat = heat;
        this.gridPositions = new ArrayList();
    }

    /**
     * Registers a jump-start penalty: the driver is released from the grid
     * anchor {@code penaltyTicks} ticks after lights out, while everyone else
     * launches immediately.
     */
    public void penalizeJumpStart(UUID uuid, long penaltyTicks) {
        if (uuid != null && penaltyTicks > 0L) {
            this.jumpStartPenalties.put(uuid, penaltyTicks);
        }
    }

    /** Whether the driver is currently held on the grid (countdown phase). */
    public boolean isFrozen(UUID uuid) {
        return uuid != null && this.frozenPlayers.contains(uuid);
    }

    public boolean generateGrid() {
        String trackNameWS = this.heat.getTrackNameWS();
        if (trackNameWS != null && !trackNameWS.isEmpty()) {
            TrackIntegrationManager trackManager = this.plugin.getTrackIntegrationManager();
            // Never cap the grid by heat.getMaxDrivers(): that value is a stale
            // snapshot taken when the track was assigned to the heat (0 when no
            // grids existed yet), so grids added later were silently truncated to
            // zero and the heat reported "no GRIDs defined" until a server restart
            // rebuilt the heat and re-ran setTrackNameWS. The heat only needs one
            // slot per enrolled driver; loadHeat already warns when the track has
            // fewer slots than drivers.
            int maxDrivers = Math.max(this.heat.getDrivers().size(), 1);
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
        // New start sequence: drop penalties from a previous start of this heat.
        this.jumpStartPenalties.clear();
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
            long penaltyTicks = this.jumpStartPenalties.getOrDefault(playerUUID, 0L);
            if (penaltyTicks > 0L) {
                // Jump starter: stay anchored while the rest of the grid launches.
                SchedulerHelper.runTaskLater(this.plugin, () -> {
                    Player penalized = this.plugin.getServer().getPlayer(playerUUID);
                    if (penalized != null && penalized.isOnline()) {
                        this.plugin.getAPI().releaseBoat(penalized);
                    }
                }, penaltyTicks);
            } else {
                Player player = this.plugin.getServer().getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    this.plugin.getAPI().releaseBoat(player);
                }
            }
        }

        this.frozenPlayers.clear();
        this.jumpStartPenalties.clear();
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
        // Folia: all player-entity work must run on the player's region thread.
        // 1) Dismount and delete any boat the player is currently riding FIRST —
        //    teleporting a player who is still a passenger of a boat is unreliable
        //    on Paper/Folia (the rider does not arrive at the destination and can
        //    get stuck), while the old boat gets removed anyway, so the driver
        //    never reached the grid when they were already sitting in a boat.
        // 2) Then teleport the (now boatless) player and WAIT for completion
        //    (teleportAsync is thread-safe) so the player is already in the grid
        //    region before the spawn runs. The previous fire-and-forget teleport
        //    followed by an immediate spawn ran on the player's OLD region thread
        //    and threw "Cannot add entity off-main thread" whenever the grid was
        //    in a different region than the player.
        SchedulerHelper.runTaskFor(this.plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            GridManager.this.plugin.getAPI().recoverPlayerBoatState(player);
            SchedulerHelper.teleportAsync(player, gridLoc).thenAccept(success -> {
            if (Boolean.TRUE.equals(success) && player.isOnline()) {
                // All player-entity work runs on the player's region thread (Folia),
                // which is now the grid region.
                SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                    if (player.isOnline()) {
                        GridManager.this.plugin.setLastTimeTrialTrack(player.getUniqueId(), trackNameWS);
                        GridManager.this.plugin.getPacketSender().resetBoatUtilsToVanilla(player);
                        GridManager.this.plugin.getPacketSender().applyBoatUtilsToPlayer(player, trackNameWS);
                        HeatState state = GridManager.this.heat.getHeatState();
                        boolean locked = state == HeatState.LOADED || state == HeatState.STARTING;
                        boolean collidable = GridManager.this.heat.getCollisionMode() != CollisionMode.DISABLED;
                        GridManager.this.plugin.getAPI().spawnBoatAt(player, gridLoc, false, locked, false, collidable);
                        // Re-apply the visibility/collision policy AFTER the player is inside the
                        // boat: loadHeat calls updatePlayersVisibility before the boat spawns, and
                        // addPassenger does NOT fire VehicleEnterEvent, so a client that arrived
                        // with nocol (e.g. from a time trial / open world with the OBU mod) would
                        // stay collisionless for the whole race otherwise.
                        // NOTE: spawnBoatAt defers the actual spawn via runTaskFor(player) with no
                        // delay; this call queues right after it on the same player scheduler, so
                        // FIFO ordering guarantees the boat exists before the policy is re-applied
                        // (as long as spawnBoatAt takes the same-region path — the player is in
                        // the grid region here because we waited for the teleport).
                        // Do not add a delay to either call without re-checking this dependency.
                        GridManager.this.plugin.getLonelyController().updatePlayersVisibility(player);
                        GridManager.this.plugin.getDebugManager().logRaceSystem("Boat spawned for " + player.getName() + " on grid (track: " + trackNameWS + ", locked: " + locked + ", collidable: " + collidable + ", yaw: " + gridLoc.getYaw() + ")");
                    }
                });
            } else {
                GridManager.this.plugin.getDebugManager().logRaceSystem(
                    "[GRID] Falha ao teleportar " + player.getName() + " para o grid (" + gridLoc.getWorld().getName() + "," + gridLoc.getBlockX() + "," + gridLoc.getBlockY() + "," + gridLoc.getBlockZ() + ") — sem barco."
                );
            }
            });
        });
    }
}
