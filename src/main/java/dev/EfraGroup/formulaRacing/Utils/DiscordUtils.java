package dev.EfraGroup.formulaRacing.Utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class DiscordUtils {
    private static String webhook = "";
    private static String roleMention = "";

    public static void init(String webhookUrl, String roleId) {
        webhook = webhookUrl;
        roleMention = roleId != null && !roleId.isEmpty() ? "<@&" + roleId + ">" : "";
    }

    private static boolean isEnabled() {
        return webhook != null && !webhook.isEmpty();
    }

    public static void sendNewTrackEmbed(JavaPlugin plugin, String trackName, String creator, String description, String imageUrl) {
        if (!isEnabled()) return;
        SchedulerHelper.runAsync(plugin, () -> {
            try {
                URL url = URI.create(webhook).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");

                JsonObject embed = new JsonObject();
                embed.addProperty("title", "🏁 New track added!");
                embed.addProperty("color", 65280); // Green

                JsonArray fields = new JsonArray();
                fields.add(createField("Track", trackName, false));
                fields.add(createField("Criador", creator, false));

                if (description != null && !description.isEmpty()) {
                    fields.add(createField("Grids", description, false));
                }
                embed.add("fields", fields);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", "FormulaRacing Discord");
                embed.add("footer", footer);

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    JsonObject image = new JsonObject();
                    image.addProperty("url", imageUrl);
                    embed.add("image", image);
                    // Opcional: Baixar a imagem localmente
                    downloadImage(plugin, trackName, imageUrl);
                }

                JsonObject payload = new JsonObject();
                JsonArray embedsArray = new JsonArray();
                embedsArray.add(embed);
                payload.add("embeds", embedsArray);
                payload.addProperty("content", roleMention);

                // Mention configuration
                JsonObject allowedMentions = new JsonObject();
                JsonArray parse = new JsonArray();
                parse.add("roles");
                allowedMentions.add("parse", parse);
                payload.add("allowed_mentions", allowedMentions);

                sendPayload(connection, payload);

                int responseCode = connection.getResponseCode();
                if (responseCode >= 300) {
                    logError(plugin, "Failed to send embed. Code: " + responseCode);
                }
            } catch (Exception e) {
                logError(plugin, "Error sending embed: " + e.getMessage());
            }
        });
    }

    public static void sendRecordMessage(JavaPlugin plugin, String firstPlayer, double firstTime, String secondPlayer, double secondTime, String trackName) {
        if (!isEnabled()) return;
        SchedulerHelper.runAsync(plugin, () -> {
            try {
                URL url = URI.create(webhook).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");

                String formattedFirstTime = formatTime(firstTime);
                String message;

                if (secondPlayer == null || secondPlayer.isEmpty() || secondTime <= 0.0) {
                    message = "🏁 **" + firstPlayer + "** set the first record on track **" + trackName + "**!\n⏱️ **Time:** " + formattedFirstTime + " 🏆";
                } else {
                    String formattedOldTime = formatTime(secondTime);
                    if (firstPlayer.equalsIgnoreCase(secondPlayer)) {
                        message = "🏁 **" + firstPlayer + "** just improved their own record on track **" + trackName + "**!\n⏱️ **New Time:** " + formattedFirstTime + " 🏆 (Beat: " + formattedOldTime + ")";
                    } else {
                        message = "🏁 **" + firstPlayer + "** improved the record on track **" + trackName + "**!\n⏱️ **New Time:** " + formattedFirstTime + " 🏆\n👤 **Previous Holder:** " + secondPlayer + " (" + formattedOldTime + ")";
                    }
                }

                JsonObject payload = new JsonObject();
                payload.addProperty("content", message);

                sendPayload(connection, payload);

                int responseCode = connection.getResponseCode();
                if (responseCode >= 300) {
                    logError(plugin, "Failed to send record. Code: " + responseCode);
                }
            } catch (Exception e) {
                logError(plugin, "Error sending record: " + e.getMessage());
            }
        });
    }

    // --- HELPER METHODS ---

    private static JsonObject createField(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    private static String formatTime(double seconds) {
        int min = (int) (seconds / 60);
        double sec = seconds % 60;
        return String.format("%d:%06.3f", min, sec);
    }

    private static void sendPayload(HttpURLConnection conn, JsonObject payload) throws Exception {
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
    }

    private static void logError(JavaPlugin plugin, String msg) {
        if (plugin instanceof FormulaRacing) {
            ((FormulaRacing) plugin).getDebugManager().logRaceSystem("[DiscordUtils] " + msg);
        }
    }

    private static void downloadImage(JavaPlugin plugin, String trackName, String imageUrl) {
        try {
            File folder = new File(plugin.getDataFolder(), "trackimages");
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, trackName.replaceAll("\\s+", "_") + ".png");
            URL url = URI.create(imageUrl).toURL();
            try (InputStream in = url.openStream()) {
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            logError(plugin, "Error downloading image: " + e.getMessage());
        }
    }
}