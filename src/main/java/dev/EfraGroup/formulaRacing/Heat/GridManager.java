package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

public class GridManager {
    /** How many drivers are teleported/spawned per tick when loading a grid in batches. */
    private static final int DRIVERS_PER_TICK = 5;
    private final FormulaRacing plugin;
    private final Heats heat;
    private final List<Location> gridPositions;
    // Read by region threads (jump-start flag) while the global thread
    // mutates it on freeze/unfreeze — must be a concurrent set.
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    /** Jump-start penalties (F1 start): ticks a driver stays anchored after lights out. */
    private final java.util.Map<UUID, Long> jumpStartPenalties = new java.util.concurrent.ConcurrentHashMap<>();
    // Grid loading is spread over several ticks (DRIVERS_PER_TICK per tick) instead of
    // teleporting and spawning every boat in one tick, which spiked the server on large
    // grids. Concurrent: enqueue and flush may run from async threads.
    private final ConcurrentLinkedDeque<Driver> pendingPlacements = new ConcurrentLinkedDeque<>();
    private volatile Consumer<Driver> pendingPlacementAction;
    private volatile FRTask placementTask;

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
        if (trackNameWS == null || trackNameWS.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("Heat " + this.heat.getId() + " has no track configured!");
            return false;
        }

        TrackIntegrationManager trackManager = this.plugin.getTrackIntegrationManager();
        // The cache must hold EVERY grid the track has:
        //  * never cap it by heat.getMaxDrivers() — that value is a stale snapshot taken
        //    when the track was assigned to the heat (0 when no grids existed yet), so
        //    grids added later were silently truncated to zero and the heat reported
        //    "no GRIDs defined" until a server restart rebuilt the heat and re-ran
        //    setTrackNameWS.
        //  * never cap it by the current driver count either. load/teleport run as soon as
        //    the FIRST driver joins, so capping by the driver count cached a single
        //    position and then pinned the heat capacity to it (see below), which rejected
        //    every later driver with "heat full" and made late joiners fail to be placed.
        this.gridPositions.clear();
        this.gridPositions.addAll(trackManager.generateGridPositions(trackNameWS, Integer.MAX_VALUE));
        if (this.gridPositions.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("Could not generate grid for track: " + trackNameWS);
            return false;
        }

        // NOTE: deliberately NOT calling heat.setMaxDrivers() here. setMaxDrivers stores a
        // non-null value that always wins in getMaxDriversLimit(), so deriving the capacity
        // from this snapshot pinned it to whatever was enrolled at the time (1 for the first
        // driver of a Quick Race / voterace event). Capacity belongs to the heat, not to a
        // transient view of the grid.
        DebugManager var10000 = this.plugin.getDebugManager();
        int var10001 = this.heat.getId();
        var10000.logRaceSystem("Grid generated for Heat " + var10001 + ": " + this.gridPositions.size() + " positions");
        return true;
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
                // Validate and filter at queue time so the caller keeps its "nobody could
                // be placed" signal (all offline / invalid grid slots), then hand the rest
                // to the batched placer (a few drivers per tick, see placeDriversInBatches).
                List<Driver> placeable = new ArrayList<>();

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
                            placeable.add(driver);
                        } else {
                            this.plugin.getDebugManager().logRaceSystem("Offline driver cannot be teleported: " + String.valueOf(driver.getUuid()));
                        }
                    } else {
                        DebugManager var8 = this.plugin.getDebugManager();
                        String var9 = String.valueOf(driver.getUuid());
                        var8.logRaceSystem("Invalid grid position for driver " + var9 + ": P" + (gridPosition + 1) + " (index: " + gridPosition + ")");
                    }
                }

                int queued = this.placeDriversInBatches(placeable, this::placeDriverOnGrid);
                this.plugin.getDebugManager().logRaceSystem("Teleported " + queued + "/" + drivers.size() + " drivers to grid (batched at " + DRIVERS_PER_TICK + "/tick)");
                return queued;
            }
        }
    }

    /**
     * Places a single driver on the grid. Runs from the batched placement task — the
     * player and the grid slot are re-checked here because both can change while the
     * driver waits in the queue.
     */
    private void placeDriverOnGrid(Driver driver) {
        Player player = this.plugin.getServer().getPlayer(driver.getUuid());
        if (player == null || !player.isOnline()) {
            this.plugin.getDebugManager().logRaceSystem("Offline driver cannot be teleported: " + String.valueOf(driver.getUuid()));
            return;
        }

        int gridPosition = driver.getStartPosition() - 1;
        if (gridPosition < 0 || gridPosition >= this.gridPositions.size()) {
            this.plugin.getDebugManager().logRaceSystem(
                "Invalid grid position for driver " + String.valueOf(driver.getUuid()) + ": P" + (gridPosition + 1) + " (index: " + gridPosition + ")");
            return;
        }

        Location gridLoc = this.gridPositions.get(gridPosition);
        this.spawnBoatWithTrackConfig(player, gridLoc, driver);
        this.plugin.getDebugManager().logRaceSystem("Driver " + player.getName() + " teleported to P" + (gridPosition + 1));
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
        // Row-based start delay (parity with TimingSystem's row start delay): when the
        // track defines gridsPerRow and the heat defines a rowStartDelay, each grid row
        // launches that many ms after the previous one — spreading the boat physics
        // burst at lights out. Without those settings every driver releases at once,
        // exactly as before.
        int gridsPerRow = 0;
        int rowStartDelayMs = Math.max(0, this.heat.getRowStartDelayMs());
        if (rowStartDelayMs > 0) {
            gridsPerRow = Math.max(0, this.plugin.getTrackIntegrationManager().getGridsPerRow(this.heat.getTrackNameWS()));
        }

        for(UUID playerUUID : this.frozenPlayers) {
            long penaltyTicks = this.jumpStartPenalties.getOrDefault(playerUUID, 0L);
            long rowDelayTicks = 0L;
            if (gridsPerRow > 0) {
                Driver driver = this.heat.getDrivers().get(playerUUID);
                int gridSlot = driver != null ? Math.max(0, driver.getStartPosition() - 1) : 0;
                rowDelayTicks = ((long) (gridSlot / gridsPerRow)) * rowStartDelayMs / 50L;
            }

            long releaseTicks = rowDelayTicks + penaltyTicks;
            if (releaseTicks > 0L) {
                // Later grid row and/or jump-start penalty: stay anchored while the
                // earlier rows launch.
                SchedulerHelper.runTaskLater(this.plugin, () -> {
                    Player delayed = this.plugin.getServer().getPlayer(playerUUID);
                    if (delayed != null && delayed.isOnline()) {
                        this.plugin.getAPI().releaseBoat(delayed);
                    }
                }, releaseTicks);
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

    /**
     * Places drivers on the grid a few per tick instead of teleporting and spawning every
     * boat in a single tick — large grids used to spike the server when loaded (parity
     * with TimingSystem's batched grid loading). The first batch runs immediately, the
     * rest are spread by a one-tick timer. Returns how many drivers were queued.
     */
    public int placeDriversInBatches(List<Driver> drivers, Consumer<Driver> placement) {
        this.cancelPendingPlacements();
        this.pendingPlacementAction = placement;
        this.pendingPlacements.addAll(drivers);
        int queued = this.pendingPlacements.size();
        this.placeNextBatch();
        if (this.pendingPlacements.isEmpty()) {
            return queued;
        }
        this.placementTask = SchedulerHelper.runTaskTimer(this.plugin, () -> {
            this.placeNextBatch();
            if (this.pendingPlacements.isEmpty()) {
                this.cancelPendingPlacements();
            }
        }, 1L, 1L);
        return queued;
    }

    private void placeNextBatch() {
        Consumer<Driver> placement = this.pendingPlacementAction;
        if (placement == null) {
            this.pendingPlacements.clear();
            return;
        }
        for (int i = 0; i < DRIVERS_PER_TICK; i++) {
            Driver driver = this.pendingPlacements.poll();
            if (driver == null) {
                break;
            }
            placement.accept(driver);
        }
    }

    /**
     * Places every queued driver immediately and stops the batch timer. Used before a
     * countdown starts so nobody is left without a boat when the lights go out.
     */
    public void placeRemainingDriversNow() {
        Consumer<Driver> placement = this.pendingPlacementAction;
        if (placement != null) {
            Driver driver;
            while ((driver = this.pendingPlacements.poll()) != null) {
                placement.accept(driver);
            }
        }
        this.cancelPendingPlacements();
    }

    /** Drops any pending placements and stops the batch timer (heat cleared/reset). */
    public void cancelPendingPlacements() {
        this.pendingPlacements.clear();
        FRTask task = this.placementTask;
        if (task != null) {
            task.cancel();
            this.placementTask = null;
        }
    }

    public void clear() {
        this.cancelPendingPlacements();
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
