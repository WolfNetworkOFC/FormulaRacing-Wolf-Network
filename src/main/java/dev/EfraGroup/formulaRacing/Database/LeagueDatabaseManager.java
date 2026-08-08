package dev.EfraGroup.formulaRacing.Database;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.League.League;
import dev.EfraGroup.formulaRacing.League.LeagueCalendarEntry;
import dev.EfraGroup.formulaRacing.League.LeagueCategory;
import dev.EfraGroup.formulaRacing.League.LeagueDriver;
import dev.EfraGroup.formulaRacing.League.LeagueStanding;
import dev.EfraGroup.formulaRacing.League.LeagueStatus;
import dev.EfraGroup.formulaRacing.League.LeagueTeam;
import dev.EfraGroup.formulaRacing.League.LeagueTeamStanding;
import dev.EfraGroup.formulaRacing.League.StandingsUpdater;
import dev.EfraGroup.formulaRacing.League.StandingsUpdater.DriverEventResult;
import dev.EfraGroup.formulaRacing.League.TeamConfig;
import dev.EfraGroup.formulaRacing.League.TeamMode;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Pontuation.PointsConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LeagueDatabaseManager {

    private final DatabaseManager databaseManager;
    private final FormulaRacing plugin;

    public LeagueDatabaseManager(DatabaseManager databaseManager, FormulaRacing plugin) {
        this.databaseManager = databaseManager;
        this.plugin = plugin;
    }

    public List<League> loadLeagues() {
        List<League> leagues = new ArrayList<>();
        String sql = "SELECT * FROM fr_leagues ORDER BY name";

        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery()
        ) {
            while (rs.next()) {
                int leagueId = rs.getInt("id");
                League league = new League(
                    leagueId,
                    UUID.fromString(rs.getString("creatorUUID")),
                    rs.getString("name"),
                    LeagueStatus.valueOf(rs.getString("status"))
                );
                loadLeagueConfig(rs, league);
                loadCategories(conn, league);
                loadTeams(conn, league);
                loadDrivers(conn, league);
                loadCalendar(conn, league);
                leagues.add(league);
            }
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation(
                "[LeagueDB] Erro ao carregar ligas: " + e.getMessage()
            );
        }

        return leagues;
    }

    private void loadLeagueConfig(ResultSet rs, League league) throws SQLException {
        String scoring = rs.getString("scoringSystem");
        if (scoring != null) league.setScoringSystem(scoring);
        String teamMode = rs.getString("teamMode");
        if (teamMode != null) {
            try { league.setTeamMode(TeamMode.valueOf(teamMode)); } catch (Exception ignored) {}
        }
        String teamConfigJson = rs.getString("teamConfigJson");
        if (teamConfigJson != null && !teamConfigJson.isBlank()) {
            league.setTeamConfig(TeamConfig.fromJson(teamConfigJson));
        }
        league.setMulliganCount(rs.getInt("mulliganCount"));
        String scaleJson = rs.getString("customScaleJson");
        if (scaleJson != null && !scaleJson.isBlank()) {
            league.setCustomScale(PointsConfig.fromJson(scaleJson));
        }
    }

    private void loadCategories(Connection conn, League league) throws SQLException {
        String sql = "SELECT * FROM fr_league_categories WHERE leagueId = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, league.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LeagueCategory cat = new LeagueCategory(rs.getInt("id"), rs.getString("name"));
                    cat.setDisplayName(rs.getString("displayName"));
                    cat.setScoringSystem(rs.getString("scoringSystem"));
                    cat.setMulliganCount(rs.getInt("mulliganCount"));
                    String scale = rs.getString("customScaleJson");
                    if (scale != null && !scale.isBlank()) {
                        cat.setCustomScale(PointsConfig.fromJson(scale));
                    }
                    league.getCategories().put(cat.getName().toLowerCase(), cat);
                }
            }
        }
    }

    private void loadCalendar(Connection conn, League league) throws SQLException {
        String sql = "SELECT * FROM fr_league_event_meta WHERE leagueId = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, league.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int eventId = rs.getInt("eventId");
                    LeagueCalendarEntry entry = league.getCalendar().computeIfAbsent(
                        eventId, LeagueCalendarEntry::new);
                    String cat = rs.getString("categoryName");
                    if (cat != null) entry.setCategoryName(cat);
                    int heatId = rs.getInt("pinnedHeatId");
                    if (!rs.wasNull()) entry.setPinnedHeatId(heatId);
                }
            }
        }
    }

    private void loadTeams(Connection conn, League league) throws SQLException {
        String sql = "SELECT * FROM fr_league_teams WHERE leagueId = ? ORDER BY name";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, league.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LeagueTeam team = new LeagueTeam(
                        rs.getInt("id"),
                        league.getId(),
                        rs.getString("name")
                    );
                    team.setColorHex(rs.getString("colorHex"));
                    league.getTeams().put(team.getId(), team);
                }
            }
        }
    }

    private void loadDrivers(Connection conn, League league) throws SQLException {
        String sql = "SELECT * FROM fr_league_drivers WHERE leagueId = ? ORDER BY id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, league.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int teamId = rs.getInt("teamId");
                    LeagueDriver driver = new LeagueDriver(
                        rs.getInt("id"),
                        league.getId(),
                        UUID.fromString(rs.getString("playerUUID")),
                        rs.wasNull() ? null : teamId
                    );
                    league.getDrivers().put(driver.getPlayerUUID(), driver);
                }
            }
        }
    }

    public League createLeague(UUID creatorUUID, String name) throws SQLException {
        String sql =
            "INSERT INTO fr_leagues (creatorUUID, name, status, createdAt) VALUES (?, ?, ?, ?)";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql, 1)
        ) {
            stmt.setString(1, creatorUUID.toString());
            stmt.setString(2, name);
            stmt.setString(3, LeagueStatus.SETUP.name());
            stmt.setLong(4, Instant.now().getEpochSecond());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new League(
                        rs.getInt(1),
                        creatorUUID,
                        name,
                        LeagueStatus.SETUP
                    );
                }
            }
        }
        return null;
    }

    public LeagueTeam addTeam(int leagueId, String teamName) throws SQLException {
        String sql = "INSERT INTO fr_league_teams (leagueId, name) VALUES (?, ?)";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql, 1)
        ) {
            stmt.setInt(1, leagueId);
            stmt.setString(2, teamName);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new LeagueTeam(rs.getInt(1), leagueId, teamName);
                }
            }
        }
        return null;
    }

    public LeagueDriver addDriver(int leagueId, UUID playerUUID, Integer teamId)
        throws SQLException {
        String sql =
            "INSERT INTO fr_league_drivers (leagueId, playerUUID, teamId, joinedAt) VALUES (?, ?, ?, ?)";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql, 1)
        ) {
            stmt.setInt(1, leagueId);
            stmt.setString(2, playerUUID.toString());
            if (teamId == null) {
                stmt.setNull(3, Types.INTEGER);
            } else {
                stmt.setInt(3, teamId);
            }
            stmt.setLong(4, Instant.now().getEpochSecond());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new LeagueDriver(rs.getInt(1), leagueId, playerUUID, teamId);
                }
            }
        }
        return null;
    }

    public void linkEvent(int leagueId, int eventId, int roundNumber) throws SQLException {
        String sql =
            "INSERT OR REPLACE INTO fr_league_events (leagueId, eventId, roundNumber, pointsApplied) VALUES (?, ?, ?, COALESCE((SELECT pointsApplied FROM fr_league_events WHERE leagueId = ? AND eventId = ?), 0))";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, leagueId);
            stmt.setInt(2, eventId);
            stmt.setInt(3, roundNumber);
            stmt.setInt(4, leagueId);
            stmt.setInt(5, eventId);
            stmt.executeUpdate();
        }
    }

    public boolean isEventLinkedToLeague(int leagueId, int eventId) {
        String sql =
            "SELECT 1 FROM fr_league_events WHERE leagueId = ? AND eventId = ? LIMIT 1";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, leagueId);
            stmt.setInt(2, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation(
                "[LeagueDB] Error checking event link: " + e.getMessage()
            );
            return false;
        }
    }

    public void saveLeagueConfig(League league) throws SQLException {
        String sql =
            "UPDATE fr_leagues SET scoringSystem = ?, teamMode = ?, teamConfigJson = ?, " +
            "mulliganCount = ?, customScaleJson = ? WHERE id = ?";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, league.getScoringSystem());
            stmt.setString(2, league.getTeamMode().name());
            stmt.setString(3, league.getTeamConfig() != null ? league.getTeamConfig().toJson() : null);
            stmt.setInt(4, league.getMulliganCount());
            stmt.setString(5, league.getCustomScale() != null ? league.getCustomScale().toJson() : null);
            stmt.setInt(6, league.getId());
            stmt.executeUpdate();
        }
    }

    public void saveCategory(League league, LeagueCategory cat) throws SQLException {
        String sql =
            "INSERT OR REPLACE INTO fr_league_categories " +
            "(id, leagueId, name, displayName, scoringSystem, mulliganCount, customScaleJson) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql, 1)
        ) {
            if (cat.getId() > 0) stmt.setInt(1, cat.getId());
            else stmt.setNull(1, Types.INTEGER);
            stmt.setInt(2, league.getId());
            stmt.setString(3, cat.getName());
            stmt.setString(4, cat.getDisplayName());
            stmt.setString(5, cat.getScoringSystem());
            stmt.setInt(6, cat.getMulliganCount());
            stmt.setString(7, cat.getCustomScale() != null ? cat.getCustomScale().toJson() : null);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) cat.setId(rs.getInt(1));
            }
        }
    }

    public void removeCategory(League league, String name) throws SQLException {
        String sql = "DELETE FROM fr_league_categories WHERE leagueId = ? AND name = ?";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, league.getId());
            stmt.setString(2, name);
            stmt.executeUpdate();
        }
    }

    public void setEventMeta(int leagueId, int eventId, String categoryName, Integer pinnedHeatId)
        throws SQLException {
        String sql =
            "INSERT OR REPLACE INTO fr_league_event_meta (leagueId, eventId, categoryName, pinnedHeatId) " +
            "VALUES (?, ?, ?, ?)";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, leagueId);
            stmt.setInt(2, eventId);
            if (categoryName == null) stmt.setNull(3, Types.VARCHAR);
            else stmt.setString(3, categoryName);
            if (pinnedHeatId == null) stmt.setNull(4, Types.INTEGER);
            else stmt.setInt(4, pinnedHeatId);
            stmt.executeUpdate();
        }
    }

    /**
     * Stores per-event results into fr_league_event_results (replacing any prior
     * results for that event in the same category) and triggers a full
     * standings recalculation for the league.
     */
    public boolean storeEventResults(
            League league, int eventId, List<Driver> results, String categoryName, Integer pinnedHeatId) {
        if (league == null || results == null || results.isEmpty()) {
            return false;
        }

        String catKey = (categoryName != null && !categoryName.isBlank())
                ? categoryName.toLowerCase() : null;

        String deleteSql =
            "DELETE FROM fr_league_event_results WHERE leagueId = ? AND eventId = ? AND " +
            "COALESCE(categoryName, '') = COALESCE(?, '')";
        String insertSql =
            "INSERT INTO fr_league_event_results " +
            "(leagueId, eventId, position, playerUUID, teamId, points, categoryName, heatId) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String markAppliedSql =
            "UPDATE fr_league_events SET pointsApplied = 1 WHERE leagueId = ? AND eventId = ?";

        try {
            Connection conn = databaseManager.getOrConnect();
            conn.setAutoCommit(false);
            try (
                PreparedStatement delStmt = conn.prepareStatement(deleteSql);
                PreparedStatement insStmt = conn.prepareStatement(insertSql);
                PreparedStatement markStmt = conn.prepareStatement(markAppliedSql)
            ) {
                delStmt.setInt(1, league.getId());
                delStmt.setInt(2, eventId);
                if (catKey == null) delStmt.setNull(3, Types.VARCHAR);
                else delStmt.setString(3, catKey);
                delStmt.executeUpdate();

                for (int index = 0; index < results.size(); index++) {
                    Driver result = results.get(index);
                    LeagueDriver leagueDriver = league.getDrivers().get(result.getUuid());
                    Integer teamId = leagueDriver != null ? leagueDriver.getTeamId() : null;
                    int points = computePoints(league, catKey, index + 1, results.size());

                    insStmt.setInt(1, league.getId());
                    insStmt.setInt(2, eventId);
                    insStmt.setInt(3, index + 1);
                    insStmt.setString(4, result.getUuid().toString());
                    if (teamId == null) insStmt.setNull(5, Types.INTEGER);
                    else insStmt.setInt(5, teamId);
                    insStmt.setInt(6, points);
                    if (catKey == null) insStmt.setNull(7, Types.VARCHAR);
                    else insStmt.setString(7, catKey);
                    if (pinnedHeatId == null) insStmt.setNull(8, Types.INTEGER);
                    else insStmt.setInt(8, pinnedHeatId);
                    insStmt.addBatch();
                }

                insStmt.executeBatch();
                markStmt.setInt(1, league.getId());
                markStmt.setInt(2, eventId);
                markStmt.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

            recalculateStandings(league);
            return true;
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation(
                "[LeagueDB] Erro ao armazenar resultados da liga: " + e.getMessage()
            );
            return false;
        }
    }

    private int computePoints(League league, String catKey, int position, int driverCount) {
        LeagueCategory cat = catKey != null ? league.getCategory(catKey) : null;
        String systemId = cat != null ? cat.getScoringSystem() : league.getScoringSystem();
        PointsConfig customScale = cat != null ? cat.getCustomScale() : league.getCustomScale();
        if (customScale != null) {
            return customScale.getRacePoints().getOrDefault(position, 0);
        }
        return dev.EfraGroup.formulaRacing.League.scoring.ScoringRegistry
            .get(systemId).pointsForPosition(position, driverCount);
    }

    /**
     * Full recalculation from stored per-event results using StandingsUpdater.
     */
    public void recalculateStandings(League league) throws SQLException {
        String sql =
            "SELECT playerUUID, teamId, points, categoryName, eventId FROM fr_league_event_results " +
            "WHERE leagueId = ?";
        Map<UUID, List<DriverEventResult>> driverResults = new LinkedHashMap<>();
        Map<UUID, Integer> driverTeam = new LinkedHashMap<>();

        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, league.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("playerUUID"));
                    String eventId = String.valueOf(rs.getInt("eventId"));
                    String cat = rs.getString("categoryName");
                    int points = rs.getInt("points");
                    driverResults
                        .computeIfAbsent(uuid, k -> new ArrayList<>())
                        .add(new DriverEventResult(eventId, cat, points));
                    int teamId = rs.getInt("teamId");
                    if (!rs.wasNull()) driverTeam.put(uuid, teamId);
                }
            }
        }

        StandingsUpdater.CalculationResult calc =
            StandingsUpdater.recalculate(league, driverResults, driverTeam);

        writeStandings(league, calc);
    }

    private void writeStandings(League league, StandingsUpdater.CalculationResult calc)
        throws SQLException {
        String clearDriver = "DELETE FROM fr_league_driver_standings WHERE leagueId = ?";
        String clearTeam = "DELETE FROM fr_league_team_standings WHERE leagueId = ?";
        String clearHistory = "DELETE FROM fr_league_point_history WHERE leagueId = ?";
        String clearBreakdown = "DELETE FROM fr_league_standings_breakdown WHERE leagueId = ?";
        String insDriver =
            "INSERT OR REPLACE INTO fr_league_driver_standings " +
            "(leagueId, playerUUID, points, wins, podiums, eventsCount) VALUES (?, ?, ?, 0, 0, " +
            "(SELECT COUNT(DISTINCT eventId) FROM fr_league_event_results WHERE leagueId = ? AND playerUUID = ?))";
        String insTeam =
            "INSERT OR REPLACE INTO fr_league_team_standings " +
            "(leagueId, teamId, points, wins, podiums) VALUES (?, ?, ?, 0, 0)";
        String insHistory =
            "INSERT INTO fr_league_point_history (leagueId, scope, targetId, eventId, pointsDelta, source, timestamp) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement cd = conn.prepareStatement(clearDriver);
            PreparedStatement ct = conn.prepareStatement(clearTeam);
            PreparedStatement ch = conn.prepareStatement(clearHistory);
            PreparedStatement cb = conn.prepareStatement(clearBreakdown);
            PreparedStatement id = conn.prepareStatement(insDriver);
            PreparedStatement it = conn.prepareStatement(insTeam);
            PreparedStatement ih = conn.prepareStatement(insHistory)
        ) {
            cd.setInt(1, league.getId()); cd.executeUpdate();
            ct.setInt(1, league.getId()); ct.executeUpdate();
            ch.setInt(1, league.getId()); ch.executeUpdate();
            cb.setInt(1, league.getId()); cb.executeUpdate();

            for (Map.Entry<UUID, Integer> e : calc.driverPoints.entrySet()) {
                id.setInt(1, league.getId());
                id.setString(2, e.getKey().toString());
                id.setInt(3, e.getValue());
                id.setInt(4, league.getId());
                id.setString(5, e.getKey().toString());
                id.addBatch();
            }
            id.executeBatch();

            for (Map.Entry<Integer, Integer> e : calc.teamPoints.entrySet()) {
                it.setInt(1, league.getId());
                it.setInt(2, e.getKey());
                it.setInt(3, e.getValue());
                it.addBatch();
            }
            it.executeBatch();

            long now = Instant.now().toEpochMilli();
            for (Map.Entry<UUID, List<DriverEventResult>> e : calc.driverHistory.entrySet()) {
                for (DriverEventResult der : e.getValue()) {
                    ih.setInt(1, league.getId());
                    ih.setString(2, "DRIVER");
                    ih.setString(3, e.getKey().toString());
                    ih.setString(4, der.eventId());
                    ih.setInt(5, der.points());
                    ih.setString(6, "RESULT");
                    ih.setLong(7, now);
                    ih.addBatch();
                }
            }
            ih.executeBatch();
        }
    }

    /**
     * Ajusta os pontos de um piloto em uma liga (delta positivo ou negativo).
     * Preserva vitórias/pódios/eventsCount. Registra no point history.
     */
    public void adjustDriverPoints(int leagueId, UUID playerUUID, int delta, String source)
        throws SQLException {
        String upsert =
            "INSERT INTO fr_league_driver_standings (leagueId, playerUUID, points, wins, podiums, eventsCount) " +
            "VALUES (?, ?, 0, 0, 0, 0) " +
            "ON CONFLICT(leagueId, playerUUID) DO UPDATE SET points = MAX(0, points + ?)";
        String history =
            "INSERT INTO fr_league_point_history (leagueId, scope, targetId, eventId, pointsDelta, source, timestamp) " +
            "VALUES (?, 'DRIVER', ?, NULL, ?, ?, ?)";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement up = conn.prepareStatement(upsert);
            PreparedStatement hist = conn.prepareStatement(history)
        ) {
            up.setInt(1, leagueId);
            up.setString(2, playerUUID.toString());
            up.setInt(3, delta);
            up.executeUpdate();

            hist.setInt(1, leagueId);
            hist.setString(2, playerUUID.toString());
            hist.setInt(3, delta);
            hist.setString(4, source);
            hist.setLong(5, Instant.now().toEpochMilli());
            hist.executeUpdate();
        }
    }

    /**
     * Transfere pontos de um piloto para outro na mesma liga.
     */
    public void transferDriverPoints(int leagueId, UUID fromUUID, UUID toUUID, int amount)
        throws SQLException {
        adjustDriverPoints(leagueId, fromUUID, -amount, "TRANSFER_OUT");
        adjustDriverPoints(leagueId, toUUID, amount, "TRANSFER_IN");
    }

    public List<LeagueStanding> loadDriverStandings(int leagueId) {
        List<LeagueStanding> standings = new ArrayList<>();
        String sql =
            "SELECT * FROM fr_league_driver_standings WHERE leagueId = ? ORDER BY points DESC, wins DESC, podiums DESC";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, leagueId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    standings.add(
                        new LeagueStanding(
                            UUID.fromString(rs.getString("playerUUID")),
                            rs.getInt("points"),
                            rs.getInt("wins"),
                            rs.getInt("podiums"),
                            rs.getInt("eventsCount")
                        )
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation(
                "[LeagueDB] Erro ao carregar standings de pilotos: " + e.getMessage()
            );
        }
        return standings;
    }

    public List<LeagueTeamStanding> loadTeamStandings(int leagueId) {
        List<LeagueTeamStanding> standings = new ArrayList<>();
        String sql =
            "SELECT s.teamId, t.name, s.points, s.wins, s.podiums FROM fr_league_team_standings s " +
            "JOIN fr_league_teams t ON t.id = s.teamId WHERE s.leagueId = ? " +
            "ORDER BY s.points DESC, s.wins DESC, s.podiums DESC";
        try (
            Connection conn = databaseManager.getOrConnect();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, leagueId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    standings.add(
                        new LeagueTeamStanding(
                            rs.getInt("teamId"),
                            rs.getString("name"),
                            rs.getInt("points"),
                            rs.getInt("wins"),
                            rs.getInt("podiums")
                        )
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation(
                "[LeagueDB] Erro ao carregar standings de equipes: " + e.getMessage()
            );
        }
        return standings;
    }
}
