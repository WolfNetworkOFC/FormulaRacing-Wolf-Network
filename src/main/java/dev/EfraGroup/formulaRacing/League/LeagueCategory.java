package dev.EfraGroup.formulaRacing.League;

import dev.EfraGroup.formulaRacing.Pontuation.PointsConfig;

public class LeagueCategory {

    private int id;
    private final String name;
    private String displayName;
    private String scoringSystem;
    private int mulliganCount;
    private PointsConfig customScale;

    public LeagueCategory(int id, String name) {
        this.id = id;
        this.name = name;
        this.displayName = name;
        this.scoringSystem = "BASIC";
        this.mulliganCount = 0;
    }

    public LeagueCategory(String name) {
        this(0, name);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getScoringSystem() {
        return scoringSystem;
    }

    public void setScoringSystem(String scoringSystem) {
        this.scoringSystem = scoringSystem;
    }

    public int getMulliganCount() {
        return mulliganCount;
    }

    public void setMulliganCount(int mulliganCount) {
        this.mulliganCount = mulliganCount;
    }

    public PointsConfig getCustomScale() {
        return customScale;
    }

    public void setCustomScale(PointsConfig customScale) {
        this.customScale = customScale;
    }
}
