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
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
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
    private final Map<UUID, String> playerRegion = new ConcurrentHashMap<>();
    private final Map<String, List<DatabaseManager.RegionData>> regions = new ConcurrentHashMap<>();
    private final Set<String> warnedWorlds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();
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
        this.lastStartEndCross.remove(uuid);
        this.lastDuelRegionCross.remove(uuid);
        this.lastTimeLimitLog.remove(uuid);
        this.justTeleported.remove(uuid);
        this.lastTTDisabledWarning.remove(uuid);
    }

    public void cleanupHeatPlayers(java.util.Collection<UUID> uuids) {
        for (UUID uuid : uuids) {
            this.lastLocation.remove(uuid);
            this.lastStartEndCross.remove(uuid);
            this.lastDuelRegionCross.remove(uuid);
            this.lastTimeLimitLog.remove(uuid);
            this.justTeleported.remove(uuid);
            this.lastTTDisabledWarning.remove(uuid);
        }
    }

    public RegionListener(FormulaRacing plugin, DatabaseManager database, TimerUtils timerUtils, PacketSender packetSender, ScoreboardTimeTrialUtils stt, TimeTrialDuelsAction DuelsTimer, TimeTrialDuels timeTrialDuels, TimeTrialController timeTrialController) {
        this.plugin = plugin;
        this.database = database;
        this.timerUtils = timerUtils;
        this.packetSender = packetSender;
        this.stt = stt;
        this.DuelsTimer = DuelsTimer;
        this.timeTrialDuels = timeTrialDuels;
        this.timeTrialController = timeTrialController;
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
                    this.plugin.getDebugManager().logRegionDetection("§c[TELEPORT] Cancelado pelo RegionListener (Duel Protection). Cause: " + String.valueOf(event.getCause()));
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
        this.plugin.getDebugManager().logRegionDetection("§a[RegionListener] Regiões recarregadas manualmente");
    }
    public void debugListRegions(String worldFilter, String trackFilter) {
        this.plugin.getDebugManager().logRegionDetection("§6========== REGIÕES CARREGADAS ==========");

        this.regions.forEach((world, regionList) -> {
            // Filtro de Mundo
            if (worldFilter != null && !world.equalsIgnoreCase(worldFilter)) return;

            this.plugin.getDebugManager().logRegionDetection("§e[MUNDO: " + world + "]");

            for (DatabaseManager.RegionData region : regionList) {
                String type = region.getType().toUpperCase();
                String trackName = region.getTrackName();

                // Filtro de Tipo (START/END) e Nome da Pista
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

                    // Alerta de colisão/detecção
                    if (height < 3.0) {
                        this.plugin.getDebugManager().logRegionDetection("    §c⚠ REGIÃO MUITO FINA! Altura Y < 3 blocos pode falhar na detecção.");
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
            Location previousRaw = this.lastLocation.get(uuid);
            Location current = currentRaw;
            Location previous = previousRaw;

            // Se for Bedrock e estiver em barco, usar posição do barco
            if (bedrockBoatDetection) {
                current = this.normalizeRegionLocation(player.getVehicle().getLocation(), true);
                if (previous != null) {
                    previous = this.normalizeRegionLocation(previous, true);
                }

            }

            this.lastLocation.put(uuid, currentRaw);
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
                            // DEBUG: região mais próxima
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

                            // Checar se cruzou região START/END
DatabaseManager.RegionData startEndRegion = this.getRegionAtLine(previous, current, worldRegions);
                                if (startEndRegion != null) {
                                    Location finalFrom = previous.clone();
                                    Location finalTo = current.clone();
                                    SchedulerHelper.runTaskFor(this.plugin, player, () -> this.handleRegion(player, startEndRegion, finalFrom, finalTo));
                                }

                            // Lógica de checkpoints e duelos
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
                                                    this.plugin.getDebugManager().logRegionDetection(player.getName() + " coletou checkpoint na pista " + activeTrack);
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
                                                this.plugin.getDebugManager().logRegionDetection("§e[TIME LIMIT] " + player.getName() + " limite de tempo excedido.");
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
                                this.plugin.getDebugManager().logRegionDetection("[FormulaRacing] Nenhuma região registrada para o mundo " + worldName);
                            }

                        }
                    }
                }
            }
        }
    }

    private void handleRegion(Player player, DatabaseManager.RegionData region, Location from, Location to) {
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
                                List<DatabaseManager.RegionData> checkpointByIdList = this.plugin.getTrackIntegrationManager().getCheckpointById(regionTrackWS, checkpointsReached - 1);
                                if (driver.getResetCount() == 0 && checkpointsReached > 0 && checkpointByIdList != null && !checkpointByIdList.isEmpty()) {
                                    DatabaseManager.RegionData cp = checkpointByIdList.get(0);
                                    targetLoc = new Location(Bukkit.getWorld(cp.getWorld()), (cp.getMinX() + cp.getMaxX()) / (double)2.0F, cp.getMaxY() - (double)0.5F, (cp.getMinZ() + cp.getMaxZ()) / (double)2.0F, player.getLocation().getYaw(), player.getLocation().getPitch());
                                    driver.incrementResetCount();
                                    DebugManager var10000 = this.plugin.getDebugManager();
                                    String var10001 = player.getName();
                                    var10000.logRaceSystem("[RESET-HEAT] " + var10001 + " -> CP " + checkpointsReached + " (resetCount=" + driver.getResetCount() + ")");
                                } else if (driver.getResetCount() >= 1) {
                                    targetLoc = this.plugin.getTrackIntegrationManager().getTrackSpawn(regionTrackWS);
                                    DebugManager var10000 = this.plugin.getDebugManager();
                                    String var10001 = player.getName();
                                    var10000.logRaceSystem("[RESET-HEAT] " + var10001 + " -> Track Spawn (resetCount=" + driver.getResetCount() + ")");
                                }
                            }
                        }

                        if (targetLoc == null) {
                            String activeTrackKey = this.timerUtils.getActiveTrack(player);
                            if (activeTrackKey != null) {
                                TimerUtils.PlayerTimerData data = this.timerUtils.getTimerData(player, activeTrackKey);
                                if (data != null) {
                                    regionTrackWS = activeTrackKey;
                                    int checkpointsReached = data.getCheckpointsReached();
                                    List<DatabaseManager.RegionData> checkpointByIdList = this.plugin.getTrackIntegrationManager().getCheckpointById(activeTrackKey, checkpointsReached - 1);
                                    if (checkpointsReached > 0 && checkpointByIdList != null && !checkpointByIdList.isEmpty()) {
                                        DatabaseManager.RegionData cp = checkpointByIdList.get(0);
                                        targetLoc = new Location(Bukkit.getWorld(cp.getWorld()), (cp.getMinX() + cp.getMaxX()) / (double)2.0F, cp.getMaxY() - (double)0.5F, (cp.getMinZ() + cp.getMaxZ()) / (double)2.0F, player.getLocation().getYaw(), player.getLocation().getPitch());
                                        DebugManager var64 = this.plugin.getDebugManager();
                                        String var67 = player.getName();
                                        var64.logTimeTrialSystem("[RESET-SOLO] " + var67 + " -> CP " + checkpointsReached);
                                    }
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

// Log do agendamento do Reset
                        debug.logTimeTrialSystem(String.format("[RESET] Scheduled Teleport for %s to: World=%s, X=%d, Y=%d, Z=%d",
                                player.getName(), worldName, targetLoc.getBlockX(), targetLoc.getBlockY(), targetLoc.getBlockZ()));

// Executa o teleporte e o spawn do barco 1 tick depois para evitar bugs de colisão/NMS
                        // Criamos referências finais para garantir que a Lambda possa capturá-las sem erro
                        final DebugManager finalDebug = this.plugin.getDebugManager();
                        final Location finalTargetLoc = targetLoc;
                        final Player finalPlayer = player;

                        SchedulerHelper.runTaskLater(this.plugin, () -> {
                            // Verificamos se o jogador ainda está online após o delay de 1 tick
                            if (finalPlayer.isOnline()) {
                                // Teleporte e efeito sonoro usando as referências finais
                                SchedulerHelper.teleport(finalPlayer, finalTargetLoc);
                                finalPlayer.playSound(finalTargetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);

                                // Gera o barco através da API
                                this.plugin.getAPI().spawnBoat(finalPlayer, false, false, false);

                                finalDebug.logTimeTrialSystem("[RESET] " + finalPlayer.getName() + " -> API Spawn Boat executed (Delayed).");
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
                                        this.plugin.getDebugManager().logRaceSystem(String.format("[HEAT] %s - BLOQUEADO: Em corrida oficial (Heat %s, Estado: %s)", player.getName(), heat.getName(), heatState));
                                        if ((type.equals("START") || type.equals("END")) && (heatState == HeatState.RACING || heatState == HeatState.PRACTICE || heatState == HeatState.QUALIFYING)) {
                                            this.handleRaceLapCrossing(player, driver, heat, from, to, region);
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
                            this.handleSoloTimeTrial(player, regionTrackDisplayName, regionTrackWS, type, from, to, region, shouldLoop);
                        }
                    } else if (!isRunningDuel && ttEnabled) {
                        if (type.equals("START") || type.equals("END")) {
                            this.handleSoloTimeTrial(player, regionTrackDisplayName, regionTrackWS, type, from, to, region, shouldLoop);
                        }
                    } else if (isRunningDuel) {
                        DebugManager var66 = this.plugin.getDebugManager();
                        String var69 = player.getName();
                        var66.logTimeTrialSystem(var69 + " está em duelo #" + activeDuelId + ", time trial solo bloqueado");
                    } else if (!ttEnabled && justFinishedDuelOnThisTrack) {
                        this.plugin.getDebugManager().logTimeTrialSystem(player.getName() + " tinha TT desabilitado antes do duelo, não auto-iniciando");
                        this.plugin.clearLastDuelTrack(uuid);
                    } else if (!ttEnabled) {
                        this.plugin.getDebugManager().logTimeTrialSystem(player.getName() + " não tem time trial habilitado");
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

    private void handleSoloTimeTrial(Player player, String regionTrackDisplayName, String regionTrackWS, String type, Location from, Location to, DatabaseManager.RegionData region, boolean shouldLoop) {
        UUID uuid = player.getUniqueId();
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
                    double proportion = RegionMathUtils.calculateRegionEntryProportion(from, to, region);
                    long tickDurationMs = 50L;
                    long adjustmentMs = (long)(((double)1.0F - proportion) * (double)tickDurationMs);
                    long preciseTime = System.currentTimeMillis() - adjustmentMs;
                    this.startSoloTimer(player, regionTrackDisplayName, regionTrackWS, type, preciseTime);
                }
            } else {
                TimerUtils.PlayerTimerData data = this.timerUtils.getTimerData(player, regionTrackWS);
                if (data != null) {
                    double rawElapsed = this.timerUtils.getPlayerElapsedTime(player, regionTrackWS);
                    int checkpoints = data.getCheckpointsReached();
                    int totalCheckpoints = this.database.getCheckpointCount(regionTrackWS);
                    if (checkpoints >= totalCheckpoints) {
                        double proportion = RegionMathUtils.calculateRegionEntryProportion(from, to, region);
                        long tickDurationMs = 50L;
                        long adjustmentMs = (long)(((double)1.0F - proportion) * (double)tickDurationMs);
                        long preciseFinishTime = System.currentTimeMillis() - adjustmentMs;
                        TimeTrialSession session = this.timeTrialController.getSession(player);
                        long totalTimeMillis = session != null ? preciseFinishTime - session.getStartTime().toEpochMilli() : (long)(rawElapsed * (double)1000.0F);
                        double preciseElapsedSeconds = (double)totalTimeMillis / (double)1000.0F;
                        SchedulerHelper.runAsync(this.plugin, () -> {
                            Object[] pb = this.database.getPlayerBestTime(player.getName(), regionTrackWS);
                            double bestTime = pb != null && pb[0] != null ? (Double)pb[0] : Double.MAX_VALUE;
                            boolean isPB = preciseElapsedSeconds < bestTime;
                            SchedulerHelper.runTask(this.plugin, () -> {
                                TimeTrialFinishEvent event = new TimeTrialFinishEvent(player, session, totalTimeMillis, isPB);
                                Bukkit.getPluginManager().callEvent(event);
                            });
                            int oldRank = this.database.getPlayerRank(uuid, regionTrackWS);
                            this.database.saveFullTime(uuid, player.getName(), regionTrackWS, preciseElapsedSeconds, checkpoints);
                            int newRank = this.database.getPlayerRank(uuid, regionTrackWS);
                            SchedulerHelper.runTask(this.plugin, () -> {
                                if (player.isOnline()) {
                                    String msg = this.plugin.getTranslation("timetrial_completed", lang_code, new String[]{"{time}", this.formatTime(preciseElapsedSeconds)});
                                    player.sendMessage(msg);
                                    if (isPB) {
                                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);
                                        String rankMessage;
                                        if (oldRank == 0) {
                                            rankMessage = this.plugin.getTranslation("timetrial_new_pb_new_rank", lang_code, new String[]{"{rank}", String.valueOf(newRank)});
                                        } else if (newRank < oldRank) {
                                            rankMessage = this.plugin.getTranslation("timetrial_new_pb_improved_rank", lang_code, new String[]{"{old}", String.valueOf(oldRank), "{new}", String.valueOf(newRank)});
                                        } else {
                                            rankMessage = this.plugin.getTranslation("timetrial_new_pb_same_rank", lang_code, new String[]{"{rank}", String.valueOf(newRank)});
                                        }

                                        player.sendMessage(rankMessage);
                                    }

                                    this.plugin.getDebugManager().logTimeTrialSystem(String.format("[SOLO TT] %s completou volta na pista %s (%s) em %s", player.getName(), regionTrackDisplayName, regionTrackWS, this.formatTime(preciseElapsedSeconds)));
                                    this.timerUtils.stopTimer(player, regionTrackWS);
                                    if (shouldLoop) {
                                        SchedulerHelper.runAsync(this.plugin, () -> {
                                            this.timerUtils.reloadCacheAsync(player, regionTrackWS);
                                            SchedulerHelper.runTask(this.plugin, () -> this.startSoloTimer(player, regionTrackDisplayName, regionTrackWS, "START", System.currentTimeMillis()));
                                        });
                                    } else {
                                        this.plugin.getDebugManager().logTimeTrialSystem("[SOLO TT] Sprint finalizado. Timer parado.");
                                    }

                                }
                            });
                        });
                    } else {
                        if (totalCheckpoints <= 0) {
                            return;
                        }

                        this.plugin.sendMessage(player, "timetrial_incomplete_lap", new String[]{"{count}", String.valueOf(checkpoints), "{total}", String.valueOf(totalCheckpoints)});
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5F, 1.0F);
                        this.timerUtils.stopTimer(player, regionTrackWS);
                        if (shouldLoop) {
                            double proportion = RegionMathUtils.calculateRegionEntryProportion(from, to, region);
                            long tickDurationMs = 50L;
                            long adjustmentMs = (long)(((double)1.0F - proportion) * (double)tickDurationMs);
                            long preciseTime = System.currentTimeMillis() - adjustmentMs;
                            this.startSoloTimer(player, regionTrackDisplayName, regionTrackWS, type, preciseTime);
                        }
                    }

                }
            }
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
        return player != null && player.getName().startsWith("*");
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

    private void startSoloTimer(Player player, String regionTrackDisplayName, String regionTrackWS, String type, long startTime) {
        UUID uuid = player.getUniqueId();
        String ownerName = null;
        DatabaseManager.TrackData td = this.database.getTrackData(regionTrackWS);
        if (td != null) {
            ownerName = td.getOwnerName();
        }

        this.plugin.setLastTimeTrialTrack(uuid, regionTrackDisplayName);
        this.stt.setPlayerTrack(player, regionTrackDisplayName, ownerName);
        this.plugin.getDebugManager().logTimeTrialSystem(String.format("[SOLO TT] %s - startSoloTimer: track=%s (%s), type=%s", player.getName(), regionTrackDisplayName, regionTrackWS, type));
        TimeTrialSession session = new TimeTrialSession(uuid, regionTrackWS, Instant.ofEpochMilli(startTime));
        TimeTrialStartEvent event = new TimeTrialStartEvent(player, session);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            this.timerUtils.startTimer(player, regionTrackWS, startTime);
            this.timeTrialController.startSession(player, regionTrackWS, session.getStartTime());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
            if (this.plugin.getLonelyController() != null) {
                this.plugin.getLonelyController().updatePlayersVisibility(player);
                this.plugin.getLonelyController().updatePlayerVisibility(player);
            }

            int totalCheckpoints = this.database.getCheckpointCount(regionTrackWS);
            this.plugin.getDebugManager().logTimeTrialSystem(String.format("[SOLO TT] %s iniciou/resetou timer na pista %s (%s) (via %s, CPs: %d)", player.getName(), regionTrackDisplayName, regionTrackWS, type, totalCheckpoints));
        }
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

    private DatabaseManager.RegionData getRegionAtLine(Location from, Location to, List<DatabaseManager.RegionData> worldRegions) {
        for(DatabaseManager.RegionData r : worldRegions) {
            String type = r.getType().toUpperCase();
            if ((type.equals("START") || type.equals("END") || type.equals("RESET")) && RegionMathUtils.intersectsRegion(from, to, r)) {
                return r;
            }
        }

        return null;
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

    // --- MÉTODOS DE INICIALIZAÇÃO QUE ESTAVAM FALTANDO ---

    private void startRegionLoader() {
        SchedulerHelper.runAsyncTimer(this.plugin, () -> {
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
        SchedulerHelper.runTaskTimer(this.plugin, scheduledTask -> {
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

}
