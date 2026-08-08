package dev.EfraGroup.formulaRacing.League.Hologram;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Hologram.HologramManager;
import dev.EfraGroup.formulaRacing.League.League;
import dev.EfraGroup.formulaRacing.League.LeagueStanding;
import dev.EfraGroup.formulaRacing.League.LeagueTeamStanding;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class LeagueHologramService {

    private static final int MAX_LINES = 15;
    private final FormulaRacing plugin;
    private final HologramManager hologramManager;

    public LeagueHologramService(FormulaRacing plugin) {
        this.plugin = plugin;
        this.hologramManager = plugin.getHologramManager();
    }

    public void createDriverHologram(League league, Location loc) {
        String name = "league-driver-" + league.getId();
        hologramManager.createHologramSelectable(name, loc, buildDriverLines(league));
    }

    public void createTeamHologram(League league, Location loc) {
        String name = "league-team-" + league.getId();
        hologramManager.createHologramSelectable(name, loc, buildTeamLines(league));
    }

    public void updateHolograms(League league) {
        hologramManager.updateHologram("league-driver-" + league.getId(), buildDriverLines(league));
        hologramManager.updateHologram("league-team-" + league.getId(), buildTeamLines(league));
    }

    public void removeHolograms(League league) {
        hologramManager.deleteHologram("league-driver-" + league.getId());
        hologramManager.deleteHologram("league-team-" + league.getId());
    }

    private List<String> buildDriverLines(League league) {
        List<String> lines = new ArrayList<>();
        lines.add("&c" + league.getName() + " driver leaderboard");
        List<LeagueStanding> standings = plugin.getLeagueManager().getDriverStandings(league);
        for (int i = 0; i < Math.min(MAX_LINES - 1, standings.size()); i++) {
            LeagueStanding s = standings.get(i);
            String pName = Bukkit.getOfflinePlayer(s.getPlayerUUID()).getName();
            lines.add("&e#" + (i + 1) + ". &b" + (pName != null ? pName : "Unknown")
                    + " &7- &a" + s.getPoints() + " pts");
        }
        while (lines.size() < MAX_LINES) {
            lines.add("&e#" + lines.size() + ". &7---");
        }
        return lines;
    }

    private List<String> buildTeamLines(League league) {
        List<String> lines = new ArrayList<>();
        lines.add("&c" + league.getName() + " team leaderboard");
        List<LeagueTeamStanding> standings = plugin.getLeagueManager().getTeamStandings(league);
        for (int i = 0; i < Math.min(MAX_LINES - 1, standings.size()); i++) {
            LeagueTeamStanding s = standings.get(i);
            lines.add("&e#" + (i + 1) + ". &b" + s.getTeamName()
                    + " &7- &a" + s.getPoints() + " pts");
        }
        while (lines.size() < MAX_LINES) {
            lines.add("&e#" + lines.size() + ". &7---");
        }
        return lines;
    }
}
