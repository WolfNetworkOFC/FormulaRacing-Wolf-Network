//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Database;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.EventState;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Participant.Subscriber;
import dev.EfraGroup.formulaRacing.Round.PracticeRound;
import dev.EfraGroup.formulaRacing.Round.QualificationRound;
import dev.EfraGroup.formulaRacing.Round.RaceRound;
import dev.EfraGroup.formulaRacing.Round.RoundState;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class EventsDatabaseManager {
    private final DatabaseManager databaseManager;
    private final FormulaRacing plugin;

    public EventsDatabaseManager(DatabaseManager databaseManager, FormulaRacing plugin) {
        this.databaseManager = databaseManager;
        this.plugin = plugin;
    }

    private void executeAsync(String sql, String operationName, Consumer<PreparedStatement> binder) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();
                if (conn == null) {
                    return;
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    binder.accept(stmt);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro assíncrono em " + operationName + ": " + e.getMessage());
            }

        });
    }

    public CompletableFuture<Integer> createEvent(UUID creatorUUID, String name, String trackNameWS) {
        CompletableFuture<Integer> future = new CompletableFuture();
        String sql = "INSERT INTO fr_events (creatorUUID, name, trackNameWS, creationTime, state, openSign) VALUES (?, ?, ?, ?, ?, ?)";
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();

                try (PreparedStatement stmt = conn.prepareStatement(sql, 1)) {
                    stmt.setString(1, creatorUUID.toString());
                    stmt.setString(2, name);
                    stmt.setString(3, trackNameWS);
                    stmt.setLong(4, Instant.now().getEpochSecond());
                    stmt.setString(5, EventState.SETUP.name());
                    stmt.setInt(6, 1);
                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int eventId = rs.getInt(1);
                            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Evento criado (Assíncrono): ID=" + eventId + ", Nome=" + name);
                            future.complete(eventId);
                        } else {
                            future.complete(-1);
                        }
                    }
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao criar evento (Assíncrono): " + e.getMessage());
                future.complete(-1);
            }

        });
        return future;
    }

    public CompletableFuture<Integer> createRound(int eventId, int roundIndex, RoundType type) {
        CompletableFuture<Integer> future = new CompletableFuture();
        String sql = "INSERT INTO fr_rounds (eventId, roundIndex, type, state) VALUES (?, ?, ?, ?)";
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();

                try (PreparedStatement stmt = conn.prepareStatement(sql, 1)) {
                    stmt.setInt(1, eventId);
                    stmt.setInt(2, roundIndex);
                    stmt.setString(3, type.name());
                    stmt.setString(4, RoundState.SETUP.name());
                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int roundId = rs.getInt(1);
                            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Round criado (Assíncrono): ID=" + roundId + ", EventID=" + eventId);
                            future.complete(roundId);
                        } else {
                            future.complete(-1);
                        }
                    }
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao criar round (Assíncrono): " + e.getMessage());
                future.complete(-1);
            }

        });
        return future;
    }

    public CompletableFuture<Integer> createHeat(int roundId, int heatNumber, int totalLaps, int totalPitstops, int timeLimit, int startDelay, int maxDrivers, boolean lonely, boolean canReset, boolean lapReset, boolean drs, boolean driverswap, String colisao, double drsdowntime, double drsdownpower, boolean reversegrid, double ghostingdelta, boolean pushtopass, double pushtopasspower, boolean realistc) {
        CompletableFuture<Integer> future = new CompletableFuture();
        String sql = "INSERT INTO fr_heats (roundId, heatNumber, state, totalLaps, totalPitstops, timeLimit, startDelay, maxDrivers, lonely, canReset, lapReset, drs, driverswap, colisao, drsdowntime, drsdownpower, reversegrid, ghostingdelta, pushtopass, pushtopasspower, realistc) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();

                try (PreparedStatement stmt = conn.prepareStatement(sql, 1)) {
                    stmt.setInt(1, roundId);
                    stmt.setInt(2, heatNumber);
                    stmt.setString(3, HeatState.SETUP.name());
                    stmt.setInt(4, totalLaps);
                    stmt.setInt(5, totalPitstops);
                    stmt.setInt(6, timeLimit);
                    stmt.setInt(7, startDelay);
                    stmt.setInt(8, maxDrivers);
                    stmt.setInt(9, lonely ? 1 : 0);
                    stmt.setInt(10, canReset ? 1 : 0);
                    stmt.setInt(11, lapReset ? 1 : 0);
                    stmt.setInt(12, drs ? 1 : 0);
                    stmt.setInt(13, driverswap ? 1 : 0);
                    stmt.setString(14, colisao);
                    stmt.setDouble(15, drsdowntime);
                    stmt.setDouble(16, drsdownpower);
                    stmt.setInt(17, reversegrid ? 1 : 0);
                    stmt.setDouble(18, ghostingdelta);
                    stmt.setInt(19, pushtopass ? 1 : 0);
                    stmt.setDouble(20, pushtopasspower);
                    stmt.setInt(21, realistc ? 1 : 0);
                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int heatId = rs.getInt(1);
                            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Heat criado (Assíncrono): ID=" + heatId + ", RoundID=" + roundId);
                            future.complete(heatId);
                        } else {
                            future.complete(-1);
                        }
                    }
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao criar heat (Assíncrono): " + e.getMessage());
                future.complete(-1);
            }

        });
        return future;
    }

    public void updateEventState(int eventId, EventState state) {
        String sql = "UPDATE fr_events SET state = ? WHERE id = ?";
        this.executeAsync(sql, "updateEventState", (stmt) -> {
            try {
                stmt.setString(1, state.name());
                stmt.setInt(2, eventId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void addSignup(int eventId, UUID playerUUID, String type) {
        String sql = "INSERT INTO fr_event_signups (eventId, uuid, type, subscriptionTime, confirmed) VALUES (?, ?, ?, ?, 0)";
        this.executeAsync(sql, "addSignup", (stmt) -> {
            try {
                stmt.setInt(1, eventId);
                stmt.setString(2, playerUUID.toString());
                stmt.setString(3, type);
                stmt.setLong(4, System.currentTimeMillis());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void removeSignup(int eventId, UUID playerUUID) {
        String sql = "DELETE FROM fr_event_signups WHERE eventId = ? AND uuid = ?";
        this.executeAsync(sql, "removeSignup", (stmt) -> {
            try {
                stmt.setInt(1, eventId);
                stmt.setString(2, playerUUID.toString());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateSignupType(int eventId, UUID playerUUID, String newType) {
        String sql = "UPDATE fr_event_signups SET type = ? WHERE eventId = ? AND uuid = ?";
        this.executeAsync(sql, "updateSignupType", (stmt) -> {
            try {
                stmt.setString(1, newType);
                stmt.setInt(2, eventId);
                stmt.setString(3, playerUUID.toString());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Map<String, Object>> loadSignupsForEvent(int eventId) {
        String sql = "SELECT uuid, type, subscriptionTime, confirmed FROM fr_event_signups WHERE eventId = ?";
        List<Map<String, Object>> signups = new ArrayList<>();
        try {
            Connection conn = this.databaseManager.getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, eventId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("uuid", UUID.fromString(rs.getString("uuid")));
                        data.put("type", rs.getString("type"));
                        data.put("subscriptionTime", rs.getLong("subscriptionTime"));
                        data.put("confirmed", rs.getInt("confirmed") == 1);
                        signups.add(data);
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao carregar signups: " + e.getMessage());
        }
        return signups;
    }

    public void updateEventOpenSign(int eventId, boolean openSign) {
        String sql = "UPDATE fr_events SET openSign = ? WHERE id = ?";
        this.executeAsync(sql, "updateEventOpenSign", (stmt) -> {
            try {
                stmt.setInt(1, openSign ? 1 : 0);
                stmt.setInt(2, eventId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateEventTrack(int eventId, String trackNameWS) {
        String sql = "UPDATE fr_events SET trackNameWS = ? WHERE id = ?";
        this.executeAsync(sql, "updateEventTrack", (stmt) -> {
            try {
                stmt.setString(1, trackNameWS);
                stmt.setInt(2, eventId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void heatSet(int heatId, String column, String value) {
        if (!column.matches("timeLimit|collisionMode|startDelay|totalLaps|totalPitstops|maxDrivers|lonely")) {
            this.plugin.getDebugManager().logDatabaseOperation("Tentativa de update em coluna inválida ou não permitida em heatSet: " + column);
        } else {
            String sql = "UPDATE fr_heats SET " + column + " = ? WHERE id = ?";
            this.executeAsync(sql, "heatSet_" + column, (stmt) -> {
                try {
                    stmt.setString(1, value);
                    stmt.setInt(2, heatId);
                } catch (SQLException e) {
                    this.plugin.getDebugManager().logDatabaseOperation("Erro ao atualizar heat (" + column + "): " + e.getMessage());
                }

            });
        }
    }

    public Events loadEvent(int eventId) {
        Map<Integer, Events> eventMap = new HashMap<>();
        Map<Integer, Rounds> roundMap = new HashMap<>();
        List<Map<String, Object>> roundsData = new ArrayList();

        try {
            String eventSql = "SELECT * FROM fr_events WHERE id = ?";
            Connection conn = this.databaseManager.getOrConnect();

            try (PreparedStatement stmt = conn.prepareStatement(eventSql)) {
                stmt.setInt(1, eventId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        Object event = null;
                        return (Events)event;
                    }

                    Events event = this.buildEventFromResultSetSimple(rs);
                    eventMap.put(event.getId(), event);

                    List<Map<String, Object>> signups = this.loadSignupsForEvent(eventId);
                    for (Map<String, Object> signup : signups) {
                        UUID playerUUID = (UUID) signup.get("uuid");
                        String type = (String) signup.get("type");
                        Subscriber sub = new Subscriber(playerUUID, eventId);
                        sub.setConfirmed((Boolean) signup.get("confirmed"));
                        if ("RESERVE".equals(type)) {
                            event.getReserves().put(playerUUID, sub);
                        } else {
                            event.getSubscribers().put(playerUUID, sub);
                        }
                    }
                }
            }

            Events event = (Events)eventMap.get(eventId);
            String roundSql = "SELECT * FROM fr_rounds WHERE eventId = ? AND (isRemoved = 0 OR isRemoved IS NULL) ORDER BY roundIndex";

            try (PreparedStatement stmt = conn.prepareStatement(roundSql)) {
                stmt.setInt(1, eventId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while(rs.next()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", rs.getInt("id"));
                        data.put("eventId", rs.getInt("eventId"));
                        data.put("roundIndex", rs.getInt("roundIndex"));
                        data.put("type", rs.getString("type"));
                        data.put("state", rs.getString("state"));
                        roundsData.add(data);
                    }
                }
            }

            for(Map<String, Object> data : roundsData) {
                int roundId = (Integer)data.get("id");
                int roundIndex = (Integer)data.get("roundIndex");
                RoundType roundType = RoundType.valueOf((String)data.get("type"));
                Rounds round = this.instantiateRound(this.plugin, roundId, (Events)null, roundIndex, roundType);
                round.setEventId((Integer)data.get("eventId"));
                round.setState(RoundState.valueOf((String)data.get("state")));
                round.setEvent(event);
                roundMap.put(roundId, round);
                event.getEventSchedule().getRounds().put(roundIndex, round);
            }

            if (!roundMap.isEmpty()) {
                int var10001 = roundMap.size();
                String heatSql = "SELECT * FROM fr_heats WHERE roundId IN (" + String.join(",", Collections.nCopies(var10001, "?")) + ") AND (isRemoved = 0 OR isRemoved IS NULL) ORDER BY heatNumber";

                try (PreparedStatement stmt = conn.prepareStatement(heatSql)) {
                    int idx = 1;

                    for(Integer roundId : roundMap.keySet()) {
                        stmt.setInt(idx++, roundId);
                    }

                    try (ResultSet rs = stmt.executeQuery()) {
                        while(rs.next()) {
                            int heatId = rs.getInt("id");
                            int hroundId = rs.getInt("roundId");
                            int heatNumber = rs.getInt("heatNumber");
                            Heats heat = new Heats(this.plugin, heatId, (Rounds)null, heatNumber);
                            heat.setRoundId(hroundId);
                            heat.setHeatStateForLoad(HeatState.valueOf(rs.getString("state")));
                            long startTimeValue = rs.getLong("startTime");
                            long endTimeValue = rs.getLong("endTime");
                            if (startTimeValue > 0L) {
                                heat.setStartTime(Instant.ofEpochSecond(startTimeValue));
                            }
                            if (endTimeValue > 0L) {
                                heat.setEndTime(Instant.ofEpochSecond(endTimeValue));
                            }
                            String fastestLapUUID = rs.getString("fastestLapUUID");
                            if (fastestLapUUID != null) {
                                heat.setFastestLapUUID(UUID.fromString(fastestLapUUID));
                            }
                            int totalLaps = rs.getInt("totalLaps");
                            heat.setTotalLaps(rs.wasNull() ? null : totalLaps);
                            int totalPits = rs.getInt("totalPitstops");
                            heat.setTotalPits(rs.wasNull() ? null : totalPits);
                            int timeLimit = rs.getInt("timeLimit");
                            heat.setTimeLimit(rs.wasNull() ? null : timeLimit);
                            heat.setStartDelay(rs.getInt("startDelay"));
                            int maxDrivers = rs.getInt("maxDrivers");
                            heat.setMaxDrivers(rs.wasNull() ? null : maxDrivers);
                            heat.setLonely(rs.getInt("lonely") == 1);
                            heat.setCanReset(rs.getInt("canReset") == 1);

                            try { heat.setCollisionMode(CollisionMode.valueOf(rs.getString("colisao"))); } catch (SQLException ignored) { heat.setCollisionMode(CollisionMode.DISABLED); }
                            try { heat.setDrsEnabled(rs.getInt("drs") == 1); } catch (SQLException ignored) {}
                            try { heat.setDriverSwap(rs.getInt("driverswap") == 1); } catch (SQLException ignored) {}
                            try { heat.setDrsdowntime(rs.getDouble("drsdowntime")); } catch (SQLException ignored) {}
                            try { heat.setDrsdownpower(rs.getDouble("drsdownpower")); } catch (SQLException ignored) {}
                            try { heat.setreversegrid(rs.getInt("reversegrid") == 1); } catch (SQLException ignored) {}
                            try { heat.setDeltaghosting((int) rs.getDouble("ghostingdelta")); } catch (SQLException ignored) {}
                            try { heat.setPushtopass(rs.getInt("pushtopass") == 1); } catch (SQLException ignored) {}
                            try { heat.setpushtopasspower(rs.getDouble("pushtopasspower")); } catch (SQLException ignored) {}
                            try { heat.setrealistc(rs.getInt("realistc") == 1); } catch (SQLException ignored) {}

                            Rounds round = (Rounds)roundMap.get(hroundId);
                            if (round != null) {
                                heat.setRound(round);
                                heat.setTrackNameWS(event.getTrackNameWS());
                                round.getHeats().put(heatNumber, heat);
                                this.loadDriversForHeat(heat);
                            }
                        }
                    }
                }
            }

            return event;
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao carregar evento: " + e.getMessage());
            return null;
        }
    }

    public Events loadEventByName(String name) {
        String sql = "SELECT id FROM fr_events WHERE name = ?";

        try {
            Connection conn = this.databaseManager.getOrConnect();

            Events var7;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }

                    int eventId = rs.getInt("id");
                    var7 = this.loadEvent(eventId);
                }
            }

            return var7;
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao carregar evento por nome: " + e.getMessage());
            return null;
        }
    }

    public List<Events> loadActiveEvents() {
        List<Events> events = new ArrayList();
        Map<Integer, Events> eventMap = new HashMap<>();
        Map<Integer, Rounds> roundMap = new HashMap<>();
        List<Map<String, Object>> eventData = new ArrayList();
        List<Map<String, Object>> roundData = new ArrayList();

        try {
            String eventSql = "SELECT * FROM fr_events WHERE state IN (?, ?) AND (isRemoved = 0 OR isRemoved IS NULL)";
            Connection conn = this.databaseManager.getOrConnect();

            try (PreparedStatement stmt = conn.prepareStatement(eventSql)) {
                stmt.setString(1, EventState.SETUP.name());
                stmt.setString(2, EventState.RUNNING.name());

                try (ResultSet rs = stmt.executeQuery()) {
                    while(rs.next()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", rs.getInt("id"));
                        data.put("creatorUUID", rs.getString("creatorUUID"));
                        data.put("name", rs.getString("name"));
                        data.put("trackNameWS", rs.getString("trackNameWS"));
                        data.put("state", rs.getString("state"));
                        data.put("openSign", rs.getInt("openSign"));
                        data.put("creationTime", rs.getLong("creationTime"));
                        eventData.add(data);
                    }
                }
            }

            if (eventData.isEmpty()) {
                return events;
            }

            for(Map<String, Object> data : eventData) {
                int eventId = (Integer)data.get("id");
                UUID creatorUUID = UUID.fromString((String)data.get("creatorUUID"));
                String displayName = (String)data.get("name");
                Events event = new Events(this.plugin, this.plugin.getRaceEventManager(), eventId, creatorUUID, displayName);
                event.setUuid(UUID.randomUUID());
                event.setTrackNameWS((String)data.get("trackNameWS"));
                event.setState(EventState.valueOf((String)data.get("state")));
                event.setOpenSign((Integer)data.get("openSign") == 1);
                long creationTimeSec = (Long)data.get("creationTime");
                if (creationTimeSec > 0L) {
                    event.setCreationTime(creationTimeSec * 1000L);
                }

                events.add(event);
                eventMap.put(eventId, event);

                List<Map<String, Object>> signups = this.loadSignupsForEvent(eventId);
                for (Map<String, Object> signup : signups) {
                    UUID playerUUID = (UUID) signup.get("uuid");
                    String type = (String) signup.get("type");
                    Subscriber sub = new Subscriber(playerUUID, eventId);
                    sub.setConfirmed((Boolean) signup.get("confirmed"));
                    if ("RESERVE".equals(type)) {
                        event.getReserves().put(playerUUID, sub);
                    } else {
                        event.getSubscribers().put(playerUUID, sub);
                    }
                }
            }

            int var10001 = events.size();
            String roundSql = "SELECT * FROM fr_rounds WHERE eventId IN (" + String.join(",", Collections.nCopies(var10001, "?")) + ") AND (isRemoved = 0 OR isRemoved IS NULL) ORDER BY roundIndex";

            try (PreparedStatement stmt = conn.prepareStatement(roundSql)) {
                int idx = 1;

                for(Events event : events) {
                    stmt.setInt(idx++, event.getId());
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while(rs.next()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", rs.getInt("id"));
                        data.put("eventId", rs.getInt("eventId"));
                        data.put("roundIndex", rs.getInt("roundIndex"));
                        data.put("type", rs.getString("type"));
                        data.put("state", rs.getString("state"));
                        roundData.add(data);
                    }
                }
            }

            for(Map<String, Object> data : roundData) {
                int roundId = (Integer)data.get("id");
                int eventId = (Integer)data.get("eventId");
                int roundIndex = (Integer)data.get("roundIndex");
                RoundType roundType = RoundType.valueOf((String)data.get("type"));
                Rounds round = this.instantiateRound(this.plugin, roundId, (Events)null, roundIndex, roundType);
                round.setEventId(eventId);
                round.setState(RoundState.valueOf((String)data.get("state")));
                roundMap.put(roundId, round);
                Events event = (Events)eventMap.get(eventId);
                if (event != null) {
                    round.setEvent(event);
                    event.getEventSchedule().getRounds().put(roundIndex, round);
                }
            }

            if (!roundMap.isEmpty()) {
                var10001 = roundMap.size();
                String heatSql = "SELECT * FROM fr_heats WHERE roundId IN (" + String.join(",", Collections.nCopies(var10001, "?")) + ") AND (isRemoved = 0 OR isRemoved IS NULL) ORDER BY heatNumber";

                try (PreparedStatement stmt = conn.prepareStatement(heatSql)) {
                    int idx = 1;

                    for(Integer roundId : roundMap.keySet()) {
                        stmt.setInt(idx++, roundId);
                    }

                    try (ResultSet rs = stmt.executeQuery()) {
                        while(rs.next()) {
                            int heatId = rs.getInt("id");
                            int hroundId = rs.getInt("roundId");
                            int heatNumber = rs.getInt("heatNumber");
                            Heats heat = new Heats(this.plugin, heatId, (Rounds)null, heatNumber);
                            heat.setRoundId(hroundId);
                            heat.setHeatStateForLoad(HeatState.valueOf(rs.getString("state")));
                            long startTimeValue = rs.getLong("startTime");
                            long endTimeValue = rs.getLong("endTime");
                            if (startTimeValue > 0L) {
                                heat.setStartTime(Instant.ofEpochSecond(startTimeValue));
                            }
                            if (endTimeValue > 0L) {
                                heat.setEndTime(Instant.ofEpochSecond(endTimeValue));
                            }
                            String fastestLapUUID = rs.getString("fastestLapUUID");
                            if (fastestLapUUID != null) {
                                heat.setFastestLapUUID(UUID.fromString(fastestLapUUID));
                            }
                            int totalLaps = rs.getInt("totalLaps");
                            heat.setTotalLaps(rs.wasNull() ? null : totalLaps);
                            int totalPits = rs.getInt("totalPitstops");
                            heat.setTotalPits(rs.wasNull() ? null : totalPits);
                            int timeLimit = rs.getInt("timeLimit");
                            heat.setTimeLimit(rs.wasNull() ? null : timeLimit);
                            heat.setStartDelay(rs.getInt("startDelay"));
                            int maxDrivers = rs.getInt("maxDrivers");
                            heat.setMaxDrivers(rs.wasNull() ? null : maxDrivers);
                            heat.setLonely(rs.getInt("lonely") == 1);
                            heat.setCanReset(rs.getInt("canReset") == 1);

                            try { heat.setCollisionMode(CollisionMode.valueOf(rs.getString("colisao"))); } catch (SQLException ignored) { heat.setCollisionMode(CollisionMode.DISABLED); }
                            try { heat.setDrsEnabled(rs.getInt("drs") == 1); } catch (SQLException ignored) {}
                            try { heat.setDriverSwap(rs.getInt("driverswap") == 1); } catch (SQLException ignored) {}
                            try { heat.setDrsdowntime(rs.getDouble("drsdowntime")); } catch (SQLException ignored) {}
                            try { heat.setDrsdownpower(rs.getDouble("drsdownpower")); } catch (SQLException ignored) {}
                            try { heat.setreversegrid(rs.getInt("reversegrid") == 1); } catch (SQLException ignored) {}
                            try { heat.setDeltaghosting((int) rs.getDouble("ghostingdelta")); } catch (SQLException ignored) {}
                            try { heat.setPushtopass(rs.getInt("pushtopass") == 1); } catch (SQLException ignored) {}
                            try { heat.setpushtopasspower(rs.getDouble("pushtopasspower")); } catch (SQLException ignored) {}
                            try { heat.setrealistc(rs.getInt("realistc") == 1); } catch (SQLException ignored) {}

                            Rounds round = (Rounds)roundMap.get(hroundId);
                            if (round != null) {
                                heat.setRound(round);
                                heat.setTrackNameWS(round.getEvent().getTrackNameWS());
                                round.getHeats().put(heatNumber, heat);
                                this.loadDriversForHeat(heat);
                            }
                        }
                    }
                }
            }

            for(Events event : events) {
                event.getEventSchedule().setCurrentRoundAutomatically();
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao carregar eventos ativos: " + e.getMessage());
        }

        return events;
    }

    private Events buildEventFromResultSetSimple(ResultSet rs) throws SQLException {
        int eventId = rs.getInt("id");
        String creatorUUIDStr = rs.getString("creatorUUID");
        String displayName = rs.getString("name");
        String trackNameWS = rs.getString("trackNameWS");
        String stateStr = rs.getString("state");
        int openSignInt = rs.getInt("openSign");
        UUID creatorUUID = UUID.fromString(creatorUUIDStr);
        Events event = new Events(this.plugin, this.plugin.getRaceEventManager(), eventId, creatorUUID, displayName);
        event.setUuid(UUID.randomUUID());
        event.setTrackNameWS(trackNameWS);
        event.setState(EventState.valueOf(stateStr));
        event.setOpenSign(openSignInt == 1);
        long creationTimeSec = rs.getLong("creationTime");
        if (creationTimeSec > 0L) {
            event.setCreationTime(creationTimeSec * 1000L);
        }

        return event;
    }

    private void loadDriversForHeat(Heats heat) throws SQLException {
        String sql = "SELECT * FROM fr_drivers WHERE heatId = ? ORDER BY startPosition";
        Connection conn = this.databaseManager.getOrConnect();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, heat.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    int driverId = rs.getInt("id");
                    UUID playerUUID = UUID.fromString(rs.getString("uuid"));
                    int startPosition = rs.getInt("startPosition");
                    Driver driver = new Driver(playerUUID, heat.getId(), startPosition);
                    driver.setId(driverId);
                    heat.addDriverDirect(driver);
                }
            }
        }

    }

    public void deleteEvent(int eventId) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();
                conn.setAutoCommit(false);

                try {
                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM fr_laps WHERE heatId IN (SELECT id FROM fr_heats WHERE roundId IN (SELECT id FROM fr_rounds WHERE eventId = ?))");

                    try {
                        stmt.setInt(1, eventId);
                        stmt.executeUpdate();
                    } catch (Throwable var29) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var24) {
                                var29.addSuppressed(var24);
                            }
                        }

                        throw var29;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    stmt = conn.prepareStatement("DELETE FROM fr_drivers WHERE heatId IN (SELECT id FROM fr_heats WHERE roundId IN (SELECT id FROM fr_rounds WHERE eventId = ?))");

                    try {
                        stmt.setInt(1, eventId);
                        stmt.executeUpdate();
                    } catch (Throwable var28) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var23) {
                                var28.addSuppressed(var23);
                            }
                        }

                        throw var28;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    stmt = conn.prepareStatement("DELETE FROM fr_heats WHERE roundId IN (SELECT id FROM fr_rounds WHERE eventId = ?)");

                    try {
                        stmt.setInt(1, eventId);
                        stmt.executeUpdate();
                    } catch (Throwable var27) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var22) {
                                var27.addSuppressed(var22);
                            }
                        }

                        throw var27;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    stmt = conn.prepareStatement("DELETE FROM fr_rounds WHERE eventId = ?");

                    try {
                        stmt.setInt(1, eventId);
                        stmt.executeUpdate();
                    } catch (Throwable var26) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var21) {
                                var26.addSuppressed(var21);
                            }
                        }

                        throw var26;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    stmt = conn.prepareStatement("DELETE FROM fr_events WHERE id = ?");

                    try {
                        stmt.setInt(1, eventId);
                        stmt.executeUpdate();
                    } catch (Throwable var25) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var20) {
                                var25.addSuppressed(var20);
                            }
                        }

                        throw var25;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    conn.commit();
                    this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Evento deletado (Assíncrono): ID=" + eventId);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro assíncrono ao deletar evento: " + e.getMessage());
            }

        });
    }

    public void deleteRound(int roundId) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();
                conn.setAutoCommit(false);

                try {
                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM fr_laps WHERE heatId IN (SELECT id FROM fr_heats WHERE roundId = ?)");

                    try {
                        stmt.setInt(1, roundId);
                        stmt.executeUpdate();
                    } catch (Throwable var25) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var21) {
                                var25.addSuppressed(var21);
                            }
                        }

                        throw var25;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    stmt = conn.prepareStatement("DELETE FROM fr_drivers WHERE heatId IN (SELECT id FROM fr_heats WHERE roundId = ?)");

                    try {
                        stmt.setInt(1, roundId);
                        stmt.executeUpdate();
                    } catch (Throwable var24) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var20) {
                                var24.addSuppressed(var20);
                            }
                        }

                        throw var24;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    stmt = conn.prepareStatement("DELETE FROM fr_heats WHERE roundId = ?");

                    try {
                        stmt.setInt(1, roundId);
                        stmt.executeUpdate();
                    } catch (Throwable var23) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var19) {
                                var23.addSuppressed(var19);
                            }
                        }

                        throw var23;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    stmt = conn.prepareStatement("DELETE FROM fr_rounds WHERE id = ?");

                    try {
                        stmt.setInt(1, roundId);
                        stmt.executeUpdate();
                    } catch (Throwable var22) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var18) {
                                var22.addSuppressed(var18);
                            }
                        }

                        throw var22;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    conn.commit();
                    this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Round deletado (Assíncrono): ID=" + roundId);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro assíncrono ao deletar round: " + e.getMessage());
            }

        });
    }

    public void deleteHeat(int heatId) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();
                conn.setAutoCommit(false);

                try {
                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM fr_laps WHERE heatId = ?");

                    try {
                        stmt.setInt(1, heatId);
                        stmt.executeUpdate();
                    } catch (Throwable var21) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var18) {
                                var21.addSuppressed(var18);
                            }
                        }

                        throw var21;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    stmt = conn.prepareStatement("DELETE FROM fr_drivers WHERE heatId = ?");

                    try {
                        stmt.setInt(1, heatId);
                        stmt.executeUpdate();
                    } catch (Throwable var20) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var17) {
                                var20.addSuppressed(var17);
                            }
                        }

                        throw var20;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    stmt = conn.prepareStatement("DELETE FROM fr_heats WHERE id = ?");

                    try {
                        stmt.setInt(1, heatId);
                        stmt.executeUpdate();
                    } catch (Throwable var19) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var16) {
                                var19.addSuppressed(var16);
                            }
                        }

                        throw var19;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }

                    conn.commit();
                    this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Heat deletado (Assíncrono): ID=" + heatId);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro assíncrono ao deletar heat: " + e.getMessage());
            }

        });
    }

    private Rounds instantiateRound(FormulaRacing plugin, int id, Events event, int index, RoundType type) {
        switch (type) {
            case PRACTICE -> {
                return new PracticeRound(plugin, id, event, index, type);
            }
            case QUALIFICATION -> {
                return new QualificationRound(plugin, id, event, index, type);
            }
            case FINAL -> {
                return new RaceRound(plugin, id, event, index, type);
            }
            default -> throw new IllegalArgumentException("Unknown RoundType: " + String.valueOf(type));
        }
    }

    public void updateRoundState(int roundId, RoundState state) {
        String sql = "UPDATE fr_rounds SET state = ? WHERE id = ?";
        this.executeAsync(sql, "updateRoundState", (stmt) -> {
            try {
                stmt.setString(1, state.name());
                stmt.setInt(2, roundId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Rounds> loadRoundsByEventId(int eventId) {
        String sql = "SELECT * FROM fr_rounds WHERE eventId = ? ORDER BY roundIndex";
        List<Rounds> rounds = new ArrayList();

        try (
                Connection conn = this.databaseManager.getOrConnect();
                PreparedStatement stmt = conn.prepareStatement(sql);
        ) {
            stmt.setInt(1, eventId);

            try (ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    Rounds round = this.buildRoundFromResultSet(rs);
                    rounds.add(round);
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao carregar rounds: " + e.getMessage());
        }

        return rounds;
    }

    private Rounds buildRoundFromResultSet(ResultSet rs) throws SQLException {
        int roundId = rs.getInt("id");
        int eventId = rs.getInt("eventId");
        int roundIndex = rs.getInt("roundIndex");
        RoundType roundType = RoundType.valueOf(rs.getString("type"));
        Rounds round = this.instantiateRound(this.plugin, roundId, (Events)null, roundIndex, roundType);
        round.setEventId(eventId);
        round.setState(RoundState.valueOf(rs.getString("state")));

        for(Heats heat : this.loadHeatsByRoundId(round.getId())) {
            heat.setRound(round);
            round.getHeats().put(heat.getHeatNumber(), heat);
        }

        return round;
    }

    public void updateHeatState(int heatId, HeatState state) {
        String sql = "UPDATE fr_heats SET state = ? WHERE id = ?";
        this.executeAsync(sql, "updateHeatState", (stmt) -> {
            try {
                stmt.setString(1, state.name());
                stmt.setInt(2, heatId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateHeatTimes(int heatId, Instant startTime, Instant endTime) {
        String sql = "UPDATE fr_heats SET startTime = ?, endTime = ? WHERE id = ?";
        this.executeAsync(sql, "updateHeatTimes", (stmt) -> {
            try {
                stmt.setLong(1, startTime != null ? startTime.getEpochSecond() : 0L);
                stmt.setLong(2, endTime != null ? endTime.getEpochSecond() : 0L);
                stmt.setInt(3, heatId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateHeatFastestLap(int heatId, UUID driverUUID) {
        String sql = "UPDATE fr_heats SET fastestLapUUID = ? WHERE id = ?";
        this.executeAsync(sql, "updateHeatFastestLap", (stmt) -> {
            try {
                stmt.setString(1, driverUUID.toString());
                stmt.setInt(2, heatId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void clearHeatDrivers(int heatId) {
        String sql = "DELETE FROM fr_drivers WHERE heatId = ?";
        this.executeAsync(sql, "clearHeatDrivers", (stmt) -> {
            try {
                stmt.setInt(1, heatId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean clearHeatDriversSync(int heatId) {
        String sql = "DELETE FROM fr_drivers WHERE heatId = ?";

        try {
            Connection conn = this.databaseManager.getOrConnect();
            if (conn == null) {
                return false;
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, heatId);
                stmt.executeUpdate();
                return true;
            }
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Falha sync ao limpar pilotos do heat " + heatId + ": " + exception.getMessage());
            return false;
        }
    }

    public void updateHeatLonely(int heatId, boolean lonely) {
        String sql = "UPDATE fr_heats SET lonely = ? WHERE id = ?";
        this.executeAsync(sql, "updateHeatLonely", (stmt) -> {
            try {
                stmt.setInt(1, lonely ? 1 : 0);
                stmt.setInt(2, heatId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateHeatConfig(int heatId, int totalLaps, int totalPits, int maxDrivers) {
        String sql = "UPDATE fr_heats SET totalLaps = ?, totalPitstops = ?, maxDrivers = ? WHERE id = ?";
        this.executeAsync(sql, "updateHeatConfig", (stmt) -> {
            try {
                stmt.setInt(1, totalLaps);
                stmt.setInt(2, totalPits);
                stmt.setInt(3, maxDrivers);
                stmt.setInt(4, heatId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateHeatFullConfig(int heatId, Integer totalLaps, Integer totalPits, Integer timeLimit,
                                      Integer startDelay, Integer maxDrivers, boolean lonely, boolean canReset,
                                      boolean lapReset, CollisionMode collisionMode, boolean drsEnabled,
                                      boolean driverSwap, double drsDowntime, double drsDownPower,
                                      boolean reverseGrid, int deltaGhosting, boolean pushToPass,
                                      double pushToPassPower, boolean realistic) {
        String sql = "UPDATE fr_heats SET totalLaps = ?, totalPitstops = ?, timeLimit = ?, startDelay = ?, " +
                "maxDrivers = ?, lonely = ?, canReset = ?, lapReset = ?, colisao = ?, drs = ?, " +
                "driverswap = ?, drsdowntime = ?, drsdownpower = ?, reversegrid = ?, ghostingdelta = ?, " +
                "pushtopass = ?, pushtopasspower = ?, realistc = ? WHERE id = ?";
        this.executeAsync(sql, "updateHeatFullConfig", (stmt) -> {
            try {
                if (totalLaps != null) { stmt.setInt(1, totalLaps); } else { stmt.setNull(1, java.sql.Types.INTEGER); }
                if (totalPits != null) { stmt.setInt(2, totalPits); } else { stmt.setNull(2, java.sql.Types.INTEGER); }
                if (timeLimit != null) { stmt.setInt(3, timeLimit); } else { stmt.setNull(3, java.sql.Types.INTEGER); }
                if (startDelay != null) { stmt.setInt(4, startDelay); } else { stmt.setNull(4, java.sql.Types.INTEGER); }
                if (maxDrivers != null) { stmt.setInt(5, maxDrivers); } else { stmt.setNull(5, java.sql.Types.INTEGER); }
                stmt.setInt(6, lonely ? 1 : 0);
                stmt.setInt(7, canReset ? 1 : 0);
                stmt.setInt(8, lapReset ? 1 : 0);
                stmt.setString(9, collisionMode.name());
                stmt.setInt(10, drsEnabled ? 1 : 0);
                stmt.setInt(11, driverSwap ? 1 : 0);
                stmt.setDouble(12, drsDowntime);
                stmt.setDouble(13, drsDownPower);
                stmt.setInt(14, reverseGrid ? 1 : 0);
                stmt.setDouble(15, (double) deltaGhosting);
                stmt.setInt(16, pushToPass ? 1 : 0);
                stmt.setDouble(17, pushToPassPower);
                stmt.setInt(18, realistic ? 1 : 0);
                stmt.setInt(19, heatId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Heats> loadHeatsByRoundId(int roundId) {
        String sql = "SELECT * FROM fr_heats WHERE roundId = ? ORDER BY heatNumber";
        List<Heats> heats = new ArrayList();

        try (
                Connection conn = this.databaseManager.getOrConnect();
                PreparedStatement stmt = conn.prepareStatement(sql);
        ) {
            stmt.setInt(1, roundId);

            try (ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    Heats heat = this.buildHeatFromResultSet(rs);
                    heats.add(heat);
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao carregar heats: " + e.getMessage());
        }

        return heats;
    }

    private Heats buildHeatFromResultSet(ResultSet rs) throws SQLException {
        int heatId = rs.getInt("id");
        int roundId = rs.getInt("roundId");
        int heatNumber = rs.getInt("heatNumber");
        Heats heat = new Heats(this.plugin, heatId, (Rounds)null, heatNumber);
        heat.setRoundId(roundId);
        heat.setHeatStateForLoad(HeatState.valueOf(rs.getString("state")));
        long startTimestamp = rs.getLong("startTime");
        if (startTimestamp > 0L) {
            heat.setStartTime(Instant.ofEpochSecond(startTimestamp));
        }
        long endTimestamp = rs.getLong("endTime");
        if (endTimestamp > 0L) {
            heat.setEndTime(Instant.ofEpochSecond(endTimestamp));
        }
        String fastestLapUUID = rs.getString("fastestLapUUID");
        if (fastestLapUUID != null) {
            heat.setFastestLapUUID(UUID.fromString(fastestLapUUID));
        }
        int totalLaps = rs.getInt("totalLaps");
        heat.setTotalLaps(rs.wasNull() ? null : totalLaps);
        int totalPits = rs.getInt("totalPitstops");
        heat.setTotalPits(rs.wasNull() ? null : totalPits);
        int timeLimit = rs.getInt("timeLimit");
        heat.setTimeLimit(rs.wasNull() ? null : timeLimit);
        heat.setStartDelay(rs.getInt("startDelay"));
        int maxDrivers = rs.getInt("maxDrivers");
        heat.setMaxDrivers(rs.wasNull() ? null : maxDrivers);
        heat.setLonely(rs.getInt("lonely") == 1);
        heat.setCanReset(rs.getInt("canReset") == 1);

        try { heat.setCollisionMode(CollisionMode.valueOf(rs.getString("colisao"))); } catch (SQLException ignored) { heat.setCollisionMode(CollisionMode.DISABLED); }
        try { heat.setDrsEnabled(rs.getInt("drs") == 1); } catch (SQLException ignored) {}
        try { heat.setDriverSwap(rs.getInt("driverswap") == 1); } catch (SQLException ignored) {}
        try { heat.setDrsdowntime(rs.getDouble("drsdowntime")); } catch (SQLException ignored) {}
        try { heat.setDrsdownpower(rs.getDouble("drsdownpower")); } catch (SQLException ignored) {}
        try { heat.setreversegrid(rs.getInt("reversegrid") == 1); } catch (SQLException ignored) {}
        try { heat.setDeltaghosting((int) rs.getDouble("ghostingdelta")); } catch (SQLException ignored) {}
        try { heat.setPushtopass(rs.getInt("pushtopass") == 1); } catch (SQLException ignored) {}
        try { heat.setpushtopasspower(rs.getDouble("pushtopasspower")); } catch (SQLException ignored) {}
        try { heat.setrealistc(rs.getInt("realistc") == 1); } catch (SQLException ignored) {}

        for (Driver driver : this.loadDriversByHeatId(heat.getId())) {
            heat.addDriverDirect(driver);
        }
        return heat;
    }

    public void addDriverToHeatWithShift(UUID uuid, int heatId, int position) {
        String shiftSql = "UPDATE fr_drivers SET position = position + 1, startPosition = startPosition + 1 WHERE heatId = ? AND position >= ?";
        String insertSql = "INSERT INTO fr_drivers (uuid, heatId, position, startPosition, pitstops) VALUES (?, ?, ?, ?, ?)";
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();
                if (conn == null) {
                    return;
                }

                boolean initialAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try {
                    PreparedStatement shift = conn.prepareStatement(shiftSql);

                    try {
                        shift.setInt(1, heatId);
                        shift.setInt(2, position);
                        shift.executeUpdate();
                    } catch (Throwable var22) {
                        if (shift != null) {
                            try {
                                shift.close();
                            } catch (Throwable var20) {
                                var22.addSuppressed(var20);
                            }
                        }

                        throw var22;
                    }

                    if (shift != null) {
                        shift.close();
                    }

                    shift = conn.prepareStatement(insertSql);

                    try {
                        shift.setString(1, uuid.toString());
                        shift.setInt(2, heatId);
                        shift.setInt(3, position);
                        shift.setInt(4, position);
                        shift.setInt(5, 0);
                        shift.executeUpdate();
                    } catch (Throwable var21) {
                        if (shift != null) {
                            try {
                                shift.close();
                            } catch (Throwable var19) {
                                var21.addSuppressed(var19);
                            }
                        }

                        throw var21;
                    }

                    if (shift != null) {
                        shift.close();
                    }

                    conn.commit();
                    this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Player " + String.valueOf(uuid) + " adicionado ao heat " + heatId + " na posição " + position);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(initialAutoCommit);
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao adicionar player ao heat com shift: " + e.getMessage());
            }

        });
    }

    public boolean addDriverToHeatWithShiftSync(UUID uuid, int heatId, int position) {
        String shiftSql = "UPDATE fr_drivers SET position = position + 1, startPosition = startPosition + 1 WHERE heatId = ? AND position >= ?";
        String insertSql = "INSERT INTO fr_drivers (uuid, heatId, position, startPosition, pitstops) VALUES (?, ?, ?, ?, ?)";

        try {
            Connection conn = this.databaseManager.getOrConnect();
            if (conn == null) {
                return false;
            }

            boolean initialAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                try (PreparedStatement shiftStmt = conn.prepareStatement(shiftSql)) {
                    shiftStmt.setInt(1, heatId);
                    shiftStmt.setInt(2, position);
                    shiftStmt.executeUpdate();
                }

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, uuid.toString());
                    insertStmt.setInt(2, heatId);
                    insertStmt.setInt(3, position);
                    insertStmt.setInt(4, position);
                    insertStmt.setInt(5, 0);
                    insertStmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException exception) {
                conn.rollback();
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro sync ao adicionar piloto com shift: " + exception.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(initialAutoCommit);
            }
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Falha sync ao adicionar piloto com shift: " + exception.getMessage());
            return false;
        }
    }

    public void removeDriverFromHeatWithShift(UUID uuid, int heatId) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();
                if (conn != null) {
                    boolean initialAutoCommit = conn.getAutoCommit();
                    conn.setAutoCommit(false);

                    try {
                        int removedPosition = -1;
                        PreparedStatement ps = conn.prepareStatement("SELECT position FROM fr_drivers WHERE uuid=? AND heatId=?");

                        try {
                            ps.setString(1, uuid.toString());
                            ps.setInt(2, heatId);
                            ResultSet rs = ps.executeQuery();

                            try {
                                if (rs.next()) {
                                    removedPosition = rs.getInt("position");
                                }
                            } catch (Throwable var28) {
                                if (rs != null) {
                                    try {
                                        rs.close();
                                    } catch (Throwable var25) {
                                        var28.addSuppressed(var25);
                                    }
                                }

                                throw var28;
                            }

                            if (rs != null) {
                                rs.close();
                            }
                        } catch (Throwable var29) {
                            if (ps != null) {
                                try {
                                    ps.close();
                                } catch (Throwable var24) {
                                    var29.addSuppressed(var24);
                                }
                            }

                            throw var29;
                        }

                        if (ps != null) {
                            ps.close();
                        }

                        if (removedPosition != -1) {
                            ps = conn.prepareStatement("DELETE FROM fr_drivers WHERE uuid=? AND heatId=?");

                            try {
                                ps.setString(1, uuid.toString());
                                ps.setInt(2, heatId);
                                ps.executeUpdate();
                            } catch (Throwable var27) {
                                if (ps != null) {
                                    try {
                                        ps.close();
                                    } catch (Throwable var23) {
                                        var27.addSuppressed(var23);
                                    }
                                }

                                throw var27;
                            }

                            if (ps != null) {
                                ps.close();
                            }

                            ps = conn.prepareStatement("UPDATE fr_drivers SET position = position - 1, startPosition = startPosition - 1 WHERE heatId=? AND position > ?");

                            try {
                                ps.setInt(1, heatId);
                                ps.setInt(2, removedPosition);
                                ps.executeUpdate();
                            } catch (Throwable var26) {
                                if (ps != null) {
                                    try {
                                        ps.close();
                                    } catch (Throwable var22) {
                                        var26.addSuppressed(var22);
                                    }
                                }

                                throw var26;
                            }

                            if (ps != null) {
                                ps.close();
                            }

                            conn.commit();
                            DebugManager var10000 = this.plugin.getDebugManager();
                            String var10001 = String.valueOf(uuid);
                            var10000.logDatabaseOperation("[EventsDB] Player " + var10001 + " removido e posições reajustadas no heat " + heatId);
                            return;
                        }

                        conn.rollback();
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(initialAutoCommit);
                    }

                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao remover player do heat com shift: " + e.getMessage());
            }
        });
    }

    public boolean removeDriverFromHeatWithShiftSync(UUID uuid, int heatId) {
        String fetchSql = "SELECT position FROM fr_drivers WHERE uuid=? AND heatId=?";
        String deleteSql = "DELETE FROM fr_drivers WHERE uuid=? AND heatId=?";
        String shiftSql = "UPDATE fr_drivers SET position = position - 1, startPosition = startPosition - 1 WHERE heatId=? AND position > ?";

        try {
            Connection conn = this.databaseManager.getOrConnect();
            if (conn == null) {
                return false;
            }

            boolean initialAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                int removedPosition = -1;
                try (PreparedStatement fetchStmt = conn.prepareStatement(fetchSql)) {
                    fetchStmt.setString(1, uuid.toString());
                    fetchStmt.setInt(2, heatId);
                    try (ResultSet rs = fetchStmt.executeQuery()) {
                        if (rs.next()) {
                            removedPosition = rs.getInt("position");
                        }
                    }
                }

                if (removedPosition == -1) {
                    conn.rollback();
                    return false;
                }

                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, uuid.toString());
                    deleteStmt.setInt(2, heatId);
                    deleteStmt.executeUpdate();
                }

                try (PreparedStatement shiftStmt = conn.prepareStatement(shiftSql)) {
                    shiftStmt.setInt(1, heatId);
                    shiftStmt.setInt(2, removedPosition);
                    shiftStmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException exception) {
                conn.rollback();
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro sync ao remover piloto com shift: " + exception.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(initialAutoCommit);
            }
        } catch (SQLException exception) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Falha sync ao remover piloto com shift: " + exception.getMessage());
            return false;
        }
    }

    public void updateHeatGridPositions(int heatId, Map<UUID, Integer> positions) {
        String sql = "UPDATE fr_drivers SET position = ?, startPosition = ? WHERE heatId = ? AND uuid = ?";
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();
                if (conn == null) {
                    return;
                }

                boolean initialAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try {
                    PreparedStatement stmt = conn.prepareStatement(sql);

                    try {
                        for(Map.Entry<UUID, Integer> entry : positions.entrySet()) {
                            stmt.setInt(1, (Integer)entry.getValue());
                            stmt.setInt(2, (Integer)entry.getValue());
                            stmt.setInt(3, heatId);
                            stmt.setString(4, ((UUID)entry.getKey()).toString());
                            stmt.addBatch();
                        }

                        stmt.executeBatch();
                        conn.commit();
                        this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Grid do heat " + heatId + " atualizado.");
                    } catch (Throwable var16) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable var15) {
                                var16.addSuppressed(var15);
                            }
                        }

                        throw var16;
                    }

                    if (stmt != null) {
                        stmt.close();
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(initialAutoCommit);
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao atualizar grid do heat: " + e.getMessage());
            }

        });
    }

    public void createDriver(Driver driver) {
        String sql = "INSERT INTO fr_drivers (uuid, heatId, position, startPosition, pitstops) VALUES (?, ?, ?, ?, ?)";
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                Connection conn = this.databaseManager.getOrConnect();
                if (conn == null) {
                    return;
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql, 1)) {
                    stmt.setString(1, driver.getUuid().toString());
                    stmt.setInt(2, driver.getHeatId());
                    stmt.setInt(3, driver.getStartPosition());
                    stmt.setInt(4, driver.getStartPosition());
                    stmt.setInt(5, 0);
                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int driverId = rs.getInt(1);
                            driver.setId(driverId);
                            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Driver criado (Assíncrono): ID=" + driverId + ", UUID=" + String.valueOf(driver.getUuid()));
                        }
                    }
                }
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro assíncrono ao criar driver: " + e.getMessage());
            }

        });
    }

    public void updateDriverPosition(int driverId, int position) {
        String sql = "UPDATE fr_drivers SET position = ? WHERE id = ?";
        this.executeAsync(sql, "updateDriverPosition", (stmt) -> {
            try {
                stmt.setInt(1, position);
                stmt.setInt(2, driverId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateDriverTimes(int driverId, Instant startTime, Instant endTime) {
        String sql = "UPDATE fr_drivers SET startTime = ?, endTime = ? WHERE id = ?";
        this.executeAsync(sql, "updateDriverTimes", (stmt) -> {
            try {
                stmt.setLong(1, startTime != null ? startTime.getEpochSecond() : 0L);
                stmt.setLong(2, endTime != null ? endTime.getEpochSecond() : 0L);
                stmt.setInt(3, driverId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void incrementDriverPitstops(int driverId) {
        String sql = "UPDATE fr_drivers SET pitstops = pitstops + 1 WHERE id = ?";
        this.executeAsync(sql, "incrementDriverPitstops", (stmt) -> {
            try {
                stmt.setInt(1, driverId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void deleteDriver(UUID playerUUID, int heatId) {
        String sql = "DELETE FROM fr_drivers WHERE uuid = ? AND heatId = ?";
        this.executeAsync(sql, "deleteDriver", (stmt) -> {
            try {
                stmt.setString(1, playerUUID.toString());
                stmt.setInt(2, heatId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Driver> loadDriversByHeatId(int heatId) {
        String sql = "SELECT * FROM fr_drivers WHERE heatId = ? ORDER BY position";
        List<Driver> drivers = new ArrayList();

        try (
                Connection conn = this.databaseManager.getOrConnect();
                PreparedStatement stmt = conn.prepareStatement(sql);
        ) {
            stmt.setInt(1, heatId);

            try (ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    Driver driver = this.buildDriverFromResultSet(rs);
                    drivers.add(driver);
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao carregar drivers: " + e.getMessage());
        }

        return drivers;
    }

    private Driver buildDriverFromResultSet(ResultSet rs) throws SQLException {
        UUID driverUUID = UUID.fromString(rs.getString("uuid"));
        int heatId = rs.getInt("heatId");
        int startPosition = rs.getInt("startPosition");
        Driver driver = new Driver(driverUUID, heatId, startPosition);
        driver.setId(rs.getInt("id"));
        driver.setPosition(rs.getInt("position"));
        driver.setPitstops(rs.getInt("pitstops"));
        long startTimestamp = rs.getLong("startTime");
        if (startTimestamp > 0L) {
            driver.setStartTime(startTimestamp);
        }

        long endTimestamp = rs.getLong("endTime");
        if (endTimestamp > 0L) {
            driver.setEndTime(endTimestamp);
        }

        return driver;
    }

    public void createLap(UUID playerUUID, int heatId, String trackNameWS, long lapStart, long lapEnd, boolean pitted) {
        String sql = "INSERT INTO fr_laps (uuid, heatId, tracknameWS, lapStart, lapEnd, pitted) VALUES (?, ?, ?, ?, ?, ?)";
        this.executeAsync(sql, "createLap", (stmt) -> {
            try {
                stmt.setString(1, playerUUID.toString());
                stmt.setInt(2, heatId);
                stmt.setString(3, trackNameWS);
                stmt.setLong(4, lapStart);
                stmt.setLong(5, lapEnd);
                stmt.setInt(6, pitted ? 1 : 0);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void finishLap(int lapId, long lapEnd) {
        String sql = "UPDATE fr_laps SET lapEnd = ? WHERE id = ?";
        this.executeAsync(sql, "finishLap", (stmt) -> {
            try {
                stmt.setLong(1, lapEnd);
                stmt.setInt(2, lapId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Map<String, Object>> loadLapsByDriverAndHeat(UUID playerUUID, int heatId) {
        String sql = "SELECT * FROM fr_laps WHERE uuid = ? AND heatId = ? ORDER BY lapStart";
        List<Map<String, Object>> laps = new ArrayList();

        try (
                Connection conn = this.databaseManager.getOrConnect();
                PreparedStatement stmt = conn.prepareStatement(sql);
        ) {
            stmt.setString(1, playerUUID.toString());
            stmt.setInt(2, heatId);

            try (ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    Map<String, Object> lap = new HashMap<>();
                    lap.put("id", rs.getInt("id"));
                    lap.put("lapStart", rs.getLong("lapStart"));
                    lap.put("lapEnd", rs.getLong("lapEnd"));
                    lap.put("pitted", rs.getInt("pitted") == 1);
                    laps.add(lap);
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao carregar laps: " + e.getMessage());
        }

        return laps;
    }

    public synchronized Map<String, Location> getDrsRegions(String trackNameWS) {
        Map<String, Location> regions = new HashMap<>();
        trackNameWS = trackNameWS.replace(" ", "").toLowerCase();
        String sql = "SELECT * FROM fr_drs WHERE trackNameWS = ?";

        try {
            Object var9;
            try (Connection conn = this.databaseManager.getOrConnect()) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, trackNameWS);
                    ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) {
                        return regions;
                    }

                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        if (rs.getObject("detectMinMinX") != null) {
                            regions.put("detectMin", new Location(world, rs.getDouble("detectMinMinX"), rs.getDouble("detectMinMinY"), rs.getDouble("detectMinMinZ")));
                            regions.put("detectMax", new Location(world, rs.getDouble("detectMinMaxX"), rs.getDouble("detectMinMaxY"), rs.getDouble("detectMinMaxZ")));
                        }

                        if (rs.getObject("drsMinX") != null) {
                            regions.put("startMin", new Location(world, rs.getDouble("drsMinX"), rs.getDouble("drsMinY"), rs.getDouble("drsMinZ")));
                            regions.put("startMax", new Location(world, rs.getDouble("drsMaxX"), rs.getDouble("drsMaxY"), rs.getDouble("drsMaxZ")));
                        }

                        if (rs.getObject("endDrsMinX") != null) {
                            regions.put("finishMin", new Location(world, rs.getDouble("endDrsMinX"), rs.getDouble("endDrsMinY"), rs.getDouble("endDrsMinZ")));
                            regions.put("finishMax", new Location(world, rs.getDouble("endDrsMaxX"), rs.getDouble("endDrsMaxY"), rs.getDouble("endDrsMaxZ")));
                        }

                        return regions;
                    }

                    var9 = regions;
                }
            }

            return (Map<String, Location>)var9;
        } catch (SQLException e) {
            e.printStackTrace();
            return regions;
        }
    }

    public Map<String, Object> getFastestLapInHeat(int heatId) {
        String sql = "SELECT uuid, MIN(lapEnd - lapStart) as fastestTime FROM fr_laps WHERE heatId = ? AND lapEnd IS NOT NULL GROUP BY uuid ORDER BY fastestTime LIMIT 1";

        try {
            try (
                    Connection conn = this.databaseManager.getOrConnect();
                    PreparedStatement stmt = conn.prepareStatement(sql);
            ) {
                stmt.setInt(1, heatId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("uuid", UUID.fromString(rs.getString("uuid")));
                        result.put("time", rs.getLong("fastestTime"));
                        Object var7 = result;
                        return (Map<String, Object>)var7;
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao buscar fastest lap: " + e.getMessage());
            return null;
        }
    }
}
