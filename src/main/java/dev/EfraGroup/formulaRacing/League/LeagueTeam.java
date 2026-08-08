package dev.EfraGroup.formulaRacing.League;

public class LeagueTeam {

    private final int id;
    private final int leagueId;
    private final String name;
    private String colorHex;

    public LeagueTeam(int id, int leagueId, String name) {
        this.id = id;
        this.leagueId = leagueId;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public int getLeagueId() {
        return leagueId;
    }

    public String getName() {
        return name;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }
}
