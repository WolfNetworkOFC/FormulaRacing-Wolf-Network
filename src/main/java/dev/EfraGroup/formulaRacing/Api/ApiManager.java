package dev.EfraGroup.formulaRacing.Api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import spark.Spark;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static spark.Spark.*;

public class ApiManager {
    private static ApiManager instance;
    private final FormulaRacing plugin;
    private final Gson gson;
    private final Map<String, Long> rateLimitCache;
    private FileConfiguration config;
    private int port;
    private boolean corsEnabled;
    private boolean rateLimitEnabled;
    private int requestsPerMinute;
    private boolean logRequests;
    private boolean logErrors;
    private boolean authEnabled;
    private String authToken;
    private boolean authProtectLogs;
    private final Map<String, String> authTokensByValue = new ConcurrentHashMap<>();
    private final List<JsonObject> systemLogs;
    private static final int MAX_LOGS = 100;

    public ApiManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().create();
        this.rateLimitCache = new ConcurrentHashMap<>();
        this.systemLogs = new ArrayList<>();
        setupFiles();
        loadConfig();
    }

    public static ApiManager getInstance() {
        return instance;
    }

    public void shutdown() {
        // Nothing to clean up currently
    }

    public void init() {
        instance = this;

        port(port);
        threadPool(8, 2, 30000);

        // Serve static files from dashboard folder
        String dashboardPath = plugin.getDataFolder().getAbsolutePath() + "/dashboard";
        File dashboardDir = new File(dashboardPath);
        if (dashboardDir.exists()) {
            staticFiles.externalLocation(dashboardPath);
            plugin.getLogger().info("Serving dashboard from: " + dashboardPath);
        } else {
            plugin.getLogger().warning("Dashboard folder not found: " + dashboardPath);
        }

        before((request, response) -> {
            response.type("application/json");

            if (corsEnabled) {
                response.header("Access-Control-Allow-Origin", "*");
                response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            }

            if (request.requestMethod().equals("OPTIONS")) {
                halt(200);
            }
        });

        // Redirect root to dashboard
        get("/", (request, response) -> {
            response.redirect("/dashboard/index.html");
            return "";
        });

        setupAuthentication();
        setupRateLimiting();
        setupEndpoints();

        exception(Exception.class, (exception, request, response) -> {
            if (logErrors) {
                plugin.getLogger().log(Level.SEVERE, "API Error: " + exception.getMessage(), exception);
                logError("API Error: " + exception.getMessage());
            }
            response.status(500);
            response.body(createErrorResponse("Internal server error: " + exception.getMessage()));
        });

        notFound((request, response) -> {
            response.status(404);
            return createErrorResponse("Endpoint not found: " + request.pathInfo());
        });

        plugin.getLogger().info("FormulaRacing REST API started on port " + port);
        logInfo("API iniciada na porta " + port);
    }

    private void setupAuthentication() {
        // Rotas /api/v1/admin/* sempre exigem token quando auth.enabled=true.
        // Rotas readonly continuam públicas (dashboard não quebra); /readonly/logs
        // só exige token se auth.protect_logs=true.
        before("/api/*", (request, response) -> {
            if (!authEnabled) return;

            String path = request.pathInfo();
            boolean protectedPath = path.startsWith("/api/v1/admin/")
                    || (authProtectLogs && path.equals("/api/v1/readonly/logs"));
            if (!protectedPath) return;

            String provided = extractBearerToken(request);
            if (!isAuthorized(provided)) {
                halt(401, createErrorResponse("Unauthorized: valid API token required"));
            }
        });
    }

    private String extractBearerToken(spark.Request request) {
        String header = request.headers("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) return token;
        }
        String query = request.queryParams("token");
        if (query != null && !query.isEmpty()) return query;
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(ab, bb);
    }

    /**
     * Aceita o token único legado (auth.token) ou qualquer chave nomeada (auth.tokens).
     */
    private boolean isAuthorized(String provided) {
        if (provided == null || provided.isEmpty()) return false;
        if (authToken != null && !authToken.isEmpty() && constantTimeEquals(provided, authToken)) {
            return true;
        }
        for (String token : authTokensByValue.keySet()) {
            if (constantTimeEquals(provided, token)) return true;
        }
        return false;
    }

    /**
     * Nome da chave usada (para logs). Retorna "legacy", o nome configurado ou "unknown".
     */
    private String resolveKeyName(String provided) {
        if (provided == null) return "none";
        if (authToken != null && !authToken.isEmpty() && constantTimeEquals(provided, authToken)) {
            return "legacy";
        }
        for (Map.Entry<String, String> entry : authTokensByValue.entrySet()) {
            if (constantTimeEquals(provided, entry.getKey())) return entry.getValue();
        }
        return "unknown";
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void setupRateLimiting() {
        if (!rateLimitEnabled) return;

        before("/api/*", (request, response) -> {
            String clientIp = request.ip();
            long now = System.currentTimeMillis();
            long oneMinuteAgo = now - 60000;

            rateLimitCache.entrySet().removeIf(entry -> entry.getValue() < oneMinuteAgo);

            long requestCount = rateLimitCache.values().stream()
                    .filter(time -> time > oneMinuteAgo)
                    .count();

            if (requestCount >= requestsPerMinute) {
                halt(429, createErrorResponse("Rate limit exceeded"));
            }

            rateLimitCache.put(clientIp, now);
        });
    }

    private JsonObject createDriverPositionObject(dev.EfraGroup.formulaRacing.Participant.Driver driver, dev.EfraGroup.formulaRacing.Heat.Heats heat) {
        JsonObject driverObj = new JsonObject();
        OfflinePlayer player = Bukkit.getOfflinePlayer(driver.getUuid());

        driverObj.addProperty("uuid", driver.getUuid().toString());
        driverObj.addProperty("name", player.getName());
        driverObj.addProperty("position", driver.getPosition());
        driverObj.addProperty("start_position", driver.getStartPosition());
        driverObj.addProperty("laps", driver.getLapCount());
        driverObj.addProperty("checkpoints", driver.getCheckpointsReached());
        driverObj.addProperty("finished", driver.isFinished());
        driverObj.addProperty("dnf", driver.isDnf());

        // Total time in readable format
        long totalTime = driver.getTotalTime();
        driverObj.addProperty("total_time_ms", totalTime);
        driverObj.addProperty("total_time", totalTime > 0 ? formatTime(totalTime) : "--:--:---");

        // Fastest lap
        if (driver.getFastestLap() != null) {
            driverObj.addProperty("fastest_lap_ms", driver.getFastestLap().getLapTime());
            driverObj.addProperty("fastest_lap", formatTime(driver.getFastestLap().getLapTime()));
        } else {
            driverObj.addProperty("fastest_lap", "--:--:---");
        }

        // Current position if online
        Player onlinePlayer = Bukkit.getPlayer(driver.getUuid());
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            Location loc = onlinePlayer.getLocation();
            driverObj.addProperty("online", true);
            driverObj.addProperty("world", loc.getWorld().getName());
            driverObj.addProperty("x", Math.round(loc.getX() * 100.0) / 100.0);
            driverObj.addProperty("y", Math.round(loc.getY() * 100.0) / 100.0);
            driverObj.addProperty("z", Math.round(loc.getZ() * 100.0) / 100.0);
            driverObj.addProperty("yaw", Math.round(loc.getYaw() * 100.0) / 100.0);
        } else {
            driverObj.addProperty("online", false);
        }

        return driverObj;
    }

    private int parseLimit(spark.Request request, int def, int max) {
        try {
            String param = request.queryParams("limit");
            if (param != null) {
                int limit = Integer.parseInt(param);
                if (limit > 0) return Math.min(limit, max);
            }
        } catch (NumberFormatException ignored) {}
        return def;
    }

    private UUID resolvePlayerUuid(String uuidOrUsername) {
        if (uuidOrUsername == null) {
            halt(400, createErrorResponse("UUID or username is required"));
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidOrUsername);
        } catch (IllegalArgumentException e) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuidOrUsername);
            if (offline == null) {
                halt(404, createErrorResponse("Player not found: " + uuidOrUsername));
                return null;
            }
            uuid = offline.getUniqueId();
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player == null || !player.hasPlayedBefore()) {
            halt(404, createErrorResponse("Player not found"));
        }
        return uuid;
    }

    private String offlineName(UUID uuid) {
        if (uuid == null) return "unknown";
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            String name = player == null ? null : player.getName();
            return name != null ? name : uuid.toString();
        } catch (Exception e) {
            return uuid.toString();
        }
    }

    private String offlineName(String uuidOrName) {
        try {
            return offlineName(UUID.fromString(uuidOrName));
        } catch (IllegalArgumentException e) {
            return uuidOrName;
        }
    }

    private dev.EfraGroup.formulaRacing.League.League requireLeague(String name) {
        if (name == null) {
            halt(400, createErrorResponse("League name is required"));
        }
        var leagueOpt = plugin.getLeagueManager().getLeagueByName(name);
        if (leagueOpt.isEmpty()) {
            halt(404, createErrorResponse("League not found: " + name));
        }
        return leagueOpt.get();
    }

    private JsonObject serializeLeague(dev.EfraGroup.formulaRacing.League.League league, boolean detailed) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", league.getId());
        obj.addProperty("name", league.getName());
        obj.addProperty("status", league.getStatus().name());
        obj.addProperty("team_mode", league.getTeamMode().name());
        obj.addProperty("scoring_system", league.getScoringSystem());
        obj.addProperty("teams", league.getTeamsView().size());
        obj.addProperty("drivers", league.getDriversView().size());
        if (!detailed) return obj;

        obj.addProperty("creator", league.getCreatorUUID().toString());
        obj.addProperty("mulligan", league.getMulliganCount());

        JsonArray teams = new JsonArray();
        for (var team : league.getTeamsView()) {
            JsonObject t = new JsonObject();
            t.addProperty("id", team.getId());
            t.addProperty("name", team.getName());
            t.addProperty("color", team.getColorHex());
            teams.add(t);
        }
        obj.add("teams_list", teams);

        JsonArray categories = new JsonArray();
        for (var category : league.getCategories().values()) {
            JsonObject c = new JsonObject();
            c.addProperty("id", category.getId());
            c.addProperty("name", category.getName());
            c.addProperty("display_name", category.getDisplayName());
            categories.add(c);
        }
        obj.add("categories", categories);

        JsonArray calendar = new JsonArray();
        for (var entry : league.getCalendar().values()) {
            JsonObject c = new JsonObject();
            c.addProperty("event_id", entry.getEventId());
            c.addProperty("category", entry.hasCategory() ? entry.getCategoryName() : null);
            if (entry.hasPinnedHeat()) {
                c.addProperty("pinned_heat", entry.getPinnedHeatId());
            }
            calendar.add(c);
        }
        obj.add("calendar", calendar);

        return obj;
    }

    private JsonObject serializeDriverResult(dev.EfraGroup.formulaRacing.Participant.Driver driver) {
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", driver.getUuid().toString());
        String name = driver.getName();
        obj.addProperty("name", name != null ? name : offlineName(driver.getUuid()));
        obj.addProperty("position", driver.getPosition());
        obj.addProperty("start_position", driver.getStartPosition());
        obj.addProperty("finished", driver.isFinished());
        obj.addProperty("dnf", driver.isDnf());
        long totalTime = driver.getTotalTime();
        obj.addProperty("total_time_ms", totalTime);
        obj.addProperty("total_time", totalTime > 0 ? formatTime(totalTime) : "--:--:---");
        if (driver.getFastestLap() != null) {
            obj.addProperty("fastest_lap_ms", driver.getFastestLap().getLapTime());
            obj.addProperty("fastest_lap", formatTime(driver.getFastestLap().getLapTime()));
        } else {
            obj.addProperty("fastest_lap", "--:--:---");
        }
        return obj;
    }

    private String formatTime(long milliseconds) {        long minutes = (milliseconds / 60000) % 60;
        long seconds = (milliseconds / 1000) % 60;
        long millis = milliseconds % 1000;
        return String.format("%02d:%02d:%03d", minutes, seconds, millis);
    }

    private String formatTimeLabel(long timestamp, String pattern) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern);
        return sdf.format(new java.util.Date(timestamp));
    }

    private void setupEndpoints() {
        // ============================================================
        //  1. TRACKS (v1 + v3)
        // ============================================================
        // GET /api/v1/readonly/tracks - List all tracks
        get("/api/v1/readonly/tracks", (request, response) -> {
            try {
                Map<String, DatabaseManager.TrackData> tracks = plugin.getDatabaseManager().getAllTracksWithData();

                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("number", tracks.size());

                JsonArray tracksArray = new JsonArray();
                for (Map.Entry<String, DatabaseManager.TrackData> entry : tracks.entrySet()) {
                    DatabaseManager.TrackData trackData = entry.getValue();
                    JsonObject trackObj = new JsonObject();
                    trackObj.addProperty("name", entry.getKey());
                    trackObj.addProperty("world", trackData.getWorldName());
                    trackObj.addProperty("creator", trackData.getOwnerName());
                    trackObj.addProperty("checkpoints", trackData.getTotalCheckpoints());
                    trackObj.addProperty("icon", trackData.getIconName() != null ? trackData.getIconName() : "null");
                    tracksArray.add(trackObj);
                }
                responseObject.add("tracks", tracksArray);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/tracks - " + tracks.size() + " tracks returned");
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /tracks endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading tracks: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/tracks/:trackname - Details of a specific track
        get("/api/v1/readonly/tracks/:trackname", (request, response) -> {
            try {
                String trackName = request.params("trackname");
                if (trackName == null) {
                    halt(400, createErrorResponse("Track name is required"));
                }

                DatabaseManager.TrackData trackData = plugin.getDatabaseManager().getTrackData(trackName);
                if (trackData == null) {
                    halt(404, createErrorResponse("Track not found: " + trackName));
                }

                Location spawn = trackData.getSpawnLocation();
                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("name", trackName);
                responseObject.addProperty("world", trackData.getWorldName());
                responseObject.addProperty("creator", trackData.getOwnerName());
                responseObject.addProperty("checkpoints", trackData.getTotalCheckpoints());
                responseObject.addProperty("icon", trackData.getIconName() != null ? trackData.getIconName() : "null");

                JsonObject spawnObj = new JsonObject();
                if (spawn != null) {
                    spawnObj.addProperty("x", spawn.getX());
                    spawnObj.addProperty("y", spawn.getY());
                    spawnObj.addProperty("z", spawn.getZ());
                    spawnObj.addProperty("yaw", spawn.getYaw());
                    spawnObj.addProperty("pitch", spawn.getPitch());
                    spawnObj.addProperty("world", spawn.getWorld() != null ? spawn.getWorld().getName() : "null");
                }
                responseObject.add("spawn", spawnObj);

                // Medals/Ranks
                Map<String, String> rankTimes = plugin.getDatabaseManager().getTrackRankTimes(trackName.replaceAll("\\s+", ""));
                JsonObject medalsObj = new JsonObject();
                for (Map.Entry<String, String> entry : rankTimes.entrySet()) {
                    medalsObj.addProperty(entry.getKey(), entry.getValue());
                }
                responseObject.add("medals", medalsObj);

                // Track record
                // DB stores seconds, convert to ms
                Double trackRecord = plugin.getDatabaseManager().getTrackRecord(trackName);
                responseObject.addProperty("record", trackRecord != null ? trackRecord * 1000.0 : 0);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/tracks/" + trackName);
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /tracks/:trackname endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading track details: " + e.getMessage());
            }
        });

        // ============================================================
        //  2. PLAYERS (v1)
        // ============================================================
        // GET /api/v1/readonly/players - List all online players
        get("/api/v1/readonly/players", (request, response) -> {
            try {
                JsonArray playersArray = new JsonArray();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    JsonObject playerObj = new JsonObject();
                    playerObj.addProperty("uuid", player.getUniqueId().toString());
                    playerObj.addProperty("name", player.getName());
                    playerObj.addProperty("online", true);

                    // Player settings
                    String language = plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
                    playerObj.addProperty("language", language != null ? language : "en");

                    String color1 = plugin.getDatabaseManager().getPlayerColor1(player.getUniqueId());
                    playerObj.addProperty("color1", color1 != null ? color1 : "#FFFFFF");

                    String color2 = plugin.getDatabaseManager().getPlayerColor2(player.getUniqueId());
                    playerObj.addProperty("color2", color2 != null ? color2 : "#FFFFFF");

                    int boatType = plugin.getDatabaseManager().getPlayerBoatType(player.getUniqueId());
                    playerObj.addProperty("boat_type", boatType);

                    boolean scoreboard = plugin.getDatabaseManager().getTimeTrialScoreboard(player.getUniqueId());
                    playerObj.addProperty("scoreboard", scoreboard);

                    boolean compactMode = plugin.getDatabaseManager().getPlayerCompactMode(player.getUniqueId());
                    playerObj.addProperty("compact_mode", compactMode);

                    playersArray.add(playerObj);
                }

                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("total", playersArray.size());
                responseObject.add("players", playersArray);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/players - " + playersArray.size() + " players returned");
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /players endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading players: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/players/all - List all players (including offline)
        get("/api/v1/readonly/players/all", (request, response) -> {
            JsonArray playersArray = new JsonArray();

            try {
                // Add online players
                for (Player player : Bukkit.getOnlinePlayers()) {
                    JsonObject playerObj = new JsonObject();
                    playerObj.addProperty("uuid", player.getUniqueId().toString());
                    playerObj.addProperty("name", player.getName());
                    playerObj.addProperty("online", true);

                    // Player settings
                    String language = plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
                    playerObj.addProperty("language", language != null ? language : "en");

                    String color1 = plugin.getDatabaseManager().getPlayerColor1(player.getUniqueId());
                    playerObj.addProperty("color1", color1 != null ? color1 : "#FFFFFF");

                    String color2 = plugin.getDatabaseManager().getPlayerColor2(player.getUniqueId());
                    playerObj.addProperty("color2", color2 != null ? color2 : "#FFFFFF");

                    int boatType = plugin.getDatabaseManager().getPlayerBoatType(player.getUniqueId());
                    playerObj.addProperty("boat_type", boatType);

                    boolean scoreboard = plugin.getDatabaseManager().getTimeTrialScoreboard(player.getUniqueId());
                    playerObj.addProperty("scoreboard", scoreboard);

                    boolean compactMode = plugin.getDatabaseManager().getPlayerCompactMode(player.getUniqueId());
                    playerObj.addProperty("compact_mode", compactMode);

                    playersArray.add(playerObj);
                }

                // Note: Adding offline players would require database access
                // This can be implemented later if needed

                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("total", playersArray.size());
                responseObject.add("players", playersArray);

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /players/all endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading players: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/players/:uuidorusername - Player information
        get("/api/v1/readonly/players/:uuidorusername", (request, response) -> {
            try {
                String uuidOrUsername = request.params("uuidorusername");
                if (uuidOrUsername == null) {
                    halt(400, createErrorResponse("UUID or username is required"));
                }

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidOrUsername);
                } catch (IllegalArgumentException e) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(uuidOrUsername);
                    if (offline == null) {
                        halt(404, createErrorResponse("Player not found: " + uuidOrUsername));
                    }
                    uuid = offline.getUniqueId();
                }

                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                if (player == null || !player.hasPlayedBefore()) {
                    halt(404, createErrorResponse("Player not found"));
                }

                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("uuid", uuid.toString());
                responseObject.addProperty("name", player.getName());
                responseObject.addProperty("online", player.isOnline());

                // Player settings
                String language = plugin.getDatabaseManager().getPlayerLanguage(uuid);
                responseObject.addProperty("language", language != null ? language : "en");

                String color1 = plugin.getDatabaseManager().getPlayerColor1(uuid);
                responseObject.addProperty("color1", color1 != null ? color1 : "#FFFFFF");

                String color2 = plugin.getDatabaseManager().getPlayerColor2(uuid);
                responseObject.addProperty("color2", color2 != null ? color2 : "#FFFFFF");

                int boatType = plugin.getDatabaseManager().getPlayerBoatType(uuid);
                responseObject.addProperty("boat_type", boatType);

                boolean scoreboard = plugin.getDatabaseManager().getTimeTrialScoreboard(uuid);
                responseObject.addProperty("scoreboard", scoreboard);

                boolean compactMode = plugin.getDatabaseManager().getPlayerCompactMode(uuid);
                responseObject.addProperty("compact_mode", compactMode);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/players/" + uuidOrUsername);
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /players/:uuidorusername endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading player: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/players/:uuid/timetrials/:trackname - Player's best time on a track
        get("/api/v1/readonly/players/:uuid/timetrials/:trackname", (request, response) -> {
            try {
                String uuidString = request.params("uuid");
                String trackName = request.params("trackname");

                if (uuidString == null || trackName == null) {
                    halt(400, createErrorResponse("UUID and track name are required"));
                }

                UUID uuid = null;
                try {
                    uuid = UUID.fromString(uuidString);
                } catch (IllegalArgumentException e) {
                    halt(400, createErrorResponse("Invalid UUID format"));
                }

                Double bestTime = plugin.getDatabaseManager().getPlayerBestFinishedTime(uuid, trackName);
                int rank = plugin.getDatabaseManager().getPlayerRank(uuid, trackName);

                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("uuid", uuid.toString());
                responseObject.addProperty("track", trackName);
                // DB stores seconds, convert to ms
                responseObject.addProperty("best_time", bestTime != null ? bestTime * 1000.0 : 0);
                responseObject.addProperty("rank", rank);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/players/" + uuidString + "/timetrials/" + trackName);
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /players/:uuid/timetrials/:trackname endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading player timetrial: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/leaderboard/:trackname - Track leaderboard
        get("/api/v1/readonly/leaderboard/:trackname", (request, response) -> {
            try {
                String trackName = request.params("trackname");
                if (trackName == null) {
                    halt(400, createErrorResponse("Track name is required"));
                }

                Map<String, String> rankTimes = plugin.getDatabaseManager().getTrackRankTimes(trackName.replaceAll("\\s+", ""));

                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("track", trackName);
                responseObject.addProperty("total_ranks", rankTimes.size());

                JsonArray ranksArray = new JsonArray();
                for (Map.Entry<String, String> entry : rankTimes.entrySet()) {
                    JsonObject rankObj = new JsonObject();
                    rankObj.addProperty("medal", entry.getKey());
                    rankObj.addProperty("time", entry.getValue());
                    ranksArray.add(rankObj);
                }
                responseObject.add("ranks", ranksArray);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/leaderboard/" + trackName);
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /leaderboard/:trackname endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading leaderboard: " + e.getMessage());
            }
        });

        // ============================================================
        //  3. SERVER STATUS (v1)
        // ============================================================
        // ============================================================
        //  8. LEAGUES (v1)
        // ============================================================
        // GET /api/v1/readonly/leagues - All leagues
        get("/api/v1/readonly/leagues", (request, response) -> {
            try {
                JsonObject responseObject = new JsonObject();
                JsonArray leagues = new JsonArray();

                for (var league : plugin.getLeagueManager().getAllLeagues()) {
                    leagues.add(serializeLeague(league, false));
                }

                responseObject.add("leagues", leagues);
                responseObject.addProperty("total", leagues.size());

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/leagues - " + leagues.size() + " leagues returned");
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /leagues endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading leagues: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/leagues/:name - League details
        get("/api/v1/readonly/leagues/:name", (request, response) -> {
            try {
                var league = requireLeague(request.params("name"));
                return serializeLeague(league, true).toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /leagues/:name endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading league: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/leagues/:name/standings/drivers - Driver standings
        get("/api/v1/readonly/leagues/:name/standings/drivers", (request, response) -> {
            try {
                var league = requireLeague(request.params("name"));
                JsonObject responseObject = new JsonObject();
                JsonArray standings = new JsonArray();

                for (var standing : plugin.getLeagueManager().getDriverStandings(league)) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("uuid", standing.getPlayerUUID().toString());
                    obj.addProperty("name", offlineName(standing.getPlayerUUID()));
                    obj.addProperty("points", standing.getPoints());
                    obj.addProperty("wins", standing.getWins());
                    obj.addProperty("podiums", standing.getPodiums());
                    obj.addProperty("events", standing.getEventsCount());
                    standings.add(obj);
                }

                responseObject.addProperty("league", league.getName());
                responseObject.add("standings", standings);
                responseObject.addProperty("total", standings.size());

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /leagues/:name/standings/drivers endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading driver standings: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/leagues/:name/standings/teams - Team standings
        get("/api/v1/readonly/leagues/:name/standings/teams", (request, response) -> {
            try {
                var league = requireLeague(request.params("name"));
                JsonObject responseObject = new JsonObject();
                JsonArray standings = new JsonArray();

                for (var standing : plugin.getLeagueManager().getTeamStandings(league)) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("team_id", standing.getTeamId());
                    obj.addProperty("team_name", standing.getTeamName());
                    obj.addProperty("points", standing.getPoints());
                    obj.addProperty("wins", standing.getWins());
                    obj.addProperty("podiums", standing.getPodiums());
                    standings.add(obj);
                }

                responseObject.addProperty("league", league.getName());
                responseObject.add("standings", standings);
                responseObject.addProperty("total", standings.size());

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /leagues/:name/standings/teams endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading team standings: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/leagues/:name/calendar - League calendar
        get("/api/v1/readonly/leagues/:name/calendar", (request, response) -> {
            try {
                var league = requireLeague(request.params("name"));
                JsonObject responseObject = new JsonObject();
                JsonArray calendar = new JsonArray();

                for (var entry : league.getCalendar().values()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("event_id", entry.getEventId());
                    obj.addProperty("category", entry.hasCategory() ? entry.getCategoryName() : null);
                    if (entry.hasPinnedHeat()) {
                        obj.addProperty("pinned_heat", entry.getPinnedHeatId());
                    }
                    calendar.add(obj);
                }

                responseObject.addProperty("league", league.getName());
                responseObject.add("calendar", calendar);
                responseObject.addProperty("total", calendar.size());

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /leagues/:name/calendar endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading league calendar: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/leagues/:name/team/:team - Team details
        get("/api/v1/readonly/leagues/:name/team/:team", (request, response) -> {
            try {
                var league = requireLeague(request.params("name"));
                String teamName = request.params("team");

                var team = league.getTeamsView().stream()
                        .filter(t -> t.getName().equalsIgnoreCase(teamName))
                        .findFirst()
                        .orElse(null);
                if (team == null) {
                    halt(404, createErrorResponse("Team not found: " + teamName));
                }

                JsonObject obj = new JsonObject();
                obj.addProperty("id", team.getId());
                obj.addProperty("name", team.getName());
                obj.addProperty("color", team.getColorHex());

                JsonArray drivers = new JsonArray();
                for (var driver : league.getDriversView()) {
                    if (team.getId() == (driver.getTeamId() == null ? -1 : driver.getTeamId())) {
                        JsonObject d = new JsonObject();
                        d.addProperty("uuid", driver.getPlayerUUID().toString());
                        d.addProperty("name", offlineName(driver.getPlayerUUID()));
                        drivers.add(d);
                    }
                }
                obj.add("drivers", drivers);
                obj.addProperty("driver_count", drivers.size());

                return obj.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /leagues/:name/team/:team endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading team: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/leagues/:name/categories - League categories
        get("/api/v1/readonly/leagues/:name/categories", (request, response) -> {
            try {
                var league = requireLeague(request.params("name"));
                JsonObject responseObject = new JsonObject();
                JsonArray categories = new JsonArray();

                for (var category : league.getCategories().values()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", category.getId());
                    obj.addProperty("name", category.getName());
                    obj.addProperty("display_name", category.getDisplayName());
                    categories.add(obj);
                }

                responseObject.addProperty("league", league.getName());
                responseObject.add("categories", categories);
                responseObject.addProperty("total", categories.size());

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /leagues/:name/categories endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading categories: " + e.getMessage());
            }
        });

        // ============================================================
        //  9. DUELS (v1)
        // ============================================================
        // GET /api/v1/readonly/duels/leaderboard - ELO leaderboard
        get("/api/v1/readonly/duels/leaderboard", (request, response) -> {
            try {
                int limit = parseLimit(request, 50, 500);
                JsonObject responseObject = new JsonObject();
                JsonArray board = new JsonArray();

                for (Map<String, Object> row : plugin.getDatabaseManager().getEloLeaderboard(limit)) {
                    JsonObject obj = new JsonObject();
                    String uuid = String.valueOf(row.get("uuid"));
                    obj.addProperty("uuid", uuid);
                    obj.addProperty("name", offlineName(uuid));
                    obj.addProperty("elo", ((Number) row.getOrDefault("elo", 1200)).intValue());
                    obj.addProperty("wins", ((Number) row.getOrDefault("wins", 0)).intValue());
                    obj.addProperty("losses", ((Number) row.getOrDefault("losses", 0)).intValue());
                    board.add(obj);
                }

                responseObject.add("leaderboard", board);
                responseObject.addProperty("total", board.size());

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/duels/leaderboard - " + board.size() + " entries returned");
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /duels/leaderboard endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading duel leaderboard: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/duels/players/:uuidorusername - Duel stats for a player
        get("/api/v1/readonly/duels/players/:uuidorusername", (request, response) -> {
            try {
                UUID uuid = resolvePlayerUuid(request.params("uuidorusername"));
                Map<String, Object> record = plugin.getDatabaseManager().getEloRecord(uuid);

                JsonObject obj = new JsonObject();
                obj.addProperty("uuid", uuid.toString());
                obj.addProperty("name", offlineName(uuid));
                obj.addProperty("elo", ((Number) record.getOrDefault("elo", 1200)).intValue());
                obj.addProperty("wins", ((Number) record.getOrDefault("wins", 0)).intValue());
                obj.addProperty("losses", ((Number) record.getOrDefault("losses", 0)).intValue());

                return obj.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /duels/players/:uuidorusername endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading duel stats: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/duels/players/:uuidorusername/matches - Recent duel matches
        get("/api/v1/readonly/duels/players/:uuidorusername/matches", (request, response) -> {
            try {
                UUID uuid = resolvePlayerUuid(request.params("uuidorusername"));
                int limit = parseLimit(request, 20, 100);

                JsonObject responseObject = new JsonObject();
                JsonArray matches = new JsonArray();

                for (Map<String, Object> row : plugin.getDatabaseManager().getRecentDuelsForPlayer(uuid.toString(), limit)) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", ((Number) row.getOrDefault("id", 0)).intValue());
                    obj.addProperty("track", String.valueOf(row.get("track")));
                    obj.addProperty("state", String.valueOf(row.get("state")));
                    obj.addProperty("owner", String.valueOf(row.get("owner")));
                    obj.addProperty("winner", row.get("winner") == null ? null : String.valueOf(row.get("winner")));
                    obj.addProperty("laps", ((Number) row.getOrDefault("laps", 0)).intValue());
                    obj.addProperty("started", String.valueOf(row.get("started")));
                    obj.addProperty("finished", row.get("finished") == null ? null : String.valueOf(row.get("finished")));
                    String players = row.get("players") == null ? "" : String.valueOf(row.get("players"));
                    JsonArray playerArray = new JsonArray();
                    for (String part : players.split(",")) {
                        String trimmed = part.trim();
                        if (trimmed.isEmpty()) continue;
                        JsonObject p = new JsonObject();
                        p.addProperty("uuid", trimmed);
                        p.addProperty("name", offlineName(trimmed));
                        playerArray.add(p);
                    }
                    obj.add("players", playerArray);
                    matches.add(obj);
                }

                responseObject.addProperty("uuid", uuid.toString());
                responseObject.add("matches", matches);
                responseObject.addProperty("total", matches.size());

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /duels/players/:uuidorusername/matches endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading duel matches: " + e.getMessage());
            }
        });

        // ============================================================
        //  10. DAILY RACE (v1)
        // ============================================================
        // GET /api/v1/readonly/dailyrace - Active daily race info
        get("/api/v1/readonly/dailyrace", (request, response) -> {
            try {
                var daily = plugin.getDailyRaceManager();
                if (daily == null) {
                    halt(503, createErrorResponse("Daily race system not available"));
                }

                JsonObject obj = new JsonObject();
                var activeOpt = daily.getActiveDailyEvent();
                obj.addProperty("active", activeOpt.isPresent());
                if (activeOpt.isPresent()) {
                    var event = activeOpt.get();
                    obj.addProperty("event_id", event.getId());
                    obj.addProperty("event_name", event.getDisplayName());
                    obj.addProperty("track", event.getTrackNameWS());
                    obj.addProperty("state", event.getState().name());
                    obj.addProperty("phase", daily.getPhase().name());
                    obj.addProperty("practice_remaining_ms", daily.getPracticeTimeRemaining());
                }

                JsonArray excluded = new JsonArray();
                for (String track : daily.getExcludedTracks()) {
                    excluded.add(track);
                }
                obj.add("excluded_tracks", excluded);

                return obj.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /dailyrace endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading daily race: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/dailyrace/results/latest - Latest finished heat results
        get("/api/v1/readonly/dailyrace/results/latest", (request, response) -> {
            try {
                var daily = plugin.getDailyRaceManager();
                if (daily == null) {
                    halt(503, createErrorResponse("Daily race system not available"));
                }
                var activeOpt = daily.getActiveDailyEvent();
                if (activeOpt.isEmpty()) {
                    halt(404, createErrorResponse("No active daily race"));
                }

                var event = activeOpt.get();
                dev.EfraGroup.formulaRacing.Heat.Heats latest = null;
                for (var round : event.getSchedule().getRoundsList()) {
                    for (var heat : round.getHeats().values()) {
                        if (heat.getHeatState() == dev.EfraGroup.formulaRacing.Heat.HeatState.FINISHED) {
                            if (latest == null || heat.getId() > latest.getId()) {
                                latest = heat;
                            }
                        }
                    }
                }
                if (latest == null) {
                    halt(404, createErrorResponse("No finished heats in the daily race yet"));
                }

                JsonObject obj = new JsonObject();
                obj.addProperty("event_id", event.getId());
                obj.addProperty("event_name", event.getDisplayName());
                obj.addProperty("heat_id", latest.getId());
                obj.addProperty("track", latest.getTrackNameWS());

                JsonArray results = new JsonArray();
                for (var driver : dev.EfraGroup.formulaRacing.Event.EventResults.generateHeatResults(latest)) {
                    results.add(serializeDriverResult(driver));
                }
                obj.add("results", results);
                obj.addProperty("total", results.size());

                return obj.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /dailyrace/results/latest endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading daily race results: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/dailyrace/stats - Daily race stats overview
        get("/api/v1/readonly/dailyrace/stats", (request, response) -> {
            try {
                var daily = plugin.getDailyRaceManager();
                if (daily == null) {
                    halt(503, createErrorResponse("Daily race system not available"));
                }

                JsonObject obj = new JsonObject();
                var activeOpt = daily.getActiveDailyEvent();
                obj.addProperty("active", activeOpt.isPresent());
                if (activeOpt.isPresent()) {
                    var event = activeOpt.get();
                    obj.addProperty("event_id", event.getId());
                    obj.addProperty("event_name", event.getDisplayName());
                    obj.addProperty("track", event.getTrackNameWS());
                    obj.addProperty("phase", daily.getPhase().name());
                }
                obj.addProperty("excluded_tracks", daily.getExcludedTracks().size());
                obj.addProperty("total_events", plugin.getRaceEventManager().getAllEvents().size());

                return obj.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /dailyrace/stats endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading daily race stats: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/dailyrace/stats/:uuidorusername - Player daily/duel stats
        get("/api/v1/readonly/dailyrace/stats/:uuidorusername", (request, response) -> {
            try {
                UUID uuid = resolvePlayerUuid(request.params("uuidorusername"));
                Map<String, Object> record = plugin.getDatabaseManager().getEloRecord(uuid);

                JsonObject obj = new JsonObject();
                obj.addProperty("uuid", uuid.toString());
                obj.addProperty("name", offlineName(uuid));
                obj.addProperty("elo", ((Number) record.getOrDefault("elo", 1200)).intValue());
                obj.addProperty("wins", ((Number) record.getOrDefault("wins", 0)).intValue());
                obj.addProperty("losses", ((Number) record.getOrDefault("losses", 0)).intValue());

                return obj.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /dailyrace/stats/:uuidorusername endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading player stats: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/dailyrace/excluded-tracks - Excluded tracks
        get("/api/v1/readonly/dailyrace/excluded-tracks", (request, response) -> {
            try {
                var daily = plugin.getDailyRaceManager();
                if (daily == null) {
                    halt(503, createErrorResponse("Daily race system not available"));
                }

                JsonObject responseObject = new JsonObject();
                JsonArray tracks = new JsonArray();
                for (String track : daily.getExcludedTracks()) {
                    tracks.add(track);
                }
                responseObject.add("excluded_tracks", tracks);
                responseObject.addProperty("total", tracks.size());

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /dailyrace/excluded-tracks endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading excluded tracks: " + e.getMessage());
            }
        });

        // ============================================================
        //  11. EVENT RESULTS (v1)
        // ============================================================
        // GET /api/v1/readonly/events/results/:eventname - Full results for an event
        get("/api/v1/readonly/events/results/:eventname", (request, response) -> {
            try {
                String eventName = request.params("eventname");
                var eventOpt = plugin.getRaceEventManager().getEventByName(eventName);
                if (eventOpt.isEmpty()) {
                    halt(404, createErrorResponse("Event not found: " + eventName));
                }
                var event = eventOpt.get();

                JsonObject obj = new JsonObject();
                obj.addProperty("id", event.getId());
                obj.addProperty("name", event.getDisplayName());
                obj.addProperty("track", event.getTrackNameWS());
                obj.addProperty("state", event.getState().name());

                JsonArray rounds = new JsonArray();
                for (var round : event.getSchedule().getRoundsList()) {
                    JsonObject roundObj = new JsonObject();
                    roundObj.addProperty("name", round.getDisplayName());
                    roundObj.addProperty("type", round.getRoundType().name());
                    roundObj.addProperty("state", round.getRoundState().name());

                    JsonArray heats = new JsonArray();
                    for (var heat : round.getHeats().values()) {
                        JsonObject heatObj = new JsonObject();
                        heatObj.addProperty("id", heat.getId());
                        heatObj.addProperty("track", heat.getTrackNameWS());
                        heatObj.addProperty("state", heat.getHeatState().name());
                        heatObj.addProperty("laps", heat.getTotalLaps());

                        JsonArray results = new JsonArray();
                        for (var driver : dev.EfraGroup.formulaRacing.Event.EventResults.generateHeatResults(heat)) {
                            results.add(serializeDriverResult(driver));
                        }
                        heatObj.add("results", results);
                        heats.add(heatObj);
                    }
                    roundObj.add("heats", heats);
                    rounds.add(roundObj);
                }
                obj.add("rounds", rounds);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/events/results/" + eventName);
                }

                return obj.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /events/results/:eventname endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading event results: " + e.getMessage());
            }
        });

        // GET /api/v1/admin/ping - Auth check (requires token when auth.enabled=true)
        get("/api/v1/admin/ping", (request, response) -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("ok", true);
            obj.addProperty("auth", authEnabled ? "enabled" : "disabled");
            obj.addProperty("timestamp", System.currentTimeMillis());
            return gson.toJson(obj);
        });

        // GET /api/v1/readonly/status - API status
        get("/api/v1/readonly/status", (request, response) -> {
            try {
                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("status", "online");
                responseObject.addProperty("version", plugin.getDescription().getVersion());
                responseObject.addProperty("server", Bukkit.getServer().getName());
                responseObject.addProperty("players_online", Bukkit.getServer().getOnlinePlayers().size());
                responseObject.addProperty("max_players", Bukkit.getServer().getMaxPlayers());

                Map<String, DatabaseManager.TrackData> tracks = plugin.getDatabaseManager().getAllTracksWithData();
                responseObject.addProperty("total_tracks", tracks.size());

                // Count active heats
                int activeHeats = 0;
                for (var event : plugin.getRaceEventManager().getAllEvents()) {
                    if (!event.isActive()) continue;
                    for (var round : event.getSchedule().getRoundsList()) {
                        for (var heat : round.getHeats().values()) {
                            if (heat.getHeatState() == dev.EfraGroup.formulaRacing.Heat.HeatState.RACING ||
                                heat.getHeatState() == dev.EfraGroup.formulaRacing.Heat.HeatState.STARTING) {
                                activeHeats++;
                            }
                        }
                    }
                }
                responseObject.addProperty("active_heats", activeHeats);

                // Count total events
                responseObject.addProperty("total_events", plugin.getRaceEventManager().getAllEvents().size());

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/status");
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /status endpoint: " + e.getMessage());
                e.printStackTrace();
                logError("Erro no endpoint /status: " + e.getMessage());
                return createErrorResponse("Error loading status: " + e.getMessage());
            }
        });

        // ============================================================
        //  4. LIVE / REAL-TIME (v1)
        // ============================================================
        // GET /api/v1/readonly/live/positions - Real-time positions
        get("/api/v1/readonly/live/positions", (request, response) -> {
            try {
                String heatId = request.queryParams("heat_id");

                JsonObject responseObj = new JsonObject();
                responseObj.addProperty("timestamp", System.currentTimeMillis());
                JsonArray positions = new JsonArray();

                if (heatId != null && !heatId.isEmpty()) {
                    try {
                        int id = Integer.parseInt(heatId);
                        var heatOpt = plugin.getRaceEventManager().getHeat(id);
                        if (heatOpt.isPresent()) {
                            var heat = heatOpt.get();
                            responseObj.addProperty("heat_id", id);
                            responseObj.addProperty("state", heat.getHeatState().name());
                            responseObj.addProperty("track", heat.getTrackNameWS());

                            for (var driver : heat.getLivePositions()) {
                                positions.add(createDriverPositionObject(driver, heat));
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                } else {
                    // All active heats
                    for (var event : plugin.getRaceEventManager().getAllEvents()) {
                        if (!event.isActive()) continue;
                        for (var round : event.getSchedule().getRoundsList()) {
                            for (var heat : round.getHeats().values()) {
                                if (heat.getHeatState() == dev.EfraGroup.formulaRacing.Heat.HeatState.RACING ||
                                    heat.getHeatState() == dev.EfraGroup.formulaRacing.Heat.HeatState.STARTING) {

                                    JsonObject heatObj = new JsonObject();
                                    heatObj.addProperty("heat_id", heat.getId());
                                    heatObj.addProperty("state", heat.getHeatState().name());
                                    heatObj.addProperty("track", heat.getTrackNameWS());

                                    JsonArray heatPositions = new JsonArray();
                                    for (var driver : heat.getLivePositions()) {
                                        heatPositions.add(createDriverPositionObject(driver, heat));
                                    }
                                    heatObj.add("positions", heatPositions);
                                    positions.add(heatObj);
                                }
                            }
                        }
                    }
                }

                responseObj.add("data", positions);
                return responseObj.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /live/positions endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading live positions: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/live/events - Active events
        get("/api/v1/readonly/live/events", (request, response) -> {
            try {
                JsonObject responseObj = new JsonObject();
                responseObj.addProperty("timestamp", System.currentTimeMillis());
                JsonArray events = new JsonArray();

                for (var event : plugin.getRaceEventManager().getAllEvents()) {
                    if (!event.isActive()) continue;

                    JsonObject eventObj = new JsonObject();
                    eventObj.addProperty("event_id", event.getId());
                    eventObj.addProperty("name", event.getDisplayName());
                    eventObj.addProperty("state", event.getState().name());
                    eventObj.addProperty("track", event.getTrackNameWS());

                    var roundOpt = event.getSchedule().getCurrentRound();
                    if (roundOpt.isPresent()) {
                        var round = roundOpt.get();
                        eventObj.addProperty("round", round.getDisplayName());

                        var heatOpt = round.getCurrentHeat();
                        if (heatOpt.isPresent()) {
                            var heat = heatOpt.get();
                            eventObj.addProperty("heat_id", heat.getId());
                            eventObj.addProperty("heat_state", heat.getHeatState().name());
                            eventObj.addProperty("drivers", heat.getDriverCount());
                            eventObj.addProperty("laps", heat.getTotalLaps());
                        }
                    }
                    events.add(eventObj);
                }

                responseObj.add("events", events);
                return responseObj.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /live/events endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading live events: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/tracks/:trackname/times - Track times
        get("/api/v1/readonly/tracks/:trackname/times", (request, response) -> {
            try {
                String trackName = request.params("trackname");
                if (trackName == null) {
                    halt(400, createErrorResponse("Track name is required"));
                }

                List<DatabaseManager.TrackRecord> topTimes = plugin.getDatabaseManager().getTopTimes(trackName);

                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("track", trackName);
                responseObject.addProperty("total", topTimes.size());

                JsonArray timesArray = new JsonArray();
                for (DatabaseManager.TrackRecord record : topTimes) {
                    JsonObject timeObj = new JsonObject();
                    // DB stores seconds, convert to ms
                    long timeMs = (long)(record.getTime() * 1000.0);
                    timeObj.addProperty("player_name", record.getPlayerName());
                    timeObj.addProperty("time", formatTime(timeMs));
                    timeObj.addProperty("time_ms", timeMs);
                    timeObj.addProperty("checkpoints", record.getCheckpointsReached());
                    timeObj.addProperty("finished", record.isFinished());
                    timeObj.addProperty("date", record.getTimeCreated());
                    timesArray.add(timeObj);
                }
                responseObject.add("times", timesArray);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/tracks/" + trackName + "/times - " + topTimes.size() + " times returned");
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /tracks/:trackname/times endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading track times: " + e.getMessage());
            }
        });

        // ============================================================
        //  5. ACTIVITY (v1)
        // ============================================================
        // GET /api/v1/readonly/activity/stats - Activity statistics
        get("/api/v1/readonly/activity/stats", (request, response) -> {
            try {
                String period = request.queryParams("period");
                if (period == null) period = "1h";

                JsonObject responseObject = new JsonObject();
                responseObject.addProperty("period", period);

                // Example data for server activity
                JsonArray labels = new JsonArray();
                JsonArray values = new JsonArray();

                long now = System.currentTimeMillis();
                long interval;

                switch (period) {
                    case "1h":
                        interval = 10 * 60 * 1000; // 10 minutes
                        for (int i = 6; i >= 0; i--) {
                            long time = now - (i * interval);
                            labels.add(formatTimeLabel(time, "HH:mm"));
                            values.add(Bukkit.getServer().getOnlinePlayers().size());
                        }
                        break;
                    case "24h":
                        interval = 60 * 60 * 1000; // 1 hour
                        for (int i = 24; i >= 0; i--) {
                            long time = now - (i * interval);
                            labels.add(formatTimeLabel(time, "HH:mm"));
                            values.add(Math.max(0, (int) (Math.random() * 20)));
                        }
                        break;
                    case "7d":
                        interval = 24 * 60 * 60 * 1000; // 1 day
                        for (int i = 7; i >= 0; i--) {
                            long time = now - (i * interval);
                            labels.add(formatTimeLabel(time, "dd/MM"));
                            values.add(Math.max(0, (int) (Math.random() * 30)));
                        }
                        break;
                    case "30d":
                        interval = 24 * 60 * 60 * 1000; // 1 day
                        for (int i = 30; i >= 0; i--) {
                            long time = now - (i * interval);
                            labels.add(formatTimeLabel(time, "dd/MM"));
                            values.add(Math.max(0, (int) (Math.random() * 40)));
                        }
                        break;
                    default:
                        interval = 60 * 60 * 1000; // 1 hour
                        for (int i = 24; i >= 0; i--) {
                            long time = now - (i * interval);
                            labels.add(formatTimeLabel(time, "HH:mm"));
                            values.add(Math.max(0, (int) (Math.random() * 20)));
                        }
                }

                responseObject.add("labels", labels);
                responseObject.add("values", values);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/activity/stats - period: " + period);
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /activity/stats endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading activity stats: " + e.getMessage());
            }
        });

        // GET /api/v1/readonly/activity/recent - Recent activity
        get("/api/v1/readonly/activity/recent", (request, response) -> {
            try {
                JsonObject responseObject = new JsonObject();
                JsonArray activities = new JsonArray();

                // Add example activities (in production, this would come from the database)
                int onlineCount = Bukkit.getServer().getOnlinePlayers().size();
                if (onlineCount > 0) {
                    JsonObject activity = new JsonObject();
                    activity.addProperty("type", "join");
                    activity.addProperty("title", "Jogadores Online");
                    activity.addProperty("message", onlineCount + " jogadores conectados");
                    activity.addProperty("timestamp", System.currentTimeMillis());
                    activities.add(activity);
                }

                // Add activities from active heats
                for (var event : plugin.getRaceEventManager().getAllEvents()) {
                    if (!event.isActive()) continue;
                    for (var round : event.getSchedule().getRoundsList()) {
                        for (var heat : round.getHeats().values()) {
                            if (heat.getHeatState() == dev.EfraGroup.formulaRacing.Heat.HeatState.RACING) {
                                JsonObject activity = new JsonObject();
                                activity.addProperty("type", "race");
                                activity.addProperty("title", "Corrida em Andamento");
                                activity.addProperty("message", "Heat #" + heat.getId() + " na pista " + heat.getTrackNameWS());
                                activity.addProperty("timestamp", System.currentTimeMillis());
                                activities.add(activity);
                            }
                        }
                    }
                }

                responseObject.add("activities", activities);

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/activity/recent - " + activities.size() + " activities returned");
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /activity/recent endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading recent activity: " + e.getMessage());
            }
        });

        // ============================================================
        //  6. EVENTS (v1)
        // ============================================================
        // GET /api/v1/readonly/events - All events
        get("/api/v1/readonly/events", (request, response) -> {
            try {
                JsonObject responseObject = new JsonObject();
                JsonArray events = new JsonArray();

                for (var event : plugin.getRaceEventManager().getAllEvents()) {
                    JsonObject eventObj = new JsonObject();
                    eventObj.addProperty("id", event.getId());
                    eventObj.addProperty("name", event.getDisplayName());
                    eventObj.addProperty("active", event.isActive());
                    eventObj.addProperty("state", event.getState().name());
                    eventObj.addProperty("track", event.getTrackNameWS());

                    var roundOpt = event.getSchedule().getCurrentRound();
                    if (roundOpt.isPresent()) {
                        var round = roundOpt.get();
                        eventObj.addProperty("round", round.getDisplayName());

                        var heatOpt = round.getCurrentHeat();
                        if (heatOpt.isPresent()) {
                            var heat = heatOpt.get();
                            eventObj.addProperty("heat_id", heat.getId());
                            eventObj.addProperty("drivers", heat.getDriverCount());
                            eventObj.addProperty("laps", heat.getTotalLaps());
                        }
                    }

                    eventObj.addProperty("start_time", event.getCreationTime() > 0 ? event.getCreationTime() : System.currentTimeMillis());
                    events.add(eventObj);
                }

                responseObject.add("events", events);
                responseObject.addProperty("total", events.size());

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/events - " + events.size() + " events returned");
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /events endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading events: " + e.getMessage());
            }
        });

        // ============================================================
        //  7. LOGS (v1)
        // ============================================================
        // GET /api/v1/readonly/logs - System logs
        get("/api/v1/readonly/logs", (request, response) -> {
            try {
                String level = request.queryParams("level");
                String search = request.queryParams("search");
                int limit = 50;

                try {
                    String limitParam = request.queryParams("limit");
                    if (limitParam != null) {
                        limit = Integer.parseInt(limitParam);
                        if (limit > MAX_LOGS) limit = MAX_LOGS;
                    }
                } catch (NumberFormatException ignored) {}

                JsonObject responseObject = new JsonObject();
                JsonArray logsArray = new JsonArray();

                synchronized (systemLogs) {
                    int count = 0;
                    for (int i = systemLogs.size() - 1; i >= 0 && count < limit; i--) {
                        JsonObject log = systemLogs.get(i);

                        // Filter by level
                        if (level != null && !level.isEmpty() && !level.equalsIgnoreCase("all")) {
                            String logLevel = log.get("level").getAsString();
                            if (!logLevel.equalsIgnoreCase(level)) {
                                continue;
                            }
                        }

                        // Filter by search
                        if (search != null && !search.isEmpty()) {
                            String message = log.get("message").getAsString().toLowerCase();
                            if (!message.contains(search.toLowerCase())) {
                                continue;
                            }
                        }

                        logsArray.add(log);
                        count++;
                    }
                }

                responseObject.add("logs", logsArray);
                responseObject.addProperty("total", logsArray.size());

                if (logRequests) {
                    plugin.getLogger().info("API: GET /api/v1/readonly/logs - " + logsArray.size() + " logs returned");
                }

                return responseObject.toString();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in /logs endpoint: " + e.getMessage());
                e.printStackTrace();
                return createErrorResponse("Error loading logs: " + e.getMessage());
            }
        });
    }

    private String createErrorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", true);
        error.addProperty("message", message);
        error.addProperty("timestamp", System.currentTimeMillis());
        return gson.toJson(error);
    }

    private void setupFiles() {
        File dataFolder = plugin.getDataFolder();

        // Create dashboard folder if it doesn't exist
        File dashboardDir = new File(dataFolder, "dashboard");
        if (!dashboardDir.exists()) {
            dashboardDir.mkdirs();
            plugin.getLogger().info("Created dashboard folder: " + dashboardDir.getAbsolutePath());

            // Copy dashboard files from JAR
            copyResourceFromJar("/dashboard/index.html", new File(dashboardDir, "index.html"));
            copyResourceFromJar("/dashboard/css/dashboard.css", new File(dashboardDir, "css/dashboard.css"));
            copyResourceFromJar("/dashboard/js/dashboard.js", new File(dashboardDir, "js/dashboard.js"));
            copyResourceFromJar("/dashboard/INSTALL.md", new File(dashboardDir, "INSTALL.md"));
            copyResourceFromJar("/dashboard/CONFIG_EXAMPLES.md", new File(dashboardDir, "CONFIG_EXAMPLES.md"));
        }

        // Create api_config.yml from the bundled resource if it doesn't exist
        File apiConfigFile = new File(dataFolder, "api_config.yml");
        if (!apiConfigFile.exists()) {
            try {
                plugin.saveResource("api_config.yml", false);
                plugin.getLogger().info("========================================");
                plugin.getLogger().info("Created api_config.yml");
                plugin.getLogger().info("========================================");
                plugin.getLogger().info("Dashboard URL: http://localhost:8080/dashboard/");
                plugin.getLogger().info("No authentication required!");
                plugin.getLogger().info("========================================");

                // Create DASHBOARD_SETUP.md file
                createDashboardSetupFile(dataFolder);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create api_config.yml: " + e.getMessage());
            }
        } else {
            // File already exists, just notify
            plugin.getLogger().info("api_config.yml already exists, using existing configuration");
        }
    }

    private void createDashboardSetupFile(File dataFolder) {
        File setupFile = new File(dataFolder, "DASHBOARD_SETUP.md");
        try {
            String content = "# FormulaRacing - Configuração da Dashboard\n\n" +
                "## Como Acessar a Dashboard\n\n" +
                "1. **Acesse a dashboard**: `http://localhost:8080/dashboard/`\n" +
                "2. **Nenhuma autenticação necessária!**\n\n" +
                "## Endpoints da API\n\n" +
                "### Status\n" +
                "- `GET /api/v1/readonly/status`\n\n" +
                "### Pistas\n" +
                "- `GET /api/v1/readonly/tracks`\n" +
                "- `GET /api/v1/readonly/tracks/:nome`\n\n" +
                "### Jogadores\n" +
                "- `GET /api/v1/readonly/players`\n" +
                "- `GET /api/v1/readonly/players/all`\n\n" +
                "### Ao Vivo\n" +
                "- `GET /api/v1/readonly/live/positions`\n" +
                "- `GET /api/v1/readonly/live/events`\n\n" +
                "### Ligas\n" +
                "- `GET /api/v1/readonly/leagues`\n" +
                "- `GET /api/v1/readonly/leagues/:nome`\n" +
                "- `GET /api/v1/readonly/leagues/:nome/standings/drivers`\n" +
                "- `GET /api/v1/readonly/leagues/:nome/standings/teams`\n" +
                "- `GET /api/v1/readonly/leagues/:nome/calendar`\n" +
                "- `GET /api/v1/readonly/leagues/:nome/team/:time`\n" +
                "- `GET /api/v1/readonly/leagues/:nome/categories`\n\n" +
                "### Duelos\n" +
                "- `GET /api/v1/readonly/duels/leaderboard`\n" +
                "- `GET /api/v1/readonly/duels/players/:uuid`\n" +
                "- `GET /api/v1/readonly/duels/players/:uuid/matches`\n\n" +
                "### Corrida diária\n" +
                "- `GET /api/v1/readonly/dailyrace`\n" +
                "- `GET /api/v1/readonly/dailyrace/results/latest`\n" +
                "- `GET /api/v1/readonly/dailyrace/stats`\n" +
                "- `GET /api/v1/readonly/dailyrace/stats/:uuid`\n" +
                "- `GET /api/v1/readonly/dailyrace/excluded-tracks`\n\n" +
                "### Resultados\n" +
                "- `GET /api/v1/readonly/events/results/:nome-do-evento`\n\n" +
                "### Admin (exige token se auth.enabled=true)\n" +
                "- `GET /api/v1/admin/ping`\n\n" +
                "## Autenticação\n\n" +
                "Por padrão a API é pública. Para proteger, edite `api_config.yml`:\n\n" +
                "```yaml\n" +
                "auth:\n" +
                "  enabled: true\n" +
                "  token: \"seu-token-aqui\"\n" +
                "  tokens:\n" +
                "    - name: \"site\"\n" +
                "      token: \"outro-token\"\n" +
                "  protect_logs: false\n" +
                "```\n\n" +
                "Com `enabled: true`, `/api/v1/admin/*` exige o token via header\n" +
                "`Authorization: Bearer <token>` ou query `?token=<token>`.\n" +
                "Com `protect_logs: true`, `/api/v1/readonly/logs` também exige.\n" +
                "Se habilitar sem token, um token aleatório é gerado e exibido no console.\n\n" +
                "## Solução de Problemas\n\n" +
                "### Dashboard não carrega\n" +
                "1. Verifique se o servidor está rodando\n" +
                "2. Verifique se a porta 8080 está disponível\n" +
                "3. Verifique o console para erros\n\n" +
                "### API não responde\n" +
                "1. Verifique se o plugin está habilitado\n" +
                "2. Verifique o arquivo api_config.yml\n" +
                "3. Verifique os logs do servidor\n";

            Files.writeString(setupFile.toPath(), content);
            plugin.getLogger().info("Created DASHBOARD_SETUP.md");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create DASHBOARD_SETUP.md: " + e.getMessage());
        }
    }

    private void copyResourceFromJar(String resourcePath, File targetFile) {
        try {
            targetFile.getParentFile().mkdirs();
            try (var inputStream = getClass().getResourceAsStream(resourcePath)) {
                if (inputStream != null) {
                    Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Copied " + resourcePath + " to " + targetFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to copy " + resourcePath + ": " + e.getMessage());
        }
    }

    /**
     * Carrega as chaves nomeadas (auth.tokens: [{name, token}, ...]) para o mapa token->nome.
     */
    @SuppressWarnings("unchecked")
    private void loadNamedTokens() {
        authTokensByValue.clear();
        if (config == null) return;
        try {
            for (Map<?, ?> entry : config.getMapList("auth.tokens")) {
                Object name = entry.get("name");
                Object token = entry.get("token");
                if (name instanceof String && token instanceof String
                        && !((String) name).isEmpty() && !((String) token).isEmpty()) {
                    authTokensByValue.put((String) token, (String) name);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load auth.tokens: " + e.getMessage());
        }
        if (authEnabled) {
            plugin.getLogger().info("API auth: " + authTokensByValue.size() + " chave(s) nomeada(s) + "
                    + ((authToken != null && !authToken.isEmpty()) ? "token legado" : "sem token legado"));
        }
    }

    /**
     * Completa chaves de auth ausentes em api_config.yml de instalações antigas.
     * Se auth.enabled=true sem token, gera um token aleatório, salva e exibe no console.
     */
    private void ensureAuthDefaults(File apiConfigFile) {
        try {
            boolean changed = false;
            if (!config.contains("auth.enabled")) {
                config.set("auth.enabled", false);
                changed = true;
            }
            if (!config.contains("auth.token")) {
                config.set("auth.token", "");
                changed = true;
            }
            if (!config.contains("auth.protect_logs")) {
                config.set("auth.protect_logs", false);
                changed = true;
            }
            if (!config.contains("auth.tokens")) {
                config.set("auth.tokens", new java.util.ArrayList<>());
                changed = true;
            }
            authEnabled = config.getBoolean("auth.enabled", false);
            authToken = config.getString("auth.token", "");
            authProtectLogs = config.getBoolean("auth.protect_logs", false);
            loadNamedTokens();

            if (authEnabled && (authToken == null || authToken.isEmpty()) && authTokensByValue.isEmpty()) {
                authToken = generateToken();
                config.set("auth.token", authToken);
                changed = true;
                plugin.getLogger().info("========================================");
                plugin.getLogger().info("API auth enabled: generated token");
                plugin.getLogger().info("Token: " + authToken);
                plugin.getLogger().info("Guarde este token, ele não será exibido de novo!");
                plugin.getLogger().info("========================================");
            }
            if (changed) {
                config.save(apiConfigFile);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update auth defaults: " + e.getMessage());
        }
    }

    private void loadConfig() {
        try {
            // API config lives only in api_config.yml (bundled resource, no config.yml fallback)
            File apiConfigFile = new File(plugin.getDataFolder(), "api_config.yml");
            if (apiConfigFile.exists()) {
                config = YamlConfiguration.loadConfiguration(apiConfigFile);
                port = config.getInt("port", 8080);
                corsEnabled = config.getBoolean("enable_cors", true);
                rateLimitEnabled = config.getBoolean("rate_limit.enabled", true);
                requestsPerMinute = config.getInt("rate_limit.requests_per_minute", 60);
                logRequests = config.getBoolean("log_requests", true);
                logErrors = config.getBoolean("log_errors", true);
                authEnabled = config.getBoolean("auth.enabled", false);
                authToken = config.getString("auth.token", "");
                authProtectLogs = config.getBoolean("auth.protect_logs", false);
                loadNamedTokens();
                plugin.getLogger().info("Loaded API config from api_config.yml");
                ensureAuthDefaults(apiConfigFile);
            } else {
                // Use default values
                port = 8080;
                corsEnabled = true;
                rateLimitEnabled = true;
                requestsPerMinute = 60;
                logRequests = true;
                logErrors = true;
                authEnabled = false;
                authToken = "";
                authProtectLogs = false;
                plugin.getLogger().warning("No API config found, using defaults");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load API config, using defaults: " + e.getMessage());
            port = 8080;
            corsEnabled = true;
            rateLimitEnabled = true;
            requestsPerMinute = 60;
            logRequests = true;
            logErrors = true;
            authEnabled = false;
            authToken = "";
            authProtectLogs = false;
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    public void stop() {
        Spark.stop();
        plugin.getLogger().info("FormulaRacing REST API stopped");
    }

    public int getPort() {
        return port;
    }

    /**
     * Adds a log to the log system
     * @param level Log level (info, warning, error)
     * @param message Log message
     */
    public void addLog(String level, String message) {
        JsonObject log = new JsonObject();
        log.addProperty("timestamp", System.currentTimeMillis());
        log.addProperty("time", formatTimeLabel(System.currentTimeMillis(), "dd/MM/yyyy HH:mm:ss"));
        log.addProperty("level", level);
        log.addProperty("message", message);

        synchronized (systemLogs) {
            systemLogs.add(log);
            // Keep only the last MAX_LOGS logs
            if (systemLogs.size() > MAX_LOGS) {
                systemLogs.remove(0);
            }
        }
    }

    /**
     * Adds an info log
     */
    public void logInfo(String message) {
        addLog("info", message);
    }

    /**
     * Adds a warning log
     */
    public void logWarning(String message) {
        addLog("warning", message);
    }

    /**
     * Adds an error log
     */
    public void logError(String message) {
        addLog("error", message);
    }
}
