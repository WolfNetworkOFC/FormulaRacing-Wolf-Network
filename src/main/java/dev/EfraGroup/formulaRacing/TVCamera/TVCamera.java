package dev.EfraGroup.formulaRacing.TVCamera;

import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class TVCamera {

    private int id;
    private String trackNameWS;
    private Location location;
    private int camIndex;
    private Vector min;
    private Vector max;
    private String label;

    public TVCamera(int id, String trackNameWS, Location location, int camIndex, Vector min, Vector max, String label) {
        this.id = id;
        this.trackNameWS = trackNameWS;
        this.location = location;
        this.camIndex = camIndex;
        this.min = min;
        this.max = max;
        this.label = label;
    }

    public void tpPlayer(Player player) {
        SchedulerHelper.teleport(player, location);
    }

    public boolean isInsideRegion(Player player) {
        if (min == null || max == null) return false;
        Vector pLoc = player.getLocation().toVector();
        return pLoc.isInAABB(min, max);
    }

    public int getId() { return id; }
    public String getTrackNameWS() { return trackNameWS; }
    public Location getLocation() { return location; }
    public int getCamIndex() { return camIndex; }
    public Vector getMin() { return min; }
    public Vector getMax() { return max; }
    public String getLabel() { return label; }
    public boolean hasRegion() { return min != null && max != null; }

    public String getMinMaxString() {
        if (min == null || max == null) return null;
        return min.getBlockX() + "," + min.getBlockY() + "," + min.getBlockZ() + ":" +
                max.getBlockX() + "," + max.getBlockY() + "," + max.getBlockZ();
    }
}
