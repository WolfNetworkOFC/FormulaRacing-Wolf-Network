package dev.EfraGroup.formulaRacing;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class TrackLeaderboard {
    private final String trackName;
    private Location location;
    private final DatabaseManager mySQLManager;
    private final JavaPlugin plugin;
    private int taskId = -1;

    // Armazena os hologramas separadamente para Java e Bedrock
    private final Map<String, Hologram> holograms = new HashMap<>();

    public TrackLeaderboard(JavaPlugin plugin, String trackName, Location defaultLocation, DatabaseManager mySQLManager) {
        this.plugin = plugin;
        this.trackName = trackName;
        this.mySQLManager = mySQLManager;
        Location savedLoc = mySQLManager.getHologramLocation(trackName);
        this.location = savedLoc != null ? savedLoc : defaultLocation;
    }

    public void startAutoUpdate() {
        if (this.taskId == -1) {
            long ticks = this.plugin.getConfig().getLong("leaderboards.updateticks", 200L);
            this.taskId = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
                this.updateJavaLeaderboard();
                this.updateBedrockLeaderboard();
            }, 0L, ticks).getTaskId();
        }
    }

    public void updateJavaLeaderboard() {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            List<DatabaseManager.PlayerTime> leaderboard = this.mySQLManager.getLeaderboardJava(this.trackName);
            processAndShow(leaderboard, "java");
        });
    }

    public void updateBedrockLeaderboard() {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            List<DatabaseManager.PlayerTime> leaderboard = this.mySQLManager.getLeaderboardBedrock(this.trackName);
            processAndShow(leaderboard, "bedrock");
        });
    }

    public void setLocation(Location newLocation) {
        this.location = newLocation;

        // Salva a nova localização no Banco de Dados
        this.mySQLManager.saveHologramLocation(this.trackName, newLocation);

        // Atualiza a posição de todos os hologramas ativos (Java e Bedrock)
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            holograms.forEach((type, holo) -> {
                // Mantém o offset de 3 blocos para o Bedrock se for o caso
                double xOffset = type.equals("bedrock") ? 3.0 : 0.0;
                Location holoLoc = newLocation.clone().add(xOffset, 0.5, 0.0);
                holo.setLocation(holoLoc);
            });
        });
    }

    private void processAndShow(List<DatabaseManager.PlayerTime> leaderboard, String type) {
        int totalCheckpoints = this.mySQLManager.getCheckpointCount(this.trackName);

        List<DatabaseManager.PlayerTime> top10 = leaderboard.stream()
                .sorted((p1, p2) -> p1.getCheckpointsReached() != p2.getCheckpointsReached()
                        ? Integer.compare(p2.getCheckpointsReached(), p1.getCheckpointsReached())
                        : Double.compare(p1.getTime(), p2.getTime()))
                .limit(10)
                .toList();

        // Puxa as linhas da config baseado no tipo (java ou bedrock)
        List<String> configLines = this.plugin.getConfig().getStringList("leaderboards.fastesttime-" + type + ".lines");
        List<String> lines = new ArrayList<>();

        for (String configLine : configLines) {
            // Primeiro substitui o nome do mapa (isso garante que o título apareça)
            String line = configLine.replace("{mapname}", this.trackName);

            // Depois substitui as posições de 1 a 10
            for (int j = 1; j <= 10; ++j) {
                String nameKey = "{name" + j + "}";
                String timeKey = "{time" + j + "}";

                if (top10.size() >= j) {
                    DatabaseManager.PlayerTime p = top10.get(j - 1);
                    boolean hasFinished = (totalCheckpoints > 0 && p.getCheckpointsReached() >= totalCheckpoints) || p.isFinished();

                    String displayTime = hasFinished
                            ? "§e" + this.formatTime(p.getTime())
                            : "§7" + p.getCheckpointsReached() + "CP §e(" + this.formatTime(p.getTime()) + ")";

                    line = line.replace(nameKey, p.getPlayerName()).replace(timeKey, displayTime);
                } else {
                    line = line.replace(nameKey, "----").replace(timeKey, "----");
                }
            }
            lines.add(line);
        }

        // Volta para a Main Thread para mexer no DecentHolograms
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            String holoName = "leaderboard-" + type + "-" + this.trackName.toLowerCase().replace(" ", "_");

            // Offset: Se for Bedrock, coloca um pouco pro lado para não encavalar no Java
            double xOffset = type.equals("bedrock") ? 3.0 : 0.0;
            Location holoLoc = this.location.clone().add(xOffset, 0.5, 0.0);

            Hologram holo = holograms.get(type);
            if (holo == null) {
                holo = DHAPI.createHologram(holoName, holoLoc, false, lines);
                holograms.put(type, holo);
            } else {
                DHAPI.setHologramLines(holo, lines);
            }
        });
    }

    public synchronized void removeHologram() {
        this.cancelUpdateTask();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            holograms.values().forEach(Hologram::delete);
            holograms.clear();
        });
    }

    public void cancelUpdateTask() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }
    }

    public String formatTime(double timeInSeconds) {
        long minutes = (long)(timeInSeconds / 60.0);
        long seconds = (long)(timeInSeconds % 60.0);
        long millis = (long)((timeInSeconds - Math.floor(timeInSeconds)) * 1000.0);
        return minutes > 0 ? String.format("%d:%02d.%03d", minutes, seconds, millis) : String.format("%d.%03d", seconds, millis);
    }

    public abstract void updateLeaderboard();
}