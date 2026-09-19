package dev.EfraGroup.formulaRacing.Heat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * On-disk storage of the gimmick system. Two file kinds live inside
 * {@code plugins/FormulaRacing/gimmicks/<pista>/}:
 *
 * <ul>
 *   <li>{@code <nome>.gimmick} — the build the admin saved, stored as offsets
 *       relative to the paste anchor, so the same file pastes anywhere;</li>
 *   <li>{@code <nome>.<heatId>.backup} — what the world had before a paste, as
 *       absolute blocks plus the world name. It is written <b>before</b> the paste,
 *       so a crash mid-race can still be undone on the next start.</li>
 * </ul>
 *
 * <p>Both are gzip text, so they are cheap to read off the main thread and stay
 * human-inspectable with {@code gunzip -c}.</p>
 */
public final class GimmickFile {

    private static final String HEADER = "FR1";

    private GimmickFile() {
    }

    /** A single block: coordinates (relative for a gimmick, absolute for a backup) + block data string. */
    public record Entry(int x, int y, int z, String data) {
    }

    /** A backup file: which world it belongs to and the blocks that must be put back. */
    public record Backup(String worldName, List<Entry> blocks) {
    }

    /** Folder of a track, e.g. {@code plugins/FormulaRacing/gimmicks/spawntrack}. */
    public static File trackFolder(File root, String trackNameWS) {
        return new File(root, sanitize(trackNameWS));
    }

    /** The saved build of a gimmick. */
    public static File gimmickFile(File root, String trackNameWS, String gimmickName) {
        return new File(trackFolder(root, trackNameWS), sanitize(gimmickName) + ".gimmick");
    }

    /**
     * Where the world state before a paste is kept. One file per heat: a gimmick can be
     * scheduled only once per heat, so the name cannot collide there.
     */
    public static File backupFile(File root, String trackNameWS, String gimmickName, int heatId) {
        String suffix = heatId > 0 ? String.valueOf(heatId) : "manual";
        return new File(
            trackFolder(root, trackNameWS),
            sanitize(gimmickName) + "." + suffix + ".backup"
        );
    }

    /** Keeps the name readable but safe to use as a file name. */
    static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "unnamed";
        return raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase(java.util.Locale.ROOT);
    }

    public static void writeGimmick(File file, List<Entry> blocks) throws IOException {
        write(file, null, blocks);
    }

    public static void writeBackup(File file, String worldName, List<Entry> blocks) throws IOException {
        write(file, worldName, blocks);
    }

    public static List<Entry> readGimmick(File file) throws IOException {
        return read(file).blocks();
    }

    public static Backup readBackup(File file) throws IOException {
        return read(file);
    }

    private static void write(File file, String worldName, List<Entry> blocks) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta " + parent.getPath());
        }

        try (
            Writer writer = new BufferedWriter(
                new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(file)),
                    java.nio.charset.StandardCharsets.UTF_8
                )
            )
        ) {
            writer.write(HEADER + "\n");
            if (worldName != null) {
                writer.write("WORLD|" + worldName + "\n");
            }
            writer.write("BLOCKS|" + blocks.size() + "\n");
            for (Entry entry : blocks) {
                writer.write(entry.x() + " " + entry.y() + " " + entry.z() + " " + entry.data() + "\n");
            }
        }
    }

    private static Backup read(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("Arquivo não encontrado: " + (file == null ? "?" : file.getName()));
        }

        String worldName = null;
        List<Entry> blocks = new ArrayList<>();

        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    new GZIPInputStream(new FileInputStream(file)),
                    java.nio.charset.StandardCharsets.UTF_8
                )
            )
        ) {
            String header = reader.readLine();
            if (header == null || !header.startsWith(HEADER)) {
                throw new IOException("Formato inválido em " + file.getName());
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (line.startsWith("WORLD|")) {
                    worldName = line.substring("WORLD|".length());
                    continue;
                }
                if (line.startsWith("BLOCKS|")) {
                    continue; // count is informative only
                }

                // "<x> <y> <z> <block data>" — the data never contains a space.
                String[] parts = line.split(" ", 4);
                if (parts.length < 4) continue;
                try {
                    blocks.add(
                        new Entry(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]),
                            parts[3]
                        )
                    );
                } catch (NumberFormatException ignored) {
                    // corrupted line: skip instead of losing the whole file
                }
            }
        }

        return new Backup(worldName, blocks);
    }

    /** Every leftover backup file under the gimmicks folder (used for crash recovery). */
    public static List<File> findBackups(File rootFolder) {
        List<File> found = new ArrayList<>();
        collect(rootFolder, found);
        return found;
    }

    private static void collect(File folder, List<File> found) {
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, found);
            } else if (child.getName().endsWith(".backup")) {
                found.add(child);
            }
        }
    }
}
