package dev.EfraGroup.formulaRacing;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Hologram.HologramManager;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

public class TrackLeaderboard {
    private final String trackName;
    private Location location;
    private final DatabaseManager mySQLManager;
    private final JavaPlugin plugin;
    private ScheduledTask task;

    private final Map<String, Hologram> holograms = new HashMap<>();

    public TrackLeaderboard(JavaPlugin plugin, String trackName, Location defaultLocation, DatabaseManager mySQLManager) {
        this.plugin = plugin;
        this.trackName = trackName;
        this.mySQLManager = mySQLManager;
        Location savedLoc = mySQLManager.getHologramLocation(trackName);
        this.location = savedLoc != null ? savedLoc : defaultLocation;
    }

    public void startAutoUpdate() {
        if (this.task == null || this.task.isCancelled()) {
            long ticks = this.plugin.getConfig().getLong("leaderboards.updateticks", 200L);
            this.task = SchedulerHelper.runTaskTimer(this.plugin, () -> {
                this.updateJavaLeaderboard();
                this.updateBedrockLeaderboard();
            }, 0L, ticks);
        }
    }

    public void updateJavaLeaderboard() {
        SchedulerHelper.runAsync(this.plugin, () -> {
            List<DatabaseManager.PlayerTime> leaderboard = this.mySQLManager.getLeaderboardJava(this.trackName);
            processAndShow(leaderboard, "java");
        });
    }

    public void updateBedrockLeaderboard() {
        SchedulerHelper.runAsync(this.plugin, () -> {
            List<DatabaseManager.PlayerTime> leaderboard = this.mySQLManager.getLeaderboardBedrock(this.trackName);
            processAndShow(leaderboard, "bedrock");
        });
    }

    public void setLocation(Location newLocation) {
        this.location = newLocation;
        this.mySQLManager.saveHologramLocation(this.trackName, newLocation);

        SchedulerHelper.runTask(this.plugin, () -> {
            if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
                try {
                    holograms.forEach((type, holo) -> {
                        if (holo != null) {
                            double xOffset = type.equals("bedrock") ? 3.0 : 0.0;
                            Location holoLoc = newLocation.clone().add(xOffset, 0.5, 0.0);
                            holo.setLocation(holoLoc);
                        }
                    });
                } catch (Exception ignored) {}
            }
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

        String configPath = "leaderboards.fastesttime-" + type + ".lines";
        List<String> configLines = this.plugin.getConfig().getStringList(configPath);

        if (configLines.isEmpty()) {
            configLines = new ArrayList<>();
            configLines.add("&6&lLeaderboard &e" + this.trackName);
            configLines.add("&7Nenhum tempo registrado");
        }

        List<String> finalLines = new ArrayList<>();

        for (String configLine : configLines) {
            String processedLine = configLine.replace("{mapname}", this.trackName);
            processedLine = processedLine.replace("$java$", ":java:").replace("$bedrock$", ":bedrock:");
            String line = ChatColor.translateAlternateColorCodes('&', processedLine);

            if (line.contains("{name")) {
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
            }
            finalLines.add(line);
        }

        String safeTrackName = this.trackName.toLowerCase().replaceAll("[^a-z0-9]", "");
        double xOffset = type.equals("bedrock") ? 4.0 : 0.0;
        Location holoLoc = this.location.clone().add(xOffset, 0.5, 0.0);

        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            SchedulerHelper.runTask(this.plugin, () -> {
                try {
                    String holoName = "lb_" + type + "_" + safeTrackName;
                    Hologram holo = DHAPI.getHologram(holoName);
                    if (holo == null) {
                        holo = DHAPI.createHologram(holoName, holoLoc, false, finalLines);
                    } else {
                        DHAPI.setHologramLines(holo, finalLines);
                        holo.setLocation(holoLoc);
                    }
                    holograms.put(type, holo);
                } catch (Exception ignored) {}
            });
        } else {
            SchedulerHelper.runTaskAt(this.plugin, holoLoc, () -> {
                if (holoLoc.getWorld() == null) return;
                FormulaRacing.getInstance().getHologramManager().createHologramSync(safeTrackName + "_" + type, holoLoc, finalLines);
            });
        }
    }

    public synchronized void removeHologram() {
        this.cancelUpdateTask();
        SchedulerHelper.runTask(this.plugin, () -> {
            holograms.values().forEach(holo -> {
                if (holo != null) {
                    try { holo.delete(); } catch (Exception ignored) {}
                }
            });
            holograms.clear();
            if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
                for (String type : new String[]{"java", "bedrock"}) {
                    String safeName = this.trackName.toLowerCase().replaceAll("[^a-z0-9]", "");
                    String holoName = "lb_" + type + "_" + safeName;
                    try {
                        Hologram orphan = DHAPI.getHologram(holoName);
                        if (orphan != null) {
                            orphan.delete();
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                HologramManager hm = FormulaRacing.getInstance().getHologramManager();
                if (hm != null) {
                    for (String type : new String[]{"java", "bedrock"}) {
                        String safeName = this.trackName.toLowerCase().replaceAll("[^a-z0-9]", "");
                        hm.deleteHologram(safeName + "_" + type);
                    }
                }
            }
        });
    }

    public void cancelUpdateTask() {
        if (this.task != null && !this.task.isCancelled()) {
            this.task.cancel();
            this.task = null;
        }
    }

    public String formatTime(double timeInSeconds) {
        long minutes = (long)(timeInSeconds / 60.0);
        long seconds = (long)(timeInSeconds % 60.0);
        long millis = (long)((timeInSeconds - Math.floor(timeInSeconds)) * 1000.0);
        return minutes > 0 ? String.format("%d:%02d.%03d", minutes, seconds, millis) : String.format("%d.%03d", seconds, millis);
    }
}
