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

    private WolfTimingProtocol() {}

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
        List<DatabaseManager.RegionData> endRegions
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
        json.add("regions", regions);
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
        JsonObject json = new JsonObject();
        json.addProperty("role", role.startsWith("start") ? "START" : "END");
        json.addProperty("id", role);
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
