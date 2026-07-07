package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.Database.LeagueDatabaseManager;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.League.League;
import dev.EfraGroup.formulaRacing.League.LeagueDriver;
import dev.EfraGroup.formulaRacing.League.LeagueStanding;
import dev.EfraGroup.formulaRacing.League.LeagueTeam;
import dev.EfraGroup.formulaRacing.League.LeagueTeamStanding;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeagueManager {

    private final FormulaRacing plugin;
    private final LeagueDatabaseManager databaseManager;
    private final Map<Integer, League> leaguesById;
    private final Map<String, League> leaguesByName;
    private final Map<UUID, Integer> selectedLeagueByPlayer;

    public LeagueManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.databaseManager = new LeagueDatabaseManager(plugin.getDatabaseManager(), plugin);
        this.leaguesById = new ConcurrentHashMap<>();
        this.leaguesByName = new ConcurrentHashMap<>();
        this.selectedLeagueByPlayer = new ConcurrentHashMap<>();
    }

    public void loadLeagues() {
        leaguesById.clear();
        leaguesByName.clear();
        for (League league : databaseManager.loadLeagues()) {
            leaguesById.put(league.getId(), league);
            leaguesByName.put(league.getName().toLowerCase(), league);
        }
    }

    public Collection<League> getAllLeagues() {
        return leaguesById.values();
    }

    public Optional<League> getLeagueByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(leaguesByName.get(name.toLowerCase()));
    }

    public Optional<League> getSelectedLeague(UUID playerUUID) {
        Integer leagueId = selectedLeagueByPlayer.get(playerUUID);
        return leagueId == null ? Optional.empty() : Optional.ofNullable(leaguesById.get(leagueId));
    }

    public void selectLeague(UUID playerUUID, League league) {
        selectedLeagueByPlayer.put(playerUUID, league.getId());
    }

    public League createLeague(UUID creatorUUID, String name) throws SQLException {
        League league = databaseManager.createLeague(creatorUUID, name);
        if (league != null) {
            leaguesById.put(league.getId(), league);
            leaguesByName.put(league.getName().toLowerCase(), league);
        }
        return league;
    }

    public LeagueTeam addTeam(League league, String teamName) throws SQLException {
        LeagueTeam team = databaseManager.addTeam(league.getId(), teamName);
        if (team != null) {
            league.getTeams().put(team.getId(), team);
        }
        return team;
    }

    public LeagueDriver addDriver(League league, UUID playerUUID, String teamName)
        throws SQLException {
        Integer teamId = null;
        if (teamName != null && !teamName.isBlank()) {
            teamId = findTeamId(league, teamName);
            if (teamId == null) {
                return null;
            }
        }
        LeagueDriver driver = databaseManager.addDriver(league.getId(), playerUUID, teamId);
        if (driver != null) {
            league.getDrivers().put(playerUUID, driver);
        }
        return driver;
    }

    public Integer findTeamId(League league, String teamName) {
        for (LeagueTeam team : league.getTeamsView()) {
            if (team.getName().equalsIgnoreCase(teamName)) {
                return team.getId();
            }
        }
        return null;
    }

    public boolean linkEvent(League league, Events event, int roundNumber) {
        try {
            databaseManager.linkEvent(league.getId(), event.getId(), roundNumber);
            plugin.getRaceEventManager().getDatabaseManager().updateEventLeague(
                event.getId(),
                league.getName()
            );
            event.setLeague(league.getName());
            return true;
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation(
                "[League] Error linking event to league: " + e.getMessage()
            );
            return false;
        }
    }

    public void onEventFinished(Events event, List<Driver> results) {
        if (event == null || event.getLeague() == null || event.getLeague().isBlank()) {
            return;
        }

        League league = leaguesByName.get(event.getLeague().toLowerCase());
        if (league == null) {
            return;
        }

        if (!databaseManager.isEventLinkedToLeague(league.getId(), event.getId())) {
            return;
        }

        boolean applied = databaseManager.applyEventResults(league, event.getId(), results);
        if (applied) {
            plugin.getDebugManager().logRaceSystem(
                "[League] Standings atualizadas para a liga " + league.getName()
            );
        }
    }

    public List<LeagueStanding> getDriverStandings(League league) {
        return databaseManager.loadDriverStandings(league.getId());
    }

    public List<LeagueTeamStanding> getTeamStandings(League league) {
        return databaseManager.loadTeamStandings(league.getId());
    }
}
