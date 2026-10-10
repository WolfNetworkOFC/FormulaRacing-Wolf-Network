package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.League.League;
import dev.EfraGroup.formulaRacing.League.LeagueStanding;
import dev.EfraGroup.formulaRacing.League.LeagueTeamStanding;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import java.util.List;

public class LeagueNameCommand extends Command {

    private final FormulaRacing plugin;
    private final League league;

    public LeagueNameCommand(FormulaRacing plugin, League league) {
        super(LeagueNameCommand.sanitize(league.getName()));
        this.plugin = plugin;
        this.league = league;
        this.setDescription("Informações e standings da liga " + league.getName());
    }

    public static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "league";
        }
        String cleaned = name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return cleaned.isEmpty() ? "league" : cleaned;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && "teams".equalsIgnoreCase(args[0])) {
            this.showTeamStandings(sender);
            return true;
        }
        if (args.length > 0 && ("standings".equalsIgnoreCase(args[0]) || "s".equalsIgnoreCase(args[0]))) {
            this.showDriverStandings(sender);
            return true;
        }
        this.showInfo(sender);
        return true;
    }

    private void showInfo(CommandSender sender) {
        this.plugin.sendMessage(sender, "league_info_header", "{league}", this.league.getName());
        this.plugin.sendMessage(
            sender,
            "league_info_status",
            "{status}",
            this.league.getStatus().name()
        );
        this.plugin.sendMessage(
            sender,
            "league_info_teams",
            "{teams}",
            String.valueOf(this.league.getTeams().size())
        );
        this.plugin.sendMessage(
            sender,
            "league_info_drivers",
            "{drivers}",
            String.valueOf(this.league.getDrivers().size())
        );
    }

    private void showDriverStandings(CommandSender sender) {
        this.plugin.sendMessage(
            sender,
            "league_standings_drivers_header",
            "{league}",
            this.league.getName()
        );
        List<LeagueStanding> standings = this.plugin.getLeagueManager().getDriverStandings(this.league);
        for (int i = 0; i < standings.size(); i++) {
            LeagueStanding row = standings.get(i);
            String name = row.getPlayerName();
            if (name == null || name.isBlank()) {
                name = Bukkit.getOfflinePlayer(row.getPlayerUUID()).getName();
            }
            if (name == null) {
                name = row.getPlayerUUID().toString();
            }
            this.plugin.sendMessage(
                sender,
                "league_standings_driver_row",
                "{position}",
                String.valueOf(i + 1),
                "{player}",
                name,
                "{points}",
                String.valueOf(row.getPoints())
            );
        }
    }

    private void showTeamStandings(CommandSender sender) {
        this.plugin.sendMessage(
            sender,
            "league_standings_teams_header",
            "{league}",
            this.league.getName()
        );
        List<LeagueTeamStanding> standings = this.plugin.getLeagueManager().getTeamStandings(this.league);
        for (int i = 0; i < standings.size(); i++) {
            LeagueTeamStanding row = standings.get(i);
            this.plugin.sendMessage(
                sender,
                "league_standings_team_row",
                "{position}",
                String.valueOf(i + 1),
                "{team}",
                row.getTeamName(),
                "{points}",
                String.valueOf(row.getPoints())
            );
        }
    }
}
