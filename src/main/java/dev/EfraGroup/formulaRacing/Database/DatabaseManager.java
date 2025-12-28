package dev.EfraGroup.formulaRacing.Database;

//import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FileManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
//import dev.EfraGroup.formulaRacing.Heat.Heats;
//import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.DiscordUtils;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import dev.EfraGroup.formulaRacing.Utils.WorldEditSelect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;

public class DatabaseManager {

        private static final Logger logger = Logger.getLogger("FormulaRacing");
        private final FormulaRacing plugin;
        private final FileManager fileManager;
        private Connection connection;

        public enum DatabaseType {
            SQLITE, MYSQL
        }

        private DatabaseType databaseType;
        private boolean isDatabaseInitialized = false; // Flag para garantir inicialização única

    public DatabaseManager(FormulaRacing plugin, FileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;

        // 1. Define o tipo de banco
        try {
            this.databaseType = DatabaseType.valueOf(fileManager.getDatabaseType().toUpperCase());
        } catch (IllegalArgumentException e) {
            this.databaseType = DatabaseType.SQLITE;
            logger.warning("[FormulaRacing] Tipo de banco de dados inválido na configuração. Usando SQLite por padrão.");
        }

        // 2. Abre a conexão inicial e inicializa as tabelas
        try {
            getOrConnect();
            logger.info("[FormulaRacing] Conexão inicial com o banco de dados estabelecida com sucesso (" + databaseType + ").");
        } catch (SQLException e) {
            logger.severe("[FormulaRacing] FALHA CRÍTICA ao conectar ao banco de dados: " + e.getMessage());
            // Opcional: Desativar o plugin se o banco for essencial
            // Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }
    public Connection getOrConnect() throws SQLException {
        // 1. Se a conexão já existe, vamos testar se ela ainda funciona
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.setQueryTimeout(1); // Timeout rápido para o teste
                stmt.execute("SELECT 1;");
                return connection; // Conexão está viva, retorna ela mesma
            } catch (SQLException e) {
                // Se o SELECT 1 falhar, a conexão morreu. Vamos fechar e criar outra.
                try { connection.close(); } catch (SQLException ignored) {}
            }
        }

        // 2. Se chegou aqui, ou é a primeira vez ou a conexão antiga caiu
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "database.db");
        SQLException lastException = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                // Cria a conexão ÚNICA
                this.connection = DriverManager.getConnection(
                        "jdbc:sqlite:" + dbFile.getAbsolutePath()
                );

                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL;");
                    stmt.execute("PRAGMA synchronous=NORMAL;");
                    stmt.execute("PRAGMA busy_timeout=15000;");
                }

                if (!isDatabaseInitialized) {
                    initDatabase(connection);
                    isDatabaseInitialized = true;
                }

                return connection;

            } catch (SQLException e) {
                lastException = e;
                if (e.getMessage() != null && (e.getMessage().contains("database is locked")
                        || e.getMessage().contains("SQLITE_BUSY"))) {

                    try { Thread.sleep(50L * attempt); } catch (InterruptedException ignored) {}
                    continue;
                }
                throw e;
            }
        }
        throw lastException != null ? lastException : new SQLException("Não foi possível obter conexão SQLite");
    }


    /**
     * Inicializa todas as tabelas do banco de dados.
     * @param conn A conexão ativa passada pelo método inicializador para evitar recursão.
     */
    private void initDatabase(Connection conn) {
        try (Statement stmt = conn.createStatement()) {

            // 1. Configurações Globais das Tabelas (Cameras, Tracks, Regions)
            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_cameras (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            trackNameWS TEXT DEFAULT NULL,
            worldName TEXT NOT NULL,
            x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
            yaw REAL NOT NULL, pitch REAL NOT NULL
        )""");

            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_tracks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            trackName TEXT NOT NULL,
            trackNameWS TEXT DEFAULT NULL,
            uuid TEXT NOT NULL,
            description TEXT,
            creatorName TEXT,
            creatorUUID TEXT,
            spawnPoint_x REAL, spawnPoint_y REAL, spawnPoint_z REAL,
            spawnPoint_yaw REAL, spawnPoint_pitch REAL,
            worldName TEXT, icon_name TEXT, open INTEGER DEFAULT 0
        )""");

            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_regions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            trackNameWS TEXT DEFAULT NULL,
            regionType TEXT, regionShape TEXT, worldName TEXT,
            min_x REAL, min_y REAL, min_z REAL,
            max_x REAL, max_y REAL, max_z REAL
        )""");

            // 2. Sistema de Corridas Oficiais (Events, Rounds, Heats, Drivers, Laps)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fr_events (id INTEGER PRIMARY KEY AUTOINCREMENT, creatorUUID TEXT NOT NULL, name TEXT NOT NULL, league TEXT, trackNameWS TEXT DEFAULT NULL, creationTime INTEGER DEFAULT NULL, state TEXT NOT NULL, openSign INTEGER NOT NULL DEFAULT 1)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fr_rounds (id INTEGER PRIMARY KEY AUTOINCREMENT, eventId INTEGER NOT NULL, roundIndex INTEGER NOT NULL DEFAULT 1, type TEXT DEFAULT NULL, state TEXT NOT NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fr_heats (id INTEGER PRIMARY KEY AUTOINCREMENT, roundId INTEGER NOT NULL, heatNumber INTEGER NOT NULL, state TEXT NOT NULL, startTime INTEGER DEFAULT NULL, endTime INTEGER DEFAULT NULL, fastestLapUUID TEXT, totalLaps INTEGER DEFAULT NULL, totalPitstops INTEGER DEFAULT NULL, timeLimit INTEGER DEFAULT NULL, startDelay INTEGER DEFAULT NULL, maxDrivers INTEGER DEFAULT NULL, lonely INTEGER DEFAULT NULL, canReset INTEGER DEFAULT NULL, lapReset INTEGER DEFAULT NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fr_drivers (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, heatId INTEGER NOT NULL, position INTEGER NOT NULL, startPosition INTEGER NOT NULL, startTime INTEGER, endTime INTEGER, pitstops INTEGER, qualifyingTime INTEGER)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fr_laps (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, heatId INTEGER NOT NULL, tracknameWS TEXT NOT NULL, lapStart INTEGER, lapEnd INTEGER, pitted INTEGER NOT NULL DEFAULT 0)");

            // 3. BoatUtils e Checkpoints
            stmt.executeUpdate("""
    CREATE TABLE IF NOT EXISTS fr_boatutils (
        trackNameWS TEXT PRIMARY KEY,
        stepHeight REAL DEFAULT 1.25,
        defaultSlipperiness REAL DEFAULT 0.6,
        fallDamage BOOLEAN DEFAULT TRUE,
        waterElevation BOOLEAN DEFAULT TRUE,
        airControl BOOLEAN DEFAULT TRUE,
        jumpForce REAL DEFAULT 0.36,
        gravity DOUBLE DEFAULT -0.03999999910593033,
        yawAcceleration REAL DEFAULT 1.0,
        forwardAcceleration REAL DEFAULT 0.04,
        backwardAcceleration REAL DEFAULT 0.005,
        turningForwardAcceleration REAL DEFAULT 0.005,
        allowAccelerationStacking BOOLEAN DEFAULT TRUE,
        underwaterControl BOOLEAN DEFAULT TRUE,
        surfaceWaterControl BOOLEAN DEFAULT TRUE,
        coyoteTime INT DEFAULT 0,
        waterJumping BOOLEAN DEFAULT TRUE,
        swimForce REAL DEFAULT 0.0,
        collisionMode SMALLINT DEFAULT 0,
        airStepping BOOLEAN DEFAULT FALSE,
        tenStepInterpolation BOOLEAN DEFAULT FALSE,
        collisionResolution TINYINT DEFAULT 5,
        exclusiveMode BOOLEAN DEFAULT FALSE,
        customSlipperiness TEXT DEFAULT NULL,
        perBlockSetting TEXT DEFAULT NULL
    )""");
            try {
                stmt.execute("ALTER TABLE fr_boatutils ADD COLUMN exclusiveMode BOOLEAN DEFAULT FALSE;");
            } catch (SQLException ignored) {}

            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_checkpoint (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            trackNameWS TEXT DEFAULT NULL,
            checkpointId INTEGER NOT NULL,
            worldName TEXT NOT NULL,
            min_x REAL NOT NULL, min_y REAL NOT NULL, min_z REAL NOT NULL,
            max_x REAL NOT NULL, max_y REAL NOT NULL, max_z REAL NOT NULL
        )""");

            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_checkpoint_times (
            timetrial_id INTEGER NOT NULL,
            player_uuid TEXT NOT NULL,
            trackNameWS TEXT DEFAULT NULL,
            checkpointId INTEGER NOT NULL,
            time REAL NOT NULL,
            PRIMARY KEY (player_uuid, trackNameWS, checkpointId)
        )""");
            stmt.executeUpdate("DROP TABLE IF EXISTS fr_timetrial_duels");
            stmt.executeUpdate("DROP TABLE IF EXISTS fr_timetrial_dueltimes");
            stmt.executeUpdate("DROP TABLE IF EXISTS fr_timetrial_duel_players");

            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_timetrial_duels_checkpoint_times (
            timetrial_id INTEGER NOT NULL,
            duel_id INTEGER NOT NULL,
            player_uuid TEXT NOT NULL,
            trackNameWS TEXT DEFAULT NULL,
            checkpointId INTEGER NOT NULL,
            time REAL NOT NULL,
            PRIMARY KEY (player_uuid, trackNameWS, checkpointId)
        )""");
            // 4. Sistema de DUELOS
            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_timetrial_duels (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            owner VARCHAR(36) NOT NULL,
            trackNameWS VARCHAR(64) NOT NULL,
            started_in TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            finished_in TIMESTAMP NULL,
            laps INT DEFAULT 1,
            time_limit DOUBLE DEFAULT 0,
            lonely BOOLEAN DEFAULT TRUE,
            state VARCHAR(20) DEFAULT 'WAITING',
            winner VARCHAR(36) NULL
        )""");
            try { stmt.execute("ALTER TABLE fr_players ADD COLUMN lang STRING DEFAULT NULL;"); } catch (SQLException ignored) {}

            // Migração segura para a coluna winner
            try { stmt.execute("ALTER TABLE fr_timetrial_duels ADD COLUMN winner VARCHAR(36) NULL;"); } catch (SQLException ignored) {}

            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_timetrial_dueltimes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            duel_id INTEGER NOT NULL,
            playerName VARCHAR(36) NOT NULL,
            time DOUBLE DEFAULT 0,
            checkpointsReached INT DEFAULT 0,
            finished BOOLEAN DEFAULT FALSE,
            FOREIGN KEY (duel_id) REFERENCES fr_timetrial_duels(id) ON DELETE CASCADE
        )""");

            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_timetrial_duel_players (
             duel_id INTEGER PRIMARY KEY,
             players TEXT NOT NULL,
             FOREIGN KEY (duel_id) REFERENCES fr_timetrial_duels(id) ON DELETE CASCADE
        )""");

            // 5. Sistema de Jogadores e Tempos (Time Trial)
            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_player_times (
             id INTEGER PRIMARY KEY AUTOINCREMENT,
             trackNameWS TEXT NOT NULL,
             player_uuid TEXT NOT NULL,
             player_name TEXT NOT NULL,
             bestTime REAL DEFAULT 0,
             checkpointsReached INTEGER DEFAULT 0,
             finished BOOLEAN DEFAULT FALSE,
             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""");


            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_players (
            uuid TEXT PRIMARY KEY,
            displayName TEXT NOT NULL,
            color1 TEXT, color2 TEXT,
            timetrialScoreboard INTEGER DEFAULT 1,
            baseBoat INTEGER DEFAULT 1,
            timetrial INTEGER DEFAULT 1,
            announceCheckpoint INTEGER DEFAULT 1,
            announceLap INTEGER DEFAULT 1,
            compactScoreboard INTEGER DEFAULT 0,
            music INTEGER DEFAULT 1,
            animation INTEGER DEFAULT 1,
            horn INTEGER DEFAULT 1,
            hat INTEGER DEFAULT 1,
            boots INTEGER DEFAULT 1,
            lastKnownTrackz TEXT,
            lonelyMode INTEGER DEFAULT 0,
            selectedEvent TEXT DEFAULT NULL,
            lang TEXT DEFAULT NULL
        )""");

            // 6. Outros
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fr_holograms (trackNameWS TEXT PRIMARY KEY, world TEXT, x REAL, y REAL, z REAL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fr_party (id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL UNIQUE, members TEXT NOT NULL)");
            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS fr_grid_positions (
            id INTEGER NOT NULL,
            trackNameWS TEXT DEFAULT NULL,
            positionIndex INTEGER NOT NULL,
            x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
            yaw REAL NOT NULL, pitch REAL NOT NULL, world TEXT,
            PRIMARY KEY (id, trackNameWS),
            UNIQUE (positionIndex, trackNameWS)
        )""");

            logger.info("[FormulaRacing] Tabelas do banco de dados verificadas com sucesso.");

        } catch (SQLException e) {
            logger.severe("[FormulaRacing] Erro crítico ao criar tabelas: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Define o idioma de um jogador no banco de dados.
     * @param uuid UUID do jogador.
     * @param langCode Código da língua (ex: "pt", "en", "es").
     */
    public synchronized void setPlayerLanguage(UUID uuid, String langCode) {
        String sql = "UPDATE fr_players SET lang = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, langCode);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            handleSqlError(e);
            plugin.getLogger().severe("Erro ao definir linguagem para " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Busca o idioma do jogador diretamente do banco de dados.
     * @param uuid UUID do jogador.
     * @return Código da língua ou o padrão definido na config.
     */
    public synchronized String getPlayerLanguage(UUID uuid) {
        String sql = "SELECT lang FROM fr_players WHERE uuid = ?";
        String defaultLang = "en";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String lang = rs.getString("lang");
                        return (lang != null && !lang.isEmpty()) ? lang : defaultLang;
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return defaultLang;
    }

/* =======================================================
           MÉTODOS DE DUELOS, BOAT TYPE E PLACAR
======================================================= */

    public synchronized void setPlayerBoatType(UUID uuid, int boatId) {
        String sqlInsert = "INSERT INTO fr_players (uuid, displayName, baseBoat) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET baseBoat = excluded.baseBoat";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setString(1, uuid.toString());
                String name = Bukkit.getPlayer(uuid) != null ? Bukkit.getPlayer(uuid).getName() : "Unknown";
                ps.setString(2, name);
                ps.setInt(3, boatId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public synchronized Map<UUID, Long> getBestTimesForDuel(int duelId) {
        Map<UUID, Long> times = new HashMap<>();
        String sql = "SELECT playerName as player_uuid, MIN(time) as best_time FROM fr_timetrial_dueltimes WHERE duel_id = ? GROUP BY playerName";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, duelId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        times.put(UUID.fromString(rs.getString("player_uuid")), rs.getLong("best_time"));
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return times;
    }

    public int getplayerpositiononduel(int duelId, Player player) {
        // Nota: getBestTimesForDuel já é synchronized, não precisa de lock extra aqui
        Map<UUID, Long> duelTimes = getBestTimesForDuel(duelId);
        if (duelTimes == null || duelTimes.isEmpty()) return 1;

        Long playerTime = duelTimes.get(player.getUniqueId());
        if (playerTime == null || playerTime == 0) return duelTimes.size();

        int position = 1;
        for (long time : duelTimes.values()) {
            if (time > 0 && time < playerTime) position++;
        }
        return position;
    }

    public synchronized boolean playerExists(UUID uuid) {
        String sql = "SELECT 1 FROM fr_players WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized int getPlayerBoatType(UUID playerUUID) {
        String sql = "SELECT baseBoat FROM fr_players WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("baseBoat");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return 1; // Oak Boat padrão
    }

    public synchronized boolean getTimeTrialScoreboard(UUID playerUUID) {
        String sql = "SELECT timetrialScoreboard FROM fr_players WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("timetrialScoreboard") == 1;
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return true; // Habilitado por padrão
    }
 /* =======================================================
           MÉTODOS DE JOGADORES E CHECKPOINTS
======================================================= */

    public synchronized boolean setTimeTrialScoreboard(UUID playerUUID, boolean value) {
        String sql = "UPDATE fr_players SET timetrialScoreboard = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, value ? 1 : 0);
                ps.setString(2, playerUUID.toString());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean insertPlayer(UUID uuid, String displayName) {
        String sql = """
    INSERT INTO fr_players (
        uuid, displayName, color1, color2, baseBoat, timetrial, timetrialScoreboard,
        announceCheckpoint, announceLap, compactScoreboard, music,
        animation, horn, hat, boots, lastKnownTrackz, lonelyMode
    ) VALUES (?, ?, NULL, NULL, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, NULL, 0)
    ON CONFLICT(uuid) DO UPDATE SET displayName = excluded.displayName
    """;
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, displayName);
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized List<Integer> getCheckpointIds(String trackNameWS) {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT checkpointId FROM fr_checkpoint WHERE trackNameWS = ? ORDER BY checkpointId ASC";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, trackNameWS);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) ids.add(rs.getInt("checkpointId"));
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return ids;
    }

    public synchronized boolean getTimeTrialEnabled(UUID playerUUID) {
        String sql = "SELECT timetrial FROM fr_players WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("timetrial") == 1;
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return false;
    }

    public synchronized void setTimeTrialEnabled(UUID playerUUID, boolean enabled) {
        String sql = "UPDATE fr_players SET timetrial = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, enabled ? 1 : 0);
                ps.setString(2, playerUUID.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public synchronized List<RegionData> getCheckpoints(String trackNameWS) {
        List<RegionData> checkpoints = new ArrayList<>();
        String sql = "SELECT checkpointId, worldName, min_x, min_y, min_z, max_x, max_y, max_z " +
                "FROM fr_checkpoint WHERE trackNameWS = ? ORDER BY checkpointId ASC";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String worldName = rs.getString("worldName");
                        if (Bukkit.getWorld(worldName) == null) continue;

                        checkpoints.add(new RegionData(
                                rs.getInt("checkpointId"), trackNameWS, "CHECKPOINT",
                                rs.getDouble("min_x"), rs.getDouble("min_y"), rs.getDouble("min_z"),
                                rs.getDouble("max_x"), rs.getDouble("max_y"), rs.getDouble("max_z"),
                                worldName
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return checkpoints;
    }

    public synchronized int getCheckpointCount(String trackNameWS) {
        String sql = "SELECT COUNT(*) AS total FROM fr_checkpoint WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return 0;
    }

    public synchronized boolean addCheckpoint(int checkpointId, String trackNameWS, Player player) {
        if (!WorldEditSelect.hasSelection(player)) return false;

        Location selMin = WorldEditSelect.getMin(player);
        Location selMax = WorldEditSelect.getMax(player);
        String world = selMin.getWorld().getName();

        double minX = Math.min(selMin.getX(), selMax.getX()), minY = Math.min(selMin.getY(), selMax.getY()), minZ = Math.min(selMin.getZ(), selMax.getZ());
        double maxX = Math.max(selMin.getX(), selMax.getX()), maxY = Math.max(selMin.getY(), selMax.getY()), maxZ = Math.max(selMin.getZ(), selMax.getZ());

        try {
            Connection conn = getOrConnect();
            conn.setAutoCommit(false); // Atômico: evita IDs pulados ou duplicados

            try {
                int finalId = checkpointId;
                if (finalId <= 0) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT MAX(checkpointId) FROM fr_checkpoint WHERE trackNameWS = ?")) {
                        ps.setString(1, trackNameWS);
                        try (ResultSet rs = ps.executeQuery()) {
                            finalId = rs.next() ? rs.getInt(1) + 1 : 1;
                        }
                    }
                }

                String sqlInsert = "INSERT INTO fr_checkpoint (checkpointId, trackNameWS, worldName, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                    ps.setInt(1, finalId);
                    ps.setString(2, trackNameWS);
                    ps.setString(3, world);
                    ps.setDouble(4, minX); ps.setDouble(5, minY); ps.setDouble(6, minZ);
                    ps.setDouble(7, maxX); ps.setDouble(8, maxY); ps.setDouble(9, maxZ);
                    ps.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

   /* =======================================================
          MÉTODOS DE REGIÕES, CHECKPOINTS E CRIAÇÃO
======================================================= */

    public synchronized List<RegionData> getAllRegions() {
        List<RegionData> list = new ArrayList<>();
        String sql = "SELECT id, trackNameWS, regionType, regionShape, worldName, " +
                "min_x, min_y, min_z, max_x, max_y, max_z FROM fr_regions";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RegionData(
                            rs.getInt("id"),
                            rs.getString("trackNameWS"),
                            rs.getString("regionType"),
                            rs.getDouble("min_x"), rs.getDouble("min_y"), rs.getDouble("min_z"),
                            rs.getDouble("max_x"), rs.getDouble("max_y"), rs.getDouble("max_z"),
                            rs.getString("worldName")
                    ));
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return list;
    }

    public synchronized boolean removeCheckpoint(String trackName, int checkpointId) {
        String sql = "DELETE FROM fr_checkpoint WHERE trackNameWS = ? AND checkpointId = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, trackName.replace(" ", ""));
                stmt.setInt(2, checkpointId);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean createTrack(String trackName, Location spawnLocation, String ownerName, String ownerUuid) {
        String trackNameWS = trackName.replace(" ", "");
        String sqlTrack = "INSERT INTO fr_tracks (trackName, trackNameWS, uuid, creatorName, creatorUUID, " +
                "spawnPoint_x, spawnPoint_y, spawnPoint_z, spawnPoint_yaw, spawnPoint_pitch, worldName, open) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String sqlBoatUtils = "INSERT INTO fr_boatutils (trackNameWS, stepHeight, defaultSlipperiness, fallDamage, " +
                "waterElevation, airControl, jumpForce, gravity, yawAcceleration, forwardAcceleration, " +
                "backwardAcceleration, turningForwardAcceleration, allowAccelerationStacking, underwaterControl, " +
                "surfaceWaterControl, coyoteTime, waterJumping, swimForce, collisionMode, " +
                "airStepping, tenStepInterpolation, collisionResolution) " +
                "VALUES (?, 0.0, 0.6, 1, 1, 0, 0.0, -0.04, 1.0, 0.04, 0.005, 0.005, 1, 0, 0, 0, 0, 0, 0.0, 0, 0, 0, 5)";

        try {
            Connection conn = getOrConnect();
            conn.setAutoCommit(false); // Inicia transação para garantir integridade

            try {
                try (PreparedStatement ps = conn.prepareStatement(sqlTrack)) {
                    ps.setString(1, trackName);
                    ps.setString(2, trackNameWS);
                    ps.setString(3, UUID.randomUUID().toString());
                    ps.setString(4, ownerName);
                    ps.setString(5, ownerUuid);
                    ps.setDouble(6, spawnLocation.getX());
                    ps.setDouble(7, spawnLocation.getY());
                    ps.setDouble(8, spawnLocation.getZ());
                    ps.setFloat(9, spawnLocation.getYaw());
                    ps.setFloat(10, spawnLocation.getPitch());
                    ps.setString(11, spawnLocation.getWorld().getName());
                    ps.setBoolean(12, false);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps2 = conn.prepareStatement(sqlBoatUtils)) {
                    ps2.setString(1, trackNameWS);
                    ps2.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized Map<Integer, Double> getCheckpointTimes(UUID playerUUID, String trackName) {
        Map<Integer, Double> checkpointTimes = new HashMap<>();
        String sql = "SELECT checkpointId, time FROM fr_checkpoint_times WHERE player_uuid = ? AND trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, trackName.replace(" ", ""));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        checkpointTimes.put(rs.getInt("checkpointId"), rs.getDouble("time"));
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return checkpointTimes;
    }

   /* =======================================================
          MÉTODOS DE DELEÇÃO, REGIÕES E CONSULTAS
======================================================= */

    public synchronized Location deleteTrack(String trackName) {
        Location hologramLocation = null;
        String trackNameWS = trackName.replace(" ", "");

        // Queries de deleção organizada
        String selectHologramSql = "SELECT world, x, y, z FROM fr_holograms WHERE trackNameWS = ?";
        String[] deleteSqls = {
                "DELETE FROM fr_heats WHERE roundId IN (SELECT id FROM fr_rounds WHERE eventId IN (SELECT id FROM fr_events WHERE trackNameWS = ?))",
                "DELETE FROM fr_rounds WHERE eventId IN (SELECT id FROM fr_events WHERE trackNameWS = ?)",
                "DELETE FROM fr_regions WHERE trackNameWS = ?",
                "DELETE FROM fr_player_times WHERE trackNameWS = ?",
                "DELETE FROM fr_checkpoint WHERE trackNameWS = ?",
                "DELETE FROM fr_checkpoint_times WHERE trackNameWS = ?",
                "DELETE FROM fr_holograms WHERE trackNameWS = ?",
                "DELETE FROM fr_events WHERE trackNameWS = ?",
                "DELETE FROM fr_boatutils WHERE trackNameWS = ?",
                "DELETE FROM fr_tracks WHERE trackNameWS = ?"
        };

        try {
            Connection conn = getOrConnect();
            conn.setAutoCommit(false); // Inicia transação atômica

            // 1. Buscar holograma antes de apagar
            try (PreparedStatement ps = conn.prepareStatement(selectHologramSql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        World world = Bukkit.getWorld(rs.getString("world"));
                        if (world != null) {
                            hologramLocation = new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
                        }
                    }
                }
            }

            // 2. Executar deleções em cascata
            for (String sql : deleteSqls) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, trackNameWS);
                    ps.executeUpdate();
                }
            }

            conn.commit();
            plugin.getLogger().info("Pista '" + trackName + "' deletada completamente.");
        } catch (SQLException e) {
            try { if (connection != null) connection.rollback(); } catch (SQLException ex) { }
            handleSqlError(e);
        } finally {
            try { if (connection != null) connection.setAutoCommit(true); } catch (SQLException ex) { }
        }
        return hologramLocation;
    }

    public synchronized int saveRegion(String track, Location min, Location max, String type) {
        if (track == null || min == null || max == null || type == null) return -1;
        String trackWS = track.replace(" ", "");
        String normalizedType = type.toUpperCase();

        double minX = Math.min(min.getX(), max.getX()), minY = Math.min(min.getY(), max.getY()), minZ = Math.min(min.getZ(), max.getZ());
        double maxX = Math.max(min.getX(), max.getX()), maxY = Math.max(min.getY(), max.getY()), maxZ = Math.max(min.getZ(), max.getZ());

        try {
            Connection conn = getOrConnect();
            // Remove anterior do mesmo tipo
            try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM fr_regions WHERE trackNameWS=? AND regionType=?")) {
                psDel.setString(1, trackWS);
                psDel.setString(2, normalizedType);
                psDel.executeUpdate();
            }

            String insertSql = "INSERT INTO fr_regions (trackNameWS, regionType, worldName, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psIns = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                psIns.setString(1, trackWS);
                psIns.setString(2, normalizedType);
                psIns.setString(3, min.getWorld().getName());
                psIns.setDouble(4, minX); psIns.setDouble(5, minY); psIns.setDouble(6, minZ);
                psIns.setDouble(7, maxX); psIns.setDouble(8, maxY); psIns.setDouble(9, maxZ);
                psIns.executeUpdate();
                try (ResultSet keys = psIns.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return -1;
    }

    public synchronized List<Map<String, Object>> getAllTimesOnTrackByPlayer(String trackName, String playerName, int page) {
        List<Map<String, Object>> results = new ArrayList<>();
        int limit = 15;
        int offset = (page - 1) * limit;

        String sql = "SELECT bestTime, checkpointsReached, finished, created_at FROM fr_player_times " +
                "WHERE trackNameWS = ? AND player_name = ? ORDER BY finished DESC, " +
                "CASE WHEN finished = 1 THEN bestTime END ASC, " +
                "CASE WHEN finished = 0 THEN checkpointsReached END DESC, created_at ASC LIMIT ? OFFSET ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replace(" ", ""));
                ps.setString(2, playerName);
                ps.setInt(3, limit);
                ps.setInt(4, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    int pos = offset + 1;
                    while (rs.next()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("pos", pos++);
                        entry.put("player", playerName);
                        entry.put("time", rs.getDouble("bestTime"));
                        entry.put("checkpoints", rs.getInt("checkpointsReached"));
                        entry.put("finished", rs.getBoolean("finished"));
                        entry.put("date", rs.getString("created_at"));
                        results.add(entry);
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return results;
    }

    public synchronized List<Map<String, Object>> getAllTimesOnTrack(String trackName, int page) {
        List<Map<String, Object>> results = new ArrayList<>();
        int limit = 15;
        int offset = (page - 1) * limit;

        String sql = "SELECT ranked.player_name, ranked.bestTime, ranked.checkpointsReached, ranked.finished, ranked.created_at " +
                "FROM (SELECT t.*, ROW_NUMBER() OVER (PARTITION BY t.player_name ORDER BY t.finished DESC, " +
                "CASE WHEN t.finished = 1 THEN t.bestTime END ASC, t.checkpointsReached DESC) AS rn " +
                "FROM fr_player_times t WHERE t.trackNameWS = ?) ranked WHERE ranked.rn = 1 " +
                "ORDER BY finished DESC, CASE WHEN finished = 1 THEN bestTime END ASC LIMIT ? OFFSET ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replace(" ", ""));
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    int pos = offset + 1;
                    while (rs.next()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("pos", pos++);
                        entry.put("player", rs.getString("player_name"));
                        entry.put("time", rs.getDouble("bestTime"));
                        entry.put("checkpoints", rs.getInt("checkpointsReached"));
                        entry.put("finished", rs.getBoolean("finished"));
                        entry.put("created_at", rs.getTimestamp("created_at"));
                        results.add(entry);
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return results;
    }

    public synchronized List<String> getAllTracks() {
        List<String> tracks = new ArrayList<>();
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement("SELECT trackNameWS FROM fr_tracks");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tracks.add(rs.getString("trackNameWS"));
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return tracks;
    }

   /* =======================================================
          LEADERBOARD, GRIDS, CÂMERAS E CONFIGS
======================================================= */
   public synchronized List<PlayerTime> getLeaderboard(String trackName) {
       List<PlayerTime> leaderboard = new ArrayList<>();
       String trackWS = trackName.replace(" ", "");

       try {
           int totalCheckpoints = getCheckpointCount(trackWS);

           // SQL Refatorado para garantir a ordem correta:
           // 1. Quem terminou (finished = 1) vem antes de quem não terminou (finished = 0).
           // 2. Entre os que terminaram: Menor tempo vence.
           // 3. Entre os que NÃO terminaram: Mais checkpoints vencem.
           // 4. Se empatarem em checkpoints: Menor tempo vence.
           String sql = """
            SELECT player_uuid, player_name, bestTime, checkpointsReached, finished 
            FROM (
                SELECT *, ROW_NUMBER() OVER (
                    PARTITION BY player_uuid 
                    ORDER BY 
                        finished DESC, 
                        CASE WHEN finished = 1 THEN bestTime ELSE 999999 END ASC, 
                        checkpointsReached DESC, 
                        bestTime ASC
                ) as rn 
                FROM fr_player_times 
                WHERE trackNameWS = ?
            ) t 
            WHERE rn = 1 
            ORDER BY 
                finished DESC, 
                CASE WHEN finished = 1 THEN bestTime ELSE 999999 END ASC, 
                checkpointsReached DESC, 
                bestTime ASC
            LIMIT 10
            """;

           Connection conn = getOrConnect();
           try (PreparedStatement ps = conn.prepareStatement(sql)) {
               ps.setString(1, trackWS);
               try (ResultSet rs = ps.executeQuery()) {
                   while (rs.next()) {
                       String uuidString = rs.getString("player_uuid");
                       UUID playerUuid = (uuidString != null) ? UUID.fromString(uuidString) : null;

                       leaderboard.add(new PlayerTime(
                               playerUuid,
                               rs.getString("player_name"),
                               rs.getDouble("bestTime"),
                               rs.getInt("checkpointsReached"),
                               totalCheckpoints,
                               rs.getBoolean("finished")
                       ));
                   }
               }
           }
       } catch (SQLException e) {
           handleSqlError(e);
       }
       return leaderboard;
   }

    public synchronized void setLonelyModePlayer(UUID uuid, boolean lonelyMode) {
        String sql = "UPDATE fr_players SET lonelyMode = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, lonelyMode);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public synchronized boolean getLonelyModePlayer(UUID uuid) {
        String sql = "SELECT lonelyMode FROM fr_players WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("lonelyMode");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return false;
    }

    public synchronized boolean setTrackIcon(String trackName, String iconName) {
        String sql = "UPDATE fr_tracks SET icon_name = ? WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, iconName);
                ps.setString(2, trackName.replace(" ", ""));
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean setTrackSpawn(String trackName, Location location) {
        if (trackName == null || location == null) return false;
        String sql = "UPDATE fr_tracks SET worldName = ?, spawnPoint_x = ?, spawnPoint_y = ?, spawnPoint_z = ?, " +
                "spawnPoint_yaw = ?, spawnPoint_pitch = ? WHERE LOWER(trackNameWS) = LOWER(?)";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, location.getWorld().getName());
                ps.setDouble(2, location.getX());
                ps.setDouble(3, location.getY());
                ps.setDouble(4, location.getZ());
                ps.setFloat(5, location.getYaw());
                ps.setFloat(6, location.getPitch());
                ps.setString(7, trackName.replace(" ", ""));
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean addGridPosition(String trackName, int gridId, Location location) {
        String sql = "INSERT INTO fr_grid_positions (id, trackNameWS, positionIndex, x, y, z, yaw, pitch, world) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(trackNameWS, positionIndex) DO UPDATE SET " +
                "x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch, world = excluded.world";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, gridId);
                ps.setString(2, trackName.replace(" ", ""));
                ps.setInt(3, gridId);
                ps.setDouble(4, location.getX());
                ps.setDouble(5, location.getY());
                ps.setDouble(6, location.getZ());
                ps.setDouble(7, location.getYaw());
                ps.setDouble(8, location.getPitch());
                ps.setString(9, location.getWorld().getName());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean removeGridPosition(String trackName, int gridNumber) {
        String sql = "DELETE FROM fr_grid_positions WHERE trackNameWS = ? AND positionIndex = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replace(" ", ""));
                ps.setInt(2, gridNumber);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized List<Integer> getCamerasForTrack(String trackName) {
        List<Integer> cameraIds = new ArrayList<>();
        String sql = "SELECT id FROM fr_cameras WHERE trackNameWS = ? ORDER BY id ASC";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replace(" ", ""));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) cameraIds.add(rs.getInt("id"));
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return cameraIds;
    }

    public synchronized List<Location> getAllCameras() {
        List<Location> cameras = new ArrayList<>();
        String sql = "SELECT x, y, z, yaw, pitch FROM fr_cameras";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cameras.add(new Location(
                            Bukkit.getWorld("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch")
                    ));
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return cameras;
    }

  /* =======================================================
           MÉTODOS DE GRID, DONO, STATUS E DUELOS
======================================================= */

    public synchronized Location getGridLocation(String trackName, int gridId) {
        String sql = "SELECT x, y, z, yaw, pitch, world FROM fr_grid_positions WHERE trackNameWS = ? AND positionIndex = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replace(" ", ""));
                ps.setInt(2, gridId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String worldName = rs.getString("world");
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) return null;

                        return new Location(
                                world,
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getFloat("yaw"),
                                rs.getFloat("pitch")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized String getTrackOwner(String trackNameWS) {
        String sql = "SELECT creatorName FROM fr_tracks WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("creatorName");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized boolean setTrackOwner(String trackNameWS, String newOwnerName) {
        String sql = "UPDATE fr_tracks SET creatorName = ? WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newOwnerName);
                ps.setString(2, trackNameWS);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized Map<String, TrackData> getAllTracksWithData() {
        Map<String, TrackData> trackDataMap = new HashMap<>();
        String sql = "SELECT trackNameWS, worldName, spawnPoint_x, spawnPoint_y, spawnPoint_z, " +
                "spawnPoint_yaw, spawnPoint_pitch, creatorName, icon_name FROM fr_tracks";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String trackNameWS = rs.getString("trackNameWS");
                    String worldName = rs.getString("worldName");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    Location spawnLocation = new Location(
                            world,
                            rs.getDouble("spawnPoint_x"),
                            rs.getDouble("spawnPoint_y"),
                            rs.getDouble("spawnPoint_z"),
                            rs.getFloat("spawnPoint_yaw"),
                            rs.getFloat("spawnPoint_pitch")
                    );

                    trackDataMap.put(trackNameWS, new TrackData(
                            spawnLocation,
                            worldName,
                            rs.getString("creatorName"),
                            rs.getString("icon_name") != null ? rs.getString("icon_name") : "N/A",
                            getCheckpointCount(trackNameWS)
                    ));
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return trackDataMap;
    }

    public synchronized void setTrackOpen(String trackName, boolean open) {
        String sql = "UPDATE fr_tracks SET open = ? WHERE LOWER(trackNameWS) = LOWER(?)";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, open);
                ps.setString(2, trackName.replace(" ", ""));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    /**
     * Salva os tempos de checkpoint com mecanismo de recuperação em caso de erro de Constraint.
     */
    public void saveCheckpointTimes(Connection conn, UUID playerUUID, String trackName, Integer newTimetrialId, double newTime, int newCheckpointsCount, boolean newFinished) throws SQLException {
        try {
            String trackNameWS = trackName.replace(" ", "");
            double roundedNewTime = Math.round(newTime * 1000.0) / 1000.0;

            List<TimerUtils.CheckpointData> newCheckpoints = plugin.getTimerUtils().getTempCheckpoints(playerUUID);
            if (newCheckpoints == null || newCheckpoints.isEmpty()) return;

            // --- LÓGICA DE COMPARAÇÃO (Mantida conforme original) ---
            Integer oldTimetrialId = null;
            double oldTime = Double.MAX_VALUE;
            int oldCheckpoints = 0;
            boolean oldFinished = false;

            String sqlQueryOld = "SELECT id, bestTime, checkpointsReached, finished FROM fr_player_times " +
                    "WHERE player_uuid = ? AND trackNameWS = ? ORDER BY bestTime ASC LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sqlQueryOld)) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        oldTimetrialId = rs.getInt("id");
                        oldTime = rs.getDouble("bestTime");
                        oldCheckpoints = rs.getInt("checkpointsReached");
                        oldFinished = rs.getBoolean("finished");
                    }
                }
            }

            boolean newIsBetter = (oldTimetrialId == null) ||
                    (!oldFinished && newFinished) ||
                    (oldFinished && newFinished && roundedNewTime < oldTime) ||
                    (!oldFinished && !newFinished && (newCheckpointsCount > oldCheckpoints || (newCheckpointsCount == oldCheckpoints && roundedNewTime < oldTime)));

            if (!newIsBetter) return;
            String sqlInsert = "INSERT OR REPLACE INTO fr_checkpoint_times (timetrial_id, player_uuid, checkpointId, time, trackNameWS) VALUES (?, ?, ?, ?, ?)";

            try {
                executeCheckpointBatch(conn, sqlInsert, newCheckpoints, playerUUID, trackNameWS, newTimetrialId);
            } catch (SQLException e) {
                if (e.getMessage().contains("CONSTRAINT") || e.getMessage().contains("UNIQUE")) {
                    plugin.getLogger().warning("[FormulaRacing] Conflito de PK detectado para " + playerUUID + ". Tentando recuperação forçada...");

                    String sqlForceDelete = "DELETE FROM fr_checkpoint_times WHERE player_uuid = ? AND trackNameWS = ?";
                    try (PreparedStatement psDel = conn.prepareStatement(sqlForceDelete)) {
                        psDel.setString(1, playerUUID.toString());
                        psDel.setString(2, trackNameWS);
                        psDel.executeUpdate();
                    }

                    // Tenta inserir novamente após o delete forçado
                    executeCheckpointBatch(conn, sqlInsert, newCheckpoints, playerUUID, trackNameWS, newTimetrialId);
                    plugin.getLogger().info("[FormulaRacing] Recuperação concluída com sucesso.");
                } else {
                    // Se for outro erro que não seja constraint, repassa para o log
                    throw e;
                }
            }
        } finally {
            plugin.getTimerUtils().clearTempCheckpoints(playerUUID);
        }
    }

    private void executeCheckpointBatch(Connection conn, String sql, List<TimerUtils.CheckpointData> checkpoints,
                                        UUID uuid, String track, int tid) throws SQLException {
        try (PreparedStatement psIns = conn.prepareStatement(sql)) {
            for (TimerUtils.CheckpointData cp : checkpoints) {
                psIns.setInt(1, tid);
                psIns.setString(2, uuid.toString());
                psIns.setInt(3, cp.getId());
                psIns.setDouble(4, Math.round(cp.getTime() * 1000.0) / 1000.0);
                psIns.setString(5, track);
                psIns.addBatch();
            }
            psIns.executeBatch();
        }
    }

    public void setDuelState(int duelId, String newState) {
        String sql = newState.equalsIgnoreCase("FINISHED") ?
                "UPDATE fr_timetrial_duels SET state = ?, finished_in = CURRENT_TIMESTAMP WHERE id = ?" :
                "UPDATE fr_timetrial_duels SET state = ? WHERE id = ?";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized(this) { // Sincronização mesmo em task assíncrona
                try {
                    Connection conn = getOrConnect();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, newState.toUpperCase());
                        ps.setInt(2, duelId);
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    handleSqlError(e);
                }
            }
        });
    }

    public synchronized boolean isPlayerInActiveDuel(UUID playerUUID) {
        String sql = "SELECT d.id FROM fr_timetrial_duel_players p " +
                "JOIN fr_timetrial_duels d ON p.duel_id = d.id " +
                "WHERE p.players LIKE ? AND d.state != 'FINISHED' LIMIT 1";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "%" + playerUUID.toString() + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized void saveFullTime(UUID playerUUID, String playerName, String trackName, double time, int checkpointsReached) {
        String trackNameWS = trackName.replace(" ", "");
        double roundedTime = Math.round(time / 0.05) * 0.05;

        try {
            Connection conn = getOrConnect();

            // --- LÓGICA DE RECORDE (DISCORD) ---
            String prevBestPlayer = null;
            double prevBestTime = 0.0;
            boolean isNewGlobalRecord = false;

            String globalRecordSql = "SELECT player_name, bestTime FROM fr_player_times WHERE trackNameWS = ? ORDER BY bestTime ASC LIMIT 1";

            try (PreparedStatement psCheck = conn.prepareStatement(globalRecordSql)) {
                psCheck.setString(1, trackNameWS);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) {
                        prevBestTime = rs.getDouble("bestTime");
                        prevBestPlayer = rs.getString("player_name");
                        if (roundedTime < prevBestTime) isNewGlobalRecord = true;
                    } else {
                        isNewGlobalRecord = true;
                    }
                }
            }

            if (isNewGlobalRecord) {
                DiscordUtils.sendRecordMessage(playerName, roundedTime, prevBestPlayer, prevBestTime, trackName);
            }

            // --- TRANSAÇÃO DE ESCRITA ---
            try {
                conn.setAutoCommit(false);

                String insertSql = "INSERT INTO fr_player_times (trackNameWS, player_uuid, player_name, bestTime, checkpointsReached, finished) VALUES (?, ?, ?, ?, ?, ?)";
                Integer generatedId = null;

                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, trackNameWS);
                    ps.setString(2, playerUUID.toString());
                    ps.setString(3, playerName);
                    ps.setDouble(4, roundedTime);
                    ps.setInt(5, checkpointsReached);
                    ps.setBoolean(6, true);
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) generatedId = rs.getInt(1);
                    }
                }

                if (generatedId != null) {
                    saveCheckpointTimes(conn, playerUUID, trackNameWS, generatedId, roundedTime, checkpointsReached, true);
                }

                conn.commit();
            } catch (Exception e) {
                if (conn != null) conn.rollback();
                plugin.getLogger().severe("❌ Erro na transação de FullTime: " + e.getMessage());
            } finally {
                if (conn != null && !conn.isClosed()) conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public synchronized void savePartialTime(UUID playerUUID, String playerName, String trackName, double time, int lastCheckpoint) {
        if (lastCheckpoint <= 0) return;
        String trackNameWS = trackName.replace(" ", "");
        double roundedTime = Math.round(time / 0.05) * 0.05;

        try {
            Connection conn = getOrConnect();

            try {
                conn.setAutoCommit(false);

                double prevTime = Double.MAX_VALUE;
                String sqlCheck = "SELECT bestTime FROM fr_player_times WHERE trackNameWS = ? AND player_uuid = ? AND checkpointsReached = ? AND finished = FALSE ORDER BY bestTime ASC LIMIT 1";

                try (PreparedStatement ps = conn.prepareStatement(sqlCheck)) {
                    ps.setString(1, trackNameWS);
                    ps.setString(2, playerUUID.toString());
                    ps.setInt(3, lastCheckpoint);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) prevTime = rs.getDouble("bestTime");
                    }
                }

                if (roundedTime < prevTime) {
                    Integer generatedId = null;
                    String insertSql = "INSERT INTO fr_player_times (trackNameWS, player_uuid, player_name, bestTime, checkpointsReached, finished) VALUES (?, ?, ?, ?, ?, FALSE)";

                    try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, trackNameWS);
                        ps.setString(2, playerUUID.toString());
                        ps.setString(3, playerName);
                        ps.setDouble(4, roundedTime);
                        ps.setInt(5, lastCheckpoint);
                        ps.executeUpdate();

                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) generatedId = keys.getInt(1);
                        }
                    }

                    if (generatedId != null) {
                        saveCheckpointTimes(conn, playerUUID, trackNameWS, generatedId, roundedTime, lastCheckpoint, false);
                    }
                    conn.commit();
                } else {
                    conn.rollback();
                }
            } catch (SQLException e) {
                if (conn != null) conn.rollback();
                throw e;
            } finally {
                if (conn != null && !conn.isClosed()) conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Erro ao salvar tempo parcial: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized boolean isTrackOpen(String trackName) {
        if (trackName == null || trackName.isBlank()) return false;
        String sql = "SELECT open FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?) LIMIT 1";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replace(" ", ""));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("open");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return false;
    }

    public synchronized void addCamera(int id, String trackName, Location loc) {
        String sql = "INSERT INTO fr_cameras(id, trackNameWS, x, y, z, yaw, pitch) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "trackNameWS = excluded.trackNameWS, x = excluded.x, y = excluded.y, z = excluded.z, " +
                "yaw = excluded.yaw, pitch = excluded.pitch";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setString(2, trackName);
                ps.setDouble(3, loc.getX());
                ps.setDouble(4, loc.getY());
                ps.setDouble(5, loc.getZ());
                ps.setFloat(6, loc.getYaw());
                ps.setFloat(7, loc.getPitch());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public synchronized boolean removeCamera(String trackName, int cameraId) {
        if (trackName == null || trackName.isBlank()) return false;
        String sql = "DELETE FROM fr_cameras WHERE id = ? AND trackNameWS = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, cameraId);
                ps.setString(2, trackName.replace(" ", ""));
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }
    public synchronized List<Location> getCameras(String trackName) {
        List<Location> cameras = new ArrayList<>();
        if (trackName == null || trackName.isBlank()) return cameras;

        String trackNameWS = trackName.replace(" ", "");
        String sql = "SELECT x, y, z FROM fr_cameras WHERE trackNameWS = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double x = rs.getDouble("x");
                        double y = rs.getDouble("y");
                        double z = rs.getDouble("z");
                        // Tenta pegar o mundo padrão com segurança
                        World defaultWorld = plugin.getServer().getWorlds().get(0);
                        cameras.add(new Location(defaultWorld, x, y, z));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao listar câmeras: " + e.getMessage());
            handleSqlError(e);
        }
        return cameras;
    }

    public synchronized void setSelectedEvent(UUID uuid, String eventName) {
        String sql = "UPDATE fr_players SET selectedEvent = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, eventName);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao definir selectedEvent: " + e.getMessage());
            handleSqlError(e);
        }
    }

    public synchronized Object[] getPlayerBestTime(String playerName, String trackName) {
        String sql = """
        SELECT pt.bestTime, pt.checkpointsReached, pt.finished, pt.created_at
        FROM fr_player_times pt
        WHERE pt.player_name = ?
          AND pt.trackNameWS = ?
          AND pt.finished = TRUE
        ORDER BY pt.bestTime ASC
        LIMIT 1
    """;
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerName);
                ps.setString(2, trackName.replace(" ", ""));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Object[]{
                                rs.getDouble("bestTime"),
                                rs.getInt("checkpointsReached"),
                                rs.getBoolean("finished"),
                                rs.getTimestamp("created_at")
                        };
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized List<TrackRecord> getTopTimes(String trackName) {
        List<TrackRecord> topTimes = new ArrayList<>();
        String trackWS = trackName.replace(" ", "");
        String sql = """
        SELECT player_name, bestTime, checkpointsReached, finished, created_at
        FROM (
            SELECT 
                t.player_name, t.bestTime, t.checkpointsReached, t.finished, t.created_at,
                ROW_NUMBER() OVER (
                    PARTITION BY t.player_uuid
                    ORDER BY 
                        t.finished DESC,
                        CASE WHEN t.finished = 1 THEN t.bestTime END ASC,
                        CASE WHEN t.finished = 0 THEN t.checkpointsReached END DESC,
                        t.bestTime ASC,
                        t.created_at ASC
                ) AS rn
            FROM fr_player_times t
            WHERE t.trackNameWS = ?
        ) ranked
        WHERE rn = 1
        ORDER BY 
            finished DESC,
            CASE WHEN finished = 1 THEN bestTime END ASC,
            CASE WHEN finished = 0 THEN checkpointsReached END DESC,
            bestTime ASC
        """;
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackWS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        topTimes.add(new TrackRecord(
                                rs.getString("player_name"),
                                rs.getDouble("bestTime"),
                                rs.getInt("checkpointsReached"),
                                rs.getBoolean("finished"),
                                rs.getString("created_at")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao carregar top times: " + e.getMessage());
            handleSqlError(e);
        }
        return topTimes;
    }

    public synchronized Double getBestTime(String trackName) {
        String sql = "SELECT bestTime FROM fr_player_times WHERE trackNameWS = ? ORDER BY bestTime ASC LIMIT 1";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, trackName.replace(" ", ""));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getDouble("bestTime");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized TrackData getTrackData(String trackName) {
        String sql = "SELECT trackName, worldName, spawnPoint_x, spawnPoint_y, spawnPoint_z, " +
                "spawnPoint_pitch, spawnPoint_yaw, creatorName, creatorUUID, icon_name " +
                "FROM fr_tracks WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            String trackNameWS = trackName.replace(" ", "");
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String worldName = rs.getString("worldName");
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) return null;

                        Location spawnLocation = new Location(
                                world,
                                rs.getDouble("spawnPoint_x"),
                                rs.getDouble("spawnPoint_y"),
                                rs.getDouble("spawnPoint_z"),
                                rs.getFloat("spawnPoint_yaw"),
                                rs.getFloat("spawnPoint_pitch")
                        );

                        return new TrackData(
                                spawnLocation,
                                worldName,
                                rs.getString("creatorName"),
                                rs.getString("icon_name"),
                                getCheckpointCount(trackNameWS)
                        );
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Location getTrackSpawn(String trackName) {
        if (trackName == null || trackName.isEmpty()) return null;
        String trackNameWS = trackName.replace(" ", "");
        String sql = "SELECT spawnPoint_x, spawnPoint_y, spawnPoint_z, spawnPoint_yaw, spawnPoint_pitch, worldName " +
                "FROM fr_tracks WHERE trackNameWS = ? LIMIT 1";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String worldName = rs.getString("worldName");
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) return null;

                        return new Location(
                                world,
                                rs.getDouble("spawnPoint_x"),
                                rs.getDouble("spawnPoint_y"),
                                rs.getDouble("spawnPoint_z"),
                                rs.getFloat("spawnPoint_yaw"),
                                rs.getFloat("spawnPoint_pitch")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

/* =======================================================
          MÉTODOS DE RESET E EXCLUSÃO DE TEMPOS
======================================================= */

    public synchronized void resetPlayerTimes(String playerUUID, String trackNameWS) {
        String sqlPlayerTimes = "DELETE FROM fr_player_times WHERE trackNameWS = ? AND player_uuid = ?";
        String sqlCheckpointTimes = "DELETE FROM fr_checkpoint_times WHERE trackNameWS = ? AND player_uuid = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement psPlayer = conn.prepareStatement(sqlPlayerTimes)) {
                psPlayer.setString(1, trackNameWS);
                psPlayer.setString(2, playerUUID);
                psPlayer.executeUpdate();
            }
            try (PreparedStatement psCheckpoint = conn.prepareStatement(sqlCheckpointTimes)) {
                psCheckpoint.setString(1, trackNameWS);
                psCheckpoint.setString(2, playerUUID);
                psCheckpoint.executeUpdate();
            }
            logger.info("[FormulaRacing] Tempos do jogador '" + playerUUID + "' na pista '" + trackNameWS + "' resetados.");
        } catch (SQLException e) {
            logger.severe("[FormulaRacing] Erro ao resetar tempos: " + e.getMessage());
            handleSqlError(e);
        }
    }

    public synchronized boolean deletePlayerBestTimeOnTrack(String trackName, String playerName) {
        String trackWS = trackName.replace(" ", "");

        // Usamos rowid (específico do SQLite) para deletar exatamente a melhor entrada
        String deleteSql = """
        DELETE FROM fr_player_times
        WHERE rowid IN (
            SELECT rowid
            FROM fr_player_times
            WHERE trackNameWS = ? AND player_name = ?
            ORDER BY finished DESC, checkpointsReached DESC, bestTime ASC
            LIMIT 1
        )
    """;

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, trackWS);
                ps.setString(2, playerName);
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[FormulaRacing] Erro ao deletar melhor tempo: " + e.getMessage(), e);
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean deletePlayerAllTimes(String playerName) {
        String sql = "DELETE FROM fr_player_times WHERE player_name = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerName);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean deleteAllTimes(String trackName, String player) {
        boolean hasPlayer = (player != null && !player.isEmpty());
        String sql = hasPlayer
                ? "DELETE FROM fr_player_times WHERE trackNameWS = ? AND player_name = ?"
                : "DELETE FROM fr_player_times WHERE trackNameWS = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replace(" ", ""));
                if (hasPlayer) ps.setString(2, player);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean resetAllTrackTimes(String trackNameWS) {
        String deletePlayerTimesSql = "DELETE FROM fr_player_times WHERE trackNameWS = ?";
        String sqlCheckpointTimes = "DELETE FROM fr_checkpoint_times WHERE trackNameWS = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement psPlayer = conn.prepareStatement(deletePlayerTimesSql)) {
                psPlayer.setString(1, trackNameWS);
                psPlayer.executeUpdate();
            }
            try (PreparedStatement psCheckpoint = conn.prepareStatement(sqlCheckpointTimes)) {
                psCheckpoint.setString(1, trackNameWS);
                psCheckpoint.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

  /* =======================================================
           MÉTODOS DE HOLOGRAMAS, ÍCONES E BOAT UTILS
======================================================= */

    public synchronized boolean saveHologramLocation(String trackName, Location location) {
        if (trackName == null || location == null) return false;

        String trackNameWS = trackName.replace(" ", "");
        String sqlSelect = "SELECT x, y, z, world FROM fr_holograms WHERE trackNameWS = ?";
        String sqlInsertOrUpdate = "INSERT INTO fr_holograms (trackNameWS, world, x, y, z) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z";

        try {
            Connection conn = getOrConnect();
            boolean needsUpdate = true;

            try (PreparedStatement psSelect = conn.prepareStatement(sqlSelect)) {
                psSelect.setString(1, trackNameWS);
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        double oldX = rs.getDouble("x");
                        double oldY = rs.getDouble("y");
                        double oldZ = rs.getDouble("z");
                        String oldWorld = rs.getString("world");

                        if (oldX == location.getX() && oldY == location.getY() &&
                                oldZ == location.getZ() && oldWorld.equals(location.getWorld().getName())) {
                            needsUpdate = false;
                        }
                    }
                }
            }

            if (!needsUpdate) return false;

            try (PreparedStatement psUpdate = conn.prepareStatement(sqlInsertOrUpdate)) {
                psUpdate.setString(1, trackNameWS);
                psUpdate.setString(2, location.getWorld().getName());
                psUpdate.setDouble(3, location.getX());
                psUpdate.setDouble(4, location.getY());
                psUpdate.setDouble(5, location.getZ());

                int rows = psUpdate.executeUpdate();
                if (rows > 0) {
                    logger.info("[FormulaRacing] Holograma da pista '" + trackName + "' salvo/atualizado com sucesso.");
                    return true;
                }
            }
        } catch (SQLException e) {
            logger.severe("[FormulaRacing] Erro ao salvar holograma: " + e.getMessage());
            handleSqlError(e);
        }
        return false;
    }

    public synchronized String getIcon(String trackName) {
        String trackNameNoSpaces = trackName.replace(" ", "");
        String sql = "SELECT icon_name FROM fr_tracks WHERE tracknameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameNoSpaces);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("icon_name");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar ícone: " + e.getMessage());
            handleSqlError(e);
        }
        return null;
    }

    public synchronized String getTrackIconByTrackNameWS(String trackNameWS) {
        String sql = "SELECT icon_name FROM fr_tracks WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("icon_name");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return "PAPER";
    }

    public synchronized Location getHologramLocation(String trackName) {
        if (trackName == null) return null;
        String trackNameWS = trackName.replace(" ", "");
        String sql = "SELECT world, x, y, z FROM fr_holograms WHERE trackNameWS = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        World world = Bukkit.getWorld(rs.getString("world"));
                        if (world == null) return null;
                        return new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized void setStepHigh(String trackNameWS, double stepHeight) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, stepHeight) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET stepHeight = excluded.stepHeight";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, trackNameWS);
                stmt.setDouble(2, stepHeight);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized Double getStepHigh(String trackNameWS) {
        String sql = "SELECT stepHeight FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("stepHeight");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized void setDefaultSlipperiness(String trackNameWS, double value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, defaultSlipperiness) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET defaultSlipperiness = excluded.defaultSlipperiness";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setFallDamage(String trackNameWS, boolean value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, fallDamage) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET fallDamage = excluded.fallDamage";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setWaterElevation(String trackNameWS, boolean value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, waterElevation) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET waterElevation = excluded.waterElevation";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setJumpForce(String trackNameWS, double value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, jumpForce) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET jumpForce = excluded.jumpForce";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setGravity(String trackNameWS, double value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, gravity) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET gravity = excluded.gravity";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setYawAcceleration(String trackNameWS, double value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, yawAcceleration) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET yawAcceleration = excluded.yawAcceleration";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setForwardAcceleration(String trackNameWS, double value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, forwardAcceleration) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET forwardAcceleration = excluded.forwardAcceleration";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setBackwardAcceleration(String trackNameWS, double value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, backwardAcceleration) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET backwardAcceleration = excluded.backwardAcceleration";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }
   /* =======================================================
           MÉTODOS DE ESCRITA (SETTERS) E RESET
======================================================= */

    public synchronized void setTurningForwardAcceleration(String trackNameWS, double value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, turningForwardAcceleration) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET turningForwardAcceleration = excluded.turningForwardAcceleration";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setAllowAccelerationStacking(String trackNameWS, boolean value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, allowAccelerationStacking) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET allowAccelerationStacking = excluded.allowAccelerationStacking";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setUnderwaterControl(String trackNameWS, boolean value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, underwaterControl) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET underwaterControl = excluded.underwaterControl";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setSurfaceWaterControl(String trackNameWS, boolean value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, surfaceWaterControl) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET surfaceWaterControl = excluded.surfaceWaterControl";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setCoyoteTime(String trackNameWS, int value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, coyoteTime) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET coyoteTime = excluded.coyoteTime";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setInt(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setWaterJumping(String trackNameWS, boolean value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, waterJumping) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET waterJumping = excluded.waterJumping";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setSwimForce(String trackNameWS, double value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, swimForce) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET swimForce = excluded.swimForce";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setCollisionMode(String trackNameWS, int value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, collisionMode) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET collisionMode = excluded.collisionMode";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setInt(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setAirStepping(String trackNameWS, boolean value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, airStepping) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET airStepping = excluded.airStepping";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setTenStepInterpolation(String trackNameWS, boolean value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, tenStepInterpolation) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET tenStepInterpolation = excluded.tenStepInterpolation";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void resetBoatUtilsSettings(String trackNameWS) {
        final String sql = """
    UPDATE fr_boatutils SET
        stepHeight = 1.25, defaultSlipperiness = 0.6, fallDamage = TRUE, waterElevation = TRUE,
        airControl = TRUE, jumpForce = 0.36, gravity = -0.03999999910593033, yawAcceleration = 1.0,
        forwardAcceleration = 0.04, backwardAcceleration = 0.005, turningForwardAcceleration = 0.005,
        allowAccelerationStacking = TRUE, underwaterControl = TRUE, surfaceWaterControl = TRUE,
        coyoteTime = 0, waterJumping = TRUE, swimForce = 0.0, collisionMode = 0, airStepping = FALSE,
        tenStepInterpolation = FALSE, collisionResolution = 5, customSlipperiness = NULL, perBlockSetting = NULL
    WHERE trackNameWS = ?
    """;
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
    }


  /* =======================================================
           MÉTODOS DE CONFIGURAÇÃO GERAL (BOAT UTILS)
======================================================= */

    public synchronized void replaceAllBoatUtilsSettings(
            String trackNameWS, float stepHeight, float defaultSlipperiness, boolean fallDamage,
            boolean waterElevation, boolean airControl, float jumpForce, double gravity,
            float yawAcceleration, float forwardAcceleration, float backwardAcceleration,
            float turningForwardAcceleration, boolean allowAccelerationStacking, boolean underwaterControl,
            boolean surfaceWaterControl, int coyoteTime, boolean waterJumping, float swimForce,
            int collisionMode, boolean airStepping, boolean tenStepInterpolation, int collisionResolution,
            String customSlipperiness, String perBlockSetting
    ) {
        final String sql = """
    INSERT INTO fr_boatutils (
        trackNameWS, stepHeight, defaultSlipperiness, fallDamage, waterElevation, airControl,
        jumpForce, gravity, yawAcceleration, forwardAcceleration, backwardAcceleration,
        turningForwardAcceleration, allowAccelerationStacking, underwaterControl, surfaceWaterControl,
        coyoteTime, waterJumping, swimForce, collisionMode, airStepping, tenStepInterpolation,
        collisionResolution, customSlipperiness, perBlockSetting
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(trackNameWS) DO UPDATE SET
        stepHeight = excluded.stepHeight, defaultSlipperiness = excluded.defaultSlipperiness,
        fallDamage = excluded.fallDamage, waterElevation = excluded.waterElevation,
        airControl = excluded.airControl, jumpForce = excluded.jumpForce, gravity = excluded.gravity,
        yawAcceleration = excluded.yawAcceleration, forwardAcceleration = excluded.forwardAcceleration,
        backwardAcceleration = excluded.backwardAcceleration, turningForwardAcceleration = excluded.turningForwardAcceleration,
        allowAccelerationStacking = excluded.allowAccelerationStacking, underwaterControl = excluded.underwaterControl,
        surfaceWaterControl = excluded.surfaceWaterControl, coyoteTime = excluded.coyoteTime,
        waterJumping = excluded.waterJumping, swimForce = excluded.swimForce, collisionMode = excluded.collisionMode,
        airStepping = excluded.airStepping, tenStepInterpolation = excluded.tenStepInterpolation,
        collisionResolution = excluded.collisionResolution, customSlipperiness = excluded.customSlipperiness,
        perBlockSetting = excluded.perBlockSetting;
    """;

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setString(i++, trackNameWS);
                ps.setFloat(i++, stepHeight);
                ps.setFloat(i++, defaultSlipperiness);
                ps.setBoolean(i++, fallDamage);
                ps.setBoolean(i++, waterElevation);
                ps.setBoolean(i++, airControl);
                ps.setFloat(i++, jumpForce);
                ps.setDouble(i++, gravity);
                ps.setFloat(i++, yawAcceleration);
                ps.setFloat(i++, forwardAcceleration);
                ps.setFloat(i++, backwardAcceleration);
                ps.setFloat(i++, turningForwardAcceleration);
                ps.setBoolean(i++, allowAccelerationStacking);
                ps.setBoolean(i++, underwaterControl);
                ps.setBoolean(i++, surfaceWaterControl);
                ps.setInt(i++, coyoteTime);
                ps.setBoolean(i++, waterJumping);
                ps.setFloat(i++, swimForce);
                ps.setInt(i++, collisionMode);
                ps.setBoolean(i++, airStepping);
                ps.setBoolean(i++, tenStepInterpolation);
                ps.setInt(i++, collisionResolution);
                ps.setString(i++, customSlipperiness);
                ps.setString(i++, perBlockSetting);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
    }

    public synchronized void setCollisionResolution(String trackNameWS, int value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, collisionResolution) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET collisionResolution = excluded.collisionResolution;";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setInt(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized Double getDefaultSlipperiness(String trackNameWS) {
        String sql = "SELECT defaultSlipperiness FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("defaultSlipperiness");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getFallDamage(String trackNameWS) {
        String sql = "SELECT fallDamage FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("fallDamage");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getWaterElevation(String trackNameWS) {
        String sql = "SELECT waterElevation FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("waterElevation");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Double getJumpForce(String trackNameWS) {
        String sql = "SELECT jumpForce FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("jumpForce");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Double getGravity(String trackNameWS) {
        String sql = "SELECT gravity FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("gravity");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Double getYawAcceleration(String trackNameWS) {
        String sql = "SELECT yawAcceleration FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("yawAcceleration");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Double getForwardAcceleration(String trackNameWS) {
        String sql = "SELECT forwardAcceleration FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("forwardAcceleration");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }
   /* =======================================================
           MÉTODOS DE FÍSICA E DUELO (CORRIGIDOS)
======================================================= */

    public synchronized Double getBackwardAcceleration(String trackNameWS) {
        String sql = "SELECT backwardAcceleration FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("backwardAcceleration");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Double getTurningForwardAcceleration(String trackNameWS) {
        String sql = "SELECT turningForwardAcceleration FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("turningForwardAcceleration");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getAllowAccelerationStacking(String trackNameWS) {
        String sql = "SELECT allowAccelerationStacking FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("allowAccelerationStacking");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getUnderwaterControl(String trackNameWS) {
        String sql = "SELECT underwaterControl FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("underwaterControl");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Integer getCoyoteTime(String trackNameWS) {
        String sql = "SELECT coyoteTime FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("coyoteTime");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getWaterJumping(String trackNameWS) {
        String sql = "SELECT waterJumping FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("waterJumping");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Double getSwimForce(String trackNameWS) {
        String sql = "SELECT swimForce FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("swimForce");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized List<Player> getPlayersInDuel(int duelId) {
        List<Player> players = new ArrayList<>();
        String sql = "SELECT players FROM fr_timetrial_duel_players WHERE duel_id = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, duelId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String uuidString = rs.getString("players");
                        if (uuidString != null && !uuidString.isEmpty()) {
                            String[] uuids = uuidString.split(",");
                            for (String uuidStr : uuids) {
                                try {
                                    Player p = Bukkit.getPlayer(UUID.fromString(uuidStr.trim()));
                                    if (p != null) players.add(p);
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar jogadores do duelo " + duelId + ": " + e.getMessage());
            handleSqlError(e);
        }
        return players;
    }
   /* =======================================================
           MÉTODOS DE DUELO E BOAT UTILS
======================================================= */

    public synchronized void createDuel(Player owner, List<Player> participants, String trackNameWS, int laps, int timeLimit) {
        String sqlDuel = "INSERT INTO fr_timetrial_duels (owner, trackNameWS, laps, time_limit, state) VALUES (?, ?, ?, ?, 'STARTED')";
        String sqlPlayers = "INSERT INTO fr_timetrial_duel_players (duel_id, players) VALUES (?, ?)";

        String playersString = participants.stream()
                .map(p -> p.getUniqueId().toString())
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        try {
            Connection conn = getOrConnect();
            // Transação: Desativar auto-commit
            conn.setAutoCommit(false);

            try (PreparedStatement psDuel = conn.prepareStatement(sqlDuel, Statement.RETURN_GENERATED_KEYS)) {
                psDuel.setString(1, owner.getUniqueId().toString());
                psDuel.setString(2, trackNameWS);
                psDuel.setInt(3, laps);
                psDuel.setDouble(4, (double) timeLimit);
                psDuel.executeUpdate();

                try (ResultSet rs = psDuel.getGeneratedKeys()) {
                    if (rs.next()) {
                        int duelId = rs.getInt(1);
                        try (PreparedStatement psPlayers = conn.prepareStatement(sqlPlayers)) {
                            psPlayers.setInt(1, duelId);
                            psPlayers.setString(2, playersString);
                            psPlayers.executeUpdate();
                        }
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                if (conn != null) conn.rollback();
                throw e;
            } finally {
                if (conn != null && !conn.isClosed()) conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[FormulaRacing] Erro ao registrar duelo: " + e.getMessage());
            handleSqlError(e);
        }
    }

    public synchronized Integer getCollisionMode(String trackNameWS) {
        String sql = "SELECT collisionMode FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("collisionMode");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getAirStepping(String trackNameWS) {
        String sql = "SELECT airStepping FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("airStepping");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getTenStepInterpolation(String trackNameWS) {
        String sql = "SELECT tenStepInterpolation FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("tenStepInterpolation");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Integer getCollisionResolution(String trackNameWS) {
        String sql = "SELECT collisionResolution FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("collisionResolution");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized String getPerBlockSetting(String trackNameWS) {
        String sql = "SELECT perBlockSetting FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("perBlockSetting");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getSurfaceWaterControl(String trackNameWS) {
        String sql = "SELECT surfaceWaterControl FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("surfaceWaterControl");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Integer getPlayerTimeId(String trackNameWS, UUID playerUUID, double bestTime) throws SQLException {
        String sql = "SELECT id FROM fr_player_times WHERE trackNameWS = ? AND player_uuid = ? AND bestTime = ? LIMIT 1";

        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, trackNameWS);
            stmt.setString(2, playerUUID.toString());
            stmt.setDouble(3, bestTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
        return null;
    }

    public synchronized Map<String, Object> getBoatUtilsRaw(String trackNameWS) {
        final String sql = "SELECT * FROM fr_boatutils WHERE trackNameWS = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;

                    Map<String, Object> map = new HashMap<>();
                    map.put("trackNameWS", rs.getString("trackNameWS"));
                    map.put("stepHeight", rs.getFloat("stepHeight"));
                    map.put("defaultSlipperiness", rs.getFloat("defaultSlipperiness"));
                    map.put("fallDamage", rs.getBoolean("fallDamage"));
                    map.put("waterElevation", rs.getBoolean("waterElevation"));
                    map.put("airControl", rs.getBoolean("airControl"));
                    map.put("jumpForce", rs.getFloat("jumpForce"));
                    map.put("gravity", rs.getDouble("gravity"));
                    map.put("yawAcceleration", rs.getFloat("yawAcceleration"));
                    map.put("forwardAcceleration", rs.getFloat("forwardAcceleration"));
                    map.put("backwardAcceleration", rs.getFloat("backwardAcceleration"));
                    map.put("turningForwardAcceleration", rs.getFloat("turningForwardAcceleration"));
                    map.put("allowAccelerationStacking", rs.getBoolean("allowAccelerationStacking"));
                    map.put("underwaterControl", rs.getBoolean("underwaterControl"));
                    map.put("surfaceWaterControl", rs.getBoolean("surfaceWaterControl"));
                    map.put("coyoteTime", rs.getInt("coyoteTime"));
                    map.put("waterJumping", rs.getBoolean("waterJumping"));
                    map.put("swimForce", rs.getFloat("swimForce"));
                    map.put("collisionMode", rs.getInt("collisionMode"));
                    map.put("airStepping", rs.getBoolean("airStepping"));
                    map.put("tenStepInterpolation", rs.getBoolean("tenStepInterpolation"));
                    map.put("collisionResolution", rs.getInt("collisionResolution"));
                    map.put("customSlipperiness", rs.getString("customSlipperiness"));
                    map.put("perBlockSetting", rs.getString("perBlockSetting"));

                    return map;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }




    public synchronized void setPerBlockSetting(String trackNameWS, String value) {
        String sql = "INSERT INTO fr_boatutils (trackNameWS, perBlockSetting) VALUES (?, ?) " +
                "ON CONFLICT(trackNameWS) DO UPDATE SET perBlockSetting = excluded.perBlockSetting";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setString(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized boolean trackHaveBoatUtils(String trackNameWS) {
        final String sql = "SELECT * FROM fr_boatutils WHERE trackNameWS = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;

                    boolean modified = false;

                    // Comparações de valores
                    if (rs.getFloat("stepHeight") != BoatUtilsVanillaValues.STEP_HEIGHT) modified = true;
                    if (rs.getFloat("defaultSlipperiness") != BoatUtilsVanillaValues.DEFAULT_SLIPPERINESS) modified = true;
                    if (rs.getBoolean("fallDamage") != BoatUtilsVanillaValues.FALL_DAMAGE) modified = true;
                    if (rs.getBoolean("waterElevation") != BoatUtilsVanillaValues.WATER_ELEVATION) modified = true;
                    if (rs.getBoolean("airControl") != BoatUtilsVanillaValues.AIR_CONTROL) modified = true;
                    if (rs.getFloat("jumpForce") != BoatUtilsVanillaValues.JUMP_FORCE) modified = true;
                    if (rs.getDouble("gravity") != BoatUtilsVanillaValues.GRAVITY) modified = true;
                    if (rs.getFloat("yawAcceleration") != BoatUtilsVanillaValues.YAW_ACCEL) modified = true;
                    if (rs.getFloat("forwardAcceleration") != BoatUtilsVanillaValues.FORWARD_ACCEL) modified = true;
                    if (rs.getFloat("backwardAcceleration") != BoatUtilsVanillaValues.BACKWARD_ACCEL) modified = true;
                    if (rs.getFloat("turningForwardAcceleration") != BoatUtilsVanillaValues.TURN_FORWARD_ACCEL) modified = true;
                    if (rs.getBoolean("allowAccelerationStacking") != BoatUtilsVanillaValues.ALLOW_ACCEL_STACKING) modified = true;
                    if (rs.getBoolean("underwaterControl") != BoatUtilsVanillaValues.UNDERWATER_CONTROL) modified = true;
                    if (rs.getBoolean("surfaceWaterControl") != BoatUtilsVanillaValues.SURFACE_WATER_CONTROL) modified = true;
                    if (rs.getInt("coyoteTime") != BoatUtilsVanillaValues.COYOTE_TIME) modified = true;
                    if (rs.getBoolean("waterJumping") != BoatUtilsVanillaValues.WATER_JUMPING) modified = true;
                    if (rs.getFloat("swimForce") != BoatUtilsVanillaValues.SWIM_FORCE) modified = true;
                    if (rs.getInt("collisionMode") != BoatUtilsVanillaValues.COLLISION_MODE) modified = true;
                    if (rs.getBoolean("airStepping") != BoatUtilsVanillaValues.AIR_STEPPING) modified = true;
                    if (rs.getBoolean("tenStepInterpolation") != BoatUtilsVanillaValues.TEN_STEP_INTERPOLATION) modified = true;
                    if (rs.getInt("collisionResolution") != BoatUtilsVanillaValues.COLLISION_RESOLUTION) modified = true;

                    if (!Objects.equals(rs.getString("customSlipperiness"), BoatUtilsVanillaValues.CUSTOM_SLIPPERINESS)) modified = true;
                    if (!Objects.equals(rs.getString("perBlockSetting"), BoatUtilsVanillaValues.PER_BLOCK_SETTING)) modified = true;

                    return modified;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return false;
    }
    public synchronized List<UUID> getActivePlayersInDuel(int duelId) {
        List<UUID> activePlayers = new ArrayList<>();
        String sql = "SELECT players FROM fr_timetrial_duel_players WHERE duel_id = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, duelId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String playersStr = rs.getString("players");
                        if (playersStr != null && !playersStr.isEmpty()) {
                            for (String s : playersStr.split(",")) {
                                activePlayers.add(UUID.fromString(s.trim()));
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return activePlayers;
    }


    public synchronized int getActiveDuelId(UUID uuid) {
        String uuidTarget = uuid.toString();

        // A query agora verifica se a UUID está no início, no meio (entre vírgulas) ou no fim da string
        // Isso evita que "uuid1" seja confundido com "uuid10" (se os nomes fossem simples)
        String sqlFinal = "SELECT d.id FROM fr_timetrial_duel_players p " +
                "JOIN fr_timetrial_duels d ON p.duel_id = d.id " +
                "WHERE (p.players = ? " +              // Caso seja a única UUID
                "OR p.players LIKE ? " +               // Caso esteja no início: 'uuid,%'
                "OR p.players LIKE ? " +               // Caso esteja no fim: '%,uuid'
                "OR p.players LIKE ?) " +              // Caso esteja no meio: '%,uuid,%'
                "AND d.state = 'STARTED' LIMIT 1";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sqlFinal)) {
                ps.setString(1, uuidTarget);
                ps.setString(2, uuidTarget + ",%");
                ps.setString(3, "%," + uuidTarget);
                ps.setString(4, "%," + uuidTarget + ",%");

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("§c[Erro] Falha ao buscar Duelo Ativo para " + uuidTarget + ": " + e.getMessage());
            handleSqlError(e);
        }
        return -1;
    }

    public void setDuelStateWithWinner(int duelId, String state, UUID winnerUUID) {
        String sql = "UPDATE fr_timetrial_duels SET state = ?, winner = ?, finished_in = CURRENT_TIMESTAMP WHERE id = ?";
        String winnerStr = winnerUUID.toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) {
                try {
                    Connection conn = getOrConnect();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, state);
                        ps.setString(2, winnerStr);
                        ps.setInt(3, duelId);
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    handleSqlError(e);
                }
            }
        });
    }


    public synchronized void addCustomSlipperiness(String trackNameWS, String blockId, float value) {
        Map<String, Float> map = getCustomSlipperiness(trackNameWS);
        map.put(blockId.toLowerCase(), value);
        saveCustomSlipperiness(trackNameWS, map);
    }

    public synchronized void resetCustomSlipperiness(String trackNameWS) {
        saveCustomSlipperiness(trackNameWS, new HashMap<>());
    }
    public synchronized Map<String, Float> getCustomSlipperiness(String trackNameWS) {
        String sql = "SELECT customSlipperiness FROM fr_boatutils WHERE trackNameWS = ?";
        Map<String, Float> result = new HashMap<>();

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String raw = rs.getString("customSlipperiness");
                        if (raw != null && !raw.isEmpty()) {
                            String[] entries = raw.split(",");
                            for (String entry : entries) {
                                String[] parts = entry.split(";");
                                if (parts.length == 2) {
                                    result.put(parts[0], Float.parseFloat(parts[1]));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
        return result;
    }


/* =======================================================
                    Método interno
======================================================= */

    private synchronized void saveCustomSlipperiness(String trackNameWS, Map<String, Float> map) {
        StringBuilder builder = new StringBuilder();

        for (var e : map.entrySet()) {
            builder.append(e.getKey())
                    .append(";")
                    .append(e.getValue())
                    .append(",");
        }

        if (builder.length() > 0)
            builder.setLength(builder.length() - 1);

        String finalString = builder.toString();

        String sql = """
        INSERT INTO fr_boatutils (trackNameWS, customSlipperiness)
        VALUES (?, ?)
        ON CONFLICT(trackNameWS)
        DO UPDATE SET customSlipperiness = excluded.customSlipperiness;
    """;

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setString(2, finalString);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    // ============================ SET ===============================
    public synchronized void setAirControl(String trackNameWS, boolean value) {
        String sql = """
        INSERT INTO fr_boatutils (trackNameWS, airControl)
        VALUES (?, ?)
        ON CONFLICT(trackNameWS)
        DO UPDATE SET airControl = excluded.airControl;
    """;

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, trackNameWS);
                stmt.setBoolean(2, value);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
    }

    public synchronized Boolean getAirControl(String trackNameWS) {
        String sql = "SELECT airControl FROM fr_boatutils WHERE trackNameWS = ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        boolean value = rs.getBoolean("airControl");
                        if (rs.wasNull()) return null;
                        return value;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            handleSqlError(e);
        }
        return null;
    }

    public synchronized void deleteAllParties() throws SQLException {
        try (PreparedStatement stmt = getOrConnect().prepareStatement("DELETE FROM fr_party")) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized boolean hasParty(UUID uuid) throws SQLException {
        String sql = "SELECT 1 FROM fr_party WHERE (',' || members || ',') LIKE '%,' || ? || ',%'";
        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized boolean createParty(UUID owner) throws SQLException {
        // Evita criar party duplicada
        if (hasParty(owner)) {
            return false;
        }

        String sql = "INSERT INTO fr_party (owner, members) VALUES (?, ?)";
        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, owner.toString());
            stmt.setString(2, owner.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized UUID getOwner(UUID member) throws SQLException {
        String sql = "SELECT owner FROM fr_party WHERE (',' || members || ',') LIKE '%,' || ? || ',%'";
        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, member.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString("owner")) : null;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized String getMembers(UUID owner) throws SQLException {
        String sql = "SELECT members FROM fr_party WHERE owner = ?";
        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, owner.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("members") : null;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized void addMember(UUID owner, UUID member) throws SQLException {
        String sql = "UPDATE fr_party SET members = members || ',' || ? WHERE owner = ?";
        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, member.toString());
            stmt.setString(2, owner.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized void removeMember(UUID owner, UUID member) throws SQLException {
        String sql = "UPDATE fr_party SET members = TRIM(REPLACE(',' || members || ',', ',' || ? || ',', ','), ',') WHERE owner = ?";
        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, member.toString());
            stmt.setString(2, owner.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized void disbandParty(UUID owner) throws SQLException {
        String sql = "DELETE FROM fr_party WHERE owner = ?";
        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, owner.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized Object[] getPlayerBestTimeOnDuel(UUID uuid, int duelId) {
        String sql = "SELECT time, checkpointsReached, finished FROM fr_timetrial_dueltimes " +
                "WHERE playerName = ? AND duel_id = ? " +
                "ORDER BY finished DESC, checkpointsReached DESC, time ASC LIMIT 1";

        try {
            Connection conn = this.getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setInt(2, duelId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new Object[]{
                                rs.getDouble("time"),
                                rs.getInt("checkpointsReached"),
                                rs.getBoolean("finished")
                        };
                    }
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[FormulaRacing] Erro ao buscar Duel Time: " + e.getMessage());
            handleSqlError(e);
        }
        return null;
    }

    public void saveDuelTime(int duelId, Player player, double time, int checkpoints, boolean finished) {
        String uuidStr = player.getUniqueId().toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) { // Garante que a escrita assíncrona não quebre o ponteiro
                String sql = "INSERT INTO fr_timetrial_dueltimes (duel_id, playerName, time, checkpointsReached, finished) VALUES (?, ?, ?, ?, ?)";
                try {
                    Connection conn = this.getOrConnect();
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setInt(1, duelId);
                        pstmt.setString(2, uuidStr);
                        pstmt.setDouble(3, time);
                        pstmt.setInt(4, checkpoints);
                        pstmt.setBoolean(5, finished);
                        pstmt.executeUpdate();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    handleSqlError(e);
                }
            }
        });
    }

    private void handleSqlError(SQLException e) {
        if (e.getMessage().toLowerCase().contains("closed") || e.getMessage().toLowerCase().contains("pointer")) {
            this.connection = null;
        }
    }

    // ======================== CLASSES INTERNAS ========================

    public class PlayerTime {
        private final UUID playerUUID;
        private final String playerName;
        private final double time;
        private final int checkpointsReached;
        private final int totalCheckpoints;
        private final boolean finished; // agora explícito

        public PlayerTime(UUID playerUUID, String playerName, double time, int checkpointsReached, int totalCheckpoints, boolean finished) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.time = time;
            this.checkpointsReached = checkpointsReached;
            this.totalCheckpoints = totalCheckpoints;
            this.finished = finished;
        }

        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public double getTime() { return time; }
        public int getCheckpointsReached() { return checkpointsReached; }
        public int getTotalCheckpoints() { return totalCheckpoints; }
        public boolean isFinished() { return finished || checkpointsReached >= totalCheckpoints; } // fallback automático
    }



    public static class TrackData {

        // --- Campos principais da pista ---
        private final Location spawnLocation;  // Local de spawn configurado
        private final String worldName;        // Nome do mundo onde está a pista
        private final String ownerName;        // Nome do criador/dono da pista
        private final String iconName;         // Nome do ícone (Material) usado no menu
        private final int totalCheckpoints;    // Número total de checkpoints da pista

        // --- Construtor ---
        public TrackData(Location spawnLocation, String worldName, String ownerName, String iconName, int totalCheckpoints) {
            this.spawnLocation = spawnLocation;
            this.worldName = worldName;
            this.ownerName = ownerName;
            this.iconName = iconName;
            this.totalCheckpoints = totalCheckpoints;
        }

        // --- Getters ---
        public Location getSpawnLocation() {
            return spawnLocation;
        }

        public String getWorldName() {
            return worldName;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public String getIconName() {
            return iconName;
        }

        public int getTotalCheckpoints() {
            return totalCheckpoints;
        }
    }



    public static class RegionData {
        private final int id;
        private final String trackName;
        private final String type;       // START, END, etc.
        private final String world;
        private final double minX, minY, minZ;
        private final double maxX, maxY, maxZ;

        public RegionData(int id, String trackName, String type, double minX, double minY, double minZ,
                          double maxX, double maxY, double maxZ, String world) {
            this.id = id;
            this.trackName = trackName;
            this.type = type.toUpperCase();
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
            this.world = world;
        }

        public int getId() {
            return id;
        }

        public String getTrackName() {
            return trackName;
        }

        public String getType() {
            return type;
        }

        public String getWorld() {
            return world;
        }

        public double getMinX() {
            return minX;
        }

        public double getMinY() {
            return minY;
        }

        public double getMinZ() {
            return minZ;
        }

        public double getMaxX() {
            return maxX;
        }

        public double getMaxY() {
            return maxY;
        }

        public double getMaxZ() {
            return maxZ;
        }
    }


    public static class TrackRecord {
        private final String playerName;
        private final double time;
        private final int checkpointsReached;
        private final boolean finished;
        private final String timeCreated; // 🔹 Data/hora em que o tempo foi feito

        public TrackRecord(String playerName, double time, int checkpointsReached, boolean finished, String timeCreated) {
            this.playerName = playerName;
            this.time = time;
            this.checkpointsReached = checkpointsReached;
            this.finished = finished;
            this.timeCreated = timeCreated;
        }

        public String getPlayerName() {
            return playerName;
        }

        public double getTime() {
            return time;
        }

        public int getCheckpointsReached() {
            return checkpointsReached;
        }

        public boolean isFinished() {
            return finished;
        }

        public String getTimeCreated() {
            return timeCreated;
        }
    }

    public class BoatUtilsVanillaValues {

        public static final float STEP_HEIGHT = 0f;
        public static final float DEFAULT_SLIPPERINESS = 0.6f;
        public static final boolean FALL_DAMAGE = true;
        public static final boolean WATER_ELEVATION = true;
        public static final boolean AIR_CONTROL = false;
        public static final float JUMP_FORCE = 0f;
        public static final double GRAVITY = -0.03999999910593033;
        public static final float YAW_ACCEL = 1.0f;
        public static final float FORWARD_ACCEL = 0.04f;
        public static final float BACKWARD_ACCEL = 0.005f;
        public static final float TURN_FORWARD_ACCEL = 0.005f;
        public static final boolean ALLOW_ACCEL_STACKING = true;
        public static final boolean UNDERWATER_CONTROL = false;
        public static final boolean SURFACE_WATER_CONTROL = false;
        public static final int COYOTE_TIME = 0;
        public static final boolean WATER_JUMPING = false;
        public static final float SWIM_FORCE = 0f;
        public static final int COLLISION_MODE = 0;
        public static final boolean AIR_STEPPING = false;
        public static final boolean TEN_STEP_INTERPOLATION = false;
        public static final int COLLISION_RESOLUTION = 5;

        public static final String CUSTOM_SLIPPERINESS = null;
        public static final String PER_BLOCK_SETTING = null;
    }


}


