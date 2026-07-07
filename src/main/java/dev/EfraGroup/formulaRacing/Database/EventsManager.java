package dev.EfraGroup.formulaRacing.Database;

import dev.EfraGroup.formulaRacing.FileManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EventsManager {
    private final FormulaRacing plugin;
    private final FileManager fileManager;
    private final DatabaseManager db;

    public EventsManager(FormulaRacing plugin, FileManager fileManager, DatabaseManager db) {
        this.plugin = plugin;
        this.fileManager = fileManager;
        this.db = db;
    }

    public Connection getConnection() throws SQLException {
        File dataFolder = this.plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "database.db");

        try {
            if (!dbFile.exists()) {
                dbFile.createNewFile();
            }
        } catch (IOException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error creating SQLite file: " + e.getMessage());
            throw new SQLException("Erro ao criar arquivo SQLite.", e);
        }

        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    public void createEvent(UUID creatorUUID, String name, String trackNameWS) {
        String sql = "INSERT INTO fr_events (creatorUUID, name, trackNameWS, state, creationTime) VALUES (?, ?, ?, 'SETUP', ?)";
        long creationTime = System.currentTimeMillis();

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, 1);
        ) {
            ps.setString(1, creatorUUID.toString());
            ps.setString(2, name);
            ps.setString(3, trackNameWS);
            ps.setLong(4, creationTime);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int var10 = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error creating event: " + e.getMessage());
        }

    }

    public Integer getRoundIdByEventAndNumber(int eventId, int roundNumber) {
        String sql = "SELECT id FROM rounds WHERE eventId = ? AND roundIndex = ? LIMIT 1";

        try {
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql);
            ) {
                stmt.setInt(1, eventId);
                stmt.setInt(2, roundNumber);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Integer var7 = rs.getInt("id");
                        return var7;
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error obtaining roundId: " + e.getMessage());
            return null;
        }
    }

    public boolean deleteRound(int roundId) {
        String checkRoundStateSql = "SELECT state FROM fr_rounds WHERE id = ?";

        try {
            label349: {
                boolean var45;
                try (
                        Connection conn = this.getConnection();
                        PreparedStatement ps = conn.prepareStatement(checkRoundStateSql);
                ) {
                    ps.setInt(1, roundId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Attempt to delete non-existent round: " + roundId);
                            boolean var43 = false;
                            return var43;
                        }

                        String state = rs.getString("state");
                        if (state.equalsIgnoreCase("SETUP")) {
                            break label349;
                        }

                        this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Round " + roundId + " is not in SETUP. Cancelling delete.");
                        var45 = false;
                    }
                }

                return var45;
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error checking state of round " + roundId + ": " + e.getMessage());
            return false;
        }

        String checkHeatsSql = "SELECT state FROM fr_heats WHERE roundId = ?";

        try {
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement ps = conn.prepareStatement(checkHeatsSql);
            ) {
                ps.setInt(1, roundId);

                try (ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        String heatState = rs.getString("state");
                        if (!heatState.equalsIgnoreCase("SETUP")) {
                            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] One or more heats of round " + roundId + " are not in SETUP. Cancelling delete.");
                            boolean var8 = false;
                            return var8;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error checking heats of round " + roundId + ": " + e.getMessage());
            return false;
        }

        String deleteHeatsSql = "DELETE FROM fr_heats WHERE roundId = ?";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(deleteHeatsSql);
        ) {
            ps.setInt(1, roundId);
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error deleting heats of round " + roundId + ": " + e.getMessage());
            return false;
        }

        String deleteRoundSql = "DELETE FROM fr_rounds WHERE id = ?";

        try {
            try (Connection conn = this.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(deleteRoundSql)) {
                    ps.setInt(1, roundId);
                    int affected = ps.executeUpdate();
                    if (affected == 0) {
                        this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Round " + roundId + " was not deleted (didn't exist?).");
                        boolean var9 = false;
                        return var9;
                    }
                }
            }

            return true;
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error deleting round " + roundId + ": " + e.getMessage());
            return false;
        }
    }

    public List<String> getAllEvents() {
        List<String> events = new ArrayList();
        String sql = "SELECT name FROM fr_events";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery();
        ) {
            while(rs.next()) {
                events.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error fetching event list: " + e.getMessage());
        }

        return events;
    }

    public boolean getIfEventExistsByName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            String sql = "SELECT 1 FROM fr_events WHERE name = ? LIMIT 1";

            try {
                boolean var6;
                try (
                        Connection conn = this.getConnection();
                        PreparedStatement ps = conn.prepareStatement(sql);
                ) {
                    ps.setString(1, name);

                    try (ResultSet rs = ps.executeQuery()) {
                        var6 = rs.next();
                    }
                }

                return var6;
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error checking existence of event '" + name + "': " + e.getMessage());
                return false;
            }
        } else {
            return false;
        }
    }

    public void setEventTrack(int eventId, String trackNameWS) {
        String sql = "UPDATE fr_events SET trackNameWS = ? WHERE id = ?";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, trackNameWS);
            ps.setInt(2, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error updating event track (ID: " + eventId + "): " + e.getMessage());
        }

    }

    public synchronized boolean addPlayerToHeat(UUID uuid, int heatId, int position) {
        String shiftSql = "UPDATE fr_drivers SET position = position + 1, startPosition = startPosition + 1 WHERE heatId = ? AND position >= ?";
        String insertSql = "    INSERT INTO fr_drivers (uuid, heatId, position, startPosition)\n    VALUES (?, ?, ?, ?)\n";

        try {
            boolean var21;
            try (Connection conn = this.getConnection()) {
                conn.setAutoCommit(false);

                try (Statement pragma = conn.createStatement()) {
                    pragma.execute("PRAGMA busy_timeout = 5000");
                }

                try (PreparedStatement shift = conn.prepareStatement(shiftSql)) {
                    shift.setInt(1, heatId);
                    shift.setInt(2, position);
                    shift.executeUpdate();
                }

                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    insert.setString(1, uuid.toString());
                    insert.setInt(2, heatId);
                    insert.setInt(3, position);
                    insert.setInt(4, position);
                    insert.executeUpdate();
                }

                conn.commit();
                this.plugin.getDebugManager().logRaceSystem("[FormulaRacing] Player " + String.valueOf(uuid) + " added to heat " + heatId + " at position " + position);
                var21 = true;
            }

            return var21;
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error adding player to heat: " + e.getMessage());
            return false;
        }
    }

    public int getEventIDByName(String name) {
        String sql = "SELECT id FROM fr_events WHERE name = ?";

        try {
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql);
            ) {
                ps.setString(1, name);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int var6 = rs.getInt("id");
                        return var6;
                    } else {
                        return -1;
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error fetching event ID by name: " + e.getMessage());
            return -1;
        }
    }

    public boolean deleteEventByName(String eventName) {
        String sql = "DELETE FROM fr_events WHERE name = ?";

        try {
            boolean var6;
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql);
            ) {
                ps.setString(1, eventName);
                int affectedRows = ps.executeUpdate();
                var6 = affectedRows > 0;
            }

            return var6;
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error deleting event '" + eventName + "': " + e.getMessage());
            return false;
        }
    }

    public int getHeatCountForRound(int roundId) {
        String sql = "SELECT COUNT(*) AS total FROM heats WHERE roundId = ?";

        try {
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql);
            ) {
                stmt.setInt(1, roundId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int var6 = rs.getInt("total");
                        return var6;
                    } else {
                        return 0;
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error getting heat count: " + e.getMessage());
            return 0;
        }
    }

    public Boolean createHeat(int roundId, int heatNumber) {
        String sql = "INSERT INTO fr_heats (roundId, heatNumber, state, totalLaps, totalPitstops) VALUES (?, ?, ?, ?, ?)";

        try {
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql, 1);
            ) {
                ps.setInt(1, roundId);
                ps.setInt(2, heatNumber);
                ps.setString(3, "SETUP");
                ps.setString(4, "5");
                ps.setString(5, "0");
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        Boolean var8 = true;
                        return var8;
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error creating heat: " + e.getMessage());
            return null;
        }
    }

    public void setSelectedEvent(UUID uuid, String eventName) {
        String sql = "UPDATE fr_players SET selectedEvent = ? WHERE uuid = ?";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, eventName);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error setting selectedEvent: " + e.getMessage());
        }

    }

    public String getSelectedEvent(UUID uuid) {
        String sql = "SELECT selectedEvent FROM fr_players WHERE uuid = ?";

        try {
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql);
            ) {
                ps.setString(1, uuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String var6 = rs.getString("selectedEvent");
                        return var6;
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error getting selectedEvent: " + e.getMessage());
            return null;
        }
    }

    public Boolean createRound(int event, String type, String state) {
        int nextIndex = this.getNextRoundIndex(event);
        String sql = "INSERT INTO fr_rounds (eventId, roundIndex, type, state) VALUES (?, ?, ?, ?)";

        try {
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql, 1);
            ) {
                ps.setInt(1, event);
                ps.setInt(2, nextIndex);
                ps.setString(3, type);
                ps.setString(4, state);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        Boolean var10 = true;
                        return var10;
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error creating round: " + e.getMessage());
            return null;
        }
    }

    private int getNextRoundIndex(int event) {
        String sql = "SELECT MAX(roundIndex) AS maxIndex FROM fr_rounds WHERE eventId = ?";

        try {
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql);
            ) {
                ps.setInt(1, event);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int max = rs.getInt("maxIndex");
                        int var7 = max + 1;
                        return var7;
                    } else {
                        return 1;
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error getting next roundIndex: " + e.getMessage());
            return 1;
        }
    }

    public List<Map<String, Object>> getRoundsByEvent(int eventId) {
        List<Map<String, Object>> rounds = new ArrayList();
        String sql = "SELECT * FROM fr_rounds WHERE eventId=? ORDER BY roundIndex ASC";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, eventId);

            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    Map<String, Object> round = new HashMap();
                    round.put("id", rs.getInt("id"));
                    round.put("roundIndex", rs.getInt("roundIndex"));
                    round.put("type", rs.getString("type"));
                    round.put("state", rs.getString("state"));
                    rounds.add(round);
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error fetching event rounds: " + e.getMessage());
        }

        return rounds;
    }

    public boolean finishRound(int round) {
        String sql = "UPDATE fr_rounds SET state=? WHERE id=?";

        try {
            boolean var6;
            try (Connection conn = this.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, "FINISHED");
                    ps.setInt(2, round);
                    int affectedRows = ps.executeUpdate();
                    if (affectedRows <= 0) {
                        return false;
                    }

                    var6 = true;
                }
            }

            return var6;
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error finishing round: " + e.getMessage());
            return false;
        }
    }

    public int getNextHeatNumber(int roundId) {
        String sql = "SELECT MAX(heatNumber) AS maxHeat FROM fr_heats WHERE roundId=?";

        try {
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql);
            ) {
                ps.setInt(1, roundId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int var6 = rs.getInt("maxHeat") + 1;
                        return var6;
                    } else {
                        return 1;
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error getting next heat number: " + e.getMessage());
            return 1;
        }
    }

    public Set<UUID> getSubscribers(String eventName) {
        Set<UUID> subscribers = new HashSet();
        String sql = "SELECT s.playerUUID\nFROM fr_event_subscribers s\nJOIN fr_events e ON s.eventId = e.id\nWHERE e.name = ?\n";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, eventName);

            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    String uuidStr = rs.getString("playerUUID");
                    subscribers.add(UUID.fromString(uuidStr));
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error fetching subscribers of event " + eventName + ": " + e.getMessage());
        }

        return subscribers;
    }

    public boolean setEventOpenSign(String eventName, boolean openSign) {
        String sql = "UPDATE fr_events SET openSign = ? WHERE name = ?";

        try {
            boolean var7;
            try (
                    Connection conn = this.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql);
            ) {
                stmt.setInt(1, openSign ? 1 : 0);
                stmt.setString(2, eventName);
                int rowsUpdated = stmt.executeUpdate();
                var7 = rowsUpdated > 0;
            }

            return var7;
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error setting openSign: " + e.getMessage());
            return false;
        }
    }

    public boolean isEventOpenSign(String eventName) {
        String sql = "SELECT openSign FROM fr_events WHERE name = ?";

        try {
            boolean var6;
            try (Connection conn = this.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, eventName);
                    ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) {
                        return false;
                    }

                    var6 = rs.getInt("openSign") == 1;
                }
            }

            return var6;
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error checking openSign: " + e.getMessage());
            return false;
        }
    }

    public Set<UUID> getReserves(String eventName) {
        Set<UUID> set = new HashSet();

        try (
                Connection conn = this.getConnection();
                PreparedStatement stmt = conn.prepareStatement("SELECT playerUUID FROM fr_event_reserves WHERE eventId = ?");
        ) {
            int eventId = this.getEventIDByName(eventName);
            stmt.setInt(1, eventId);
            ResultSet rs = stmt.executeQuery();

            while(rs.next()) {
                set.add(UUID.fromString(rs.getString("playerUUID")));
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error fetching reserves: " + e.getMessage());
        }

        return set;
    }

    public void addSubscriber(int eventId, UUID playerUUID) {
        String sql = "INSERT OR REPLACE INTO fr_event_subscribers (eventId, playerUUID) VALUES (?, ?)";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error adding subscriber: " + e.getMessage());
        }

    }

    public void removeSubscriber(int eventId, UUID playerUUID) {
        String sql = "DELETE FROM fr_event_subscribers WHERE eventId = ? AND playerUUID = ?";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error removing subscriber: " + e.getMessage());
        }

    }

    public void addReserve(int eventId, UUID playerUUID) {
        String sql = "INSERT OR IGNORE INTO fr_event_reserves (eventId, playerUUID) VALUES (?, ?)";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error adding reserve: " + e.getMessage());
        }

    }

    public void removeReserve(int eventId, UUID playerUUID) {
        String sql = "DELETE FROM fr_event_reserves WHERE eventId = ? AND playerUUID = ?";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error removing reserve: " + e.getMessage());
        }

    }

    public void addSpectator(int eventId, UUID playerUUID) {
        String sql = "INSERT INTO fr_event_spectators (event_id, player_uuid) VALUES (?, ?) ON DUPLICATE KEY UPDATE player_uuid = player_uuid";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error adding spectator: " + e.getMessage());
        }

    }

    public void removeSpectator(int eventId, UUID playerUUID) {
        String sql = "DELETE FROM fr_event_spectators WHERE event_id = ? AND player_uuid = ?";

        try (
                Connection conn = this.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[FormulaRacing] Error removing spectator: " + e.getMessage());
        }

    }
}
