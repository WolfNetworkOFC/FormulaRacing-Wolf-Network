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
    private final Map<UUID, PendingInvite> pendingInvites;

    public LeagueManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.storage = new LeagueJsonStore(plugin);
        this.leaguesById = new ConcurrentHashMap<>();
        this.leaguesByName = new ConcurrentHashMap<>();
        this.selectedLeagueByPlayer = new ConcurrentHashMap<>();
        this.pendingInvites = new ConcurrentHashMap<>();
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

    public Optional<League> getLeagueById(int leagueId) {
        return Optional.ofNullable(this.leaguesById.get(leagueId));
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
        return this.addDriver(league, playerUUID, teamName, LeagueDriver.Role.MAIN);
    }

    public LeagueDriver addDriver(
        League league,
        UUID playerUUID,
        String teamName,
        LeagueDriver.Role role
    ) throws SQLException {
        Integer teamId = teamName == null || teamName.isBlank() ? null : this.findTeamId(league, teamName);
        if (teamName != null && !teamName.isBlank() && teamId == null) {
            return null;
        }
        if (teamId != null && !this.canJoinTeam(league, teamId, role)) {
            return null;
        }
        return this.storage.addDriver(league, playerUUID, teamId, role);
    }

    private boolean canJoinTeam(League league, Integer teamId, LeagueDriver.Role role) {
        LeagueTeam team = league.getTeams().get(teamId);
        if (team == null) {
            return false;
        }
        if (role == LeagueDriver.Role.RESERVE) {
            int limit = team.effectiveMaxReserves(league);
            return limit <= 0 || team.getReserveDrivers().size() < limit;
        }
        if (role == LeagueDriver.Role.MAIN || role == LeagueDriver.Role.PRIORITY) {
            int limit = team.effectiveMaxMains(league);
            return limit <= 0 || team.getMainDrivers().size() < limit;
        }
        return true;
    }

    public boolean promoteDriver(League league, UUID playerUUID) throws SQLException {
        LeagueDriver driver = league.getDrivers().get(playerUUID);
        if (driver == null || driver.getTeamId() == null) {
            return false;
        }
        LeagueTeam team = league.getTeams().get(driver.getTeamId());
        if (team == null || !team.isReserve(playerUUID)) {
            return false;
        }
        int limit = team.effectiveMaxMains(league);
        if (limit > 0 && team.getMainDrivers().size() >= limit) {
            return false;
        }
        this.storage.setDriverRole(league, playerUUID, LeagueDriver.Role.MAIN);
        return true;
    }

    public boolean demoteDriver(League league, UUID playerUUID) throws SQLException {
        LeagueDriver driver = league.getDrivers().get(playerUUID);
        if (driver == null || driver.getTeamId() == null) {
            return false;
        }
        LeagueTeam team = league.getTeams().get(driver.getTeamId());
        if (team == null || !team.isMain(playerUUID)) {
            return false;
        }
        int limit = team.effectiveMaxReserves(league);
        if (limit > 0 && team.getReserveDrivers().size() >= limit) {
            return false;
        }
        this.storage.setDriverRole(league, playerUUID, LeagueDriver.Role.RESERVE);
        return true;
    }

    public boolean setDriverPriority(League league, UUID playerUUID, int priority)
        throws SQLException {
        LeagueDriver driver = league.getDrivers().get(playerUUID);
        if (driver == null || driver.getTeamId() == null) {
            return false;
        }
        this.storage.setDriverPriority(league, playerUUID, priority);
        return true;
    }

    public boolean removeDriver(League league, UUID playerUUID) throws SQLException {
        if (!league.getDrivers().containsKey(playerUUID)) {
            return false;
        }
        this.storage.removeDriver(league, playerUUID);
        return true;
    }

    public void removeTeam(League league, int teamId) throws SQLException {
        this.storage.removeTeam(league, teamId);
    }

    public void setTeamOwner(League league, int teamId, UUID ownerUUID) throws SQLException {
        this.storage.setTeamOwner(league, teamId, ownerUUID);
    }

    public void setTeamConfig(League league, int teamId, Integer maxMains, Integer maxReserves, Integer countedScorers)
        throws SQLException {
        this.storage.setTeamConfig(league, teamId, maxMains, maxReserves, countedScorers);
    }

    public void setStandingsEnabled(League league, boolean driverStandings, boolean teamStandings)
        throws SQLException {
        this.storage.setStandingsEnabled(league, driverStandings, teamStandings);
    }

    public void inviteDriver(League league, int teamId, UUID targetUUID, UUID inviterUUID) {
        this.pendingInvites.put(targetUUID, new PendingInvite(league.getId(), teamId, inviterUUID));
    }

    public PendingInvite pollInvite(UUID targetUUID) {
        return this.pendingInvites.remove(targetUUID);
    }

    public PendingInvite peekInvite(UUID targetUUID) {
        return this.pendingInvites.get(targetUUID);
    }

    public boolean acceptInvite(UUID targetUUID) throws SQLException {
        PendingInvite invite = this.pendingInvites.remove(targetUUID);
        if (invite == null) {
            return false;
        }
        League league = this.leaguesById.get(invite.leagueId());
        if (league == null) {
            return false;
        }
        LeagueTeam team = league.getTeams().get(invite.teamId());
        if (team == null) {
            return false;
        }
        LeagueDriver existing = league.getDrivers().get(targetUUID);
        if (existing != null && existing.getTeamId() != null && existing.getTeamId() == team.getId()) {
            return true;
        }
        Integer teamId = team.getId();
        if (!this.canJoinTeam(league, teamId, LeagueDriver.Role.MAIN)) {
            this.pendingInvites.put(targetUUID, invite);
            return false;
        }
        if (existing != null) {
            this.storage.setDriverRole(league, targetUUID, LeagueDriver.Role.MAIN);
            existing.setTeamId(teamId);
            team.addMainDriver(targetUUID);
            this.storage.saveLeague(league);
        } else {
            this.storage.addDriver(league, targetUUID, teamId, LeagueDriver.Role.MAIN);
        }
        return true;
    }

    public record PendingInvite(int leagueId, int teamId, UUID inviterUUID) {}

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

    public java.io.File exportCsv(League league) {
        try {
            java.nio.file.Path directory =
                this.plugin.getDataFolder().toPath().resolve("leagues").resolve("exports");
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Path target =
                directory.resolve("league-" + league.getId() + "-standings.csv");
            StringBuilder csv = new StringBuilder();
            csv.append("type,position,name,uuid,team,points,wins,podiums,events\n");
            if (league.isDriverStandingsEnabled()) {
                List<LeagueStanding> standings = this.getDriverStandings(league);
                for (int i = 0; i < standings.size(); i++) {
                    LeagueStanding row = standings.get(i);
                    csv.append("driver").append(',')
                        .append(i + 1).append(',')
                        .append(quote(row.getPlayerName())).append(',')
                        .append(row.getPlayerUUID()).append(',')
                        .append(quote(this.teamName(league, this.findDriverTeam(league, row.getPlayerUUID())))).append(',')
                        .append(row.getPoints()).append(',')
                        .append(row.getWins()).append(',')
                        .append(row.getPodiums()).append(',')
                        .append(row.getEventsCount()).append('\n');
                }
            }
            if (league.isTeamStandingsEnabled()) {
                List<LeagueTeamStanding> standings = this.getTeamStandings(league);
                for (int i = 0; i < standings.size(); i++) {
                    LeagueTeamStanding row = standings.get(i);
                    csv.append("team").append(',')
                        .append(i + 1).append(',')
                        .append(quote(row.getTeamName())).append(',')
                        .append(',').append(',')
                        .append(quote(row.getTeamName())).append(',')
                        .append(row.getPoints()).append(',')
                        .append(row.getWins()).append(',')
                        .append(row.getPodiums()).append(',')
                        .append(',').append('\n');
                }
            }
            java.nio.file.Files.writeString(target, csv.toString(), java.nio.charset.StandardCharsets.UTF_8);
            return target.toFile();
        } catch (java.io.IOException exception) {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[League] Erro ao exportar CSV: " + exception.getMessage()
            );
            return null;
        }
    }

    private String teamName(League league, Integer teamId) {
        if (teamId == null) {
            return "";
        }
        LeagueTeam team = league.getTeams().get(teamId);
        return team == null ? "" : team.getName();
    }

    private Integer findDriverTeam(League league, UUID playerUUID) {
        LeagueDriver driver = league.getDrivers().get(playerUUID);
        return driver == null ? null : driver.getTeamId();
    }

    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
