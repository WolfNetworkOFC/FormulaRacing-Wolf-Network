package dev.EfraGroup.formulaRacing.Database;

import dev.EfraGroup.formulaRacing.FileManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
//import dev.EfraGroup.formulaRacing.Heat.Heats;
//import dev.EfraGroup.formulaRacing.Round.Rounds;
//import dev.EfraGroup.formulaRacing.Participant.Driver;
//import dev.EfraGroup.formulaRacing.Utils.RaceUtils;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class EventsManager {

    private final FormulaRacing plugin;
    private final FileManager fileManager;
    private final DatabaseManager db;
    //private final RaceUtils ru;
    //private final Map<UUID, Lap> activeLaps = new HashMap<>();

    public EventsManager(FormulaRacing plugin, FileManager fileManager, DatabaseManager db) {
        this.plugin = plugin;
        this.fileManager = fileManager;
        this.db = db;
        //this.ru = ru;
    }


    public Connection getConnection() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "database.db");
        try {
            if (!dbFile.exists()) {
                dbFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao criar arquivo SQLite: " + e.getMessage(), e);
            throw new SQLException("Erro ao criar arquivo SQLite.", e);
        }

        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }


    // ================== EVENTS ==================
    public void createEvent(UUID creatorUUID, String name, String trackNameWS) {
        String sql = "INSERT INTO fr_events (creatorUUID, name, trackNameWS, state, creationTime) VALUES (?, ?, ?, 'SETUP', ?)";
        long creationTime = System.currentTimeMillis();

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, creatorUUID.toString());
            ps.setString(2, name);
            ps.setString(3, trackNameWS);
            ps.setLong(4, creationTime);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao criar evento: " + e.getMessage());
        }
    }

    public Integer getRoundIdByEventAndNumber(int eventId, int roundNumber) {
        String sql = "SELECT id FROM rounds WHERE eventId = ? AND roundIndex = ? LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, eventId);
            stmt.setInt(2, roundNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null; // not found
    }


    /*
    // ============================
// 🔹 Pega o horário de início do Heat
// ============================
    public String getHeatStartTime(int heatId) {
        String sql = "SELECT startTime FROM fr_heats WHERE id=?";
        int retries = 3;
        while (retries-- > 0) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, heatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long time = rs.getLong("startTime");
                        if (time > 0) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yy 'às' HH:mm");
                            return sdf.format(new java.util.Date(time));
                        }
                    }
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("database is locked")) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                } else {
                    plugin.getLogger().severe("[FormulaRacing] Erro ao obter startTime do heat " + heatId + ": " + e.getMessage());
                    break;
                }
            }
        }
        return null;
    }

    // ============================
// 🔹 Pega o delay antes do início
// ============================
    public long getHeatStartDelay(int heatId) {
        String sql = "SELECT startDelay FROM fr_heats WHERE id=?";
        int retries = 3;
        while (retries-- > 0) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, heatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong("startDelay");
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("database is locked")) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                } else {
                    plugin.getLogger().severe("[FormulaRacing] Erro ao obter startDelay do heat " + heatId + ": " + e.getMessage());
                    break;
                }
            }
        }
        return 0;
    }

    // ============================
// 🔹 Número de voltas
// ============================
    public int getHeatLaps(int heatId) {
        String sql = "SELECT totalLaps FROM fr_heats WHERE id=?";
        int retries = 3;
        while (retries-- > 0) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, heatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("totalLaps");
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("database is locked")) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                } else {
                    plugin.getLogger().severe("[FormulaRacing] Erro ao obter laps do heat " + heatId + ": " + e.getMessage());
                    break;
                }
            }
        }
        return 0;
    }

    // ============================
// 🔹 Número de pits
// ============================
    public int getHeatPits(int heatId) {
        String sql = "SELECT totalPitstops FROM fr_heats WHERE id=?";
        int retries = 3;
        while (retries-- > 0) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, heatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("totalPitstops");
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("database is locked")) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                } else {
                    plugin.getLogger().severe("[FormulaRacing] Erro ao obter pits do heat " + heatId + ": " + e.getMessage());
                    break;
                }
            }
        }
        return 0;
    }

    // ============================
// 🔹 Máximo de drivers
// ============================
    public int getHeatMaxDrivers(int heatId) {
        String sql = "SELECT maxDrivers FROM fr_heats WHERE id=?";
        int retries = 3;
        while (retries-- > 0) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, heatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("maxDrivers");
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("database is locked")) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                } else {
                    plugin.getLogger().severe("[FormulaRacing] Erro ao obter maxDrivers do heat " + heatId + ": " + e.getMessage());
                    break;
                }
            }
        }
        return 0;
    }

    // ============================
// 🔹 Melhor volta (tempo)
// ============================
    public String getFastestLapTime(int heatId) {
        String sql = """
        SELECT startTime, endTime
        FROM fr_drivers
        WHERE heatId = ? AND startTime IS NOT NULL AND endTime IS NOT NULL
    """;

        long bestTime = Long.MAX_VALUE;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, heatId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long start = rs.getLong("startTime");
                    long end = rs.getLong("endTime");

                    if (end > start) {
                        long duration = end - start;
                        if (duration < bestTime) {
                            bestTime = duration;
                        }
                    }
                }
            }

            if (bestTime != Long.MAX_VALUE) {
                long minutes = (bestTime / 1000) / 60;
                long seconds = (bestTime / 1000) % 60;
                long millis = bestTime % 1000;
                return String.format("%d:%02d.%03d", minutes, seconds, millis);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao obter melhor volta do heat " + heatId + ": " + e.getMessage());
        }

        return null;
    }


    public String getFastestLapDriver(int heatId) {
        String sql = "SELECT fastestLapUUID FROM fr_heats WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, heatId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String uuidStr = rs.getString("fastestLapUUID");
                    if (uuidStr != null && !uuidStr.isEmpty()) {
                        UUID uuid = UUID.fromString(uuidStr);
                        String name = Bukkit.getOfflinePlayer(uuid).getName();
                        return (name != null) ? name : uuid.toString();
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao obter melhor piloto do heat "
                    + heatId + ": " + e.getMessage());
        }

        return null;
    }



    public List<Heats> getHeats() {
        List<Heats> heats = new ArrayList<>();
        String sql = "SELECT * FROM fr_heats";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int roundId = rs.getInt("roundId");
                int heatNumber = rs.getInt("heatNumber");
                Optional<Rounds> round = getRounds().stream().filter(r -> r.getId() == roundId).findFirst();
                if (round.isPresent()) {
                    heats.add(new Heats(id, round.get(), heatNumber, this, plugin, db, ru));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar heats: " + e.getMessage());
        }
        return heats;
    }

    public boolean updateHeatState(int heatId, Heats.HeatState state) {
        String sql = "UPDATE fr_heats SET state=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, state.name());
            ps.setInt(2, heatId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao atualizar estado do heat: " + e.getMessage());
        }
        return false;
    }

    public void deleteHeat(Heats heat) {
        String sql = "DELETE FROM fr_heats WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, heat.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao deletar heat: " + e.getMessage());
        }
    }
*/
    public boolean deleteRound(int roundId) {

        // ==========================================
        // 1. Verificar se o Round está em SETUP
        // ==========================================
        String checkRoundStateSql = "SELECT state FROM fr_rounds WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(checkRoundStateSql)) {
            ps.setInt(1, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    plugin.getLogger().warning("[FormulaRacing] Tentativa de deletar round inexistente: " + roundId);
                    return false;
                }
                String state = rs.getString("state");
                if (!state.equalsIgnoreCase("SETUP")) {
                    plugin.getLogger().warning("[FormulaRacing] Round " + roundId + " não está em SETUP. Cancelando delete.");
                    return false;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao checar estado do round " + roundId + ": " + e.getMessage());
            return false;
        }


        // ==========================================
        // 2. Verificar se TODOS os Heats do round estão em SETUP
        // ==========================================
        String checkHeatsSql = "SELECT state FROM fr_heats WHERE roundId = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(checkHeatsSql)) {
            ps.setInt(1, roundId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String heatState = rs.getString("state");
                    if (!heatState.equalsIgnoreCase("SETUP")) {
                        plugin.getLogger().warning("[FormulaRacing] Um ou mais heats do round " + roundId + " não estão em SETUP. Cancelando delete.");
                        return false;
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao checar heats do round " + roundId + ": " + e.getMessage());
            return false;
        }


        // ==========================================
        // 3. Deletar heats agora que tudo está validado
        // ==========================================
        String deleteHeatsSql = "DELETE FROM fr_heats WHERE roundId = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(deleteHeatsSql)) {
            ps.setInt(1, roundId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao deletar heats do round " + roundId + ": " + e.getMessage());
            return false;
        }


        // ==========================================
        // 4. Deletar o round
        // ==========================================
        String deleteRoundSql = "DELETE FROM fr_rounds WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(deleteRoundSql)) {
            ps.setInt(1, roundId);
            int affected = ps.executeUpdate();

            if (affected == 0) {
                plugin.getLogger().warning("[FormulaRacing] Round " + roundId + " não foi deletado (não existia?).");
                return false;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao deletar round " + roundId + ": " + e.getMessage());
            return false;
        }

        return true;
    }

    /*
    public Optional<Events> getEventById(int id) {
        String sql = "SELECT * FROM fr_events WHERE id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapEvent(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar evento por ID: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<Events> getEventByName(String name) {
        String sql = "SELECT * FROM fr_events WHERE name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapEvent(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar evento por nome: " + e.getMessage());
        }
        return Optional.empty();
    }
*/
    public List<String> getAllEvents() {
        List<String> events = new ArrayList<>();
        String sql = "SELECT name FROM fr_events";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                events.add(rs.getString("name"));
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar lista de eventos: " + e.getMessage());
        }

        return events;
    }


    public boolean getIfEventExistsByName(String name) {

        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String sql = "SELECT 1 FROM fr_events WHERE name = ? LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // TRUE se existir, FALSE se não existir
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao verificar existência de evento '" + name + "': " + e.getMessage());
            return false;
        }
    }


    public void setEventTrack(int eventId, String trackNameWS) {
        String sql = "UPDATE fr_events SET trackNameWS = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trackNameWS);
            ps.setInt(2, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao atualizar pista do evento (ID: " + eventId + "): " + e.getMessage());
            e.printStackTrace();
        }
    }
/*
    public List<Events> getEvents() {
        List<Events> events = new ArrayList<>();
        String sql = "SELECT id, creatorUUID, name, trackNameWS, creationTime, openSign, state " +
                "FROM fr_events";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                UUID creatorUUID = UUID.fromString(rs.getString("creatorUUID"));
                String name = rs.getString("name");
                String trackNameWS = rs.getString("trackNameWS");
                long creationTime = rs.getLong("creationTime");
                boolean openSign = rs.getBoolean("openSign");
                Events.EventState state = Events.EventState.valueOf(rs.getString("state"));

                // Inicializa subscribers/reserves/spectators como vazios
                Events event = new Events(
                        this,
                        id,
                        creatorUUID,
                        name,
                        trackNameWS,
                        creationTime,
                        openSign,
                        state,
                        new HashSet<>(), // subscribers começa vazio
                        new HashSet<>(), // reserves começa vazio
                        new HashSet<>()  // spectators começa vazio
                );

                events.add(event);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao buscar eventos: " + e.getMessage(), e);
        }

        return events;
    }
    */

    // ================================
// 🔹 Adiciona um player a um heat em uma posição específica,
//     reajustando automaticamente os demais.
// ================================
    public synchronized boolean addPlayerToHeat(UUID uuid, int heatId, int position) {
        String shiftSql = "UPDATE fr_drivers SET position = position + 1, startPosition = startPosition + 1 " +
                "WHERE heatId = ? AND position >= ?";
        String insertSql = """
        INSERT INTO fr_drivers (uuid, heatId, position, startPosition)
        VALUES (?, ?, ?, ?)
    """;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (Statement pragma = conn.createStatement()) {
                pragma.execute("PRAGMA busy_timeout = 5000");
            }

            // 🔹 1. Empurra os jogadores que estão na mesma posição ou depois
            try (PreparedStatement shift = conn.prepareStatement(shiftSql)) {
                shift.setInt(1, heatId);
                shift.setInt(2, position);
                shift.executeUpdate();
            }

            // 🔹 2. Insere o novo jogador na posição desejada
            try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                insert.setString(1, uuid.toString());
                insert.setInt(2, heatId);
                insert.setInt(3, position);
                insert.setInt(4, position);
                insert.executeUpdate();
            }

            conn.commit();
            plugin.getLogger().info("[FormulaRacing] Player " + uuid + " adicionado ao heat " + heatId + " na posição " + position);
            return true;

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao adicionar player ao heat: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
/*
    // No seu EventsManager ou onde você controla os heats
    public Heats getActiveHeatForPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Percorre todos os heats gerenciados
        for (Heats heat : getAllHeats()) { // getAllHeats() deve retornar List<Heats>
            if (heat.getState() == Heats.HeatState.STARTED) {
                // Verifica se o jogador está registrado neste heat
                for (Driver driver : heat.getDrivers()) {
                    if (driver.getUUID().equals(uuid)) {
                        return heat; // Jogador está nesse heat ativo
                    }
                }
            }
        }

        return null; // Jogador não está em nenhum heat ativo
    }

    public List<Heats> getAllHeats() {
        List<Heats> allHeats = new ArrayList<>();

        String sql = "SELECT id, roundId, heatNumber FROM fr_heats ORDER BY id ASC";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int roundId = rs.getInt("roundId");
                int heatNumber = rs.getInt("heatNumber");

                // Aqui você precisa pegar o round correspondente
                Rounds round = getRoundById(roundId);
                if (round == null) {
                    Bukkit.getLogger().warning("[FormulaRacing] Round não encontrado para heat " + id);
                    continue;
                }

                // Cria o objeto Heat
                Heats heat = new Heats(id, round, heatNumber, this, plugin, db, ru);
                allHeats.add(heat);
            }

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[FormulaRacing] Erro ao buscar todos os heats: " + e.getMessage());
            e.printStackTrace();
        }

        return allHeats;
    }

    public synchronized List<UUID> getPlayersOnHeat(int heatId) {
            List<UUID> players = new ArrayList<>();
            String sql = "SELECT uuid FROM fr_drivers WHERE heatId=?";
            int retries = 3;

            while (retries-- > 0) {
                try (Connection conn = getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, heatId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            players.add(UUID.fromString(rs.getString("uuid")));
                        }
                    }
                    return players;
                } catch (SQLException e) {
                    if (e.getMessage().contains("database is locked")) {
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    } else {
                        plugin.getLogger().severe("[FormulaRacing] Erro ao obter players do heat " + heatId + ": " + e.getMessage());
                        break;
                    }
                }
            }
            return players;
        }



    // ================================
// 🔹 Retorna o tempo de qualificação (qualifyingTime) do jogador
// ================================
    public synchronized double getPlayerHeatTime(UUID uuid, int heatId) {
        String sql = "SELECT qualifyingTime FROM fr_drivers WHERE uuid=? AND heatId=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, heatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("qualifyingTime");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao obter tempo do player no heat: " + e.getMessage());
        }
        return -1; // Retorna -1 se não encontrado
    }

    public int getNextAvailablePosition(int heatId) {
        String sql = "SELECT position FROM fr_drivers WHERE heatId = ? ORDER BY position ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, heatId);

            ResultSet rs = ps.executeQuery();
            Set<Integer> occupied = new HashSet<>();
            while (rs.next()) {
                occupied.add(rs.getInt("position"));
            }

            // Procura a primeira posição livre começando de 1
            int pos = 1;
            while (occupied.contains(pos)) {
                pos++;
            }
            return pos;

        } catch (SQLException e) {
            e.printStackTrace();
            return -1; // erro ao buscar posição
        }
    }
    public void deleteLapsByHeatId(int heatId) {
        String sql = "DELETE FROM fr_laps WHERE heatId = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, heatId);
            stmt.executeUpdate();
            FormulaRacing.getInstance().getLogger().info("[DB] Todas as voltas do heat " + heatId + " foram deletadas.");
        } catch (SQLException e) {
            FormulaRacing.getInstance().getLogger().severe("[DB] Erro ao deletar voltas do heat " + heatId + ": " + e.getMessage());
        }
    }

    public Heats getHeatByCode(String selectedEventName, String code) {
        // ✅ Verifica formato
        if (code == null || !code.matches("^R\\d+[FQ]\\d+$")) {
            plugin.getLogger().warning("[FormulaRacing] Código inválido: " + code);
            return null;
        }

        try {
            // 🔹 Extrai round e heat number
            String roundPart = code.substring(0, code.indexOf('F') > -1 ? code.indexOf('F') : code.indexOf('Q'));
            String typePart = code.substring(roundPart.length());

            int roundNumber = Integer.parseInt(roundPart.replace("R", ""));
            int heatNumber = Integer.parseInt(typePart.substring(1));

            // 🔹 Busca o evento
            if (selectedEventName == null) {
                plugin.getLogger().warning("[FormulaRacing] Nenhum evento selecionado!");
                return null;
            }

            Optional<Events> eventOpt = getEventByName(selectedEventName);
            if (eventOpt.isEmpty()) {
                plugin.getLogger().warning("[FormulaRacing] Evento não encontrado: " + selectedEventName);
                return null;
            }

            Events event = eventOpt.get();

            // 🔹 Busca o round correspondente
            Rounds targetRound = getRoundsByEvent(event).stream()
                    .filter(r -> r.getRoundIndex() == roundNumber)
                    .findFirst()
                    .orElse(null);

            if (targetRound == null) {
                plugin.getLogger().warning("[FormulaRacing] Round R" + roundNumber + " não encontrado no evento " + event.getName());
                return null;
            }

            // 🔹 Busca o heat correspondente dentro do round
            Heats targetHeat = getHeatsByRound(targetRound).stream()
                    .filter(h -> h.getHeatNumber() == heatNumber)
                    .findFirst()
                    .orElse(null);

            if (targetHeat == null) {
                plugin.getLogger().warning("[FormulaRacing] Heat " + code + " não encontrado no round " + roundNumber);
                return null;
            }

            return targetHeat;

        } catch (Exception e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar heat por código " + code + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    public synchronized boolean removePlayerFromHeat(UUID uuid, int heatId) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            int removedPosition = -1;

            // 1️⃣ Pega posição atual do jogador
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT position FROM fr_drivers WHERE uuid=? AND heatId=?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, heatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        removedPosition = rs.getInt("position");
                    }
                }
            }

            if (removedPosition == -1) {
                plugin.getLogger().warning("[FormulaRacing] Jogador não encontrado no heat " + heatId);
                conn.rollback();
                return false;
            }

            // 2️⃣ Deleta jogador do banco
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM fr_drivers WHERE uuid=? AND heatId=?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, heatId);
                ps.executeUpdate();
            }

            // 3️⃣ Reajusta posições posteriores
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE fr_drivers SET position = position - 1, startPosition = startPosition - 1 WHERE heatId=? AND position > ?")) {
                ps.setInt(1, heatId);
                ps.setInt(2, removedPosition);
                ps.executeUpdate();
            }

            conn.commit();
            plugin.getLogger().info("[FormulaRacing] Player removido e posições reajustadas no heat " + heatId);

            // 4️⃣ Solta o barco/ArmorStand do jogador (na thread principal)
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(uuid);
                Heats heat = getHeatById(heatId);

                if (heat != null && player != null && player.isOnline()) {
                    heat.release(player); // ✅ solta o barco e ArmorStand só desse jogador
                    plugin.getLogger().info("[FormulaRacing] Visual do player " + player.getName() + " liberado do heat " + heatId);
                }
            });

            return true;

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao remover player do heat: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    public String getTrackNameByHeatId(int heatId) {
        String trackName = null;

        String sql = """
        SELECT e.trackNameWS
        FROM fr_heats h
        JOIN fr_rounds r ON h.roundId = r.id
        JOIN fr_events e ON r.eventId = e.id
        WHERE h.id = ?
    """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, heatId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    trackName = rs.getString("trackNameWS");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar trackName pelo heatId " + heatId + ": " + e.getMessage());
        }

        return trackName;
    }


*/
    public String getEventTrack(int eventId) {
        String sql = "SELECT trackNameWS FROM fr_events WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("trackNameWS");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar pista do evento (ID: " + eventId + "): " + e.getMessage());
            e.printStackTrace();
        }

        return null; // caso não encontre ou ocorra erro
    }


    public int getEventIDByName(String name) {
        String sql = "SELECT id FROM fr_events WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar ID do evento por nome: " + e.getMessage());
        }

        return -1; // não encontrou ou deu erro
    }

    // -------------------- DELETE --------------------
    public boolean deleteEventByName(String eventName) {
        String sql = "DELETE FROM fr_events WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, eventName);
            int affectedRows = ps.executeUpdate();

            return affectedRows > 0; // true se algum evento foi deletado

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[FormulaRacing] Erro ao deletar evento '" + eventName + "': " + e.getMessage());
        }

        return false; // falha
    }

    public int getHeatCountForRound(int roundId) {
        String sql = "SELECT COUNT(*) AS total FROM heats WHERE roundId = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, roundId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0; // caso der erro ou não encontrar
    }



    /*

    public List<Events> getActiveEvents() {
        List<Events> events = new ArrayList<>();
        String sql = "SELECT * FROM fr_events WHERE state != 'FINISHED'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                events.add(mapEvent(rs));
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar eventos ativos: " + e.getMessage());
        }
        return events;
    }
*/
    public Boolean createHeat(int roundId, int heatNumber) {
        String sql = "INSERT INTO fr_heats (roundId, heatNumber, state, totalLaps, totalPitstops) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {


            ps.setInt(1, roundId);
            ps.setInt(2, heatNumber);
            ps.setString(3, "SETUP");
            ps.setString(4, "5");
            ps.setString(5, "0");
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao criar heat: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
/*


    public void saveEvent(Events event) {
        String sql = "UPDATE fr_events SET name=?, trackNameWS=?, state=?, openSign=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, event.getName());
            ps.setString(2, event.getTrackNameWS());
            ps.setString(3, event.getState().name());
            ps.setBoolean(4, event.isOpenSign());
            ps.setInt(5, event.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao salvar evento: " + e.getMessage());
        }
    }

    public void deleteEvent(Events event) {

        String sqlGetHeats = """
        SELECT h.id AS heatId 
        FROM fr_heats h
        INNER JOIN fr_rounds r ON r.id = h.roundId
        WHERE r.eventId = ?
    """;

        String sqlDeleteDrivers = "DELETE FROM fr_drivers WHERE heatId = ?";
        String sqlDeleteHeats = "DELETE FROM fr_heats WHERE roundId IN (SELECT id FROM fr_rounds WHERE eventId = ?)";
        String sqlDeleteRounds = "DELETE FROM fr_rounds WHERE eventId = ?";
        String sqlDeleteEvent = "DELETE FROM fr_events WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            // 🔹 Primeiro deleta todos os drivers vinculados a heats deste evento
            try (PreparedStatement psHeats = conn.prepareStatement(sqlGetHeats);
                 PreparedStatement psDrivers = conn.prepareStatement(sqlDeleteDrivers)) {

                psHeats.setInt(1, event.getId());
                try (ResultSet rs = psHeats.executeQuery()) {
                    while (rs.next()) {
                        int heatId = rs.getInt("heatId");
                        plugin.getLogger().info("[DEBUG][deleteEvent] Deletando drivers do heatId=" + heatId);
                        psDrivers.setInt(1, heatId);
                        psDrivers.executeUpdate();
                    }
                }
            }

            // 🔹 Agora deleta todos os heats
            try (PreparedStatement ps = conn.prepareStatement(sqlDeleteHeats)) {
                ps.setInt(1, event.getId());
                int heatsDeleted = ps.executeUpdate();
                plugin.getLogger().info("[DEBUG][deleteEvent] Heats deletados: " + heatsDeleted);
            }

            // 🔹 Depois deleta todos os rounds
            try (PreparedStatement ps = conn.prepareStatement(sqlDeleteRounds)) {
                ps.setInt(1, event.getId());
                int roundsDeleted = ps.executeUpdate();
                plugin.getLogger().info("[DEBUG][deleteEvent] Rounds deletados: " + roundsDeleted);
            }

            // 🔹 Finalmente deleta o evento
            try (PreparedStatement ps = conn.prepareStatement(sqlDeleteEvent)) {
                ps.setInt(1, event.getId());
                int eventsDeleted = ps.executeUpdate();
                plugin.getLogger().info("[DEBUG][deleteEvent] Evento deletado: " + eventsDeleted);
            }

            conn.commit();
            plugin.getLogger().warning("[DEBUG][deleteEvent] ✅ Exclusão completa concluída para evento ID=" + event.getId());

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing][deleteEvent] ❌ Erro ao deletar evento ID=" + event.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }


    private Events mapEvent(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID creator = UUID.fromString(rs.getString("creatorUUID"));
        String name = rs.getString("name");
        String track = rs.getString("trackNameWS");
        long creationTime = rs.getLong("creationTime");
        boolean openSign = rs.getInt("openSign") == 1;
        Events.EventState state = Events.EventState.valueOf(rs.getString("state"));
        return new Events(this, id, creator, name, track, creationTime, openSign, state, new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    public void eventSet(int eventId, String field, Object value) {
        String sql = "UPDATE fr_events SET " + field + "=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, value);
            ps.setInt(2, eventId);
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao atualizar campo do evento: " + e.getMessage());
        }
    }
*/

        public void setSelectedEvent(UUID uuid, String eventName) {
            String sql = "UPDATE fr_players SET selectedEvent = ? WHERE uuid = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, eventName);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[FormulaRacing] Erro ao definir selectedEvent: " + e.getMessage());
            }
        }

    public String getSelectedEvent(UUID uuid) {
        String sql = "SELECT selectedEvent FROM fr_players WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("selectedEvent");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao obter selectedEvent: " + e.getMessage());
        }
        return null;
    }


    public Boolean createRound(int event, String type, String state) {
        int nextIndex = getNextRoundIndex(event);

        String sql = "INSERT INTO fr_rounds (eventId, roundIndex, type, state) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, event);
            ps.setInt(2, nextIndex);
            ps.setString(3, type);
            ps.setString(4, state);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return true;
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao criar round: " + e.getMessage());
        }
        return null;
    }

    private int getNextRoundIndex(int event) {
        String sql = "SELECT MAX(roundIndex) AS maxIndex FROM fr_rounds WHERE eventId = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, event);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int max = rs.getInt("maxIndex");
                    return max + 1;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[FormulaRacing] Erro ao obter próximo roundIndex: " + e.getMessage());
        }
        return 1;
    }

/*
    // ========================================================
    // ================ BUSCAR ROUND PELO ID ==================
    // ========================================================
    public Rounds getRoundById(int id) {
        plugin.getLogger().info("[DEBUG] Tentando buscar round com ID: " + id);

        // Primeiro, mostrar todos os rounds que existem na tabela
        plugin.getLogger().info("[DEBUG] Lista de rounds existentes na tabela fr_rounds:");
        try (Connection conn = getConnection();
             PreparedStatement psAll = conn.prepareStatement("SELECT * FROM fr_rounds");
             ResultSet rsAll = psAll.executeQuery()) {

            while (rsAll.next()) {
                int rId = rsAll.getInt("id");
                int eventId = rsAll.getInt("eventId");
                int roundIndex = rsAll.getInt("roundIndex");
                String type = rsAll.getString("type");
                String state = rsAll.getString("state");

                plugin.getLogger().info(" - ID: " + rId + " | eventId: " + eventId + " | roundIndex: " + roundIndex + " | type: " + type + " | state: " + state);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[DEBUG] Erro ao listar todos os rounds: " + e.getMessage());
        }

        // Agora tentar buscar o round específico
        String sql = "SELECT * FROM fr_rounds WHERE id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int eventId = rs.getInt("eventId");
                    Events event = getEventById(eventId).orElse(null);
                    if (event == null) {
                        plugin.getLogger().warning("[DEBUG] Evento com ID " + eventId + " não encontrado para o round " + id);
                        return null;
                    }
                    int roundIndex = rs.getInt("roundIndex");
                    String type = rs.getString("type");
                    String state = rs.getString("state");

                    plugin.getLogger().info("[DEBUG] Round encontrado: ID " + id + " | eventId: " + eventId + " | roundIndex: " + roundIndex + " | type: " + type + " | state: " + state);
                    return new Rounds(this, id, event, roundIndex, type, state);
                } else {
                    plugin.getLogger().warning("[DEBUG] Nenhum round encontrado com ID " + id);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar round: " + e.getMessage());
        }
        return null;
    }

*/
// ========================================================
// =============== BUSCAR ROUNDS DE UM EVENTO =============
// ========================================================
public List<Map<String, Object>> getRoundsByEvent(int eventId) {

    List<Map<String, Object>> rounds = new ArrayList<>();

    String sql = "SELECT * FROM fr_rounds WHERE eventId=? ORDER BY roundIndex ASC";

    try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, eventId);

        try (ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                Map<String, Object> round = new HashMap<>();

                round.put("id", rs.getInt("id"));
                round.put("roundIndex", rs.getInt("roundIndex"));
                round.put("type", rs.getString("type"));
                round.put("state", rs.getString("state"));

                rounds.add(round);
            }
        }

    } catch (SQLException e) {
        plugin.getLogger().severe("[FormulaRacing] Erro ao buscar rounds do evento: " + e.getMessage());
    }

    return rounds;
}


    /*
    // ========================================================
    // ============== BUSCAR HEATS DE UM ROUND ================
    // ========================================================
    public List<Heats> getHeatsByRound(Rounds round) {
        List<Heats> heats = new ArrayList<>();
        String sql = "SELECT * FROM fr_heats WHERE roundId=? ORDER BY heatNumber ASC";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, round.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int heatNumber = rs.getInt("heatNumber");
                    String stateStr = rs.getString("state");
                    Heats.HeatState state;
                    try {
                        state = Heats.HeatState.valueOf(stateStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        state = Heats.HeatState.SETUP;
                    }
                    Heats heat = new Heats(id, round, heatNumber, this, plugin, db, ru);
                    heat.setState(state);
                    heats.add(heat);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar heats do round: " + e.getMessage());
        }
        return heats;
    }

    public List<Rounds> getRounds() {
        List<Rounds> rounds = new ArrayList<>();
        String sql = "SELECT * FROM fr_rounds";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int eventId = rs.getInt("eventId");
                int index = rs.getInt("roundIndex");
                String type = rs.getString("type");
                String state = rs.getString("state");
                Optional<Events> event = getEventById(eventId);
                event.ifPresent(e -> rounds.add(new Rounds(this, id, e, index, type, state)));
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar rounds: " + e.getMessage());
        }
        return rounds;
    }

    // Retorna o round ativo de um evento específico
    public Optional<Rounds> getCurrentRound(Events event) {
        for (Rounds round : getRoundsByEvent(event)) {
            if ("ONGOING".equalsIgnoreCase(round.getState())) {
                return Optional.of(round);
            }
        }
        return Optional.empty();
    }

*/
    public boolean finishRound(int round) {
        // Atualiza o estado para FINISHED no banco
        String sql = "UPDATE fr_rounds SET state=? WHERE id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "FINISHED");
            ps.setInt(2, round);
            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao finalizar round: " + e.getMessage());
        }

        return false;
    }

    /*
    public List<Driver> generateRoundResults(Rounds round) {
        List<Driver> results = new ArrayList<>();
        if (round == null) return results;

        // Percorre todos os heats desse round
        for (Heats heat : getHeatsByRound(round)) {
            results.addAll(heat.getDrivers());
        }

        // Remove desclassificados
        results.removeIf(Driver::isDisqualified);

        // Ordena por:
        // 1️⃣ Finalizados primeiro
        // 2️⃣ Menor tempo de corrida (finishTime)
        // 3️⃣ Menos pitstops (em caso de empate)
        results.sort((d1, d2) -> {
            if (d1.isFinished() && !d2.isFinished()) return -1;
            if (!d1.isFinished() && d2.isFinished()) return 1;

            long time1 = d1.getFinishTime();
            long time2 = d2.getFinishTime();

            int cmp = Long.compare(time1, time2);
            if (cmp != 0) return cmp;

            return Integer.compare(d1.getPitstops(), d2.getPitstops());
        });

        Bukkit.getLogger().info("[FormulaRacing] Resultados do round '" + round + "' gerados com " + results.size() + " drivers.");
        return results;
    }




    public boolean roundSet(int roundId, String column, String value) {
        String sql = "UPDATE fr_rounds SET " + column + " = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, value);
            ps.setInt(2, roundId);

            int affectedRows = ps.executeUpdate();
            return affectedRows > 0; // true se pelo menos 1 linha foi atualizada

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao atualizar round " + roundId + ": " + e.getMessage());
            return false; // update falhou
        }
    }

*/
    // ================== HEATS ==================
    public int getNextHeatNumber(int roundId) {
        String sql = "SELECT MAX(heatNumber) AS maxHeat FROM fr_heats WHERE roundId=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("maxHeat") + 1;
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao pegar próximo heat number: " + e.getMessage());
        }
        return 1;
    }

    /*
    // Retorna um heat pelo ID
    public Heats getHeatById(int heatId) {
        String sql = "SELECT * FROM fr_heats WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, heatId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int roundId = rs.getInt("roundId");
                    Rounds round = getRoundById(roundId);
                    int heatNumber = rs.getInt("heatNumber");
                    Heats.HeatState state = Heats.HeatState.valueOf(rs.getString("state"));

                    Heats heat = new Heats(heatId, round, heatNumber, this, plugin, db, ru);
                    heat.setState(state);
                    return heat;
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar heat por ID: " + e.getMessage());
        }

        return null;
    }

    // Inicia um heat (apenas seta estado ONGOING)
    public boolean startHeat(int heatId) {
        Heats heat = getHeatById(heatId);
        if (heat != null) {
            heat.setState(Heats.HeatState.STARTED); // seta o estado
            updateHeatState(heatId, heat.getState());
            return true;
        }
        return false;
    }

    // Finaliza um heat (apenas seta estado FINISHED)
    public boolean finishHeat(int heatId) {
        Heats heat = getHeatById(heatId);
        if (heat != null) {
            heat.setState(Heats.HeatState.FINISHED); // seta o estado
            updateHeatState(heatId, heat.getState());
            return true;
        }
        return false;
    }


*/

    public Set<UUID> getSubscribers(String eventName) {
        Set<UUID> subscribers = new HashSet<>();
        String sql = """
                SELECT s.playerUUID
                FROM fr_event_subscribers s
                JOIN fr_events e ON s.eventId = e.id
                WHERE e.name = ?
                """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, eventName);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("playerUUID");
                    subscribers.add(UUID.fromString(uuidStr));
                }
            }

        } catch (SQLException e) {
            System.err.println("[FormulaRacing] Erro ao buscar inscritos do evento " + eventName + ": " + e.getMessage());
            e.printStackTrace();
        }

        return subscribers;
    }

    public boolean setEventOpenSign(String eventName, boolean openSign) {
        String sql = "UPDATE fr_events SET openSign = ? WHERE name = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, openSign ? 1 : 0);
            stmt.setString(2, eventName);

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isEventOpenSign(String eventName) {
        String sql = "SELECT openSign FROM fr_events WHERE name = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, eventName);

            var rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("openSign") == 1;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    public Set<UUID> getReserves(String eventName) {
        Set<UUID> set = new HashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT playerUUID FROM fr_event_reserves WHERE eventId = ?"
             )) {
            int eventId = getEventIDByName(eventName);
            stmt.setInt(1, eventId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                set.add(UUID.fromString(rs.getString("playerUUID")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return set;
    }

    public void addSubscriber(int eventId, UUID playerUUID) {
        String sql = "INSERT OR REPLACE INTO fr_event_subscribers (eventId, playerUUID) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao adicionar subscriber: " + e.getMessage());
        }
    }


    public void removeSubscriber(int eventId, UUID playerUUID) {
        String sql = "DELETE FROM fr_event_subscribers WHERE eventId = ? AND playerUUID = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao remover subscriber: " + e.getMessage());
        }
    }



    public void addReserve(int eventId, UUID playerUUID) {
        String sql = "INSERT OR IGNORE INTO fr_event_reserves (eventId, playerUUID) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao adicionar reserve: " + e.getMessage());
        }
    }


    public void removeReserve(int eventId, UUID playerUUID) {
        String sql = "DELETE FROM fr_event_reserves WHERE eventId = ? AND playerUUID = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao remover reserve: " + e.getMessage());
        }
    }

    public void addSpectator(int eventId, UUID playerUUID) {
        String sql = "INSERT INTO fr_event_spectators (event_id, player_uuid) VALUES (?, ?) ON DUPLICATE KEY UPDATE player_uuid = player_uuid";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao adicionar spectator: " + e.getMessage());
        }
    }

    public void removeSpectator(int eventId, UUID playerUUID) {
        String sql = "DELETE FROM fr_event_spectators WHERE event_id = ? AND player_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao remover spectator: " + e.getMessage());
        }
    }

    /*

    // DatabaseManager.java
    public void updateHeatName(int heatId, String newName) {
        String sql = "UPDATE fr_heats SET name = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newName);
            ps.setInt(2, heatId);
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao atualizar nome do heat: " + e.getMessage());
        }
    }

    public int getCurrentHeatForPlayer(UUID uuid) {
        String sql = """
        SELECT heatId 
        FROM fr_heats_players
        WHERE player_uuid = ?
        LIMIT 1
    """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt("heatId");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar heat atual: " + e.getMessage());
        }

        return -1;
    }
    public void finishLap(Player player) {
        UUID uuid = player.getUniqueId();
        int heatId = getCurrentHeatForPlayer(uuid);
        if (heatId == -1) return;

        long end = System.currentTimeMillis();

        String sqlFind = """
        SELECT id, lapStart FROM fr_laps
        WHERE uuid=? AND heatId=? AND lapEnd IS NULL
        ORDER BY id DESC LIMIT 1
    """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFind)) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, heatId);

            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return;

            int lapId = rs.getInt("id");
            long startTime = rs.getLong("lapStart");

            // Atualiza tempo
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE fr_laps SET lapEnd=? WHERE id=?")) {

                upd.setLong(1, end);
                upd.setInt(2, lapId);
                upd.executeUpdate();
            }

            long duration = end - startTime;

            player.sendMessage("§a🏁 Volta finalizada! Tempo: §e" + duration + "ms");

        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao finalizar volta: " + e.getMessage());
        }
    }
    public void startLap(Player player, int heatId, String trackNameWS) {
        UUID uuid = player.getUniqueId();

        // ================= 1) FINALIZAR VOLTA ATIVA =================
        String sqlFindLastLap = """
        SELECT id FROM fr_laps
        WHERE uuid=? AND heatId=? AND lapEnd IS NULL
        ORDER BY id DESC LIMIT 1
    """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFindLastLap)) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, heatId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int oldId = rs.getInt("id");

                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE fr_laps SET lapEnd=? WHERE id=?")) {

                    upd.setLong(1, System.currentTimeMillis());
                    upd.setInt(2, oldId);
                    upd.executeUpdate();
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao fechar volta ativa: " + e.getMessage());
        }

        // ================= 2) INICIAR NOVA VOLTA =================
        long start = System.currentTimeMillis();

        String sqlInsert = """
        INSERT INTO fr_laps (uuid, heatId, tracknameWS, lapStart, pitted)
        VALUES (?, ?, ?, ?, 0)
    """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlInsert)) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, heatId);
            ps.setString(3, trackNameWS);
            ps.setLong(4, start);

            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao iniciar volta: " + e.getMessage());
        }

        player.sendMessage("§a✅ Volta iniciada!");
    }



    // ================= ADD PIT =================
    public void addPit(Player player) {
        UUID uuid = player.getUniqueId();
        Lap lap = activeLaps.get(uuid);
        if (lap != null) {
            lap.setPitted(true);
        }
    }

    // ================= GET PITS =================
    public boolean getPits(Player player) {
        UUID uuid = player.getUniqueId();
        Lap lap = activeLaps.get(uuid);
        return lap != null && lap.isPitted();
    }
    // ================= SAVE LAP COM DEBUG =================
    private void saveLap(Lap lap) {
        UUID uuid = lap.getPlayerUUID();
        int heatId = lap.getHeatId();
        String track = lap.getTrackNameWS();
        long start = lap.getLapStart();

        plugin.getLogger().info("[DEBUG] Salvando volta inicial:");
        plugin.getLogger().info("  Player=" + uuid);
        plugin.getLogger().info("  HeatId=" + heatId);
        plugin.getLogger().info("  Track=" + track);
        plugin.getLogger().info("  LapStart=" + start);
        plugin.getLogger().info("  LapEnd=NULL (volta ainda em andamento)");
        plugin.getLogger().info("  Pitted=0 (sem pitstop)");

        String sql = """
        INSERT INTO fr_laps (uuid, heatId, tracknameWS, lapStart, lapEnd, pitted)
        VALUES (?, ?, ?, ?, NULL, 0)
    """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, heatId);
            ps.setString(3, track);
            ps.setLong(4, start);

            int rows = ps.executeUpdate();
            plugin.getLogger().info("[DEBUG] Volta salva com sucesso: Linhas afetadas=" + rows);

        } catch (SQLException e) {
            plugin.getLogger().severe("[DEBUG] ❌ Erro ao salvar volta!");
            plugin.getLogger().severe("  Player=" + uuid + " | HeatId=" + heatId + " | Track=" + track);
            e.printStackTrace();
        }
    }

    // ================= UPDATE LAP END COM DEBUG =================
    private void updateLapEnd(Lap lap) {
        UUID uuid = lap.getPlayerUUID();
        int heatId = lap.getHeatId();
        String track = lap.getTrackNameWS();
        long endTime = System.currentTimeMillis();

        plugin.getLogger().info("[DEBUG] Finalizando volta:");
        plugin.getLogger().info("  Player=" + uuid);
        plugin.getLogger().info("  HeatId=" + heatId);
        plugin.getLogger().info("  Track=" + track);
        plugin.getLogger().info("  LapEnd=" + endTime);

        String sql = """
        UPDATE fr_laps
        SET lapEnd = ?
        WHERE uuid = ? AND heatId = ? AND tracknameWS = ? AND lapEnd IS NULL
        ORDER BY lapStart DESC
        LIMIT 1
    """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, endTime);
            ps.setString(2, uuid.toString());
            ps.setInt(3, heatId);
            ps.setString(4, track);

            int rows = ps.executeUpdate();

            if (rows > 0) {
                plugin.getLogger().info("[DEBUG] ✅ Volta finalizada com sucesso! (Linhas afetadas=" + rows + ")");
            } else {
                plugin.getLogger().warning("[DEBUG] ⚠ Nenhuma volta encontrada para atualizar (possivelmente já finalizada ou não iniciada).");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[DEBUG] ❌ Erro ao atualizar lapEnd:");
            plugin.getLogger().severe("  Player=" + uuid + " | HeatId=" + heatId + " | Track=" + track);
            e.printStackTrace();
        }
    }


    public int getDriverPosition(UUID playerUUID, int heatId) {
        Map<UUID, Integer> lapCounts = new HashMap<>();

        try (Connection conn = getConnection()) {
            // Conta todas as voltas de cada jogador no heat
            String sql = "SELECT uuid, COUNT(*) AS laps FROM fr_laps WHERE heatId=? GROUP BY uuid";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, heatId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        int laps = rs.getInt("laps");
                        lapCounts.put(uuid, laps);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("[EventsManager] Erro ao calcular posição do jogador: " + e.getMessage());
            return 0; // fallback
        }

        // Ordena os jogadores pelo número de voltas (maior primeiro)
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(lapCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // A posição do jogador é o índice +1
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(playerUUID)) {
                return i + 1;
            }
        }

        // Jogador ainda não tem voltas, retorna último lugar
        return sorted.size() + 1;
    }


    public Heats getPlayerHeat(UUID playerUUID) {
        String sql = """
        SELECT h.id AS heatId, h.roundId, h.heatNumber
        FROM fr_heats h
        INNER JOIN fr_drivers d ON d.heatId = h.id
        WHERE d.uuid = ? AND h.state = 'ONGOING'
        LIMIT 1
    """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int heatId = rs.getInt("heatId");
                    int roundId = rs.getInt("roundId");
                    int heatNumber = rs.getInt("heatNumber");

                    // Pega o round pelo ID
                    Rounds round = getRoundById(roundId);
                    if (round == null) return null;

                    // Cria o objeto Heats
                    Heats heat = new Heats(
                            heatId,
                            round,
                            heatNumber,
                            this,   // EventsManager
                            plugin,
                            db,
                            ru
                    );

                    // Carrega os drivers do heat do banco
                    List<Driver> drivers = new ArrayList<>();
                    String sqlDrivers = "SELECT * FROM fr_drivers WHERE heatId = ?";
                    try (PreparedStatement psDrivers = conn.prepareStatement(sqlDrivers)) {
                        psDrivers.setInt(1, heatId);
                        try (ResultSet drs = psDrivers.executeQuery()) {
                            while (drs.next()) {
                                UUID uuid = UUID.fromString(drs.getString("uuid"));
                                String name = Bukkit.getOfflinePlayer(uuid).getName();
                                if (name == null) name = "JogadorDesconhecido";

                                Driver driver = new Driver(
                                        drs.getInt("id"),
                                        uuid,
                                        name,
                                        heat
                                );

                                driver.setPosition(drs.getInt("position"), db);
                                driver.setPitstops(drs.getInt("pitstops"), db);
                                // Start/End time
                                long start = drs.getLong("startTime");
                                long end = drs.getLong("endTime");
                                if (start > 0) driver.start(); // marca como RUNNING
                                if (end > 0) driver.finish(db); // marca como FINISHED

                                drivers.add(driver);
                            }
                        }
                    }

                    // Adiciona os drivers ao heat
                    heat.getDrivers().addAll(drivers);

                    return heat;
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao buscar heat do jogador " + playerUUID + ": " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }


    public Integer getPlayerActiveHeatId(UUID playerUUID) {
        String sql = """
        SELECT h.id AS heatId
        FROM fr_heats h
        INNER JOIN fr_drivers d ON d.heatId = h.id
        WHERE d.uuid = ?
        AND h.state IN ('LOADED', 'STARTED','STARTING')
        LIMIT 1
    """;

        plugin.getLogger().info("[DEBUG][getPlayerActiveHeatId] Iniciando busca de heat ativo para jogador: " + playerUUID);

        try (Connection conn = getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[DEBUG][getPlayerActiveHeatId] Falha ao obter conexão com o banco!");
                return null;
            }
            plugin.getLogger().info("[DEBUG][getPlayerActiveHeatId] Conexão com o banco obtida com sucesso.");

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                plugin.getLogger().info("[DEBUG][getPlayerActiveHeatId] SQL preparado:\n" + sql.replace("?", "'" + playerUUID + "'"));

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int heatId = rs.getInt("heatId");
                        plugin.getLogger().info("[DEBUG][getPlayerActiveHeatId] Jogador encontrado em heat ativo! HeatId=" + heatId);
                        return heatId;
                    } else {
                        plugin.getLogger().info("[DEBUG][getPlayerActiveHeatId] Jogador " + playerUUID + " não está em nenhum heat ativo.");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[DEBUG][getPlayerActiveHeatId] Erro SQL ao buscar heat ativo para jogador " + playerUUID + ": " + e.getMessage());
            e.printStackTrace();
        } catch (Exception ex) {
            plugin.getLogger().severe("[DEBUG][getPlayerActiveHeatId] Erro inesperado: " + ex.getMessage());
            ex.printStackTrace();
        }

        plugin.getLogger().info("[DEBUG][getPlayerActiveHeatId] Retornando null (nenhum heat ativo encontrado).");
        return null;
    }

    public List<Integer> getPlayerActiveHeatIds(UUID playerUUID) {
        String sql = """
        SELECT h.id AS heatId
        FROM fr_heats h
        INNER JOIN fr_drivers d ON d.heatId = h.id
        WHERE d.uuid = ?
        AND h.state IN ('LOADED', 'STARTED', 'STARTING')
    """;

        plugin.getLogger().info("[DEBUG][getPlayerActiveHeatIds] Buscando heats ativos do jogador: " + playerUUID);

        List<Integer> heatIds = new ArrayList<>();

        try (Connection conn = getConnection()) {
            if (conn == null) {
                plugin.getLogger().severe("[DEBUG][getPlayerActiveHeatIds] Erro ao obter conexão com o banco!");
                return heatIds;
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());

                plugin.getLogger().info("[DEBUG][getPlayerActiveHeatIds] SQL preparado:\n" +
                        sql.replace("?", "'" + playerUUID + "'")
                );

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int heatId = rs.getInt("heatId");
                        heatIds.add(heatId);
                        plugin.getLogger().info("[DEBUG][getPlayerActiveHeatIds] Heat ativo encontrado: " + heatId);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[DEBUG][getPlayerActiveHeatIds] ERRO SQL: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception ex) {
            plugin.getLogger().severe("[DEBUG][getPlayerActiveHeatIds] ERRO inesperado: " + ex.getMessage());
            ex.printStackTrace();
        }

        if (heatIds.isEmpty()) {
            plugin.getLogger().info("[DEBUG][getPlayerActiveHeatIds] Nenhum heat ativo encontrado para: " + playerUUID);
        } else {
            plugin.getLogger().info("[DEBUG][getPlayerActiveHeatIds] Total de heats ativos encontrados: " + heatIds.size());
        }

        return heatIds;
    }


    public int getLapsNumber(UUID playerUUID, int heatId, String trackNameWS) {
        String sql = """
        SELECT COUNT(*) AS totalLaps
        FROM fr_laps
        WHERE uuid=? AND heatId=? AND tracknameWS=?
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUUID.toString());
            stmt.setInt(2, heatId);
            stmt.setString(3, trackNameWS);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("totalLaps");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("[EventsManager] Erro ao obter número de voltas: " + e.getMessage());
        }

        return 0; // fallback caso não haja voltas
    }


    public List<dev.EfraGroup.formulaRacing.Heat.Lap> getLaps(UUID playerUUID, int heatId, String trackNameWS) {
        List<dev.EfraGroup.formulaRacing.Heat.Lap> laps = new ArrayList<>();
        String sql = """
        SELECT id, lapStart, lapEnd, pitted
        FROM fr_laps
        WHERE uuid=? AND heatId=? AND tracknameWS=?
        ORDER BY id ASC
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUUID.toString());
            stmt.setInt(2, heatId);
            stmt.setString(3, trackNameWS);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    long lapStart = rs.getLong("lapStart");
                    long lapEnd = rs.getLong("lapEnd");
                    boolean pitted = rs.getInt("pitted") == 1;

                    // Cria a Lap do Heat
                    laps.add(new dev.EfraGroup.formulaRacing.Heat.Lap(
                            id, playerUUID, heatId, trackNameWS, lapStart, lapEnd, pitted
                    ));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("[EventsManager] Erro ao obter laps: " + e.getMessage());
        }

        return laps;
    }




    public static class Lap {
        private final int id;
        private final UUID playerUUID;
        private final int heatId;
        private final String trackNameWS;
        private final long lapStart;
        private long lapEnd;        // não final
        private boolean pitted;     // não final

        // Construtor para voltas novas
        public Lap(UUID playerUUID, int heatId, String trackNameWS, long lapStart) {
            this.id = -1; // ainda não salvo
            this.playerUUID = playerUUID;
            this.heatId = heatId;
            this.trackNameWS = trackNameWS;
            this.lapStart = lapStart;
            this.lapEnd = 0;
            this.pitted = false;
        }

        // Construtor para voltas do banco
        public Lap(int id, UUID playerUUID, int heatId, String trackNameWS, long lapStart, long lapEnd, boolean pitted) {
            this.id = id;
            this.playerUUID = playerUUID;
            this.heatId = heatId;
            this.trackNameWS = trackNameWS;
            this.lapStart = lapStart;
            this.lapEnd = lapEnd;
            this.pitted = pitted;
        }

        // Getters
        public int getId() { return id; }
        public UUID getPlayerUUID() { return playerUUID; }
        public int getHeatId() { return heatId; }
        public String getTrackNameWS() { return trackNameWS; }
        public long getLapStart() { return lapStart; }
        public long getLapEnd() { return lapEnd; }
        public boolean isPitted() { return pitted; }

        // Setters para finalizar ou adicionar pit
        public void setLapEnd(long lapEnd) { this.lapEnd = lapEnd; }
        public void setPitted(boolean pitted) { this.pitted = pitted; }
    }
*/
}

