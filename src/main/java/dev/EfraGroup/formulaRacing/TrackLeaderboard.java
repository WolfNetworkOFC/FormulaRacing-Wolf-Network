package dev.EfraGroup.formulaRacing;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Hologram.HologramManager;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.Text;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
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
    private final DatabaseManager mySQLManager;
    private final JavaPlugin plugin;
    private FRTask task;

    // Stored as Object so the shutdown lambda never links against the
    // DecentHolograms classes — during server stop DH may be unloaded before
    // this plugin, and any hard reference (even inside try/catch) throws
    // NoClassDefFoundError while the JVM resolves the lambda, aborting onDisable().
    private final Map<String, Object> holograms = new HashMap<>();
    private volatile boolean removed;
    private boolean javaEnabled = true;
    private boolean bedrockEnabled = true;

    // Per-type base locations so moving/setting one board never moves the other.
    private Location javaLocation;
    private Location bedrockLocation;

    private Location locationForType(String type) {
        return "bedrock".equals(type) ? this.bedrockLocation : this.javaLocation;
    }

    public TrackLeaderboard(JavaPlugin plugin, String trackName, Location defaultLocation, DatabaseManager mySQLManager) {
        this.plugin = plugin;
        this.trackName = trackName;
        this.mySQLManager = mySQLManager;
        Location savedJava = mySQLManager.getHologramLocation(trackName, "java");
        Location savedBedrock = mySQLManager.getHologramLocation(trackName, "bedrock");
        this.javaLocation = savedJava != null ? savedJava : defaultLocation.clone();
        this.bedrockLocation = savedBedrock != null ? savedBedrock : defaultLocation.clone();
        this.javaEnabled = mySQLManager.isHologramEnabled(trackName, "java");
        this.bedrockEnabled = mySQLManager.isHologramEnabled(trackName, "bedrock");
    }

    public boolean isJavaEnabled() {
        return javaEnabled;
    }

    public boolean isBedrockEnabled() {
        return bedrockEnabled;
    }

    public void setJavaEnabled(boolean enabled) {
        this.javaEnabled = enabled;
        this.mySQLManager.setHologramEnabled(this.trackName, "java", enabled);
        if (!enabled) {
            removeHologramByType("java");
        } else if (this.javaLocation != null) {
            updateJavaLeaderboard();
        }
    }

    public void setBedrockEnabled(boolean enabled) {
        this.bedrockEnabled = enabled;
        this.mySQLManager.setHologramEnabled(this.trackName, "bedrock", enabled);
        if (!enabled) {
            removeHologramByType("bedrock");
        } else if (this.bedrockLocation != null) {
            updateBedrockLeaderboard();
        }
    }

    private void removeHologramByType(String type) {
        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            String safeName = this.trackName.toLowerCase().replaceAll("[^a-z0-9]", "");
            String holoName = "lb_" + type + "_" + safeName;
            try {
                Hologram holo = (Hologram) this.holograms.get(type);
                if (holo == null) {
                    holo = DHAPI.getHologram(holoName);
                }
                if (holo != null) holo.delete();
            } catch (Throwable ignored) {}
            holograms.remove(type);
        } else {
            HologramManager hm = FormulaRacing.getInstance().getHologramManager();
            if (hm != null) {
                String safeName = this.trackName.toLowerCase().replaceAll("[^a-z0-9]", "");
                hm.deleteHologram(safeName + "_" + type);
            }
            holograms.remove(type);
        }
    }

    public void startAutoUpdate() {
        if (this.removed) return;
        if (this.task == null || this.task.isCancelled()) {
            long ticks = this.plugin.getConfig().getLong("leaderboards.updateticks", 200L);
            this.task = SchedulerHelper.runTaskTimer(this.plugin, () -> {
                this.updateJavaLeaderboard();
                this.updateBedrockLeaderboard();
            }, 0L, ticks);
        }
    }

    public void updateJavaLeaderboard() {
        if (this.removed || !this.javaEnabled) return;
        SchedulerHelper.runAsync(this.plugin, () -> {
            if (this.removed || !this.javaEnabled) return;
            List<DatabaseManager.PlayerTime> leaderboard = this.mySQLManager.getLeaderboardJava(this.trackName);
            plugin.getLogger().info("[Leaderboard] Java update for " + this.trackName + ": " + leaderboard.size() + " entries");
            processAndShow(leaderboard, "java");
        });
    }

    public void updateBedrockLeaderboard() {
        if (this.removed || !this.bedrockEnabled) return;
        SchedulerHelper.runAsync(this.plugin, () -> {
            if (this.removed || !this.bedrockEnabled) return;
            List<DatabaseManager.PlayerTime> leaderboard = this.mySQLManager.getLeaderboardBedrock(this.trackName);
            plugin.getLogger().info("[Leaderboard] Bedrock update for " + this.trackName + ": " + leaderboard.size() + " entries");
            processAndShow(leaderboard, "bedrock");
        });
    }

    public void setLocation(Location newLocation, String type) {
        if (this.removed) return;
        if ("bedrock".equals(type)) {
            this.bedrockLocation = newLocation.clone();
            this.mySQLManager.saveHologramLocation(this.trackName, "bedrock", newLocation);
        } else {
            this.javaLocation = newLocation.clone();
            this.mySQLManager.saveHologramLocation(this.trackName, "java", newLocation);
        }

        boolean bedrock = "bedrock".equals(type);
        Location loc = bedrock ? this.bedrockLocation : this.javaLocation;
        Runnable moveHologram = () -> {
            if (this.removed) return;
            if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
                try {
                    Hologram holo = (Hologram) this.holograms.get(type);
                    if (holo == null) {
                        holo = DHAPI.getHologram("lb_" + type + "_" + this.trackName.toLowerCase().replaceAll("[^a-z0-9]", ""));
                    }
                    if (holo != null) {
                        holo.setLocation(loc);
                    }
                } catch (Throwable ignored) {}
            }
        };
        if (this.plugin.isEnabled()) {
            SchedulerHelper.runTask(this.plugin, moveHologram);
        } else {
            moveHologram.run();
        }
    }

    public void setLocation(Location newLocation) {
        if (this.removed) return;
        // Persist the new base per-type so each board keeps its own location.
        this.javaLocation = newLocation.clone();
        this.bedrockLocation = newLocation.clone();
        this.mySQLManager.saveHologramLocation(this.trackName, "java", newLocation);
        this.mySQLManager.saveHologramLocation(this.trackName, "bedrock", newLocation);

        Runnable moveHolograms = () -> {
            if (this.removed) return;
            if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
                try {
                    holograms.forEach((t, holo) -> {
                        if (holo instanceof Hologram h) {
                            h.setLocation(this.locationForType(t));
                        }
                    });
                } catch (Throwable ignored) {}
            }
        };

        if (this.plugin.isEnabled()) {
            SchedulerHelper.runTask(this.plugin, moveHolograms);
        } else {
            moveHolograms.run();
        }
    }

    private void processAndShow(List<DatabaseManager.PlayerTime> leaderboard, String type) {
        if (this.removed) return;
        if (type.equals("java") && !this.javaEnabled) return;
        if (type.equals("bedrock") && !this.bedrockEnabled) return;
        int totalCheckpoints = this.mySQLManager.getCheckpointCount(this.trackName);

        List<DatabaseManager.PlayerTime> top10 = leaderboard.stream()
                .sorted((p1, p2) -> {
                    // Finished laps rank above partial checkpoint runs.
                    if (p1.isFinished() != p2.isFinished()) {
                        return p2.isFinished() ? 1 : -1;
                    }
                    // More checkpoints reached rank higher.
                    int byCheckpoints = Integer.compare(p2.getCheckpointsReached(), p1.getCheckpointsReached());
                    if (byCheckpoints != 0) {
                        return byCheckpoints;
                    }
                    // Faster time ranks higher.
                    return Double.compare(p1.getTime(), p2.getTime());
                })
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
            processedLine = Text.translateEmojis(processedLine);
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
        Location holoLoc = this.locationForType(type);

        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            Runnable updateDecentHologram = () -> {
                if (this.removed) return;
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
                } catch (Throwable ignored) {}
            };

            if (this.plugin.isEnabled()) {
                SchedulerHelper.runTask(this.plugin, updateDecentHologram);
            } else {
                updateDecentHologram.run();
            }
        } else {
            Runnable updateInternalHologram = () -> {
                if (this.removed) return;
                if (holoLoc.getWorld() == null) return;
                FormulaRacing.getInstance().getHologramManager().createHologramSync(safeTrackName + "_" + type, holoLoc, finalLines);
            };

            if (this.plugin.isEnabled()) {
                SchedulerHelper.runTaskAt(this.plugin, holoLoc, updateInternalHologram);
            } else {
                updateInternalHologram.run();
            }
        }
    }

    public synchronized void removeHologram() {
        this.removed = true;
        this.cancelUpdateTask();

        Runnable cleanup = () -> {
            holograms.values().forEach(holo -> {
                if (holo != null) {
                    // Reflective delete: this lambda runs unconditionally on shutdown,
                    // so it must not reference the DecentHolograms classes at all.
                    try {
                        holo.getClass().getMethod("delete").invoke(holo);
                    } catch (Throwable ignored) {}
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
                    } catch (Throwable ignored) {}
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
        };

        if (this.plugin.isEnabled()) {
            SchedulerHelper.runTask(this.plugin, cleanup);
        } else {
            cleanup.run();
        }
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
