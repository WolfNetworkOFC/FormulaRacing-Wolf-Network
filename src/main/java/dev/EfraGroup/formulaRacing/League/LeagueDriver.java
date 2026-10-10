package dev.EfraGroup.formulaRacing.League;

import java.util.UUID;

public class LeagueDriver {

    public enum Role {
        MAIN,
        RESERVE,
        PRIORITY
    }

    private final int id;
    private final int leagueId;
    private final UUID playerUUID;
    private String playerName;
    private Integer teamId;
    private Role role = Role.MAIN;
    private Integer priority;

    public LeagueDriver(int id, int leagueId, UUID playerUUID, Integer teamId) {
        this.id = id;
        this.leagueId = leagueId;
        this.playerUUID = playerUUID;
        this.teamId = teamId;
    }

    public LeagueDriver(int id, int leagueId, UUID playerUUID, String playerName, Integer teamId) {
        this(id, leagueId, playerUUID, teamId);
        this.playerName = playerName;
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

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
