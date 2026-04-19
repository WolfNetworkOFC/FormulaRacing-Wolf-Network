//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

public class TrackLeaderboard {
    private final String trackName;
    private Location location;
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

    public void updateBedrockLeaderboard() {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            List<DatabaseManager.PlayerTime> leaderboard = this.mySQLManager.getLeaderboardBedrock(this.trackName);
            buildAndUpdateHologram(leaderboard, "bedrock");
        });
    }

    public void updateJavaLeaderboard() {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            List<DatabaseManager.PlayerTime> leaderboard = this.mySQLManager.getLeaderboardJava(this.trackName);
            buildAndUpdateHologram(leaderboard, "java");
        });
    }

    private void buildAndUpdateHologram(List<DatabaseManager.PlayerTime> leaderboard, String type) {
        int totalCheckpoints = this.mySQLManager.getCheckpointCount(this.trackName);
        List<DatabaseManager.PlayerTime> top10 = leaderboard.stream()
                .sorted((p1, p2) -> p1.getCheckpointsReached() != p2.getCheckpointsReached()
                        ? Integer.compare(p2.getCheckpointsReached(), p1.getCheckpointsReached())
                        : Double.compare(p1.getTime(), p2.getTime()))
                .limit(10)
                .collect(Collectors.toList());

        List<String> configLines = this.plugin.getConfig().getStringList("leaderboards.fastesttime.lines");
        List<String> lines = new ArrayList<>();

        for (String configLine : configLines) {
            String line = configLine.replace("{mapname}", this.trackName);

            for (int j = 1; j <= 10; ++j) {
                String nameKey = "{name" + j + "}";
                String timeKey = "{time" + j + "}";
                if (top10.size() >= j) {
                    DatabaseManager.PlayerTime p = top10.get(j - 1);
                    boolean hasFinished = totalCheckpoints > 0 && p.getCheckpointsReached() >= totalCheckpoints || p.isFinished();
                    String displayTime = hasFinished
                            ? "§e" + this.formatTime(p.getTime())
                            : "§7" + p.getCheckpointsReached() + "CP§e(" + this.formatTime(p.getTime()) + ")";
                    line = line.replace(nameKey, p.getPlayerName());
                    line = line.replace(timeKey, displayTime);
                } else {
                    line = line.replace(nameKey, "----").replace(timeKey, "----");
                }
            }
            lines.add(line);
        }

        Bukkit.getScheduler().runTask(this.plugin, () -> {
            String holoName = "leaderboard-" + type + "-" + this.trackName.replaceAll("[^a-zA-Z0-9_\\-]", "");
            Location holoLoc = this.location.clone().add(0.0, 0.5, 0.0);
            if (this.hologram == null) {
                this.hologram = DHAPI.createHologram(holoName, holoLoc, false, lines);
            } else {
                DHAPI.setHologramLines(this.hologram, lines);
            }
        });
    }


    public synchronized void removeHologram() {
        if (this.hologram != null) {
            if (this.plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    this.hologram.delete();
                    this.hologram = null;
                });
            } else {
                this.hologram.delete();
                this.hologram = null;
            }

            this.cancelUpdateTask();
        }
    }

    public void setLocation(Location newLocation) {
        this.location = newLocation;
        this.mySQLManager.saveHologramLocation(this.trackName, newLocation);
        if (this.hologram != null) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.hologram.setLocation(newLocation.clone().add((double)0.0F, (double)0.5F, (double)0.0F)));
        }

    }

    public String getTrackName() {
        return this.trackName;
    }

    public void updateLeaderboard() {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            List<DatabaseManager.PlayerTime> leaderboard = this.mySQLManager.getLeaderboard(this.trackName);
            int totalCheckpoints = this.mySQLManager.getCheckpointCount(this.trackName);
            List<DatabaseManager.PlayerTime> top10 = (List)leaderboard.stream().sorted((p1, p2) -> p1.getCheckpointsReached() != p2.getCheckpointsReached() ? Integer.compare(p2.getCheckpointsReached(), p1.getCheckpointsReached()) : Double.compare(p1.getTime(), p2.getTime())).limit(10L).collect(Collectors.toList());
            List<String> configLines = this.plugin.getConfig().getStringList("leaderboards.fastesttime.lines");
            List<String> lines = new ArrayList();

            for(String configLine : configLines) {
                String line = configLine.replace("{mapname}", this.trackName);

                for(int j = 1; j <= 10; ++j) {
                    String nameKey = "{name" + j + "}";
                    String timeKey = "{time" + j + "}";
                    if (top10.size() >= j) {
                        DatabaseManager.PlayerTime p = (DatabaseManager.PlayerTime)top10.get(j - 1);
                        boolean hasFinished = totalCheckpoints > 0 && p.getCheckpointsReached() >= totalCheckpoints || p.isFinished();
                        String displayTime;
                        if (!hasFinished) {
                            int var10000 = p.getCheckpointsReached();
                            displayTime = "§7" + var10000 + "CP§e(" + this.formatTime(p.getTime()) + ")";
                        } else {
                            String var16 = this.formatTime(p.getTime());
                            displayTime = "§e" + var16;
                        }

                        line = line.replace(nameKey, p.getPlayerName());
                        line = line.replace(timeKey, displayTime);
                    } else {
                        line = line.replace(nameKey, "----").replace(timeKey, "----");
                    }
                }

                lines.add(line);
            }

            Bukkit.getScheduler().runTask(this.plugin, () -> this.updateHologramLines(lines));
        });
    }

    private void updateHologramLines(List<String> lines) {
        if (this.hologram == null) {
            String var10000 = this.trackName.replace(" ", "");
            String holoName = "leaderboard-" + var10000.replaceAll("[^a-zA-Z0-9_\\-]", "");
            Location holoLoc = this.location.clone().add((double)0.0F, (double)0.5F, (double)0.0F);
            this.hologram = DHAPI.createHologram(holoName, holoLoc, false, lines);
        } else {
            DHAPI.setHologramLines(this.hologram, lines);
        }

    }

    public String formatTime(double timeInSeconds) {
        long minutes = (long)(timeInSeconds / (double)60.0F);
        long seconds = (long)(timeInSeconds % (double)60.0F);
        long millis = (long)((timeInSeconds - Math.floor(timeInSeconds)) * (double)1000.0F);
        return minutes > 0L ? String.format("%d:%02d.%03d", minutes, seconds, millis) : String.format("%d.%03d", seconds, millis);
    }

    public void startAutoUpdate() {
        if (this.taskId == -1) {
            long ticks = this.plugin.getConfig().getLong("leaderboards.updateticks", 200L);
            this.taskId = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
                this.updateLeaderboard();        // genérico
                this.updateJavaLeaderboard();    // específico Java
                this.updateBedrockLeaderboard(); // específico Bedrock
            }, 0L, ticks).getTaskId();
        }
    }


    public void cancelUpdateTask() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }

    }
}
