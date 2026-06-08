package dev.EfraGroup.formulaRacing.League;

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
}
