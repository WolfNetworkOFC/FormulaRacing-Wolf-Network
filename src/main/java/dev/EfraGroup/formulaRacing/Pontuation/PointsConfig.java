package dev.EfraGroup.formulaRacing.Pontuation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;

public class PointsConfig {

    private String name;
    private int scoringPositions;
    @SerializedName("racePoints")
    private Map<Integer, Integer> racePoints;
    @SerializedName("sprintPoints")
    private Map<Integer, Integer> sprintPoints;
    @SerializedName("fastestLapPoints")
    private int fastestLapPoints;
    @SerializedName("polePositionPoints")
    private int polePositionPoints;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public PointsConfig() {
        this.name = "default";
        this.scoringPositions = 10;
        this.racePoints = new HashMap<>();
        this.sprintPoints = new HashMap<>();
        this.fastestLapPoints = 1;
        this.polePositionPoints = 0;
    }

    public PointsConfig(String name) {
        this();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getScoringPositions() {
        return scoringPositions;
    }

    public void setScoringPositions(int scoringPositions) {
        this.scoringPositions = scoringPositions;
    }

    public Map<Integer, Integer> getRacePoints() {
        return racePoints;
    }

    public void setRacePoints(Map<Integer, Integer> racePoints) {
        this.racePoints = racePoints;
    }

    public Map<Integer, Integer> getSprintPoints() {
        return sprintPoints;
    }

    public void setSprintPoints(Map<Integer, Integer> sprintPoints) {
        this.sprintPoints = sprintPoints;
    }

    public int getFastestLapPoints() {
        return fastestLapPoints;
    }

    public void setFastestLapPoints(int fastestLapPoints) {
        this.fastestLapPoints = fastestLapPoints;
    }

    public int getPolePositionPoints() {
        return polePositionPoints;
    }

    public void setPolePositionPoints(int polePositionPoints) {
        this.polePositionPoints = polePositionPoints;
    }

    public int getPointsForPosition(int position, boolean isSprint) {
        if (position < 1 || position > scoringPositions) {
            return 0;
        }
        Map<Integer, Integer> pointsMap = isSprint ? sprintPoints : racePoints;
        return pointsMap.getOrDefault(position, 0);
    }

    public boolean isValid() {
        if (scoringPositions < 1) {
            return false;
        }
        if (fastestLapPoints < 0 || polePositionPoints < 0) {
            return false;
        }
        for (int i = 1; i <= scoringPositions; i++) {
            if (racePoints.getOrDefault(i, -1) < 0) {
                return false;
            }
        }
        for (int i = 1; i <= scoringPositions; i++) {
            if (sprintPoints.getOrDefault(i, -1) < 0) {
                return false;
            }
        }
        return true;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static PointsConfig fromJson(String json) {
        return GSON.fromJson(json, PointsConfig.class);
    }

    public PointsConfig copy() {
        PointsConfig copy = new PointsConfig(this.name);
        copy.setScoringPositions(this.scoringPositions);
        copy.setRacePoints(new HashMap<>(this.racePoints));
        copy.setSprintPoints(new HashMap<>(this.sprintPoints));
        copy.setFastestLapPoints(this.fastestLapPoints);
        copy.setPolePositionPoints(this.polePositionPoints);
        return copy;
    }
}
