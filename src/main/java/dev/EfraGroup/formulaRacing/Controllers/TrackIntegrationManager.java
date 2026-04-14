//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

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
                return new TrackValidationResult(false, "Pista não encontrada: " + trackNameWS);
            } else {
                Location spawnLocation = trackData.getSpawnLocation();
                if (spawnLocation != null && spawnLocation.getWorld() != null) {
                    int checkpointCount = trackData.getTotalCheckpoints();
                    return checkpointCount == 0 ? new TrackValidationResult(false, "Pista sem checkpoints configurados") : new TrackValidationResult(true, "Pista válida", trackData);
                } else {
                    return new TrackValidationResult(false, "Pista sem spawn point válido");
                }
            }
        } else {
            return new TrackValidationResult(false, "Nome da pista não pode ser vazio");
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

    public List<Location> generateGridPositions(String trackNameWS, int maxPositions) {
        List<Location> gridLocations = this.getTrackGridLocations(trackNameWS);
        if (gridLocations.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("Nenhuma posição GRID definida - gerando automaticamente para " + trackNameWS);
            return this.generateGridPositionsFromSpawn(trackNameWS, maxPositions);
        } else {
            DebugManager var10000 = this.plugin.getDebugManager();
            int var10001 = gridLocations.size();
            var10000.logRaceSystem("Usando " + var10001 + " posições GRID definidas no /trackedit para " + trackNameWS);
            if (gridLocations.size() >= maxPositions) {
                return gridLocations.subList(0, maxPositions);
            } else {
                var10000 = this.plugin.getDebugManager();
                var10001 = gridLocations.size();
                var10000.logRaceSystem("A pista possui apenas " + var10001 + " posições de grid. Gerando mais " + (maxPositions - gridLocations.size()) + " automaticamente.");
                List<Location> auto = this.generateGridPositionsFromSpawn(trackNameWS, maxPositions);
                List<Location> combined = new ArrayList(maxPositions);
                combined.addAll(gridLocations);

                for(int i = gridLocations.size(); i < maxPositions && i < auto.size(); ++i) {
                    combined.add((Location)auto.get(i));
                }

                return combined;
            }
        }
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

    private List<Location> generateGridPositionsFromSpawn(String trackNameWS, int maxPositions) {
        Location spawnPoint = this.getTrackSpawn(trackNameWS);
        if (spawnPoint == null) {
            this.plugin.getDebugManager().logRaceSystem("Não foi possível gerar grid: spawn point não encontrado para " + trackNameWS);
            return List.of();
        } else {
            List<Location> gridPositions = new ArrayList();
            double lateralSpacing = (double)3.0F;
            double forwardSpacing = (double)4.0F;
            double yaw = (double)spawnPoint.getYaw();
            double pitch = (double)spawnPoint.getPitch();
            double radYaw = Math.toRadians(yaw);
            double forwardX = -Math.sin(radYaw);
            double forwardZ = Math.cos(radYaw);
            double lateralX = Math.cos(radYaw);
            double lateralZ = Math.sin(radYaw);

            for(int i = 0; i < maxPositions; ++i) {
                int row = i / 2;
                int column = i % 2;
                double lateralOffset = column == 0 ? -lateralSpacing / (double)2.0F : lateralSpacing / (double)2.0F;
                double x = spawnPoint.getX() - (double)row * forwardSpacing * forwardX + lateralOffset * lateralX;
                double y = spawnPoint.getY();
                double z = spawnPoint.getZ() - (double)row * forwardSpacing * forwardZ + lateralOffset * lateralZ;
                Location gridPos = new Location(spawnPoint.getWorld(), x, y, z, (float)yaw, (float)pitch);
                gridPositions.add(gridPos);
            }

            this.plugin.getDebugManager().logRaceSystem("Grid de largada gerado para " + trackNameWS + ": " + gridPositions.size() + " posições");
            return gridPositions;
        }
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
