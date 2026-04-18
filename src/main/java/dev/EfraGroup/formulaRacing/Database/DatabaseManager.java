package dev.EfraGroup.formulaRacing.Database;

import dev.EfraGroup.formulaRacing.FileManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.DiscordUtils;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import dev.EfraGroup.formulaRacing.Utils.WorldEditSelect;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class DatabaseManager {

    private final FormulaRacing plugin;
    private Connection connection;
    private final Map<String, List<RegionData>> checkpointsCache =
        new ConcurrentHashMap<>();
    private final Map<String, Integer> checkpointCountCache =
        new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> ttScoreboardCache =
        new ConcurrentHashMap<>();
    private final Map<UUID, String> languageCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> ttEnabledCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> compactScoreboardCache =
        new ConcurrentHashMap<>();
    private final Map<UUID, String> playerColor1Cache =
        new ConcurrentHashMap<>();
    private final Map<UUID, String> playerColor2Cache =
        new ConcurrentHashMap<>();

    public enum DatabaseType {
        SQLITE,
        MYSQL,
    }

    private DatabaseType databaseType;
    private boolean isDatabaseInitialized = false; // Flag para garantir inicialização única

    public DatabaseManager(FormulaRacing plugin, FileManager fileManager) {
        this.plugin = plugin;

        // 1. Define o tipo de banco
        try {
            this.databaseType = DatabaseType.valueOf(
                fileManager.getDatabaseType().toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            this.databaseType = DatabaseType.SQLITE;
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Tipo de banco de dados inválido na configuração. Usando SQLite por padrão."
                );
        }

        // 2. Abre a conexão inicial e inicializa as tabelas
        try {
            getOrConnect();
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Conexão inicial com o banco de dados estabelecida com sucesso (" +
                        databaseType +
                        ")."
                );
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] FALHA CRÍTICA ao conectar ao banco de dados: " +
                        e.getMessage()
                );
            // Opcional: Desativar o plugin se o banco for essencial
            // Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    public synchronized Connection getOrConnect() throws SQLException {
        // 1. Se a conexão já existe, vamos testar se ela ainda funciona
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.setQueryTimeout(1); // Timeout rápido para o teste
                stmt.execute("SELECT 1;");
                return connection; // Conexão está viva, retorna ela mesma
            } catch (SQLException e) {
                // Se o SELECT 1 falhar, a conexão morreu. Vamos fechar e criar outra.
                try {
                    connection.close();
                } catch (SQLException ignored) {}
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
                if (
                    e.getMessage() != null &&
                    (e.getMessage().contains("database is locked") ||
                        e.getMessage().contains("SQLITE_BUSY"))
                ) {
                    try {
                        Thread.sleep(50L * attempt);
                    } catch (InterruptedException ignored) {}
                    continue;
                }
                throw e;
            }
        }
        throw lastException != null
            ? lastException
            : new SQLException("Não foi possível obter conexão SQLite");
    }

    public CompletableFuture<Boolean> savePitStopStartRegion(
        String trackName,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        return CompletableFuture.supplyAsync(() -> {
            // Usamos UPDATE para preencher apenas as colunas de START onde a pista coincide
            String sql =
                "UPDATE fr_pit_stops SET startMinX = ?, startMinY = ?, startMinZ = ?, " +
                "startMaxX = ?, startMaxY = ?, startMaxZ = ? WHERE trackNameWS = ?";

            try (
                Connection conn = this.getOrConnect();
                PreparedStatement ps = conn.prepareStatement(sql)
            ) {
                ps.setDouble(1, minX);
                ps.setDouble(2, minY);
                ps.setDouble(3, minZ);
                ps.setDouble(4, maxX);
                ps.setDouble(5, maxY);
                ps.setDouble(6, maxZ);
                ps.setString(7, trackName);

                int affectedRows = ps.executeUpdate();

                // Se nenhuma linha foi afetada, significa que a pista ainda não tem registro de Pit
                if (affectedRows == 0) {
                    this.plugin.getDebugManager().logDatabaseOperation(
                        "Aviso: Nenhuma pista encontrada com o nome " +
                            trackName +
                            " para salvar o Start."
                    );
                    return false;
                }

                return true;
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation(
                    "Erro ao salvar região Start no banco: " + e.getMessage()
                );
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Atualiza as coordenadas de uma região de DRS no banco de dados.
     * @param trackName Nome da pista.
     * @param type Tipo da região (ex: "detection" ou "activation").
     * @param min Localização mínima (ponto 1).
     * @param max Localização máxima (ponto 2).
     * @return true se a operação foi bem sucedida.
     */
    public boolean updateDrsRegion(
        String trackName,
        String type,
        Location min,
        Location max
    ) {
        // Definimos as colunas dinamicamente com base no 'type'
        String columnMin = "drs_" + type.toLowerCase() + "_min";
        String columnMax = "drs_" + type.toLowerCase() + "_max";

        String sql =
            "UPDATE fr_tracks SET " +
            columnMin +
            " = ?, " +
            columnMax +
            " = ? WHERE track_name = ?;";

        try (
            Connection conn = getOrConnect();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, serializeLocation(min));
            ps.setString(2, serializeLocation(max));
            ps.setString(3, trackName);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            Bukkit.getLogger().severe(
                "[FormulaRacing] Erro ao atualizar DRS (" +
                    type +
                    "): " +
                    e.getMessage()
            );
            return false;
        }
    }
    public boolean saveDrsZone(
            String trackName,
            String type, // "detect", "drs", "end"
            Location min,
            Location max
    ) {
        // Agora sempre usamos INSERT. O 'id' (AUTOINCREMENT) cuidará de diferenciar as zonas.
        String sql = "INSERT INTO fr_drs (trackNameWS, world, type, " +
                "regionMinX, regionMinY, regionMinZ, " +
                "regionMaxX, regionMaxY, regionMaxZ) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, trackName.toLowerCase());
            ps.setString(2, min.getWorld().getName());
            ps.setString(3, type.toLowerCase());
            ps.setDouble(4, min.getX());
            ps.setDouble(5, min.getY());
            ps.setDouble(6, min.getZ());
            ps.setDouble(7, max.getX());
            ps.setDouble(8, max.getY());
            ps.setDouble(9, max.getZ());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[FormulaRacing] Erro ao adicionar nova zona de DRS (" + type + "): " + e.getMessage());
            return false;
        }
    }
    /**
     * Remove todas as zonas de DRS vinculadas a uma pista específica.
     */
    public boolean clearAllDrsRegions(String trackName) {
        // Usamos DELETE para remover todas as linhas que compartilham o nome da pista
        String sql = "DELETE FROM fr_drs WHERE trackNameWS = ?;";

        try (
                Connection conn = getOrConnect();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, trackName);

            // Em vez de verificar apenas > 0, executamos a ação.
            // Se não houver nada para deletar, não é necessariamente um "erro".
            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            Bukkit.getLogger().severe(
                    "[FormulaRacing] Erro ao deletar regiões de DRS da pista " +
                            trackName + ": " + e.getMessage()
            );
            return false;
        }
    }
    /**
     * Método auxiliar para transformar Location em String (Mundo,X,Y,Z)
     */
    private String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return (
            loc.getWorld().getName() +
            "," +
            loc.getBlockX() +
            "," +
            loc.getBlockY() +
            "," +
            loc.getBlockZ()
        );
    }

    /**
     * Salva as coordenadas da região de START (onde o carro para) no banco de dados.
     */
    public CompletableFuture<Boolean> savePitStopStart(
        String trackName,
        Location min,
        Location max
    ) {
        return CompletableFuture.supplyAsync(() -> {
            String sql =
                "UPDATE fr_pit_stops SET " +
                "startMinX = ?, startMinY = ?, startMinZ = ?, " +
                "startMaxX = ?, startMaxY = ?, startMaxZ = ? " +
                "WHERE trackNameWS = ?";

            try (
                Connection conn = this.getOrConnect();
                PreparedStatement ps = conn.prepareStatement(sql)
            ) {
                // Coordenadas Mínimas
                ps.setDouble(1, min.getX());
                ps.setDouble(2, min.getY());
                ps.setDouble(3, min.getZ());

                // Coordenadas Máximas
                ps.setDouble(4, max.getX());
                ps.setDouble(5, max.getY());
                ps.setDouble(6, max.getZ());

                // Identificador da Pista
                ps.setString(7, trackName);

                int affectedRows = ps.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                this.plugin.getDebugManager().logDatabaseOperation(
                    "Erro ao salvar região Start: " + e.getMessage()
                );
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Inicializa todas as tabelas do banco de dados.
     * @param conn A conexão ativa passada pelo método inicializador para evitar recursão.
     */
    private void initDatabase(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // 1. Configurações Globais das Tabelas (Cameras, Tracks, Regions)
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_cameras (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trackNameWS TEXT DEFAULT NULL,
                    worldName TEXT NOT NULL,
                    x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                    yaw REAL NOT NULL, pitch REAL NOT NULL
                )"""
            );

            stmt.executeUpdate(
                """
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
                    finishAll_x REAL, finishAll_y REAL, finishAll_z REAL,
                    finishAll_yaw REAL, finishAll_pitch REAL,
                    worldName TEXT, icon_name TEXT, open INTEGER DEFAULT 0
                )"""
            );

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_track_finish_positions (
                    trackNameWS TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                    yaw REAL NOT NULL, pitch REAL NOT NULL, worldName TEXT,
                    PRIMARY KEY (trackNameWS, position)
                )"""
            );

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_regions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trackNameWS TEXT DEFAULT NULL,
                    regionType TEXT, regionShape TEXT, worldName TEXT,
                    min_x REAL, min_y REAL, min_z REAL,
                    max_x REAL, max_y REAL, max_z REAL
                )"""
            );

            // 2. Sistema de Corridas Oficiais (Events, Rounds, Heats, Drivers, Laps)
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS fr_events (id INTEGER PRIMARY KEY AUTOINCREMENT, creatorUUID TEXT NOT NULL, name TEXT NOT NULL, league TEXT, trackNameWS TEXT DEFAULT NULL, creationTime INTEGER DEFAULT NULL, state TEXT NOT NULL, openSign INTEGER NOT NULL DEFAULT 1)"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS fr_rounds (id INTEGER PRIMARY KEY AUTOINCREMENT, eventId INTEGER NOT NULL, roundIndex INTEGER NOT NULL DEFAULT 1, type TEXT DEFAULT NULL, state TEXT NOT NULL)"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS fr_heats (id INTEGER PRIMARY KEY AUTOINCREMENT, roundId INTEGER NOT NULL, heatNumber INTEGER NOT NULL, state TEXT NOT NULL, startTime INTEGER DEFAULT NULL, endTime INTEGER DEFAULT NULL, fastestLapUUID TEXT, totalLaps INTEGER DEFAULT NULL, totalPitstops INTEGER DEFAULT NULL, timeLimit INTEGER DEFAULT NULL, startDelay INTEGER DEFAULT NULL, maxDrivers INTEGER DEFAULT NULL, lonely INTEGER DEFAULT NULL, canReset INTEGER DEFAULT NULL, lapReset INTEGER DEFAULT NULL, colisao TEXT DEFAULT 'DISABLED', drs INTEGER DEFAULT 0, driverswap INTEGER DEFAULT 0, drsdowntime REAL DEFAULT 0.0, drsdownpower REAL DEFAULT 0.0, reversegrid INTEGER DEFAULT 0, ghostingdelta REAL DEFAULT 0.0, pushtopass INTEGER DEFAULT 0, pushtopasspower REAL DEFAULT 0.0, realistc INTEGER DEFAULT 0)"
            );
            // Idempotent ALTER TABLE columns for existing databases
            String[] heatAlterColumns = {
                "ALTER TABLE fr_heats ADD COLUMN colisao TEXT DEFAULT 'DISABLED'",
                "ALTER TABLE fr_heats ADD COLUMN drs INTEGER DEFAULT 0",
                "ALTER TABLE fr_heats ADD COLUMN driverswap INTEGER DEFAULT 0",
                "ALTER TABLE fr_heats ADD COLUMN drsdowntime REAL DEFAULT 0.0",
                "ALTER TABLE fr_heats ADD COLUMN drsdownpower REAL DEFAULT 0.0",
                "ALTER TABLE fr_heats ADD COLUMN reversegrid INTEGER DEFAULT 0",
                "ALTER TABLE fr_heats ADD COLUMN ghostingdelta REAL DEFAULT 0.0",
                "ALTER TABLE fr_heats ADD COLUMN pushtopass INTEGER DEFAULT 0",
                "ALTER TABLE fr_heats ADD COLUMN pushtopasspower REAL DEFAULT 0.0",
                "ALTER TABLE fr_heats ADD COLUMN realistc INTEGER DEFAULT 0",
            };
            for (String alterSql : heatAlterColumns) {
                try {
                    stmt.executeUpdate(alterSql);
                } catch (SQLException ignored) {}
            }
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS fr_drivers (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, heatId INTEGER NOT NULL, position INTEGER NOT NULL, startPosition INTEGER NOT NULL, startTime INTEGER, endTime INTEGER, pitstops INTEGER, qualifyingTime INTEGER)"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS fr_laps (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, heatId INTEGER NOT NULL, tracknameWS TEXT NOT NULL, lapStart INTEGER, lapEnd INTEGER, pitted INTEGER NOT NULL DEFAULT 0)"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS fr_event_signups (id INTEGER PRIMARY KEY AUTOINCREMENT, eventId INTEGER NOT NULL, uuid TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'SUBSCRIBER', subscriptionTime INTEGER NOT NULL, confirmed INTEGER NOT NULL DEFAULT 0)"
            );
            try {
                stmt.executeUpdate(
                    "ALTER TABLE fr_event_signups ADD COLUMN type TEXT NOT NULL DEFAULT 'SUBSCRIBER'"
                );
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate(
                    "ALTER TABLE fr_event_signups ADD COLUMN confirmed INTEGER NOT NULL DEFAULT 0"
                );
            } catch (SQLException ignored) {}

            // Soft-delete columns
            try {
                stmt.executeUpdate(
                    "ALTER TABLE fr_events ADD COLUMN isRemoved INTEGER NOT NULL DEFAULT 0"
                );
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate(
                    "ALTER TABLE fr_rounds ADD COLUMN isRemoved INTEGER NOT NULL DEFAULT 0"
                );
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate(
                    "ALTER TABLE fr_heats ADD COLUMN isRemoved INTEGER NOT NULL DEFAULT 0"
                );
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate(
                    "ALTER TABLE fr_drivers ADD COLUMN isRemoved INTEGER NOT NULL DEFAULT 0"
                );
            } catch (SQLException ignored) {}

            // 3. BoatUtils e Checkpoints
            stmt.executeUpdate(
                """
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
                )"""
            );
            try {
                stmt.execute(
                    "ALTER TABLE fr_boatutils ADD COLUMN exclusiveMode BOOLEAN DEFAULT FALSE;"
                );
            } catch (SQLException ignored) {}

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_checkpoint (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trackNameWS TEXT DEFAULT NULL,
                    checkpointId INTEGER NOT NULL,
                    worldName TEXT NOT NULL,
                    shape TEXT DEFAULT 'AABB',
                    points TEXT,
                    min_x REAL NOT NULL, min_y REAL NOT NULL, min_z REAL NOT NULL,
                    max_x REAL NOT NULL, max_y REAL NOT NULL, max_z REAL NOT NULL
                )"""
            );

            try {
                stmt.executeUpdate(
                    "ALTER TABLE fr_checkpoint ADD COLUMN shape TEXT DEFAULT 'AABB'"
                );
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate(
                    "ALTER TABLE fr_checkpoint ADD COLUMN points TEXT"
                );
            } catch (SQLException ignored) {}

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_checkpoint_times (
                    timetrial_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    trackNameWS TEXT DEFAULT NULL,
                    checkpointId INTEGER NOT NULL,
                    time REAL NOT NULL,
                    PRIMARY KEY (player_uuid, trackNameWS, checkpointId)
                )"""
            );

            stmt.executeUpdate("DROP TABLE IF EXISTS fr_timetrial_duels");
            stmt.executeUpdate("DROP TABLE IF EXISTS fr_timetrial_dueltimes");
            stmt.executeUpdate(
                "DROP TABLE IF EXISTS fr_timetrial_duel_players"
            );

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_timetrial_duels_checkpoint_times (
                    timetrial_id INTEGER NOT NULL,
                    duel_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    trackNameWS TEXT DEFAULT NULL,
                    checkpointId INTEGER NOT NULL,
                    time REAL NOT NULL,
                    PRIMARY KEY (player_uuid, trackNameWS, checkpointId)
                )"""
            );
            // 4. Sistema de DUELOS
            stmt.executeUpdate(
                """
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
                )"""
            );
            try {
                stmt.execute(
                    "ALTER TABLE fr_players ADD COLUMN lang STRING DEFAULT NULL;"
                );
            } catch (SQLException ignored) {}
            try {
                stmt.execute(
                    "ALTER TABLE fr_players ADD COLUMN selectedHeat INTEGER DEFAULT NULL;"
                );
            } catch (SQLException ignored) {}

            // Migração segura para a coluna winner
            try {
                stmt.execute(
                    "ALTER TABLE fr_timetrial_duels ADD COLUMN winner VARCHAR(36) NULL;"
                );
            } catch (SQLException ignored) {}

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_timetrial_dueltimes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    duel_id INTEGER NOT NULL,
                    playerName VARCHAR(36) NOT NULL,
                    time DOUBLE DEFAULT 0,
                    checkpointsReached INT DEFAULT 0,
                    finished BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (duel_id) REFERENCES fr_timetrial_duels(id) ON DELETE CASCADE
                )"""
            );

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_timetrial_duel_players (
                     duel_id INTEGER PRIMARY KEY,
                     players TEXT NOT NULL,
                     FOREIGN KEY (duel_id) REFERENCES fr_timetrial_duels(id) ON DELETE CASCADE
                )"""
            );
            try {
                stmt.executeUpdate("DROP TABLE IF EXISTS fr_drs;");
            } catch (SQLException ignored) {}
            // 5. Sistema de Jogadores e Tempos (Time Trial)
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_player_times (
                     id INTEGER PRIMARY KEY AUTOINCREMENT,
                     trackNameWS TEXT NOT NULL,
                     player_uuid TEXT NOT NULL,
                     player_name TEXT NOT NULL,
                     bestTime REAL DEFAULT 0,
                     checkpointsReached INTEGER DEFAULT 0,
                     finished BOOLEAN DEFAULT FALSE,
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )"""
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS fr_drs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "trackNameWS TEXT NOT NULL, " +
                            "regionMinX REAL, regionMinY REAL, regionMinZ REAL, " +
                            "regionMaxX REAL, regionMaxY REAL, regionMaxZ REAL, " +
                            "world TEXT NOT NULL, " + // <-- Adicionada a vírgula aqui
                            "type TEXT NOT NULL" +     // <-- Agora o tipo fica em uma coluna separada
                            ");"
            );

            stmt.executeUpdate(
                """
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
                    selectedHeat INTEGER DEFAULT NULL,
                    lang TEXT DEFAULT NULL
                )"""
            );

            // 6. Outros
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS fr_holograms (trackNameWS TEXT PRIMARY KEY, world TEXT, x REAL, y REAL, z REAL)"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS fr_party (id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL UNIQUE, members TEXT NOT NULL)"
            );
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_grid_positions (
                    id INTEGER NOT NULL,
                    trackNameWS TEXT DEFAULT NULL,
                    positionIndex INTEGER NOT NULL,
                    x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                    yaw REAL NOT NULL, pitch REAL NOT NULL, world TEXT,
                    PRIMARY KEY (id, trackNameWS),
                    UNIQUE (positionIndex, trackNameWS)
                )"""
            );
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_pit_lanes (
                    trackNameWS TEXT NOT NULL,
                    pitId INTEGER NOT NULL,
                    minX REAL NOT NULL, minY REAL NOT NULL, minZ REAL NOT NULL,
                    maxX REAL NOT NULL, maxY REAL NOT NULL, maxZ REAL NOT NULL,
                    world TEXT NOT NULL,
                    PRIMARY KEY (trackNameWS, pitId)
                )"""
            );
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS fr_pit_stops (
                    trackNameWS TEXT NOT NULL PRIMARY KEY,
                    entryMinX REAL, entryMinY REAL, entryMinZ REAL,
                    entryMaxX REAL, entryMaxY REAL, entryMaxZ REAL,
                    exitMinX REAL, exitMinY REAL, exitMinZ REAL,
                    exitMaxX REAL, exitMaxY REAL, exitMaxZ REAL,
                    world TEXT NOT NULL
                )"""
            );

            // MIGRAÇÃO: garante que todas as colunas novas existam (caso tabela seja antiga)
            String[] newColumns = {
                "entryMinX",
                "entryMinY",
                "entryMinZ",
                "entryMaxX",
                "entryMaxY",
                "entryMaxZ",
                "exitMinX",
                "exitMinY",
                "exitMinZ",
                "exitMaxX",
                "exitMaxY",
                "exitMaxZ",
            };
            for (String col : newColumns) {
                try {
                    stmt.executeUpdate(
                        "ALTER TABLE fr_pit_stops ADD COLUMN " + col + " REAL"
                    );
                } catch (SQLException ignored) {
                    // Coluna já existe – ignora
                }
            }

            // [NOVO] Adiciona colunas para a "Pit Area"
            String[] areaColumns = {
                "areaMinX",
                "areaMinY",
                "areaMinZ",
                "areaMaxX",
                "areaMaxY",
                "areaMaxZ",
            };
            for (String col : areaColumns) {
                try {
                    stmt.executeUpdate(
                        "ALTER TABLE fr_pit_stops ADD COLUMN " + col + " REAL"
                    );
                } catch (SQLException ignored) {
                    // Coluna já existe – ignora
                }
            }

            // [NOVO] Adiciona colunas para "finishAll" na tabela fr_tracks
            String[] finishAllColumns = {
                "finishAll_x",
                "finishAll_y",
                "finishAll_z",
                "finishAll_yaw",
                "finishAll_pitch",
            };
            for (String col : finishAllColumns) {
                try {
                    stmt.executeUpdate(
                        "ALTER TABLE fr_tracks ADD COLUMN " +
                            col +
                            " REAL DEFAULT NULL"
                    );
                } catch (SQLException ignored) {}
            }

            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Tabelas do banco de dados verificadas com sucesso."
                );
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Erro crítico ao criar tabelas: " +
                        e.getMessage()
                );
        }
    }

    public boolean deleteDRSRegionById(int id) {
        String sql = "DELETE FROM fr_drs WHERE id = ?;";

        try (Connection conn = getOrConnect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0; // Retorna true se deletou uma linha

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[FormulaRacing] Erro ao deletar região por ID: " + e.getMessage());
            return false;
        }
    }

    /**
     * ✅ MIGRAÇÃO: Normaliza todos os trackNameWS no banco de dados
     * Converte para lowercase e remove espaços
     * Atualiza TODAS as tabelas que referenciam trackNameWS
     */
    public synchronized void normalizeAllTrackNames() {
        plugin
            .getDebugManager()
            .logDatabaseOperation("========================================");
        plugin
            .getDebugManager()
            .logDatabaseOperation(
                "[MIGRAÇÃO] Iniciando normalização de trackNameWS..."
            );
        plugin
            .getDebugManager()
            .logDatabaseOperation("========================================");

        try {
            Connection conn = getOrConnect();

            // ========== PASSO 1: Mapear todos os trackNameWS antigos e novos ==========
            Map<String, String> trackNameMapping = new HashMap<>(); // old -> new

            String sqlSelect = "SELECT trackName, trackNameWS FROM fr_tracks";
            try (
                PreparedStatement ps = conn.prepareStatement(sqlSelect);
                ResultSet rs = ps.executeQuery()
            ) {
                while (rs.next()) {
                    String trackName = rs.getString("trackName");
                    String oldTrackNameWS = rs.getString("trackNameWS");
                    String newTrackNameWS = oldTrackNameWS
                        .replaceAll("\\s+", "")
                        .toLowerCase();

                    if (!oldTrackNameWS.equals(newTrackNameWS)) {
                        trackNameMapping.put(oldTrackNameWS, newTrackNameWS);
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[MIGRAÇÃO] Mapeado: '" +
                                    oldTrackNameWS +
                                    "' -> '" +
                                    newTrackNameWS +
                                    "'"
                            );
                    }
                }
            }

            if (trackNameMapping.isEmpty()) {
                plugin
                    .getDebugManager()
                    .logDatabaseOperation(
                        "[MIGRAÇÃO] Nenhuma pista precisa ser normalizada. Tudo OK!"
                    );
                plugin
                    .getDebugManager()
                    .logDatabaseOperation(
                        "========================================"
                    );
                return;
            }

            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Encontradas " +
                        trackNameMapping.size() +
                        " pistas para normalizar"
                );
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Atualizando tabela: fr_tracks"
                );
            String sqlUpdateTracks =
                "UPDATE fr_tracks SET trackNameWS = ? WHERE trackNameWS = ?";
            try (
                PreparedStatement ps = conn.prepareStatement(sqlUpdateTracks)
            ) {
                for (Map.Entry<
                    String,
                    String
                > entry : trackNameMapping.entrySet()) {
                    ps.setString(1, entry.getValue()); // novo
                    ps.setString(2, entry.getKey()); // antigo
                    ps.executeUpdate();
                }
            }

            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Atualizando tabela: fr_player_times"
                );
            String sqlUpdatePlayerTimes =
                "UPDATE fr_player_times SET trackNameWS = ? WHERE trackNameWS = ?";
            try (
                PreparedStatement ps = conn.prepareStatement(
                    sqlUpdatePlayerTimes
                )
            ) {
                for (Map.Entry<
                    String,
                    String
                > entry : trackNameMapping.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    int updated = ps.executeUpdate();
                    if (updated > 0) {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[MIGRAÇÃO]   ✅ fr_player_times: " +
                                    updated +
                                    " registros atualizados para '" +
                                    entry.getValue() +
                                    "'"
                            );
                    }
                }
            }

            // ========== PASSO 4: Atualizar fr_checkpoint ==========
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Atualizando tabela: fr_checkpoint"
                );
            String sqlUpdateCheckpoint =
                "UPDATE fr_checkpoint SET trackNameWS = ? WHERE trackNameWS = ?";
            try (
                PreparedStatement ps = conn.prepareStatement(
                    sqlUpdateCheckpoint
                )
            ) {
                for (Map.Entry<
                    String,
                    String
                > entry : trackNameMapping.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    int updated = ps.executeUpdate();
                    if (updated > 0) {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[MIGRAÇÃO]   ✅ fr_checkpoint: " +
                                    updated +
                                    " checkpoints atualizados"
                            );
                    }
                }
            }

            // ========== PASSO 5: Atualizar fr_checkpoint_times ==========
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Atualizando tabela: fr_checkpoint_times"
                );
            String sqlUpdateCheckpointTimes =
                "UPDATE fr_checkpoint_times SET trackNameWS = ? WHERE trackNameWS = ?";
            try (
                PreparedStatement ps = conn.prepareStatement(
                    sqlUpdateCheckpointTimes
                )
            ) {
                for (Map.Entry<
                    String,
                    String
                > entry : trackNameMapping.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    int updated = ps.executeUpdate();
                    if (updated > 0) {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[MIGRAÇÃO]   ✅ fr_checkpoint_times: " +
                                    updated +
                                    " tempos de checkpoint atualizados"
                            );
                    }
                }
            }

            // ========== PASSO 6: Atualizar fr_boatutils ==========
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Atualizando tabela: fr_boatutils"
                );
            String sqlUpdateBoatUtils =
                "UPDATE fr_boatutils SET trackNameWS = ? WHERE trackNameWS = ?";
            try (
                PreparedStatement ps = conn.prepareStatement(sqlUpdateBoatUtils)
            ) {
                for (Map.Entry<
                    String,
                    String
                > entry : trackNameMapping.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    int updated = ps.executeUpdate();
                    if (updated > 0) {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[MIGRAÇÃO]   ✅ fr_boatutils: " +
                                    updated +
                                    " configurações atualizadas"
                            );
                    }
                }
            }

            // ========== PASSO 7: Atualizar fr_grid_positions ==========
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Atualizando tabela: fr_grid_positions"
                );
            String sqlUpdateGrid =
                "UPDATE fr_grid_positions SET trackNameWS = ? WHERE trackNameWS = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdateGrid)) {
                for (Map.Entry<
                    String,
                    String
                > entry : trackNameMapping.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    int updated = ps.executeUpdate();
                    if (updated > 0) {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[MIGRAÇÃO]   ✅ fr_grid_positions: " +
                                    updated +
                                    " posições de grid atualizadas"
                            );
                    }
                }
            }

            // ========== PASSO 8: Atualizar fr_cameras ==========
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Atualizando tabela: fr_cameras"
                );
            String sqlUpdateCameras =
                "UPDATE fr_cameras SET trackNameWS = ? WHERE trackNameWS = ?";
            try (
                PreparedStatement ps = conn.prepareStatement(sqlUpdateCameras)
            ) {
                for (Map.Entry<
                    String,
                    String
                > entry : trackNameMapping.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    int updated = ps.executeUpdate();
                    if (updated > 0) {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[MIGRAÇÃO]   ✅ fr_cameras: " +
                                    updated +
                                    " câmeras atualizadas"
                            );
                    }
                }
            }

            // ========== PASSO 8.5: Atualizar fr_track_finish_positions ==========
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Atualizando tabela: fr_track_finish_positions"
                );
            String sqlUpdateFinishPos =
                "UPDATE fr_track_finish_positions SET trackNameWS = ? WHERE trackNameWS = ?";
            try (
                PreparedStatement ps = conn.prepareStatement(sqlUpdateFinishPos)
            ) {
                for (Map.Entry<
                    String,
                    String
                > entry : trackNameMapping.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    int updated = ps.executeUpdate();
                    if (updated > 0) {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[MIGRAÇÃO]   ✅ fr_track_finish_positions: " +
                                    updated +
                                    " posições atualizadas"
                            );
                    }
                }
            }

            // ========== PASSO 9: Atualizar fr_timetrial_duels ==========
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Atualizando tabela: fr_timetrial_duels"
                );
            String sqlUpdateDuels =
                "UPDATE fr_timetrial_duels SET trackNameWS = ? WHERE trackNameWS = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdateDuels)) {
                for (Map.Entry<
                    String,
                    String
                > entry : trackNameMapping.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    int updated = ps.executeUpdate();
                    if (updated > 0) {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[MIGRAÇÃO]   ✅ fr_timetrial_duels: " +
                                    updated +
                                    " duelos atualizados"
                            );
                    }
                }
            }

            // ========== PASSO 10: Atualizar fr_events (se houver coluna trackNameWS) ==========
            try {
                plugin
                    .getDebugManager()
                    .logDatabaseOperation(
                        "[MIGRAÇÃO] Atualizando tabela: fr_events"
                    );
                String sqlUpdateEvents =
                    "UPDATE fr_events SET trackNameWS = ? WHERE trackNameWS = ?";
                try (
                    PreparedStatement ps = conn.prepareStatement(
                        sqlUpdateEvents
                    )
                ) {
                    for (Map.Entry<
                        String,
                        String
                    > entry : trackNameMapping.entrySet()) {
                        ps.setString(1, entry.getValue());
                        ps.setString(2, entry.getKey());
                        int updated = ps.executeUpdate();
                        if (updated > 0) {
                            plugin
                                .getDebugManager()
                                .logDatabaseOperation(
                                    "[MIGRAÇÃO]   ✅ fr_events: " +
                                        updated +
                                        " eventos atualizados"
                                );
                        }
                    }
                }
            } catch (SQLException e) {
                // Coluna trackNameWS pode não existir em fr_events
                plugin
                    .getDebugManager()
                    .logDatabaseOperation(
                        "[MIGRAÇÃO]   ⚠️ fr_events: tabela não tem trackNameWS (OK)"
                    );
            }

            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "========================================"
                );
            plugin
                .getDebugManager()
                .logDatabaseOperation("[MIGRAÇÃO] ✅ CONCLUÍDA COM SUCESSO!");
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Total de pistas normalizadas: " +
                        trackNameMapping.size()
                );
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] Todas as tabelas foram atualizadas!"
                );
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "========================================"
                );
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRAÇÃO] ❌ ERRO CRÍTICO: " + e.getMessage()
                );
            handleSqlError(e);
        }
    }

    /**
     * Busca o nome da pista vinculada a um Duelo específico
     */
    public String getTrackNameFromDuelId(int duelId) {
        String sql = "SELECT trackNameWS FROM fr_timetrial_duels WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, duelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("trackNameWS");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "[DB] Erro ao buscar track do duelo " +
                        duelId +
                        ": " +
                        e.getMessage()
                );
        }
        return null;
    }

    /**
     * Busca o idioma do jogador diretamente do banco de dados.
     * @param uuid UUID do jogador.
     * @return Código da língua ou o padrão definido na config.
     */
    public synchronized String getPlayerLanguage(UUID uuid) {
        if (languageCache.containsKey(uuid)) {
            return languageCache.get(uuid);
        }

        String sql = "SELECT lang FROM fr_players WHERE uuid = ?";
        String lang = "en_US";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String dbLang = rs.getString("lang");
                        if (dbLang != null && !dbLang.isEmpty()) {
                            lang = dbLang;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }

        languageCache.put(uuid, lang);
        return lang;
    }

    public synchronized void setPlayerLanguage(UUID uuid, String lang) {
        String sql = "UPDATE fr_players SET lang = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, lang);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
                languageCache.put(uuid, lang);
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public synchronized String getPlayerColor1(UUID uuid) {
        return playerColor1Cache.computeIfAbsent(uuid, id -> {
            String sql = "SELECT color1 FROM fr_players WHERE uuid = ?";
            try {
                Connection conn = getOrConnect();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, id.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String color = rs.getString("color1");
                            return (color != null && !color.isEmpty())
                                ? color
                                : "#7bf200";
                        }
                    }
                }
            } catch (SQLException e) {
                handleSqlError(e);
            }
            return "#7bf200";
        });
    }

    public synchronized void setPlayerColor1(UUID uuid, String hex) {
        String sql = "UPDATE fr_players SET color1 = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, hex);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
                playerColor1Cache.put(uuid, hex);
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public synchronized String getPlayerColor2(UUID uuid) {
        return playerColor2Cache.computeIfAbsent(uuid, id -> {
            String sql = "SELECT color2 FROM fr_players WHERE uuid = ?";
            try {
                Connection conn = getOrConnect();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, id.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String color = rs.getString("color2");
                            return (color != null && !color.isEmpty())
                                ? color
                                : "#FFFFFF";
                        }
                    }
                }
            } catch (SQLException e) {
                handleSqlError(e);
            }
            return "#FFFFFF";
        });
    }

    public synchronized void migrateNullPlayerColors() {
        String sql =
            "UPDATE fr_players SET color1 = '#7bf200' WHERE color1 IS NULL OR color1 = ''";
        String sql2 =
            "UPDATE fr_players SET color2 = '#FFFFFF' WHERE color2 IS NULL OR color2 = ''";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps1 = conn.prepareStatement(sql)) {
                int updated1 = ps1.executeUpdate();
                if (updated1 > 0) {
                    plugin
                        .getDebugManager()
                        .logDatabaseOperation(
                            "[MIGRATION] Set default color1 for " +
                                updated1 +
                                " players"
                        );
                }
            }
            try (PreparedStatement ps2 = conn.prepareStatement(sql2)) {
                int updated2 = ps2.executeUpdate();
                if (updated2 > 0) {
                    plugin
                        .getDebugManager()
                        .logDatabaseOperation(
                            "[MIGRATION] Set default color2 for " +
                                updated2 +
                                " players"
                        );
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[MIGRATION] Color migration failed: " + e.getMessage()
                );
        }
    }

    public synchronized void setPlayerColor2(UUID uuid, String hex) {
        String sql = "UPDATE fr_players SET color2 = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, hex);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
                playerColor2Cache.put(uuid, hex);
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    /* =======================================================
           MÉTODOS DE DUELOS, BOAT TYPE E PLACAR
======================================================= */

    public synchronized void setPlayerBoatType(UUID uuid, int boatId) {
        String sqlInsert =
            "INSERT INTO fr_players (uuid, displayName, baseBoat) VALUES (?, ?, ?) " +
            "ON CONFLICT(uuid) DO UPDATE SET baseBoat = excluded.baseBoat";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setString(1, uuid.toString());
                String name =
                    Bukkit.getPlayer(uuid) != null
                        ? Bukkit.getPlayer(uuid).getName()
                        : "Unknown";
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
        String sql =
            "SELECT playerName as player_uuid, MIN(time) as best_time FROM fr_timetrial_dueltimes WHERE duel_id = ? GROUP BY playerName";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, duelId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        times.put(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getLong("best_time")
                        );
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
        // Debug inicial

        String sql = "SELECT 1 FROM fr_players WHERE uuid = ?";

        try {
            Connection conn = getOrConnect();

            // Debug de conexão
            if (conn == null || conn.isClosed()) {
                return false;
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    boolean exists = rs.next();
                    return exists;
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return false;
    }

    /**
     * Define o heat selecionado por um jogador.
     * @param uuid UUID do jogador.
     * @param heatId ID do heat (null para deselecionar).
     */
    public synchronized void setSelectedHeat(UUID uuid, Integer heatId) {
        String sql = "UPDATE fr_players SET selectedHeat = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (heatId == null) {
                    ps.setNull(1, Types.INTEGER);
                } else {
                    ps.setInt(1, heatId);
                }
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            handleSqlError(e);
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao definir heat selecionado para " +
                        uuid +
                        ": " +
                        e.getMessage()
                );
        }
    }

    /**
     * Retorna o ID do heat selecionado pelo jogador.
     * @param uuid UUID do jogador.
     * @return Optional com o ID do heat, ou vazio se nenhum selecionado.
     */
    public synchronized Optional<Integer> getPlayerSelectedHeat(UUID uuid) {
        String sql = "SELECT selectedHeat FROM fr_players WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("selectedHeat");
                        if (!rs.wasNull()) {
                            return Optional.of(id);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return Optional.empty();
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

    public boolean getTimeTrialScoreboard(UUID playerUUID) {
        if (playerUUID == null) return true;
        if (ttScoreboardCache.containsKey(playerUUID)) {
            return ttScoreboardCache.get(playerUUID);
        }

        synchronized (this) {
            // Double check
            if (ttScoreboardCache.containsKey(playerUUID)) {
                return ttScoreboardCache.get(playerUUID);
            }

            String sql =
                "SELECT timetrialScoreboard FROM fr_players WHERE uuid = ?";
            try {
                Connection conn = getOrConnect();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUUID.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            boolean value =
                                rs.getInt("timetrialScoreboard") == 1;
                            ttScoreboardCache.put(playerUUID, value);
                            return value;
                        }
                    }
                }
            } catch (SQLException e) {
                handleSqlError(e);
            }
        }
        return true; // Habilitado por padrão
    }

    /* =======================================================
           MÉTODOS DE JOGADORES E CHECKPOINTS
======================================================= */

    public synchronized boolean setTimeTrialScoreboard(
        UUID playerUUID,
        boolean value
    ) {
        String sql =
            "UPDATE fr_players SET timetrialScoreboard = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, value ? 1 : 0);
                ps.setString(2, playerUUID.toString());
                boolean success = ps.executeUpdate() > 0;
                if (success) {
                    ttScoreboardCache.put(playerUUID, value);
                }
                return success;
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
            ) VALUES (?, ?, '#7bf200', '#FFFFFF', 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, NULL, 0)
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

    /**
     * Busca o UUID de um jogador pelo nome (case-insensitive)
     * Útil para comandos que aceitam jogadores offline
     * @param playerName Nome do jogador
     * @return UUID do jogador ou null se não encontrado
     */
    public synchronized UUID getPlayerUUIDByName(String playerName) {
        String sql =
            "SELECT uuid FROM fr_players WHERE LOWER(displayName) = LOWER(?) LIMIT 1";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String uuidString = rs.getString("uuid");
                        return UUID.fromString(uuidString);
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized List<Integer> getCheckpointIds(String trackNameWS) {
        List<Integer> ids = new ArrayList<>();
        String sql =
            "SELECT checkpointId FROM fr_checkpoint WHERE LOWER(trackNameWS) = LOWER(?) ORDER BY checkpointId ASC";
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
        if (ttEnabledCache.containsKey(playerUUID)) {
            return ttEnabledCache.get(playerUUID);
        }

        String sql = "SELECT timetrial FROM fr_players WHERE uuid = ?";
        boolean enabled = true; // default

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        enabled = rs.getInt("timetrial") == 1;
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }

        ttEnabledCache.put(playerUUID, enabled);
        return enabled;
    }

    public synchronized void setTimeTrialEnabled(
        UUID playerUUID,
        boolean enabled
    ) {
        String sql = "UPDATE fr_players SET timetrial = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, enabled ? 1 : 0);
                ps.setString(2, playerUUID.toString());
                ps.executeUpdate();
                ttEnabledCache.put(playerUUID, enabled);
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public synchronized boolean getPlayerCompactMode(UUID uuid) {
        if (compactScoreboardCache.containsKey(uuid)) {
            return compactScoreboardCache.get(uuid);
        }

        String sql = "SELECT compactScoreboard FROM fr_players WHERE uuid = ?";
        boolean compact = false;

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        compact = rs.getInt("compactScoreboard") == 1;
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }

        compactScoreboardCache.put(uuid, compact);
        return compact;
    }

    public synchronized void setPlayerCompactMode(UUID uuid, boolean compact) {
        String sql =
            "UPDATE fr_players SET compactScoreboard = ? WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, compact ? 1 : 0);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
                compactScoreboardCache.put(uuid, compact);
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public List<RegionData> getCheckpoints(String trackNameWS) {
        if (trackNameWS == null) return new ArrayList<>();
        String cleanTrack = trackNameWS.toLowerCase();

        if (checkpointsCache.containsKey(cleanTrack)) {
            return new ArrayList<>(checkpointsCache.get(cleanTrack));
        }

        List<RegionData> checkpoints = new ArrayList<>();
        String sql =
            "SELECT checkpointId, worldName, shape, points, min_x, min_y, min_z, max_x, max_y, max_z " +
            "FROM fr_checkpoint WHERE LOWER(trackNameWS) = LOWER(?) ORDER BY checkpointId ASC";

        String trackNameDisplay = trackNameWS;
        TrackData td = getTrackData(trackNameWS);
        if (td != null) {
            trackNameDisplay = td.getTrackName();
        }

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, cleanTrack);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String worldName = rs.getString("worldName");
                        if (Bukkit.getWorld(worldName) == null) continue;

                        String shape = rs.getString("shape");
                        if (shape == null) shape = "AABB";
                        String points = rs.getString("points");

                        checkpoints.add(
                            new RegionData(
                                rs.getInt("checkpointId"),
                                trackNameDisplay,
                                trackNameWS,
                                "CHECKPOINT",
                                shape,
                                points,
                                rs.getDouble("min_x"),
                                rs.getDouble("min_y"),
                                rs.getDouble("min_z"),
                                rs.getDouble("max_x"),
                                rs.getDouble("max_y"),
                                rs.getDouble("max_z"),
                                worldName
                            )
                        );
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }

        checkpointsCache.put(cleanTrack, new ArrayList<>(checkpoints));
        return checkpoints;
    }

    public synchronized void clearCheckpointsCache(String trackNameWS) {
        if (trackNameWS == null) {
            checkpointsCache.clear();
            checkpointCountCache.clear();
        } else {
            String cleanTrack = trackNameWS.toLowerCase();
            checkpointsCache.remove(cleanTrack);
            checkpointCountCache.remove(cleanTrack);
        }
    }

    public int getCheckpointCount(String trackNameWS) {
        if (trackNameWS == null) return 0;
        String normalized = trackNameWS.toLowerCase();

        if (checkpointCountCache.containsKey(normalized)) {
            return checkpointCountCache.get(normalized);
        }

        synchronized (this) {
            // Double check
            if (checkpointCountCache.containsKey(normalized)) {
                return checkpointCountCache.get(normalized);
            }

            String sql =
                "SELECT COUNT(*) AS total FROM fr_checkpoint WHERE LOWER(trackNameWS) = LOWER(?)";
            try {
                Connection conn = getOrConnect();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, trackNameWS);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int count = rs.getInt("total");
                            checkpointCountCache.put(normalized, count);
                            return count;
                        }
                    }
                }
            } catch (SQLException e) {
                handleSqlError(e);
            }
        }
        return 0;
    }

    public void clearCheckpointCountCache(String trackNameWS) {
        if (trackNameWS == null) {
            checkpointCountCache.clear();
        } else {
            checkpointCountCache.remove(trackNameWS.toLowerCase());
        }
    }

    public synchronized boolean addCheckpoint(
        int checkpointId,
        String trackNameWS,
        Player player
    ) {
        if (!WorldEditSelect.hasSelection(player)) return false;

        Location selMin = WorldEditSelect.getMin(player);
        Location selMax = WorldEditSelect.getMax(player);
        String world = selMin.getWorld().getName();

        double minX = Math.min(selMin.getX(), selMax.getX()),
            minY = Math.min(selMin.getY(), selMax.getY()),
            minZ = Math.min(selMin.getZ(), selMax.getZ());
        double maxX = Math.max(selMin.getX(), selMax.getX()),
            maxY = Math.max(selMin.getY(), selMax.getY()),
            maxZ = Math.max(selMin.getZ(), selMax.getZ());

        try {
            Connection conn = getOrConnect();
            conn.setAutoCommit(false); // Atômico: evita IDs pulados ou duplicados

            try {
                int finalId = checkpointId;
                if (finalId <= 0) {
                    try (
                        PreparedStatement ps = conn.prepareStatement(
                            "SELECT MAX(checkpointId) FROM fr_checkpoint WHERE LOWER(trackNameWS) = LOWER(?)"
                        )
                    ) {
                        ps.setString(1, trackNameWS);
                        try (ResultSet rs = ps.executeQuery()) {
                            finalId = rs.next() ? rs.getInt(1) + 1 : 1;
                        }
                    }
                }

                String sqlInsert =
                    "INSERT INTO fr_checkpoint (checkpointId, trackNameWS, worldName, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                    ps.setInt(1, finalId);
                    ps.setString(2, trackNameWS);
                    ps.setString(3, world);
                    ps.setDouble(4, minX);
                    ps.setDouble(5, minY);
                    ps.setDouble(6, minZ);
                    ps.setDouble(7, maxX);
                    ps.setDouble(8, maxY);
                    ps.setDouble(9, maxZ);
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

    public synchronized boolean addCheckpointPoly(
        int checkpointId,
        String trackNameWS,
        Player player,
        String points
    ) {
        if (!WorldEditSelect.hasSelection(player)) return false;

        Location selMin = WorldEditSelect.getMin(player);
        Location selMax = WorldEditSelect.getMax(player);
        String world = selMin.getWorld().getName();

        double minX = Math.min(selMin.getX(), selMax.getX());
        double minY = Math.min(selMin.getY(), selMax.getY());
        double minZ = Math.min(selMin.getZ(), selMax.getZ());
        double maxX = Math.max(selMin.getX(), selMax.getX());
        double maxY = Math.max(selMin.getY(), selMax.getY());
        double maxZ = Math.max(selMin.getZ(), selMax.getZ());

        try {
            Connection conn = getOrConnect();
            conn.setAutoCommit(false);

            try {
                int finalId = checkpointId;
                if (finalId <= 0) {
                    try (
                        PreparedStatement ps = conn.prepareStatement(
                            "SELECT MAX(checkpointId) FROM fr_checkpoint WHERE LOWER(trackNameWS) = LOWER(?)"
                        )
                    ) {
                        ps.setString(1, trackNameWS);
                        try (ResultSet rs = ps.executeQuery()) {
                            finalId = rs.next() ? rs.getInt(1) + 1 : 1;
                        }
                    }
                }

                String sqlInsert =
                    "INSERT INTO fr_checkpoint (checkpointId, trackNameWS, worldName, shape, points, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                    ps.setInt(1, finalId);
                    ps.setString(2, trackNameWS);
                    ps.setString(3, world);
                    ps.setString(4, "POLY");
                    ps.setString(5, points);
                    ps.setDouble(6, minX);
                    ps.setDouble(7, minY);
                    ps.setDouble(8, minZ);
                    ps.setDouble(9, maxX);
                    ps.setDouble(10, maxY);
                    ps.setDouble(11, maxZ);
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
        String sql =
            "SELECT r.id, r.trackNameWS, r.regionType, r.worldName, " +
            "r.min_x, r.min_y, r.min_z, r.max_x, r.max_y, r.max_z, " +
            "COALESCE(t.trackName, r.trackNameWS) as displayName " +
            "FROM fr_regions r " +
            "LEFT JOIN fr_tracks t ON LOWER(r.trackNameWS) = LOWER(t.trackNameWS)";
        try {
            Connection conn = getOrConnect();
            try (
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
            ) {
                while (rs.next()) {
                    list.add(
                        new RegionData(
                            rs.getInt("id"),
                            rs.getString("displayName"),
                            rs.getString("trackNameWS"),
                            rs.getString("regionType"),
                            rs.getDouble("min_x"),
                            rs.getDouble("min_y"),
                            rs.getDouble("min_z"),
                            rs.getDouble("max_x"),
                            rs.getDouble("max_y"),
                            rs.getDouble("max_z"),
                            rs.getString("worldName")
                        )
                    );
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return list;
    }

    /**
     * Verifica se uma pista é um CIRCUITO FECHADO.
     * Retorna FALSE se for POINT-TO-POINT (Sprint, Parkour, etc), ou seja,
     * se START e END forem regiões distintas em locais diferentes.
     */
    public boolean isCircuit(String trackNameWS) {
        if (trackNameWS == null || trackNameWS.isEmpty()) return true;

        List<RegionData> trackRegions = getAllRegions()
            .stream()
            .filter(r -> r.getTrackNameWS().equalsIgnoreCase(trackNameWS))
            .toList();

        RegionData start = null;
        RegionData end = null;

        for (RegionData r : trackRegions) {
            if ("START".equalsIgnoreCase(r.getType())) {
                start = r;
            } else if ("END".equalsIgnoreCase(r.getType())) {
                end = r;
            }
        }

        // Se não tem END, assumimos que é circuito (ou incompleto, mas tratamos como circuito por segurança)
        if (end == null) return true;
        // Se não tem START, mesma coisa
        if (start == null) return true;

        // Se START e END são a MESMA região (mesmo ID ou tecnicamente sobrepostos), é circuito
        // Mas a convenção é: se existir END separado que não é o START, é sprint.
        // No Heats.java, geralmente se a região é tipo START ela conta lap.
        // Se for Sprint, a lap só fecha no END.

        // Vamos comparar a distância entre os centros
        double startX = (start.getMinX() + start.getMaxX()) / 2.0;
        double startZ = (start.getMinZ() + start.getMaxZ()) / 2.0;
        double endX = (end.getMinX() + end.getMaxX()) / 2.0;
        double endZ = (end.getMinZ() + end.getMaxZ()) / 2.0;

        double distSq = Math.pow(startX - endX, 2) + Math.pow(startZ - endZ, 2);

        // Se a distância for significativa (> 20 blocos), consideramos Point-to-Point
        // 400 = 20^2
        return distSq < 400;
    }

    public synchronized boolean deleteRegionById(int regionId) {
        String sql = "DELETE FROM fr_regions WHERE id = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, regionId);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean removeCheckpoint(
        String trackName,
        int checkpointId
    ) {
        String sql =
            "DELETE FROM fr_checkpoint WHERE LOWER(trackNameWS) = LOWER(?) AND checkpointId = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, trackName.replaceAll("\\s+", ""));
                stmt.setInt(2, checkpointId);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean removeCheckpointById(int id) {
        String sql = "DELETE FROM fr_checkpoint WHERE id = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean updateRegionBounds(
        int regionId,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        String sql =
            "UPDATE fr_regions SET min_x=?, min_y=?, min_z=?, max_x=?, max_y=?, max_z=? WHERE id=?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, minX);
                stmt.setDouble(2, minY);
                stmt.setDouble(3, minZ);
                stmt.setDouble(4, maxX);
                stmt.setDouble(5, maxY);
                stmt.setDouble(6, maxZ);
                stmt.setInt(7, regionId);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean createTrack(
        String trackName,
        Location spawnLocation,
        String ownerName,
        String ownerUuid
    ) {
        String trackNameWS = trackName.replaceAll("\\s+", "").toLowerCase();
        String sqlTrack =
            "INSERT INTO fr_tracks (trackName, trackNameWS, uuid, creatorName, creatorUUID, " +
            "spawnPoint_x, spawnPoint_y, spawnPoint_z, spawnPoint_yaw, spawnPoint_pitch, worldName, open) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String sqlBoatUtils =
            "INSERT INTO fr_boatutils (trackNameWS, stepHeight, defaultSlipperiness, fallDamage, " +
            "waterElevation, airControl, jumpForce, gravity, yawAcceleration, forwardAcceleration, " +
            "backwardAcceleration, turningForwardAcceleration, allowAccelerationStacking, underwaterControl, " +
            "surfaceWaterControl, coyoteTime, waterJumping, swimForce, collisionMode, " +
            "airStepping, tenStepInterpolation, collisionResolution) " +
            "VALUES (?, 0.0, 0.6, 1, 1, 0, 0.0, -0.04, 1.0, 0.04, 0.005, 0.005, 1, 0, 0, 0, 0, 0.0, 0, 0, 0, 5) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET " +
            "stepHeight = excluded.stepHeight, " +
            "defaultSlipperiness = excluded.defaultSlipperiness, " +
            "fallDamage = excluded.fallDamage, " +
            "waterElevation = excluded.waterElevation, " +
            "airControl = excluded.airControl, " +
            "jumpForce = excluded.jumpForce, " +
            "gravity = excluded.gravity, " +
            "yawAcceleration = excluded.yawAcceleration, " +
            "forwardAcceleration = excluded.forwardAcceleration, " +
            "backwardAcceleration = excluded.backwardAcceleration, " +
            "turningForwardAcceleration = excluded.turningForwardAcceleration, " +
            "allowAccelerationStacking = excluded.allowAccelerationStacking, " +
            "underwaterControl = excluded.underwaterControl, " +
            "surfaceWaterControl = excluded.surfaceWaterControl, " +
            "coyoteTime = excluded.coyoteTime, " +
            "waterJumping = excluded.waterJumping, " +
            "swimForce = excluded.swimForce, " +
            "collisionMode = excluded.collisionMode, " +
            "airStepping = excluded.airStepping, " +
            "tenStepInterpolation = excluded.tenStepInterpolation, " +
            "collisionResolution = excluded.collisionResolution";

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

                try (
                    PreparedStatement ps2 = conn.prepareStatement(sqlBoatUtils)
                ) {
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

    public synchronized Map<Integer, Double> getCheckpointTimes(
        UUID playerUUID,
        String trackName
    ) {
        Map<Integer, Double> checkpointTimes = new HashMap<>();
        // ✅ Remove apenas espaços, sem toLowerCase
        String trackNameWS = trackName.replaceAll("\\s+", "");

        // ✅ LÓGICA CORRETA: Busca checkpoints do tempo mais rápido QUE TENHA checkpoints salvos
        // Não busca o PB absoluto, porque ele pode não ter checkpoints (feito antes do sistema)
        // Isso garante que o delta sempre compare com uma volta válida que tenha dados salvos
        String sql =
            "SELECT ct.checkpointId, ct.time " +
            "FROM fr_checkpoint_times ct " +
            "INNER JOIN fr_player_times pt ON ct.timetrial_id = pt.id " +
            "WHERE ct.player_uuid = ? AND LOWER(ct.trackNameWS) = LOWER(?) " +
            "AND pt.finished = TRUE " +
            "AND pt.id = (" +
            "    SELECT pt2.id FROM fr_player_times pt2 " +
            "    WHERE pt2.player_uuid = ? AND LOWER(pt2.trackNameWS) = LOWER(?) " +
            "    AND pt2.finished = TRUE " +
            "    AND EXISTS (" + // ✅ CRÍTICO: Só considera tempos que TEM checkpoints
            "        SELECT 1 FROM fr_checkpoint_times ct3 " +
            "        WHERE ct3.timetrial_id = pt2.id LIMIT 1" +
            "    ) " +
            "    ORDER BY pt2.bestTime ASC LIMIT 1" + // O mais rápido QUE TENHA checkpoints
            ")";

        try {
            Connection conn = getOrConnect();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, trackNameWS);
                ps.setString(3, playerUUID.toString());
                ps.setString(4, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        checkpointTimes.put(
                            rs.getInt("checkpointId"),
                            rs.getDouble("time")
                        );
                    }
                }
            }

            // 🔍 DEBUG: Log detalhado
            if (plugin.getDebugManager().isTimeTrialSystemEnabled()) {
                if (checkpointTimes.isEmpty()) {
                    // Verifica se existe PB SEM checkpoints
                    String debugSql =
                        "SELECT bestTime FROM fr_player_times " +
                        "WHERE player_uuid = ? AND LOWER(trackNameWS) = LOWER(?) " +
                        "AND finished = TRUE ORDER BY bestTime ASC LIMIT 1";
                    try (
                        PreparedStatement psDebug = conn.prepareStatement(
                            debugSql
                        )
                    ) {
                        psDebug.setString(1, playerUUID.toString());
                        psDebug.setString(2, trackNameWS);
                        try (ResultSet rsDebug = psDebug.executeQuery()) {
                            if (rsDebug.next()) {
                                double pbTime = rsDebug.getDouble("bestTime");
                                plugin
                                    .getDebugManager()
                                    .logTimeTrialSystem(
                                        "§e[DB] PB de " +
                                            String.format("%.3f", pbTime) +
                                            "s existe mas SEM checkpoints - " +
                                            "delta só aparecerá após nova volta com checkpoints"
                                    );
                            } else {
                                plugin
                                    .getDebugManager()
                                    .logTimeTrialSystem(
                                        "§e[DB] Nenhum PB registrado - primeira volta"
                                    );
                            }
                        }
                    }
                } else {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "§a[DB] Carregados " +
                                checkpointTimes.size() +
                                " checkpoints para comparação"
                        );
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Erro ao buscar checkpoints: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return checkpointTimes;
    }

    /* =======================================================
          MÉTODOS DE DELEÇÃO, REGIÕES E CONSULTAS
======================================================= */

    public synchronized Location deleteTrack(String trackName) {
        Location hologramLocation = null;
        String trackNameWS = trackName.replaceAll("\\s+", "");

        // Queries de deleção organizada
        String selectHologramSql =
            "SELECT world, x, y, z FROM fr_holograms WHERE LOWER(trackNameWS) = LOWER(?)";
        String[] deleteSqls = {
            "DELETE FROM fr_heats WHERE roundId IN (SELECT id FROM fr_rounds WHERE eventId IN (SELECT id FROM fr_events WHERE LOWER(trackNameWS) = LOWER(?)))",
            "DELETE FROM fr_rounds WHERE eventId IN (SELECT id FROM fr_events WHERE LOWER(trackNameWS) = LOWER(?))",
            "DELETE FROM fr_regions WHERE LOWER(trackNameWS) = LOWER(?)",
            "DELETE FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?)",
            "DELETE FROM fr_checkpoint WHERE LOWER(trackNameWS) = LOWER(?)",
            "DELETE FROM fr_checkpoint_times WHERE LOWER(trackNameWS) = LOWER(?)",
            "DELETE FROM fr_holograms WHERE LOWER(trackNameWS) = LOWER(?)",
            "DELETE FROM fr_events WHERE LOWER(trackNameWS) = LOWER(?)",
            "DELETE FROM fr_boatutils WHERE LOWER(trackNameWS) = LOWER(?)",
            "DELETE FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?)",
        };

        try {
            Connection conn = getOrConnect();
            conn.setAutoCommit(false); // Inicia transação atômica

            // 1. Buscar holograma antes de apagar
            try (
                PreparedStatement ps = conn.prepareStatement(selectHologramSql)
            ) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        World world = Bukkit.getWorld(rs.getString("world"));
                        if (world != null) {
                            hologramLocation = new Location(
                                world,
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z")
                            );
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
            clearCheckpointsCache(trackNameWS); // Added
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Pista '" + trackName + "' deletada completamente."
                );
        } catch (SQLException e) {
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException ex) {}
            handleSqlError(e);
        } finally {
            try {
                if (connection != null) connection.setAutoCommit(true);
            } catch (SQLException ex) {}
        }
        return hologramLocation;
    }

    public synchronized int saveRegion(
        String track,
        Location min,
        Location max,
        String type
    ) {
        if (
            track == null || min == null || max == null || type == null
        ) return -1;
        String trackWS = track.replace(" ", "").toLowerCase();
        String normalizedType = type.toUpperCase();

        double minX = Math.min(min.getX(), max.getX()),
            minY = Math.min(min.getY(), max.getY()),
            minZ = Math.min(min.getZ(), max.getZ());
        double maxX = Math.max(min.getX(), max.getX()),
            maxY = Math.max(min.getY(), max.getY()),
            maxZ = Math.max(min.getZ(), max.getZ());

        try {
            Connection conn = getOrConnect();
            // Remove anterior do mesmo tipo (exceto RESET, que pode ter vários)
            int deletedCount = 0;
            if (!normalizedType.equals("RESET")) {
                try (
                    PreparedStatement psDel = conn.prepareStatement(
                        "DELETE FROM fr_regions WHERE trackNameWS=? AND regionType=?"
                    )
                ) {
                    psDel.setString(1, trackWS);
                    psDel.setString(2, normalizedType);
                    deletedCount = psDel.executeUpdate();
                }
            }

            if (deletedCount > 0) {
                plugin
                    .getDebugManager()
                    .logDatabaseOperation(
                        String.format(
                            "§e[REGION] Deletadas %d região(ões) %s anterior(es) da pista '%s' (normalizado: '%s')",
                            deletedCount,
                            normalizedType,
                            track,
                            trackWS
                        )
                    );
            }

            String insertSql =
                "INSERT INTO fr_regions (trackNameWS, regionType, worldName, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (
                PreparedStatement psIns = conn.prepareStatement(
                    insertSql,
                    Statement.RETURN_GENERATED_KEYS
                )
            ) {
                psIns.setString(1, trackWS);
                psIns.setString(2, normalizedType);
                psIns.setString(3, min.getWorld().getName());
                psIns.setDouble(4, minX);
                psIns.setDouble(5, minY);
                psIns.setDouble(6, minZ);
                psIns.setDouble(7, maxX);
                psIns.setDouble(8, maxY);
                psIns.setDouble(9, maxZ);
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

    public synchronized List<Map<String, Object>> getAllTimesOnTrackByPlayer(
        String trackName,
        String playerName,
        int page
    ) {
        List<Map<String, Object>> results = new ArrayList<>();
        int limit = 15;
        int offset = (page - 1) * limit;

        String sql =
            "SELECT bestTime, checkpointsReached, finished, created_at FROM fr_player_times " +
            "WHERE LOWER(trackNameWS) = LOWER(?) AND player_name = ? ORDER BY finished DESC, " +
            "CASE WHEN finished = 1 THEN bestTime END ASC, " +
            "CASE WHEN finished = 0 THEN checkpointsReached END DESC, created_at ASC LIMIT ? OFFSET ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
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
                        entry.put(
                            "checkpoints",
                            rs.getInt("checkpointsReached")
                        );
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

    public synchronized List<Map<String, Object>> getAllTimesOnTrack(
        String trackName,
        int page
    ) {
        List<Map<String, Object>> results = new ArrayList<>();
        int limit = 15;
        int offset = (page - 1) * limit;

        String sql =
            "SELECT ranked.player_name, ranked.bestTime, ranked.checkpointsReached, ranked.finished, ranked.created_at " +
            "FROM (SELECT t.*, ROW_NUMBER() OVER (PARTITION BY t.player_name ORDER BY t.finished DESC, " +
            "CASE WHEN t.finished = 1 THEN t.bestTime END ASC, t.checkpointsReached DESC) AS rn " +
            "FROM fr_player_times t WHERE LOWER(t.trackNameWS) = LOWER(?)) ranked WHERE ranked.rn = 1 " +
            "ORDER BY finished DESC, CASE WHEN finished = 1 THEN bestTime END ASC LIMIT ? OFFSET ?";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    int pos = offset + 1;
                    while (rs.next()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("pos", pos++);
                        entry.put("player", rs.getString("player_name"));
                        entry.put("time", rs.getDouble("bestTime"));
                        entry.put(
                            "checkpoints",
                            rs.getInt("checkpointsReached")
                        );
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
            // ✅ Busca trackName ORIGINAL ao invés de trackNameWS
            try (
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT trackName FROM fr_tracks"
                );
                ResultSet rs = ps.executeQuery()
            ) {
                while (rs.next()) tracks.add(rs.getString("trackName"));
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return tracks;
    }

    /**
     * Verifica se uma pista existe no banco de dados (normalizado).
     * @param trackName Nome da pista (com ou sem espaços)
     * @return true se a pista existir
     */
    public synchronized boolean isTrackExists(String trackName) {
        if (trackName == null || trackName.isBlank()) return false;
        String sql =
            "SELECT 1 FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?) LIMIT 1";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return false;
    }

    /**
     * Obtém o tempo recorde (menor tempo) de uma pista.
     * Usado para validar tempo limite mínimo em duelos.
     *
     * @param trackName Nome da pista
     * @return O menor tempo registrado na pista, ou null se não houver tempos
     */
    public synchronized Double getTrackRecord(String trackName) {
        String trackWS = trackName.replaceAll("\\s+", "");
        String sql =
            "SELECT MIN(bestTime) AS record FROM fr_player_times " +
            "WHERE LOWER(trackNameWS) = LOWER(?) AND finished = 1";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double record = rs.getDouble("record");
                        // Retorna null se não houver registro (valor 0.0 do MIN em SQL quando não há dados)
                        return record > 0 ? record : null;
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    /* =======================================================
          LEADERBOARD, GRIDS, CÂMERAS E CONFIGS
======================================================= */
    public synchronized List<PlayerTime> getLeaderboard(String trackName) {
        List<PlayerTime> leaderboard = new ArrayList<>();
        String trackWS = trackName.replaceAll("\\s+", "");

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
                    WHERE LOWER(trackNameWS) = LOWER(?)
                ) t
                WHERE rn = 1
                ORDER BY
                    finished DESC,
                    CASE WHEN finished = 1 THEN bestTime ELSE 999999 END ASC,
                    checkpointsReached DESC,
                    bestTime ASC
                """;

            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackWS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String uuidString = rs.getString("player_uuid");
                        UUID playerUuid = (uuidString != null)
                            ? UUID.fromString(uuidString)
                            : null;

                        leaderboard.add(
                            new PlayerTime(
                                playerUuid,
                                rs.getString("player_name"),
                                rs.getDouble("bestTime"),
                                rs.getInt("checkpointsReached"),
                                totalCheckpoints,
                                rs.getBoolean("finished")
                            )
                        );
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return leaderboard;
    }

    public synchronized void setLonelyModePlayer(
        UUID uuid,
        boolean lonelyMode
    ) {
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

    public synchronized boolean setTrackIcon(
        String trackName,
        String iconName
    ) {
        String sql =
            "UPDATE fr_tracks SET icon_name = ? WHERE LOWER(trackNameWS) = LOWER(?)";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, iconName);
                ps.setString(2, trackName.replaceAll("\\s+", ""));
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean setTrackSpawn(
        String trackName,
        Location location
    ) {
        if (trackName == null || location == null) return false;
        String sql =
            "UPDATE fr_tracks SET worldName = ?, spawnPoint_x = ?, spawnPoint_y = ?, spawnPoint_z = ?, " +
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
                ps.setString(7, trackName.replaceAll("\\s+", "").toLowerCase());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean addGridPosition(
        String trackName,
        int gridId,
        Location location
    ) {
        String sql =
            "INSERT INTO fr_grid_positions (id, trackNameWS, positionIndex, x, y, z, yaw, pitch, world) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(trackNameWS, positionIndex) DO UPDATE SET " +
            "x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch, world = excluded.world";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, gridId);
                ps.setString(2, trackName.replaceAll("\\s+", "").toLowerCase());
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

    public synchronized boolean removeGridPosition(
        String trackName,
        int gridNumber
    ) {
        String sql =
            "DELETE FROM fr_grid_positions WHERE LOWER(trackNameWS) = LOWER(?) AND positionIndex = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
                ps.setInt(2, gridNumber);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized List<GridPosition> getGridPositions(String trackName) {
        List<GridPosition> positions = new ArrayList<>();
        String sql =
            "SELECT positionIndex, x, y, z, yaw, pitch, world FROM fr_grid_positions WHERE LOWER(trackNameWS) = LOWER(?) ORDER BY positionIndex ASC";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String worldName = rs.getString("world");
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) continue;

                        positions.add(
                            new GridPosition(
                                rs.getInt("positionIndex"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getDouble("yaw"),
                                rs.getDouble("pitch"),
                                worldName
                            )
                        );
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return positions;
    }

    public synchronized boolean clearGridPositions(String trackName) {
        String sql =
            "DELETE FROM fr_grid_positions WHERE LOWER(trackNameWS) = LOWER(?)";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized List<Integer> getCamerasForTrack(String trackName) {
        List<Integer> cameraIds = new ArrayList<>();
        String sql =
            "SELECT id FROM fr_cameras WHERE LOWER(trackNameWS) = LOWER(?) ORDER BY id ASC";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
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
            try (
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
            ) {
                while (rs.next()) {
                    cameras.add(
                        new Location(
                            Bukkit.getWorld("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                        )
                    );
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
        String sql =
            "SELECT x, y, z, yaw, pitch, world FROM fr_grid_positions WHERE LOWER(trackNameWS) = LOWER(?) AND positionIndex = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
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
        String sql =
            "SELECT creatorName FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?)";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS.replaceAll("\\s+", ""));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("creatorName");
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized boolean setTrackOwner(
        String trackNameWS,
        String newOwnerName
    ) {
        // Usamos REPLACE para remover espaços e LOWER para ignorar maiúsculas
        // O primeiro argumento de REPLACE é a coluna, o segundo é o caractere ' ' e o terceiro é vazio ''
        String sql =
            "UPDATE fr_tracks SET creatorName = ? WHERE REPLACE(LOWER(trackNameWS), ' ', '') = LOWER(?)";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newOwnerName);

                // Removemos os espaços do argumento que o jogador digitou tmb
                String cleanedTrackName = trackNameWS
                    .replace(" ", "")
                    .toLowerCase(Locale.ROOT);
                ps.setString(2, cleanedTrackName);

                int affectedRows = ps.executeUpdate();

                if (affectedRows > 0) {
                    return true;
                } else {
                    plugin
                        .getLogger()
                        .warning(
                            "[SQLite] Pista '" +
                                trackNameWS +
                                "' não encontrada (mesmo ignorando espaços/letras)."
                        );
                    return false;
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized Map<String, TrackData> getAllTracksWithData() {
        Map<String, TrackData> trackDataMap = new HashMap<>();
        String sql =
            "SELECT trackName, trackNameWS, worldName, spawnPoint_x, spawnPoint_y, spawnPoint_z, " +
            "spawnPoint_yaw, spawnPoint_pitch, creatorName, icon_name FROM fr_tracks";
        try {
            Connection conn = getOrConnect();
            try (
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
            ) {
                while (rs.next()) {
                    String trackName = rs.getString("trackName"); // Nome ORIGINAL com formatação
                    String trackNameWS = rs.getString("trackNameWS");
                    String worldName = rs.getString("worldName");

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[getAllTracksWithData] Mundo '" +
                                    worldName +
                                    "' não encontrado para pista: " +
                                    trackName
                            );
                        continue;
                    }

                    Location spawnLocation = new Location(
                        world,
                        rs.getDouble("spawnPoint_x"),
                        rs.getDouble("spawnPoint_y"),
                        rs.getDouble("spawnPoint_z"),
                        rs.getFloat("spawnPoint_yaw"),
                        rs.getFloat("spawnPoint_pitch")
                    );

                    // ✅ USA trackName ORIGINAL como chave (para exibir no menu)
                    trackDataMap.put(
                        trackName,
                        new TrackData(
                            trackName, // Nome original para exibição
                            spawnLocation,
                            worldName,
                            rs.getString("creatorName"),
                            rs.getString("icon_name") != null
                                ? rs.getString("icon_name")
                                : "N/A",
                            getCheckpointCount(
                                trackNameWS.replaceAll("\\s+", "").toLowerCase()
                            ) // Normaliza ao buscar checkpoints
                        )
                    );
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }

        plugin
            .getDebugManager()
            .logDatabaseOperation(
                "[getAllTracksWithData] Carregou " +
                    trackDataMap.size() +
                    " pistas"
            );
        return trackDataMap;
    }

    public synchronized void setTrackOpen(String trackName, boolean open) {
        String sql =
            "UPDATE fr_tracks SET open = ? WHERE LOWER(trackNameWS) = LOWER(?)";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, open);
                ps.setString(2, trackName.replaceAll("\\s+", "").toLowerCase());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    /**
     * Salva os tempos de checkpoint com mecanismo de recuperação em caso de erro de Constraint.
     */
    public void saveCheckpointTimes(
        Connection conn,
        UUID playerUUID,
        String trackName,
        Integer newTimetrialId,
        double newTime,
        int newCheckpointsCount,
        boolean newFinished
    ) throws SQLException {
        try {
            // ✅ Remove apenas espaços, sem toLowerCase
            String trackNameWS = trackName.replaceAll("\\s+", "");
            double roundedNewTime = Math.round(newTime * 1000.0) / 1000.0;

            List<TimerUtils.CheckpointData> newCheckpoints = plugin
                .getTimerUtils()
                .getTempCheckpoints(playerUUID);

            // DEBUG: Log detalhado
            if (plugin.getDebugManager().isTimeTrialSystemEnabled()) {
                if (newCheckpoints == null) {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "§c[DB] getTempCheckpoints retornou NULL para " +
                                trackNameWS
                        );
                } else if (newCheckpoints.isEmpty()) {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "§c[DB] getTempCheckpoints retornou VAZIO para " +
                                trackNameWS
                        );
                } else {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "§e[DB] Total de checkpoints temporários: " +
                                newCheckpoints.size()
                        );
                }
            }

            if (newCheckpoints == null || newCheckpoints.isEmpty()) {
                return;
            }

            // 🔹 CORREÇÃO CRÍTICA: Filtra apenas os checkpoints da pista atual!
            // Isso evita salvar checkpoints de outras pistas quando o jogador troca de track
            List<TimerUtils.CheckpointData> filteredCheckpoints =
                new ArrayList<>();
            for (TimerUtils.CheckpointData cp : newCheckpoints) {
                if (
                    cp.getTrack().replace(" ", "").equalsIgnoreCase(trackNameWS)
                ) {
                    filteredCheckpoints.add(cp);
                }
            }

            // DEBUG: Log após filtro
            if (plugin.getDebugManager().isTimeTrialSystemEnabled()) {
                plugin
                    .getDebugManager()
                    .logTimeTrialSystem(
                        "§a[DB] Após filtro: " +
                            filteredCheckpoints.size() +
                            " checkpoints para " +
                            trackNameWS +
                            " (timetrial_id=" +
                            newTimetrialId +
                            ")"
                    );
                for (TimerUtils.CheckpointData cp : filteredCheckpoints) {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "  → CP " +
                                cp.getId() +
                                " @ " +
                                String.format("%.3f", cp.getTime()) +
                                "s"
                        );
                }
            }

            if (filteredCheckpoints.isEmpty()) {
                plugin
                    .getDebugManager()
                    .logTimeTrialSystem(
                        "§c[DB] Nenhum checkpoint da pista " +
                            trackNameWS +
                            " encontrado! Checkpoints são de outras pistas."
                    );
                return;
            }

            // Usa a lista filtrada em vez da original
            newCheckpoints = filteredCheckpoints;

            // --- LÓGICA DE COMPARAÇÃO ---
            // 🔹 CORREÇÃO CRÍTICA: Busca o melhor tempo COMPLETO (finished=1) QUE TEM CHECKPOINTS SALVOS
            // Ignora registros órfãos (tempos sem checkpoints)
            Integer oldTimetrialId = null;
            double oldTime = Double.MAX_VALUE;
            int oldCheckpoints = 0;
            boolean oldFinished = false;

            String sqlQueryOld =
                "SELECT pt.id, pt.bestTime, pt.checkpointsReached, pt.finished " +
                "FROM fr_player_times pt " +
                "INNER JOIN fr_checkpoint_times ct ON ct.timetrial_id = pt.id " +
                "WHERE pt.player_uuid = ? AND pt.trackNameWS = ? AND pt.finished = 1 " +
                "GROUP BY pt.id " +
                "ORDER BY pt.bestTime ASC LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sqlQueryOld)) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        oldTimetrialId = rs.getInt("id");
                        oldTime = rs.getDouble("bestTime");
                        oldCheckpoints = rs.getInt("checkpointsReached");
                        oldFinished = rs.getBoolean("finished");

                        if (
                            plugin.getDebugManager().isTimeTrialSystemEnabled()
                        ) {
                            plugin
                                .getDebugManager()
                                .logTimeTrialSystem(
                                    "§e[DB] Tempo anterior COM checkpoints: id=" +
                                        oldTimetrialId +
                                        ", tempo=" +
                                        oldTime +
                                        "s"
                                );
                        }
                    } else {
                        if (
                            plugin.getDebugManager().isTimeTrialSystemEnabled()
                        ) {
                            plugin
                                .getDebugManager()
                                .logTimeTrialSystem(
                                    "§a[DB] Nenhum tempo anterior COM checkpoints → Salvando PRIMEIRO tempo válido!"
                                );
                        }
                    }
                }
            }

            boolean newIsBetter =
                (oldTimetrialId == null) ||
                (!oldFinished && newFinished) ||
                (oldFinished && newFinished && roundedNewTime <= oldTime) ||
                (!oldFinished &&
                    !newFinished &&
                    (newCheckpointsCount > oldCheckpoints ||
                        (newCheckpointsCount == oldCheckpoints &&
                            roundedNewTime <= oldTime)));

            if (plugin.getDebugManager().isTimeTrialSystemEnabled()) {
                if (newIsBetter) {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "§a[DB] Novo tempo É MELHOR → Salvando checkpoints!"
                        );
                } else {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "§c[DB] Novo tempo NÃO é melhor → Descartando checkpoints (old=" +
                                oldTime +
                                "s, new=" +
                                roundedNewTime +
                                "s)"
                        );
                }
            }

            if (!newIsBetter) {
                return;
            }

            // 🔹 NOVO: Deleta checkpoints do tempo ANTERIOR (antigo recorde) antes de salvar o novo
            if (oldTimetrialId != null) {
                String sqlDeleteOld =
                    "DELETE FROM fr_checkpoint_times WHERE timetrial_id = ?";
                try (
                    PreparedStatement psDel = conn.prepareStatement(
                        sqlDeleteOld
                    )
                ) {
                    psDel.setInt(1, oldTimetrialId);
                    int deleted = psDel.executeUpdate();

                    if (plugin.getDebugManager().isTimeTrialSystemEnabled()) {
                        plugin
                            .getDebugManager()
                            .logTimeTrialSystem(
                                "§c[DB] Checkpoints antigos deletados (timetrial_id=" +
                                    oldTimetrialId +
                                    ", total=" +
                                    deleted +
                                    ")"
                            );
                    }
                } catch (SQLException e) {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "⚠️ Falha ao deletar checkpoints antigos (id=" +
                                oldTimetrialId +
                                "): " +
                                e.getMessage()
                        );
                }
            }

            String sqlInsert =
                "INSERT OR REPLACE INTO fr_checkpoint_times (timetrial_id, player_uuid, checkpointId, time, trackNameWS) VALUES (?, ?, ?, ?, ?)";

            try {
                executeCheckpointBatch(
                    conn,
                    sqlInsert,
                    newCheckpoints,
                    playerUUID,
                    trackNameWS,
                    newTimetrialId
                );

                if (plugin.getDebugManager().isTimeTrialSystemEnabled()) {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "§a[DB] ✅ Checkpoints SALVOS com sucesso! (timetrial_id=" +
                                newTimetrialId +
                                ", total=" +
                                newCheckpoints.size() +
                                ")"
                        );
                }
                clearCheckpointsCache(trackNameWS); // Added
            } catch (SQLException e) {
                if (
                    e.getMessage().contains("CONSTRAINT") ||
                    e.getMessage().contains("UNIQUE")
                ) {
                    plugin
                        .getDebugManager()
                        .logDatabaseOperation(
                            "[FormulaRacing] Conflito de PK detectado para " +
                                playerUUID +
                                ". Tentando recuperação forçada..."
                        );

                    String sqlForceDelete =
                        "DELETE FROM fr_checkpoint_times WHERE player_uuid = ? AND LOWER(trackNameWS) = LOWER(?)";
                    try (
                        PreparedStatement psDel = conn.prepareStatement(
                            sqlForceDelete
                        )
                    ) {
                        psDel.setString(1, playerUUID.toString());
                        psDel.setString(2, trackNameWS);
                        psDel.executeUpdate();
                    }

                    // Tenta inserir novamente após o delete forçado
                    executeCheckpointBatch(
                        conn,
                        sqlInsert,
                        newCheckpoints,
                        playerUUID,
                        trackNameWS,
                        newTimetrialId
                    );
                    clearCheckpointsCache(trackNameWS); // Added
                    plugin
                        .getDebugManager()
                        .logDatabaseOperation(
                            "[FormulaRacing] Recuperação concluída com sucesso."
                        );
                } else {
                    // Se for outro erro que não seja constraint, repassa para o log
                    throw e;
                }
            }
        } finally {
            plugin.getTimerUtils().clearTempCheckpoints(playerUUID);
        }
    }

    private void executeCheckpointBatch(
        Connection conn,
        String sql,
        List<TimerUtils.CheckpointData> checkpoints,
        UUID uuid,
        String track,
        int tid
    ) throws SQLException {
        if (plugin.getDebugManager().isTimeTrialSystemEnabled()) {
            plugin
                .getDebugManager()
                .logTimeTrialSystem(
                    "§e[DB BATCH] Inserindo " +
                        checkpoints.size() +
                        " checkpoints para timetrial_id=" +
                        tid
                );
        }

        try (PreparedStatement psIns = conn.prepareStatement(sql)) {
            int count = 0;
            for (TimerUtils.CheckpointData cp : checkpoints) {
                psIns.setInt(1, tid);
                psIns.setString(2, uuid.toString());
                psIns.setInt(3, cp.getId());
                psIns.setDouble(4, Math.round(cp.getTime() * 1000.0) / 1000.0);
                psIns.setString(5, track);
                psIns.addBatch();
                count++;
            }

            int[] results = psIns.executeBatch();

            if (plugin.getDebugManager().isTimeTrialSystemEnabled()) {
                int successCount = 0;
                for (int result : results) {
                    if (result >= 0) successCount++;
                }
                plugin
                    .getDebugManager()
                    .logTimeTrialSystem(
                        "§a[DB BATCH] Batch executado: " +
                            successCount +
                            "/" +
                            count +
                            " inserções bem-sucedidas"
                    );
            }
        }
    }

    /**
     * 🧹 Remove checkpoints órfãos (timetrial_id não existe mais em fr_player_times)
     * Garante integridade referencial no banco de dados
     */
    public synchronized void cleanOrphanedCheckpoints() {
        String sql =
            "DELETE FROM fr_checkpoint_times WHERE timetrial_id NOT IN (SELECT id FROM fr_player_times)";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int deleted = ps.executeUpdate();

                if (deleted > 0) {
                    plugin
                        .getDebugManager()
                        .logDatabaseOperation(
                            "[FormulaRacing] 🧹 Limpeza de checkpoints órfãos: " +
                                deleted +
                                " registros removidos."
                        );
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "⚠️ Erro ao limpar checkpoints órfãos: " + e.getMessage()
                );
            handleSqlError(e);
        }
    }

    public void setDuelState(int duelId, String newState) {
        String sql = newState.equalsIgnoreCase("FINISHED")
            ? "UPDATE fr_timetrial_duels SET state = ?, finished_in = CURRENT_TIMESTAMP WHERE id = ?"
            : "UPDATE fr_timetrial_duels SET state = ? WHERE id = ?";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) {
                // Sincronização mesmo em task assíncrona
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
        String sql =
            "SELECT d.id FROM fr_timetrial_duel_players p " +
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

    public synchronized void saveFullTime(
        UUID playerUUID,
        String playerName,
        String trackName,
        double time,
        int checkpointsReached
    ) {
        String trackNameWS = trackName.replaceAll("\\s+", "");
        // Arredondamento para milissegundos (3 casas decimais) para suportar subtick
        double roundedTime = Math.round(time * 1000.0) / 1000.0;

        try {
            Connection conn = getOrConnect();

            // --- LÓGICA DE RECORDE (DISCORD) ---
            String prevBestPlayer = null;
            double prevBestTime = Double.MAX_VALUE;
            double playerOwnBestTime = Double.MAX_VALUE;
            double playerBestTimeWithCheckpoints = Double.MAX_VALUE;
            boolean isNewGlobalRecord = false;

            plugin
                .getDebugManager()
                .logTimeTrialSystem(
                    "[saveFullTime] Verificando recorde para " +
                        playerName +
                        " na pista " +
                        trackName +
                        " com tempo " +
                        roundedTime
                );

            // PRIMEIRO: Busca o melhor tempo do PRÓPRIO jogador (APENAS voltas COMPLETAS)
            String ownBestSql =
                "SELECT bestTime FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?) AND player_uuid = ? AND finished = TRUE ORDER BY bestTime ASC LIMIT 1";
            try (PreparedStatement psOwn = conn.prepareStatement(ownBestSql)) {
                psOwn.setString(1, trackNameWS);
                psOwn.setString(2, playerUUID.toString());
                try (ResultSet rsOwn = psOwn.executeQuery()) {
                    if (rsOwn.next()) {
                        playerOwnBestTime = rsOwn.getDouble("bestTime");
                        plugin
                            .getDebugManager()
                            .logTimeTrialSystem(
                                "[saveFullTime] Melhor tempo anterior do jogador: " +
                                    playerOwnBestTime
                            );
                    }
                }
            }

            // NOVO: Busca o melhor tempo do jogador QUE TEM CHECKPOINTS SALVOS
            String ownBestWithCheckpointsSql =
                "SELECT pt.bestTime " +
                "FROM fr_player_times pt " +
                "INNER JOIN fr_checkpoint_times ct ON ct.timetrial_id = pt.id " +
                "WHERE pt.player_uuid = ? AND pt.trackNameWS = ? AND pt.finished = TRUE " +
                "GROUP BY pt.id " +
                "ORDER BY pt.bestTime ASC LIMIT 1";
            try (
                PreparedStatement psCheckpoints = conn.prepareStatement(
                    ownBestWithCheckpointsSql
                )
            ) {
                psCheckpoints.setString(1, playerUUID.toString());
                psCheckpoints.setString(2, trackNameWS);
                try (ResultSet rsCheckpoints = psCheckpoints.executeQuery()) {
                    if (rsCheckpoints.next()) {
                        playerBestTimeWithCheckpoints = rsCheckpoints.getDouble(
                            "bestTime"
                        );
                        plugin
                            .getDebugManager()
                            .logTimeTrialSystem(
                                "[saveFullTime] Melhor tempo COM checkpoints do jogador: " +
                                    playerBestTimeWithCheckpoints
                            );
                    }
                }
            }

            // SE O TEMPO NÃO É MELHOR QUE O PRÓPRIO TEMPO DO JOGADOR, NÃO SALVA O TEMPO
            // MAS AINDA PODE SALVAR CHECKPOINTS SE FOR MELHOR QUE O TEMPO COM CHECKPOINTS
            boolean shouldSaveTime = roundedTime < playerOwnBestTime;
            boolean shouldSaveCheckpoints =
                roundedTime < playerBestTimeWithCheckpoints;

            if (!shouldSaveTime && !shouldSaveCheckpoints) {
                plugin
                    .getDebugManager()
                    .logTimeTrialSystem(
                        "[saveFullTime] ⏭️ Tempo não é melhor que o recorde (" +
                            playerOwnBestTime +
                            ") nem que o melhor com checkpoints (" +
                            playerBestTimeWithCheckpoints +
                            "). Não salvando."
                    );
                return;
            }

            if (!shouldSaveTime && shouldSaveCheckpoints) {
                plugin
                    .getDebugManager()
                    .logTimeTrialSystem(
                        "[saveFullTime] ✅ Tempo não é recorde, mas é melhor que o melhor com checkpoints. Salvando APENAS checkpoints para delta."
                    );
            }

            // SEGUNDO: Busca o melhor tempo GLOBAL (de qualquer jogador, APENAS voltas COMPLETAS) - APENAS SE SALVAR TEMPO
            if (shouldSaveTime) {
                String globalRecordSql =
                    "SELECT player_name, bestTime FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?) AND finished = TRUE ORDER BY bestTime ASC LIMIT 1";
                try (
                    PreparedStatement psCheck = conn.prepareStatement(
                        globalRecordSql
                    )
                ) {
                    psCheck.setString(1, trackNameWS);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            prevBestTime = rs.getDouble("bestTime");
                            prevBestPlayer = rs.getString("player_name");
                            plugin
                                .getDebugManager()
                                .logTimeTrialSystem(
                                    "[saveFullTime] Melhor tempo GLOBAL da pista: " +
                                        prevBestPlayer +
                                        " = " +
                                        prevBestTime
                                );

                            // É recorde APENAS se:
                            // 1. Tempo novo < tempo do recorde global atual
                            // 2. E o recorde global NÃO é do próprio jogador (ou é, mas melhorou)
                            if (roundedTime < (prevBestTime - 0.001)) {
                                // Bateu o recorde global
                                if (prevBestPlayer.equals(playerName)) {
                                    // É o próprio jogador, melhorando seu próprio recorde
                                    plugin
                                        .getDebugManager()
                                        .logTimeTrialSystem(
                                            "[saveFullTime] ✅ Jogador melhorou seu PRÓPRIO recorde!"
                                        );
                                } else {
                                    // Bateu o recorde de OUTRO jogador
                                    plugin
                                        .getDebugManager()
                                        .logTimeTrialSystem(
                                            "[saveFullTime] ✅ NOVO RECORDE! " +
                                                playerName +
                                                " bateu o tempo de " +
                                                prevBestPlayer
                                        );
                                }
                                isNewGlobalRecord = true;
                            } else {
                                plugin
                                    .getDebugManager()
                                    .logTimeTrialSystem(
                                        "[saveFullTime] ❌ Não é recorde global. Tempo atual: " +
                                            roundedTime +
                                            " vs melhor: " +
                                            prevBestTime
                                    );
                            }
                        } else {
                            // Nenhum tempo na pista ainda = primeiro recorde!
                            plugin
                                .getDebugManager()
                                .logTimeTrialSystem(
                                    "[saveFullTime] ✅ PRIMEIRO RECORDE da pista!"
                                );
                            isNewGlobalRecord = true;
                        }
                    }
                }

                if (isNewGlobalRecord) {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "[saveFullTime] 📢 Enviando mensagem de recorde para o Discord!"
                        );

                    // ✅ CORREÇÃO: Busca o nome de exibição (display name) da pista para o Discord
                    String displayTrackName = trackName;
                    try (
                        PreparedStatement psName = conn.prepareStatement(
                            "SELECT trackName FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?) LIMIT 1"
                        )
                    ) {
                        psName.setString(1, trackNameWS);
                        try (ResultSet rsName = psName.executeQuery()) {
                            if (rsName.next()) {
                                displayTrackName = rsName.getString(
                                    "trackName"
                                );
                            }
                        }
                    } catch (SQLException ignored) {
                        // Se falhar o SELECT do nome, usa o que já tem (fail-safe)
                    }

                    DiscordUtils.sendRecordMessage(
                        plugin,
                        playerName,
                        roundedTime,
                        prevBestPlayer,
                        (prevBestTime == Double.MAX_VALUE ? 0 : prevBestTime),
                        displayTrackName
                    );
                } else {
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "[saveFullTime] Não enviando para Discord (não é recorde global)"
                        );
                }
            }

            // --- TRANSAÇÃO DE ESCRITA ---
            try {
                conn.setAutoCommit(false);

                Integer generatedId = null;

                if (shouldSaveTime) {
                    // Salva o tempo normalmente (é um novo recorde pessoal)
                    String insertSql =
                        "INSERT INTO fr_player_times (trackNameWS, player_uuid, player_name, bestTime, checkpointsReached, finished) VALUES (?, ?, ?, ?, ?, ?)";
                    try (
                        PreparedStatement ps = conn.prepareStatement(
                            insertSql,
                            Statement.RETURN_GENERATED_KEYS
                        )
                    ) {
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
                } else if (shouldSaveCheckpoints) {
                    // Não é recorde pessoal, mas é melhor que o tempo com checkpoints
                    // Cria um registro separado APENAS para os checkpoints (não conta como recorde oficial)
                    String insertSql =
                        "INSERT INTO fr_player_times (trackNameWS, player_uuid, player_name, bestTime, checkpointsReached, finished) VALUES (?, ?, ?, ?, ?, ?)";
                    try (
                        PreparedStatement ps = conn.prepareStatement(
                            insertSql,
                            Statement.RETURN_GENERATED_KEYS
                        )
                    ) {
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
                    plugin
                        .getDebugManager()
                        .logTimeTrialSystem(
                            "[saveFullTime] ✅ Registro criado para checkpoints (não é PB, mas tem checkpoints melhores)"
                        );
                }

                if (generatedId != null) {
                    saveCheckpointTimes(
                        conn,
                        playerUUID,
                        trackNameWS,
                        generatedId,
                        roundedTime,
                        checkpointsReached,
                        true
                    );
                }

                conn.commit();
            } catch (Exception e) {
                if (conn != null) conn.rollback();
                plugin
                    .getDebugManager()
                    .logDatabaseOperation(
                        "❌ Erro na transação de FullTime: " + e.getMessage()
                    );
            } finally {
                if (conn != null && !conn.isClosed()) conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public synchronized void savePartialTime(
        UUID playerUUID,
        String playerName,
        String trackName,
        double time,
        int lastCheckpoint
    ) {
        if (lastCheckpoint <= 0) return;
        String trackNameWS = trackName.replaceAll("\\s+", "");
        double roundedTime = Math.round(time * 1000.0) / 1000.0;

        try {
            Connection conn = getOrConnect();

            try {
                conn.setAutoCommit(false);

                double prevTime = Double.MAX_VALUE;
                String sqlCheck =
                    "SELECT bestTime FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?) AND player_uuid = ? AND checkpointsReached = ? AND finished = FALSE ORDER BY bestTime ASC LIMIT 1";

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
                    String insertSql =
                        "INSERT INTO fr_player_times (trackNameWS, player_uuid, player_name, bestTime, checkpointsReached, finished) VALUES (?, ?, ?, ?, ?, FALSE)";

                    try (
                        PreparedStatement ps = conn.prepareStatement(
                            insertSql,
                            Statement.RETURN_GENERATED_KEYS
                        )
                    ) {
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
                        saveCheckpointTimes(
                            conn,
                            playerUUID,
                            trackNameWS,
                            generatedId,
                            roundedTime,
                            lastCheckpoint,
                            false
                        );
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
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "❌ Erro ao salvar tempo parcial: " + e.getMessage()
                );
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized boolean isTrackOpen(String trackName) {
        if (trackName == null || trackName.isBlank()) return false;
        String sql =
            "SELECT open FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?) LIMIT 1";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
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
        String sql =
            "INSERT INTO fr_cameras(id, trackNameWS, x, y, z, yaw, pitch) " +
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
                ps.setString(2, trackName.replaceAll("\\s+", "").toLowerCase());
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

        // ✅ Remove apenas espaços, sem toLowerCase
        String trackNameWS = trackName.replaceAll("\\s+", "");
        String sql =
            "SELECT x, y, z FROM fr_cameras WHERE LOWER(trackNameWS) = LOWER(?)";

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
                        World defaultWorld = plugin
                            .getServer()
                            .getWorlds()
                            .get(0);
                        cameras.add(new Location(defaultWorld, x, y, z));
                    }
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao listar câmeras: " + e.getMessage()
                );
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
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Erro ao definir selectedEvent: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
    }

    public synchronized Object[] getPlayerBestTime(
        String playerName,
        String trackName
    ) {
        String sql = """
                SELECT pt.bestTime, pt.checkpointsReached, pt.finished, pt.created_at
                FROM fr_player_times pt
                WHERE pt.player_name = ?
                  AND LOWER(pt.trackNameWS) = LOWER(?)
                  AND pt.finished = TRUE
                ORDER BY pt.bestTime ASC
                LIMIT 1
            """;
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerName);
                ps.setString(2, trackName.replaceAll("\\s+", ""));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Object[] {
                            rs.getDouble("bestTime"),
                            rs.getInt("checkpointsReached"),
                            rs.getBoolean("finished"),
                            rs.getTimestamp("created_at"),
                        };
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized int getPlayerRank(UUID playerUUID, String trackNameWS) {
        // 1. Verificar se o jogador tem tempo
        String checkTimeSql =
            "SELECT 1 FROM fr_player_times WHERE player_uuid = ? AND LOWER(trackNameWS) = LOWER(?) AND finished = TRUE LIMIT 1";

        try {
            Connection conn = getOrConnect();
            try (
                PreparedStatement psCheck = conn.prepareStatement(checkTimeSql)
            ) {
                psCheck.setString(1, playerUUID.toString());
                psCheck.setString(2, trackNameWS.replaceAll("\\s+", "")); // Garantir WS
                try (ResultSet rsCheck = psCheck.executeQuery()) {
                    if (!rsCheck.next()) return 0; // Sem tempo = Sem Rank
                }
            }

            // 2. Calcular Rank: Contar quantos jogadores ÚNICOS têm tempo melhor
            String sql = """
                    SELECT COUNT(DISTINCT player_uuid) + 1 AS rank
                    FROM fr_player_times
                    WHERE LOWER(trackNameWS) = LOWER(?)
                      AND finished = TRUE
                      AND bestTime < (
                          SELECT MIN(bestTime)
                          FROM fr_player_times
                          WHERE player_uuid = ?
                            AND LOWER(trackNameWS) = LOWER(?)
                            AND finished = TRUE
                      )
                """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String cleanTrack = trackNameWS.replaceAll("\\s+", "");
                ps.setString(1, cleanTrack);
                ps.setString(2, playerUUID.toString());
                ps.setString(3, cleanTrack);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("rank");
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return 0;
    }

    public synchronized List<TrackRecord> getTopTimes(String trackName) {
        List<TrackRecord> topTimes = new ArrayList<>();
        String trackWS = trackName.replaceAll("\\s+", "");
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
                WHERE LOWER(t.trackNameWS) = LOWER(?)
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
                        topTimes.add(
                            new TrackRecord(
                                rs.getString("player_name"),
                                rs.getDouble("bestTime"),
                                rs.getInt("checkpointsReached"),
                                rs.getBoolean("finished"),
                                rs.getString("created_at")
                            )
                        );
                    }
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao carregar top times: " + e.getMessage()
                );
            handleSqlError(e);
        }
        return topTimes;
    }

    public synchronized Double getBestTime(String trackName) {
        String sql =
            "SELECT bestTime FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?) AND finished = TRUE ORDER BY bestTime ASC LIMIT 1";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, trackName.replaceAll("\\s+", ""));
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
        // ✅ Usa LOWER() na query
        String sql =
            "SELECT trackName, worldName, spawnPoint_x, spawnPoint_y, spawnPoint_z, " +
            "spawnPoint_pitch, spawnPoint_yaw, creatorName, creatorUUID, icon_name " +
            "FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?)";
        try {
            Connection conn = getOrConnect();

            // 1. Tenta buscar pelo nome exato (display name) primeiro - Resolve conflitos de "Floor is Lava" vs "floorislava"
            String sqlExact =
                "SELECT trackName, worldName, spawnPoint_x, spawnPoint_y, spawnPoint_z, " +
                "spawnPoint_pitch, spawnPoint_yaw, creatorName, creatorUUID, icon_name, trackNameWS " +
                "FROM fr_tracks WHERE LOWER(trackName) = LOWER(?)";

            try (PreparedStatement ps = conn.prepareStatement(sqlExact)) {
                ps.setString(1, trackName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return createTrackDataFromResultSet(
                            rs,
                            rs.getString("trackNameWS")
                        );
                    }
                }
            }

            // 2. Fallback: Busca pelo trackNameWS (lógica antiga)
            // ✅ Remove apenas espaços, sem toLowerCase para montar o critério de busca (mas a query usa LOWER)
            String trackNameWS = trackName.replaceAll("\\s+", "");
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String trackNameOriginal = rs.getString("trackName"); // Nome original da pista
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
                            trackNameOriginal, // ✅ Nome original como primeiro parâmetro
                            spawnLocation,
                            worldName,
                            rs.getString("creatorName"),
                            rs.getString("icon_name"),
                            getCheckpointCount(trackNameWS)
                        );
                    }
                }
            } catch (SQLException e) {
                handleSqlError(e);
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Location getTrackSpawn(String trackName) {
        if (trackName == null || trackName.isEmpty()) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[getTrackSpawn] trackName é null ou vazio!"
                );
            return null;
        }

        // ✅ Remove apenas os espaços, mantém o case original
        String trackNameWS = trackName.replaceAll("\\s+", "");

        // ✅ Usa LOWER() na query para comparação case-insensitive
        String sql =
            "SELECT spawnPoint_x, spawnPoint_y, spawnPoint_z, spawnPoint_yaw, spawnPoint_pitch, worldName " +
            "FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?) LIMIT 1";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String worldName = rs.getString("worldName");

                        World world = Bukkit.getWorld(worldName);
                        if (world == null) {
                            plugin
                                .getDebugManager()
                                .logDatabaseOperation(
                                    "[getTrackSpawn] Mundo '" +
                                        worldName +
                                        "' não encontrado no servidor!"
                                );
                            return null;
                        }

                        Location loc = new Location(
                            world,
                            rs.getDouble("spawnPoint_x"),
                            rs.getDouble("spawnPoint_y"),
                            rs.getDouble("spawnPoint_z"),
                            rs.getFloat("spawnPoint_yaw"),
                            rs.getFloat("spawnPoint_pitch")
                        );

                        return loc;
                    } else {
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[getTrackSpawn] Nenhuma pista encontrada com trackNameWS = '" +
                                    trackNameWS +
                                    "'"
                            );
                    }
                }
            } catch (SQLException e) {
                plugin
                    .getDebugManager()
                    .logDatabaseOperation(
                        "[getTrackSpawn] Erro ao buscar spawn: " +
                            e.getMessage()
                    );
                handleSqlError(e);
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[getTrackSpawn] Erro na conexão: " + e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    /* =======================================================
          MÉTODOS DE RESET E EXCLUSÃO DE TEMPOS
======================================================= */

    public synchronized void resetPlayerTimes(
        String playerUUID,
        String trackNameWS
    ) {
        String trackNormalized = trackNameWS.replaceAll("\\s+", "");
        String sqlPlayerTimes =
            "DELETE FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?) AND player_uuid = ?";
        String sqlCheckpointTimes =
            "DELETE FROM fr_checkpoint_times WHERE LOWER(trackNameWS) = LOWER(?) AND player_uuid = ?";

        try {
            Connection conn = getOrConnect();
            try (
                PreparedStatement psPlayer = conn.prepareStatement(
                    sqlPlayerTimes
                )
            ) {
                psPlayer.setString(1, trackNormalized);
                psPlayer.setString(2, playerUUID);
                psPlayer.executeUpdate();
            }
            try (
                PreparedStatement psCheckpoint = conn.prepareStatement(
                    sqlCheckpointTimes
                )
            ) {
                psCheckpoint.setString(1, trackNormalized);
                psCheckpoint.setString(2, playerUUID);
                psCheckpoint.executeUpdate();
            }
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Tempos do jogador '" +
                        playerUUID +
                        "' na pista '" +
                        trackNormalized +
                        "' resetados."
                );
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Erro ao resetar tempos: " + e.getMessage()
                );
            handleSqlError(e);
        }
    }

    public synchronized boolean deletePlayerBestTimeOnTrack(
        String trackName,
        String playerName
    ) {
        String trackWS = trackName.replaceAll("\\s+", "").toLowerCase();

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
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Erro ao deletar melhor tempo: " +
                        e.getMessage()
                );
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

    public synchronized boolean deleteAllTimes(
        String trackName,
        String player
    ) {
        boolean hasPlayer = (player != null && !player.isEmpty());
        String sql = hasPlayer
            ? "DELETE FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?) AND player_name = ?"
            : "DELETE FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?)";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackName.replaceAll("\\s+", ""));
                if (hasPlayer) ps.setString(2, player);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    public synchronized boolean resetAllTrackTimes(String trackNameWS) {
        String trackNormalized = trackNameWS.replaceAll("\\s+", "");
        String deletePlayerTimesSql =
            "DELETE FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?)";
        String sqlCheckpointTimes =
            "DELETE FROM fr_checkpoint_times WHERE LOWER(trackNameWS) = LOWER(?)";

        try {
            Connection conn = getOrConnect();
            try (
                PreparedStatement psPlayer = conn.prepareStatement(
                    deletePlayerTimesSql
                )
            ) {
                psPlayer.setString(1, trackNormalized);
                psPlayer.executeUpdate();
            }
            try (
                PreparedStatement psCheckpoint = conn.prepareStatement(
                    sqlCheckpointTimes
                )
            ) {
                psCheckpoint.setString(1, trackNormalized);
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

    public synchronized boolean saveHologramLocation(
        String trackName,
        Location location
    ) {
        if (trackName == null || location == null) return false;

        String trackNameWS = trackName.replaceAll("\\s+", "");
        String sqlSelect =
            "SELECT x, y, z, world FROM fr_holograms WHERE LOWER(trackNameWS) = LOWER(?)";
        String sqlInsertOrUpdate =
            "INSERT INTO fr_holograms (trackNameWS, world, x, y, z) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z";

        try {
            Connection conn = getOrConnect();
            boolean needsUpdate = true;

            try (
                PreparedStatement psSelect = conn.prepareStatement(sqlSelect)
            ) {
                psSelect.setString(1, trackNameWS);
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        double oldX = rs.getDouble("x");
                        double oldY = rs.getDouble("y");
                        double oldZ = rs.getDouble("z");
                        String oldWorld = rs.getString("world");

                        if (
                            oldX == location.getX() &&
                            oldY == location.getY() &&
                            oldZ == location.getZ() &&
                            oldWorld.equals(location.getWorld().getName())
                        ) {
                            needsUpdate = false;
                        }
                    }
                }
            }

            if (!needsUpdate) return false;

            try (
                PreparedStatement psUpdate = conn.prepareStatement(
                    sqlInsertOrUpdate
                )
            ) {
                psUpdate.setString(1, trackNameWS);
                psUpdate.setString(2, location.getWorld().getName());
                psUpdate.setDouble(3, location.getX());
                psUpdate.setDouble(4, location.getY());
                psUpdate.setDouble(5, location.getZ());

                int rows = psUpdate.executeUpdate();
                if (rows > 0) {
                    plugin
                        .getDebugManager()
                        .logDatabaseOperation(
                            "[FormulaRacing] Holograma da pista '" +
                                trackName +
                                "' salvo/atualizado com sucesso."
                        );
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[FormulaRacing] Erro ao salvar holograma: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return false;
    }

    public synchronized String getIcon(String trackName) {
        String trackNameNoSpaces = trackName.replaceAll("\\s+", "");
        String sql =
            "SELECT icon_name FROM fr_tracks WHERE LOWER(trackNameWS) = LOWER(?)";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameNoSpaces);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("icon_name");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar ícone: " + e.getMessage()
                );
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
        String trackNameWS = trackName.replaceAll("\\s+", "");
        String sql =
            "SELECT world, x, y, z FROM fr_holograms WHERE LOWER(trackNameWS) = LOWER(?)";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        World world = Bukkit.getWorld(rs.getString("world"));
                        if (world == null) return null;
                        return new Location(
                            world,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public synchronized void setStepHigh(
        String trackNameWS,
        double stepHeight
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, stepHeight) VALUES (?, ?) " +
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
        String sql =
            "SELECT stepHeight FROM fr_boatutils WHERE trackNameWS = ?";
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

    public synchronized void setDefaultSlipperiness(
        String trackNameWS,
        double value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, defaultSlipperiness) VALUES (?, ?) " +
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
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, fallDamage) VALUES (?, ?) " +
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

    public synchronized void setWaterElevation(
        String trackNameWS,
        boolean value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, waterElevation) VALUES (?, ?) " +
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
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, jumpForce) VALUES (?, ?) " +
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
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, gravity) VALUES (?, ?) " +
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

    public synchronized void setYawAcceleration(
        String trackNameWS,
        double value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, yawAcceleration) VALUES (?, ?) " +
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

    public synchronized void setForwardAcceleration(
        String trackNameWS,
        double value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, forwardAcceleration) VALUES (?, ?) " +
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

    public synchronized void setBackwardAcceleration(
        String trackNameWS,
        double value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, backwardAcceleration) VALUES (?, ?) " +
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

    public synchronized void setTurningForwardAcceleration(
        String trackNameWS,
        double value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, turningForwardAcceleration) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET turningForwardAcceleration = excluded.turningForwardAcceleration";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setAllowAccelerationStacking(
        String trackNameWS,
        boolean value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, allowAccelerationStacking) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET allowAccelerationStacking = excluded.allowAccelerationStacking";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setUnderwaterControl(
        String trackNameWS,
        boolean value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, underwaterControl) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET underwaterControl = excluded.underwaterControl";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setSurfaceWaterControl(
        String trackNameWS,
        boolean value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, surfaceWaterControl) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET surfaceWaterControl = excluded.surfaceWaterControl";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setCoyoteTime(String trackNameWS, int value) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, coyoteTime) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET coyoteTime = excluded.coyoteTime";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setInt(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setWaterJumping(
        String trackNameWS,
        boolean value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, waterJumping) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET waterJumping = excluded.waterJumping";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setSwimForce(String trackNameWS, double value) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, swimForce) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET swimForce = excluded.swimForce";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setCollisionMode(String trackNameWS, int value) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, collisionMode) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET collisionMode = excluded.collisionMode";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setInt(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setAirStepping(String trackNameWS, boolean value) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, airStepping) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET airStepping = excluded.airStepping";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized void setTenStepInterpolation(
        String trackNameWS,
        boolean value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, tenStepInterpolation) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET tenStepInterpolation = excluded.tenStepInterpolation";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setBoolean(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
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
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            handleSqlError(e);
        }
    }

    /* =======================================================
           MÉTODOS DE CONFIGURAÇÃO GERAL (BOAT UTILS)
======================================================= */

    public synchronized boolean replaceAllBoatUtilsSettings(
        String trackNameWS,
        float stepHeight,
        float defaultSlipperiness,
        boolean fallDamage,
        boolean waterElevation,
        boolean airControl,
        float jumpForce,
        double gravity,
        float yawAcceleration,
        float forwardAcceleration,
        float backwardAcceleration,
        float turningForwardAcceleration,
        boolean allowAccelerationStacking,
        boolean underwaterControl,
        boolean surfaceWaterControl,
        int coyoteTime,
        boolean waterJumping,
        float swimForce,
        int collisionMode,
        boolean airStepping,
        boolean tenStepInterpolation,
        int collisionResolution,
        String customSlipperiness,
        String perBlockSetting
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
            return true;
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            handleSqlError(e);
        }
        return false;
    }

    public synchronized void setCollisionResolution(
        String trackNameWS,
        int value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, collisionResolution) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET collisionResolution = excluded.collisionResolution;";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setInt(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized Double getDefaultSlipperiness(String trackNameWS) {
        String sql =
            "SELECT defaultSlipperiness FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("defaultSlipperiness");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getFallDamage(String trackNameWS) {
        String sql =
            "SELECT fallDamage FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("fallDamage");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getWaterElevation(String trackNameWS) {
        String sql =
            "SELECT waterElevation FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("waterElevation");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
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
            plugin
                .getDebugManager()
                .logDatabaseOperation("Database error: " + e.getMessage());
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
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar gravidade para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Double getYawAcceleration(String trackNameWS) {
        String sql =
            "SELECT yawAcceleration FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("yawAcceleration");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar aceleração de yaw para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Double getForwardAcceleration(String trackNameWS) {
        String sql =
            "SELECT forwardAcceleration FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("forwardAcceleration");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar aceleração frontal para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    /* =======================================================
           MÉTODOS DE FÍSICA E DUELO (CORRIGIDOS)
======================================================= */

    public synchronized Double getBackwardAcceleration(String trackNameWS) {
        String sql =
            "SELECT backwardAcceleration FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("backwardAcceleration");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar aceleração traseira para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Double getTurningForwardAcceleration(
        String trackNameWS
    ) {
        String sql =
            "SELECT turningForwardAcceleration FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble(
                        "turningForwardAcceleration"
                    );
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar aceleração de giro frontal para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getAllowAccelerationStacking(
        String trackNameWS
    ) {
        String sql =
            "SELECT allowAccelerationStacking FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean(
                        "allowAccelerationStacking"
                    );
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar empilhamento de aceleração para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getUnderwaterControl(String trackNameWS) {
        String sql =
            "SELECT underwaterControl FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("underwaterControl");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar controle subaquático para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Integer getCoyoteTime(String trackNameWS) {
        String sql =
            "SELECT coyoteTime FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("coyoteTime");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar coyote time para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getWaterJumping(String trackNameWS) {
        String sql =
            "SELECT waterJumping FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("waterJumping");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar pulo na água para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
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
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar força de nado para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized List<Player> getPlayersInDuel(int duelId) {
        List<Player> players = new ArrayList<>();
        String sql =
            "SELECT players FROM fr_timetrial_duel_players WHERE duel_id = ?";
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
                                    Player p = Bukkit.getPlayer(
                                        UUID.fromString(uuidStr.trim())
                                    );
                                    if (p != null) players.add(p);
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "Erro ao buscar jogadores do duelo " +
                        duelId +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return players;
    }

    /* =======================================================
           MÉTODOS DE DUELO E BOAT UTILS
======================================================= */

    public synchronized void createDuel(
        Player owner,
        List<Player> participants,
        String trackNameWS,
        int laps,
        int timeLimit,
        boolean lonely
    ) {
        String sqlDuel =
            "INSERT INTO fr_timetrial_duels (owner, trackNameWS, laps, time_limit, lonely, state ) VALUES (?, ?, ?, ?, ?, 'STARTED')";
        String sqlPlayers =
            "INSERT INTO fr_timetrial_duel_players (duel_id, players) VALUES (?, ?)";

        String playersString = participants
            .stream()
            .map(p -> p.getUniqueId().toString())
            .reduce((a, b) -> a + "," + b)
            .orElse("");

        try {
            Connection conn = getOrConnect();
            // Transação: Desativar auto-commit
            conn.setAutoCommit(false);

            try (
                PreparedStatement psDuel = conn.prepareStatement(
                    sqlDuel,
                    Statement.RETURN_GENERATED_KEYS
                )
            ) {
                psDuel.setString(1, owner.getUniqueId().toString());
                psDuel.setString(2, trackNameWS);
                psDuel.setInt(3, laps);
                psDuel.setDouble(4, (double) timeLimit);
                psDuel.setBoolean(5, lonely);

                psDuel.executeUpdate();

                try (ResultSet rs = psDuel.getGeneratedKeys()) {
                    if (rs.next()) {
                        int duelId = rs.getInt(1);
                        try (
                            PreparedStatement psPlayers = conn.prepareStatement(
                                sqlPlayers
                            )
                        ) {
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
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "[FormulaRacing] Erro ao registrar duelo: " + e.getMessage()
                );
            handleSqlError(e);
        }
    }

    public synchronized Integer getCollisionMode(String trackNameWS) {
        String sql =
            "SELECT collisionMode FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("collisionMode");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar modo de colisão para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getAirStepping(String trackNameWS) {
        String sql =
            "SELECT airStepping FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("airStepping");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar air stepping para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getTenStepInterpolation(String trackNameWS) {
        String sql =
            "SELECT tenStepInterpolation FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("tenStepInterpolation");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar interpolação de 10 passos para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Integer getCollisionResolution(String trackNameWS) {
        String sql =
            "SELECT collisionResolution FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("collisionResolution");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar resolução de colisão para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized String getPerBlockSetting(String trackNameWS) {
        String sql =
            "SELECT perBlockSetting FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("perBlockSetting");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar configuração por bloco para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Boolean getSurfaceWaterControl(String trackNameWS) {
        String sql =
            "SELECT surfaceWaterControl FROM fr_boatutils WHERE trackNameWS = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("surfaceWaterControl");
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar controle de água na superfície para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized Integer getPlayerTimeId(
        String trackNameWS,
        UUID playerUUID,
        double bestTime
    ) throws SQLException {
        String sql =
            "SELECT id FROM fr_player_times WHERE LOWER(trackNameWS) = LOWER(?) AND player_uuid = ? AND bestTime = ? LIMIT 1";

        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, trackNameWS.replaceAll("\\s+", ""));
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

    public synchronized boolean isBoatUtilsExclusive(String trackNameWS) {
        String trackNameNormalized = trackNameWS.replaceAll("\\s+", "");
        final String sql =
            "SELECT exclusiveMode FROM fr_boatutils WHERE LOWER(trackNameWS) = LOWER(?)";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameNormalized);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("exclusiveMode");
                    }
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return false;
    }

    public synchronized Map<String, Object> getBoatUtilsRaw(
        String trackNameWS
    ) {
        // ✅ Remove espaços do nome da pista para normalização
        String trackNameNormalized = trackNameWS.replaceAll("\\s+", "");
        final String sql =
            "SELECT * FROM fr_boatutils WHERE LOWER(trackNameWS) = LOWER(?)";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameNormalized);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;

                    Map<String, Object> map = new HashMap<>();
                    map.put("trackNameWS", rs.getString("trackNameWS"));
                    map.put("stepHeight", rs.getFloat("stepHeight"));
                    map.put(
                        "defaultSlipperiness",
                        rs.getFloat("defaultSlipperiness")
                    );
                    map.put("fallDamage", rs.getBoolean("fallDamage"));
                    map.put("waterElevation", rs.getBoolean("waterElevation"));
                    map.put("airControl", rs.getBoolean("airControl"));
                    map.put("jumpForce", rs.getFloat("jumpForce"));
                    map.put("gravity", rs.getDouble("gravity"));
                    map.put("yawAcceleration", rs.getFloat("yawAcceleration"));
                    map.put(
                        "forwardAcceleration",
                        rs.getFloat("forwardAcceleration")
                    );
                    map.put(
                        "backwardAcceleration",
                        rs.getFloat("backwardAcceleration")
                    );
                    map.put(
                        "turningForwardAcceleration",
                        rs.getFloat("turningForwardAcceleration")
                    );
                    map.put(
                        "allowAccelerationStacking",
                        rs.getBoolean("allowAccelerationStacking")
                    );
                    map.put(
                        "underwaterControl",
                        rs.getBoolean("underwaterControl")
                    );
                    map.put(
                        "surfaceWaterControl",
                        rs.getBoolean("surfaceWaterControl")
                    );
                    map.put("coyoteTime", rs.getInt("coyoteTime"));
                    map.put("waterJumping", rs.getBoolean("waterJumping"));
                    map.put("swimForce", rs.getFloat("swimForce"));
                    map.put("collisionMode", rs.getInt("collisionMode"));
                    map.put("airStepping", rs.getBoolean("airStepping"));
                    map.put(
                        "tenStepInterpolation",
                        rs.getBoolean("tenStepInterpolation")
                    );
                    map.put(
                        "collisionResolution",
                        rs.getInt("collisionResolution")
                    );
                    map.put("exclusiveMode", rs.getBoolean("exclusiveMode"));
                    map.put(
                        "customSlipperiness",
                        rs.getString("customSlipperiness")
                    );
                    map.put("perBlockSetting", rs.getString("perBlockSetting"));

                    return map;
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao buscar boat utils brutos para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    public synchronized void setPerBlockSetting(
        String trackNameWS,
        String value
    ) {
        String sql =
            "INSERT INTO fr_boatutils (trackNameWS, perBlockSetting) VALUES (?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET perBlockSetting = excluded.perBlockSetting";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setString(2, value);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao definir configuração por bloco para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
    }

    public synchronized boolean trackHaveBoatUtils(String trackNameWS) {
        // ✅ Remove espaços do nome da pista para normalização
        String trackNameNormalized = trackNameWS.replaceAll("\\s+", "");
        final String sql =
            "SELECT * FROM fr_boatutils WHERE LOWER(trackNameWS) = LOWER(?)";

        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameNormalized);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;

                    boolean modified = false;

                    // Comparações de valores
                    if (
                        rs.getFloat("stepHeight") !=
                        BoatUtilsVanillaValues.STEP_HEIGHT
                    ) modified = true;
                    if (
                        rs.getFloat("defaultSlipperiness") !=
                        BoatUtilsVanillaValues.DEFAULT_SLIPPERINESS
                    ) modified = true;
                    if (
                        rs.getBoolean("fallDamage") !=
                        BoatUtilsVanillaValues.FALL_DAMAGE
                    ) modified = true;
                    if (
                        rs.getBoolean("waterElevation") !=
                        BoatUtilsVanillaValues.WATER_ELEVATION
                    ) modified = true;
                    if (
                        rs.getBoolean("airControl") !=
                        BoatUtilsVanillaValues.AIR_CONTROL
                    ) modified = true;
                    if (
                        rs.getFloat("jumpForce") !=
                        BoatUtilsVanillaValues.JUMP_FORCE
                    ) modified = true;
                    if (
                        rs.getDouble("gravity") !=
                        BoatUtilsVanillaValues.GRAVITY
                    ) modified = true;
                    if (
                        rs.getFloat("yawAcceleration") !=
                        BoatUtilsVanillaValues.YAW_ACCEL
                    ) modified = true;
                    if (
                        rs.getFloat("forwardAcceleration") !=
                        BoatUtilsVanillaValues.FORWARD_ACCEL
                    ) modified = true;
                    if (
                        rs.getFloat("backwardAcceleration") !=
                        BoatUtilsVanillaValues.BACKWARD_ACCEL
                    ) modified = true;
                    if (
                        rs.getFloat("turningForwardAcceleration") !=
                        BoatUtilsVanillaValues.TURN_FORWARD_ACCEL
                    ) modified = true;
                    if (
                        rs.getBoolean("allowAccelerationStacking") !=
                        BoatUtilsVanillaValues.ALLOW_ACCEL_STACKING
                    ) modified = true;
                    if (
                        rs.getBoolean("underwaterControl") !=
                        BoatUtilsVanillaValues.UNDERWATER_CONTROL
                    ) modified = true;
                    if (
                        rs.getBoolean("surfaceWaterControl") !=
                        BoatUtilsVanillaValues.SURFACE_WATER_CONTROL
                    ) modified = true;
                    if (
                        rs.getInt("coyoteTime") !=
                        BoatUtilsVanillaValues.COYOTE_TIME
                    ) modified = true;
                    if (
                        rs.getBoolean("waterJumping") !=
                        BoatUtilsVanillaValues.WATER_JUMPING
                    ) modified = true;
                    if (
                        rs.getFloat("swimForce") !=
                        BoatUtilsVanillaValues.SWIM_FORCE
                    ) modified = true;
                    if (
                        rs.getInt("collisionMode") !=
                        BoatUtilsVanillaValues.COLLISION_MODE
                    ) modified = true;
                    if (
                        rs.getBoolean("airStepping") !=
                        BoatUtilsVanillaValues.AIR_STEPPING
                    ) modified = true;
                    if (
                        rs.getBoolean("tenStepInterpolation") !=
                        BoatUtilsVanillaValues.TEN_STEP_INTERPOLATION
                    ) modified = true;
                    if (
                        rs.getInt("collisionResolution") !=
                        BoatUtilsVanillaValues.COLLISION_RESOLUTION
                    ) modified = true;

                    if (
                        !Objects.equals(
                            rs.getString("customSlipperiness"),
                            BoatUtilsVanillaValues.CUSTOM_SLIPPERINESS
                        )
                    ) modified = true;
                    if (
                        !Objects.equals(
                            rs.getString("perBlockSetting"),
                            BoatUtilsVanillaValues.PER_BLOCK_SETTING
                        )
                    ) modified = true;

                    return modified;
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "Erro ao verificar se pista " +
                        trackNameWS +
                        " tem boat utils: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return false;
    }

    public synchronized List<UUID> getActivePlayersInDuel(int duelId) {
        List<UUID> activePlayers = new ArrayList<>();
        String sql =
            "SELECT players FROM fr_timetrial_duel_players WHERE duel_id = ?";

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
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "Erro ao buscar jogadores ativos no duelo " +
                        duelId +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return activePlayers;
    }

    public synchronized int getActiveDuelId(UUID uuid) {
        String uuidTarget = uuid.toString();

        // A query agora verifica se a UUID está no início, no meio (entre vírgulas) ou no fim da string
        // Isso evita que "uuid1" seja confundido com "uuid10" (se os nomes fossem simples)
        String sqlFinal =
            "SELECT d.id FROM fr_timetrial_duel_players p " +
            "JOIN fr_timetrial_duels d ON p.duel_id = d.id " +
            "WHERE (p.players = ? " + // Caso seja a única UUID
            "OR p.players LIKE ? " + // Caso esteja no início: 'uuid,%'
            "OR p.players LIKE ? " + // Caso esteja no fim: '%,uuid'
            "OR p.players LIKE ?) " + // Caso esteja no meio: '%,uuid,%'
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
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "Falha ao buscar Duelo Ativo para " +
                        uuidTarget +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return -1;
    }

    public void setDuelStateWithWinner(
        int duelId,
        String state,
        UUID winnerUUID
    ) {
        String sql =
            "UPDATE fr_timetrial_duels SET state = ?, winner = ?, finished_in = CURRENT_TIMESTAMP WHERE id = ?";
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
                    plugin
                        .getDebugManager()
                        .logDuelSystem(
                            "Erro ao definir estado do duelo " +
                                duelId +
                                " com vencedor " +
                                winnerStr +
                                ": " +
                                e.getMessage()
                        );
                    handleSqlError(e);
                }
            }
        });
    }

    public synchronized void addCustomSlipperiness(
        String trackNameWS,
        String blockId,
        float value
    ) {
        Map<String, Float> map = getCustomSlipperiness(trackNameWS);
        map.put(blockId.toLowerCase(), value);
        saveCustomSlipperiness(trackNameWS, map);
    }

    public synchronized void resetCustomSlipperiness(String trackNameWS) {
        saveCustomSlipperiness(trackNameWS, new HashMap<>());
    }

    public synchronized Map<String, Float> getCustomSlipperiness(
        String trackNameWS
    ) {
        String sql =
            "SELECT customSlipperiness FROM fr_boatutils WHERE trackNameWS = ?";
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
                                    result.put(
                                        parts[0],
                                        Float.parseFloat(parts[1])
                                    );
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin
                .getDebugManager()
                .logBoatUtils(
                    "Erro ao buscar fricção customizada para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            if (e instanceof SQLException) handleSqlError((SQLException) e);
        }
        return result;
    }

    /* =======================================================
     *                MÉTODOS DE TRACK FINISH LOCATIONS
     * ======================================================= */

    public void setTrackFinishAll(String trackNameWS, Location loc) {
        String sql =
            "UPDATE fr_tracks SET finishAll_x = ?, finishAll_y = ?, finishAll_z = ?, finishAll_yaw = ?, finishAll_pitch = ? WHERE trackNameWS = ?";
        try (
            Connection conn = getOrConnect();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setDouble(1, loc.getX());
            ps.setDouble(2, loc.getY());
            ps.setDouble(3, loc.getZ());
            ps.setDouble(4, loc.getYaw());
            ps.setDouble(5, loc.getPitch());
            ps.setString(6, trackNameWS);
            ps.executeUpdate();
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public Location getTrackFinishAll(String trackNameWS) {
        String sql =
            "SELECT finishAll_x, finishAll_y, finishAll_z, finishAll_yaw, finishAll_pitch, worldName FROM fr_tracks WHERE trackNameWS = ?";
        try (
            Connection conn = getOrConnect();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, trackNameWS);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double x = rs.getDouble("finishAll_x");
                    // Se x for 0 e todos os outros forem 0 ou null, pode ser que não esteja setado, mas como são coordinates, 0 é valido.
                    // Porém como inicializamos com NULL, podemos checar com wasNull()
                    if (rs.wasNull()) return null;

                    double y = rs.getDouble("finishAll_y");
                    double z = rs.getDouble("finishAll_z");
                    double yaw = rs.getDouble("finishAll_yaw");
                    double pitch = rs.getDouble("finishAll_pitch");
                    String worldName = rs.getString("worldName");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) return null;
                    return new Location(
                        world,
                        x,
                        y,
                        z,
                        (float) yaw,
                        (float) pitch
                    );
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    public void setTrackFinishPos(
        String trackNameWS,
        int position,
        Location loc
    ) {
        String sql =
            "INSERT OR REPLACE INTO fr_track_finish_positions (trackNameWS, position, x, y, z, yaw, pitch, worldName) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (
            Connection conn = getOrConnect();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, trackNameWS);
            ps.setInt(2, position);
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setDouble(6, loc.getYaw());
            ps.setDouble(7, loc.getPitch());
            ps.setString(8, loc.getWorld().getName());
            ps.executeUpdate();
        } catch (SQLException e) {
            handleSqlError(e);
        }
    }

    public Location getTrackFinishPos(String trackNameWS, int position) {
        String sql =
            "SELECT x, y, z, yaw, pitch, worldName FROM fr_track_finish_positions WHERE trackNameWS = ? AND position = ?";
        try (
            Connection conn = getOrConnect();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, trackNameWS);
            ps.setInt(2, position);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    double yaw = rs.getDouble("yaw");
                    double pitch = rs.getDouble("pitch");
                    String worldName = rs.getString("worldName");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) return null;
                    return new Location(
                        world,
                        x,
                        y,
                        z,
                        (float) yaw,
                        (float) pitch
                    );
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return null;
    }

    /* =======================================================
     *                MÉTODOS DE SUPORTE
     * ======================================================= */

    private synchronized void saveCustomSlipperiness(
        String trackNameWS,
        Map<String, Float> map
    ) {
        StringBuilder builder = new StringBuilder();

        for (var e : map.entrySet()) {
            builder
                .append(e.getKey())
                .append(";")
                .append(e.getValue())
                .append(",");
        }

        if (builder.length() > 0) builder.setLength(builder.length() - 1);

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
            plugin
                .getDebugManager()
                .logBoatUtils(
                    "Erro ao salvar fricção customizada para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
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
            plugin
                .getDebugManager()
                .logBoatUtils(
                    "Erro ao definir controle aéreo para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
    }

    public synchronized Boolean getAirControl(String trackNameWS) {
        String sql =
            "SELECT airControl FROM fr_boatutils WHERE trackNameWS = ?";

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
            plugin
                .getDebugManager()
                .logBoatUtils(
                    "Erro ao buscar controle aéreo para " +
                        trackNameWS +
                        ": " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    /* =======================================================
           MÉTODOS DE GERENCIAMENTO DE EVENTOS SELECIONADOS
    ======================================================= */

    /**
     * Obtém o evento selecionado por um jogador
     */
    public synchronized Optional<
        dev.EfraGroup.formulaRacing.Event.Events
    > getPlayerSelectedEvent(UUID playerUUID) {
        String sql = "SELECT selected_event_id FROM fr_players WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int eventId = rs.getInt("selected_event_id");
                        if (rs.wasNull() || eventId == 0) {
                            return Optional.empty();
                        }

                        // ✅ CRÍTICO: Buscar evento do RaceEventManager (memória) ao invés de recarregar do banco
                        // Isso garante que sempre retornamos a MESMA instância do evento com os mesmos Round e Heat objects
                        if (plugin.getRaceEventManager() != null) {
                            Optional<
                                dev.EfraGroup.formulaRacing.Event.Events
                            > eventOpt = plugin
                                .getRaceEventManager()
                                .getEventById(eventId);
                            if (eventOpt.isPresent()) {
                                plugin
                                    .getDebugManager()
                                    .logDatabaseOperation(
                                        "[DB DEBUG] Evento encontrado na memória: " +
                                            eventOpt.get().getDisplayName() +
                                            " (EventObj: " +
                                            System.identityHashCode(
                                                eventOpt.get()
                                            ) +
                                            ")"
                                    );
                                return eventOpt;
                            }
                        }

                        // Fallback: Se não encontrou na memória, carrega do banco (não deveria acontecer normalmente)
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[DB DEBUG] Evento ID " +
                                    eventId +
                                    " não encontrado na memória, carregando do banco..."
                            );
                        dev.EfraGroup.formulaRacing.Database.EventsDatabaseManager eventsDb =
                            new dev.EfraGroup.formulaRacing.Database.EventsDatabaseManager(
                                this,
                                plugin
                            );
                        return Optional.ofNullable(eventsDb.loadEvent(eventId));
                    }
                }
            }
        } catch (SQLException e) {
            // Se a coluna não existe, adiciona ela
            if (
                e.getMessage().contains("no such column") ||
                e.getMessage().contains("selected_event_id")
            ) {
                try {
                    Connection conn = getOrConnect();
                    try (
                        PreparedStatement ps = conn.prepareStatement(
                            "ALTER TABLE fr_players ADD COLUMN selected_event_id INTEGER DEFAULT NULL"
                        )
                    ) {
                        ps.executeUpdate();
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[FormulaRacing] Coluna selected_event_id adicionada à tabela fr_players"
                            );
                    }
                } catch (SQLException ex) {
                    handleSqlError(ex);
                }
            } else {
                handleSqlError(e);
            }
        }

        // ✅ NOVO: Fallback para Quick Race ativa
        // Se o jogador não tem um evento selecionado no banco, mas está participando de uma Quick Race
        // ou é um admin e existe uma Quick Race ativa, retornamos a Quick Race como contexto.
        if (
            plugin.getQuickRaceManager() != null &&
            plugin.getQuickRaceManager().isQuickRaceActive()
        ) {
            Optional<dev.EfraGroup.formulaRacing.Event.Events> quickEvent =
                plugin.getQuickRaceManager().getCurrentQuickRace();
            if (quickEvent.isPresent()) {
                dev.EfraGroup.formulaRacing.Heat.Heats heat = plugin
                    .getQuickRaceManager()
                    .getCurrentHeat()
                    .orElse(null);
                if (heat != null) {
                    // Jogador é piloto na corrida
                    if (heat.getDriver(playerUUID) != null) {
                        return quickEvent;
                    }

                    // Jogador é admin (pode gerenciar a corrida ativa)
                    org.bukkit.entity.Player player =
                        org.bukkit.Bukkit.getPlayer(playerUUID);
                    if (
                        player != null &&
                        player.hasPermission("formularacing.event.admin")
                    ) {
                        return quickEvent;
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Define o evento selecionado por um jogador
     */
    public synchronized void setPlayerSelectedEvent(
        UUID playerUUID,
        dev.EfraGroup.formulaRacing.Event.Events event
    ) {
        String sql = """
            INSERT INTO fr_players (uuid, displayName, selected_event_id)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET selected_event_id = excluded.selected_event_id
            """;
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(
                    playerUUID
                );
                ps.setString(
                    2,
                    online != null ? online.getName() : playerUUID.toString()
                );
                ps.setInt(3, event.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            // Se a coluna não existe, adiciona ela e tenta novamente
            if (
                e.getMessage().contains("no such column") ||
                e.getMessage().contains("selected_event_id")
            ) {
                try {
                    Connection conn = getOrConnect();
                    try (
                        PreparedStatement ps = conn.prepareStatement(
                            "ALTER TABLE fr_players ADD COLUMN selected_event_id INTEGER DEFAULT NULL"
                        )
                    ) {
                        ps.executeUpdate();
                        plugin
                            .getDebugManager()
                            .logDatabaseOperation(
                                "[FormulaRacing] Coluna selected_event_id adicionada à tabela fr_players"
                            );
                    }
                    // Tenta novamente
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, playerUUID.toString());
                        org.bukkit.entity.Player online =
                            org.bukkit.Bukkit.getPlayer(playerUUID);
                        ps.setString(
                            2,
                            online != null
                                ? online.getName()
                                : playerUUID.toString()
                        );
                        ps.setInt(3, event.getId());
                        ps.executeUpdate();
                    }
                } catch (SQLException ex) {
                    handleSqlError(ex);
                }
            } else {
                handleSqlError(e);
            }
        }
    }

    /**
     * Limpa o evento selecionado de um jogador
     */
    public synchronized void clearPlayerSelectedEvent(UUID playerUUID) {
        String sql =
            "UPDATE fr_players SET selected_event_id = NULL WHERE uuid = ?";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            // Se a coluna não existe, não faz nada
            if (
                !e.getMessage().contains("no such column") &&
                !e.getMessage().contains("selected_event_id")
            ) {
                handleSqlError(e);
            }
        }
    }

    /**
     * Limpa todos os eventos selecionados (útil para manutenção)
     */
    public synchronized void clearAllPlayerSelectedEvents() {
        String sql = "UPDATE fr_players SET selected_event_id = NULL";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int updated = ps.executeUpdate();
                plugin
                    .getDebugManager()
                    .logDatabaseOperation(
                        "[FormulaRacing] Limpou eventos selecionados de " +
                            updated +
                            " jogadores"
                    );
            }
        } catch (SQLException e) {
            // Se a coluna não existe, não faz nada
            if (
                !e.getMessage().contains("no such column") &&
                !e.getMessage().contains("selected_event_id")
            ) {
                handleSqlError(e);
            }
        }
    }

    public synchronized void deleteAllParties() throws SQLException {
        try (
            PreparedStatement stmt = getOrConnect().prepareStatement(
                "DELETE FROM fr_party"
            )
        ) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized boolean hasParty(UUID uuid) throws SQLException {
        String sql =
            "SELECT 1 FROM fr_party WHERE (',' || members || ',') LIKE '%,' || ? || ',%'";
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
        String sql =
            "SELECT owner FROM fr_party WHERE (',' || members || ',') LIKE '%,' || ? || ',%'";
        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, member.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next()
                    ? UUID.fromString(rs.getString("owner"))
                    : null;
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

    public synchronized void addMember(UUID owner, UUID member)
        throws SQLException {
        String sql =
            "UPDATE fr_party SET members = members || ',' || ? WHERE owner = ?";
        try (PreparedStatement stmt = getOrConnect().prepareStatement(sql)) {
            stmt.setString(1, member.toString());
            stmt.setString(2, owner.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            handleSqlError(e);
            throw e;
        }
    }

    public synchronized void removeMember(UUID owner, UUID member)
        throws SQLException {
        String sql =
            "UPDATE fr_party SET members = TRIM(REPLACE(',' || members || ',', ',' || ? || ',', ','), ',') WHERE owner = ?";
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

    public synchronized Object[] getPlayerBestTimeOnDuel(
        UUID uuid,
        int duelId
    ) {
        String sql =
            "SELECT time, checkpointsReached, finished FROM fr_timetrial_dueltimes " +
            "WHERE playerName = ? AND duel_id = ? " +
            "ORDER BY finished DESC, checkpointsReached DESC, time ASC LIMIT 1";

        try {
            Connection conn = this.getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setInt(2, duelId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new Object[] {
                            rs.getDouble("time"),
                            rs.getInt("checkpointsReached"),
                            rs.getBoolean("finished"),
                        };
                    }
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "[FormulaRacing] Erro ao buscar Duel Time: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    /**
     * Salva o tempo de uma volta específica de um jogador no duelo.
     * Usado para registrar PB e histórico de voltas.
     */
    public synchronized void saveDuelLapTime(
        UUID playerUUID,
        String playerName,
        int duelId,
        int lapNumber,
        double lapTime,
        String trackName
    ) {
        String sql =
            "INSERT INTO fr_timetrial_dueltimes (playerName, duel_id, time, checkpointsReached, finished) VALUES (?, ?, ?, ?, ?)";

        try {
            Connection conn = this.getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setInt(2, duelId);
                pstmt.setDouble(3, lapTime);
                pstmt.setInt(4, 0); // Checkpoints não são usados em duelos de volta
                pstmt.setBoolean(5, false); // Marca como não finalizado (é apenas uma volta)

                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "[FormulaRacing] Erro ao salvar tempo de volta do duelo: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
    }

    /**
     * Salva o tempo total final de um jogador no duelo.
     * Este é o tempo usado para o PB (Personal Best).
     */
    public synchronized void saveDuelFinalTime(
        UUID playerUUID,
        String playerName,
        int duelId,
        double totalTime,
        String trackName
    ) {
        String sql =
            "INSERT INTO fr_timetrial_dueltimes (playerName, duel_id, time, checkpointsReached, finished) VALUES (?, ?, ?, ?, ?)";

        try {
            Connection conn = this.getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setInt(2, duelId);
                pstmt.setDouble(3, totalTime);
                pstmt.setInt(4, 0); // Checkpoints não são usados em duelos
                pstmt.setBoolean(5, true); // Marca como finalizado (tempo total)

                pstmt.executeUpdate();
                plugin
                    .getDebugManager()
                    .logDuelSystem(
                        "[FormulaRacing] Tempo final salvo para " +
                            playerName +
                            " no duelo #" +
                            duelId +
                            ": " +
                            String.format("%.3f", totalTime) +
                            "s"
                    );
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "[FormulaRacing] Erro ao salvar tempo final do duelo: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
    }

    /**
     * Busca o melhor tempo de volta do jogador no duelo atual (apenas voltas não finalizadas)
     * Retorna null se não houver nenhuma volta registrada ainda
     */
    public synchronized Double getPlayerBestLapTimeInDuel(
        UUID playerUUID,
        int duelId
    ) {
        String sql =
            "SELECT MIN(time) FROM fr_timetrial_dueltimes WHERE playerName = ? AND duel_id = ? AND finished = 0";

        try {
            Connection conn = this.getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setInt(2, duelId);

                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    double time = rs.getDouble(1);
                    // Se retornar 0, significa que não há registros (MIN retorna 0 em vez de NULL em alguns casos)
                    return (time > 0) ? time : null;
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "[FormulaRacing] Erro ao buscar melhor volta no duelo: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return null;
    }

    /**
     * Salva o tempo de um checkpoint específico durante um duelo.
     * Usado para cálculo de delta em tempo real.
     */
    public synchronized void saveDuelCheckpointTime(
        UUID playerUUID,
        int duelId,
        String trackName,
        int checkpointId,
        double time
    ) {
        // ✅ Remove apenas espaços, sem toLowerCase
        String trackNameWS = trackName.replaceAll("\\s+", "");
        String sql =
            "INSERT OR REPLACE INTO fr_timetrial_duels_checkpoint_times " +
            "(timetrial_id, duel_id, player_uuid, trackNameWS, checkpointId, time) " +
            "VALUES (0, ?, ?, ?, ?, ?)";

        try {
            Connection conn = this.getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, duelId);
                pstmt.setString(2, playerUUID.toString());
                pstmt.setString(3, trackNameWS);
                pstmt.setInt(4, checkpointId);
                pstmt.setDouble(5, time);

                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "[FormulaRacing] Erro ao salvar checkpoint do duelo: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
    }

    /**
     * Recupera os tempos de todos os checkpoints do jogador no duelo atual.
     * Usado para calcular delta comparando com checkpoints da melhor volta.
     */
    public synchronized Map<Integer, Double> getDuelCheckpointTimes(
        UUID playerUUID,
        int duelId
    ) {
        Map<Integer, Double> checkpointTimes = new HashMap<>();
        String sql =
            "SELECT checkpointId, time FROM fr_timetrial_duels_checkpoint_times " +
            "WHERE player_uuid = ? AND duel_id = ?";

        try {
            Connection conn = this.getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setInt(2, duelId);

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    checkpointTimes.put(
                        rs.getInt("checkpointId"),
                        rs.getDouble("time")
                    );
                }
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "[FormulaRacing] Erro ao buscar checkpoints do duelo: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
        return checkpointTimes;
    }

    /**
     * Limpa todos os checkpoint times de um jogador em um duelo específico.
     * Usado quando o jogador inicia uma nova volta.
     */
    public synchronized void clearDuelCheckpointTimes(
        UUID playerUUID,
        int duelId
    ) {
        String sql =
            "DELETE FROM fr_timetrial_duels_checkpoint_times WHERE player_uuid = ? AND duel_id = ?";

        try {
            Connection conn = this.getOrConnect();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setInt(2, duelId);

                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin
                .getDebugManager()
                .logDuelSystem(
                    "[FormulaRacing] Erro ao limpar checkpoints do duelo: " +
                        e.getMessage()
                );
            handleSqlError(e);
        }
    }

    private void handleSqlError(SQLException e) {
        // 1. Log detalhado para o console
        String message = e.getMessage();
        plugin
            .getLogger()
            .log(Level.SEVERE, "[FormulaRacing] Erro SQL detectado!", e);

        // 2. Verificação robusta de conexão morta
        // SQLState que começa com "08" geralmente indica erro de conexão/comunicação
        String sqlState = e.getSQLState();
        boolean connectionIssue = false;

        if (sqlState != null && sqlState.startsWith("08")) {
            connectionIssue = true;
        } else if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (
                lower.contains("closed") ||
                lower.contains("broken pipe") ||
                lower.contains("communication link failure")
            ) {
                connectionIssue = true;
            }
        }

        // 3. Reset da conexão se necessário
        if (connectionIssue) {
            plugin
                .getLogger()
                .warning(
                    "[FormulaRacing] Conexão com o banco perdida. Tentando resetar..."
                );
            try {
                if (this.connection != null && !this.connection.isClosed()) {
                    this.connection.close();
                }
            } catch (SQLException ignored) {
                // Ignora erro ao fechar uma conexão já morta
            }
            this.connection = null; // Força o getOrConnect a criar uma nova na próxima chamada
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

        public PlayerTime(
            UUID playerUUID,
            String playerName,
            double time,
            int checkpointsReached,
            int totalCheckpoints,
            boolean finished
        ) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.time = time;
            this.checkpointsReached = checkpointsReached;
            this.totalCheckpoints = totalCheckpoints;
            this.finished = finished;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
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

        public int getTotalCheckpoints() {
            return totalCheckpoints;
        }

        public boolean isFinished() {
            return finished || checkpointsReached >= totalCheckpoints;
        } // fallback automático
    }

    public static class TrackData {

        // --- Campos principais da pista ---
        private final String trackName; // Nome ORIGINAL da pista (com formatação)
        private final Location spawnLocation; // Local de spawn configurado
        private final String worldName; // Nome do mundo onde está a pista
        private final String ownerName; // Nome do criador/dono da pista
        private final String iconName; // Nome do ícone (Material) usado no menu
        private final int totalCheckpoints; // Número total de checkpoints da pista

        // --- Construtor ---
        public TrackData(
            String trackName,
            Location spawnLocation,
            String worldName,
            String ownerName,
            String iconName,
            int totalCheckpoints
        ) {
            this.trackName = trackName;
            this.spawnLocation = spawnLocation;
            this.worldName = worldName;
            this.ownerName = ownerName;
            this.iconName = iconName;
            this.totalCheckpoints = totalCheckpoints;
        }

        // --- Getters ---
        public String getTrackName() {
            return trackName;
        }

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
        private final String trackName; // Display Name (com espaços)
        private final String trackNameWS; // Internal Name (sem espaços)
        private final String type; // START, END, etc.
        private final String world;
        private final String shape; // AABB or POLY
        private final String points; // For POLY: "x1,z1;x2,z2;x3,z3..."
        private final double minX, minY, minZ;
        private final double maxX, maxY, maxZ;

        public RegionData(
            int id,
            String trackName,
            String trackNameWS,
            String type,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            String world
        ) {
            this(
                id,
                trackName,
                trackNameWS,
                type,
                "AABB",
                null,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                world
            );
        }

        public RegionData(
            int id,
            String trackName,
            String trackNameWS,
            String type,
            String shape,
            String points,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            String world
        ) {
            this.id = id;
            this.trackName = trackName;
            this.trackNameWS = trackNameWS;
            this.type = type.toUpperCase();
            this.shape = shape != null ? shape.toUpperCase() : "AABB";
            this.points = points;
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

        public String getTrackNameWS() {
            return trackNameWS;
        }

        public String getType() {
            return type;
        }

        public String getWorld() {
            return world;
        }

        public String getShape() {
            return shape;
        }

        public boolean isPoly() {
            return "POLY".equals(shape);
        }

        public String getPoints() {
            return points;
        }

        public double[][] getPolyPoints() {
            if (points == null || points.isEmpty()) {
                return new double[0][];
            }
            String[] pairs = points.split(";");
            double[][] result = new double[pairs.length][];
            for (int i = 0; i < pairs.length; i++) {
                String[] coords = pairs[i].split(",");
                if (coords.length == 2) {
                    result[i] = new double[] {
                        Double.parseDouble(coords[0]),
                        Double.parseDouble(coords[1]),
                    };
                }
            }
            return result;
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

        public TrackRecord(
            String playerName,
            double time,
            int checkpointsReached,
            boolean finished,
            String timeCreated
        ) {
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

    /**
     * Classe para armazenar dados de pit stop region (entrada, saída e AREA de minigame)
     */
    public class PitStopData {

        private final String trackName;
        private final Location entryMin, entryMax;
        private final Location exitMin, exitMax;
        private final Location areaMin, areaMax;
        // NOVOS CAMPOS
        private final Location startMin, startMax;

        public PitStopData(
            String trackName,
            Location entryMin,
            Location entryMax,
            Location exitMin,
            Location exitMax,
            Location areaMin,
            Location areaMax,
            Location startMin,
            Location startMax
        ) {
            this.trackName = trackName;
            this.entryMin = entryMin;
            this.entryMax = entryMax;
            this.exitMin = exitMin;
            this.exitMax = exitMax;
            this.areaMin = areaMin;
            this.areaMax = areaMax;
            this.startMin = startMin;
            this.startMax = startMax;
        }

        // Não esqueça de adicionar os Getters para startMin e startMax aqui embaixo
        public Location getStartMin() {
            return startMin;
        }

        public Location getStartMax() {
            return startMax;
        }

        public String getTrackNameWS() {
            return trackName;
        }

        public Location getEntryMin() {
            return entryMin;
        }

        public Location getEntryMax() {
            return entryMax;
        }

        public Location getExitMin() {
            return exitMin;
        }

        public Location getExitMax() {
            return exitMax;
        }

        public Location getAreaMin() {
            return areaMin;
        }

        public Location getAreaMax() {
            return areaMax;
        }
    }

    /**
     * Salva pit stop entry region para uma pista
     */
    public synchronized boolean savePitStopEntry(
        String trackName,
        Location min,
        Location max
    ) {
        if (trackName == null) return false;
        String trackNameWS = trackName.replaceAll("\\s+", "");
        // Inclui colunas legadas minX..maxZ (NOT NULL) para compatibilidade
        String sql =
            "INSERT INTO fr_pit_stops (trackNameWS, " +
            "minX, minY, minZ, maxX, maxY, maxZ, " +
            "entryMinX, entryMinY, entryMinZ, entryMaxX, entryMaxY, entryMaxZ, world) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET " +
            "entryMinX=excluded.entryMinX, entryMinY=excluded.entryMinY, entryMinZ=excluded.entryMinZ, " +
            "entryMaxX=excluded.entryMaxX, entryMaxY=excluded.entryMaxY, entryMaxZ=excluded.entryMaxZ, " +
            "minX=excluded.minX, minY=excluded.minY, minZ=excluded.minZ, " +
            "maxX=excluded.maxX, maxY=excluded.maxY, maxZ=excluded.maxZ";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                // minX..maxZ legadas = valores da entry (garante NOT NULL)
                ps.setDouble(2, min.getX());
                ps.setDouble(3, min.getY());
                ps.setDouble(4, min.getZ());
                ps.setDouble(5, max.getX());
                ps.setDouble(6, max.getY());
                ps.setDouble(7, max.getZ());

                // entryMin/Max
                ps.setDouble(8, min.getX());
                ps.setDouble(9, min.getY());
                ps.setDouble(10, min.getZ());
                ps.setDouble(11, max.getX());
                ps.setDouble(12, max.getY());
                ps.setDouble(13, max.getZ());
                ps.setString(14, min.getWorld().getName());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    /**
     * Salva pit stop exit region para uma pista
     */
    public synchronized boolean savePitStopExit(
        String trackName,
        Location min,
        Location max
    ) {
        if (trackName == null) return false;
        String trackNameWS = trackName.replaceAll("\\s+", "");
        String sql =
            "INSERT INTO fr_pit_stops (trackNameWS, " +
            "exitMinX, exitMinY, exitMinZ, exitMaxX, exitMaxY, exitMaxZ, world) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET " +
            "exitMinX=excluded.exitMinX, exitMinY=excluded.exitMinY, exitMinZ=excluded.exitMinZ, " +
            "exitMaxX=excluded.exitMaxX, exitMaxY=excluded.exitMaxY, exitMaxZ=excluded.exitMaxZ";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, min.getX());
                ps.setDouble(3, min.getY());
                ps.setDouble(4, min.getZ());
                ps.setDouble(5, max.getX());
                ps.setDouble(6, max.getY());
                ps.setDouble(7, max.getZ());
                ps.setString(8, min.getWorld().getName());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    /**
     * Salva pit stop AREA de minigame
     */
    public synchronized boolean savePitStopArea(
        String trackName,
        Location min,
        Location max
    ) {
        if (trackName == null) return false;
        String trackNameWS = trackName.replaceAll("\\s+", "");
        String sql =
            "INSERT INTO fr_pit_stops (trackNameWS, " +
            "areaMinX, areaMinY, areaMinZ, areaMaxX, areaMaxY, areaMaxZ, world) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(trackNameWS) DO UPDATE SET " +
            "areaMinX=excluded.areaMinX, areaMinY=excluded.areaMinY, areaMinZ=excluded.areaMinZ, " +
            "areaMaxX=excluded.areaMaxX, areaMaxY=excluded.areaMaxY, areaMaxZ=excluded.areaMaxZ";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                ps.setDouble(2, min.getX());
                ps.setDouble(3, min.getY());
                ps.setDouble(4, min.getZ());
                ps.setDouble(5, max.getX());
                ps.setDouble(6, max.getY());
                ps.setDouble(7, max.getZ());
                ps.setString(8, min.getWorld().getName());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }


    /**
     * Remove pit stop region de uma pista
     */
    public synchronized boolean removePitStop(String trackName) {
        if (trackName == null) return false;
        String trackNameWS = trackName.replaceAll("\\s+", "");
        String sql =
            "DELETE FROM fr_pit_stops WHERE LOWER(trackNameWS) = LOWER(?)";
        try {
            Connection conn = getOrConnect();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trackNameWS);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            handleSqlError(e);
            return false;
        }
    }

    /**
     * Retorna todos os pit stops de todas as pistas, incluindo a região de Start
     */
    public synchronized List<PitStopData> getAllPitStops() {
        List<PitStopData> pitStops = new ArrayList<>();
        String sql =
            "SELECT trackNameWS, world, " +
            "entryMinX, entryMinY, entryMinZ, entryMaxX, entryMaxY, entryMaxZ, " +
            "exitMinX, exitMinY, exitMinZ, exitMaxX, exitMaxY, exitMaxZ, " +
            "areaMinX, areaMinY, areaMinZ, areaMaxX, areaMaxY, areaMaxZ, " +
            "startMinX, startMinY, startMinZ, startMaxX, startMaxY, startMaxZ " +
            "FROM fr_pit_stops";
        try {
            Connection conn = getOrConnect();
            try (
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
            ) {
                while (rs.next()) {
                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    // Buscando cada Location individualmente (Min e Max de cada região)
                    Location entryMin = createLocation(world, rs, "entryMin");
                    Location entryMax = createLocation(world, rs, "entryMax");

                    Location exitMin = createLocation(world, rs, "exitMin");
                    Location exitMax = createLocation(world, rs, "exitMax");

                    Location areaMin = createLocation(world, rs, "areaMin");
                    Location areaMax = createLocation(world, rs, "areaMax");

                    Location startMin = createLocation(world, rs, "startMin");
                    Location startMax = createLocation(world, rs, "startMax");

                    // Agora passamos os 9 argumentos na ordem correta
                    pitStops.add(
                        new PitStopData(
                            rs.getString("trackNameWS"),
                            entryMin,
                            entryMax,
                            exitMin,
                            exitMax,
                            areaMin,
                            areaMax,
                            startMin,
                            startMax
                        )
                    );
                }
            }
        } catch (SQLException e) {
            handleSqlError(e);
        }
        return pitStops;
    }

    /**
     * Método auxiliar que agora recebe exatamente 3 argumentos.
     */
    private Location createLocation(World world, ResultSet rs, String prefix)
        throws SQLException {
        if (rs.getObject(prefix + "X") == null) return null;
        return new Location(
            world,
            rs.getDouble(prefix + "X"),
            rs.getDouble(prefix + "Y"),
            rs.getDouble(prefix + "Z")
        );
    }

    // Helper para criar TrackData a partir do ResultSet (evita duplicação de código)
    private TrackData createTrackDataFromResultSet(
        ResultSet rs,
        String trackNameWSQuery
    ) throws SQLException {
        String trackNameOriginal = rs.getString("trackName");
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

        String effectiveTrackWS = trackNameWSQuery;
        try {
            String ws = rs.getString("trackNameWS");
            if (ws != null) effectiveTrackWS = ws;
        } catch (SQLException ignored) {
            // Coluna não existe no result set (fallback query original)
        }

        return new TrackData(
            trackNameOriginal,
            spawnLocation,
            worldName,
            rs.getString("creatorName"),
            rs.getString("icon_name"),
            getCheckpointCount(effectiveTrackWS) // Usa o WS correto para contar checkpoints
        );
    }

    // --- ASYNC WRAPPERS ---

    public void saveFullTimeAsync(
        UUID playerUUID,
        String playerName,
        String trackName,
        double time,
        int checkpointsReached
    ) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            saveFullTime(
                playerUUID,
                playerName,
                trackName,
                time,
                checkpointsReached
            );
        });
    }

    public CompletableFuture<Object[]> getPlayerBestTimeAsync(
        String playerName,
        String trackName
    ) {
        CompletableFuture<Object[]> future = new CompletableFuture<>();
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            future.complete(getPlayerBestTime(playerName, trackName));
        });
        return future;
    }

    public CompletableFuture<Integer> getPlayerRankAsync(
        UUID playerUUID,
        String trackNameWS
    ) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            future.complete(getPlayerRank(playerUUID, trackNameWS));
        });
        return future;
    }
}
