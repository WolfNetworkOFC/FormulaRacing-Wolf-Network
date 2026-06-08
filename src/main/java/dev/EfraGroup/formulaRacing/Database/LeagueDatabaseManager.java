package dev.EfraGroup.formulaRacing.Database;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.League.League;
import dev.EfraGroup.formulaRacing.League.LeagueDriver;
import dev.EfraGroup.formulaRacing.League.LeagueStanding;
import dev.EfraGroup.formulaRacing.League.LeagueStatus;
import dev.EfraGroup.formulaRacing.League.LeagueTeam;
import dev.EfraGroup.formulaRacing.League.LeagueTeamStanding;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LeagueDatabaseManager {

    private static final int[] DEFAULT_POINTS = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};

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
                loadTeams(conn, league);
                loadDrivers(conn, league);
                leagues.add(league);
            }
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation(
                "[LeagueDB] Erro ao carregar ligas: " + e.getMessage()
            );
        }

        return leagues;
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
                stmt.setNull(3, java.sql.Types.INTEGER);
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
                "[LeagueDB] Erro ao verificar vínculo do evento: " + e.getMessage()
            );
            return false;
        }
    }

    public boolean applyEventResults(League league, int eventId, List<Driver> results) {
        if (league == null || results == null || results.isEmpty()) {
            return false;
        }

        String checkSql =
            "SELECT pointsApplied FROM fr_league_events WHERE leagueId = ? AND eventId = ?";
        String upsertDriverSql =
            "INSERT INTO fr_league_driver_standings (leagueId, playerUUID, points, wins, podiums, eventsCount) VALUES (?, ?, ?, ?, ?, 1) " +
            "ON CONFLICT(leagueId, playerUUID) DO UPDATE SET points = points + excluded.points, wins = wins + excluded.wins, podiums = podiums + excluded.podiums, eventsCount = eventsCount + 1";
        String upsertTeamSql =
            "INSERT INTO fr_league_team_standings (leagueId, teamId, points, wins, podiums) VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT(leagueId, teamId) DO UPDATE SET points = points + excluded.points, wins = wins + excluded.wins, podiums = podiums + excluded.podiums";
        String markAppliedSql =
            "UPDATE fr_league_events SET pointsApplied = 1 WHERE leagueId = ? AND eventId = ?";

        try {
            Connection conn = databaseManager.getOrConnect();
            conn.setAutoCommit(false);
            try (
                PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                PreparedStatement driverStmt = conn.prepareStatement(upsertDriverSql);
                PreparedStatement teamStmt = conn.prepareStatement(upsertTeamSql);
                PreparedStatement markStmt = conn.prepareStatement(markAppliedSql)
            ) {
                checkStmt.setInt(1, league.getId());
                checkStmt.setInt(2, eventId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    if (rs.getInt("pointsApplied") == 1) {
                        conn.rollback();
                        return false;
                    }
                }

                for (int index = 0; index < results.size(); index++) {
                    Driver result = results.get(index);
                    LeagueDriver leagueDriver = league.getDrivers().get(result.getUuid());
                    if (leagueDriver == null) {
                        continue;
                    }

                    int points = index < DEFAULT_POINTS.length ? DEFAULT_POINTS[index] : 0;
                    int wins = index == 0 ? 1 : 0;
                    int podiums = index < 3 ? 1 : 0;

                    driverStmt.setInt(1, league.getId());
                    driverStmt.setString(2, result.getUuid().toString());
                    driverStmt.setInt(3, points);
                    driverStmt.setInt(4, wins);
                    driverStmt.setInt(5, podiums);
                    driverStmt.addBatch();

                    if (leagueDriver.getTeamId() != null) {
                        teamStmt.setInt(1, league.getId());
                        teamStmt.setInt(2, leagueDriver.getTeamId());
                        teamStmt.setInt(3, points);
                        teamStmt.setInt(4, wins);
                        teamStmt.setInt(5, podiums);
                        teamStmt.addBatch();
                    }
                }

                driverStmt.executeBatch();
                teamStmt.executeBatch();
                markStmt.setInt(1, league.getId());
                markStmt.setInt(2, eventId);
                markStmt.executeUpdate();
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getDebugManager().logDatabaseOperation(
                "[LeagueDB] Erro ao aplicar resultado da liga: " + e.getMessage()
            );
            return false;
        }
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
