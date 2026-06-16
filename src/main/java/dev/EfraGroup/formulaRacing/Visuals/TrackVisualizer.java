package dev.EfraGroup.formulaRacing.Visuals;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.GridPosition;
import dev.EfraGroup.formulaRacing.Heat.PitStopRegion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public class TrackVisualizer {
    private final FormulaRacing plugin;
    private final Map<UUID, String> activeViewers = new ConcurrentHashMap();
    private final Map<String, TrackCache> cache = new ConcurrentHashMap();
    private static final double RENDER_DISTANCE_SQ = (double)4096.0F;

    public TrackVisualizer(FormulaRacing plugin) {
        this.plugin = plugin;
        this.startVisualizationTask();
    }

    private TrackCache getTrackCache(String trackNameWS) {
        TrackCache c = (TrackCache)this.cache.get(trackNameWS);
        if (c == null || c.isExpired()) {
            List<DatabaseManager.RegionData> all = this.plugin.getDatabaseManager().getAllRegions();
            List<DatabaseManager.RegionData> se = new ArrayList();

            for(DatabaseManager.RegionData r : all) {
                if (r.getTrackNameWS().equalsIgnoreCase(trackNameWS)) {
                    se.add(r);
                }
            }

            c = new TrackCache(se, this.plugin.getDatabaseManager().getCheckpoints(trackNameWS), this.plugin.getPitStopManager().getPitStop(trackNameWS), this.plugin.getDatabaseManager().getGridPositions(trackNameWS), this.plugin.getDatabaseManager().getTrackSpawn(trackNameWS));
            this.cache.put(trackNameWS, c);
        }

        return c;
    }

    public boolean isViewing(UUID uuid, String trackName) {
        return this.activeViewers.containsKey(uuid) && ((String)this.activeViewers.get(uuid)).equalsIgnoreCase(trackName);
    }

    public void toggleView(Player player, String trackName) {
        UUID uuid = player.getUniqueId();
        if (this.activeViewers.containsKey(uuid) && ((String)this.activeViewers.get(uuid)).equalsIgnoreCase(trackName)) {
            this.activeViewers.remove(uuid);
            this.plugin.sendMessage(player, "visual_disabled", new String[]{"{track}", trackName});
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        } else {
            this.activeViewers.put(uuid, trackName);
            this.plugin.sendMessage(player, "visual_enabled", new String[]{"{track}", trackName});
            this.plugin.sendMessage(player, "visual_showing", new String[0]);
        }

    }

    public void stopView(Player player) {
        this.activeViewers.remove(player.getUniqueId());
    }

    private void startVisualizationTask() {
        SchedulerHelper.runTaskTimer(this.plugin, (scheduledTask) -> {
            if (!this.activeViewers.isEmpty()) {
                for(Map.Entry<UUID, String> entry : this.activeViewers.entrySet()) {
                    Player player = this.plugin.getServer().getPlayer((UUID)entry.getKey());
                    if (player != null && player.isOnline()) {
                        String trackName = (String)entry.getValue();
                        this.renderTrackRegions(player, trackName);
                    } else {
                        this.activeViewers.remove(entry.getKey());
                    }
                }

            }
        }, 0L, 10L);
    }

    private void renderTrackRegions(Player player, String trackName) {
        String trackNameWS = trackName.replaceAll("\\s+", "").toLowerCase();
        String playerWorldName = player.getWorld().getName();
        Location playerLoc = player.getLocation();
        TrackCache trackCache = this.getTrackCache(trackNameWS);
        String langCode = this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
        String currentRegionName = null;

        for(DatabaseManager.RegionData region : trackCache.startEnd) {
            String type = region.getType().toUpperCase();
            if ((type.equals("START") || type.equals("END") || type.equals("RESET")) && region.getWorld().equalsIgnoreCase(playerWorldName)) {
                if (this.isNearRegion(playerLoc, region.getMinX(), region.getMinY(), region.getMinZ(), region.getMaxX(), region.getMaxY(), region.getMaxZ())) {
                    Color color;
                    if (type.equals("START")) {
                        color = Color.GREEN;
                    } else if (type.equals("END")) {
                        color = Color.RED;
                    } else {
                        color = Color.YELLOW;
                    }

                    this.drawRegion(player, region.getMinX(), region.getMinY(), region.getMinZ(), region.getMaxX(), region.getMaxY(), region.getMaxZ(), color);
                }

                if (this.isInsideRegion(playerLoc, region.getMinX(), region.getMinY(), region.getMinZ(), region.getMaxX(), region.getMaxY(), region.getMaxZ())) {
                    if (type.equals("START")) {
                        currentRegionName = this.plugin.getTranslation("visual_region_start", langCode, new String[0]);
                    } else if (type.equals("END")) {
                        currentRegionName = this.plugin.getTranslation("visual_region_end", langCode, new String[0]);
                    } else {
                        currentRegionName = "§e§lRESET REGION #" + region.getId();
                    }
                }
            }
        }

        for(DatabaseManager.RegionData cp : trackCache.checkpoints) {
            if (cp.getWorld().equalsIgnoreCase(playerWorldName)) {
                if (this.isNearRegion(playerLoc, cp.getMinX(), cp.getMinY(), cp.getMinZ(), cp.getMaxX(), cp.getMaxY(), cp.getMaxZ())) {
                    this.drawRegion(player, cp.getMinX(), cp.getMinY(), cp.getMinZ(), cp.getMaxX(), cp.getMaxY(), cp.getMaxZ(), Color.ORANGE);
                }

                if (this.isInsideRegion(playerLoc, cp.getMinX(), cp.getMinY(), cp.getMinZ(), cp.getMaxX(), cp.getMaxY(), cp.getMaxZ())) {
                    String checkpointId = String.valueOf(cp.getId());
                    String checkpointLabel = this.plugin.getTranslation("visual_region_checkpoint", langCode, new String[]{"{id}", checkpointId, "%s", checkpointId, "{checkpoint}", checkpointId});
                    if (checkpointLabel.contains("%s") || checkpointLabel.contains("%1$s")) {
                        checkpointLabel = checkpointLabel.replace("%1$s", checkpointId).replace("%s", checkpointId);
                    }
                    if (checkpointLabel.contains("[Lang Error]")) {
                        checkpointLabel = "§6§lCHECKPOINT #" + checkpointId;
                    }

                    currentRegionName = checkpointLabel;
                }
            }
        }

        PitStopRegion pitStop = trackCache.pitStop;
        if (pitStop != null) {
            if (pitStop.getEntryRegion() != null && pitStop.getEntryRegion().getMin().getWorld().getName().equalsIgnoreCase(playerWorldName)) {
                RegionBox box = pitStop.getEntryRegion();
                if (this.isNearRegion(playerLoc, box.getMin().getX(), box.getMin().getY(), box.getMin().getZ(), box.getMax().getX(), box.getMax().getY(), box.getMax().getZ())) {
                    this.drawRegionBox(player, box, Color.BLUE);
                }

                if (box.contains(playerLoc)) {
                    currentRegionName = this.plugin.getTranslation("visual_region_pit_entry", langCode, new String[0]);
                }
            }

            if (pitStop.getExitRegion() != null && pitStop.getExitRegion().getMin().getWorld().getName().equalsIgnoreCase(playerWorldName)) {
                RegionBox box = pitStop.getExitRegion();
                if (this.isNearRegion(playerLoc, box.getMin().getX(), box.getMin().getY(), box.getMin().getZ(), box.getMax().getX(), box.getMax().getY(), box.getMax().getZ())) {
                    this.drawRegionBox(player, box, Color.AQUA);
                }

                if (box.contains(playerLoc)) {
                    currentRegionName = this.plugin.getTranslation("visual_region_pit_exit", langCode, new String[0]);
                }
            }
        }

        if (pitStop != null && pitStop.hasArea() && pitStop.getAreaRegion().getMin().getWorld().getName().equalsIgnoreCase(playerWorldName)) {
            RegionBox box = pitStop.getAreaRegion();
            if (this.isNearRegion(playerLoc, box.getMin().getX(), box.getMin().getY(), box.getMin().getZ(), box.getMax().getX(), box.getMax().getY(), box.getMax().getZ())) {
                this.drawRegionBox(player, box, Color.PURPLE);
            }

            if (box.contains(playerLoc)) {
                currentRegionName = "§5§lPIT AREA (MINIGAME)";
            }
        }

        if (trackCache.gridPositions != null) {
            for(GridPosition grid : trackCache.gridPositions) {
                if (grid.getWorld().equalsIgnoreCase(playerWorldName) && playerLoc.distanceSquared(new Location(player.getWorld(), grid.getX(), grid.getY(), grid.getZ())) < (double)400.0F) {
                    Location loc = grid.toLocation(this.plugin.getServer());
                    if (loc != null) {
                        this.drawPoint(player, loc, Color.WHITE);
                    }
                }
            }
        }

        Location spawn = trackCache.spawn;
        if (spawn != null && spawn.getWorld().getName().equalsIgnoreCase(playerWorldName) && playerLoc.distanceSquared(spawn) < (double)400.0F) {
            this.drawPoint(player, spawn, Color.FUCHSIA);
        }

        if (currentRegionName != null) {
            String msg = this.plugin.getTranslation("visual_actionbar_in", langCode, new String[]{"%s", currentRegionName, "{region}", currentRegionName});
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
        } else {
            String msg = this.plugin.getTranslation("visual_actionbar_none", langCode, new String[0]);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
        }

    }

    private boolean isNearRegion(Location playerLoc, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double centerX = (minX + maxX) / (double)2.0F;
        double centerY = (minY + maxY) / (double)2.0F;
        double centerZ = (minZ + maxZ) / (double)2.0F;
        Location center = new Location(playerLoc.getWorld(), centerX, centerY, centerZ);
        double regionSize = Math.max(Math.abs(maxX - minX), Math.max(Math.abs(maxY - minY), Math.abs(maxZ - minZ)));
        double distanceThreshold = (double)64.0F + regionSize / (double)2.0F;
        return playerLoc.distanceSquared(center) < distanceThreshold * distanceThreshold;
    }

    private boolean isInsideRegion(Location loc, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return loc.getX() >= Math.min(minX, maxX) && loc.getX() <= Math.max(minX, maxX) && loc.getY() >= Math.min(minY, maxY) && loc.getY() <= Math.max(minY, maxY) && loc.getZ() >= Math.min(minZ, maxZ) && loc.getZ() <= Math.max(minZ, maxZ);
    }

    private void drawPoint(Player player, Location loc, Color color) {
        this.drawRegion(player, loc.getX() - 0.2, loc.getY(), loc.getZ() - 0.2, loc.getX() + 0.2, loc.getY() + (double)2.0F, loc.getZ() + 0.2, color);
    }

    private void drawRegionBox(Player player, RegionBox box, Color color) {
        Location min = box.getMin();
        Location max = box.getMax();
        this.drawRegion(player, min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), color);
    }

    private void drawRegion(Player player, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
        double x1 = Math.min(minX, maxX);
        double x2 = Math.max(minX, maxX);
        double y1 = Math.min(minY, maxY);
        double y2 = Math.max(minY, maxY);
        double z1 = Math.min(minZ, maxZ);
        double z2 = Math.max(minZ, maxZ);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2F);

        for(double x = x1; x <= x2; x += 0.8) {
            this.spawnParticle(player, x, y1, z1, dust);
            this.spawnParticle(player, x, y2, z1, dust);
            this.spawnParticle(player, x, y1, z2, dust);
            this.spawnParticle(player, x, y2, z2, dust);
        }

        for(double y = y1; y <= y2; y += 0.8) {
            this.spawnParticle(player, x1, y, z1, dust);
            this.spawnParticle(player, x2, y, z1, dust);
            this.spawnParticle(player, x1, y, z2, dust);
            this.spawnParticle(player, x2, y, z2, dust);
        }

        for(double z = z1; z <= z2; z += 0.8) {
            this.spawnParticle(player, x1, y1, z, dust);
            this.spawnParticle(player, x1, y2, z, dust);
            this.spawnParticle(player, x2, y1, z, dust);
            this.spawnParticle(player, x2, y2, z, dust);
        }

    }

    private void spawnParticle(Player player, double x, double y, double z, Particle.DustOptions dust) {
        Location loc = new Location(player.getWorld(), x, y, z);
        if (loc.distanceSquared(player.getLocation()) < (double)4096.0F) {
            player.spawnParticle(Particle.DUST, loc, 1, dust);
        }

    }

    private static class TrackCache {
        final List<DatabaseManager.RegionData> startEnd;
        final List<DatabaseManager.RegionData> checkpoints;
        final PitStopRegion pitStop;
        final List<GridPosition> gridPositions;
        final Location spawn;
        final long lastUpdate;

        TrackCache(List<DatabaseManager.RegionData> startEnd, List<DatabaseManager.RegionData> checkpoints, PitStopRegion pitStop, List<GridPosition> gridPositions, Location spawn) {
            this.startEnd = startEnd;
            this.checkpoints = checkpoints;
            this.pitStop = pitStop;
            this.gridPositions = gridPositions;
            this.spawn = spawn;
            this.lastUpdate = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - this.lastUpdate > 30000L;
        }
    }
}
