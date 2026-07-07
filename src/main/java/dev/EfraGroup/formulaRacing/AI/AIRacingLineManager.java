package dev.EfraGroup.formulaRacing.AI;

import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
     * Manages all AI racing lines on the server.
 */
public class AIRacingLineManager {

    private static final String LINES_FILE = "ai_racing_lines.yml";

    private final FormulaRacing plugin;
    private final Map<String, AIRacingLine> racingLines;
    private AIRacingLineRecorder recorder;

    public AIRacingLineManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.racingLines = new HashMap<>();
    }

    /**
     * Initializes the manager: loads saved lines and initializes the recorder.
     * Should be called after the plugin is fully loaded.
     */
    public void initialize() {
        loadAllRacingLines();
        initializeRecorder();
    }

    public void initializeRecorder() {
        if (recorder == null) {
            recorder = new AIRacingLineRecorder(plugin, this);
        }
    }

    public AIRacingLineRecorder getRecorder() {
        if (recorder == null) {
            initializeRecorder();
        }
        return recorder;
    }

    public AIRacingLine getRacingLine(String trackName) {
        return racingLines.computeIfAbsent(normalizeTrackName(trackName), AIRacingLine::new);
    }

    public void removeRacingLine(String trackName) {
        racingLines.remove(normalizeTrackName(trackName));
    }

    public boolean hasRacingLine(String trackName) {
        AIRacingLine line = racingLines.get(normalizeTrackName(trackName));
        return line != null && line.isUsable();
    }

    public Optional<AIRacingLine> getRacingLineIfExists(String trackName) {
        return Optional.ofNullable(racingLines.get(normalizeTrackName(trackName)))
                .filter(AIRacingLine::isUsable);
    }

    public void clearAll() {
        racingLines.clear();
    }

    public int getTrackCount() {
        return (int) racingLines.values().stream().filter(AIRacingLine::isUsable).count();
    }

    public void generateBasicRacingLine(String trackName) {
        String normalizedTrack = normalizeTrackName(trackName);
        TrackIntegrationManager trackManager = plugin.getTrackIntegrationManager();
        Location spawn = trackManager.getTrackSpawn(normalizedTrack);
        List<DatabaseManager.RegionData> checkpoints = new ArrayList<>(trackManager.getTrackCheckpoints(normalizedTrack));

        if (spawn == null || checkpoints.isEmpty()) {
            plugin.getDebugManager().logRaceSystem("[AI] Could not generate basic line for " + normalizedTrack + ": track has no spawn or checkpoints.");
            return;
        }

        checkpoints.sort(Comparator.comparingInt(DatabaseManager.RegionData::getId));

        AIRacingLine line = getRacingLine(normalizedTrack);
        line.clear();

        List<Location> anchors = new ArrayList<>();
        anchors.add(spawn.clone());
        for (DatabaseManager.RegionData checkpoint : checkpoints) {
            Location center = getRegionCenter(checkpoint);
            if (center != null) {
                anchors.add(center);
            }
        }
        anchors.add(spawn.clone());

        for (int i = 0; i < anchors.size() - 1; i++) {
            Location start = anchors.get(i);
            Location end = anchors.get(i + 1);
            if (start.getWorld() == null || end.getWorld() == null || !start.getWorld().equals(end.getWorld())) {
                continue;
            }

            double segmentDistance = Math.max(1.0, start.distance(end));
            int steps = Math.max(2, (int) Math.ceil(segmentDistance / 2.0));
            double segmentSpeed = calculateAnchorSpeed(anchors, i);

            for (int step = 0; step < steps; step++) {
                double t = (double) step / (double) steps;
                line.addIdealLinePoint(interpolate(start, end, t), segmentSpeed);
            }
        }

        plugin.getDebugManager().logRaceSystem("[AI] Basic line generated for " + normalizedTrack + " with " + line.getIdealLineSize() + " points.");
    }

    /**
     * Saves all racing lines to a YAML file in the plugin folder.
     */
    public void saveAllRacingLines() {
        File file = new File(plugin.getDataFolder(), LINES_FILE);
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, AIRacingLine> entry : racingLines.entrySet()) {
            String trackKey = entry.getKey();
            AIRacingLine line = entry.getValue();

            if (!line.isUsable()) {
                continue;
            }

            String basePath = "lines." + trackKey;

            // Save ideal points
            List<Location> idealLine = line.getIdealLine();
            for (int i = 0; i < idealLine.size(); i++) {
                Location loc = idealLine.get(i);
                String pointPath = basePath + ".ideal." + i;
                yaml.set(pointPath + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "");
                yaml.set(pointPath + ".x", loc.getX());
                yaml.set(pointPath + ".y", loc.getY());
                yaml.set(pointPath + ".z", loc.getZ());
                yaml.set(pointPath + ".speed", line.getIdealSpeedAtIndex(i));
            }

            // Save braking points
            List<Location> brakingPoints = line.getBrakingPoints();
            for (int i = 0; i < brakingPoints.size(); i++) {
                Location loc = brakingPoints.get(i);
                String pointPath = basePath + ".braking." + i;
                yaml.set(pointPath + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "");
                yaml.set(pointPath + ".x", loc.getX());
                yaml.set(pointPath + ".y", loc.getY());
                yaml.set(pointPath + ".z", loc.getZ());
            }

            // Save acceleration points
            List<Location> accelPoints = line.getAccelerationPoints();
            for (int i = 0; i < accelPoints.size(); i++) {
                Location loc = accelPoints.get(i);
                String pointPath = basePath + ".acceleration." + i;
                yaml.set(pointPath + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "");
                yaml.set(pointPath + ".x", loc.getX());
                yaml.set(pointPath + ".y", loc.getY());
                yaml.set(pointPath + ".z", loc.getZ());
            }
        }

        try {
            yaml.save(file);
            plugin.getDebugManager().logRaceSystem("[AI] Racing lines saved to " + LINES_FILE + " (" + racingLines.size() + " tracks).");
        } catch (IOException e) {
            plugin.getLogger().warning("[AI] Error saving racing lines: " + e.getMessage());
        }
    }

    /**
     * Loads all racing lines from the YAML file.
     */
    public void loadAllRacingLines() {
        File file = new File(plugin.getDataFolder(), LINES_FILE);
        if (!file.exists()) {
            plugin.getDebugManager().logRaceSystem("[AI] File " + LINES_FILE + " not found. No lines loaded.");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection linesSection = yaml.getConfigurationSection("lines");
        if (linesSection == null) {
            return;
        }

        int loadedCount = 0;
        for (String trackKey : linesSection.getKeys(false)) {
            String basePath = "lines." + trackKey;
            AIRacingLine line = getRacingLine(trackKey);
            line.clear();

            // Load ideal points
            ConfigurationSection idealSection = yaml.getConfigurationSection(basePath + ".ideal");
            if (idealSection != null) {
                // Sort by numerical index
                List<Integer> indices = new ArrayList<>();
                for (String key : idealSection.getKeys(false)) {
                    try {
                        indices.add(Integer.parseInt(key));
                    } catch (NumberFormatException ignored) {}
                }
                indices.sort(Integer::compareTo);

                for (int i : indices) {
                    String pointPath = basePath + ".ideal." + i;
                    String worldName = yaml.getString(pointPath + ".world", "");
                    double x = yaml.getDouble(pointPath + ".x");
                    double y = yaml.getDouble(pointPath + ".y");
                    double z = yaml.getDouble(pointPath + ".z");
                    double speed = yaml.getDouble(pointPath + ".speed", 0.5);

                    World world = Bukkit.getWorld(worldName);
                    Location loc = new Location(world, x, y, z);
                    line.addIdealLinePoint(loc, speed);
                }
            }

            // Load braking points
            ConfigurationSection brakingSection = yaml.getConfigurationSection(basePath + ".braking");
            if (brakingSection != null) {
                List<Integer> indices = new ArrayList<>();
                for (String key : brakingSection.getKeys(false)) {
                    try {
                        indices.add(Integer.parseInt(key));
                    } catch (NumberFormatException ignored) {}
                }
                indices.sort(Integer::compareTo);

                for (int i : indices) {
                    String pointPath = basePath + ".braking." + i;
                    String worldName = yaml.getString(pointPath + ".world", "");
                    double x = yaml.getDouble(pointPath + ".x");
                    double y = yaml.getDouble(pointPath + ".y");
                    double z = yaml.getDouble(pointPath + ".z");

                    World world = Bukkit.getWorld(worldName);
                    Location loc = new Location(world, x, y, z);
                    line.addBrakingPoint(loc);
                }
            }

            // Load acceleration points
            ConfigurationSection accelSection = yaml.getConfigurationSection(basePath + ".acceleration");
            if (accelSection != null) {
                List<Integer> indices = new ArrayList<>();
                for (String key : accelSection.getKeys(false)) {
                    try {
                        indices.add(Integer.parseInt(key));
                    } catch (NumberFormatException ignored) {}
                }
                indices.sort(Integer::compareTo);

                for (int i : indices) {
                    String pointPath = basePath + ".acceleration." + i;
                    String worldName = yaml.getString(pointPath + ".world", "");
                    double x = yaml.getDouble(pointPath + ".x");
                    double y = yaml.getDouble(pointPath + ".y");
                    double z = yaml.getDouble(pointPath + ".z");

                    World world = Bukkit.getWorld(worldName);
                    Location loc = new Location(world, x, y, z);
                    line.addAccelerationPoint(loc);
                }
            }

            if (line.isUsable()) {
                loadedCount++;
            }
        }

        plugin.getDebugManager().logRaceSystem("[AI] " + loadedCount + " racing line(s) loaded from " + LINES_FILE);
    }

    private String normalizeTrackName(String trackName) {
        return trackName == null ? "" : trackName.replace(" ", "").toLowerCase();
    }

    private Location getRegionCenter(DatabaseManager.RegionData checkpoint) {
        World world = plugin.getServer().getWorld(checkpoint.getWorld());
        if (world == null) {
            return null;
        }

        return new Location(
                world,
                (checkpoint.getMinX() + checkpoint.getMaxX()) / 2.0,
                (checkpoint.getMinY() + checkpoint.getMaxY()) / 2.0,
                (checkpoint.getMinZ() + checkpoint.getMaxZ()) / 2.0
        );
    }

    private Location interpolate(Location start, Location end, double t) {
        Location point = start.clone();
        point.setX(start.getX() + ((end.getX() - start.getX()) * t));
        point.setY(start.getY() + ((end.getY() - start.getY()) * t));
        point.setZ(start.getZ() + ((end.getZ() - start.getZ()) * t));
        return point;
    }

    private double calculateAnchorSpeed(List<Location> anchors, int anchorIndex) {
        if (anchorIndex <= 0 || anchorIndex >= anchors.size() - 2) {
            return 0.7;
        }

        Location previous = anchors.get(anchorIndex - 1);
        Location current = anchors.get(anchorIndex);
        Location next = anchors.get(anchorIndex + 1);

        double angle = Math.abs(normalizeAngle(yawBetween(previous, current) - yawBetween(current, next)));
        if (angle > 90.0) {
            return 0.4;
        }
        if (angle > 45.0) {
            return 0.55;
        }
        return 0.8;
    }

    private double yawBetween(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    private double normalizeAngle(double angle) {
        double normalized = angle % 360.0;
        if (normalized <= -180.0) {
            normalized += 360.0;
        } else if (normalized > 180.0) {
            normalized -= 360.0;
        }
        return normalized;
    }
}
