package dev.EfraGroup.formulaRacing.Utils;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.session.SessionOwner;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public class WorldEditSelect {
    
    public static class SelectionData {
        private final String type;
        private final String shape;
        private final Location min;
        private final Location max;
        private final List<Location> points;
        private final String worldName;
        
        public SelectionData(String type, String shape, Location min, Location max, List<Location> points, String worldName) {
            this.type = type;
            this.shape = shape;
            this.min = min;
            this.max = max;
            this.points = points != null ? points : new ArrayList<>();
            this.worldName = worldName;
        }
        
        public String getType() { return type; }
        public String getShape() { return shape; }
        public Location getMin() { return min; }
        public Location getMax() { return max; }
        public List<Location> getPoints() { return points; }
        public String getWorldName() { return worldName; }
    }
    
    private static Region getRegion(Player player) {
        BukkitPlayer bukkitPlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get((SessionOwner) bukkitPlayer);
        try {
            return session.getSelection(bukkitPlayer.getWorld());
        } catch (Exception e) {
            return null;
        }
    }
    
    public static boolean hasSelection(Player player) {
        return WorldEditSelect.getRegion(player) != null;
    }
    
    public static String getSelectionType(Player player) {
        Region region = WorldEditSelect.getRegion(player);
        if (region == null) return "CUBOID";
        
        if (region instanceof Polygonal2DRegion) {
            return "POLY";
        }
        if (region.getClass().getSimpleName().contains("Poly")) {
            return "POLYHEDRAL";
        }
        return "CUBOID";
    }
    
    public static SelectionData getSelectionData(Player player) {
        Region region = WorldEditSelect.getRegion(player);
        if (region == null) return null;
        
        String selectorType = WorldEditSelect.getSelectionType(player);
        String shape = "AABB";
        List<Location> points = new ArrayList<>();
        String worldName = region.getWorld().getName();
        
        if (region instanceof Polygonal2DRegion) {
            shape = "POLY";
            Polygonal2DRegion poly = (Polygonal2DRegion) region;
            for (BlockVector2 point : poly.getPoints()) {
                points.add(new Location(player.getWorld(), point.getX(), poly.getMinimumPoint().getBlockY(), point.getZ()));
            }
        }
        
        Location min = new Location(player.getWorld(), 
            (double) region.getMinimumPoint().getX(),
            (double) region.getMinimumPoint().getY(),
            (double) region.getMinimumPoint().getZ());
        Location max = new Location(player.getWorld(),
            (double) region.getMaximumPoint().getX() + 1.0,
            (double) region.getMaximumPoint().getY() + 1.0,
            (double) region.getMaximumPoint().getZ() + 1.0);
        
        return new SelectionData(selectorType, shape, min, max, points, worldName);
    }
    
    public static Location getMin(Player player) {
        Region region = WorldEditSelect.getRegion(player);
        if (region == null) return null;
        return new Location(player.getWorld(),
            (double) region.getMinimumPoint().getX(),
            (double) region.getMinimumPoint().getY(),
            (double) region.getMinimumPoint().getZ());
    }
    
    public static Location getMax(Player player) {
        Region region = WorldEditSelect.getRegion(player);
        if (region == null) return null;
        return new Location(player.getWorld(),
            (double) region.getMaximumPoint().getX() + 1.0,
            (double) region.getMaximumPoint().getY() + 1.0,
            (double) region.getMaximumPoint().getZ() + 1.0);
    }
    
    public static String getPointsString(List<Location> points) {
        if (points == null || points.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) sb.append(";");
            Location p = points.get(i);
            sb.append(String.format("%.0f,%.0f,%.0f", p.getX(), p.getY(), p.getZ()));
        }
        return sb.toString();
    }
}