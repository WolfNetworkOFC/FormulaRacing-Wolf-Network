package dev.EfraGroup.formulaRacing.Utils.trackexchange;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
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
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class TrackExchangeManager {

    public static final int TRACK_VERSION = 5;

    private static final ClipboardFormat SCHEMATIC_FORMAT = BuiltInClipboardFormat.FAST_V3;

    private final FormulaRacing plugin;
    private final DatabaseManager db;
    private final Gson gson;
    private final File exportDir;
    private final Map<UUID, Stack<Runnable>> playerActions = new ConcurrentHashMap<>();

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
        public Double minX, minY, minZ;
        public Double maxX, maxY, maxZ;
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
        data.tags = db.getTrackTags(finalTrackNameWS);
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
                    loc.minX = rs.getDouble("min_x");
                    loc.minY = rs.getDouble("min_y");
                    loc.minZ = rs.getDouble("min_z");
                    loc.maxX = rs.getDouble("max_x");
                    loc.maxY = rs.getDouble("max_y");
                    loc.maxZ = rs.getDouble("max_z");
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
        String dataComponentJson = gson.toJson(new DataComponent(TRACK_VERSION, null, null));
        byte[] schematicBytes = null;

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
                try (ClipboardWriter writer = SCHEMATIC_FORMAT.getWriter(baos)) {
                    writer.write(clipboard);
                }

                BlockVector3 exportOrigin = BlockVector3.at(data.origin.x, data.origin.y, data.origin.z);
                BlockVector3 offset = clipboard.getOrigin().subtract(exportOrigin);
                JsonObject clipboardOffset = new JsonObject();
                clipboardOffset.addProperty("x", offset.x());
                clipboardOffset.addProperty("y", offset.y());
                clipboardOffset.addProperty("z", offset.z());
                clipboardOffset.addProperty("yaw", 0);
                clipboardOffset.addProperty("pitch", 0);

                dataComponentJson = gson.toJson(new DataComponent(TRACK_VERSION, SCHEMATIC_FORMAT.getName(), clipboardOffset));
                schematicBytes = baos.toByteArray();
            }
        } catch (Exception e) {
            debug.logDatabaseOperation("§e[TrackExchange] No WorldEdit selection for schematic (optional): " + e.getMessage());
        }

        // Write ZIP
        String outputFileName = fileName != null && !fileName.isEmpty() ? fileName : finalDisplayName;
        if (!outputFileName.endsWith(".trackexchange")) {
            outputFileName += ".trackexchange";
        }
        File outputFile = new File(exportDir, outputFileName);
        Files.write(outputFile.toPath(), compressBytes(
            dataComponentJson.getBytes("UTF-8"),
            trackJson.getBytes("UTF-8"),
            schematicBytes
        ));

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

        TrackExchangeBytes components = splitBytes(Files.readAllBytes(importFile.toPath()));
        String trackComponentStr = new String(components.trackBytes, "UTF-8");
        String dataComponentStr = new String(components.dataBytes, "UTF-8");
        byte[] schematicBytes = components.schematicBytes;

        JsonElement dataEl;
        try {
            dataEl = JsonParser.parseString(dataComponentStr);
        } catch (Exception e) {
            throw new IOException("Arquivo .trackexchange inválido: data.component não é JSON válido.");
        }
        if (!dataEl.isJsonObject() || !dataEl.getAsJsonObject().has("version")) {
            throw new IOException("Arquivo .trackexchange inválido: versão não encontrada em data.component.");
        }
        JsonObject dataObj = dataEl.getAsJsonObject();
        int fileVersion = dataObj.get("version").getAsInt();
        if (fileVersion != TRACK_VERSION) {
            throw new IOException("Versão do arquivo incompatível: arquivo v" + fileVersion + ", servidor espera v" + TRACK_VERSION + ".");
        }

        ClipboardFormat schematicFormat = SCHEMATIC_FORMAT;
        JsonElement fmtEl = dataObj.get("schematic_format");
        if (fmtEl != null && fmtEl.isJsonPrimitive() && !fmtEl.getAsString().isEmpty()) {
            try {
                schematicFormat = BuiltInClipboardFormat.valueOf(fmtEl.getAsString());
            } catch (Exception ex) {
                debug.logDatabaseOperation("§e[TrackExchange] Formato de schematic desconhecido '" + fmtEl.getAsString() + "', usando " + SCHEMATIC_FORMAT.getName() + ".");
            }
        }

        BlockVector3 clipboardOffset = BlockVector3.at(0, 0, 0);
        JsonElement offEl = dataObj.get("clipboardOffset");
        if (offEl != null && offEl.isJsonObject()) {
            JsonObject offObj = offEl.getAsJsonObject();
            clipboardOffset = BlockVector3.at(offObj.get("x").getAsDouble(), offObj.get("y").getAsDouble(), offObj.get("z").getAsDouble());
        }

        TrackExchangeData data = gson.fromJson(trackComponentStr, TrackExchangeData.class);
        if (data.spawn == null) {
            throw new IOException("Dados da pista inválidos: spawn não encontrado.");
        }

        String finalTrackName = (newName != null && !newName.isEmpty()) ? newName : importFile.getName().replace(".trackexchange", "");

        // Get existing world or use the one from the file
        String spawnWorldName = data.spawn.world;
        final World targetWorld;
        boolean worldFallback;
        if (spawnWorldName != null) {
            World w = org.bukkit.Bukkit.getWorld(spawnWorldName);
            if (w != null) {
                targetWorld = w;
                worldFallback = false;
            } else {
                targetWorld = player.getWorld();
                worldFallback = true;
            }
        } else {
            targetWorld = player.getWorld();
            worldFallback = true;
        }

        final Location spawnLocation = new Location(targetWorld, data.spawn.x, data.spawn.y, data.spawn.z, data.spawn.yaw, data.spawn.pitch);
        final TrackExchangeData finalData = data;
        final byte[] finalSchematic = schematicBytes;
        final ClipboardFormat finalFormat = schematicFormat;
        final BlockVector3 finalClipboardOffset = clipboardOffset;
        final String finalTrackNameFinal = finalTrackName;
        final DebugManager debugFinal = debug;

        // Create track in database
        final boolean worldFallbackFinal = worldFallback;
        SchedulerHelper.runTask(plugin, () -> {
            if (db.isTrackExists(finalTrackNameFinal)) {
                player.sendMessage("§cJá existe uma pista chamada '" + finalTrackNameFinal + "'. Importe com outro nome: /trackedit import <arquivo> <novoNome>");
                return;
            }

            if (worldFallbackFinal) {
                player.sendMessage("§eAviso: o mundo '" + spawnWorldName + "' do arquivo não está carregado. Usando seu mundo atual (" + targetWorld.getName() + ").");
            }

            boolean created = db.createTrack(finalTrackNameFinal, spawnLocation, player.getName(), player.getUniqueId().toString());
            if (!created) {
                player.sendMessage("§cErro ao criar pista no banco de dados.");
                return;
            }

            String trackNameWS = finalTrackNameFinal.replaceAll("\\s+", "").toLowerCase();
            if (finalData.tags != null) {
                db.setTrackTags(trackNameWS, finalData.tags);
            }

            // Persist the GUI icon stored in the file (falls back to the export default)
            String icon = "BIRCH_BOAT";
            if (finalData.guiItem != null && Material.matchMaterial(finalData.guiItem) != null) {
                icon = finalData.guiItem;
            }
            db.setTrackIcon(trackNameWS, icon);

            // Import regions
            if (finalData.regions != null) {
                for (TrackExchangeRegion r : finalData.regions) {
                    if (r.shape == null) r.shape = "AABB";
                    if (!validCoords(r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ)) {
                        debugFinal.logDatabaseOperation("§e[TrackExchange] Região #" + r.index + " ignorada: coordenadas inválidas.");
                        continue;
                    }

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

                        // Files with real bounds keep the original checkpoint size;
                        // older files only store the center and fall back to a 3x3x3 box
                        Location cpMin;
                        Location cpMax;
                        if (loc.minX != null && loc.minY != null && loc.minZ != null
                            && loc.maxX != null && loc.maxY != null && loc.maxZ != null
                            && validCoords(loc.minX, loc.minY, loc.minZ, loc.maxX, loc.maxY, loc.maxZ)) {
                            cpMin = new Location(cpWorld,
                                Math.min(loc.minX, loc.maxX), Math.min(loc.minY, loc.maxY), Math.min(loc.minZ, loc.maxZ));
                            cpMax = new Location(cpWorld,
                                Math.max(loc.minX, loc.maxX), Math.max(loc.minY, loc.maxY), Math.max(loc.minZ, loc.maxZ));
                        } else if (validCoords(cx, cy, cz)) {
                            cpMin = new Location(cpWorld, cx - 1, cy - 1, cz - 1);
                            cpMax = new Location(cpWorld, cx + 1, cy + 1, cz + 1);
                        } else {
                            debugFinal.logDatabaseOperation("§e[TrackExchange] Checkpoint #" + loc.index + " ignorado: coordenadas inválidas.");
                            continue;
                        }

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

            // A imported track has a new geometry, so any delta stored for it is stale.
            db.clearCheckpointTimesForTrack(trackNameWS);
            db.clearCheckpointsCache(trackNameWS);

            player.sendMessage("§aPista '" + finalTrackNameFinal + "' importada com sucesso!");

            // Paste schematic if present
            EditSession pasteSession = null;
            if (finalSchematic != null) {
                try {
                    pasteSession = pasteSchematic(player, finalSchematic, finalFormat, finalClipboardOffset);
                } catch (Exception e) {
                    player.sendMessage("§eAviso: Erro ao colar schematic: " + e.getMessage());
                }
            }

            // Register the undo action (schematic revert + track removal), like the official TrackExchange
            pushInverse(player, finalTrackNameFinal, pasteSession);
        });
    }

    private EditSession pasteSchematic(Player player, byte[] schematicBytes, ClipboardFormat format, BlockVector3 clipboardOffset) throws Exception {
        BukkitPlayer bukkitPlayer = BukkitAdapter.adapt(player);
        ByteArrayInputStream bais = new ByteArrayInputStream(schematicBytes);

        ClipboardReader reader = format.getReader(bais);
        Clipboard clipboard = reader.read();

        Location playerLoc = player.getLocation();
        BlockVector3 pasteAt = clipboardOffset.add(BlockVector3.at(playerLoc.getX(), playerLoc.getY(), playerLoc.getZ()));

        EditSession editSession = WorldEdit.getInstance().newEditSession(bukkitPlayer);
        try {
            com.sk89q.worldedit.function.operation.Operation operation = new ClipboardHolder(clipboard)
                .createPaste(editSession)
                .to(pasteAt)
                .ignoreAirBlocks(true)
                .build();
            Operations.completeLegacy(operation);
        } catch (Exception e) {
            editSession.close();
            throw e;
        }

        player.sendMessage("§aSchematic colado com sucesso!");
        return editSession;
    }

    private void pushInverse(Player player, String trackName, EditSession pasteSession) {
        Runnable inverse = () -> {
            if (pasteSession != null) {
                SchedulerHelper.runAsync(plugin, () -> {
                    try (EditSession undoSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(player))) {
                        pasteSession.undo(undoSession);
                    } catch (Exception e) {
                        player.sendMessage("§eAviso: Erro ao desfazer schematic: " + e.getMessage());
                    }
                });
            }
            SchedulerHelper.runTask(plugin, () -> {
                db.deleteTrack(trackName);
                plugin.getDebugManager().logDatabaseOperation("§6[TrackExchange] Undo: pista '" + trackName + "' removida.");
                player.sendMessage("§aPista '" + trackName + "' removida (undo).");
            });
        };
        playerActions.computeIfAbsent(player.getUniqueId(), k -> new Stack<>()).push(inverse);
    }

    public Optional<Runnable> popAction(UUID playerUuid) {
        Stack<Runnable> actions = playerActions.get(playerUuid);
        if (actions == null || actions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(actions.pop());
    }

    private static boolean validCoords(double... coords) {
        for (double c : coords) {
            if (Double.isNaN(c) || Double.isInfinite(c)) return false;
        }
        return true;
    }

    private static byte[] compressBytes(byte[] dataBytes, byte[] trackBytes, byte[] schematicBytes) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(byteOut)) {
            zipOut.putNextEntry(new ZipEntry("data.component"));
            zipOut.write(dataBytes);
            zipOut.closeEntry();

            zipOut.putNextEntry(new ZipEntry("track.component"));
            zipOut.write(trackBytes);
            zipOut.closeEntry();

            if (schematicBytes != null) {
                zipOut.putNextEntry(new ZipEntry("schematic.component"));
                zipOut.write(schematicBytes);
                zipOut.closeEntry();
            }
        }
        return byteOut.toByteArray();
    }

    private static TrackExchangeBytes splitBytes(byte[] zipBytes) throws IOException {
        byte[] dataBytes = null;
        byte[] trackBytes = null;
        byte[] schematicBytes = null;

        try (ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                byte[] content = zipStream.readAllBytes();
                String name = entry.getName();
                if ("data.component".equals(name)) {
                    dataBytes = content;
                } else if ("track.component".equals(name)) {
                    trackBytes = content;
                } else if ("schematic.component".equals(name)) {
                    schematicBytes = content;
                }
                zipStream.closeEntry();
            }
        }

        if (dataBytes == null || trackBytes == null) {
            throw new IOException("Arquivo .trackexchange inválido: componentes obrigatórios não encontrados.");
        }
        return new TrackExchangeBytes(dataBytes, trackBytes, schematicBytes);
    }

    private static final class TrackExchangeBytes {
        final byte[] dataBytes;
        final byte[] trackBytes;
        final byte[] schematicBytes;

        TrackExchangeBytes(byte[] dataBytes, byte[] trackBytes, byte[] schematicBytes) {
            this.dataBytes = dataBytes;
            this.trackBytes = trackBytes;
            this.schematicBytes = schematicBytes;
        }
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
