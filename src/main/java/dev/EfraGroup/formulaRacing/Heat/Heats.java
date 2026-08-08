package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.Event.EventAnnouncements;
import dev.EfraGroup.formulaRacing.Event.EventState;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Round.RoundState;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Logic.PracticeSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.QualifyingSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.RaceSession;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.RaceActionBarManager;
import dev.EfraGroup.formulaRacing.Utils.RaceScoreboardService;
import java.lang.MatchException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

public class Heats {

    private final FormulaRacing plugin;
    private int id;
    private int roundId;
    private Rounds round;
    private int heatNumber;
    private Instant startTime;
    private Instant endTime;
    private HeatState heatState;
    private HeatState previousState;
    private final Map<UUID, Driver> drivers;
    private List<Driver> startPositions;
    private List<Driver> livePositions;
    private UUID fastestLapUUID;
    private Integer timeLimit;
    private Integer totalLaps;
    private Integer totalPits;
    private Integer startDelay;
    private Integer maxDrivers;
    private CollisionMode collisionMode;
    private boolean canReset;
    private boolean lonely;
    private String trackNameWS;
    private GridManager gridManager;
    private boolean drsEnabled;
    private FRTask offlineMonitorTask;
    private List<DrsRegion> drsRegions = new ArrayList<>();
    private boolean pushtopass;
    private int deltaghosting;
    private boolean driverswap;
    private double drsdowntime;
    private double drsdownpower;
    private boolean realistc;
    private boolean reversegrid;
    private double pushtopasspower;
    private FRTask sessionTask;
    private boolean configDirty = false;
    private boolean onlyBedrock = false;
    private boolean ErsEnabled = false;
    private boolean gridReversed = false;
    private Map<Integer, Integer> originalPositions = new HashMap<>();
    private int eliminationIntervalSeconds = 30;
    private int minimumDrivers = 2;
    private boolean elimination = false;

    public Heats(FormulaRacing plugin, int id, Rounds round, int heatNumber) {
        this.plugin = plugin;
        this.id = id;
        this.round = round;
        this.roundId = round != null ? round.getId() : 0;
        this.heatNumber = heatNumber;
        this.heatState = HeatState.SETUP;
        this.drivers = new ConcurrentHashMap<>();
        this.startPositions = new ArrayList();
        this.livePositions = new ArrayList();
        this.totalLaps = 3;
        this.totalPits = 0;
        this.startDelay = 5;
        this.maxDrivers = 1000;
        this.collisionMode = CollisionMode.HIGH;
        this.canReset = true;
        this.lonely = false;
        this.trackNameWS = "";
        this.drsEnabled = false;
        this.pushtopass = true;
        this.driverswap = false;
        this.drsdowntime =  3.0F;
        this.drsdownpower = 0.052;
        this.realistc = false;
        this.pushtopasspower = 0.05;
        this.reversegrid = false;
        this.deltaghosting = 0;
        this.ErsEnabled = false;
        this.gridManager = new GridManager(plugin, this);
    }

    public FormulaRacing getPlugin() {
        return this.plugin;
    }

    public Heats() {
        this.plugin = null;
        this.drivers = new ConcurrentHashMap<>();
        this.startPositions = new ArrayList();
        this.livePositions = new ArrayList();
    }

    public void setOnlyBedrock(boolean onlyBedrock) {
        this.onlyBedrock = onlyBedrock;
        this.markConfigDirty();
    }

    public boolean getOnlyBedrock() {
        return this.onlyBedrock;
    }


    public boolean getrealistc() {
        return this.realistc;
    }

    public boolean getreversegrid() {
        return this.reversegrid;
    }

    public double getpushtopasspower() {
        return this.pushtopasspower;
    }

    public void markConfigDirty() {
        this.configDirty = true;
    }

    public void saveConfigIfDirty() {
        if (!this.configDirty || !this.shouldSaveConfig()) {
            return;
        }
        this.plugin.getRaceEventManager()
            .getDatabaseManager()
            .updateHeatFullConfig(
                this.id,
                this.totalLaps,
                this.totalPits,
                this.timeLimit,
                this.startDelay,
                this.maxDrivers,
                this.lonely,
                this.canReset,
                true,
                this.collisionMode,
                this.drsEnabled,
                this.driverswap,
                this.drsdowntime,
                this.drsdownpower,
                this.reversegrid,
                this.deltaghosting,
                this.pushtopass,
                this.pushtopasspower,
                this.realistc,
                this.eliminationIntervalSeconds,
                this.minimumDrivers
            );
        this.configDirty = false;
    }

    private boolean shouldSaveConfig() {
        return this.id > 0
            && this.plugin != null
            && this.plugin.getRaceEventManager() != null;
    }

    public void setrealistc(boolean realistc) {
        this.realistc = realistc;
        this.markConfigDirty();
    }

    public void setreversegrid(boolean reversegrid) {
        this.reversegrid = reversegrid;
        this.markConfigDirty();
    }

    public void setpushtopasspower(double pushtopasspower) {
        this.pushtopasspower = pushtopasspower;
        this.markConfigDirty();
    }

    public double getDrsdowntime() {
        return this.drsdowntime;
    }

    public void setDrsdowntime(double drsdowntime) {
        this.drsdowntime = drsdowntime;
        this.markConfigDirty();
    }

    public double getDrsdownpower() {
        return this.drsdownpower;
    }

    public void setDrsdownpower(double drsdownpower) {
        this.drsdownpower = drsdownpower;
        this.markConfigDirty();
    }

    public boolean getDriverSwap() {
        return this.driverswap;
    }

    public void setDriverSwap(boolean driverswap) {
        this.driverswap = driverswap;
        this.markConfigDirty();
    }

    public boolean isDrsEnabled() {
        return this.drsEnabled;
    }

    public boolean isPushtopass() {
        return this.pushtopass;
    }

    public void setPushtopass(boolean pushtopass) {
        this.pushtopass = pushtopass;
        this.markConfigDirty();
    }

    public int getDeltaGhosting() {
        return this.deltaghosting;
    }

    public void setDeltaghosting(int deltaghosting) {
        this.deltaghosting = deltaghosting;
        this.markConfigDirty();
    }

    public void setDrsEnabled(boolean drsEnabled) {
        this.drsEnabled = drsEnabled;
        this.markConfigDirty();
    }

    public int getEliminationIntervalSeconds() {
        return eliminationIntervalSeconds;
    }

    public void setEliminationIntervalSeconds(int seconds) {
        this.eliminationIntervalSeconds = seconds;
        this.markConfigDirty();
    }

    public int getMinimumDrivers() {
        return minimumDrivers;
    }

    public void setMinimumDrivers(int minimum) {
        this.minimumDrivers = minimum;
        this.markConfigDirty();
    }

    public boolean isElimination() {
        return this.elimination;
    }

    public void setElimination(boolean elimination) {
        this.elimination = elimination;
    }

    public List<Driver> getLivePositions() {
        return this.livePositions;
    }

    public void setCollisionMode(CollisionMode collisionMode) {
        this.collisionMode = collisionMode;
    }

    public CollisionMode getCollisionMode() {
        return this.collisionMode;
    }

    public void applyCollisionModeToPlayer(Player player) {
        if (this.plugin.getPacketSender() != null) {
            short modeValue;
            switch (this.collisionMode) {
                case HIGH:
                    modeValue = 0; // Vanilla — full collision (same as Frosthex)
                    break;
                case LOW:
                    modeValue = 0; // Vanilla — same as HIGH (no filtered system yet)
                    break;
                case DISABLED:
                    modeValue = 2; // No collision with anything (same as Frosthex)
                    break;
                default:
                    modeValue = 0; // Vanilla (fallback)
                    break;
            }

            this.plugin.getPacketSender().sendBoatSetting(
                player,
                27,
                new Object[] { modeValue }
            );
        }
    }

    public void onFirstPlayerCrossStartLine(Player player) {
        if (this.startTime == null) {
            this.startTime = Instant.now();
            this.plugin.getDebugManager().logRaceSystem(
                "[HEAT] Session timer started by " + player.getName()
            );
            if (
                this.plugin != null &&
                this.id > 0 &&
                this.plugin.getRaceEventManager() != null
            ) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateHeatTimes(this.id, this.startTime, (Instant) null);
            }
        }
    }

    public void setupDrs() {
        if (this.drsEnabled) {
            // Now getDrsRegionsList should return a List<DrsRegion>
            this.drsRegions = this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .getDrsRegionsList(this.trackNameWS);

            this.plugin.getLogger().info("§a[DRS] Loaded " + drsRegions.size() + " zones for " + this.trackNameWS);
        }
    }

    public List<DrsRegion> getDrsRegions() {
        return this.drsRegions;
    }
    public void reorderGrid() {
        this.startPositions = new ArrayList(this.drivers.values());
        this.startPositions.sort(
            Comparator.comparingInt(Driver::getStartPosition)
        );
        this.livePositions = new ArrayList(this.startPositions);
        if (
            this.heatState == HeatState.LOADED ||
            this.heatState == HeatState.STARTING
        ) {
            boolean isQuali =
                this.round != null &&
                (this.round.getRoundType() == RoundType.QUALIFICATION ||
                 this.round.getRoundType() == RoundType.SPRINT_QUALIFICATION);
            if (!isQuali) {
                this.gridManager.teleportDriversToGrid();
            }
        }
    }

    public boolean loadHeat() {
        DebugManager var10000 = this.plugin.getDebugManager();
        int var10001 = this.id;
        var10000.logRaceSystem(
            "[LOAD DEBUG] Attempting to load heat " +
                var10001 +
                " (current state: " +
                String.valueOf(this.heatState) +
                ") - HeatObj: " +
                System.identityHashCode(this)
        );
        if (
            this.heatState != HeatState.SETUP &&
            this.heatState != HeatState.IDLE
        ) {
            var10000 = this.plugin.getDebugManager();
            var10001 = this.id;
            var10000.logRaceSystem(
                "Heat " +
                    var10001 +
                    " is in state " +
                    String.valueOf(this.heatState) +
                    " - resetting automatically..."
            );
            this.resetHeat();
            this.plugin.getDebugManager().logRaceSystem(
                "Heat " + this.id + " reset, continuing load..."
            );
        }

        if (this.drivers.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem(
                "Heat " + this.id + " has no drivers!"
            );
            return false;
        } else {
            var10000 = this.plugin.getDebugManager();
            var10001 = this.id;
            var10000.logRaceSystem(
                "[LOAD DEBUG] Heat " +
                    var10001 +
                    " has " +
                    this.drivers.size() +
                    " drivers"
            );
            if (this.round != null && this.round.getEvent() != null) {
                String eventTrack = this.round.getEvent().getTrackNameWS();
                if (eventTrack == null || eventTrack.isEmpty()) {
                    this.plugin.getDebugManager().logRaceSystem(
                        "Event does not have a track defined!"
                    );
                    return false;
                }

                if (!eventTrack.equals(this.trackNameWS)) {
                    this.plugin.getDebugManager().logRaceSystem(
                        "[LOAD DEBUG] Heat track updated: " +
                            this.trackNameWS +
                            " -> " +
                            eventTrack
                    );
                    this.trackNameWS = eventTrack;
                    this.gridManager.clear();
                }
            } else if (this.trackNameWS == null || this.trackNameWS.isEmpty()) {
                this.plugin.getDebugManager().logRaceSystem(
                    "Heat is not associated with a valid event and has no track defined!"
                );
                return false;
            }

            this.plugin.getDebugManager().logRaceSystem(
                "[LOAD DEBUG] Track configured: " + this.trackNameWS
            );
            this.startPositions = new ArrayList(this.drivers.values());
            this.startPositions.sort(
                Comparator.comparingInt(Driver::getStartPosition)
            );
            this.livePositions = new ArrayList(this.startPositions);
            this.plugin.getDebugManager().logRaceSystem(
                "[LOAD DEBUG] Starting grid positions organized"
            );
            boolean isQuali =
                this.round != null &&
                (this.round.getRoundType() == RoundType.QUALIFICATION ||
                 this.round.getRoundType() == RoundType.SPRINT_QUALIFICATION);
            if (isQuali) {
                List<Location> qualiGridPositions = this.plugin
                    .getTrackIntegrationManager()
                    .generateQualiGridPositions(this.trackNameWS, this.drivers.size());
                if (qualiGridPositions.isEmpty()) {
                    Location spawnLoc =
                        this.plugin.getTrackIntegrationManager().getTrackSpawn(
                            this.trackNameWS
                        );
                    if (spawnLoc == null) {
                        this.plugin.getDebugManager().logRaceSystem(
                            "Failed to get track spawn for Qualifying!"
                        );
                        return false;
                    }

                    for (Driver driver : this.drivers.values()) {
                        Player player = Bukkit.getPlayer(driver.getUuid());
                        if (player != null && player.isOnline()) {
                            SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                                this.plugin.getAPI().recoverPlayerBoatState(player);
                            });
                            this.spawnQualiDriver(player, driver, spawnLoc);
                        }
                    }
                    this.plugin.getDebugManager().logRaceSystem(
                        "Drivers teleported to SPAWN for Qualifying (qualigrid not configured)."
                    );
                } else {
                    for (Driver driver : this.drivers.values()) {
                        Player player = Bukkit.getPlayer(driver.getUuid());
                        if (player != null && player.isOnline()) {
                            int gridIndex = driver.getStartPosition() - 1;
                            if (gridIndex >= 0 && gridIndex < qualiGridPositions.size()) {
                                Location qualiLoc = qualiGridPositions.get(gridIndex);
                                SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                                    this.plugin.getAPI().recoverPlayerBoatState(player);
                                });
                                this.spawnQualiDriver(player, driver, qualiLoc);
                            } else {
                                player.sendMessage("§cInvalid position on qualigrid.");
                            }
                        }
                    }
                    this.plugin.getDebugManager().logRaceSystem(
                        "Drivers teleported to QUALIGRID for Qualifying."
                    );
                }
            } else {
                for (Driver driver : this.drivers.values()) {
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        this.plugin.getLonelyController().updatePlayersVisibility(
                            player
                        );
                    }
                }

                if (!this.gridManager.generateGrid()) {
                    this.plugin.getDebugManager().logRaceSystem(
                        "Failed to generate grid for Heat " + this.id
                    );
                    return false;
                }

                this.plugin.getDebugManager().logRaceSystem(
                    "[LOAD DEBUG] Grid generated successfully"
                );
                int teleported = this.gridManager.teleportDriversToGrid();
                this.plugin.getDebugManager().logRaceSystem(
                    "Heat " +
                        this.id +
                        " loaded: " +
                        teleported +
                        "/" +
                        this.drivers.size() +
                        " drivers on grid"
                );
                if (teleported == 0) {
                    this.plugin.getDebugManager().logRaceSystem(
                        "No drivers were teleported!"
                    );
                    return false;
                }
            }

            for (Driver driver : this.drivers.values()) {
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    this.stopTimeTrialTimer(player);
                    this.plugin.getRaceScoreboardManager().addPlayer(
                        player,
                        this
                    );
                    this.plugin.getRaceActionBarManager().addPlayer(
                        player,
                        this
                    );
                    this.plugin.getHotbarController().giveHeatHotbar(player, this);
                }
            }

            // Re-register all drivers in the lookup after reset+load
            if (this.plugin != null && this.plugin.getDriverLookup() != null) {
                for (Driver driver : this.drivers.values()) {
                    this.plugin.getDriverLookup().register(driver, this);
                }
            }

            this.setHeatState(HeatState.LOADED);

            // Spawn AI opponents as soon as the heat is loaded so they appear on the grid.
            if (this.plugin != null && this.plugin.getAIOpponentManager() != null) {
                this.plugin.getAIOpponentManager().startAIForHeat(this);
            }

            this.saveConfigIfDirty();
            var10000 = this.plugin.getDebugManager();
            var10001 = this.id;
            var10000.logRaceSystem(
                "✓ Heat " +
                    var10001 +
                    " loaded with " +
                    this.drivers.size() +
                    " drivers."
            );
            return true;
        }
    }

    public boolean startCountdown() {
        return this.startCountdown(
            this.startDelay != null ? this.startDelay : 5
        );
    }

    public boolean startCountdown(int seconds) {
        if (this.round != null) {
            if (this.round.getRoundState() == RoundState.SETUP) {
                this.round.setRoundState(RoundState.RUNNING);
            }
            Events ev = this.round.getEvent();
            if (ev != null && ev.getState() == EventState.SETUP) {
                ev.ensureRunning(this.round);
            }
        }
        DebugManager var10000 = this.plugin.getDebugManager();
        int var10001 = this.id;
        var10000.logRaceSystem(
            "[START DEBUG] Attempting to start countdown heat " +
                var10001 +
                " (current state: " +
                String.valueOf(this.heatState) +
                ") - HeatObj: " +
                System.identityHashCode(this)
        );
        if (this.heatState != HeatState.LOADED) {
            var10000 = this.plugin.getDebugManager();
            var10001 = this.id;
            var10000.logRaceSystem(
                "Heat " +
                    var10001 +
                    " must be LOADED to start! Current state: " +
                    String.valueOf(this.heatState)
            );
            return false;
        } else {
            this.plugin.getDebugManager().logRaceSystem(
                "Starting countdown for Heat " +
                    this.id +
                    " (" +
                    seconds +
                    "s)..."
            );
            if (
                this.id > 0 &&
                this.plugin != null &&
                this.plugin.getRaceEventManager() != null
            ) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateHeatFullConfig(
                        this.id,
                        this.totalLaps,
                        this.totalPits,
                        this.timeLimit,
                        this.startDelay,
                        this.maxDrivers,
                        this.lonely,
                        this.canReset,
                        true,
                        this.collisionMode,
                        this.drsEnabled,
                        this.driverswap,
                        this.drsdowntime,
                        this.drsdownpower,
                        this.reversegrid,
                        this.deltaghosting,
                        this.pushtopass,
                        this.pushtopasspower,
                        this.realistc,
                        this.eliminationIntervalSeconds,
                        this.minimumDrivers
                    );
                this.configDirty = false;
            }
            this.setHeatState(HeatState.STARTING);
            this.gridManager.freezePlayers();
            Runnable startAction = this::startRace;
            if (this.round != null) {
                startAction = () -> this.round.getSessionLogic().start(this);
            }

            RaceCountdown countdown = new RaceCountdown(
                this.plugin,
                this,
                seconds,
                startAction
            );
            countdown.start();
            return true;
        }
    }

    public void startPractice() {
        if (this.round != null) {
            if (this.round.getRoundState() == RoundState.SETUP) {
                this.round.setRoundState(RoundState.RUNNING);
            }
            Events ev = this.round.getEvent();
            if (ev != null && ev.getState() == EventState.SETUP) {
                ev.ensureRunning(this.round);
            }
        }
        (new PracticeSession()).start(this);
    }

    public void startQualifying() {
        (new QualifyingSession()).start(this);
    }

    public void startRace() {
        (new RaceSession(this.plugin)).start(this);
    }

    public boolean passLap(Driver driver) {
        return this.round != null
            ? this.round.getSessionLogic().passLap(this, driver)
            : (new RaceSession(this.plugin)).passLap(this, driver);
    }

    public boolean passLap(
        Driver driver,
        Location from,
        Location to,
        RegionBox region
    ) {
        return this.round != null
            ? this.round.getSessionLogic().passLap(
                  this,
                  driver,
                  from,
                  to,
                  region
              )
            : (new RaceSession(this.plugin)).passLap(
                  this,
                  driver,
                  from,
                  to,
                  region
              );
    }

    public void finishHeat() {
        this.finishHeat(true);
    }

    public void finishHeat(boolean teleportToSpawn) {
        if (
            this.heatState != HeatState.RACING &&
            this.heatState != HeatState.PRACTICE &&
            this.heatState != HeatState.QUALIFYING
        ) {
            this.plugin.getDebugManager().logRaceSystem(
                "Heat " + this.id + " is not in race, practice or quali!"
            );
        } else {
            java.util.List<java.util.UUID> driverUUIDs =
                new java.util.ArrayList<>(this.drivers.keySet());
            if (this.plugin.getRegionListener() != null) {
                this.plugin.getRegionListener().cleanupHeatPlayers(driverUUIDs);
            }
            if (this.plugin.getRaceCheckpointListener() != null) {
                this.plugin.getRaceCheckpointListener().cleanupHeatPlayers(
                    driverUUIDs
                );
            }
            this.plugin.getLonelyController().clearGhostForPlayers(
                this.drivers.keySet()
            );
            if (this.plugin != null && this.plugin.getDriverLookup() != null) {
                for (UUID uuid : driverUUIDs) {
                    this.plugin.getDriverLookup().unregister(uuid);
                }
            }
            if (this.plugin != null && this.plugin.getLightningRodListener() != null) {
                for (UUID uuid : driverUUIDs) {
                    this.plugin.getLightningRodListener().removeRodForPlayer(uuid);
                }
            }
            if (
                this.heatState == HeatState.RACING &&
                this.totalPits != null &&
                this.totalPits > 0
            ) {
                this.validateMandatoryPits();
            }

            this.setHeatState(HeatState.FINISHED);
            this.endTime = Instant.now();
            this.stopSessionTimer();
            this.stopOfflineMonitoring();
            this.updateLivePositions();
            if (this.plugin.getPitStopManager() != null) {
                this.plugin.getPitStopManager().clear();
            }

            this.gridManager.clear();
            // Despawn any AI-controlled boats so they don't leak after the heat ends.
            if (this.plugin.getAIOpponentManager() != null) {
                this.plugin.getAIOpponentManager().stopAIForHeat(this.id);
            }
            if (this.plugin.getPacketSender() != null) {
                long now = System.currentTimeMillis();
                for (Driver driver : this.drivers.values()) {
                    if (!driver.isFinished() && !driver.isDnf()) {
                        driver.setEndTime(now);
                        driver.setDnf(true);
                    }
                }

                SchedulerHelper.runAsync(this.plugin, () -> {
                    Map<UUID, Boolean> lonelyStates = new HashMap();

                    for (UUID uuid : driverUUIDs) {
                        boolean dbLonely =
                            this.plugin.getDatabaseManager().getLonelyModePlayer(
                                uuid
                            );
                        lonelyStates.put(uuid, dbLonely);
                    }

                    SchedulerHelper.runTask(this.plugin, () -> {
                        for (Map.Entry<
                            UUID,
                            Boolean
                        > entry : lonelyStates.entrySet()) {
                            Player p = Bukkit.getPlayer((UUID) entry.getKey());
                            if (p != null && p.isOnline()) {
                                this.plugin.getLonelyController().setLonelyMode(
                                    p,
                                    (Boolean) entry.getValue()
                                );
                            }
                        }
                    });
                });
            }

            for (Driver driver : this.drivers.values()) {
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    if (this.plugin.getPTP() != null) {
                        this.plugin.getPTP().disablePTP(player, driver);
                    }
                    this.clearTimeTrialActionBar(player);
                }
            }

            SchedulerHelper.runTaskLater(
                this.plugin,
                () -> {
                    this.plugin.getRaceScoreboardManager().removeHeat(this);
                    this.plugin.getRaceActionBarManager().removeHeat(this);
                },
                60L
            );
            if (
                this.plugin != null &&
                this.id > 0 &&
                this.plugin.getRaceEventManager() != null
            ) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateHeatTimes(this.id, this.startTime, this.endTime);
            }

            this.plugin.getDebugManager().logRaceSystem(
                "Heat " + this.id + " finished!"
            );
            this.displayFinalStandings();

            if (this.round != null) {
                this.plugin.getDebugManager().logRaceSystem(
                    "Heat " + this.id + " finished, advancing round... (roundId=" + this.round.getId() + ")"
                );
                this.round.nextHeat();
            }
            if (teleportToSpawn) {
                Location targetLoc =
                    this.plugin.getDatabaseManager().getTrackFinishAll(
                        this.trackNameWS
                    );
                if (targetLoc == null) {
                    targetLoc =
                        this.plugin.getTrackIntegrationManager().getTrackSpawn(
                            this.trackNameWS
                        );
                }

                if (targetLoc != null) {
                    Location finalTargetLoc = targetLoc;
                    for (Driver d : this.drivers.values()) {
                        if (!d.isFinished()) {
                            Player p = Bukkit.getPlayer(d.getUuid());
                            if (p != null && p.isOnline()) {
                                SchedulerHelper.runTaskFor(this.plugin, p, () -> {
                                    this.plugin.getAPI().recoverPlayerBoatState(p);
                                    SchedulerHelper.teleportAsync(this.plugin, p, finalTargetLoc);
                                });
                            }
                        }
                    }
                }
            }
        }
    }

    private void displayFinalStandings() {
        List<Driver> results = new ArrayList(this.drivers.values());
        if (this.isElimination()) {
            results.sort((d1, d2) -> Long.compare(d2.getTotalTime(), d1.getTotalTime()));
        } else if (
            this.previousState != HeatState.QUALIFYING &&
            this.previousState != HeatState.PRACTICE
        ) {
            results.sort((d1, d2) -> {
                int g1 = this.getFinalRaceGroup(d1);
                int g2 = this.getFinalRaceGroup(d2);
                if (g1 != g2) {
                    return Integer.compare(g1, g2);
                }

                if (g1 == 0) {
                    return Integer.compare(d1.getPosition(), d2.getPosition());
                }

                int lapCompare = Integer.compare(
                    d2.getLapCount(),
                    d1.getLapCount()
                );
                if (lapCompare != 0) {
                    return lapCompare;
                }

                int cpCompare = Integer.compare(
                    d2.getCheckpointsReached(),
                    d1.getCheckpointsReached()
                );
                if (cpCompare != 0) {
                    return cpCompare;
                }

                Long time1 = d1.getAbsoluteTimeAtProgress(
                    d1.getLapCount(),
                    d1.getCheckpointsReached()
                );
                Long time2 = d2.getAbsoluteTimeAtProgress(
                    d2.getLapCount(),
                    d2.getCheckpointsReached()
                );
                if (time1 != null && time2 != null) {
                    int timeCompare = Long.compare(time1, time2);
                    if (timeCompare != 0) {
                        return timeCompare;
                    }
                }

                return Long.compare(d1.getTotalTime(), d2.getTotalTime());
            });
        } else {
            results.sort((d1, d2) -> {
                long t1 =
                    d1.getFastestLap() != null
                        ? d1.getFastestLap().getLapTime()
                        : Long.MAX_VALUE;
                long t2 =
                    d2.getFastestLap() != null
                        ? d2.getFastestLap().getLapTime()
                        : Long.MAX_VALUE;
                return Long.compare(t1, t2);
            });
        }

        for (int i = 0; i < results.size(); ++i) {
            ((Driver) results.get(i)).setPosition(i + 1);
        }

        EventAnnouncements announcements =
            this.round != null && this.round.getEvent() != null
                ? this.round.getEvent().getAnnouncements()
                : this.plugin.getEventAnnouncements();
        announcements.broadcastFinalStandings(
            this,
            results,
            this.previousState
        );

        for (Driver d : results) {
            if (this.plugin != null && d.getId() > 0) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .updateDriverPosition(d.getId(), d.getPosition());
            }
        }
    }

    public void resetHeat() {
        // If it was running, force stop
        if (this.heatState != HeatState.SETUP &&
                this.heatState != HeatState.FINISHED &&
                this.heatState != HeatState.PRACTICE) {

            EventAnnouncements announcements =
                    this.round != null && this.round.getEvent() != null
                            ? this.round.getEvent().getAnnouncements()
                            : this.plugin.getEventAnnouncements();
            announcements.broadcastSessionCancelled(this);

            for (Driver driver : this.drivers.values()) {
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    this.clearTimeTrialActionBar(player);
                }
            }
        } else {
            DebugManager var10000 = this.plugin.getDebugManager();
            int var10001 = this.id;
            var10000.logRaceSystem(
                    "Heat " + var10001 + " in progress (" + String.valueOf(this.heatState) +
                            ") - forcing stop before reset..."
            );
            if (this.heatState != HeatState.PRACTICE) {
                EventAnnouncements announcements =
                        this.round != null && this.round.getEvent() != null
                                ? this.round.getEvent().getAnnouncements()
                                : this.plugin.getEventAnnouncements();
                announcements.broadcastSessionCancelled(this);

                for (Driver driver : this.drivers.values()) {
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        this.clearTimeTrialActionBar(player);
                    }
                }
            }

            this.forceFinishWithoutSave();
            if (this.heatState != HeatState.FINISHED) {
                this.setHeatStateForLoad(HeatState.FINISHED);
            }
        }

        // Clear timers and managers
        this.stopSessionTimer();
        this.stopOfflineMonitoring();
        if (this.plugin.getPitStopManager() != null) {
            this.plugin.getPitStopManager().clear();
        }
        this.gridManager.clear();
        this.plugin.getRaceActionBarManager().removeHeat(this);
        this.plugin.getRaceScoreboardManager().removeHeat(this);

        // Reset internal variables
        this.startTime = null;
        this.endTime = null;
        this.fastestLapUUID = null;
        if (this.startPositions != null) this.startPositions.clear();
        if (this.livePositions != null) this.livePositions.clear();

        // Complete driver reset
        for (Driver driver : this.drivers.values()) {
            driver.reset();                // clears laps, pits, etc.
            driver.resetLagFlags();        // ensures lagStart/lagEnd are reset
            driver.setCheckpointsReached(0);
            driver.setFinished(false);
            driver.setDnf(false);

            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                boolean dbLonely =
                        this.plugin.getDatabaseManager().getLonelyModePlayer(player.getUniqueId());
                this.plugin.getLonelyController().setLonelyMode(player, dbLonely);
            }
        }

        // Remove rods from players
        if (this.plugin != null && this.plugin.getLightningRodListener() != null) {
            for (UUID uuid : this.drivers.keySet()) {
                this.plugin.getLightningRodListener().removeRodForPlayer(uuid);
            }
        }

        // Clear lookup so it doesn't stay stuck to the old Heat
        if (this.plugin != null && this.plugin.getDriverLookup() != null) {
            for (UUID uuid : this.drivers.keySet()) {
                this.plugin.getDriverLookup().unregister(uuid);
            }
        }

        // Clear checkpoints in listener (prevents cooldown blocking new CPs)
        if (this.plugin.getRaceCheckpointListener() != null) {
            this.plugin.getRaceCheckpointListener().cleanupHeatPlayers(this.drivers.keySet());
        }

        // Return to initial state
        if (this.heatState != HeatState.SETUP) {
            this.setHeatStateForLoad(HeatState.SETUP);
        }
        this.plugin.getDebugManager().logRaceSystem(
                "✓ Heat " + this.id + " reset to initial state."
        );
    }


    public long getSessionTimeRemaining() {
        if (this.startTime == null) {
            return this.timeLimit != null && this.timeLimit > 0
                ? (long) this.timeLimit * 1000L
                : -1L;
        } else if (this.timeLimit != null && this.timeLimit > 0) {
            long elapsed =
                System.currentTimeMillis() - this.startTime.toEpochMilli();
            long limitMs = (long) this.timeLimit * 1000L;
            return Math.max(0L, limitMs - elapsed);
        } else {
            return Long.MAX_VALUE;
        }
    }

    /** @deprecated */
    @Deprecated
    public long getPracticeTimeRemaining() {
        return this.getSessionTimeRemaining();
    }

    public void startOfflineMonitoring() {
        this.stopOfflineMonitoring();
        this.offlineMonitorTask = SchedulerHelper.runTaskTimer(
            this.plugin,
                () -> {
                    if (this.heatState != HeatState.RACING) {
                        this.stopOfflineMonitoring();
                    } else {
                        for (Driver driver : this.drivers.values()) {
                            if (!driver.isFinished() && !driver.isDnf()) {
                                Player player =
                                    this.plugin.getServer().getPlayer(
                                        driver.getUuid()
                                    );
                                if (player == null || !player.isOnline()) {
                                    this.plugin.getDebugManager().logRaceSystem(
                                        "Driver " +
                                            String.valueOf(driver.getUuid()) +
                                            " disconnected during race - marking as DNF"
                                    );
                                    driver.setDnf(true);
                                    driver.setPtpActive(false);
                                    driver.setPtpEnergy((double) 0.0F);
                                    EventAnnouncements announcements =
                                        this.round != null &&
                                        this.round.getEvent() != null
                                            ? this.round.getEvent().getAnnouncements()
                                            : this.plugin.getEventAnnouncements();
                                    announcements.broadcastDNF(
                                        this,
                                        driver,
                                        "Disconnected"
                                    );
                                }
                            }
                        }

                        boolean allFinished = this.drivers.values()
                            .stream()
                            .allMatch(
                                driverx ->
                                    driverx.isFinished() || driverx.isDnf()
                            );
                        if (allFinished) {
                            this.plugin.getDebugManager().logRaceSystem(
                                "All drivers finished or were marked as DNF - finishing heat"
                            );
                            SchedulerHelper.runTask(this.plugin, () -> {
                                this.finishHeat();
                                this.plugin.getRaceEventManager().tryDeleteEventForHeat(this);
                            });
                        }
                    }
                },
                200L,
                200L
            );
        this.plugin.getDebugManager().logRaceSystem(
            "Offline player monitoring started for Heat " + this.id
        );
    }

    private void stopOfflineMonitoring() {
        if (
            this.offlineMonitorTask != null &&
            !this.offlineMonitorTask.isCancelled()
        ) {
            this.offlineMonitorTask.cancel();
            this.plugin.getDebugManager().logRaceSystem(
                "Offline player monitoring cancelled for Heat " +
                    this.id
            );
        }

        this.offlineMonitorTask = null;
    }

    public void clearTimeTrialActionBar(Player player) {
        if (this.plugin.getTimeTrialDuelsAction() != null) {
            this.plugin.getTimeTrialDuelsAction().stopAll(player);
        }
    }

    public void stopTimeTrialTimer(Player player) {
        if (this.plugin.getTimerUtils() != null) {
            this.plugin.getTimerUtils().stopTimer(player);
            this.plugin.getDebugManager().logRaceSystem(
                "[RACE DEBUG] Time Trial timer stopped for " +
                    player.getName()
            );
        }
    }

    private void forceFinishWithoutSave() {
        this.gridManager.unfreezePlayers();

        for (Driver driver : this.drivers.values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                this.plugin.getRaceScoreboardManager().removePlayer(player);
                this.plugin.getRaceActionBarManager().removePlayer(player);
                if (this.plugin.getPacketSender() != null) {
                    this.plugin.getLonelyController().setLonelyMode(
                        player,
                        false
                    );
                }
            }
        }

        this.plugin.getDebugManager().logRaceSystem(
            "Heat " +
                this.id +
                " force finished (without saving results)"
        );
    }

    public boolean setDriverPosition(Driver driver, int newStartPosition) {
        if (
            this.heatState != HeatState.SETUP &&
            this.heatState != HeatState.LOADED
        ) {
            return false;
        } else if (
            newStartPosition >= 1 && newStartPosition <= this.drivers.size()
        ) {
            int prevPos = driver.getStartPosition();
            if (prevPos == newStartPosition) {
                return false;
            } else {
                for (Driver d : this.drivers.values()) {
                    int currentStart = d.getStartPosition();
                    if (newStartPosition < prevPos) {
                        if (
                            currentStart >= newStartPosition &&
                            currentStart < prevPos &&
                            !d.getUuid().equals(driver.getUuid())
                        ) {
                            d.setStartPosition(currentStart + 1);
                            d.setPosition(currentStart + 1);
                        }
                    } else if (
                        newStartPosition > prevPos &&
                        currentStart > prevPos &&
                        currentStart <= newStartPosition &&
                        !d.getUuid().equals(driver.getUuid())
                    ) {
                        d.setStartPosition(currentStart - 1);
                        d.setPosition(currentStart - 1);
                    }
                }

                driver.setStartPosition(newStartPosition);
                driver.setPosition(newStartPosition);
                this.reorderGrid();
                if (
                    this.plugin != null &&
                    this.id > 0 &&
                    this.plugin.getRaceEventManager() != null
                ) {
                    Map<UUID, Integer> positions = new HashMap();

                    for (Driver d : this.drivers.values()) {
                        positions.put(d.getUuid(), d.getStartPosition());
                    }

                    this.plugin.getRaceEventManager()
                        .getDatabaseManager()
                        .updateHeatGridPositions(this.id, positions);
                }

                return true;
            }
        } else {
            return false;
        }
    }

    private void spawnQualiDriver(Player player, Driver driver, Location gridLoc) {
        this.plugin.getLonelyController().updatePlayersVisibility(player);
        SchedulerHelper.runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
                SchedulerHelper.teleport(player, gridLoc);
                if (this.plugin.getPacketSender() != null) {
                    this.plugin.getPacketSender().resetBoatUtilsToVanilla(player);
                    this.plugin.getPacketSender().applyBoatUtilsToPlayer(player, this.trackNameWS);
                    this.applyCollisionModeToPlayer(player);
                }
                // Apply track game time (day/night cycle)
                this.plugin.applyTrackGameTime(player, this.trackNameWS);
                boolean collidable = this.collisionMode != CollisionMode.DISABLED;
                this.plugin.getAPI().spawnBoatAt(player, gridLoc, false, true, false, collidable);
            }
        }, 10L);
    }

    public boolean addDriver(UUID uuid, int gridPosition) {
        if (this.drivers.containsKey(uuid)) {
            return false;
        } else if (this.drivers.size() >= this.maxDrivers) {
            return false;
        } else {
            if (this.round != null) {
                for (Heats heat : this.round.getHeats().values()) {
                    if (heat != this && heat.getDrivers().containsKey(uuid)) {
                        DebugManager var10000 = this.plugin.getDebugManager();
                        String var10001 = String.valueOf(uuid);
                        var10000.logRaceSystem(
                            "Attempt to add driver " +
                                var10001 +
                                " to multiple heats in round " +
                                this.round.getId()
                        );
                        return false;
                    }
                }
            }

            Driver driver = new Driver(uuid, this.id, gridPosition);
            this.drivers.put(uuid, driver);
            if (this.plugin != null && this.plugin.getDriverLookup() != null) {
                this.plugin.getDriverLookup().register(driver, this);
            }
            if (
                this.plugin != null &&
                this.id > 0 &&
                this.plugin.getRaceEventManager() != null
            ) {
                this.plugin.getRaceEventManager()
                    .getDatabaseManager()
                    .createDriver(driver);
            }

            return true;
        }
    }

    public void addDriverDirect(Driver driver) {
        this.drivers.put(driver.getUuid(), driver);
        if (this.plugin != null && this.plugin.getDriverLookup() != null) {
            this.plugin.getDriverLookup().register(driver, this);
        }
    }

    public boolean removeDriver(UUID uuid) {
        this.plugin.getLonelyController().clearGhost(uuid);
        if (this.plugin != null && this.plugin.getDriverLookup() != null) {
            this.plugin.getDriverLookup().unregister(uuid);
        }
        return this.drivers.remove(uuid) != null;
    }

    private int getFinalRaceGroup(Driver driver) {
        if (driver.isDnf()) {
            return 2;
        }

        return driver.isFinished() ? 0 : 1;
    }

    public void handleLateJoin(Player player) {
        if (this.drivers.containsKey(player.getUniqueId())) {
            DebugManager var10000 = this.plugin.getDebugManager();
            String var10001 = player.getName();
            var10000.logRaceSystem(
                "Processing late join of " +
                    var10001 +
                    " to Heat " +
                    this.id
            );

            // Only add scoreboard if the heat is already loaded or in progress
            if (this.heatState != HeatState.IDLE && this.heatState != HeatState.SETUP) {
                this.plugin.getRaceScoreboardManager().addPlayer(player, this);
                this.plugin.getRaceActionBarManager().addPlayer(player, this);
            }

            this.stopTimeTrialTimer(player);
            this.plugin.getHotbarController().giveHeatHotbar(player, this);
            if (this.plugin.getPacketSender() != null) {
                this.plugin.getPacketSender().applyBoatUtilsToPlayer(
                    player,
                    this.trackNameWS
                );
                this.applyCollisionModeToPlayer(player);
            }

            if (
                this.heatState != HeatState.IDLE &&
                this.heatState != HeatState.SETUP &&
                this.heatState != HeatState.LOADED &&
                this.heatState != HeatState.FINISHED
            ) {
                if (
                    this.heatState != HeatState.RACING &&
                    this.heatState != HeatState.STARTING
                ) {
                    if (
                        !this.isLonely() &&
                        this.heatState != HeatState.PRACTICE &&
                        this.heatState != HeatState.QUALIFYING
                    ) {
                        Driver driver = (Driver) this.drivers.get(
                            player.getUniqueId()
                        );
                        if (driver != null) {
                            if (driver.getStartPosition() <= 0) {
                                driver.setStartPosition(this.drivers.size());
                            }

                            if (!this.startPositions.contains(driver)) {
                                this.startPositions.add(driver);
                                this.livePositions.add(driver);
                            }

                            this.gridManager.teleportDriver(driver);
                            // Apply track game time (day/night cycle) for grid teleport
                            this.plugin.applyTrackGameTime(player, this.trackNameWS);
                            var10000 = this.plugin.getDebugManager();
                            var10001 = player.getName();
                            var10000.logRaceSystem(
                                "Player " +
                                    var10001 +
                                    " teleported to GRID (P" +
                                    driver.getStartPosition() +
                                    ")."
                            );
                        }
                    } else {
                        Location spawnLoc =
                            this.plugin.getDatabaseManager().getTrackSpawn(
                                this.trackNameWS
                            );
                        if (spawnLoc != null) {
                            SchedulerHelper.teleportAsync(this.plugin, player, spawnLoc);
                            // Apply track game time (day/night cycle)
                            this.plugin.applyTrackGameTime(player, this.trackNameWS);
                            boolean collidable = this.collisionMode != CollisionMode.DISABLED;
                            this.plugin.getAPI().spawnBoat(
                                player,
                                false,
                                false,
                                false,
                                collidable
                            );
                            this.plugin.getLonelyController().updatePlayersVisibility(
                                player
                            );
                            this.plugin.getDebugManager().logRaceSystem(
                                "Player " +
                                    player.getName() +
                                    " teleported to Practice/Quali spawn."
                            );
                        }
                    }
                } else {
                    this.plugin.getDebugManager().logRaceSystem(
                        "Player " +
                            player.getName() +
                            " tried to join during RACING/STARTING. Blocked."
                    );
                    player.sendMessage(
                        String.valueOf(ChatColor.RED) +
                            "⚠ You cannot join a race that is already in progress!"
                    );
                }
            }
        }
    }

    public void handleLateLeave(Player player) {
        DebugManager var10000 = this.plugin.getDebugManager();
        String var10001 = player.getName();
        var10000.logRaceSystem(
            "Processing leave of " + var10001 + " from Heat " + this.id
        );
        SchedulerHelper.runTaskFor(this.plugin, player, () -> {
            this.plugin.getLonelyController().clearGhost(player.getUniqueId());
            this.plugin.getRaceScoreboardManager().removePlayer(player);
            this.plugin.getRaceActionBarManager().removePlayer(player);
            this.plugin.resetTrackGameTime(player);
            this.plugin.getAPI().recoverPlayerBoatState(player);
            if (this.plugin.getPacketSender() != null) {
                boolean dbLonely =
                    this.plugin.getDatabaseManager().getLonelyModePlayer(
                        player.getUniqueId()
                    );
                this.plugin.getLonelyController().setLonelyMode(player, dbLonely);
            }

            if (
                this.heatState != HeatState.IDLE &&
                this.heatState != HeatState.SETUP &&
                this.heatState != HeatState.FINISHED
            ) {
                Location spawnLoc = this.plugin.getDatabaseManager().getTrackSpawn(
                    this.trackNameWS
                );
                if (spawnLoc != null) {
                    SchedulerHelper.teleportAsync(this.plugin, player, spawnLoc);
                }
            }
        });
    }

    public int getFinishedCount() {
        return (int) this.drivers.values()
            .stream()
            .filter(Driver::isFinished)
            .count();
    }

    public void updateLivePositions() {
        if (this.isElimination()) {
            List<Driver> sorted = this.drivers.values().stream()
                .sorted((d1, d2) -> Long.compare(d2.getTotalTime(), d1.getTotalTime()))
                .toList();
            this.livePositions = new ArrayList(sorted);
            for (int i = 0; i < sorted.size(); ++i) {
                sorted.get(i).setPosition(i + 1);
            }
        } else if (
            this.heatState != HeatState.QUALIFYING &&
            this.heatState != HeatState.PRACTICE
        ) {
            List<Driver> racingDrivers = this.drivers.values()
                .stream()
                .filter(d -> !d.isFinished() && !d.isDnf())
                .sorted((d1, d2) -> {
                    int lapCompare = Integer.compare(
                        d2.getLapCount(),
                        d1.getLapCount()
                    );
                    if (lapCompare != 0) {
                        return lapCompare;
                    } else {
                        int cpCompare = Integer.compare(
                            d2.getCheckpointsReached(),
                            d1.getCheckpointsReached()
                        );
                        if (cpCompare != 0) {
                            return cpCompare;
                        } else {
                            int latestCP1 = d1.getCheckpointsReached();
                            int latestCP2 = d2.getCheckpointsReached();
                            int currentLapIdx1 = d1.getLapCount();
                            int currentLapIdx2 = d2.getLapCount();
                            Long time1 = d1.getAbsoluteTimeAtProgress(
                                currentLapIdx1,
                                latestCP1
                            );
                            Long time2 = d2.getAbsoluteTimeAtProgress(
                                currentLapIdx2,
                                latestCP2
                            );
                            if (time1 != null && time2 != null) {
                                int timeCompare = Long.compare(time1, time2);
                                if (timeCompare != 0) {
                                    return timeCompare;
                                }
                            }

                            return Long.compare(
                                d1.getTotalTime(),
                                d2.getTotalTime()
                            );
                        }
                    }
                })
                .toList();
            List<Driver> finishedDrivers = this.drivers.values()
                .stream()
                .filter(Driver::isFinished)
                .filter(d -> !d.isDnf())
                .sorted(Comparator.comparingInt(Driver::getPosition))
                .toList();
            this.livePositions = new ArrayList();
            this.livePositions.addAll(finishedDrivers);
            this.livePositions.addAll(racingDrivers);
            int nextPosition = finishedDrivers.size() + 1;

            for (Driver racingDriver : racingDrivers) {
                racingDriver.setPosition(nextPosition++);
            }
        } else {
            List<Driver> sortedDrivers = this.drivers.values()
                .stream()
                .sorted((d1, d2) -> {
                    long t1 =
                        d1.getFastestLap() != null
                            ? d1.getFastestLap().getLapTime()
                            : Long.MAX_VALUE;
                    long t2 =
                        d2.getFastestLap() != null
                            ? d2.getFastestLap().getLapTime()
                            : Long.MAX_VALUE;
                    return t1 == Long.MAX_VALUE && t2 == Long.MAX_VALUE
                        ? Integer.compare(
                              d1.getStartPosition(),
                              d2.getStartPosition()
                          )
                        : Long.compare(t1, t2);
                })
                .toList();
            this.livePositions = new ArrayList(sortedDrivers);

            for (int i = 0; i < sortedDrivers.size(); ++i) {
                ((Driver) sortedDrivers.get(i)).setPosition(i + 1);
            }
        }
    }

    public Driver getFastestLapDriver() {
        return this.fastestLapUUID == null
            ? null
            : (Driver) this.drivers.get(this.fastestLapUUID);
    }

    public boolean isPlayerInActiveHeat(UUID playerUUID) {
        if (this.drivers.containsKey(playerUUID)) {
            return (
                (this.heatState == HeatState.RACING ||
                    this.heatState == HeatState.STARTING ||
                    this.heatState == HeatState.LOADED ||
                    this.heatState == HeatState.PRACTICE)
            );
        }
        
        if (this.round != null && this.round.getEvent() != null) {
            Events event = this.round.getEvent();
            if (event.isSubscriber(playerUUID) || event.isReserve(playerUUID)) {
                return true;
            }
        }
        
        return false;
    }

    public boolean isPlayerActivelyRacing(UUID playerUUID) {
        Driver driver = (Driver) this.drivers.get(playerUUID);
        if (driver == null) {
            return false;
        } else {
            return (
                (this.heatState == HeatState.RACING ||
                    this.heatState == HeatState.STARTING ||
                    this.heatState == HeatState.LOADED) &&
                !driver.isFinished() &&
                !driver.isDnf()
            );
        }
    }

    public boolean isDriver(UUID uuid) {
        return this.drivers.containsKey(uuid);
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Rounds getRound() {
        return this.round;
    }

    public void setRound(Rounds round) {
        this.round = round;
        if (round != null) {
            this.roundId = round.getId();
        }
    }

    public int getRoundId() {
        return this.roundId;
    }

    public void setRoundId(int roundId) {
        this.roundId = roundId;
    }

    public Integer getHeatNumber() {
        return this.heatNumber;
    }

    public String getName() {
        if (this.round == null) {
            return "H" + this.heatNumber;
        } else {
            String roundNum = "R" + this.round.getRoundNumber();
            String var10000;
            switch (this.round.getType()) {
                case PRACTICE -> var10000 = "P";
                case QUALIFICATION -> var10000 = "Q";
                case SPRINT_QUALIFICATION -> var10000 = "SQ";
                case FINAL -> var10000 = "F";
                case ELIMINATION -> var10000 = "E";
                case SPRINT_RACE -> var10000 = "S";
                default -> throw new MatchException(
                    (String) null,
                    (Throwable) null
                );
            }

            String typeCode = var10000;
            return roundNum + typeCode + this.heatNumber;
        }
    }

    public void setHeatNumber(Integer heatNumber) {
        this.heatNumber = heatNumber;
    }

    public Instant getStartTime() {
        return this.startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return this.endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public HeatState getHeatState() {
        return this.heatState;
    }

    public void setHeatState(HeatState heatState) {
        HeatStateMachine.validateTransition(this.heatState, heatState);
        HeatState previous = this.heatState;
        this.previousState = this.heatState;
        this.heatState = heatState;
        if (
            this.plugin != null &&
            this.id > 0 &&
            this.plugin.getRaceEventManager() != null
        ) {
            this.plugin.getRaceEventManager()
                .getDatabaseManager()
                .updateHeatState(this.id, heatState);
        }

        // Render the AI racing line when a heat with AI drivers goes live.
        if (this.plugin != null && this.plugin.getAILineVisualizer() != null) {
            if (heatState == HeatState.RACING && previous != HeatState.RACING) {
                this.plugin.getAILineVisualizer().registerHeat(this);
            } else if (heatState == HeatState.FINISHED) {
                this.plugin.getAILineVisualizer().unregisterHeat(this.id);
            }
        }
    }

    public void setHeatStateForLoad(HeatState heatState) {
        this.heatState = heatState;
    }

    public void startSessionTimer() {
        this.stopSessionTimer();
        if (this.timeLimit != null && this.timeLimit > 0) {
            this.sessionTask = SchedulerHelper.runTaskTimer(
                this.plugin,
                () -> {
                    long remaining = this.getSessionTimeRemaining();
                    if (remaining > 0L) {
                        long seconds = remaining / 1000L;
                        if (
                            seconds == 60L ||
                            seconds == 30L ||
                            seconds == 10L ||
                            (seconds <= 5L && seconds > 0L)
                        ) {
                            EventAnnouncements announcements =
                                this.round != null &&
                                this.round.getEvent() != null
                                    ? this.round.getEvent().getAnnouncements()
                                    : this.plugin.getEventAnnouncements();
                            announcements.broadcastSessionWarning(
                                this,
                                seconds
                            );
                        }
                    }

                    if (remaining <= 0L) {
                        this.plugin.getDebugManager().logRaceSystem(
                            "Session time expired on Heat " + this.id
                        );
                        EventAnnouncements announcements =
                            this.round != null && this.round.getEvent() != null
                                ? this.round.getEvent().getAnnouncements()
                                : this.plugin.getEventAnnouncements();
                        if (
                            this.heatState != HeatState.QUALIFYING &&
                            this.heatState != HeatState.PRACTICE
                        ) {
                            this.finishHeat();
                        } else {
                            announcements.broadcastSessionExpired(this, true);
                            SchedulerHelper.runTaskLater(
                                this.plugin,
                                () -> {
                                    if (
                                        this.heatState ==
                                            HeatState.QUALIFYING ||
                                        this.heatState == HeatState.PRACTICE
                                    ) {
                                        this.finishHeat();
                                    }
                                },
                                3600L
                            );
                        }

                        this.stopSessionTimer();
                    }
                },
                20L,
                20L
            );
        } else {
            this.plugin.getDebugManager().logRaceSystem(
                "Heat " + this.id + " has no time limit set."
            );
        }
    }

    public void stopSessionTimer() {
        if (this.sessionTask != null) {
            this.sessionTask.cancel();
            this.sessionTask = null;
        }
    }

    public Map<UUID, Driver> getDrivers() {
        return this.drivers;
    }

    public Driver getDriver(UUID uuid) {
        return (Driver) this.drivers.get(uuid);
    }

    public List<Driver> getStartPositions() {
        return this.startPositions;
    }

    public UUID getFastestLapUUID() {
        return this.fastestLapUUID;
    }

    public void setFastestLapUUID(UUID fastestLapUUID) {
        this.fastestLapUUID = fastestLapUUID;
    }


    public boolean isErsEnabled() {
        return this.ErsEnabled;
    }

    public void setErsEnabled(boolean a) {
        this.ErsEnabled = a;
    }

    public Integer getTimeLimit() {
        return this.timeLimit;
    }

    public void setTimeLimit(Integer timeLimit) {
        this.timeLimit = timeLimit;
    }

    public Integer getTotalLaps() {
        return this.totalLaps;
    }

    public void setTotalLaps(Integer totalLaps) {
        this.totalLaps = totalLaps;
        this.updateDatabaseConfig();
    }

    public Integer getTotalPits() {
        return this.totalPits;
    }

    public void setTotalPits(Integer totalPits) {
        this.totalPits = totalPits;
        this.updateDatabaseConfig();
    }

    public Integer getStartDelay() {
        return this.startDelay;
    }

    public void setStartDelay(Integer startDelay) {
        this.startDelay = startDelay;
        this.updateDatabaseConfig();
    }

    public Integer getMaxDrivers() {
        return this.maxDrivers;
    }

    public void setMaxDrivers(Integer maxDrivers) {
        this.maxDrivers = maxDrivers;
        this.updateDatabaseConfig();
    }

    private void updateDatabaseConfig() {
        if (
            this.plugin != null &&
            this.id > 0 &&
            this.plugin.getRaceEventManager() != null
        ) {
            this.plugin.getRaceEventManager()
                .getDatabaseManager()
                .updateHeatConfig(
                    this.id,
                    this.totalLaps,
                    this.totalPits,
                    this.maxDrivers
                );
        }
    }

    public boolean isCanReset() {
        return this.canReset;
    }

    public void setCanReset(boolean canReset) {
        this.canReset = canReset;
    }

    public boolean isLonely() {
        return this.lonely;
    }

    public void setLonely(boolean lonely) {
        this.lonely = lonely;
        if (
            this.plugin != null &&
            this.id > 0 &&
            this.plugin.getRaceEventManager() != null
        ) {
            this.plugin.getRaceEventManager()
                .getDatabaseManager()
                .updateHeatLonely(this.id, lonely);
        }
    }

    public void reverseGrid(int percentage) {
        if (
            this.heatState == HeatState.SETUP ||
            this.heatState == HeatState.LOADED
        ) {
            List<Driver> driversToReverse = new ArrayList(this.startPositions);
            int totalToReverse = Math.min(
                (driversToReverse.size() * percentage) / 100,
                driversToReverse.size()
            );
            if (totalToReverse > 1) {
                List<Driver> subList = driversToReverse.subList(
                    0,
                    totalToReverse
                );
                List<Integer> originalPositions = subList
                    .stream()
                    .map(Driver::getStartPosition)
                    .toList();
                Collections.reverse(subList);

                for (int i = 0; i < subList.size(); ++i) {
                    Driver d = (Driver) subList.get(i);
                    int newStartPos = (Integer) originalPositions.get(i);
                    d.setStartPosition(newStartPos);
                    d.setPosition(newStartPos);
                }

                this.reorderGrid();
                if (
                    this.plugin != null &&
                    this.id > 0 &&
                    this.plugin.getRaceEventManager() != null
                ) {
                    Map<UUID, Integer> positions = new HashMap();

                    for (Driver d : this.drivers.values()) {
                        positions.put(d.getUuid(), d.getStartPosition());
                    }

                    this.plugin.getRaceEventManager()
                        .getDatabaseManager()
                        .updateHeatGridPositions(this.id, positions);
                }
            }
        }
    }

    public void reverseFullGrid() {
        if (this.heatState != HeatState.SETUP && this.heatState != HeatState.LOADED) {
            return;
        }
        this.originalPositions.clear();
        for (Driver driver : this.startPositions) {
            this.originalPositions.put(driver.getId(), driver.getStartPosition());
        }
        List<Driver> reversed = new ArrayList<>(this.startPositions);
        Collections.reverse(reversed);
        for (int i = 0; i < reversed.size(); i++) {
            Driver driver = reversed.get(i);
            int newPos = i + 1;
            driver.setStartPosition(newPos);
            driver.setPosition(newPos);
        }
        this.startPositions = reversed;
        this.reorderGrid();
        this.gridReversed = true;
        if (this.plugin != null && this.id > 0 && this.plugin.getRaceEventManager() != null) {
            Map<UUID, Integer> positions = new HashMap<>();
            for (Driver d : this.drivers.values()) {
                positions.put(d.getUuid(), d.getStartPosition());
            }
            this.plugin.getRaceEventManager().getDatabaseManager().updateHeatGridPositions(this.id, positions);
        }
    }

    public void restoreOriginalGrid() {
        if (!this.gridReversed || this.originalPositions.isEmpty()) {
            return;
        }
        for (Driver driver : this.drivers.values()) {
            Integer origPos = this.originalPositions.get(driver.getId());
            if (origPos != null) {
                driver.setStartPosition(origPos);
                driver.setPosition(origPos);
            }
        }
        this.originalPositions.clear();
        this.gridReversed = false;
        this.reorderGrid();
        if (this.plugin != null && this.id > 0 && this.plugin.getRaceEventManager() != null) {
            Map<UUID, Integer> positions = new HashMap<>();
            for (Driver d : this.drivers.values()) {
                positions.put(d.getUuid(), d.getStartPosition());
            }
            this.plugin.getRaceEventManager().getDatabaseManager().updateHeatGridPositions(this.id, positions);
        }
    }

    public boolean isGridReversed() {
        return this.gridReversed;
    }

    public void handleDriverDNF(Driver driver, String reason) {
        if (driver == null || driver.isDnf() || driver.isFinished()) {
            return;
        }
        driver.setDnf(true);
        driver.setEndTime(System.currentTimeMillis());
        driver.setPtpActive(false);
        driver.setPtpEnergy(0.0);
        EventAnnouncements announcements = this.round != null && this.round.getEvent() != null
            ? this.round.getEvent().getAnnouncements()
            : this.plugin.getEventAnnouncements();
        announcements.broadcastDNF(this, driver, reason);
        boolean allFinished = this.drivers.values().stream().allMatch(d -> d.isFinished() || d.isDnf());
        if (allFinished) {
            SchedulerHelper.runTask(this.plugin, (Runnable) this::finishHeat);
        }
    }

    public String getTrackNameWS() {
        return this.trackNameWS;
    }

    public void setTrackNameWS(String trackNameWS) {
        this.trackNameWS = trackNameWS;
        if (trackNameWS != null && !trackNameWS.isEmpty() && this.plugin != null) {
            this.maxDrivers = this.plugin.getTrackIntegrationManager().getGridPositionCount(trackNameWS);
        }
    }

    public int getDriverCount() {
        return this.drivers.size();
    }

    public GridManager getGridManager() {
        return this.gridManager;
    }

    public RaceScoreboardService getScoreboardManager() {
        return this.plugin.getRaceScoreboardManager();
    }

    public RaceActionBarManager getActionBarManager() {
        return this.plugin.getRaceActionBarManager();
    }

    private void validateMandatoryPits() {
        if (this.totalPits != null && this.totalPits > 0) {
            for (Driver driver : this.drivers.values()) {
                if (!driver.hasCompletedMandatoryPits(this.totalPits)) {
                    int missingPits = this.totalPits - driver.getPitstops();
                    EventAnnouncements announcements =
                        this.round != null && this.round.getEvent() != null
                            ? this.round.getEvent().getAnnouncements()
                            : this.plugin.getEventAnnouncements();
                    announcements.broadcastPitStopPenalty(
                        this,
                        driver,
                        missingPits
                    );
                    DebugManager var7 = this.plugin.getDebugManager();
                    String var10001 = String.valueOf(driver.getUuid());
                    var7.logPitStopSystem(
                        "Driver " +
                            var10001 +
                            " penalized for missing pits: " +
                            missingPits
                    );
                    driver.setDnf(true);
                    driver.setPtpActive(false);
                    driver.setPtpEnergy((double) 0.0F);
                }
            }
        }
    }

    public String toString() {
        int var10000 = this.id;
        return (
            "Heats{id=" +
            var10000 +
            ", heatNumber=" +
            this.heatNumber +
            ", state=" +
            String.valueOf(this.heatState) +
            ", drivers=" +
            this.drivers.size() +
            ", totalLaps=" +
            this.totalLaps +
            "}"
        );
    }

    private HeatConfig heatConfig;

    public HeatConfig getHeatConfig() {
        if (heatConfig == null) {
            heatConfig = new HeatConfig();
        }
        return heatConfig;
    }

    public void setHeatConfig(HeatConfig heatConfig) {
        this.heatConfig = heatConfig;
    }

    public void markPositionsDirty() {
        // Flag to indicate that positions need to be recalculated
    }

    public static class DrsRegion {
        private final String type;
        private final Location min;
        private final Location max;

        public DrsRegion(String type, Location min, Location max) {
            this.type = type;
            this.min = min;
            this.max = max;
        }

        public String getType() { return type; }
        public Location getMin() { return min; }
        public Location getMax() { return max; }
    }

}
