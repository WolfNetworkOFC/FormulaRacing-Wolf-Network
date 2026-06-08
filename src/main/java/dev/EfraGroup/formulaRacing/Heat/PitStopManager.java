//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Config.PitStopConfigManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.Track;
import dev.EfraGroup.formulaRacing.Event.DriverPassPitEvent;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class PitStopManager {
    private final Set<UUID> pitStopCompleted = new HashSet();
    private final Map<UUID, Set<Integer>> driverSwappedLaps = new ConcurrentHashMap<>();
    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private final PitStopConfigManager pitConfigManager;
    private final Map<String, PitStopRegion> pitStopRegions;
    private final Map<UUID, PitStopMinigame> activeMinigames;
    private final Set<UUID> playersInPit;
    private final Set<UUID> playersPassedPitStop;
    private final Set<UUID> playersInPitRegion;
    private final Set<UUID> playersInPitLane;
    private final Map<UUID, Long> pitStartTimes;
    private final Map<UUID, Long> pitCooldowns;
    private static final long PIT_COOLDOWN_MS = 3000L;

    public Set<String> getLoadedTracks() {
        return this.pitStopRegions.keySet();
    }

    public PitStopConfigManager getPitConfigManager() {
        return this.pitConfigManager;
    }

    public PitStopManager(FormulaRacing plugin, DatabaseManager databaseManager, PitStopConfigManager pitConfigManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.pitConfigManager = pitConfigManager;
        this.pitStopRegions = new ConcurrentHashMap();
        this.activeMinigames = new ConcurrentHashMap();
        this.playersInPit = ConcurrentHashMap.newKeySet();
        this.playersPassedPitStop = ConcurrentHashMap.newKeySet();
        this.playersInPitRegion = ConcurrentHashMap.newKeySet();
        this.playersInPitLane = ConcurrentHashMap.newKeySet();
        this.pitStartTimes = new ConcurrentHashMap();
        this.pitCooldowns = new ConcurrentHashMap();
        this.loadPitStops();
    }

    private void loadPitStops() {
        for(DatabaseManager.PitStopData data : this.databaseManager.getAllPitStops()) {
            RegionBox entryRegion = this.createRegionBox(data.getEntryMin(), data.getEntryMax());
            RegionBox exitRegion = this.createRegionBox(data.getExitMin(), data.getExitMax());
            RegionBox areaRegion = this.createRegionBox(data.getAreaMin(), data.getAreaMax());
            RegionBox startRegion = this.createRegionBox(data.getStartMin(), data.getStartMax());
            String trackNameWS = data.getTrackNameWS().toLowerCase();
            PitStopRegion pitStop = new PitStopRegion(trackNameWS, entryRegion, exitRegion, areaRegion, startRegion);
            this.pitStopRegions.put(trackNameWS, pitStop);
            this.plugin.getDebugManager().logPitStopSystem("[PitStop] Carregada pit stop region para: " + trackNameWS + " (start=" + (startRegion != null) + ", entry=" + (entryRegion != null) + ", exit=" + (exitRegion != null) + ", area=" + (areaRegion != null) + ")");
        }

    }

    private RegionBox createRegionBox(Location min, Location max) {
        return min != null && max != null ? new RegionBox(min, max) : null;
    }

    public String getPitStopStartAtLocation(Location location) {
        if (location != null && location.getWorld() != null) {
            for(Map.Entry<String, PitStopRegion> entry : this.pitStopRegions.entrySet()) {
                if ((entry.getValue()).isInStart(location)) {
                    return entry.getKey();
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public String getPitStopEntryAtLocation(Location location) {
        if (location != null && location.getWorld() != null) {
            for(Map.Entry<String, PitStopRegion> entry : this.pitStopRegions.entrySet()) {
                if (((PitStopRegion)entry.getValue()).isInEntry(location)) {
                    return (String)entry.getKey();
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public String getPitStopExitAtLocation(Location location) {
        if (location != null && location.getWorld() != null) {
            for(Map.Entry<String, PitStopRegion> entry : this.pitStopRegions.entrySet()) {
                if (((PitStopRegion)entry.getValue()).isInExit(location)) {
                    return (String)entry.getKey();
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public String getPitAreaAtLocation(Location location) {
        if (location != null && location.getWorld() != null) {
            for(Map.Entry<String, PitStopRegion> entry : this.pitStopRegions.entrySet()) {
                if (((PitStopRegion)entry.getValue()).isInArea(location)) {
                    return (String)entry.getKey();
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public boolean isOverPitBlock(Location location) {
        if (location == null) {
            return false;
        } else if (this.checkVerticalColumn(location.getBlock())) {
            return true;
        } else {
            int[][] offsets = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

            for(int[] offset : offsets) {
                if (this.checkVerticalColumn(location.getBlock().getRelative(offset[0], 0, offset[1]))) {
                    return true;
                }
            }

            return false;
        }
    }

    private boolean checkVerticalColumn(Block block) {
        Block current = block;

        for(int i = 0; i <= 3; ++i) {
            if (current.getType() == Material.YELLOW_GLAZED_TERRACOTTA) {
                return true;
            }

            current = current.getRelative(BlockFace.DOWN);
        }

        return false;
    }

    public boolean isValidPitStopLocation(Location location, String trackNameWS) {
        if (trackNameWS != null && location != null) {
            PitStopRegion region = this.getPitStop(trackNameWS);
            if (region == null) {
                return false;
            } else {
                return !region.isInArea(location) ? false : this.isOverPitBlock(location);
            }
        } else {
            return false;
        }
    }

    public boolean isInStartRegion(Location location, String trackNameWS) {
        PitStopRegion region = this.getPitStop(trackNameWS);
        return region != null && region.isInStart(location);
    }

    public boolean hasPitStop(String trackNameWS) {
        return trackNameWS != null && this.pitStopRegions.containsKey(trackNameWS.toLowerCase());
    }

    public boolean hasPitLane(String trackNameWS) {
        PitStopRegion region = this.getPitStop(trackNameWS);
        return region != null && region.hasArea();
    }

    public PitStopRegion getPitStop(String trackNameWS) {
        return trackNameWS != null ? (PitStopRegion)this.pitStopRegions.get(trackNameWS.toLowerCase()) : null;
    }

    public void onPlayerEnterPit(Player player, String trackNameWS, Heats heat) {
        UUID playerId = player.getUniqueId();
        if (!this.isOnCooldown(playerId)) {
            if (!this.pitStopCompleted.contains(playerId)) {
                if (!this.playersInPit.contains(playerId)) {
                    if (!this.activeMinigames.containsKey(playerId)) {
                        if (player.getVehicle() != null) {
                            player.getVehicle().setVelocity(new Vector(0, 0, 0));
                        }

                        this.playersInPit.add(playerId);
                        if (!this.pitStartTimes.containsKey(playerId)) {
                            this.pitStartTimes.put(playerId, System.currentTimeMillis());
                        }

                        this.startMinigame(player, heat);
                        Driver driver = heat.getDriver(playerId);
                        if (driver != null && heat.getRound() != null && heat.getRound().getEvent() != null) {
                            heat.getRound().getEvent().getAnnouncements().broadcastPitEntry(heat, driver);
                        }

                    }
                }
            }
        }
    }

    public void onPlayerExitPit(Player player) {
        UUID playerId = player.getUniqueId();
        PitStopMinigame minigame = (PitStopMinigame)this.activeMinigames.get(playerId);
        if (minigame != null && !minigame.isCompleted()) {
            minigame.cancel();
            this.activeMinigames.remove(playerId);
        }

        boolean wasInPit = this.playersInPit.remove(playerId);
        if (!wasInPit) {
            this.activeMinigames.remove(playerId);
        }

        this.playersInPitRegion.remove(playerId);
        this.pitStartTimes.remove(playerId);
        this.pitCooldowns.put(playerId, System.currentTimeMillis());
    }

    public void processPitStopStats(Driver driver, Heats heat, long duration) {
        if (driver != null) {
            UUID playerId = driver.getUuid();
            if (!this.pitStopCompleted.contains(playerId)) {
                this.pitStopCompleted.add(playerId);
                driver.incrementPitstops();
                if (this.plugin.getRaceEventManager() != null && this.plugin.getRaceEventManager().getDatabaseManager() != null) {
                    this.plugin.getRaceEventManager().getDatabaseManager().incrementDriverPitstops(driver.getId());
                }

                Lap currentLap = driver.getCurrentLap();
                if (currentLap != null) {
                    currentLap.setPitted(true);
                }

                Bukkit.getPluginManager().callEvent(new DriverPassPitEvent(driver, currentLap, driver.getPitstops()));
                this.broadcastPitStop(heat, driver, driver.getPitstops(), duration);
            }
        }
    }

    private void onEnterPitLaneStart(Player player, Heats heat) {
        this.plugin.getDebugManager().logPitStopSystem("[PIT] " + player.getName() + " entrou no corredor (START)");
        this.plugin.sendMessage(player, "pit_lane_start", new String[0]);
    }

    public void handlePitExit(Player player, Heats heat) {
        UUID playerId = player.getUniqueId();
        Driver driver = heat.getDriver(playerId);
        if (driver != null) {
            if (!this.pitStopCompleted.contains(playerId)) {
                Long start = (Long)this.pitStartTimes.get(playerId);
                long duration = start != null ? System.currentTimeMillis() - start : 0L;
                this.processPitStopStats(driver, heat, duration);
            }

            Lap currentLap = driver.getCurrentLap();
            if (driver.getCheckpointsReached() == 0 && currentLap != null && currentLap.getStartTime() > 0L) {
                this.plugin.getDebugManager().logPitStopSystem("[PIT EXIT] Bloqueada volta extra para " + player.getName() + " (Checkpoints já resetados).");
                this.finalizeExit(player, heat, driver);
            } else {
                int totalCheckpoints = 0;
                Track track = this.plugin.getTrackIntegrationManager().getTrack(heat.getTrackNameWS());
                if (track != null) {
                    totalCheckpoints = track.getTotalCheckpoints();
                }

                boolean canPassLap = false;
                if (totalCheckpoints > 0) {
                    double threshold = (double)totalCheckpoints * 0.8;
                    if ((double)driver.getCheckpointsReached() >= threshold) {
                        driver.forceCompleteCheckpoints(totalCheckpoints);
                        canPassLap = true;
                    }
                } else {
                    canPassLap = false;
                }

                if (canPassLap) {
                    this.plugin.getDebugManager().logPitStopSystem("[PIT] Passando volta para " + player.getName() + " via Pit Exit.");
                    heat.passLap(driver);
                }

                this.finalizeExit(player, heat, driver);
            }
        }
    }

    private void finalizeExit(Player player, Heats heat, Driver driver) {
        if (heat.getRound() != null && heat.getRound().getEvent() != null) {
            heat.getRound().getEvent().getAnnouncements().broadcastPitExit(heat, driver);
        }

        this.clearPitStopState(player.getUniqueId());
    }

    private void startMinigame(Player player, Heats heat) {
        UUID playerId = player.getUniqueId();
        Runnable onComplete = () -> {
            Long startTime = (Long)this.pitStartTimes.get(playerId);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0L;
            this.plugin.getDebugManager().logPitStopSystem("[PIT] Minigame concluído em " + duration + "ms para " + String.valueOf(playerId));
            Driver driver = heat.getDriver(playerId);
            if (driver != null) {
                this.processPitStopStats(driver, heat, duration);
            }

            this.plugin.sendMessage(player, "pit_go_msg", new String[0]);
        };
        PitStopConfigManager.PitConfig cfg = this.pitConfigManager.getConfig(heat != null ? heat.getTrackNameWS() : null);
        PitStopMinigame minigame = new PitStopMinigame(this.plugin, player, cfg, onComplete);
        this.activeMinigames.put(playerId, minigame);
    }

    public boolean handleInventoryClick(Player player, int slot) {
        PitStopMinigame minigame = (PitStopMinigame)this.activeMinigames.get(player.getUniqueId());
        return minigame != null ? minigame.handleClick(slot) : false;
    }

    private boolean isOnCooldown(UUID playerId) {
        Long lastPit = (Long)this.pitCooldowns.get(playerId);
        if (lastPit == null) {
            return false;
        } else {
            long elapsed = System.currentTimeMillis() - lastPit;
            return elapsed < 3000L;
        }
    }

    public boolean hasActiveMinigame(UUID playerId) {
        return this.activeMinigames.containsKey(playerId);
    }

    public PitStopMinigame getMinigame(UUID playerId) {
        return (PitStopMinigame)this.activeMinigames.get(playerId);
    }

    public boolean startTestMinigame(Player player, String trackName) {
        UUID playerId = player.getUniqueId();
        if (this.activeMinigames.containsKey(playerId)) {
            this.plugin.sendMessage(player, "pit_error_active", new String[0]);
            return false;
        } else {
            this.pitStartTimes.put(playerId, System.currentTimeMillis());
            Runnable onComplete = () -> {
                this.activeMinigames.remove(playerId);
                this.pitStartTimes.remove(playerId);
            };
            String trackNameFromLoc;
            if (trackName != null) {
                trackNameFromLoc = trackName;
            } else {
                trackNameFromLoc = this.getPitStopEntryAtLocation(player.getLocation());
                if (trackNameFromLoc == null) {
                    trackNameFromLoc = this.getPitStopExitAtLocation(player.getLocation());
                }

                if (trackNameFromLoc == null) {
                    Optional<Heats> heatOpt = this.plugin.getRaceEventManager().getPlayerActiveHeat(player.getUniqueId());
                    if (heatOpt.isPresent()) {
                        trackNameFromLoc = ((Heats)heatOpt.get()).getTrackNameWS();
                    }
                }

                if (trackNameFromLoc == null) {
                    trackNameFromLoc = this.getNearestTrack(player.getLocation(), (double)300.0F);
                    if (trackNameFromLoc != null) {
                        this.plugin.getDebugManager().logPitStopSystem("[PitStop] Detectado via Proximidade (<300m): " + trackNameFromLoc);
                    }
                }

                if (trackNameFromLoc == null) {
                    this.plugin.sendMessage(player, "pit_error_no_track", new String[0]);
                    this.plugin.sendMessage(player, "pit_using_default", new String[0]);
                    this.plugin.sendMessage(player, "pit_check_tip", new String[0]);
                }
            }

            this.plugin.getDebugManager().logPitStopSystem("[PitStop] /pit chamado por " + player.getName() + " -> Track detectada: " + trackNameFromLoc + " (Arg: " + trackName + ")");
            PitStopConfigManager.PitConfig cfg = this.pitConfigManager.getConfig(trackNameFromLoc);
            this.plugin.getDebugManager().logPitStopSystem("[PitStop] Config usada para " + trackNameFromLoc + ": " + cfg.targetItems().size() + " alvos");
            PitStopMinigame mg = new PitStopMinigame(this.plugin, player, cfg, onComplete);
            this.activeMinigames.put(playerId, mg);
            return true;
        }
    }

    public boolean startTestMinigame(Player player) {
        return this.startTestMinigame(player, (String)null);
    }

    public void clear() {
        for(PitStopMinigame minigame : this.activeMinigames.values()) {
            if (!minigame.isCompleted()) {
                Player player = this.plugin.getServer().getPlayer(minigame.getPlayerId());
                if (player != null) {
                    player.closeInventory();
                }
            }
        }

        this.activeMinigames.clear();
        this.playersInPit.clear();
        this.pitCooldowns.clear();
        this.pitStopCompleted.clear();
        this.driverSwappedLaps.clear();
    }

    public void addPitStopEntry(String trackNameWS, Location min, Location max) {
        if (trackNameWS != null) {
            String lookupName = trackNameWS.toLowerCase();
            PitStopRegion existing = (PitStopRegion)this.pitStopRegions.get(lookupName);
            RegionBox entryRegion = new RegionBox(min, max);
            RegionBox startRegion = existing != null ? existing.getStartRegion() : null;
            RegionBox exitRegion = existing != null ? existing.getExitRegion() : null;
            RegionBox areaRegion = existing != null ? existing.getAreaRegion() : null;
            PitStopRegion pitStop = new PitStopRegion(lookupName, entryRegion, exitRegion, areaRegion, startRegion);
            this.databaseManager.savePitStopEntry(trackNameWS, min, max);
            this.pitStopRegions.put(lookupName, pitStop);
            this.plugin.getDebugManager().logPitStopSystem("[PitStop] ENTRY region atualizada para: " + lookupName);
        }
    }

    public void addPitStopExit(String trackNameWS, Location min, Location max) {
        if (trackNameWS != null) {
            String lookupName = trackNameWS.toLowerCase();
            PitStopRegion existing = (PitStopRegion)this.pitStopRegions.get(lookupName);
            if (existing != null && existing.hasEntry()) {
                RegionBox exitRegion = new RegionBox(min, max);
                RegionBox startRegion = existing.getStartRegion();
                RegionBox entryRegion = existing.getEntryRegion();
                RegionBox areaRegion = existing.getAreaRegion();
                PitStopRegion pitStop = new PitStopRegion(lookupName, entryRegion, exitRegion, areaRegion, startRegion);
                this.databaseManager.savePitStopExit(trackNameWS, min, max);
                this.pitStopRegions.put(lookupName, pitStop);
                this.plugin.getDebugManager().logPitStopSystem("[PitStop] EXIT region atualizada para: " + lookupName);
            } else {
                this.plugin.getDebugManager().logPitStopSystem("[PitStop] Erro: Não é possível adicionar EXIT sem ENTRY configurada para: " + trackNameWS);
            }
        }
    }

    public void addPitStopArea(String trackNameWS, Location min, Location max) {
        if (trackNameWS != null) {
            String lookupName = trackNameWS.toLowerCase();
            PitStopRegion existing = (PitStopRegion)this.pitStopRegions.get(lookupName);
            RegionBox areaRegion = new RegionBox(min, max);
            RegionBox startRegion = existing != null ? existing.getStartRegion() : null;
            RegionBox entryRegion = existing != null ? existing.getEntryRegion() : null;
            RegionBox exitRegion = existing != null ? existing.getExitRegion() : null;
            PitStopRegion pitStop = new PitStopRegion(lookupName, entryRegion, exitRegion, areaRegion, startRegion);
            this.databaseManager.savePitStopArea(trackNameWS, min, max);
            this.pitStopRegions.put(lookupName, pitStop);
            this.plugin.getDebugManager().logPitStopSystem("[PitStop] AREA region salva para: " + lookupName);
        }
    }

    public void addPitStopStart(String trackNameWS, Location min, Location max) {
        if (trackNameWS != null) {
            String lookupName = trackNameWS.toLowerCase();
            PitStopRegion existing = (PitStopRegion)this.pitStopRegions.get(lookupName);
            RegionBox startRegion = new RegionBox(min, max);
            RegionBox entryRegion = existing != null ? existing.getEntryRegion() : null;
            RegionBox exitRegion = existing != null ? existing.getExitRegion() : null;
            RegionBox areaRegion = existing != null ? existing.getAreaRegion() : null;
            PitStopRegion pitStop = new PitStopRegion(lookupName, entryRegion, exitRegion, areaRegion, startRegion);
            databaseManager.savePitStopStart(trackNameWS, min, max);
            this.pitStopRegions.put(lookupName, pitStop);
            this.plugin.getDebugManager().logPitStopSystem("[PitStop] START region salva para: " + lookupName);
        }
    }

    public boolean removePitStop(String trackNameWS) {
        if (trackNameWS == null) {
            return false;
        } else {
            String lookupName = trackNameWS.toLowerCase();
            PitStopRegion removed = (PitStopRegion)this.pitStopRegions.remove(lookupName);
            if (removed != null) {
                this.databaseManager.removePitStop(trackNameWS);
                this.plugin.getDebugManager().logPitStopSystem("[PitStop] Pit stop region removida de: " + trackNameWS);
                return true;
            } else {
                return false;
            }
        }
    }

    public Set<String> getTracksWithPitStop() {
        return new HashSet<>(this.pitStopRegions.keySet());
    }

    public void markPlayerInPitStop(UUID playerId) {
        this.playersInPitRegion.add(playerId);
        this.playersPassedPitStop.add(playerId);
    }

    public boolean hasPassedPitStop(UUID playerId) {
        return this.playersPassedPitStop.contains(playerId);
    }

    public boolean hasCompletedMinigame(UUID playerId) {
        PitStopMinigame minigame = (PitStopMinigame)this.activeMinigames.get(playerId);
        return minigame != null && minigame.isCompleted();
    }

    /** @deprecated */
    @Deprecated
    public void completePitStop(Player player, Heats heat) {
        this.handlePitExit(player, heat);
    }

    public void clearPitStopState(UUID playerId) {
        this.playersPassedPitStop.remove(playerId);
        this.playersInPitRegion.remove(playerId);
        this.pitStartTimes.remove(playerId);
        this.activeMinigames.remove(playerId);
        this.pitStopCompleted.remove(playerId);
    }

    public PitBoxRegion getPitBoxAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (Map.Entry<String, PitStopRegion> entry : this.pitStopRegions.entrySet()) {
            PitStopRegion region = entry.getValue();
            RegionBox regionBox = region.getStartRegion() != null ? region.getStartRegion() : region.getAreaRegion();
            if (regionBox != null && regionBox.contains(location)) {
                return new PitBoxRegion(entry.getKey(), "", regionBox);
            }
        }
        return null;
    }

    public boolean isDriverEligibleForSwap(UUID playerId, int lap) {
        Set<Integer> swappedLaps = this.driverSwappedLaps.get(playerId);
        return swappedLaps == null || !swappedLaps.contains(lap);
    }

    public boolean isPitStopCompleted(UUID playerId) {
        return this.pitStopCompleted.contains(playerId);
    }

    public void markDriverSwapped(UUID playerId, int lap) {
        this.driverSwappedLaps.computeIfAbsent(playerId, k -> new HashSet<>()).add(lap);
    }

    public boolean isPlayerInPitRegion(UUID playerId) {
        return this.playersInPitRegion.contains(playerId);
    }

    public Set<UUID> getPlayersInPitRegion() {
        return new HashSet(this.playersInPitRegion);
    }

    public long getPitDuration(UUID playerId) {
        Long startTime = (Long)this.pitStartTimes.get(playerId);
        return startTime == null ? 0L : System.currentTimeMillis() - startTime;
    }

    private void broadcastPitStop(Heats heat, Driver driver, int totalPits, long duration) {
        double seconds = (double)duration / (double)1000.0F;
        String formattedDuration = String.format("%.2f", seconds);
        if (heat.getRound() != null && heat.getRound().getEvent() != null) {
            heat.getRound().getEvent().getAnnouncements().broadcastPitStop(heat, driver, totalPits, formattedDuration);
        } else {
            String driverName = this.getName(driver);
            String message = String.format("§e\ud83d\udee0 §f%s §7completou pit stop #%d §7(§f%ss§7)", driverName, totalPits, formattedDuration);
            this.broadcastMsg(heat, message);
        }

    }

    private String getName(Driver d) {
        Player p = this.plugin.getServer().getPlayer(d.getUuid());
        return p != null ? p.getName() : "Piloto";
    }

    private void broadcastMsg(Heats heat, String message) {
        for(Driver d : heat.getDrivers().values()) {
            Player p = this.plugin.getServer().getPlayer(d.getUuid());
            if (p != null) {
                p.sendMessage(message);
            }
        }

    }

    public String getNearestTrack(Location location, double maxDistance) {
        if (location != null && location.getWorld() != null) {
            String nearestTrack = null;
            double minDistanceSq = maxDistance * maxDistance;

            for(Map.Entry<String, PitStopRegion> entry : this.pitStopRegions.entrySet()) {
                String track = (String)entry.getKey();
                PitStopRegion region = (PitStopRegion)entry.getValue();
                if (region.hasEntry()) {
                    Location center = region.getEntryCenter();
                    if (center != null && center.getWorld().equals(location.getWorld())) {
                        double distSq = center.distanceSquared(location);
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            nearestTrack = track;
                        }
                    }
                }

                if (region.hasExit()) {
                    Location center = region.getExitCenter();
                    if (center != null && center.getWorld().equals(location.getWorld())) {
                        double distSq = center.distanceSquared(location);
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            nearestTrack = track;
                        }
                    }
                }
            }

            for(Map.Entry<String, PitStopRegion> entry : this.pitStopRegions.entrySet()) {
                PitStopRegion region = entry.getValue();
                if (region.hasArea()) {
                    Location center = region.getAreaCenter();
                    if (center != null && center.getWorld().equals(location.getWorld())) {
                        double distSq = center.distanceSquared(location);
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            nearestTrack = (String)entry.getKey();
                        }
                    }
                }
            }

            return nearestTrack;
        } else {
            return null;
        }
    }
}
