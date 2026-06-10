package dev.EfraGroup.formulaRacing.TVCamera;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TVCameraController {

    private final FormulaRacing plugin;
    private final DatabaseManager database;
    private final List<TVCamera> cameras = new ArrayList<>();
    private static final Map<UUID, TVCamPlayer> players = new ConcurrentHashMap<>();

    public TVCameraController(FormulaRacing plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void loadCameras() {
        cameras.clear();
        String sql = "SELECT id, trackNameWS, worldName, x, y, z, yaw, pitch, cam_index, min_x, min_y, min_z, max_x, max_y, max_z, label FROM fr_cameras ORDER BY trackNameWS, cam_index";
        try {
            Connection conn = database.getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String worldName = rs.getString("worldName");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    Location loc = new Location(
                            world,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                    );

                    int id = rs.getInt("id");
                    String trackNameWS = rs.getString("trackNameWS");
                    int camIndex = rs.getInt("cam_index");

                    double minX = rs.getDouble("min_x");
                    Vector min = rs.wasNull() ? null : new Vector(minX, rs.getDouble("min_y"), rs.getDouble("min_z"));
                    Vector max = null;
                    if (min != null) {
                        double maxX = rs.getDouble("max_x");
                        max = rs.wasNull() ? null : new Vector(maxX, rs.getDouble("max_y"), rs.getDouble("max_z"));
                    }

                    String label = rs.getString("label");

                    cameras.add(new TVCamera(id, trackNameWS, loc, camIndex, min, max, label));
                }
            }
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation("Error loading TV cameras: " + e.getMessage());
        }
    }

    public void saveCamera(TVCamera camera) {
        String sql = "INSERT INTO fr_cameras (trackNameWS, worldName, x, y, z, yaw, pitch, cam_index, min_x, min_y, min_z, max_x, max_y, max_z, label) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "trackNameWS = excluded.trackNameWS, worldName = excluded.worldName, " +
                "x = excluded.x, y = excluded.y, z = excluded.z, " +
                "yaw = excluded.yaw, pitch = excluded.pitch, " +
                "cam_index = excluded.cam_index, " +
                "min_x = excluded.min_x, min_y = excluded.min_y, min_z = excluded.min_z, " +
                "max_x = excluded.max_x, max_y = excluded.max_y, max_z = excluded.max_z, " +
                "label = excluded.label";
        try {
            Connection conn = database.getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, camera.getTrackNameWS());
                ps.setString(2, camera.getLocation().getWorld().getName());
                ps.setDouble(3, camera.getLocation().getX());
                ps.setDouble(4, camera.getLocation().getY());
                ps.setDouble(5, camera.getLocation().getZ());
                ps.setFloat(6, camera.getLocation().getYaw());
                ps.setFloat(7, camera.getLocation().getPitch());
                ps.setInt(8, camera.getCamIndex());
                if (camera.hasRegion()) {
                    ps.setDouble(9, camera.getMin().getX());
                    ps.setDouble(10, camera.getMin().getY());
                    ps.setDouble(11, camera.getMin().getZ());
                    ps.setDouble(12, camera.getMax().getX());
                    ps.setDouble(13, camera.getMax().getY());
                    ps.setDouble(14, camera.getMax().getZ());
                } else {
                    ps.setNull(9, java.sql.Types.DOUBLE);
                    ps.setNull(10, java.sql.Types.DOUBLE);
                    ps.setNull(11, java.sql.Types.DOUBLE);
                    ps.setNull(12, java.sql.Types.DOUBLE);
                    ps.setNull(13, java.sql.Types.DOUBLE);
                    ps.setNull(14, java.sql.Types.DOUBLE);
                }
                ps.setString(15, camera.getLabel());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation("Error saving TV camera: " + e.getMessage());
        }
    }

    public void removeCamera(int id) {
        String sql = "DELETE FROM fr_cameras WHERE id = ?";
        try {
            Connection conn = database.getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation("Error removing TV camera: " + e.getMessage());
        }
        cameras.removeIf(c -> c.getId() == id);
    }

    public void addCamera(TVCamera camera) {
        saveCamera(camera);
        loadCameras();
    }

    public TVCamera getCamera(String trackNameWS, int camIndex) {
        for (TVCamera cam : cameras) {
            if (cam.getTrackNameWS() != null && cam.getTrackNameWS().equalsIgnoreCase(trackNameWS) && cam.getCamIndex() == camIndex) {
                return cam;
            }
        }
        return null;
    }

    public List<TVCamera> getCamerasForTrack(String trackNameWS) {
        List<TVCamera> result = new ArrayList<>();
        for (TVCamera cam : cameras) {
            if (cam.getTrackNameWS() != null && cam.getTrackNameWS().equalsIgnoreCase(trackNameWS)) {
                result.add(cam);
            }
        }
        return result;
    }

    public List<TVCamera> getAllCameras() {
        return Collections.unmodifiableList(cameras);
    }

    public String getNearestTrackName(Player player) {
        Location playerLoc = player.getLocation();
        String closest = null;
        double closestDist = Double.MAX_VALUE;

        Map<String, DatabaseManager.TrackData> tracks = database.getAllTracksWithData();
        for (Map.Entry<String, DatabaseManager.TrackData> entry : tracks.entrySet()) {
            Location spawn = entry.getValue().getSpawnLocation();
            if (spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(playerLoc.getWorld())) {
                double dist = spawn.distanceSquared(playerLoc);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entry.getValue().getTrackName();
                }
            }
        }
        return closest;
    }

    public Location getNearestCameraLocation(Player target) {
        Location targetLoc = target.getLocation();
        TVCamera nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (TVCamera cam : cameras) {
            Location loc = cam.getLocation();
            if (loc.getWorld() != null && loc.getWorld().equals(targetLoc.getWorld())) {
                double dist = loc.distanceSquared(targetLoc);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = cam;
                }
            }
        }
        return nearest != null ? nearest.getLocation() : null;
    }

    public TVCamera getNearestTVCamera(Player target) {
        Location targetLoc = target.getLocation();
        TVCamera nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (TVCamera cam : cameras) {
            Location loc = cam.getLocation();
            if (loc.getWorld() != null && loc.getWorld().equals(targetLoc.getWorld())) {
                double dist = loc.distanceSquared(targetLoc);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = cam;
                }
            }
        }
        return nearest;
    }

    public static TVCamPlayer getPlayer(UUID uuid) {
        return players.computeIfAbsent(uuid, TVCamPlayer::new);
    }

    public static void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public void updateFollowers() {
        for (TVCamPlayer tp : players.values()) {
            if (!tp.isFollowing()) continue;
            Player follower = Bukkit.getPlayer(tp.getUniqueId());
            if (follower == null || !follower.isOnline()) continue;
            Player target = tp.getFollowing();
            if (target == null || !target.isOnline()) {
                tp.stopFollowing();
                continue;
            }
            TVCamPlayer targetTp = getPlayer(target.getUniqueId());
            TVCamera bestCam = findBestCamera(target);
            if (bestCam != null && !bestCam.equals(tp.getCurrentCamera())) {
                bestCam.tpPlayer(follower);
                tp.setCurrentCamera(bestCam);
                targetTp.setCurrentCamera(bestCam);
            }
        }
    }

    public TVCamera findBestCamera(Player target) {
        String trackName = getNearestTrackName(target);
        if (trackName == null) return null;

        for (TVCamera cam : cameras) {
            if (cam.getTrackNameWS() != null && cam.getTrackNameWS().equalsIgnoreCase(trackName.replaceAll("\\s+", ""))) {
                if (cam.isInsideRegion(target) || !cam.hasRegion()) {
                    return cam;
                }
            }
        }
        return getNearestTVCamera(target);
    }

    public void startFollowingNormal(Player follower, Player target) {
        TVCamPlayer fp = getPlayer(follower.getUniqueId());
        TVCamPlayer tp = getPlayer(target.getUniqueId());
        fp.startFollowing(target);
        tp.addFollower(follower);

        Location nearest = getNearestCameraLocation(target);
        if (nearest != null) {
            SchedulerHelper.teleport(follower, nearest);
        }

        String langCode = database.getPlayerLanguage(follower.getUniqueId());
        follower.sendMessage(plugin.getDirectTranslation("cam_started_following", langCode).replace("{player}", target.getName()));
    }

    public boolean stopFollowingNormal(Player follower) {
        TVCamPlayer fp = getPlayer(follower.getUniqueId());
        if (fp.isFollowing()) {
            fp.stopFollowing();
            String langCode = database.getPlayerLanguage(follower.getUniqueId());
            follower.sendMessage(plugin.getDirectTranslation("cam_stopped_following", langCode));
            return true;
        }
        return false;
    }

    public boolean isFollowingNormal(Player follower) {
        return getPlayer(follower.getUniqueId()).isFollowing();
    }

    public Player getTargetNormal(Player follower) {
        return getPlayer(follower.getUniqueId()).getFollowing();
    }
}
