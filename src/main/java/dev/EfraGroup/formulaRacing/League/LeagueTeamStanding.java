package dev.EfraGroup.formulaRacing.League;

public class LeagueTeamStanding {

    private final int teamId;
    private final String teamName;
    private final int points;
    private final int wins;
    private final int podiums;

    public LeagueTeamStanding(
        int teamId,
        String teamName,
        int points,
        int wins,
        int podiums
    ) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.points = points;
        this.wins = wins;
        this.podiums = podiums;
    }

    public int getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
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
}
