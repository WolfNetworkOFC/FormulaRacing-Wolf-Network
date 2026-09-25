package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.TimeTrial.Events.TimeTrialCheckpointEvent;
import dev.EfraGroup.formulaRacing.TimeTrial.Events.TimeTrialFinishEvent;
import dev.EfraGroup.formulaRacing.TimeTrial.Events.TimeTrialStartEvent;
import dev.EfraGroup.formulaRacing.TimeTrial.TimeTrialController;
import dev.EfraGroup.formulaRacing.TimeTrial.TimeTrialSession;
import dev.EfraGroup.formulaRacing.TimeTrial.Timing.OfficialTime;
import dev.EfraGroup.formulaRacing.TimeTrial.Timing.SoloTimingAttempt;
import dev.EfraGroup.formulaRacing.TimeTrial.Timing.WolfTimingService;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.RegionMathUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialDuelsAction;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import org.geysermc.floodgate.api.FloodgateApi;

public class RegionListener implements Listener {
    private final FormulaRacing plugin;
    private final DatabaseManager database;
    private final TimerUtils timerUtils;
    private final PacketSender packetSender;
    private final ScoreboardTimeTrialUtils stt;
    private final TimeTrialDuelsAction DuelsTimer;
    private final TimeTrialDuels timeTrialDuels;
    private final TimeTrialController timeTrialController;
    private final WolfTimingService timingService;
    private final Map<UUID, String> playerRegion = new ConcurrentHashMap<>();
    private final Map<String, List<DatabaseManager.RegionData>> regions = new ConcurrentHashMap<>();
    private final Set<String> warnedWorlds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLocationNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStartEndCross = new ConcurrentHashMap<>();
    private static final long START_END_DEBOUNCE_MS = 1000L;
    private final Map<UUID, Long> lastDuelRegionCross = new ConcurrentHashMap<>();
    private static final long DUEL_REGION_DEBOUNCE_MS = 500L;
    private final Map<UUID, Long> lastTimeLimitLog = new ConcurrentHashMap<>();
    private static final long TIME_LIMIT_LOG_DEBOUNCE_MS = 2000L;
    private final Set<UUID> justTeleported = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Long> lastTTDisabledWarning = new ConcurrentHashMap<>();
    private static final long TT_DISABLED_WARNING_COOLDOWN = 60000L;
    private static final double BEDROCK_REGION_Y_OFFSET = 0.25D;

    public void cleanupPlayer(UUID uuid) {
        this.playerRegion.remove(uuid);
        this.lastLocation.remove(uuid);
        this.lastLocationNanos.remove(uuid);
        this.lastStartEndCross.remove(uuid);
        this.lastDuelRegionCross.remove(uuid);
        this.lastTimeLimitLog.remove(uuid);
        this.justTeleported.remove(uuid);
        this.lastTTDisabledWarning.remove(uuid);
    }

    public void cleanupHeatPlayers(java.util.Collection<UUID> uuids) {
        for (UUID uuid : uuids) {
            this.lastLocation.remove(uuid);
        this.lastLocationNanos.remove(uuid);
            this.lastStartEndCross.remove(uuid);
            this.lastDuelRegionCross.remove(uuid);
            this.lastTimeLimitLog.remove(uuid);
            this.justTeleported.remove(uuid);
            this.lastTTDisabledWarning.remove(uuid);
        }
    }

    public RegionListener(FormulaRacing plugin, DatabaseManager database, TimerUtils timerUtils, PacketSender packetSender, ScoreboardTimeTrialUtils stt, TimeTrialDuelsAction DuelsTimer, TimeTrialDuels timeTrialDuels, TimeTrialController timeTrialController) {
        this(plugin, database, timerUtils, packetSender, stt, DuelsTimer, timeTrialDuels, timeTrialController, plugin.getWolfTimingService());
    }

    public RegionListener(
        FormulaRacing plugin,
        DatabaseManager database,
        TimerUtils timerUtils,
        PacketSender packetSender,
        ScoreboardTimeTrialUtils stt,
        TimeTrialDuelsAction DuelsTimer,
        TimeTrialDuels timeTrialDuels,
        TimeTrialController timeTrialController,
        WolfTimingService timingService
    ) {
        this.plugin = plugin;
        this.database = database;
        this.timerUtils = timerUtils;
        this.packetSender = packetSender;
        this.stt = stt;
        this.DuelsTimer = DuelsTimer;
        this.timeTrialDuels = timeTrialDuels;
        this.timeTrialController = timeTrialController;
        this.timingService = timingService;
        startRegionLoader();
        startRegionChecker();
    }

    private void markJustTeleported(Player player) {
        this.justTeleported.add(player.getUniqueId());
        SchedulerHelper.runTaskLater(this.plugin, () -> this.justTeleported.remove(player.getUniqueId()), 10L);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        int activeDuelId = this.timeTrialDuels.getActiveDuelIdCached(player.getUniqueId());
        if (activeDuelId != -1) {
            if (event.getCause() != TeleportCause.PLUGIN && event.getCause() != TeleportCause.COMMAND) {
                if (TimeTrialDuels.isPlayerBeingLapReset(player.getUniqueId())) {
                    this.lastLocation.remove(player.getUniqueId());
                    this.markJustTeleported(player);
                } else {
                    event.setCancelled(true);
                    String langCode = this.database.getPlayerLanguage(player.getUniqueId());
                    player.sendMessage(this.plugin.getDirectTranslation("duel_cannot_teleport", langCode));
                    this.plugin.getDebugManager().logRegionDetection("§c[TELEPORT] Canceled by RegionListener (Duel Protection). Cause: " + String.valueOf(event.getCause()));
                }
            } else {
                this.lastLocation.remove(player.getUniqueId());
                this.markJustTeleported(player);
            }
        } else {
            this.lastLocation.remove(player.getUniqueId());
            this.markJustTeleported(player);
        }
    }

    private void loadRegions() {
        Map<String, List<DatabaseManager.RegionData>> newRegions = new HashMap();

        for(DatabaseManager.RegionData r : this.database.getAllRegions()) {
            String world = r.getWorld().toLowerCase();
            ((List)newRegions.computeIfAbsent(world, (k) -> new ArrayList())).add(r);
        }

        this.regions.keySet().removeIf((key) -> !newRegions.containsKey(key));
        this.regions.putAll(newRegions);
    }

    public void reloadRegions() {
        this.loadRegions();
        this.plugin.getDebugManager().logRegionDetection("§a[RegionListener] Regions reloaded manually");
    }
    public void debugListRegions(String worldFilter, String trackFilter) {
        this.plugin.getDebugManager().logRegionDetection("§6========== LOADED REGIONS ==========");

        this.regions.forEach((world, regionList) -> {
            // World filter
            if (worldFilter != null && !world.equalsIgnoreCase(worldFilter)) return;

            this.plugin.getDebugManager().logRegionDetection("§e[WORLD: " + world + "]");

            for (DatabaseManager.RegionData region : regionList) {
                String type = region.getType().toUpperCase();
                String trackName = region.getTrackName();

                // Type (START/END) and Track Name filter
                boolean matchesType = type.equals("START") || type.equals("END");
                boolean matchesTrack = trackFilter == null || trackName.toLowerCase().contains(trackFilter.toLowerCase());

                if (matchesType && matchesTrack) {
                    this.plugin.getDebugManager().logRegionDetection(String.format("  §b%s §7[%s] ID=%d", trackName, type, region.getId()));
                    this.plugin.getDebugManager().logRegionDetection(String.format("    Min: %.2f, %.2f, %.2f", region.getMinX(), region.getMinY(), region.getMinZ()));
                    this.plugin.getDebugManager().logRegionDetection(String.format("    Max: %.2f, %.2f, %.2f", region.getMaxX(), region.getMaxY(), region.getMaxZ()));

                    double width = region.getMaxX() - region.getMinX();
                    double height = region.getMaxY() - region.getMinY();
                    double depth = region.getMaxZ() - region.getMinZ();

                    this.plugin.getDebugManager().logRegionDetection(String.format("    Dimensões: %.2f x %.2f x %.2f", width, height, depth));

                    // Collision/detection alert
                    if (height < 3.0) {
                        this.plugin.getDebugManager().logRegionDetection("    §c⚠ REGION TOO THIN! Y height < 3 blocks may fail detection.");
                    }
                }
            }
        });

        this.plugin.getDebugManager().logRegionDetection("§6========================================");
    }

    private void checkPlayerRegions(Player player) {
        UUID uuid = player.getUniqueId();
        if (!this.justTeleported.contains(uuid)) {
            boolean bedrockBoatDetection = this.isBedrockPlayer(uuid) && player.isInsideVehicle() && player.getVehicle() instanceof Boat;
            Location currentRaw = this.getDetectionLocation(player);
            if (currentRaw == null) {
                return;
            }
            long currentNanos = System.nanoTime();
            Location previousRaw = this.lastLocation.get(uuid);
            long previousNanos = this.lastLocationNanos.getOrDefault(uuid, currentNanos);
            Location current = currentRaw;
            Location previous = previousRaw;

            // If Bedrock and in boat, use the boat's position
            if (bedrockBoatDetection) {
                current = this.normalizeRegionLocation(player.getVehicle().getLocation(), true);
                if (previous != null) {
                    previous = this.normalizeRegionLocation(previous, true);
                }

            }

            this.lastLocation.put(uuid, currentRaw);
            this.lastLocationNanos.put(uuid, currentNanos);
            if (previous == null || previous.getWorld() == null || current.getWorld() == null || previous.getWorld() != current.getWorld()) {
                previous = current;
            }



            double distSq = previous.distanceSquared(current);
            if (!(distSq < 0.05)) {
                if (distSq > 2500.0F) {
                    this.lastLocation.put(uuid, currentRaw);
                } else {
                    if (player.isInsideVehicle()) {
                        Entity vehicle = player.getVehicle();
                        if (vehicle instanceof Boat) {
                            List<Entity> passengers = vehicle.getPassengers();
                            if (!passengers.contains(player) && !bedrockBoatDetection) {
                                return;
                            }
                        }
                    }

                    if (current.getWorld() == null) {
                        this.plugin.getDebugManager().logRegionDetection("[RegionListener] Mundo null detectado para jogador " + player.getName());
                    } else {
                        String worldName = current.getWorld().getName().toLowerCase();
                        List<DatabaseManager.RegionData> worldRegions = this.regions.get(worldName);

                        if (worldRegions != null && !worldRegions.isEmpty()) {
                            // DEBUG: nearest region
                            DatabaseManager.RegionData nearest = null;
                            double nearestDist = Double.MAX_VALUE;
                            for (DatabaseManager.RegionData region : worldRegions) {
                                double cx = (region.getMinX() + region.getMaxX()) / 2.0;
                                double cy = (region.getMinY() + region.getMaxY()) / 2.0;
                                double cz = (region.getMinZ() + region.getMaxZ()) / 2.0;
                                Location center = new Location(current.getWorld(), cx, cy, cz);
                                double dist = current.distance(center);
                                if (dist < nearestDist) {
                                    nearestDist = dist;
                                    nearest = region;
                                }
                            }

                            // Check if crossed START/END region. When the player has an
                            // intended track (running /tt, or lastTimeTrialTrack set),
                            // only regions of THAT track are detected — otherwise tracks
                            // with overlapping/stacked regions all trigger at once and
                            // the wrong time trial starts (e.g. two tracks sharing the
                            // same start line).
                            DatabaseManager.RegionData startEndRegion = this.getRegionAtLine(player, previous, current, worldRegions);
                                if (startEndRegion != null) {
                                    Location finalFrom = previous.clone();
                                    Location finalTo = current.clone();
                                    double crossingFraction = RegionMathUtils.calculateRegionEntryProportion(
                                        previous,
                                        current,
                                        startEndRegion
                                    );
                                    long crossingNanos = interpolateNanos(
                                        previousNanos,
                                        currentNanos,
                                        crossingFraction
                                    );
                                    SchedulerHelper.runTaskFor(
                                        this.plugin,
                                        player,
                                        () -> this.handleRegion(
                                            player,
                                            startEndRegion,
                                            finalFrom,
                                            finalTo,
                                            crossingNanos
                                        )
                                    );
                                }

                            // Checkpoints and duels logic
                            String activeTrack = this.timerUtils.getActiveTrack(player);
                            int activeDuelId = this.timeTrialDuels.getActiveDuelIdCached(uuid);
                            boolean isInDuel = activeDuelId != -1;
                            if (activeTrack != null || isInDuel) {
                                String trackForCheckpoints = isInDuel ? this.timeTrialDuels.getDuelTrackNameCached(activeDuelId) : activeTrack;

                                if (!(player.getVehicle() instanceof Boat)) {
                                    return;
                                }
                                if (trackForCheckpoints == null) {
                                    return;
                                }

                                List<DatabaseManager.RegionData> checkpoints = this.database.getCheckpoints(trackForCheckpoints);


                                if (!isInDuel && activeTrack != null) {
                                    TimerUtils.PlayerTimerData data = this.timerUtils.getTimerData(player, activeTrack);
                                    if (data != null) {
                                        int nextExpectedIndex = data.getCheckpointsReached();
                                        if (nextExpectedIndex < checkpoints.size()) {
                                            DatabaseManager.RegionData nextCp = checkpoints.get(nextExpectedIndex);
                                            if (RegionMathUtils.intersectsRegion(previous, current, nextCp)) {
                                                double proportion = RegionMathUtils.calculateRegionEntryProportion(previous, current, nextCp);
                                                long tickDurationMs = 50L;
                                                long adjustmentMs = (long) ((1.0F - proportion) * tickDurationMs);
                                                long preciseTimeMs = System.currentTimeMillis() - adjustmentMs;
                                                TimeTrialSession session = this.timeTrialController.getSession(player);
                                                long splitTime = session != null ? preciseTimeMs - session.getStartTime().toEpochMilli() : 0L;

                                                if (session != null) {
                                                    session.addCheckpointTime(splitTime / 1000.0F);
                                                }

                                                int cpId = nextCp.getId();
                                                double elapsed = session != null ? splitTime / 1000.0F : this.timerUtils.getPlayerElapsedTime(player);
                                                SchedulerHelper.runTask(this.plugin, () -> {
                                                    if (session != null) {
                                                        TimeTrialCheckpointEvent event = new TimeTrialCheckpointEvent(player, session, cpId, splitTime);
                                                        Bukkit.getPluginManager().callEvent(event);
                                                    }
                                                    this.timerUtils.addCheckpoint(player, cpId);
                                                    this.timerUtils.addTempCheckpoint(uuid, cpId, elapsed, activeTrack);
                                                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.5F);
                                                    this.plugin.getDebugManager().logRegionDetection(player.getName() + " collected checkpoint on track " + activeTrack);
                                                });
                                            }
                                        }
                                    }
                                } else if (isInDuel) {
                                    if (this.timeTrialDuels.isTimeLimitReached(activeDuelId)) {
                                        int currentLap = this.timeTrialDuels.getPlayerCurrentLap(uuid, activeDuelId);
                                        int lapWhenTimeLimitReached = this.timeTrialDuels.getLapWhenTimeLimitReached(uuid, activeDuelId);
                                        if (lapWhenTimeLimitReached >= 0 && currentLap > lapWhenTimeLimitReached) {
                                            long now = System.currentTimeMillis();
                                            Long lastLog = this.lastTimeLimitLog.get(uuid);
                                            if (lastLog == null || now - lastLog >= 2000L) {
                                                this.plugin.getDebugManager().logRegionDetection("§e[TIME LIMIT] " + player.getName() + " time limit exceeded.");
                                                this.lastTimeLimitLog.put(uuid, now);
                                            }
                                            return;
                                        }
                                    }
                                    Map<Integer, Double> collectedCheckpoints = this.database.getDuelCheckpointTimes(uuid, activeDuelId);
                                    int nextExpectedIndex = collectedCheckpoints.size();
                                    if (nextExpectedIndex < checkpoints.size()) {
                                        DatabaseManager.RegionData nextCp = checkpoints.get(nextExpectedIndex);
                                        if (RegionMathUtils.intersectsRegion(previous, current, nextCp)) {
                                            double elapsedTime = this.DuelsTimer.getPlayerLapElapsedSeconds(player);
                                            int cpId = nextCp.getId();
                                            SchedulerHelper.runTask(this.plugin, () -> {
                                                this.database.saveDuelCheckpointTime(uuid, activeDuelId, trackForCheckpoints, cpId, elapsedTime);
                                                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.5F);
                                                DebugManager var10000 = this.plugin.getDebugManager();
                                                String var10001 = player.getName();
                                                var10000.logRegionDetection(var10001 + " coletou checkpoint no duelo #" + activeDuelId);
                                            });
                                        }
                                    }
                                }
                            }

                        } else {
                            if (!this.warnedWorlds.contains(worldName)) {
                                this.warnedWorlds.add(worldName);
                                this.plugin.getDebugManager().logRegionDetection("[FormulaRacing] No regions registered for world " + worldName);
                            }

                        }
                    }
                }
            }
        }
    }

    private static long interpolateNanos(long beforeNanos, long afterNanos, double fraction) {
        long span = afterNanos - beforeNanos;
        if (span <= 0L) {
            return afterNanos;
        }
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        long value = Math.round(beforeNanos + span * clamped);
        return Math.max(beforeNanos, Math.min(afterNanos, value));
    }

    private void handleRegion(
        Player player,
        DatabaseManager.RegionData region,
        Location from,
        Location to,
        long crossingNanos
    ) {
        UUID uuid = player.getUniqueId();
        String regionTrackDisplayName = region.getTrackName();
        String regionTrackWS = region.getTrackNameWS();
        String type = region.getType().toUpperCase();
        if (player.getVehicle() instanceof Boat || type.equals("RESET")) {
            this.database.getPlayerLanguage(uuid);
            if (type.equals("START") || type.equals("END") || type.equals("RESET")) {
                int activeDuelId = this.timeTrialDuels.getActiveDuelIdCached(uuid);
                boolean isRunningDuel = activeDuelId != -1;
                boolean isRunningSolo = this.timerUtils.isTimerRunning(player, regionTrackWS);
                boolean ttEnabled = this.database.getTimeTrialEnabled(uuid);
                this.plugin.getDebugManager().logTimeTrialSystem(String.format("[AUTO TT] %s - handleRegion: track=%s, trackWS=%s, type=%s, duelId=%d, runningSolo=%b, ttEnabled=%b", player.getName(), regionTrackDisplayName, regionTrackWS, type, activeDuelId, isRunningSolo, ttEnabled));
                if (type.equals("RESET")) {
                    Location targetLoc = null;
                    if (!isRunningDuel) {
                        Optional<Heats> heatOpt = this.plugin.getRaceEventManager().getPlayerActiveHeat(player.getUniqueId());
                        if (heatOpt.isPresent()) {
                            Heats heat = (Heats)heatOpt.get();
                            Driver driver = heat.getDriver(player.getUniqueId());
                            if (driver != null) {
                                regionTrackWS = heat.getTrackNameWS();
                                int checkpointsReached = driver.getCheckpointsReached();
                                // Last PASSED checkpoint by ordinal → real id
                                // (ids may have gaps; checkpointsReached-1 as an
                                // id only works by coincidence on clean tracks).
                                List<Integer> orderedIds = this.plugin.getTrackIntegrationManager().getOrderedCheckpointIds(regionTrackWS);
                                Integer lastPassedId = checkpointsReached > 0 && checkpointsReached - 1 < orderedIds.size()
                                        ? orderedIds.get(checkpointsReached - 1) : null;
                                List<DatabaseManager.RegionData> checkpointByIdList = lastPassedId != null
                                        ? this.plugin.getTrackIntegrationManager().getCheckpointById(regionTrackWS, lastPassedId) : null;
                                if (checkpointsReached > 0 && checkpointByIdList != null && !checkpointByIdList.isEmpty()) {
                                    DatabaseManager.RegionData cp = checkpointByIdList.get(0);
                                    targetLoc = new Location(Bukkit.getWorld(cp.getWorld()), (cp.getMinX() + cp.getMaxX()) / (double)2.0F, cp.getMaxY() - (double)0.5F, (cp.getMinZ() + cp.getMaxZ()) / (double)2.0F, player.getLocation().getYaw(), player.getLocation().getPitch());
                                    driver.incrementResetCount();
                                    DebugManager var10000 = this.plugin.getDebugManager();
                                    String var10001 = player.getName();
                                    var10000.logRaceSystem("[RESET-HEAT] " + var10001 + " -> CP " + checkpointsReached + " (resetCount=" + driver.getResetCount() + ")");
                                }
                            }
                        }

                        if (targetLoc == null) {
                            String activeTrackKey = this.timerUtils.getActiveTrack(player);
                            if (activeTrackKey != null) {
                                regionTrackWS = activeTrackKey;
                                targetLoc = this.plugin.getTrackIntegrationManager().getTrackSpawn(regionTrackWS);
                                if (targetLoc != null) {
                                    // Parar o timer IMEDIATAMENTE no momento do reset (mesmo
                                    // comportamento do /reset), em vez de só quando cruzar a
                                    // linha START/END novamente.
                                    this.timerUtils.stopTimer(player);
                                    this.timeTrialController.endSession(player);
                                    if (this.timingService != null) {
                                        this.timingService.abort(uuid, true);
                                    }
                                    // Cancelar a gravação de ghost para não acumular frames
                                    // da volta abortada (memory leak no buffer de gravação).
                                    if (this.plugin.getGhostManager() != null) {
                                        this.plugin.getGhostManager().cancelRecording(player);
                                        // Esconder as linhas de PB/medalha — só reaparecem
                                        // ao cruzar START/END de novo (startSoloTimer).
                                        this.plugin.getGhostManager().stopReplay(player);
                                    }
                                    this.plugin.getDebugManager().logTimeTrialSystem("[RESET-SOLO] " + player.getName() + " -> Track Spawn (full reset)");
                                }
                            }
                        }

                        if (targetLoc == null) {
                            targetLoc = this.plugin.getTrackIntegrationManager().getTrackSpawn(regionTrackWS);
                            if (targetLoc != null) {
                                this.plugin.getDebugManager().logTimeTrialSystem("[RESET-FALLBACK] " + player.getName() + " -> Track Spawn");
                            }
                        }
                    } else {
                        Map<Integer, Double> collected = this.database.getDuelCheckpointTimes(uuid, activeDuelId);
                        int maxId = -1;

                        for(Integer id : collected.keySet()) {
                            if (id > maxId) {
                                maxId = id;
                            }
                        }

                        String duelTrackWS = this.timeTrialDuels.getDuelTrackNameCached(activeDuelId);
                        if (regionTrackWS.equalsIgnoreCase(duelTrackWS)) {
                            if (maxId != -1) {
                                for(DatabaseManager.RegionData cp : this.database.getCheckpoints(duelTrackWS)) {
                                    if (cp.getId() == maxId) {
                                        double x = (cp.getMinX() + cp.getMaxX()) / (double)2.0F;
                                        double z = (cp.getMinZ() + cp.getMaxZ()) / (double)2.0F;
                                        double y = cp.getMaxY() - (double)0.5F;
                                        targetLoc = new Location(player.getWorld(), x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
                                        break;
                                    }
                                }
                            }

                            if (targetLoc == null) {
                                DatabaseManager.TrackData td = this.database.getTrackData(duelTrackWS);
                                if (td != null) {
                                    targetLoc = td.getSpawnLocation();
                                }
                            }
                        }
                    }

                    if (targetLoc != null) {
                        this.plugin.getAPI().recoverPlayerBoatState(player);

                        DebugManager debug = this.plugin.getDebugManager();
                        String worldName = targetLoc.getWorld().getName();

// Log of the Reset scheduling
                        debug.logTimeTrialSystem(String.format("[RESET] Scheduled Teleport for %s to: World=%s, X=%d, Y=%d, Z=%d",
                                player.getName(), worldName, targetLoc.getBlockX(), targetLoc.getBlockY(), targetLoc.getBlockZ()));

// Executes teleport and boat spawn 1 tick later to avoid collision/NMS bugs
                        // Create final references so the Lambda can capture them without error
                        final DebugManager finalDebug = this.plugin.getDebugManager();
                        final Location finalTargetLoc = targetLoc;
                        final Player finalPlayer = player;
                        final String finalTrackWS = regionTrackWS;

                        SchedulerHelper.runTaskLater(this.plugin, () -> {
                            // Verificamos se o jogador ainda está online após o delay de 1 tick
                            if (finalPlayer.isOnline()) {
                                // Folia: teleportAsync é ASSÍNCRONO. Se spawnarmos o barco na
                                // posição atual antes do teleport concluir, o jogador monta no
                                // barco novo dentro da região de reset, o teleport falha (player
                                // dentro de veículo) e o processo se repete em loop até sair da
                                // região. Por isso spawnamos o barco SOMENTE depois do teleport,
                                // e no local de DESTINO.
                                SchedulerHelper.teleportAsync(finalPlayer, finalTargetLoc).thenAccept(success -> {
                                    if (Boolean.TRUE.equals(success) && finalPlayer.isOnline()) {
                                        finalPlayer.playSound(finalTargetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                                        this.plugin.getAPI().spawnBoatAt(finalPlayer, finalTargetLoc, false, false, false);
                                        // O barco novo nasce com física vanilla: o mod OpenBoatUtils
                                        // perde a configuração da pista quando a entidade do barco
                                        // troca. Reenvia a config da pista (mesmo efeito do /tt e
                                        // do /reset — sem isso o barco ficava vanilla após cair em
                                        // uma região de reset).
                                        if (this.plugin.getPacketSender() != null) {
                                            this.plugin.getPacketSender().applyBoatUtilsToPlayer(finalPlayer, finalTrackWS);
                                        }
                                        finalDebug.logTimeTrialSystem("[RESET] " + finalPlayer.getName() + " -> API Spawn Boat executed (Delayed).");
                                    }
                                });
                            }
                        }, 1L);
                    }
                }

                if (isRunningDuel) {
                    String duelTrackWS = this.timeTrialDuels.getDuelTrackNameCached(activeDuelId);
                    if (regionTrackWS.equalsIgnoreCase(duelTrackWS)) {
                        long now = System.currentTimeMillis();
                        Long lastDuelCross = (Long)this.lastDuelRegionCross.get(uuid);
                        if (lastDuelCross != null && now - lastDuelCross < 500L) {
                            this.plugin.getDebugManager().logRegionDetection("§b[REGION DEBUG] Região ignorada por debounce (< 500ms) para " + player.getName() + " em duelo");
                            return;
                        }

                        this.lastDuelRegionCross.put(uuid, now);
                        int currentLap = this.timeTrialDuels.getPlayerCurrentLap(player, activeDuelId);
                        if (type.equals("END") && currentLap == 0) {
                            this.timeTrialDuels.onPlayerCrossStart(player, activeDuelId);
                        } else if (type.equals("END")) {
                            this.timeTrialDuels.onPlayerCrossStart(player, activeDuelId);
                        } else if (type.equals("START")) {
                            this.timeTrialDuels.onPlayerCrossStart(player, activeDuelId);
                        }
                    } else {
                        this.plugin.getDebugManager().logDuelSystemVerbose("[REGION] " + player.getName() + " em duelo #" + activeDuelId + " cruzou " + type + " de " + regionTrackDisplayName + " (WS: " + regionTrackWS + ") mas pista do duelo é " + duelTrackWS + " - IGNORADO");
                    }

                } else {
                    if (this.plugin.getRaceEventManager() != null) {
                        boolean foundInHeat = false;

                        label271:
                        for(Events event : this.plugin.getRaceEventManager().getAllEvents()) {
                            Map<Integer, Rounds> allRounds = event.getEventSchedule().getRounds();
                            Iterator lastWarning = allRounds.values().iterator();

                            while(true) {
                                if (lastWarning.hasNext()) {
                                    Rounds round = (Rounds)lastWarning.next();
                                    Optional<Heats> activeHeat = round.getActiveHeat();
                                    if (activeHeat.isEmpty()) {
                                        continue;
                                    }

                                    Heats heat = (Heats)activeHeat.get();
                                    HeatState heatState = heat.getHeatState();
                                    Driver driver = heat.getDriver(uuid);
                                    if (driver == null) {
                                        continue;
                                    }

                                    foundInHeat = true;
                                    if (!driver.isFinished() && !driver.isDnf() && (heatState == HeatState.RACING || heatState == HeatState.STARTING || heatState == HeatState.PRACTICE || heatState == HeatState.QUALIFYING)) {
                                        this.plugin.getDebugManager().logRaceSystem(String.format("[HEAT] %s - In race (Heat %s, State: %s, type=%s)", player.getName(), heat.getName(), heatState, type));
                                        if ((type.equals("START") || type.equals("END")) && (heatState == HeatState.RACING || heatState == HeatState.PRACTICE || heatState == HeatState.QUALIFYING)) {
                                            this.plugin.getDebugManager().logRaceSystem(String.format("[HEAT] %s - Chamando handleRaceLapCrossing", player.getName()));
                                            this.handleRaceLapCrossing(player, driver, heat, from, to, region);
                                        } else {
                                            this.plugin.getDebugManager().logRaceSystem(String.format("[HEAT] %s - Ignorado (type=%s, state=%s)", player.getName(), type, heatState));
                                        }

                                        return;
                                    }
                                }

                                if (foundInHeat) {
                                    break label271;
                                }
                                break;
                            }
                        }
                    }

                    String lastDuelTrack = this.plugin.getLastDuelTrack(uuid);
                    boolean justFinishedDuelOnThisTrack = lastDuelTrack != null && lastDuelTrack.equalsIgnoreCase(regionTrackDisplayName);
                    boolean shouldLoop = false;
                    if (type.equals("START")) {
                        shouldLoop = true;
                    } else if (type.equals("END")) {
                        DatabaseManager.RegionData startRegionOfTrack = null;

                        for(DatabaseManager.RegionData rd : this.database.getAllRegions()) {
                            if (rd.getTrackNameWS().equalsIgnoreCase(regionTrackWS) && rd.getType().equalsIgnoreCase("START")) {
                                startRegionOfTrack = rd;
                                break;
                            }
                        }

                        if (startRegionOfTrack != null) {
                            boolean sameLocation = startRegionOfTrack.getWorld().equals(region.getWorld()) && startRegionOfTrack.getMinX() == region.getMinX() && startRegionOfTrack.getMinY() == region.getMinY() && startRegionOfTrack.getMinZ() == region.getMinZ() && startRegionOfTrack.getMaxX() == region.getMaxX() && startRegionOfTrack.getMaxY() == region.getMaxY() && startRegionOfTrack.getMaxZ() == region.getMaxZ();
                            if (sameLocation) {
                                shouldLoop = true;
                                this.plugin.getDebugManager().logTimeTrialSystem("[LOOP] Circuito detectado (START == END). O timer será reiniciado.");
                            }
                        }
                    }

                    if (!isRunningDuel && ttEnabled && justFinishedDuelOnThisTrack) {
                        if (type.equals("START") || type.equals("END")) {
                            this.plugin.getDebugManager().logTimeTrialSystem("[AUTO TT] " + player.getName() + " - Auto-iniciando TT após duelo");
                            this.plugin.clearLastDuelTrack(uuid);
                            this.handleSoloTimeTrial(player, regionTrackDisplayName, regionTrackWS, type, from, to, region, shouldLoop, crossingNanos);
                        }
                    } else if (!isRunningDuel && ttEnabled) {
                        if (type.equals("START") || type.equals("END")) {
                            this.handleSoloTimeTrial(player, regionTrackDisplayName, regionTrackWS, type, from, to, region, shouldLoop, crossingNanos);
                        }
                    } else if (isRunningDuel) {
                        DebugManager var66 = this.plugin.getDebugManager();
                        String var69 = player.getName();
                        var66.logTimeTrialSystem(var69 + " is in duel #" + activeDuelId + ", solo time trial blocked");
                    } else if (!ttEnabled && justFinishedDuelOnThisTrack) {
                        this.plugin.getDebugManager().logTimeTrialSystem(player.getName() + " had TT disabled before duel, not auto-starting");
                        this.plugin.clearLastDuelTrack(uuid);
                    } else if (!ttEnabled) {
                        this.plugin.getDebugManager().logTimeTrialSystem(player.getName() + " does not have time trial enabled");
                        if (type.equals("START") || type.equals("END")) {
                            long now = System.currentTimeMillis();
                            Long lastWarning = (Long)this.lastTTDisabledWarning.get(uuid);
                            if (lastWarning == null || now - lastWarning > 60000L) {
                                this.lastTTDisabledWarning.put(uuid, now);
                                this.plugin.sendMessage(player, "region_tt_disabled", new String[]{"{track}", regionTrackDisplayName});
                                this.plugin.sendMessage(player, "region_tt_enable_hint", new String[0]);
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                            }
                        }
                    }

                }
            }
        }
    }

    private void handleSoloTimeTrial(
        Player player,
        String regionTrackDisplayName,
        String regionTrackWS,
        String type,
        Location from,
        Location to,
        DatabaseManager.RegionData region,
        boolean shouldLoop,
        long crossingNanos
    ) {
        UUID uuid = player.getUniqueId();

        if (this.plugin.getDriverLookup().isRacing(uuid)) {
            return;
        }
        if (this.plugin.getQuickRaceManager() != null && this.plugin.getQuickRaceManager().isPlayerInActiveRace(uuid)) {
            return;
        }
        if (this.timeTrialDuels.isPlayerInDuel(uuid)) {
            return;
        }

        String lang_code = this.database.getPlayerLanguage(uuid);
        long now = System.currentTimeMillis();
        Long lastCross = (Long)this.lastStartEndCross.get(uuid);
        if (lastCross != null && now - lastCross < 2000L) {
            this.plugin.getDebugManager().logTimeTrialSystem("Ignorando cruz de " + type + " por debounce (< 2s) para " + player.getName());
        } else {
            this.lastStartEndCross.put(uuid, now);
            boolean isRunningSolo = this.timerUtils.isTimerRunning(player, regionTrackWS);
            this.plugin.getDebugManager().logTimeTrialSystem(String.format("[SOLO TT] %s - handleSoloTimeTrial: isRunning=%b, shouldLoop=%b", player.getName(), isRunningSolo, shouldLoop));
            if (!isRunningSolo) {
                if (shouldLoop) {
                    this.startSoloTimer(
                        player,
                        regionTrackDisplayName,
                        regionTrackWS,
                        type,
                        System.currentTimeMillis(),
                        crossingNanos
                    );
                }
            } else {
                TimerUtils.PlayerTimerData data = this.timerUtils.getTimerData(player, regionTrackWS);
                if (data != null) {
                    int checkpoints = data.getCheckpointsReached();
                    int totalCheckpoints = this.database.getCheckpointCount(regionTrackWS);
                    if (checkpoints >= totalCheckpoints) {
                        this.finishSoloTimeTrial(
                            player,
                            regionTrackDisplayName,
                            regionTrackWS,
                            checkpoints,
                            shouldLoop,
                            crossingNanos
                        );
                    } else {
                        if (totalCheckpoints > 0) {
                            this.plugin.sendMessage(player, "timetrial_incomplete_lap", new String[]{"{count}", String.valueOf(checkpoints), "{total}", String.valueOf(totalCheckpoints)});
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5F, 1.0F);
                            this.timerUtils.stopTimer(player, regionTrackWS);
                            if (this.timingService != null) {
                                this.timingService.abort(uuid, true);
                            }
                            if (shouldLoop) {
                                this.startSoloTimer(
                                    player,
                                    regionTrackDisplayName,
                                    regionTrackWS,
                                    type,
                                    System.currentTimeMillis(),
                                    crossingNanos
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isDrivingBoat(Player player) {
        if (player == null) return false;
        try {
            // No Folia, o estado da entidade só pode ser lido na thread da região dela.
            // Fora dela, usa só isInsideVehicle (estado do player) em vez de tocar
            // no barco, e assume pilotando — o checkPlayerRegions já validou
            // na thread correta antes de chegar aqui.
            if (!Bukkit.isOwnedByCurrentRegion(player)) {
                return player.isInsideVehicle();
            }
            if (!player.isInsideVehicle()) {
                return false;
            }
            if (!(player.getVehicle() instanceof org.bukkit.entity.Boat)) {
                return false;
            }
            // O piloto é o primeiro passageiro; demais são caronas
            java.util.List<org.bukkit.entity.Entity> passengers = player.getVehicle().getPassengers();
            return !passengers.isEmpty() && passengers.get(0).equals(player);
        } catch (IllegalStateException e) {
            return safeInsideVehicle(player);
        }
    }

    private boolean safeInsideVehicle(Player player) {
        try {
            return player.isInsideVehicle();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private boolean isBoatVehicle(Player player) {
        try {
            return player.getVehicle() instanceof org.bukkit.entity.Boat;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private boolean isBedrockPlayer(UUID uuid) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Floodgate") || Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
            }
        } catch (NoClassDefFoundError var3) {
        }

        Player player = Bukkit.getPlayer(uuid);
        return player != null && (player.getName().startsWith("*") || player.getName().startsWith("."));
    }

    private Location normalizeRegionLocation(Location location, boolean bedrockBoatDetection) {
        if (location == null || !bedrockBoatDetection) {
            return location;
        }

        Location adjusted = location.clone();
        adjusted.setY(adjusted.getY() - BEDROCK_REGION_Y_OFFSET);
        return adjusted;
    }

    private Location getDetectionLocation(Player player) {
        if (player == null) {
            return null;
        }

        UUID uuid = player.getUniqueId();
        if (this.isBedrockPlayer(uuid) && player.isInsideVehicle() && player.getVehicle() instanceof Boat) {
            Location boatLocation = player.getVehicle().getLocation();
            return boatLocation;
        }

        return player.getLocation();
    }

    private void startSoloTimer(
        Player player,
        String regionTrackDisplayName,
        String regionTrackWS,
        String type,
        long startTime,
        long startNanos
    ) {
        UUID uuid = player.getUniqueId();
        if (!isDrivingBoat(player)
            || this.plugin.getDriverLookup().isRacing(uuid)
            || (this.plugin.getQuickRaceManager() != null && this.plugin.getQuickRaceManager().isPlayerInActiveRace(uuid))
            || this.timeTrialDuels.isPlayerInDuel(uuid)) {
            return;
        }

        SoloTimingAttempt timingAttempt = this.timingService != null
            ? this.timingService.getAttempt(uuid)
            : null;
        if (this.timingService != null && (timingAttempt == null || !timingAttempt.getTrackName().equalsIgnoreCase(regionTrackWS))) {
            timingAttempt = this.timingService.armForTrack(player, regionTrackWS);
        }
        if (this.timingService != null) {
            timingAttempt = this.timingService.onServerStart(uuid, startNanos);
        }
        UUID runId = timingAttempt != null ? timingAttempt.getRunId() : null;
        long wallStartTime = startTime > 0L
            ? startTime
            : System.currentTimeMillis() - Math.max(0L, System.nanoTime() - startNanos) / 1_000_000L;

        DatabaseManager.TrackData trackData = this.database.getTrackData(regionTrackWS);
        String ownerName = trackData == null ? null : trackData.getOwnerName();
        this.plugin.setLastTimeTrialTrack(uuid, regionTrackDisplayName);
        this.stt.setPlayerTrack(player, regionTrackDisplayName, ownerName);

        TimeTrialSession session = new TimeTrialSession(
            uuid,
            regionTrackWS,
            Instant.ofEpochMilli(wallStartTime),
            startNanos,
            runId == null ? UUID.randomUUID() : runId
        );
        TimeTrialStartEvent event = new TimeTrialStartEvent(player, session);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            if (this.timingService != null && timingAttempt != null) {
                this.timingService.markAttemptFailed(player, timingAttempt);
            }
            return;
        }

        this.timerUtils.startTimerAtNanos(
            player,
            regionTrackWS,
            wallStartTime,
            startNanos,
            runId
        );
        this.timeTrialController.startSession(session);
        this.plugin.applyTrackGameTime(player, regionTrackDisplayName);
        if (this.plugin.getHotbarController() != null) {
            this.plugin.getHotbarController().giveTimeTrialHotbar(player);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
        if (this.plugin.getLonelyController() != null) {
            this.plugin.getLonelyController().updatePlayersVisibility(player);
            this.plugin.getLonelyController().updatePlayerVisibility(player);
        }
        if (this.plugin.getGhostManager() != null) {
            this.plugin.getGhostManager().startRecording(player);
            if (!this.plugin.isBedrockPlayer(player)) {
                this.plugin.getGhostManager().loadGhostAsync(uuid, regionTrackWS, frames -> {
                    if (frames != null && !frames.isEmpty() && player.isOnline()) {
                        this.plugin.getGhostManager().startReplay(player, frames);
                    }
                });
            }
        }
        if (this.plugin.getMedalManager() != null && !this.plugin.isBedrockPlayer(player)) {
            this.plugin.getMedalManager().startMedalReplayIfBetter(player, regionTrackWS);
        }
    }

    private void finishSoloTimeTrial(
        Player player,
        String regionTrackDisplayName,
        String regionTrackWS,
        int checkpoints,
        boolean shouldLoop,
        long finishNanos
    ) {
        UUID uuid = player.getUniqueId();
        TimerUtils.PlayerTimerData timerData = this.timerUtils.getTimerData(player, regionTrackWS);
        if (timerData == null) {
            return;
        }
        TimeTrialSession session = this.timeTrialController.getSession(player);
        long startNanos = timerData.getStartNanoTime();
        List<dev.EfraGroup.formulaRacing.Ghost.GhostFrame> ghostFrames =
            this.plugin.getGhostManager() != null
                ? this.plugin.getGhostManager().stopRecording(player)
                : null;

        CompletableFuture<OfficialTime> resolution;
        if (this.timingService != null) {
            resolution = this.timingService.onServerFinish(player, startNanos, finishNanos);
        } else {
            long elapsedMillis = Math.max(0L, finishNanos - startNanos) / 1_000_000L;
            resolution = CompletableFuture.completedFuture(
                OfficialTime.fromServerMillis(elapsedMillis, null)
            );
        }

        resolution.thenAccept(officialTime -> SchedulerHelper.runAsync(
            this.plugin,
            () -> this.persistSoloTime(
                player,
                regionTrackDisplayName,
                regionTrackWS,
                session,
                checkpoints,
                shouldLoop,
                officialTime,
                ghostFrames
            )
        ));
    }

    private void persistSoloTime(
        Player player,
        String regionTrackDisplayName,
        String regionTrackWS,
        TimeTrialSession session,
        int checkpoints,
        boolean shouldLoop,
        OfficialTime officialTime,
        List<dev.EfraGroup.formulaRacing.Ghost.GhostFrame> ghostFrames
    ) {
        UUID uuid = player.getUniqueId();
        OfficialTime previousBest = this.database.getPlayerBestOfficialTime(uuid, regionTrackWS);
        boolean isPersonalBest = officialTime.isBetterThan(previousBest);
        int oldRank = this.database.getPlayerRank(uuid, regionTrackWS);
        this.database.saveSoloFullTime(
            uuid,
            player.getName(),
            regionTrackWS,
            officialTime,
            checkpoints
        );
        int newRank = this.database.getPlayerRank(uuid, regionTrackWS);

        if (isPersonalBest && ghostFrames != null && !ghostFrames.isEmpty() && this.plugin.getGhostManager() != null) {
            this.plugin.getGhostManager().saveGhostAsync(uuid, regionTrackWS, ghostFrames);
        }
        if (this.plugin.getMedalManager() != null) {
            double displaySeconds = officialTime.getDisplaySeconds();
            this.plugin.getMedalManager().handleLapFinish(player, regionTrackWS, displaySeconds, ghostFrames);
            this.plugin.getMedalManager().checkMedalAchievement(player, regionTrackWS, displaySeconds);
        }

        String langCode = this.database.getPlayerLanguage(uuid);
        SchedulerHelper.runTaskFor(this.plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            TimeTrialFinishEvent event = new TimeTrialFinishEvent(
                player,
                session,
                officialTime.getOfficialMillis(),
                officialTime.displayMillis(),
                officialTime.officialTicks(),
                officialTime.runId(),
                officialTime.source(),
                isPersonalBest
            );
            Bukkit.getPluginManager().callEvent(event);
            player.sendMessage(this.plugin.getTranslation(
                "timetrial_completed",
                langCode,
                new String[]{"{time}", this.formatTime(officialTime.getDisplaySeconds())}
            ));
            if (isPersonalBest) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);
                String rankMessage;
                if (oldRank == 0) {
                    rankMessage = this.plugin.getTranslation("timetrial_new_pb_new_rank", langCode, new String[]{"{rank}", String.valueOf(newRank)});
                } else if (newRank < oldRank) {
                    rankMessage = this.plugin.getTranslation("timetrial_new_pb_improved_rank", langCode, new String[]{"{old}", String.valueOf(oldRank), "{new}", String.valueOf(newRank)});
                } else {
                    rankMessage = this.plugin.getTranslation("timetrial_new_pb_same_rank", langCode, new String[]{"{rank}", String.valueOf(newRank)});
                }
                player.sendMessage(rankMessage);
            }

            this.timerUtils.stopTimer(player, regionTrackWS);
            this.timeTrialController.endSession(player);
            if (shouldLoop) {
                this.startSoloTimer(
                    player,
                    regionTrackDisplayName,
                    regionTrackWS,
                    "START",
                    System.currentTimeMillis(),
                    System.nanoTime()
                );
            }
        });
    }

    public double roundTime(double sec) {
        return (double)Math.round(sec * (double)100.0F) / (double)100.0F;
    }

    public String formatTime(double elapsed) {
        long totalMillis = Math.round(elapsed * (double)1000.0F);
        long minutes = totalMillis / 60000L;
        long seconds = totalMillis % 60000L / 1000L;
        long millis = totalMillis % 1000L;
        return minutes > 0L ? String.format("%02d:%02d.%03d", minutes, seconds, millis) : String.format("%02d.%03d", seconds, millis);
    }

    private DatabaseManager.RegionData getRegionAtLine(Player player, Location from, Location to, List<DatabaseManager.RegionData> worldRegions) {
        String intendedTrack = resolveIntendedTrack(player);
        // Normalize once per check (hot path: runs every 2 ticks per online player).
        String normalizedTrack = intendedTrack == null ? null
                : intendedTrack.replaceAll("\\s+", "").toLowerCase();
        for(DatabaseManager.RegionData r : worldRegions) {
            String type = r.getType().toUpperCase();
            if ((type.equals("START") || type.equals("END") || type.equals("RESET")) && RegionMathUtils.intersectsRegion(from, to, r)) {
                // With an intended track selected, ignore regions from other tracks
                // (they may share the same position and would hijack the time trial).
                if (normalizedTrack != null && !matchesNormalizedTrack(r, normalizedTrack)) {
                    continue;
                }
                return r;
            }
        }

        return null;
    }

    /**
     * The track the player intends to drive on, if any:
     * 1. The track of a running solo timer (time trial in progress);
     * 2. The track last selected via /tt (or the TT menu) — survives until the
     *    player crosses START/END on that track, so overlapping regions on other
     *    tracks cannot hijack the detection.
     * Returns null when the player has no time trial intent — free roam, race
     * heats or duels (whose region handling is track-aware on their own and must
     * keep unfiltered START/END detection, e.g. a heat running on track B while
     * lastTimeTrialTrack still points to a stale /tt track).
     */
    private String resolveIntendedTrack(Player player) {
        if (player == null) {
            return null;
        }
        String activeTrack = this.timerUtils.getActiveTrack(player);
        if (activeTrack != null) {
            return activeTrack;
        }
        // Do not let a stale /tt selection filter regions while the player is in
        // a heat or duel — those modes resolve their own track.
        if (this.plugin.getDriverLookup().isRacing(player.getUniqueId())
                || this.timeTrialDuels.isPlayerInDuel(player.getUniqueId())) {
            return null;
        }
        return this.plugin.getLastTimeTrialTrack(player.getUniqueId());
    }

    private boolean matchesNormalizedTrack(DatabaseManager.RegionData region, String normalizedTrack) {
        if (region == null || normalizedTrack == null) {
            return false;
        }
        if (region.getTrackNameWS() != null && region.getTrackNameWS().equalsIgnoreCase(normalizedTrack)) {
            return true;
        }
        return region.getTrackName() != null
                && region.getTrackName().replaceAll("\\s+", "").equalsIgnoreCase(normalizedTrack);
    }

    private void handleRaceLapCrossing(Player player, Driver driver, Heats heat, Location from, Location to, DatabaseManager.RegionData regionData) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastCross = this.lastStartEndCross.get(uuid);
        if (lastCross == null || now - lastCross >= 2000L) {
            this.lastStartEndCross.put(uuid, now);
            if (!driver.isFinished() && !driver.isDnf()) {
                String trackNameWS = heat.getTrackNameWS();
                boolean hasLagStart = this.plugin.getTrackIntegrationManager().hasLagStartRegion(trackNameWS);
                boolean hasLagEnd = this.plugin.getTrackIntegrationManager().hasLagEndRegion(trackNameWS);
                if (hasLagStart && !driver.hasPassedLagStart()) {
                    this.plugin.getDebugManager().logRaceSystem(String.format("§c[LAP BLOCKED] %s tentou completar volta sem passar LAGSTART", player.getName()));
                    this.plugin.sendMessage(player, "race_checkpoint_missed", new String[]{"{expected}", "LAGSTART"});
                    return;
                }
                if (hasLagEnd && !driver.hasPassedLagEnd()) {
                    this.plugin.getDebugManager().logRaceSystem(String.format("§c[LAP BLOCKED] %s tentou completar volta sem passar LAGEND", player.getName()));
                    this.plugin.sendMessage(player, "race_checkpoint_missed", new String[]{"{expected}", "LAGEND"});
                    return;
                }
                driver.setResetCount(0);
                this.plugin.getDebugManager().logRaceSystem(String.format("[RACE LAP] %s cruzou START/END - Delegando para lógica do Heat logic...", player.getName()));
                Location min = new Location(from.getWorld(), regionData.getMinX(), regionData.getMinY(), regionData.getMinZ());
                Location max = new Location(from.getWorld(), regionData.getMaxX(), regionData.getMaxY(), regionData.getMaxZ());
                RegionBox regionBox = new RegionBox(min, max);
                heat.passLap(driver, from, to, regionBox);
            }
        }
    }

    // --- INITIALIZATION METHODS THAT WERE MISSING ---
    private FRTask regionLoaderTask;
    private FRTask regionCheckerTask;

    private void startRegionLoader() {
        this.regionLoaderTask = SchedulerHelper.runAsyncTimer(this.plugin, () -> {
            List<DatabaseManager.RegionData> allRegions = database.getAllRegions();
            SchedulerHelper.runTask(plugin, () -> {
                Map<String, List<DatabaseManager.RegionData>> newRegionsMap = new HashMap<>();
                for (DatabaseManager.RegionData r : allRegions) {
                    String world = r.getWorld().toLowerCase();
                    newRegionsMap.computeIfAbsent(world, k -> new ArrayList<>()).add(r);
                }
                regions.clear();
                regions.putAll(newRegionsMap);
            });
        }, 0L, 1200L);
    }


    private void startRegionChecker() {
        this.regionCheckerTask = SchedulerHelper.runTaskTimer(this.plugin, scheduledTask -> {
            if (regions.isEmpty()) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                    if (!player.isOnline()) return;
                    if (player.getVehicle() instanceof Boat) {
                        this.checkPlayerRegions(player);
                    }
                });
            }
        }, 0L, 2L);
    }

    public void shutdown() {
        if (regionLoaderTask != null && !regionLoaderTask.isCancelled()) {
            regionLoaderTask.cancel();
            regionLoaderTask = null;
        }
        if (regionCheckerTask != null && !regionCheckerTask.isCancelled()) {
            regionCheckerTask.cancel();
            regionCheckerTask = null;
        }
    }

}

