package dev.EfraGroup.formulaRacing.Utils.trackexchange;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.WorldEditSelect;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class TrackExchangeManager {

    private final FormulaRacing plugin;
    private final DatabaseManager db;
    private final Gson gson;
    private final File exportDir;

    public static class SimpleLocation {
        public double x, y, z;
        public float yaw, pitch;
        public String world;

        public SimpleLocation() {}

        public SimpleLocation(Location loc) {
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.yaw = loc.getYaw();
            this.pitch = loc.getPitch();
            this.world = loc.getWorld().getName();
        }

        public Location toBukkitLocation(World w) {
            return new Location(w, x, y, z, yaw, pitch);
        }
    }

    public static class TrackExchangeRegion {
        public int index;
        public String type;
        public String shape;
        public double minX, minY, minZ;
        public double maxX, maxY, maxZ;
        public List<double[]> points;
    }

    public static class TrackExchangeLocation {
        public int index;
        public String type;
        public SimpleLocation location;
    }

    public static class TrackExchangeData {
        public String owner;
        public SimpleLocation spawn;
        public SimpleLocation origin;
        public String worldName;
        public List<TrackExchangeRegion> regions;
        public List<TrackExchangeLocation> locations;
        public List<String> tags;
        public List<Integer> options;
        public String guiItem;
        public int weight;
        public String trackType;
        public int boatUtilsMode;
        public long dateCreated;
        public List<String> contributors;
    }

    public TrackExchangeManager(FormulaRacing plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.exportDir = new File(plugin.getDataFolder(), "trackexchange");
        if (!this.exportDir.exists()) {
            this.exportDir.mkdirs();
        }
    }

    public void exportTrack(Player player, String trackName, String fileName) {
        SchedulerHelper.runAsync(plugin, () -> {
            try {
                doExport(player, trackName, fileName);
            } catch (Exception e) {
                SchedulerHelper.runTask(plugin, () ->
                    player.sendMessage("§cErro ao exportar: " + e.getMessage())
                );
                plugin.getDebugManager().logDatabaseOperation("§c[TrackExchange] Export error: " + e);
                e.printStackTrace();
            }
        });
    }

    public void importTrack(Player player, String fileName, String newName) {
        SchedulerHelper.runAsync(plugin, () -> {
            try {
                doImport(player, fileName, newName);
            } catch (Exception e) {
                SchedulerHelper.runTask(plugin, () ->
                    player.sendMessage("§cErro ao importar: " + e.getMessage())
                );
                plugin.getDebugManager().logDatabaseOperation("§c[TrackExchange] Import error: " + e);
                e.printStackTrace();
            }
        });
    }

    private void doExport(Player player, String trackName, String fileName) throws IOException, SQLException {
        DebugManager debug = plugin.getDebugManager();
        debug.logDatabaseOperation("§6[TrackExchange] Iniciando exportação da pista '" + trackName + "'");

        String trackNameWS = trackName.replaceAll("\\s+", "").toLowerCase();

        // Read track data
        String trackSql = "SELECT trackName, trackNameWS, creatorUUID, creatorName, worldName, " +
            "spawnPoint_x, spawnPoint_y, spawnPoint_z, spawnPoint_yaw, spawnPoint_pitch " +
            "FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?)";
        String creatorUUID = null;
        String displayName = null;
        String worldName = null;
        double spawnX = 0, spawnY = 0, spawnZ = 0;
        float spawnYaw = 0, spawnPitch = 0;

        try (Connection conn = db.getOrConnect();
             PreparedStatement ps = conn.prepareStatement(trackSql)) {
            ps.setString(1, trackNameWS);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IOException("Pista '" + trackName + "' não encontrada.");
                }
                displayName = rs.getString("trackName");
                trackNameWS = rs.getString("trackNameWS");
                creatorUUID = rs.getString("creatorUUID");
                worldName = rs.getString("worldName");
                spawnX = rs.getDouble("spawnPoint_x");
                spawnY = rs.getDouble("spawnPoint_y");
                spawnZ = rs.getDouble("spawnPoint_z");
                spawnYaw = rs.getFloat("spawnPoint_yaw");
                spawnPitch = rs.getFloat("spawnPoint_pitch");
            }
        }

        String finalTrackNameWS = trackNameWS;
        String finalDisplayName = displayName;

        // Build TrackExchangeData
        TrackExchangeData data = new TrackExchangeData();
        data.owner = creatorUUID != null ? creatorUUID : player.getUniqueId().toString();
        data.spawn = new SimpleLocation();
        data.spawn.x = spawnX;
        data.spawn.y = spawnY;
        data.spawn.z = spawnZ;
        data.spawn.yaw = spawnYaw;
        data.spawn.pitch = spawnPitch;
        data.spawn.world = worldName;
        data.origin = new SimpleLocation(player.getLocation());
        data.worldName = worldName;
        data.regions = new ArrayList<>();
        data.locations = new ArrayList<>();
        data.tags = new ArrayList<>();
        data.options = new ArrayList<>();
        data.guiItem = "BIRCH_BOAT";
        data.weight = 0;
        data.trackType = "RACE";
        data.boatUtilsMode = 0;
        data.dateCreated = System.currentTimeMillis();
        data.contributors = new ArrayList<>();

        // Read regions from fr_regions
        String regionSql = "SELECT regionType, regionShape, worldName, min_x, min_y, min_z, max_x, max_y, max_z, points " +
            "FROM fr_regions WHERE LOWER(trackNameWS) = LOWER(?)";
        try (Connection conn = db.getOrConnect();
             PreparedStatement ps = conn.prepareStatement(regionSql)) {
            ps.setString(1, finalTrackNameWS);
            try (ResultSet rs = ps.executeQuery()) {
                int idx = 0;
                while (rs.next()) {
                    TrackExchangeRegion r = new TrackExchangeRegion();
                    r.index = idx++;
                    r.type = rs.getString("regionType");
                    String shape = rs.getString("regionShape");
                    r.shape = shape != null ? shape : "AABB";
                    r.minX = rs.getDouble("min_x");
                    r.minY = rs.getDouble("min_y");
                    r.minZ = rs.getDouble("min_z");
                    r.maxX = rs.getDouble("max_x");
                    r.maxY = rs.getDouble("max_y");
                    r.maxZ = rs.getDouble("max_z");

                    String pointsStr = rs.getString("points");
                    if (pointsStr != null && !pointsStr.isEmpty()) {
                        r.points = new ArrayList<>();
                        String[] pairs = pointsStr.split(";");
                        for (String pair : pairs) {
                            String[] coords = pair.split(",");
                            if (coords.length >= 2) {
                                r.points.add(new double[]{
                                    Double.parseDouble(coords[0]),
                                    Double.parseDouble(coords[1])
                                });
                            }
                        }
                    }

                    data.regions.add(r);
                }
            }
        }

        // Read checkpoints from fr_checkpoint as TrackExchange locations
        String cpSql = "SELECT checkpointId, worldName, min_x, min_y, min_z, max_x, max_y, max_z " +
            "FROM fr_checkpoint WHERE LOWER(trackNameWS) = LOWER(?) ORDER BY checkpointId ASC";
        try (Connection conn = db.getOrConnect();
             PreparedStatement ps = conn.prepareStatement(cpSql)) {
            ps.setString(1, finalTrackNameWS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TrackExchangeLocation loc = new TrackExchangeLocation();
                    loc.index = rs.getInt("checkpointId");
                    loc.type = "CHECKPOINT";
                    loc.location = new SimpleLocation();
                    loc.location.x = (rs.getDouble("min_x") + rs.getDouble("max_x")) / 2.0;
                    loc.location.y = (rs.getDouble("min_y") + rs.getDouble("max_y")) / 2.0;
                    loc.location.z = (rs.getDouble("min_z") + rs.getDouble("max_z")) / 2.0;
                    loc.location.yaw = 0;
                    loc.location.pitch = 0;
                    loc.location.world = rs.getString("worldName");
                    data.locations.add(loc);
                }
            }
        }

        // Add creator as contributor
        String creatorNameSql = "SELECT creatorName FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?)";
        try (Connection conn = db.getOrConnect();
             PreparedStatement ps = conn.prepareStatement(creatorNameSql)) {
            ps.setString(1, finalTrackNameWS);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString("creatorName") != null) {
                    data.contributors.add(rs.getString("creatorName"));
                }
            }
        }

        // Build JSON
        String trackJson = gson.toJson(data);
        String dataComponentJson = gson.toJson(new DataComponent(5, null, null));

        // Write ZIP
        String outputFileName = fileName != null && !fileName.isEmpty() ? fileName : finalDisplayName;
        if (!outputFileName.endsWith(".trackexchange")) {
            outputFileName += ".trackexchange";
        }
        File outputFile = new File(exportDir, outputFileName);

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // data.component
            zos.putNextEntry(new ZipEntry("data.component"));
            zos.write(dataComponentJson.getBytes("UTF-8"));
            zos.closeEntry();

            // track.component
            zos.putNextEntry(new ZipEntry("track.component"));
            zos.write(trackJson.getBytes("UTF-8"));
            zos.closeEntry();

            // schematic.component (optional - from WorldEdit selection)
            try {
                com.sk89q.worldedit.regions.Region weRegion = null;
                BukkitPlayer bukkitPlayer = BukkitAdapter.adapt(player);
                com.sk89q.worldedit.LocalSession session = WorldEdit.getInstance().getSessionManager().get(bukkitPlayer);
                if (session != null) {
                    weRegion = session.getSelection(bukkitPlayer.getWorld());
                }
                if (weRegion != null) {
                    Clipboard clipboard = new com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard(weRegion);
                    clipboard.setOrigin(weRegion.getMinimumPoint());

                    try (EditSession editSession = WorldEdit.getInstance().newEditSession(bukkitPlayer.getWorld())) {
                        ForwardExtentCopy copy = new ForwardExtentCopy(editSession, weRegion, clipboard, weRegion.getMinimumPoint());
                        Operations.completeLegacy(copy);
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(baos)) {
                        writer.write(clipboard);
                    }

                    BlockVector3 offset = weRegion.getMinimumPoint().subtract(weRegion.getMinimumPoint());
                    JsonObject clipboardOffset = new JsonObject();
                    clipboardOffset.addProperty("x", offset.getX());
                    clipboardOffset.addProperty("y", offset.getY());
                    clipboardOffset.addProperty("z", offset.getZ());
                    clipboardOffset.addProperty("yaw", 0);
                    clipboardOffset.addProperty("pitch", 0);

                    dataComponentJson = gson.toJson(new DataComponent(5, "SPONGE_V3_SCHEMATIC", clipboardOffset));

                    // Re-write data.component with schematic info
                    zos.putNextEntry(new ZipEntry("data.component"));
                    zos.write(dataComponentJson.getBytes("UTF-8"));
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry("schematic.component"));
                    zos.write(baos.toByteArray());
                    zos.closeEntry();
                }
            } catch (Exception e) {
                debug.logDatabaseOperation("§e[TrackExchange] No WorldEdit selection for schematic (optional): " + e.getMessage());
            }
        }

        String finalOutputFileName = outputFileName;
        String finalDisplayName2 = finalDisplayName;
        SchedulerHelper.runTask(plugin, () ->
            player.sendMessage("§aPista '" + finalDisplayName2 + "' exportada como '" + finalOutputFileName + "'")
        );
        debug.logDatabaseOperation("§6[TrackExchange] Exportação concluída: " + outputFile.getAbsolutePath());
    }

    private void doImport(Player player, String fileName, String newName) throws IOException, SQLException {
        DebugManager debug = plugin.getDebugManager();
        debug.logDatabaseOperation("§6[TrackExchange] Iniciando importação de '" + fileName + "'");

        File importFile = new File(exportDir, fileName);
        if (!importFile.exists()) {
            importFile = new File(exportDir, fileName + ".trackexchange");
        }
        if (!importFile.exists()) {
            throw new IOException("Arquivo '" + fileName + "' não encontrado em " + exportDir.getAbsolutePath());
        }

        String trackComponentStr = null;
        String dataComponentStr = null;
        byte[] schematicBytes = null;

        try (FileInputStream fis = new FileInputStream(importFile);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                byte[] content = baos.toByteArray();

                if (entry.getName().equals("track.component")) {
                    trackComponentStr = new String(content, "UTF-8");
                } else if (entry.getName().equals("data.component")) {
                    dataComponentStr = new String(content, "UTF-8");
                } else if (entry.getName().equals("schematic.component")) {
                    schematicBytes = content;
                }
                zis.closeEntry();
            }
        }

        if (trackComponentStr == null) {
            throw new IOException("Arquivo .trackexchange inválido: track.component não encontrado.");
        }

        TrackExchangeData data = gson.fromJson(trackComponentStr, TrackExchangeData.class);
        if (data.spawn == null) {
            throw new IOException("Dados da pista inválidos: spawn não encontrado.");
        }

        String finalTrackName = (newName != null && !newName.isEmpty()) ? newName : importFile.getName().replace(".trackexchange", "");

        // Get existing world or use the one from the file
        String spawnWorldName = data.spawn.world;
        final World targetWorld;
        if (spawnWorldName != null) {
            World w = org.bukkit.Bukkit.getWorld(spawnWorldName);
            if (w != null) {
                targetWorld = w;
            } else {
                targetWorld = player.getWorld();
            }
        } else {
            targetWorld = player.getWorld();
        }

        final Location spawnLocation = new Location(targetWorld, data.spawn.x, data.spawn.y, data.spawn.z, data.spawn.yaw, data.spawn.pitch);
        final TrackExchangeData finalData = data;
        final byte[] finalSchematic = schematicBytes;
        final String finalTrackNameFinal = finalTrackName;
        final DebugManager debugFinal = debug;

        // Create track in database
        SchedulerHelper.runTask(plugin, () -> {
            boolean created = db.createTrack(finalTrackNameFinal, spawnLocation, player.getName(), player.getUniqueId().toString());
            if (!created) {
                player.sendMessage("§cErro ao criar pista no banco de dados.");
                return;
            }

            String trackNameWS = finalTrackNameFinal.replaceAll("\\s+", "").toLowerCase();

            // Import regions
            if (finalData.regions != null) {
                for (TrackExchangeRegion r : finalData.regions) {
                    if (r.shape == null) r.shape = "AABB";

                    Location min = new Location(targetWorld, r.minX, r.minY, r.minZ);
                    Location max = new Location(targetWorld, r.maxX, r.maxY, r.maxZ);

                    if ("POLY".equalsIgnoreCase(r.shape) && r.points != null && !r.points.isEmpty()) {
                        List<Location> polyPoints = new ArrayList<>();
                        for (double[] pt : r.points) {
                            polyPoints.add(new Location(targetWorld, pt[0], r.minY, pt[1]));
                        }
                        db.saveRegion(trackNameWS, min, max, r.type, "POLY", polyPoints);
                    } else {
                        db.saveRegion(trackNameWS, min, max, r.type);
                    }
                }
            }

            // Import checkpoints from locations of type CHECKPOINT
            if (finalData.locations != null) {
                for (TrackExchangeLocation loc : finalData.locations) {
                    if ("CHECKPOINT".equalsIgnoreCase(loc.type) && loc.location != null) {
                        World cpWorld = targetWorld;
                        String cpWorldName = loc.location.world;
                        if (cpWorldName != null) {
                            World w = org.bukkit.Bukkit.getWorld(cpWorldName);
                            if (w != null) cpWorld = w;
                        }

                        double cx = loc.location.x;
                        double cy = loc.location.y;
                        double cz = loc.location.z;

                        Location cpMin = new Location(cpWorld, cx - 1, cy - 1, cz - 1);
                        Location cpMax = new Location(cpWorld, cx + 1, cy + 1, cz + 1);

                        try (Connection conn = db.getOrConnect()) {
                            String insertCp = "INSERT INTO fr_checkpoint (checkpointId, trackNameWS, worldName, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                            try (PreparedStatement ps = conn.prepareStatement(insertCp)) {
                                ps.setInt(1, loc.index);
                                ps.setString(2, trackNameWS);
                                ps.setString(3, cpWorld.getName());
                                ps.setDouble(4, Math.min(cpMin.getX(), cpMax.getX()));
                                ps.setDouble(5, Math.min(cpMin.getY(), cpMax.getY()));
                                ps.setDouble(6, Math.min(cpMin.getZ(), cpMax.getZ()));
                                ps.setDouble(7, Math.max(cpMin.getX(), cpMax.getX()));
                                ps.setDouble(8, Math.max(cpMin.getY(), cpMax.getY()));
                                ps.setDouble(9, Math.max(cpMin.getZ(), cpMax.getZ()));
                                ps.executeUpdate();
                            }
                        } catch (SQLException e) {
                            debugFinal.logDatabaseOperation("§c[TrackExchange] Erro ao importar checkpoint: " + e.getMessage());
                        }
                    }
                }
            }

            plugin.getRegionListener().reloadRegions();

            player.sendMessage("§aPista '" + finalTrackNameFinal + "' importada com sucesso!");

            // Paste schematic if present
            if (finalSchematic != null) {
                try {
                    pasteSchematic(player, finalSchematic, finalData);
                } catch (Exception e) {
                    player.sendMessage("§eAviso: Erro ao colar schematic: " + e.getMessage());
                }
            }
        });
    }

    private void pasteSchematic(Player player, byte[] schematicBytes, TrackExchangeData data) throws Exception {
        BukkitPlayer bukkitPlayer = BukkitAdapter.adapt(player);
        ByteArrayInputStream bais = new ByteArrayInputStream(schematicBytes);

        ClipboardReader reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader(bais);
        Clipboard clipboard = reader.read();

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(bukkitPlayer.getWorld())) {
            com.sk89q.worldedit.function.operation.Operation operation = new ClipboardHolder(clipboard)
                .createPaste(editSession)
                .to(clipboard.getOrigin())
                .ignoreAirBlocks(true)
                .build();
            Operations.completeLegacy(operation);
        }

        player.sendMessage("§aSchematic colado com sucesso!");
    }

    public List<String> listFiles() {
        List<String> files = new ArrayList<>();
        File[] list = exportDir.listFiles((dir, name) -> name.endsWith(".trackexchange"));
        if (list != null) {
            for (File f : list) {
                files.add(f.getName());
            }
        }
        return files;
    }

    private static class DataComponent {
        int version;
        String schematic_format;
        JsonObject clipboardOffset;

        DataComponent(int version, String schematic_format, JsonObject clipboardOffset) {
            this.version = version;
            this.schematic_format = schematic_format;
            this.clipboardOffset = clipboardOffset;
        }
    }
}
