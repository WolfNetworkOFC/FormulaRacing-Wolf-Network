package dev.EfraGroup.formulaRacing;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager.PlayerTime;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class TrackLeaderboard {

    private final String trackName;
    private Location location;  // localização do holograma
    private final DatabaseManager mySQLManager;
    private Hologram hologram;

    private final JavaPlugin plugin;
    private int taskId = -1;

    public TrackLeaderboard(JavaPlugin plugin, String trackName, Location defaultLocation, DatabaseManager mySQLManager) {
        this.plugin = plugin;
        this.trackName = trackName;
        this.mySQLManager = mySQLManager;

        Location savedLoc = mySQLManager.getHologramLocation(trackName);
        this.location = savedLoc != null ? savedLoc : defaultLocation;
    }

    /** Cria ou atualiza o holograma com as linhas recebidas. */
    public synchronized void createOrUpdateHologram(List<String> lines) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (hologram != null) {
                hologram.delete(); // remove holograma antigo
                hologram = null;
            }

            Location holoLoc = location.clone().add(0, 0.5, 0);

            // Nome seguro para o holograma (sem espaços, tudo minúsculo)
            String holoName = "leaderboard-" + trackName.replace(" ", "");

            hologram = DHAPI.createHologram(holoName, holoLoc, false, lines);

            // Salva a localização no banco
            mySQLManager.saveHologramLocation(trackName, location);
            startAutoUpdate();
        });
    }

    /** Remove o holograma manualmente. */
    public synchronized void removeHologram() {
        if (hologram != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                hologram.delete();
                hologram = null;
            });
            cancelUpdateTask();
        }
    }

    /** Define nova localização para o holograma. */
    public void setLocation(Location newLocation) {
        this.location = newLocation;
        mySQLManager.saveHologramLocation(trackName, newLocation);
        if (hologram != null) {
            Bukkit.getScheduler().runTask(plugin, () -> hologram.setLocation(newLocation.clone().add(0, 0.5, 0)));  
        }
    }

    public String getTrackName() {
        return trackName;
    }

    public void updateLeaderboard() {
        // 1. Rodar a busca de dados de forma ASSÍNCRONA
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            // Busca dados no banco (Thread separada, não trava o servidor)
            List<PlayerTime> leaderboard = mySQLManager.getLeaderboard(trackName);

            // ✅ OTIMIZAÇÃO: Use um cache para o checkpoint count no DatabaseManager
            // ou busque aqui no Async também
            int totalCheckpoints = mySQLManager.getCheckpointCount(trackName);

            List<PlayerTime> top10 = leaderboard.stream().limit(10).collect(Collectors.toList());
            List<String> configLines = plugin.getConfig().getStringList("leaderboards.fastesttime.lines");
            List<String> lines = new ArrayList<>();

            for (String configLine : configLines) {
                String line = configLine.replace("{mapname}", trackName);
                for (int j = 1; j <= 10; j++) {
                    if (top10.size() >= j) {
                        PlayerTime p = top10.get(j - 1);
                        String playerTime = (p.isFinished() || p.getCheckpointsReached() >= totalCheckpoints)
                                ? formatTime(p.getTime())
                                : "§7" + p.getCheckpointsReached() + "CP§e(" + formatTime(p.getTime()) + ")";

                        line = line.replace("{name" + j + "}", p.getPlayerName());
                        line = line.replace("{time" + j + "}", playerTime);
                    } else {
                        line = line.replace("{name" + j + "}", "----").replace("{time" + j + "}", "----");
                    }
                }
                lines.add(line);
            }

            // 2. Voltar para a Thread Principal apenas para atualizar o Holograma (Exigência da API)
            Bukkit.getScheduler().runTask(plugin, () -> {
                updateHologramLines(lines);
            });
        });
    }

    /** Atualiza apenas o texto do holograma existente sem deletar e recriar */
    private void updateHologramLines(List<String> lines) {
        if (hologram == null) {
            String holoName = "leaderboard-" + trackName.replace(" ", "");
            Location holoLoc = location.clone().add(0, 0.5, 0);
            hologram = DHAPI.createHologram(holoName, holoLoc, false, lines);
        } else {
            DHAPI.setHologramLines(hologram, lines);
        }
    }


    /** Formata tempo em mm:ss.SSS ou ss.SSS */
    public String formatTime(double timeInSeconds) {
        long minutes = (long) (timeInSeconds / 60);
        long seconds = (long) (timeInSeconds % 60);
        long millis = (long) ((timeInSeconds - Math.floor(timeInSeconds)) * 1000);

        if (minutes > 0) {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
        } else {
            return String.format("%d.%03d", seconds, millis);
        }
    }

    /** Inicia atualização automática do leaderboard */
    public void startAutoUpdate() {
        if (taskId != -1) return;

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::updateLeaderboard, 0L, 20L * 10).getTaskId();
    }

    /** Cancela atualização automática */
    public void cancelUpdateTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
