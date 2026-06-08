package dev.EfraGroup.formulaRacing.League;

import java.util.UUID;

public class LeagueDriver {

    private final int id;
    private final int leagueId;
    private final UUID playerUUID;
    private Integer teamId;

    public LeagueDriver(int id, int leagueId, UUID playerUUID, Integer teamId) {
        this.id = id;
        this.leagueId = leagueId;
        this.playerUUID = playerUUID;
        this.teamId = teamId;
    }

    public int getId() {
        return id;
    }

    public int getLeagueId() {
        return leagueId;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }
}
