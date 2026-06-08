package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.RegionBox;
import org.bukkit.Location;

public class PitBoxRegion {

    private final String trackNameWS;
    private final String teamName;
    private final RegionBox region;

    public PitBoxRegion(String trackNameWS, String teamName, RegionBox region) {
        this.trackNameWS = trackNameWS;
        this.teamName = teamName;
        this.region = region;
    }

    public boolean contains(Location location) {
        return this.region != null && this.region.contains(location);
    }

    public Location getCenter() {
        if (this.region == null) return null;
        org.bukkit.util.Vector center = this.region.getCenter();
        return new Location(this.region.getMin().getWorld(), center.getX(), center.getY(), center.getZ());
    }

    public String getTrackNameWS() {
        return this.trackNameWS;
    }

    public String getTeamName() {
        return this.teamName;
    }

    public RegionBox getRegion() {
        return this.region;
    }
}
