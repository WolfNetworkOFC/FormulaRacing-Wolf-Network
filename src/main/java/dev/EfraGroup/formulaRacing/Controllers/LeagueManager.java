package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.League.League;
import dev.EfraGroup.formulaRacing.League.LeagueCalendarEntry;
import dev.EfraGroup.formulaRacing.League.LeagueCategory;
import dev.EfraGroup.formulaRacing.League.LeagueDriver;
import dev.EfraGroup.formulaRacing.League.LeagueJsonStore;
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
    private final LeagueJsonStore storage;
    private final Map<Integer, League> leaguesById;
    private final Map<String, League> leaguesByName;
    private final Map<UUID, Integer> selectedLeagueByPlayer;

    public LeagueManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.storage = new LeagueJsonStore(plugin);
        this.leaguesById = new ConcurrentHashMap<>();
        this.leaguesByName = new ConcurrentHashMap<>();
        this.selectedLeagueByPlayer = new ConcurrentHashMap<>();
        this.loadLeagues();
    }

    public void loadLeagues() {
        this.leaguesById.clear();
        this.leaguesByName.clear();
        for (League league : this.storage.loadLeagues()) {
            this.leaguesById.put(league.getId(), league);
            this.leaguesByName.put(league.getName().toLowerCase(), league);
        }
    }

    public Collection<League> getAllLeagues() {
        return this.leaguesById.values();
    }

    public Optional<League> getLeagueByName(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(this.leaguesByName.get(name.toLowerCase()));
    }

    public Optional<League> getLeagueByEventId(int eventId) {
        return this.leaguesById.values().stream()
            .filter(league -> league.getCalendar().containsKey(eventId))
            .findFirst();
    }

    public Optional<League> getSelectedLeague(UUID playerUUID) {
        Integer leagueId = this.selectedLeagueByPlayer.get(playerUUID);
        return leagueId == null ? Optional.empty() : Optional.ofNullable(this.leaguesById.get(leagueId));
    }

    public void selectLeague(UUID playerUUID, League league) {
        this.selectedLeagueByPlayer.put(playerUUID, league.getId());
    }

    public void deselectPlayer(UUID playerUUID) {
        this.selectedLeagueByPlayer.remove(playerUUID);
    }

    public League createLeague(UUID creatorUUID, String name) throws SQLException {
        League league = this.storage.createLeague(creatorUUID, name);
        if (league != null) {
            this.leaguesById.put(league.getId(), league);
            this.leaguesByName.put(league.getName().toLowerCase(), league);
        }
        return league;
    }

    public LeagueTeam addTeam(League league, String teamName) throws SQLException {
        return this.storage.addTeam(league, teamName);
    }

    public LeagueDriver addDriver(League league, UUID playerUUID, String teamName) throws SQLException {
        Integer teamId = teamName == null || teamName.isBlank() ? null : this.findTeamId(league, teamName);
        if (teamName != null && !teamName.isBlank() && teamId == null) {
            return null;
        }
        return this.storage.addDriver(league, playerUUID, teamId);
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
            this.storage.linkEvent(league, event.getId(), roundNumber);
            return true;
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[League] Erro ao vincular evento: " + exception.getMessage()
            );
            return false;
        }
    }

    public void unlinkEvent(League league, int eventId) {
        try {
            this.storage.unlinkEvent(league, eventId);
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[League] Erro ao desvincular evento: " + exception.getMessage()
            );
        }
    }

    public void deleteLeague(League league) {
        try {
            this.storage.deleteLeague(league);
            this.leaguesById.remove(league.getId());
            this.leaguesByName.remove(league.getName().toLowerCase());
            this.selectedLeagueByPlayer.entrySet().removeIf(entry -> entry.getValue() == league.getId());
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[League] Erro ao excluir liga: " + exception.getMessage()
            );
        }
    }

    public void onEventFinished(Events event, List<Driver> results) {
        League league = this.getLeagueByEventId(event.getId()).orElse(null);
        if (league == null) {
            return;
        }
        String categoryName = this.resolveEventCategory(league, event.getId());
        Integer pinnedHeatId = this.resolvePinnedHeat(league, event.getId());
        try {
            if (this.storage.storeEventResults(league, event.getId(), results, categoryName, pinnedHeatId)) {
                this.plugin.getDebugManager().logRaceSystem(
                    "[League] Standings atualizadas para a liga " + league.getName()
                );
            }
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[League] Erro ao armazenar resultados: " + exception.getMessage()
            );
        }
    }

    private String resolveEventCategory(League league, int eventId) {
        LeagueCalendarEntry entry = league.getCalendar().get(eventId);
        return entry != null && entry.hasCategory() ? entry.getCategoryName() : null;
    }

    private Integer resolvePinnedHeat(League league, int eventId) {
        LeagueCalendarEntry entry = league.getCalendar().get(eventId);
        return entry != null && entry.hasPinnedHeat() ? entry.getPinnedHeatId() : null;
    }

    public List<LeagueStanding> getDriverStandings(League league) {
        return this.storage.loadDriverStandings(league.getId());
    }

    public List<LeagueTeamStanding> getTeamStandings(League league) {
        return this.storage.loadTeamStandings(league.getId());
    }

    public void adjustPoints(League league, UUID playerUUID, int delta) {
        try {
            this.storage.adjustDriverPoints(league, playerUUID, delta, "ADMIN");
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[League] Erro ao ajustar pontos: " + exception.getMessage()
            );
        }
    }

    public void transferPoints(League league, UUID fromUUID, UUID toUUID, int amount) {
        try {
            this.storage.transferDriverPoints(league, fromUUID, toUUID, amount);
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[League] Erro ao transferir pontos: " + exception.getMessage()
            );
        }
    }

    public void saveLeagueConfig(League league) throws SQLException {
        this.storage.saveLeagueConfig(league);
    }

    public LeagueCategory addCategory(League league, String name) throws SQLException {
        LeagueCategory category = new LeagueCategory(name);
        this.storage.saveCategory(league, category);
        return category;
    }

    public void removeCategory(League league, String name) throws SQLException {
        this.storage.removeCategory(league, name);
    }

    public void setEventMeta(League league, int eventId, String categoryName, Integer pinnedHeatId)
        throws SQLException {
        this.storage.setEventMeta(league, eventId, categoryName, pinnedHeatId);
    }

    public void recalculate(League league) {
        try {
            this.storage.recalculateStandings(league);
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[League] Erro ao recalcular standings: " + exception.getMessage()
            );
        }
    }
}
