package dev.EfraGroup.formulaRacing.Heat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File-only storage for gimmicks. One JSON file per gimmick inside
 * {@code plugins/FormulaRacing/gimmicks/}, named
 * {@code <Nome>-<Pista>.json} (ex: {@code Batata-SpawnTrack.json}).
 *
 * <p>Each file holds the gimmick definition plus its heat schedules
 * ({@code heatId -> triggerLap}).
 */
public final class GimmickStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File folder;

    public GimmickStore(File dataFolder) {
        this.folder = new File(dataFolder, "gimmicks");
        if (!this.folder.exists()) {
            this.folder.mkdirs();
        }
    }

    public File getFolder() {
        return folder;
    }

    /** File name for a gimmick: {@code <Nome>-<Pista>.json}. */
    public static String fileName(String name, String trackNameWS) {
        return sanitize(name) + "-" + sanitize(trackNameWS) + ".json";
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "unnamed";
        return raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public File fileOf(String name, String trackNameWS) {
        return new File(folder, fileName(name, trackNameWS));
    }

    /** All stored gimmicks, loaded from disk. */
    public synchronized List<GimmickConfig> loadAll() {
        List<GimmickConfig> result = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return result;
        for (File file : files) {
            try {
                GimmickConfig gimmick = read(file);
                if (gimmick != null) result.add(gimmick);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /** heatId -> triggerLap schedules stored in the gimmick file. */
    public synchronized Map<Integer, Integer> loadSchedules(GimmickConfig gimmick) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        File file = resolveFile(gimmick);
        if (file == null || !file.exists()) return result;
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject schedules = root.has("schedules") ? root.getAsJsonObject("schedules") : null;
            if (schedules == null) return result;
            for (Map.Entry<String, JsonElement> entry : schedules.entrySet()) {
                try {
                    result.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsInt());
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /** Persists the definition, keeping existing schedules. */
    public synchronized void save(GimmickConfig gimmick) {
        Map<Integer, Integer> schedules = loadSchedules(gimmick);
        write(gimmick, schedules);
    }

    /** Adds or updates the schedule of a heat. */
    public synchronized void saveSchedule(GimmickConfig gimmick, int heatId, int triggerLap) {
        Map<Integer, Integer> schedules = loadSchedules(gimmick);
        schedules.put(heatId, triggerLap);
        write(gimmick, schedules);
    }

    /** Removes the schedule of a heat. Returns true when something was removed. */
    public synchronized boolean removeSchedule(GimmickConfig gimmick, int heatId) {
        Map<Integer, Integer> schedules = loadSchedules(gimmick);
        if (schedules.remove(heatId) == null) return false;
        write(gimmick, schedules);
        return true;
    }

    /** Removes every schedule of a heat across all gimmicks. */
    public synchronized void removeAllSchedules(int heatId) {
        for (GimmickConfig gimmick : loadAll()) {
            removeSchedule(gimmick, heatId);
        }
    }

    /** Removes every schedule pointing at a gimmick (used when deleting it). */
    public synchronized void removeGimmickEverywhere(GimmickConfig gimmick) {
        File file = resolveFile(gimmick);
        if (file != null && file.exists() && !file.delete()) {
            throw new GimmickException("Não foi possível apagar o arquivo " + file.getName());
        }
    }

    /**
     * Renames the file when the display name changed but the file name does not
     * match anymore (names are case-insensitive lookups, files keep the case).
     */
    public synchronized void renameIfNeeded(GimmickConfig gimmick, String oldName, String oldTrack) {
        File oldFile = fileOf(oldName, oldTrack);
        File newFile = resolveFile(gimmick);
        if (newFile == null) return;
        if (oldFile.exists() && !oldFile.equals(newFile)) {
            oldFile.renameTo(newFile);
        }
    }

    private File resolveFile(GimmickConfig gimmick) {
        if (gimmick == null) return null;
        // Exact match first.
        File exact = fileOf(gimmick.getName(), gimmick.getTrackNameWS());
        if (exact.exists()) return exact;
        // Case-insensitive fallback: find the existing file.
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return exact;
        String wanted = fileName(gimmick.getName(), gimmick.getTrackNameWS());
        for (File file : files) {
            if (file.getName().equalsIgnoreCase(wanted)) return file;
        }
        return exact;
    }

    private GimmickConfig read(File file) throws IOException {
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            GimmickConfig gimmick = new GimmickConfig();
            gimmick.setName(getString(root, "name"));
            gimmick.setTrackNameWS(getString(root, "track"));
            gimmick.setWorldName(getString(root, "world"));
            if (root.has("x")) gimmick.setCoordinates(
                root.get("x").getAsDouble(),
                root.get("y").getAsDouble(),
                root.get("z").getAsDouble()
            );
            gimmick.setPasteWithAir(root.has("pasteWithAir") && root.get("pasteWithAir").getAsBoolean());
            gimmick.setAnnounceMessage(getString(root, "announceMessage"));
            gimmick.setEnabled(!root.has("enabled") || root.get("enabled").getAsBoolean());
            gimmick.setCreatedBy(getString(root, "createdBy"));
            gimmick.setCreatedAt(root.has("createdAt") ? root.get("createdAt").getAsLong() : 0L);
            if (gimmick.getName() == null || gimmick.getTrackNameWS() == null) return null;
            return gimmick;
        }
    }

    private void write(GimmickConfig gimmick, Map<Integer, Integer> schedules) {
        File file = fileOf(gimmick.getName(), gimmick.getTrackNameWS());
        // If a case-different file exists, reuse it instead of duplicating.
        File existing = resolveFile(gimmick);
        if (existing != null && existing.exists()) file = existing;
        JsonObject root = new JsonObject();
        root.addProperty("name", gimmick.getName());
        root.addProperty("track", gimmick.getTrackNameWS());
        root.addProperty("world", gimmick.getWorldName());
        root.addProperty("x", gimmick.getX());
        root.addProperty("y", gimmick.getY());
        root.addProperty("z", gimmick.getZ());
        root.addProperty("pasteWithAir", gimmick.isPasteWithAir());
        if (gimmick.getAnnounceMessage() != null) root.addProperty("announceMessage", gimmick.getAnnounceMessage());
        root.addProperty("enabled", gimmick.isEnabled());
        if (gimmick.getCreatedBy() != null) root.addProperty("createdBy", gimmick.getCreatedBy());
        root.addProperty("createdAt", gimmick.getCreatedAt());
        JsonObject sched = new JsonObject();
        List<Integer> heatIds = new ArrayList<>(schedules.keySet());
        heatIds.sort(Integer::compareTo);
        for (Integer heatId : heatIds) {
            sched.addProperty(String.valueOf(heatId), schedules.get(heatId));
        }
        root.add("schedules", sched);
        // Keep a schedules array too for readability (object above is authoritative).
        JsonArray arr = new JsonArray();
        for (Integer heatId : heatIds) {
            JsonObject row = new JsonObject();
            row.addProperty("heatId", heatId);
            row.addProperty("triggerLap", schedules.get(heatId));
            arr.add(row);
        }
        root.add("scheduleList", arr);
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            throw new GimmickException("Erro ao salvar o arquivo da gimmick: " + e.getMessage(), e);
        }
    }

    private static String getString(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) return null;
        try {
            return root.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }
}
