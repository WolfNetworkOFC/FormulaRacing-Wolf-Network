package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.RegionBox;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class PitStopRegion {
    private final String trackNameWS;
    private final RegionBox startRegion;
    private final RegionBox entryRegion;
    private final RegionBox exitRegion;
    private final RegionBox areaRegion;

    public PitStopRegion(String trackNameWS, RegionBox entryRegion, RegionBox exitRegion, RegionBox areaRegion, RegionBox startRegion) {
        this.trackNameWS = trackNameWS;
        this.entryRegion = entryRegion;
        this.exitRegion = exitRegion;
        this.areaRegion = areaRegion;
        this.startRegion = startRegion;
    }

    public boolean isInStart(Location location) {
        return this.startRegion != null && this.startRegion.contains(location);
    }

    public boolean isInEntry(Location location) {
        return this.entryRegion != null && this.entryRegion.contains(location);
    }

    public boolean isInExit(Location location) {
        return this.exitRegion != null && this.exitRegion.contains(location);
    }

    public boolean isInArea(Location location) {
        return this.areaRegion != null && this.areaRegion.contains(location);
    }

    public boolean hasStart() {
        return this.startRegion != null;
    }

    public boolean hasEntry() {
        return this.entryRegion != null;
    }

    public boolean hasExit() {
        return this.exitRegion != null;
    }

    public boolean hasArea() {
        return this.areaRegion != null;
    }

    public Location getStartCenter() {
        return this.getCenterOf(this.startRegion);
    }

    public Location getEntryCenter() {
        return this.getCenterOf(this.entryRegion);
    }

    public Location getExitCenter() {
        return this.getCenterOf(this.exitRegion);
    }

    public Location getAreaCenter() {
        return this.getCenterOf(this.areaRegion);
    }

    private Location getCenterOf(RegionBox region) {
        if (region == null) {
            return null;
        } else {
            Vector center = region.getCenter();
            return new Location(region.getMin().getWorld(), center.getX(), center.getY(), center.getZ());
        }
    }

    public String getTrackNameWS() {
        return this.trackNameWS;
    }

    public RegionBox getStartRegion() {
        return this.startRegion;
    }

    public RegionBox getEntryRegion() {
        return this.entryRegion;
    }

    public RegionBox getExitRegion() {
        return this.exitRegion;
    }

    public RegionBox getAreaRegion() {
        return this.areaRegion;
    }

    public String toString() {
        return String.format("PitStopRegion{track=%s, start=%s, entry=%s, exit=%s, area=%s}", this.trackNameWS, this.startRegion != null ? "SET" : "NULL", this.entryRegion != null ? "SET" : "NULL", this.exitRegion != null ? "SET" : "NULL", this.areaRegion != null ? "SET" : "NULL");
    }
}
