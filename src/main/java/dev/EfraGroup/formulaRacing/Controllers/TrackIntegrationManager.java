package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.GridPosition;
import dev.EfraGroup.formulaRacing.Database.Track;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;

public class TrackIntegrationManager {
    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, List<DatabaseManager.RegionData>> checkpointCache = new ConcurrentHashMap();
    private final Map<String, List<DatabaseManager.RegionData>> regionsCache = new ConcurrentHashMap();
    private final Map<String, Map<Integer, List<DatabaseManager.RegionData>>> checkpointsByIdCache = new ConcurrentHashMap();
    private final Map<String, Boolean> noResetOnFutureCheckpointTracks = new ConcurrentHashMap<>();

    public TrackIntegrationManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.dm;
    }

    public void clearCheckpointCache(String trackNameWS) {
        if (trackNameWS == null) {
            this.checkpointCache.clear();
            this.checkpointsByIdCache.clear();
            this.regionsCache.clear();
        } else {
            this.checkpointCache.remove(trackNameWS);
            this.checkpointsByIdCache.remove(trackNameWS);
            this.regionsCache.remove(trackNameWS);
        }

    }

    public void clearRegionsCache() {
        this.regionsCache.clear();
    }

    public void clearRegionsCache(String trackNameWS) {
        if (trackNameWS == null) {
            this.regionsCache.clear();
        } else {
            this.regionsCache.remove(trackNameWS);
        }
    }

    public TrackValidationResult validateTrack(String trackNameWS) {
        if (trackNameWS != null && !trackNameWS.isEmpty()) {
            DatabaseManager.TrackData trackData = this.databaseManager.getTrackData(trackNameWS);
            if (trackData == null) {
                return new TrackValidationResult(false, "Track not found: " + trackNameWS);
            } else {
                Location spawnLocation = trackData.getSpawnLocation();
                if (spawnLocation != null && spawnLocation.getWorld() != null) {
                    int checkpointCount = trackData.getTotalCheckpoints();
                    return checkpointCount == 0 ? new TrackValidationResult(false, "Track has no checkpoints configured") : new TrackValidationResult(true, "Valid track", trackData);
                } else {
                    return new TrackValidationResult(false, "Track has no valid spawn point");
                }
            }
        } else {
            return new TrackValidationResult(false, "Track name cannot be empty");
        }
    }

    public Location getTrackSpawn(String trackNameWS) {
        return this.databaseManager.getTrackSpawn(trackNameWS);
    }

    public DatabaseManager.TrackData getTrackData(String trackNameWS) {
        return this.databaseManager.getTrackData(trackNameWS);
    }

    public Track getTrack(String trackNameWS) {
        DatabaseManager.TrackData trackData = this.databaseManager.getTrackData(trackNameWS);
        return trackData == null ? null : new Track(trackData, trackNameWS);
    }

    public List<DatabaseManager.RegionData> getTrackCheckpoints(String trackNameWS) {
        return (List)this.checkpointCache.computeIfAbsent(trackNameWS, (k) -> {
            List<DatabaseManager.RegionData> regions = this.databaseManager.getCheckpoints(k);
            return regions != null ? regions : new ArrayList<>();
        });
    }

    public int getCheckpointCount(String trackNameWS) {
        List<DatabaseManager.RegionData> checkpoints = this.getTrackCheckpoints(trackNameWS);
        if (checkpoints == null || checkpoints.isEmpty()) {
            return 0;
        }
        java.util.Set<Integer> uniqueIds = new java.util.HashSet<>();
        for (DatabaseManager.RegionData region : checkpoints) {
            uniqueIds.add(region.getId());
        }
        return uniqueIds.size();
    }

    public Map<Integer, List<DatabaseManager.RegionData>> getCheckpointsById(String trackNameWS) {
        return this.checkpointsByIdCache.computeIfAbsent(trackNameWS, k -> {
            List<DatabaseManager.RegionData> checkpoints = this.getTrackCheckpoints(k);
            Map<Integer, List<DatabaseManager.RegionData>> grouped = new HashMap<>();
            for (DatabaseManager.RegionData region : checkpoints) {
                int cpId = region.getId();
                grouped.computeIfAbsent(cpId, id -> new ArrayList<>()).add(region);
            }
            return grouped;
        });
    }

    /**
     * Checkpoint ids in track order (ascending). Checkpoint ids are NOT
     * guaranteed to be sequential (deleting and re-creating a checkpoint
     * leaves gaps, e.g. 1,3,5) — logic must map the lap ordinal (how many
     * checkpoints were passed) to the real id through this list, never
     * assume id == ordinal + 1.
     */
    public List<Integer> getOrderedCheckpointIds(String trackNameWS) {
        return this.getCheckpointsById(trackNameWS).keySet().stream().sorted().toList();
    }

    public List<DatabaseManager.RegionData> getCheckpointById(String trackNameWS, int checkpointId) {
        return getCheckpointsById(trackNameWS).get(checkpointId);
    }

    public List<DatabaseManager.RegionData> getTrackRegionsByType(String trackNameWS, String regionType) {
        List<DatabaseManager.RegionData> allRegions = this.regionsCache.computeIfAbsent(trackNameWS, k -> {
            List<DatabaseManager.RegionData> regions = this.databaseManager.getAllRegions();
            return regions.stream()
                    .filter(r -> r.getTrackNameWS().equalsIgnoreCase(trackNameWS))
                    .toList();
        });
        if (regionType == null) {
            return allRegions;
        }
        return allRegions.stream()
                .filter(r -> r.getType().equalsIgnoreCase(regionType))
                .toList();
    }

    public boolean hasLagStartRegion(String trackNameWS) {
        return !getTrackRegionsByType(trackNameWS, "LAGSTART").isEmpty();
    }

    public boolean hasLagEndRegion(String trackNameWS) {
        return !getTrackRegionsByType(trackNameWS, "LAGEND").isEmpty();
    }

    public boolean getNoResetOnFutureCheckpoint(String trackNameWS) {
        return this.noResetOnFutureCheckpointTracks.getOrDefault(trackNameWS, false);
    }

    public void setNoResetOnFutureCheckpoint(String trackNameWS, boolean value) {
        this.noResetOnFutureCheckpointTracks.put(trackNameWS, value);
    }

    public int getGridPositionCount(String trackNameWS) {
        return this.getTrackGridLocations(trackNameWS).size();
    }

    public int getQualiGridPositionCount(String trackNameWS) {
        return this.databaseManager.getQualiGridPositions(trackNameWS).size();
    }

    public List<Location> generateQualiGridPositions(String trackNameWS, int maxPositions) {
        List<Location> gridLocations = new ArrayList();
        List<GridPosition> qualGrids = this.databaseManager.getQualiGridPositions(trackNameWS);
        this.plugin.getDebugManager().logRaceSystem("[QUALIGRID DEBUG] Posições encontradas: " + qualGrids.size());
        int limit = Math.min(qualGrids.size(), maxPositions);
        for (int i = 0; i < limit; i++) {
            GridPosition gridPos = qualGrids.get(i);
            Location loc = gridPos.toLocation(this.plugin.getServer());
            if (loc != null) {
                gridLocations.add(loc);
            }
        }
        return gridLocations;
    }

    public List<Location> generateGridPositions(String trackNameWS, int maxPositions) {
        List<Location> gridLocations = this.getTrackGridLocations(trackNameWS);
        if (gridLocations.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem(
                "No GRID positions defined for " + trackNameWS +
                " — grid is NEVER auto-generated from spawn; define them in /trackedit."
            );
            return List.of();
        }
        this.plugin.getDebugManager().logRaceSystem(
            "Using " + gridLocations.size() + " GRID positions defined in /trackedit for " + trackNameWS
        );
        if (gridLocations.size() > maxPositions) {
            return gridLocations.subList(0, maxPositions);
        }
        // Fewer positions than drivers: return what exists — the caller loads
        // the drivers that fit and warns about the missing slots.
        return gridLocations;
    }

    private List<Location> getTrackGridLocations(String trackNameWS) {
        List<Location> gridLocations = new ArrayList();
        List<GridPosition> gridPositions = this.databaseManager.getGridPositions(trackNameWS);
        this.plugin.getDebugManager().logRaceSystem("[GRID DEBUG] Posições GRID encontradas em fr_grid_positions: " + gridPositions.size());

        for(GridPosition gridPos : gridPositions) {
            Location loc = gridPos.toLocation(this.plugin.getServer());
            if (loc != null) {
                gridLocations.add(loc);
                DebugManager var10000 = this.plugin.getDebugManager();
                int var10001 = gridPos.getPosition();
                var10000.logRaceSystem("[GRID DEBUG] P" + var10001 + " em " + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
            }
        }

        return gridLocations;
    }

    public boolean isPlayerInCheckpoint(Location playerLocation, DatabaseManager.RegionData checkpoint) {
        if (playerLocation != null && checkpoint != null) {
            if (!playerLocation.getWorld().getName().equals(checkpoint.getWorld())) {
                return false;
            } else {
                double px = playerLocation.getX();
                double py = playerLocation.getY();
                double pz = playerLocation.getZ();
                double minX = Math.min(checkpoint.getMinX(), checkpoint.getMaxX());
                double maxX = Math.max(checkpoint.getMinX(), checkpoint.getMaxX());
                double minY = Math.min(checkpoint.getMinY(), checkpoint.getMaxY());
                double maxY = Math.max(checkpoint.getMinY(), checkpoint.getMaxY());
                double minZ = Math.min(checkpoint.getMinZ(), checkpoint.getMaxZ());
                double maxZ = Math.max(checkpoint.getMinZ(), checkpoint.getMaxZ());
                return px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ;
            }
        } else {
            return false;
        }
    }

    public static class TrackValidationResult {
        private final boolean valid;
        private final String message;
        private final DatabaseManager.TrackData trackData;

        public TrackValidationResult(boolean valid, String message) {
            this(valid, message, (DatabaseManager.TrackData)null);
        }

        public TrackValidationResult(boolean valid, String message, DatabaseManager.TrackData trackData) {
            this.valid = valid;
            this.message = message;
            this.trackData = trackData;
        }

        public boolean isValid() {
            return this.valid;
        }

        public String getMessage() {
            return this.message;
        }

        public DatabaseManager.TrackData getTrackData() {
            return this.trackData;
        }
    }
}
