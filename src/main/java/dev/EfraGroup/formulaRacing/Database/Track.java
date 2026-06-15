package dev.EfraGroup.formulaRacing.Database;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Location;

public class Track {
    private final DatabaseManager.TrackData trackData;
    private final String trackNameWS;

    public Track(DatabaseManager.TrackData trackData, String trackNameWS) {
        this.trackData = trackData;
        this.trackNameWS = trackNameWS;
    }

    public String getTrackName() {
        return this.trackNameWS;
    }

    public Location getSpawnLocation() {
        return this.trackData != null ? this.trackData.getSpawnLocation() : null;
    }

    public int getTotalCheckpoints() {
        return this.trackData != null ? this.trackData.getTotalCheckpoints() : 0;
    }

    public DatabaseManager.TrackData getTrackData() {
        return this.trackData;
    }

    public boolean isValid() {
        return this.trackData != null && this.trackData.getSpawnLocation() != null && this.trackData.getTotalCheckpoints() > 0;
    }

    public boolean isCircuit() {
        return FormulaRacing.getInstance().getDatabaseManager().isCircuit(this.trackNameWS);
    }
}
