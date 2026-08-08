package dev.EfraGroup.formulaRacing.League;

import dev.EfraGroup.formulaRacing.Pontuation.PointsConfig;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class League {

    private final int id;
    private final UUID creatorUUID;
    private final String name;
    private LeagueStatus status;
    private final Map<Integer, LeagueTeam> teams;
    private final Map<UUID, LeagueDriver> drivers;

    private String scoringSystem = "BASIC";
    private PointsConfig customScale;
    private TeamMode teamMode = TeamMode.MAIN_RESERVE;
    private TeamConfig teamConfig = new TeamConfig();
    private int mulliganCount = 0;
    private final Map<String, LeagueCategory> categories = new LinkedHashMap<>();
    private final Map<Integer, LeagueCalendarEntry> calendar = new LinkedHashMap<>();

    public League(int id, UUID creatorUUID, String name, LeagueStatus status) {
        this.id = id;
        this.creatorUUID = creatorUUID;
        this.name = name;
        this.status = status;
        this.teams = new LinkedHashMap<>();
        this.drivers = new LinkedHashMap<>();
    }

    public int getId() {
        return id;
    }

    public UUID getCreatorUUID() {
        return creatorUUID;
    }

    public String getName() {
        return name;
    }

    public LeagueStatus getStatus() {
        return status;
    }

    public void setStatus(LeagueStatus status) {
        this.status = status;
    }

    public Map<Integer, LeagueTeam> getTeams() {
        return teams;
    }

    public Map<UUID, LeagueDriver> getDrivers() {
        return drivers;
    }

    public Collection<LeagueTeam> getTeamsView() {
        return teams.values();
    }

    public Collection<LeagueDriver> getDriversView() {
        return drivers.values();
    }

    public String getScoringSystem() {
        return scoringSystem;
    }

    public void setScoringSystem(String scoringSystem) {
        this.scoringSystem = scoringSystem;
    }

    public PointsConfig getCustomScale() {
        return customScale;
    }

    public void setCustomScale(PointsConfig customScale) {
        this.customScale = customScale;
    }

    public TeamMode getTeamMode() {
        return teamMode;
    }

    public void setTeamMode(TeamMode teamMode) {
        this.teamMode = teamMode;
    }

    public TeamConfig getTeamConfig() {
        return teamConfig;
    }

    public void setTeamConfig(TeamConfig teamConfig) {
        this.teamConfig = teamConfig;
    }

    public int getMulliganCount() {
        return mulliganCount;
    }

    public void setMulliganCount(int mulliganCount) {
        this.mulliganCount = mulliganCount;
    }

    public Map<String, LeagueCategory> getCategories() {
        return categories;
    }

    public LeagueCategory getCategory(String name) {
        return categories.get(name.toLowerCase());
    }

    public Map<Integer, LeagueCalendarEntry> getCalendar() {
        return calendar;
    }
}
