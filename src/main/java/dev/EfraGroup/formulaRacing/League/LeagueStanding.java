package dev.EfraGroup.formulaRacing.League;

import java.util.UUID;

public class LeagueStanding {

    private final UUID playerUUID;
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

    public UUID getPlayerUUID() {
        return playerUUID;
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
