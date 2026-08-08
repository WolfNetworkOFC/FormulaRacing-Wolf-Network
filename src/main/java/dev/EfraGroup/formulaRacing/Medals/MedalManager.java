package dev.EfraGroup.formulaRacing.Medals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Ghost.GhostFrame;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de medalhas por pista.
 * <p>
 * - {@code /te medals <medal> <tempo> [pista]} guarda o tempo de cada medalha na
 *   database ({@code fr_track_medals}) e reescreve o ficheiro JSON da pista.
 * - {@code /te medals record <medal> [pista]} arma a gravação: a PRÓXIMA volta
 *   que o jogador fizer na pista fica registada (tempo + linha/ghost) nessa medalha.
 * <p>
 * Ficheiro JSON: {@code medals/<TrackNameWS>_medals.json} no formato:
 * <pre>
 * {
 *   "medals": {
 *     "gold":     { "time_seconds": 83.2, "formatted": "1:23.200" },
 *     "diamond":  { "time_seconds": 79.0, "formatted": "1:19.000",
 *                   "ghost_path": [ { "x": .., "y": .., "z": .. } ] }
 *   }
 * }
 * </pre>
 * Escrita do ficheiro é assíncrona (Folia-safe).
 */
public class MedalManager {

    /** Ordem canónica das medalhas (da mais rápida para a mais lenta). */
    public static final List<String> MEDAL_TYPES =
            List.of("saphira", "netherite", "diamond", "gold", "silver", "bronze");

    private final FormulaRacing plugin;
    private final DatabaseManager db;
    private final Gson gson;
    private final File medalsFolder;

    // Jogador que está a aguardar a gravação da próxima volta -> medalha + pista
    private final Map<UUID, PendingRecord> pendingRecords = new ConcurrentHashMap<>();

    // Cache em memória das linhas/ghost das medalhas (trackWS -> medal -> linha)
    private final Map<String, Map<String, MedalLine>> medalLineCache = new ConcurrentHashMap<>();

    // Cache dos tempos das medalhas por pista (trackWS -> medal -> tempo) p/ evitar query por volta
    private final Map<String, Map<String, Double>> medalTimesCache = new ConcurrentHashMap<>();

    // Anti-spam do broadcast de conquista (uuid:trackWS:medal)
    private final Set<String> achievementAnnounced = ConcurrentHashMap.newKeySet();

    private static class PendingRecord {
        final String medalType;
        final String trackNameWS;

        PendingRecord(String medalType, String trackNameWS) {
            this.medalType = medalType;
            this.trackNameWS = trackNameWS;
        }
    }

    /** Uma medalha da pista que tem linha gravada (ghost_path). */
    public static class MedalLine {
        public final String medalType;
        public final double timeSeconds;
        public final List<GhostFrame> frames;

        MedalLine(String medalType, double timeSeconds, List<GhostFrame> frames) {
            this.medalType = medalType;
            this.timeSeconds = timeSeconds;
            this.frames = frames;
        }
    }

    public MedalManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.medalsFolder = new File(plugin.getDataFolder(), "medals");
        if (!medalsFolder.exists()) {
            medalsFolder.mkdirs();
        }
    }

    // ========================
    //  /te medals <medal> <tempo> [pista]
    // ========================

    /**
     * Define o tempo de uma medalha na database e reescreve o JSON da pista.
     *
     * @return true se salvou na database com sucesso
     */
    public boolean setMedalTime(String trackName, String medalType, double timeSeconds) {
        String trackWS = norm(trackName);
        boolean saved = db.setTrackMedalTime(trackWS, medalType, timeSeconds);
        if (saved) {
            saveJsonAsync(trackWS, null, 0.0, null);
        }
        return saved;
    }

    // ========================
    //  /te medals record <medal> [pista]
    // ========================

    /**
     * Arma a gravação: a próxima volta completada na pista fica associada à medalha
     * (tempo + linha/ghost). Garante que a gravação de ghost está ativa para o jogador.
     */
    public void armRecord(Player player, String medalType, String trackName) {
        String trackWS = norm(trackName);
        pendingRecords.put(player.getUniqueId(), new PendingRecord(medalType, trackWS));
        if (plugin.getGhostManager() != null) {
            plugin.getGhostManager().startRecording(player);
        }
        plugin.getDebugManager().logTimeTrialSystem(
                "[MEDALS] Record armado para " + player.getName() + " -> " + medalType + " na pista " + trackWS);
    }

    /**
     * Chamado quando uma volta é completada (TT finish). Se o jogador tiver um
     * record pendente para esta pista, salva o tempo + ghost nessa medalha.
     */
    public void handleLapFinish(Player player, String trackNameWS, double timeSeconds, List<GhostFrame> frames) {
        if (player == null || trackNameWS == null) return;
        final String trackWS = norm(trackNameWS);
        PendingRecord pending = pendingRecords.get(player.getUniqueId());
        if (pending == null) return;
        if (!pending.trackNameWS.equalsIgnoreCase(trackWS)) return; // aguarda a pista certa

        pendingRecords.remove(player.getUniqueId());
        final String medalType = pending.medalType;

        SchedulerHelper.runAsync(plugin, () -> {
            boolean saved = db.setTrackMedalTime(trackWS, medalType, timeSeconds);
            saveJsonAsync(trackWS, medalType, timeSeconds, frames);
            SchedulerHelper.runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (saved) {
                    player.sendMessage("§a✅ Medalha §e" + medalType.toUpperCase()
                            + "§a gravada em §e" + trackWS + "§a — §e" + formatTime(timeSeconds)
                            + (frames != null && !frames.isEmpty() ? " §7(linha salva)" : ""));
                } else {
                    player.sendMessage("§c❌ Falha ao gravar a medalha " + medalType + " na database.");
                }
            });
        });
    }

    // ========================
    //  LINHAS DAS MEDALHAS (ghost_path) — para o replay colorido
    // ========================

    /**
     * Lê as medalhas com linha gravada do JSON (com cache em memória).
     * Chamar dentro de um contexto async (lê disco).
     */
    Map<String, MedalLine> loadMedalLines(String trackWS) {
        trackWS = norm(trackWS);
        Map<String, MedalLine> cached = medalLineCache.get(trackWS);
        if (cached != null) return cached;

        Map<String, MedalLine> lines = new LinkedHashMap<>();
        File file = new File(medalsFolder, safeName(trackWS) + "_medals.json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject medals = root.has("medals") ? root.getAsJsonObject("medals") : new JsonObject();
                for (String medal : medals.keySet()) {
                    JsonObject entry = medals.getAsJsonObject(medal);
                    if (entry == null || !entry.has("ghost_path")) continue;
                    double time = entry.has("time_seconds") ? entry.get("time_seconds").getAsDouble() : 0;
                    JsonArray path = entry.getAsJsonArray("ghost_path");
                    List<GhostFrame> frames = new ArrayList<>();
                    for (JsonElement e : path) {
                        JsonObject p = e.getAsJsonObject();
                        frames.add(new GhostFrame(
                                p.get("x").getAsDouble(),
                                p.get("y").getAsDouble(),
                                p.get("z").getAsDouble()));
                    }
                    if (!frames.isEmpty()) {
                        lines.put(medal, new MedalLine(medal, time, frames));
                    }
                }
            } catch (Exception e) {
                plugin.getDebugManager().logTimeTrialSystem(
                        "[MEDALS] Erro ao ler linhas de " + trackWS + ": " + e.getMessage());
            }
        }
        medalLineCache.put(trackWS, lines);
        return lines;
    }

    /**
     * Escolhe a linha de medalha a mostrar para um jogador:
     * medalhas com linha gravada que sejam mais rápidas que o PB do jogador
     * (time < pb), e entre essas a mais próxima do tempo dele (a mais lenta das
     * rápidas = maior time abaixo do pb).
     */
    MedalLine selectBestLine(String trackWS, double playerPbSeconds) {
        Map<String, MedalLine> lines = loadMedalLines(trackWS);
        MedalLine best = null;
        for (MedalLine line : lines.values()) {
            if (line.timeSeconds <= 0 || line.frames.isEmpty()) continue;
            if (line.timeSeconds >= playerPbSeconds) continue; // só se for melhor que o PB
            if (best == null || line.timeSeconds > best.timeSeconds) {
                best = line; // maior tempo abaixo do pb = mais próxima do jogador
            }
        }
        return best;
    }

    /** Cor (dust) por tipo de medalha. */
    public static Particle.DustOptions dustFor(String medalType) {
        return switch (medalType.toLowerCase()) {
            case "diamond" -> new Particle.DustOptions(Color.fromRGB(0, 170, 255), 1.0F);   // azul
            case "saphira" -> new Particle.DustOptions(Color.fromRGB(170, 60, 255), 1.0F);  // roxa
            case "netherite" -> new Particle.DustOptions(Color.fromRGB(50, 50, 50), 1.0F);  // escura
            case "gold" -> new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.0F);     // dourada
            case "silver" -> new Particle.DustOptions(Color.fromRGB(200, 205, 210), 1.0F);  // prateada
            case "bronze" -> new Particle.DustOptions(Color.fromRGB(200, 120, 60), 1.0F);   // bronze
            default -> new Particle.DustOptions(Color.WHITE, 1.0F);
        };
    }

    /**
     * Inicia o replay da linha de medalha certa para o jogador (async, Folia-safe).
     * Só mostra se houver medalha com linha mais rápida que o PB dele.
     */
    public void startMedalReplayIfBetter(Player player, String trackWS) {
        if (player == null || trackWS == null) return;
        final String trackWSFinal = norm(trackWS);
        // Guard: se a pista não tem medalhas com linha, nem agenda a task async
        Map<String, MedalLine> cachedLines = medalLineCache.get(trackWSFinal);
        if (cachedLines != null && cachedLines.isEmpty()) return;
        SchedulerHelper.runAsync(plugin, () -> {
            Object[] pb = db.getPlayerBestTime(player.getName(), trackWSFinal);
            double pbTime = pb != null && pb[0] != null ? (Double) pb[0] : Double.MAX_VALUE;
            MedalLine line = selectBestLine(trackWSFinal, pbTime);
            if (line == null) return;
            SchedulerHelper.runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (plugin.getGhostManager() != null) {
                    plugin.getGhostManager().startReplay(
                            player, line.frames, Particle.DUST, dustFor(line.medalType));
                }
            });
        });
    }

    /**
     * Quando um jogador completa uma volta, verifica quais medalhas ele ainda não
     * possui mas cujo tempo foi batido. Concede todas as medalhas elegíveis
     * (armazenadas em fr_player_medals) e anuncia no chat a mais valiosa
     * conquistada nesta volta (amarelo, uma linha, uma vez por sessão).
     *
     * Importante: a conquista funciona mesmo que o tempo não seja um novo PB —
     * basta o jogador ter batido o tempo de uma medalha que ainda não tem.
     */
    public void checkMedalAchievement(Player player, String trackWS, double lapSeconds) {
        if (player == null || trackWS == null || lapSeconds <= 0) return;
        final String trackWSFinal = norm(trackWS);
        final UUID uuid = player.getUniqueId();
        final double lapFinal = lapSeconds;
        // Guard: se já sabemos que a pista não tem medalhas, nem agenda task
        Map<String, Double> cachedTimes = medalTimesCache.get(trackWSFinal);
        if (cachedTimes != null && cachedTimes.isEmpty()) return;

        SchedulerHelper.runAsync(plugin, () -> {
            Map<String, Double> times = medalTimesCache.computeIfAbsent(trackWSFinal,
                    k -> db.getAllTrackMedalTimes(trackWSFinal));
            if (times.isEmpty()) return;

            // Percorre da mais valiosa para a menos valiosa (já ordenado em MEDAL_TYPES)
            String bestNewMedal = null;
            List<String> newMedals = new ArrayList<>();
            for (String medalType : MEDAL_TYPES) {
                Double medalTime = times.get(medalType);
                if (medalTime == null || medalTime <= 0) continue;
                if (lapFinal > medalTime) continue; // tempo não bateu o threshold desta medalha
                if (db.playerHasMedal(uuid, trackWSFinal, medalType)) continue; // já tem

                // Concede a medalha
                db.grantMedal(uuid, trackWSFinal, medalType, lapFinal);
                newMedals.add(medalType);
                if (bestNewMedal == null) {
                    bestNewMedal = medalType; // a primeira (mais valiosa) é a que anuncia
                }
            }

            final String medal = bestNewMedal;
            if (medal != null && achievementAnnounced.add(uuid + ":" + trackWSFinal + ":" + medal)) {
                final String displayMedals = newMedals.size() > 1
                        ? ChatColor.YELLOW + " e " + (newMedals.size() - 1) + " outras"
                        : "";
                SchedulerHelper.runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.sendMessage(ChatColor.YELLOW + "✨ "
                            + "conseguiu a medalha " + medal.toUpperCase()
                            + " na pista " + trackWSFinal + "!" + displayMedals);
                    Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName()
                            + " conseguiu a medalha " + medal.toUpperCase() + " na pista "
                            + trackWSFinal + "!");
                });
            }
        });
    }

    // ========================
    //  JSON <TrackNameWS>_medals.json
    // ========================

    /**
     * Reescreve o ficheiro JSON da pista, juntando os tempos da database com as
     * linhas/ghost existentes (e, opcionalmente, uma nova linha vinda do record).
     */
    private void saveJsonAsync(String trackWS, String newMedal, double newTime, List<GhostFrame> newFrames) {
        SchedulerHelper.runAsync(plugin, () -> {
            try {
                File file = new File(medalsFolder, safeName(trackWS) + "_medals.json");
                JsonObject root;
                if (file.exists()) {
                    try (FileReader reader = new FileReader(file)) {
                        root = JsonParser.parseReader(reader).getAsJsonObject();
                    } catch (Exception ignored) {
                        root = new JsonObject();
                    }
                } else {
                    root = new JsonObject();
                }

                JsonObject medals = root.has("medals") ? root.getAsJsonObject("medals") : new JsonObject();
                root.add("medals", medals);

                // 1. Merge dos tempos da database (fonte da verdade)
                Map<String, Double> times = db.getAllTrackMedalTimes(trackWS);
                for (Map.Entry<String, Double> e : times.entrySet()) {
                    JsonObject entry = medals.has(e.getKey())
                            ? medals.getAsJsonObject(e.getKey())
                            : new JsonObject();
                    entry.addProperty("time_seconds", e.getValue());
                    entry.addProperty("formatted", formatTime(e.getValue()));
                    medals.add(e.getKey(), entry);
                }

                // 2. Aplica a nova linha do record (se houver)
                if (newMedal != null && newFrames != null && !newFrames.isEmpty()) {
                    JsonObject entry = medals.has(newMedal)
                            ? medals.getAsJsonObject(newMedal)
                            : new JsonObject();
                    entry.addProperty("time_seconds", newTime);
                    entry.addProperty("formatted", formatTime(newTime));
                    JsonArray path = new JsonArray();
                    for (GhostFrame f : newFrames) {
                        JsonObject p = new JsonObject();
                        p.addProperty("x", f.getX());
                        p.addProperty("y", f.getY());
                        p.addProperty("z", f.getZ());
                        path.add(p);
                    }
                    entry.add("ghost_path", path);
                    medals.add(newMedal, entry);
                }

                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(root, writer);
                }
                medalLineCache.remove(trackWS); // invalida cache de linhas
                medalTimesCache.remove(trackWS); // invalida cache de tempos
                plugin.getDebugManager().logTimeTrialSystem(
                        "[MEDALS] JSON atualizado: " + file.getName());
            } catch (Exception e) {
                plugin.getDebugManager().logTimeTrialSystem(
                        "[MEDALS] Erro ao salvar JSON: " + e.getMessage());
            }
        });
    }

    // ========================
    //  Helpers estáticos
    // ========================

    /**
     * Converte "1:32.434" ou "92.434" em segundos.
     */
    public static double parseTimeToSeconds(String input) throws NumberFormatException {
        String s = input.trim();
        if (s.contains(":")) {
            String[] parts = s.split(":");
            double minutes = Double.parseDouble(parts[0].trim());
            double seconds = Double.parseDouble(parts[1].trim());
            return minutes * 60.0 + seconds;
        }
        return Double.parseDouble(s);
    }

    /**
     * Formata segundos como "1:23.200" (ou "23.200" quando menos de um minuto).
     */
    public static String formatTime(double seconds) {
        long totalMillis = Math.round(seconds * 1000.0);
        long minutes = totalMillis / 60000L;
        long secs = (totalMillis % 60000L) / 1000L;
        long millis = totalMillis % 1000L;
        return minutes > 0
                ? String.format("%d:%02d.%03d", minutes, secs, millis)
                : String.format("%d.%03d", secs, millis);
    }

    /** Normaliza o nome da medalha (aceita "sapphire" como "saphira"). */
    public static String normalizeMedal(String medalType) {
        String m = medalType.toLowerCase();
        if (m.equals("sapphire")) return "saphira";
        return m;
    }

    /** Normaliza trackWS: minúsculas + sem espaços (consistente com o resto do plugin). */
    private static String norm(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase();
    }

    private static String safeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Remove um record pendente (ex.: quando o jogador sai do servidor).
     */
    public void clearPending(UUID uuid) {
        if (uuid != null) {
            pendingRecords.remove(uuid);
            achievementAnnounced.removeIf(k -> k.startsWith(uuid + ":"));
        }
    }
}
