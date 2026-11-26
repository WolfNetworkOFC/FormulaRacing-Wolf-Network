package dev.EfraGroup.formulaRacing.TimeTrial.Timing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import java.util.List;
import java.util.UUID;

public final class WolfTimingProtocol {

    public static final int VERSION = 1;
    public static final String HELLO = "tt_v1_hello";
    public static final String CAPABILITIES = "tt_v1_capabilities";
    public static final String READY = "tt_v1_ready";
    public static final String PING = "tt_v1_ping";
    public static final String PONG = "tt_v1_pong";
    public static final String GEOMETRY = "tt_v1_geometry";
    public static final String REPORT = "tt_v1_report";
    public static final String RESULT = "tt_v1_result";
    public static final String DISARM = "tt_v1_disarm";
    public static final String TRACK = "tt_v1_track";
    /**
     * Reference lap the client draws its live delta against: the player's best lap that has
     * checkpoint times saved. Sent once per track preparation.
     */
    public static final String COMPARE = "tt_v1_compare";
    /**
     * The player left the track: the client must drop the geometry, the ghost and any run in
     * progress. Distinct from DISARM, which only ends the current lap.
     */
    public static final String TRACK_REMOVE = "tt_v1_track_remove";

    private WolfTimingProtocol() {}

    /**
     * Announces which track the client is being timed on. The geometry payload only carries
     * the world name, so this is how the client learns the track id it needs to key its local
     * personal-best cache and to label a lap it is about to upload.
     */
    public static String track(String trackName) {
        JsonObject json = base();
        json.addProperty("track", trackName == null ? "" : trackName);
        return json.toString();
    }

    /** Empty payload announcing that the client should unload the track. */
    public static String trackRemove() {
        JsonObject json = base();
        json.addProperty("remove", true);
        return json.toString();
    }

    public static String hello(int maxDeltaMillis, int maxRttMillis) {
        JsonObject json = base();
        json.addProperty("maxDeltaMs", maxDeltaMillis);
        json.addProperty("maxRttMs", maxRttMillis);
        json.add("serverCapabilities", capabilities(
            "TT_CLIENT_REPORT_V1",
            "TT_GEOMETRY_AABB_V1",
            "TT_GEOMETRY_POLY_V1"
        ));
        return json.toString();
    }

    public static String ready(int maxDeltaMillis) {
        JsonObject json = base();
        json.addProperty("enabled", true);
        json.addProperty("maxDeltaMs", maxDeltaMillis);
        json.addProperty("ready", true);
        return json.toString();
    }

    public static String geometry(
        UUID runId,
        String worldName,
        List<DatabaseManager.RegionData> startRegions,
        List<DatabaseManager.RegionData> endRegions,
        List<DatabaseManager.RegionData> guardRegions,
        List<DatabaseManager.RegionData> checkpointRegions
    ) {
        JsonObject json = base();
        json.addProperty("runId", runId.toString());
        json.addProperty("world", worldName == null ? "" : worldName);
        json.addProperty("geometryId", runId.toString());
        json.addProperty("version", VERSION);
        json.addProperty("ready", true);
        JsonArray regions = new JsonArray();
        if (startRegions != null) {
            int index = 0;
            for (DatabaseManager.RegionData region : startRegions) {
                if (region != null) {
                    regions.add(region("start-" + index++, region));
                }
            }
        }
        if (endRegions != null) {
            int index = 0;
            for (DatabaseManager.RegionData region : endRegions) {
                if (region != null) {
                    regions.add(region("end-" + index++, region));
                }
            }
        }
        // LAGSTART/LAGEND are sent with role "guard". They are not timing boundaries: the
        // server is the one that decides whether a finish is coherent, and the client only
        // uses them to keep the HUD honest about what is coming.
        if (guardRegions != null) {
            int index = 0;
            for (DatabaseManager.RegionData region : guardRegions) {
                if (region != null) {
                    regions.add(region("guard-" + index++, region));
                }
            }
        }
        // Checkpoints carry their DB id as `index`: the client only accepts them in that
        // order and will not close a lap before all of them were crossed.
        if (checkpointRegions != null) {
            int index = 0;
            for (DatabaseManager.RegionData region : checkpointRegions) {
                if (region != null) {
                    regions.add(region("checkpoint-" + index++, region, region.getId()));
                }
            }
        }
        json.add("regions", regions);
        return json.toString();
    }

    /**
     * Reference lap for the live delta. {@code checkpointSeconds} comes straight from
     * {@code fr_checkpoint_times} (seconds since the start); the client works in millis.
     */
    public static String compare(long lapMillis, java.util.Map<Integer, Double> checkpointSeconds) {
        JsonObject json = base();
        json.addProperty("lapMillis", Math.max(0L, lapMillis));
        JsonArray checkpoints = new JsonArray();
        if (checkpointSeconds != null) {
            checkpointSeconds.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    JsonObject checkpoint = new JsonObject();
                    checkpoint.addProperty("id", entry.getKey());
                    checkpoint.addProperty(
                        "millis",
                        Math.round(entry.getValue() * 1000.0)
                    );
                    checkpoints.add(checkpoint);
                });
        }
        json.add("checkpoints", checkpoints);
        return json.toString();
    }

    public static String pong(long sequence) {
        JsonObject json = base();
        json.addProperty("sequence", sequence);
        return json.toString();
    }

    public static String result(
        UUID runId,
        OfficialTime officialTime,
        boolean clientTimingAccepted
    ) {
        JsonObject json = base();
        json.addProperty("runId", runId.toString());
        json.addProperty("accepted", clientTimingAccepted);
        json.addProperty("officialTicks", officialTime.officialTicks());
        json.addProperty("officialMillis", officialTime.getOfficialMillis());
        json.addProperty("displayMillis", officialTime.displayMillis());
        json.addProperty("source", officialTime.source());
        return json.toString();
    }

    public static String disarm(UUID runId) {
        JsonObject json = base();
        json.addProperty("runId", runId.toString());
        return json.toString();
    }

    public static JsonObject parseObject(String value) {
        if (value == null || value.isBlank() || value.length() > 32767) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(value);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            return object.has("protocol") && object.get("protocol").getAsInt() == VERSION
                ? object
                : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static JsonObject base() {
        JsonObject json = new JsonObject();
        json.addProperty("protocol", VERSION);
        return json;
    }

    private static JsonArray capabilities(String... values) {
        JsonArray capabilities = new JsonArray();
        for (String value : values) {
            capabilities.add(value);
        }
        return capabilities;
    }

    private static JsonObject region(
        String role,
        DatabaseManager.RegionData region
    ) {
        return region(role, region, -1);
    }

    private static JsonObject region(
        String role,
        DatabaseManager.RegionData region,
        int index
    ) {
        JsonObject json = new JsonObject();
        // "guard" is a non-timing region: the server validates the crossing, the client only
        // draws it. Older clients ignore unknown roles, so this stays additive.
        if (role.startsWith("guard")) {
            json.addProperty("role", "GUARD");
        } else if (role.startsWith("checkpoint")) {
            json.addProperty("role", "CHECKPOINT");
        } else {
            json.addProperty("role", role.startsWith("start") ? "START" : "END");
        }
        json.addProperty("id", role);
        if (index >= 0) {
            json.addProperty("index", index);
        }
        json.addProperty("shape", region.isPoly() ? "POLY" : "AABB");
        json.addProperty("minX", Math.min(region.getMinX(), region.getMaxX()));
        json.addProperty("minY", Math.min(region.getMinY(), region.getMaxY()));
        json.addProperty("minZ", Math.min(region.getMinZ(), region.getMaxZ()));
        json.addProperty("maxX", Math.max(region.getMinX(), region.getMaxX()));
        json.addProperty("maxY", Math.max(region.getMinY(), region.getMaxY()));
        json.addProperty("maxZ", Math.max(region.getMinZ(), region.getMaxZ()));
        if (region.isPoly()) {
            JsonArray points = new JsonArray();
            double[][] polygon = region.getPolyPoints();
            if (polygon != null) {
                for (double[] point : polygon) {
                    if (point == null || point.length < 2) {
                        continue;
                    }
                    JsonArray coordinates = new JsonArray();
                    coordinates.add(point[0]);
                    coordinates.add(point[1]);
                    points.add(coordinates);
                }
            }
            json.add("points", points);
        }
        return json;
    }
}
