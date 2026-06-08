package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.Controllers.LeagueManager;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.League.League;
import dev.EfraGroup.formulaRacing.League.LeagueStanding;
import dev.EfraGroup.formulaRacing.League.LeagueTeamStanding;
import java.sql.SQLException;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

@CommandAlias("league")
public class LeagueCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final LeagueManager leagueManager;

    public LeagueCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.leagueManager = plugin.getLeagueManager();
    }

    @Default
    public void onDefault(Player player) {
        League league = leagueManager.getSelectedLeague(player.getUniqueId()).orElse(null);
        if (league == null) {
            plugin.sendMessage(player, "league_none_selected");
            return;
        }
        showInfo(player, league);
    }

    @Subcommand("create")
    @CommandPermission("formularacing.event.admin")
    @Description("Cria uma liga")
    public void onCreate(Player player, String name) {
        try {
            League league = leagueManager.createLeague(player.getUniqueId(), name);
            if (league == null) {
                plugin.sendMessage(player, "league_create_error");
                return;
            }
            leagueManager.selectLeague(player.getUniqueId(), league);
            plugin.sendMessage(player, "league_created", "{league}", league.getName());
        } catch (SQLException e) {
            plugin.sendMessage(player, "league_create_error");
        }
    }

    @Subcommand("list")
    public void onList(Player player) {
        plugin.sendMessage(player, "league_list_header");
        for (League league : leagueManager.getAllLeagues()) {
            plugin.sendMessage(
                player,
                "league_list_row",
                "{league}",
                league.getName(),
                "{drivers}",
                String.valueOf(league.getDrivers().size())
            );
        }
    }

    @Subcommand("select")
    @CommandCompletion("@leagues")
    public void onSelect(Player player, String leagueName) {
        League league = leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(player, "league_not_found", "{league}", leagueName);
            return;
        }
        leagueManager.selectLeague(player.getUniqueId(), league);
        plugin.sendMessage(player, "league_selected", "{league}", league.getName());
        showInfo(player, league);
    }

    @Subcommand("info")
    @CommandCompletion("@leagues")
    public void onInfo(Player player, @Optional String leagueName) {
        League league = leagueName == null
            ? leagueManager.getSelectedLeague(player.getUniqueId()).orElse(null)
            : leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(player, "league_none_selected");
            return;
        }
        showInfo(player, league);
    }

    @Subcommand("addteam")
    @CommandPermission("formularacing.event.admin")
    public void onAddTeam(Player player, String teamName) {
        League league = leagueManager.getSelectedLeague(player.getUniqueId()).orElse(null);
        if (league == null) {
            plugin.sendMessage(player, "league_none_selected");
            return;
        }
        try {
            if (leagueManager.addTeam(league, teamName) == null) {
                plugin.sendMessage(player, "league_team_add_error", "{team}", teamName);
                return;
            }
            plugin.sendMessage(
                player,
                "league_team_added",
                "{team}",
                teamName,
                "{league}",
                league.getName()
            );
        } catch (SQLException e) {
            plugin.sendMessage(player, "league_team_add_error", "{team}", teamName);
        }
    }

    @Subcommand("adddriver")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@players @nothing")
    public void onAddDriver(Player player, String playerName, @Optional String teamName) {
        League league = leagueManager.getSelectedLeague(player.getUniqueId()).orElse(null);
        if (league == null) {
            plugin.sendMessage(player, "league_none_selected");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            plugin.sendMessage(player, "player_not_found");
            return;
        }
        try {
            if (leagueManager.addDriver(league, target.getUniqueId(), teamName) == null) {
                plugin.sendMessage(player, "league_driver_add_error", "{player}", playerName);
                return;
            }
            plugin.sendMessage(
                player,
                "league_driver_added",
                "{player}",
                playerName,
                "{league}",
                league.getName()
            );
        } catch (SQLException e) {
            plugin.sendMessage(player, "league_driver_add_error", "{player}", playerName);
        }
    }

    @Subcommand("linkevent")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@event")
    public void onLinkEvent(Player player, String eventName, @Optional String roundNumberText) {
        League league = leagueManager.getSelectedLeague(player.getUniqueId()).orElse(null);
        if (league == null) {
            plugin.sendMessage(player, "league_none_selected");
            return;
        }
        Events event = plugin.getRaceEventManager().getEventByName(eventName).orElse(null);
        if (event == null) {
            plugin.sendMessage(player, "event_not_found");
            return;
        }
        int roundNumber = 1;
        if (roundNumberText != null) {
            try {
                roundNumber = Integer.parseInt(roundNumberText);
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, "invalid_number");
                return;
            }
        }
        if (!leagueManager.linkEvent(league, event, roundNumber)) {
            plugin.sendMessage(player, "league_link_error");
            return;
        }
        plugin.sendMessage(
            player,
            "league_event_linked",
            "{league}",
            league.getName(),
            "{event}",
            event.getDisplayName()
        );
    }

    @Subcommand("standings")
    @CommandCompletion("drivers|teams @leagues")
    public void onStandings(Player player, @Optional String type, @Optional String leagueName) {
        League league = leagueName == null
            ? leagueManager.getSelectedLeague(player.getUniqueId()).orElse(null)
            : leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(player, "league_none_selected");
            return;
        }

        if ("teams".equalsIgnoreCase(type)) {
            plugin.sendMessage(player, "league_standings_teams_header", "{league}", league.getName());
            List<LeagueTeamStanding> standings = leagueManager.getTeamStandings(league);
            for (int i = 0; i < standings.size(); i++) {
                LeagueTeamStanding row = standings.get(i);
                plugin.sendMessage(
                    player,
                    "league_standings_team_row",
                    "{position}",
                    String.valueOf(i + 1),
                    "{team}",
                    row.getTeamName(),
                    "{points}",
                    String.valueOf(row.getPoints())
                );
            }
            return;
        }

        plugin.sendMessage(player, "league_standings_drivers_header", "{league}", league.getName());
        List<LeagueStanding> standings = leagueManager.getDriverStandings(league);
        for (int i = 0; i < standings.size(); i++) {
            LeagueStanding row = standings.get(i);
            String name = Bukkit.getOfflinePlayer(row.getPlayerUUID()).getName();
            if (name == null) {
                name = row.getPlayerUUID().toString();
            }
            plugin.sendMessage(
                player,
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

    private void showInfo(Player player, League league) {
        plugin.sendMessage(player, "league_info_header", "{league}", league.getName());
        plugin.sendMessage(
            player,
            "league_info_status",
            "{status}",
            league.getStatus().name()
        );
        plugin.sendMessage(
            player,
            "league_info_teams",
            "{teams}",
            String.valueOf(league.getTeams().size())
        );
        plugin.sendMessage(
            player,
            "league_info_drivers",
            "{drivers}",
            String.valueOf(league.getDrivers().size())
        );
    }
}
