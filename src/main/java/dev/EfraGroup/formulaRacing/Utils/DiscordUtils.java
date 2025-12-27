package dev.EfraGroup.formulaRacing.Utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class DiscordUtils {

    private static final String WEBHOOK = "https://ptb.discord.com/api/webhooks/1420179966489919560/bijNlYQsQK9H9u50Yk2vWxTgHztrgdPf83FGfCbivc0oa89uUA3SFdsprKZv_kBS949o";
    private static final String ROLE_MENTION = "<@&1403835295153000560>"; // substitua pelo ID real do cargo 🔔┃Notificar Avisos

    /**
     * Envia um embed para o Discord no estilo "New track release" e menciona um cargo oculto no final
     *
     * @param plugin      Instância do plugin (para pegar o dataFolder)
     * @param trackName   Nome da pista
     * @param creator     Nome do criador
     * @param description Texto adicional ou grids (pode ser null)
     * @param imageUrl    URL da imagem opcional (pode ser null)
     */
    public static void sendNewTrackEmbed(JavaPlugin plugin, String trackName, String creator, String description, String imageUrl) {
        try {
            URL url = new URL(WEBHOOK);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            // ================= Embed =================
            JsonObject embed = new JsonObject();
            embed.addProperty("title", "🏁 Nova pista adicionada!");
            embed.addProperty("color", 0x00FF00); // Verde

            JsonArray fields = new JsonArray();

            JsonObject trackField = new JsonObject();
            trackField.addProperty("name", "Track");
            trackField.addProperty("value", trackName);
            trackField.addProperty("inline", false);
            fields.add(trackField);

            JsonObject creatorField = new JsonObject();
            creatorField.addProperty("name", "Criador");
            creatorField.addProperty("value", creator);
            creatorField.addProperty("inline", false);
            fields.add(creatorField);

            if (description != null && !description.isEmpty()) {
                JsonObject descField = new JsonObject();
                descField.addProperty("name", "Grids");
                descField.addProperty("value", description);
                descField.addProperty("inline", false);
                fields.add(descField);
            }

            embed.add("fields", fields);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "FormulaRacing Discord");
            embed.add("footer", footer);

            if (imageUrl != null && !imageUrl.isEmpty()) {
                JsonObject image = new JsonObject();
                image.addProperty("url", imageUrl); // URL externa
                embed.add("image", image);
            }

            JsonArray embedsArray = new JsonArray();
            embedsArray.add(embed);

            // ================= Payload =================
            JsonObject payload = new JsonObject();
            payload.add("embeds", embedsArray);

            // Mencionar cargo corretamente
            payload.addProperty("content", ROLE_MENTION);
            JsonObject allowedMentions = new JsonObject();
            JsonArray parse = new JsonArray();
            parse.add("roles"); // permite menção de cargos
            allowedMentions.add("parse", parse);
            payload.add("allowed_mentions", allowedMentions);

            // ================= Envia =================
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204) {
                plugin.getLogger().warning("[DiscordUtils] Falha ao enviar embed. Código: " + responseCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Envia uma mensagem simples para o Discord anunciando um novo recorde
     * e, opcionalmente, o recorde anterior.
     *
     * @param firstPlayer  Nome do novo recordista
     * @param firstTime    Tempo do novo recorde
     * @param secondPlayer Nome do recordista anterior (ou null se não houver)
     * @param secondTime   Tempo do recordista anterior (ou 0 se não houver)
     */
    public static void sendRecordMessage(String firstPlayer, double firstTime, String secondPlayer, double secondTime, String trackName) {
        try {
            URL url = new URL(WEBHOOK);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            // Formata tempo estilo minutos:segundos.milissegundos
            int firstMin = (int) (firstTime / 60);
            double firstSec = firstTime % 60;
            String formattedFirstTime = String.format("%d:%06.3f", firstMin, firstSec);

            String formattedSecondTime = "";
            if (secondPlayer != null && !secondPlayer.isEmpty() && secondTime > 0 && secondTime != firstTime) {
                int secondMin = (int) (secondTime / 60);
                double secondSec = secondTime % 60;
                formattedSecondTime = String.format(" Prev: %d:%06.3f (%s) 🥈",
                        secondMin, secondSec, secondPlayer);
            }

            // Monta mensagem com jogador e pista em negrito
            String message = "🏁 **" + firstPlayer + "** conseguiu o recorde na pista **" + trackName + "**: " +
                    formattedFirstTime + " 🏆" + formattedSecondTime;

            JsonObject payload = new JsonObject();
            payload.addProperty("content", message);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204) {
                System.out.println("[DiscordUtils] Falha ao enviar mensagem de recorde. Código: " + responseCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Baixa a imagem da URL e salva na pasta trackimages dentro do plugin, sempre como PNG
     */
    private static void downloadImage(JavaPlugin plugin, String trackName, String imageUrl) {
        try {
            File folder = new File(plugin.getDataFolder(), "trackimages");
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, trackName.replaceAll("\\s+", "_") + ".png"); // força PNG

            URL url = new URL(imageUrl);
            try (InputStream in = url.openStream()) {
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
