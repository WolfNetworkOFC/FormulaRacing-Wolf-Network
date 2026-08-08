package dev.EfraGroup.formulaRacing.League;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TeamConfig {

    private static final Gson GSON = new GsonBuilder().create();

    private int maxMains = 0;
    private int maxReserves = 0;
    private int countedScorers = 0;

    public int getMaxMains() {
        return maxMains;
    }

    public void setMaxMains(int maxMains) {
        this.maxMains = maxMains;
    }

    public int getMaxReserves() {
        return maxReserves;
    }

    public void setMaxReserves(int maxReserves) {
        this.maxReserves = maxReserves;
    }

    public int getCountedScorers() {
        return countedScorers;
    }

    public void setCountedScorers(int countedScorers) {
        this.countedScorers = countedScorers;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static TeamConfig fromJson(String json) {
        return GSON.fromJson(json, TeamConfig.class);
    }
}
