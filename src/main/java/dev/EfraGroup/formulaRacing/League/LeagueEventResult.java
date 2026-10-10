package dev.EfraGroup.formulaRacing.League;

import java.util.UUID;

public class LeagueEventResult {
    private int eventId;
    private String categoryName;
    private int position;
    private UUID playerUUID;
    private String playerName;
    private Integer teamId;
    private int points;
    private Integer heatId;

    private LeagueEventResult() {}

    public LeagueEventResult(
        int eventId,
        String categoryName,
        int position,
        UUID playerUUID,
        String playerName,
        Integer teamId,
        int points,
        Integer heatId
    ) {
        this.eventId = eventId;
        this.categoryName = categoryName;
        this.position = position;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.teamId = teamId;
        this.points = points;
        this.heatId = heatId;
    }

    public int getEventId() {
        return eventId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public int getPosition() {
        return position;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public int getPoints() {
        return points;
    }

    public Integer getHeatId() {
        return heatId;
    }
}