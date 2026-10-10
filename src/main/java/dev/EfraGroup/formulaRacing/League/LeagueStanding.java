package dev.EfraGroup.formulaRacing.League;

import java.util.UUID;

public class LeagueStanding {

    private final UUID playerUUID;
    private String playerName;
    private final int points;
    private final int wins;
    private final int podiums;
    private final int eventsCount;

    public LeagueStanding(
        UUID playerUUID,
        int points,
        int wins,
        int podiums,
        int eventsCount
    ) {
        this.playerUUID = playerUUID;
        this.points = points;
        this.wins = wins;
        this.podiums = podiums;
        this.eventsCount = eventsCount;
    }

    public LeagueStanding(
        UUID playerUUID,
        String playerName,
        int points,
        int wins,
        int podiums,
        int eventsCount
    ) {
        this(playerUUID, points, wins, podiums, eventsCount);
        this.playerName = playerName;
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

    public int getPoints() {
        return points;
    }

    public int getWins() {
        return wins;
    }

    public int getPodiums() {
        return podiums;
    }

    public int getEventsCount() {
        return eventsCount;
    }
}
