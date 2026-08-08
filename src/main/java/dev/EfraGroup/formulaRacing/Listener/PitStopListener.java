package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.Database.Track;
import dev.EfraGroup.formulaRacing.Event.EventSchedule;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.PitStopManager;
import dev.EfraGroup.formulaRacing.Heat.PitStopMinigame;
import dev.EfraGroup.formulaRacing.Heat.PitStopRegion;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.TimeTrial.TimeTrialSession;
import dev.EfraGroup.formulaRacing.TimeTrial.Events.TimeTrialFinishEvent;
import dev.EfraGroup.formulaRacing.TimeTrial.Events.TimeTrialStartEvent;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.RegionMathUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public class PitStopListener implements Listener {
    private final FormulaRacing plugin;
    private final RaceEventManager raceEventManager;
    private final PitStopManager pitStopManager;
    private final Map<UUID, Boolean> wasInPitStopStart;
    private final Map<UUID, Boolean> wasInPitStopEntry;
    private final Map<UUID, Boolean> wasInPitStopExit;
    private final Map<UUID, Boolean> wasInMinigameArea;
    private final Map<UUID, Long> pitAreaExitTime;
    private final Map<UUID, Long> lastPitStartCross;
    private static final long REGION_EXIT_GRACE_MS = 400L;

    public PitStopListener(FormulaRacing plugin, RaceEventManager raceEventManager, PitStopManager pitStopManager) {
        this.plugin = plugin;
        this.raceEventManager = raceEventManager;
        this.pitStopManager = pitStopManager;
        this.wasInPitStopEntry = new HashMap();
        this.wasInPitStopExit = new HashMap();
        this.wasInMinigameArea = new HashMap();
        this.pitAreaExitTime = new HashMap();
        this.wasInPitStopStart = new HashMap();
        this.lastPitStartCross = new HashMap();
    }

    private Heats findActiveHeat(UUID playerId) {
        for(Events eve : this.raceEventManager.getAllEvents()) {
            EventSchedule schedule = eve.getEventSchedule();

            for(Rounds round : schedule.getRounds().values()) {
                for(Heats h : round.getHeats().values()) {
                    if (h.getHeatState() == HeatState.RACING && h.getDriver(playerId) != null) {
                        return h;
                    }
                }
            }
        }

        return null;
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!(vehicle instanceof Boat)) return;
        if (vehicle.getPassengers().isEmpty()) return;

        Object passenger = vehicle.getPassengers().get(0);
        if (!(passenger instanceof Player player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Heats heat = this.findActiveHeat(uuid);

        Location location = vehicle.getLocation();
        boolean isInEntry = false;
        boolean isInStart = this.pitStopManager.getPitStopStartAtLocation(location) != null;
        boolean isInExit = false;
        boolean isInMinigameArea = false;
        String trackNameWS = null;

        if (heat != null) {
            trackNameWS = heat.getTrackNameWS();
            isInEntry = this.pitStopManager.getPitStopEntryAtLocation(location) != null;
            isInExit = this.pitStopManager.getPitStopExitAtLocation(location) != null;
            isInMinigameArea = this.pitStopManager.isValidPitStopLocation(location, trackNameWS);
        }

        if (isInStart && !(Boolean)this.wasInPitStopStart.getOrDefault(uuid, false)) {
            this.onPitLapTrigger(player, heat, from, to);
            this.wasInPitStopStart.put(uuid, true);
        } else if (!isInStart) {
            this.wasInPitStopStart.put(uuid, false);
        }

        if (heat == null) {
            return; // entry/exit/minigame only apply to race heats
        }

        if (isInEntry && !(Boolean)this.wasInPitStopEntry.getOrDefault(uuid, false)) {
            this.onEnterPitStopEntry(player, heat);
            this.wasInPitStopEntry.put(uuid, true);
        } else if (!isInEntry) {
            this.wasInPitStopEntry.put(uuid, false);
        }

        this.processMinigameLogic(player, heat, isInMinigameArea, trackNameWS);

        if (isInExit && !(Boolean)this.wasInPitStopExit.getOrDefault(uuid, false)) {
            this.onEnterPitStopExit(player, heat);
            this.wasInPitStopExit.put(uuid, true);
        } else if (!isInExit) {
            this.wasInPitStopExit.put(uuid, false);
        }
    }

    private void onPitLapTrigger(Player player, Heats heat, Location from, Location to) {
        UUID uuid = player.getUniqueId();
        this.plugin.getDebugManager().logPitStopSystem("[PIT] " + player.getName() + " passou pela linha de START do pit.");

        String trackNameWS;
        RegionBox regionBox;

        if (heat != null) {
            // --- Race heat logic ---
            Driver driver = heat.getDriver(uuid);
            if (driver == null) return;

            long now = System.currentTimeMillis();
            Long lastCross = this.lastPitStartCross.get(uuid);
            if (lastCross != null && now - lastCross < 2000L) return;
            this.lastPitStartCross.put(uuid, now);

            if (driver.isFinished() || driver.isDnf()) return;

            trackNameWS = heat.getTrackNameWS();
            PitStopRegion pitStopRegion = this.pitStopManager.getPitStop(trackNameWS);
            if (pitStopRegion == null || !pitStopRegion.hasStart()) return;
            regionBox = pitStopRegion.getStartRegion();
            if (regionBox == null) return;

            driver.setResetCount(0);
            this.plugin.getDebugManager().logPitStopSystem("[PIT] " + player.getName() + " a completar volta (heat) via START do pit.");
            heat.passLap(driver, from, to, regionBox);
            return;
        }

        // --- Time Trial logic ---
        TimerUtils timerUtils = this.plugin.getTimerUtils();
        trackNameWS = timerUtils.getActiveTrack(player);
        if (trackNameWS == null) return;

        DatabaseManager db = this.plugin.getDatabaseManager();
        if (!db.getTimeTrialEnabled(uuid)) return;

        PitStopRegion pitStopRegion = this.pitStopManager.getPitStop(trackNameWS);
        if (pitStopRegion == null || !pitStopRegion.hasStart()) return;
        regionBox = pitStopRegion.getStartRegion();
        if (regionBox == null) return;

        long now = System.currentTimeMillis();
        Long lastCross = this.lastPitStartCross.get(uuid);
        if (lastCross != null && now - lastCross < 2000L) return;
        this.lastPitStartCross.put(uuid, now);

        double proportion = RegionMathUtils.calculateRegionEntryProportion(from, to, regionBox);
        long adjustmentMs = (long) ((1.0 - proportion) * 50.0);
        long preciseTime = now - adjustmentMs;

        boolean isRunning = timerUtils.isTimerRunning(player, trackNameWS);
        this.plugin.getDebugManager().logPitStopSystem("[PIT TT] " + player.getName() + " - isRunning=" + isRunning);

        if (!isRunning) {
            // Start timer (begin lap)
            this.startTimeTrialTimer(player, trackNameWS, preciseTime);
            return;
        }

        // Timer is running — finish lap or incomplete
        TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player, trackNameWS);
        if (data == null) return;

        int checkpoints = data.getCheckpointsReached();
        int totalCheckpoints = db.getCheckpointCount(trackNameWS);

        if (checkpoints >= totalCheckpoints) {
            // Complete lap with precise timing
            double rawElapsed = timerUtils.getPlayerElapsedTime(player, trackNameWS);
            TimeTrialSession session = this.plugin.getTimeTrialController().getSession(player);
            long totalTimeMillis = session != null
                    ? preciseTime - session.getStartTime().toEpochMilli()
                    : (long) (rawElapsed * 1000.0);
            double preciseElapsedSeconds = (double) totalTimeMillis / 1000.0;
            String langCode = db.getPlayerLanguage(uuid);

            // --- Ghost System: stop recording and capture frames (PIT finish) ---
            final List<dev.EfraGroup.formulaRacing.Ghost.GhostFrame> ghostFrames =
                    this.plugin.getGhostManager() != null
                            ? this.plugin.getGhostManager().stopRecording(player)
                            : null;

            // --- Medal record: capture lap for /te medals record ---
            if (this.plugin.getMedalManager() != null) {
                this.plugin.getMedalManager().handleLapFinish(player, trackNameWS, preciseElapsedSeconds, ghostFrames);
                // Announce when the lap achieves a diamond/netherite/saphira medal
                this.plugin.getMedalManager().checkMedalAchievement(player, trackNameWS, preciseElapsedSeconds);
            }

            SchedulerHelper.runAsync(this.plugin, () -> {
                Object[] pb = db.getPlayerBestTime(player.getName(), trackNameWS);
                double bestTime = pb != null && pb[0] != null ? (Double) pb[0] : Double.MAX_VALUE;
                boolean isPB = preciseElapsedSeconds < bestTime;

                SchedulerHelper.runTask(this.plugin, () -> {
                    if (session != null) {
                        TimeTrialFinishEvent event = new TimeTrialFinishEvent(player, session, totalTimeMillis, isPB);
                        Bukkit.getPluginManager().callEvent(event);
                    }
                });

                int oldRank = db.getPlayerRank(uuid, trackNameWS);
                db.saveFullTime(uuid, player.getName(), trackNameWS, preciseElapsedSeconds, checkpoints);
                int newRank = db.getPlayerRank(uuid, trackNameWS);

                SchedulerHelper.runTask(this.plugin, () -> {
                    if (!player.isOnline()) return;

                    String msg = this.plugin.getTranslation("timetrial_completed", langCode,
                            new String[]{"{time}", this.formatTimeTrialTime(preciseElapsedSeconds)});
                    player.sendMessage(msg);
                    if (isPB) {
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);
                        String rankMessage;
                        if (oldRank == 0) {
                            rankMessage = this.plugin.getTranslation("timetrial_new_pb_new_rank", langCode,
                                    new String[]{"{rank}", String.valueOf(newRank)});
                        } else if (newRank < oldRank) {
                            rankMessage = this.plugin.getTranslation("timetrial_new_pb_improved_rank", langCode,
                                    new String[]{"{old}", String.valueOf(oldRank), "{new}", String.valueOf(newRank)});
                        } else {
                            rankMessage = this.plugin.getTranslation("timetrial_new_pb_same_rank", langCode,
                                    new String[]{"{rank}", String.valueOf(newRank)});
                        }
                        player.sendMessage(rankMessage);
                    }

                    this.plugin.getDebugManager().logPitStopSystem(
                            "[PIT TT] " + player.getName() + " completou volta na pista " + trackNameWS
                                    + " em " + this.formatTimeTrialTime(preciseElapsedSeconds));
                    timerUtils.stopTimer(player, trackNameWS);

                    // Auto-loop (pit start behaves like START region — always loop)
                    SchedulerHelper.runAsync(this.plugin, () -> {
                        timerUtils.reloadCacheAsync(player, trackNameWS);
                        SchedulerHelper.runTask(this.plugin, () ->
                                this.startTimeTrialTimer(player, trackNameWS, System.currentTimeMillis()));
                    });
                });
            });
        } else {
            // Incomplete lap
            if (totalCheckpoints <= 0) return;

            String langCode = db.getPlayerLanguage(uuid);
            this.plugin.sendMessage(player, "timetrial_incomplete_lap",
                    new String[]{"{count}", String.valueOf(checkpoints), "{total}", String.valueOf(totalCheckpoints)});
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5F, 1.0F);
            timerUtils.stopTimer(player, trackNameWS);

            // Restart fresh attempt (auto-loop)
            double restartProportion = RegionMathUtils.calculateRegionEntryProportion(from, to, regionBox);
            long restartAdjustmentMs = (long) ((1.0 - restartProportion) * 50.0);
            long restartTime = System.currentTimeMillis() - restartAdjustmentMs;
            this.startTimeTrialTimer(player, trackNameWS, restartTime);
        }
    }

    private void startTimeTrialTimer(Player player, String trackNameWS, long startTime) {
        UUID uuid = player.getUniqueId();
        this.plugin.getDebugManager().logPitStopSystem(
                "[PIT TT] " + player.getName() + " a iniciar timer na pista " + trackNameWS);

        TimeTrialSession session = new TimeTrialSession(uuid, trackNameWS, Instant.ofEpochMilli(startTime));
        TimeTrialStartEvent event = new TimeTrialStartEvent(player, session);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        this.plugin.getTimerUtils().startTimer(player, trackNameWS, startTime);
        this.plugin.getTimeTrialController().startSession(player, trackNameWS, session.getStartTime());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);

        if (this.plugin.getLonelyController() != null) {
            this.plugin.getLonelyController().updatePlayersVisibility(player);
            this.plugin.getLonelyController().updatePlayerVisibility(player);
        }

        this.plugin.getDebugManager().logPitStopSystem(
                "[PIT TT] " + player.getName() + " iniciou timer na pista " + trackNameWS);
    }

    private String formatTimeTrialTime(double elapsed) {
        long totalMillis = Math.round(elapsed * 1000.0);
        long minutes = totalMillis / 60000L;
        long seconds = totalMillis % 60000L / 1000L;
        long millis = totalMillis % 1000L;
        return minutes > 0L
                ? String.format("%02d:%02d.%03d", minutes, seconds, millis)
                : String.format("%02d.%03d", seconds, millis);
    }

    private void processMinigameLogic(Player player, Heats heat, boolean isInArea, String track) {
        UUID pid = player.getUniqueId();
        if (isInArea) {
            this.pitStopManager.onPlayerEnterPit(player, track, heat);
            this.wasInMinigameArea.put(pid, true);
            this.pitAreaExitTime.remove(pid);
        } else if ((Boolean)this.wasInMinigameArea.getOrDefault(pid, false)) {
            long now = System.currentTimeMillis();
            this.pitAreaExitTime.putIfAbsent(pid, now);
            if (now - (Long)this.pitAreaExitTime.get(pid) >= 400L) {
                this.onExitMinigameArea(player);
                this.wasInMinigameArea.put(pid, false);
            }
        }

    }

    private void onEnterPitStopEntry(Player player, Heats heat) {
        this.plugin.getDebugManager().logPitStopSystem("[PIT] " + player.getName() + " entrou na ENTRY");
        UUID playerId = player.getUniqueId();
        this.pitStopManager.markPlayerInPitStop(playerId);
        this.plugin.sendMessage(player, "pit_entering", new String[0]);
        Driver driver = heat.getDriver(playerId);
        if (driver != null) {
            int totalCheckpoints = 0;
            Track track = this.plugin.getTrackIntegrationManager().getTrack(heat.getTrackNameWS());
            if (track != null) {
                totalCheckpoints = track.getTotalCheckpoints();
            }

            this.plugin.getPTP().handlePitEntry(player, driver);

            if (totalCheckpoints > 0) {
                double threshold = (double)totalCheckpoints * (double)0.5F;
                if ((double)driver.getCheckpointsReached() >= threshold) {
                    DebugManager var10000 = this.plugin.getDebugManager();
                    String var10001 = player.getName();
                    var10000.logPitStopSystem("[PIT] Forçando checkpoints na entrada para " + var10001 + " (" + driver.getCheckpointsReached() + "/" + totalCheckpoints + ") - Threshold (50%) atingido.");
                    driver.forceCompleteCheckpoints(totalCheckpoints);
                } else {
                    DebugManager var9 = this.plugin.getDebugManager();
                    String var10 = player.getName();
                    var9.logPitStopSystem("[PIT] " + var10 + " entrou no pit mas não atingiu o threshold de checkpoints (" + driver.getCheckpointsReached() + "/" + totalCheckpoints + " < 50%). Não forçando conclusão.");
                }
            }
        }

    }

    private void onEnterPitStopExit(Player player, Heats heat) {
        this.plugin.getDebugManager().logPitStopSystem("[PIT] " + player.getName() + " entrou na EXIT");
        UUID playerId = player.getUniqueId();
        boolean hasPitLane = this.pitStopManager.hasPitLane(heat.getTrackNameWS());
        boolean passedEntry = this.pitStopManager.hasPassedPitStop(playerId);
        boolean completedMinigame = !hasPitLane || this.pitStopManager.hasCompletedMinigame(playerId);
        if (passedEntry && completedMinigame) {
            this.pitStopManager.handlePitExit(player, heat);
            this.plugin.sendMessage(player, "pit_completed", new String[0]);
        } else if (!passedEntry) {
            this.plugin.sendMessage(player, "pit_invalid_entry", new String[0]);
        } else if (hasPitLane && !completedMinigame) {
            this.plugin.sendMessage(player, "pit_invalid_game", new String[0]);
        }

        this.pitStopManager.clearPitStopState(playerId);
    }

    private void onExitMinigameArea(Player player) {
        UUID playerId = player.getUniqueId();
        if (!this.pitStopManager.hasCompletedMinigame(playerId)) {
            this.pitStopManager.onPlayerExitPit(player);
        }

    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player player) {
            if (this.pitStopManager.hasActiveMinigame(player.getUniqueId())) {
                event.setCancelled(true);
                PitStopMinigame minigame = this.pitStopManager.getMinigame(player.getUniqueId());
                if (minigame != null) {
                    if (event.getClickedInventory() != null) {
                        if (event.getClickedInventory().equals(minigame.getInventory())) {
                            switch (event.getAction()) {
                                case CLONE_STACK:
                                case MOVE_TO_OTHER_INVENTORY:
                                case HOTBAR_SWAP:
                                    this.plugin.sendMessage(player, "pit_action_blocked", new String[0]);
                                    return;
                                default:
                                    if (event.getClick().isShiftClick()) {
                                        this.plugin.sendMessage(player, "pit_shift_blocked", new String[0]);
                                    } else {
                                        int slot = event.getRawSlot();
                                        this.pitStopManager.handleInventoryClick(player, slot);
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity var3 = event.getPlayer();
        if (var3 instanceof Player player) {
            UUID var5 = player.getUniqueId();
            PitStopMinigame minigame = this.pitStopManager.getMinigame(var5);
            if (minigame != null) {
                if (!minigame.isCompleted()) {
                    minigame.cancel();
                    this.pitStopManager.onPlayerExitPit(player);
                }

            }
        }
    }

    public void clearPlayer(UUID playerId) {
        this.wasInPitStopEntry.remove(playerId);
        this.wasInPitStopExit.remove(playerId);
        this.wasInMinigameArea.remove(playerId);
        this.wasInPitStopStart.remove(playerId);
        this.lastPitStartCross.remove(playerId);
    }

    public void clearAll() {
        this.wasInPitStopEntry.clear();
        this.wasInPitStopExit.clear();
        this.wasInMinigameArea.clear();
        this.wasInPitStopStart.clear();
        this.lastPitStartCross.clear();
    }
}
