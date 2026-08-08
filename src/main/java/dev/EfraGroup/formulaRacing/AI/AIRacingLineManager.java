package dev.EfraGroup.formulaRacing.AI;

import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

    private static final String LINES_FOLDER = "ailines";

    private final FormulaRacing plugin;
    private final Map<String, AIRacingLine> racingLines;
    private File linesDir;
    private AIRacingLineRecorder recorder;

    public AIRacingLineManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.racingLines = new HashMap<>();
        this.linesDir = new File(plugin.getDataFolder(), LINES_FOLDER);
        if (!this.linesDir.exists()) {
            this.linesDir.mkdirs();
        }
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
     * Saves all racing lines to individual binary files in the ailines/ folder.
     */
    public void saveAllRacingLines() {
        int savedCount = 0;
        for (Map.Entry<String, AIRacingLine> entry : racingLines.entrySet()) {
            if (entry.getValue().isUsable() && saveRacingLine(entry.getKey(), entry.getValue())) {
                savedCount++;
            }
        }
        plugin.getDebugManager().logRaceSystem("[AI] Racing lines saved (" + savedCount + " tracks) to " + LINES_FOLDER + "/");
    }

    /**
     * Saves a single racing line to its binary file (incremental).
     */
    public boolean saveRacingLine(String normalizedTrackName, AIRacingLine line) {
        if (line == null || !line.isUsable()) {
            return false;
        }

        File file = new File(linesDir, sanitizeFileName(normalizedTrackName) + ".bin");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            line.writeTo(out);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[AI] Error saving racing line for " + normalizedTrackName + ": " + e.getMessage());
            return false;
        }
    }

    public void deleteRacingLine(String normalizedTrackName) {
        File file = new File(linesDir, sanitizeFileName(normalizedTrackName) + ".bin");
        file.delete();
        racingLines.remove(normalizedTrackName);
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Loads all racing lines from individual binary files in the ailines/ folder.
     */
    public void loadAllRacingLines() {
        File[] files = linesDir.listFiles((dir, name) -> name.endsWith(".bin"));
        if (files == null) {
            plugin.getDebugManager().logRaceSystem("[AI] Folder " + LINES_FOLDER + "/ not found. No lines loaded.");
            return;
        }

        // One-time migration from legacy YAML format if present
        if (files.length == 0) {
            migrateFromYaml();
            files = linesDir.listFiles((dir, name) -> name.endsWith(".bin"));
            if (files == null || files.length == 0) {
                plugin.getDebugManager().logRaceSystem("[AI] No racing lines found in " + LINES_FOLDER + "/");
                return;
            }
        }

        int loadedCount = 0;
        for (File file : files) {
            String trackKey = file.getName().replace(".bin", "");
            try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
                AIRacingLine line = AIRacingLine.readFrom(in, trackKey);
                if (line.isUsable()) {
                    racingLines.put(trackKey, line);
                    loadedCount++;
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[AI] Error loading racing line from " + file.getName() + ": " + e.getMessage());
            }
        }

        plugin.getDebugManager().logRaceSystem("[AI] " + loadedCount + " racing line(s) loaded from " + LINES_FOLDER + "/");
    }

    /**
     * Migrates racing lines from the legacy YAML file (ai_racing_lines.yml) into
     * individual binary files. Runs once; the legacy file is renamed afterwards.
     */
    private void migrateFromYaml() {
        File legacyFile = new File(plugin.getDataFolder(), "ai_racing_lines.yml");
        if (!legacyFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection linesSection = yaml.getConfigurationSection("lines");
        if (linesSection == null) {
            legacyFile.renameTo(new File(plugin.getDataFolder(), "ai_racing_lines.yml.migrated"));
            return;
        }

        int migrated = 0;
        for (String trackKey : linesSection.getKeys(false)) {
            String basePath = "lines." + trackKey;
            AIRacingLine line = new AIRacingLine(trackKey);

            loadYamlPoints(yaml, basePath + ".ideal", line, PointType.IDEAL);
            loadYamlPoints(yaml, basePath + ".braking", line, PointType.BRAKING);
            loadYamlPoints(yaml, basePath + ".acceleration", line, PointType.ACCELERATION);

            if (line.isUsable()) {
                saveRacingLine(trackKey, line);
                racingLines.put(trackKey, line);
                migrated++;
            }
        }

        legacyFile.renameTo(new File(plugin.getDataFolder(), "ai_racing_lines.yml.migrated"));
        plugin.getDebugManager().logRaceSystem("[AI] Migrated " + migrated + " racing line(s) from legacy YAML to binary format.");
    }

    private enum PointType { IDEAL, BRAKING, ACCELERATION }

    private void loadYamlPoints(YamlConfiguration yaml, String sectionPath, AIRacingLine line, PointType type) {
        ConfigurationSection section = yaml.getConfigurationSection(sectionPath);
        if (section == null) {
            return;
        }

        List<Integer> indices = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                indices.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {}
        }
        indices.sort(Integer::compareTo);

        for (int i : indices) {
            String pointPath = sectionPath + "." + i;
            String worldName = yaml.getString(pointPath + ".world", "");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }
            Location loc = new Location(
                    world,
                    yaml.getDouble(pointPath + ".x"),
                    yaml.getDouble(pointPath + ".y"),
                    yaml.getDouble(pointPath + ".z")
            );
            if (type == PointType.IDEAL) {
                double speed = yaml.getDouble(pointPath + ".speed", 0.5);
                line.addIdealLinePoint(loc, speed);
            } else if (type == PointType.BRAKING) {
                line.addBrakingPoint(loc);
            } else {
                line.addAccelerationPoint(loc);
            }
        }
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
