package dev.EfraGroup.formulaRacing;

import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import co.aikar.taskchain.BukkitTaskChainFactory;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainFactory;
import dev.EfraGroup.formulaRacing.AI.AIOpponentManager;
import dev.EfraGroup.formulaRacing.AI.AIRacingLineManager;
import dev.EfraGroup.formulaRacing.Command.*;
import dev.EfraGroup.formulaRacing.Listener.AIRacingLineRecorderListener;
import dev.EfraGroup.formulaRacing.Command.TrackEditorCommand.BoatUtilsGroupMode;
import dev.EfraGroup.formulaRacing.Config.PitStopConfigManager;
import dev.EfraGroup.formulaRacing.Controllers.DailyRaceManager;
import dev.EfraGroup.formulaRacing.Controllers.HotbarController;
import dev.EfraGroup.formulaRacing.Controllers.LonelyController;
import dev.EfraGroup.formulaRacing.Controllers.PodiumManager;
import dev.EfraGroup.formulaRacing.Controllers.PartyRaceManager;
import dev.EfraGroup.formulaRacing.Controllers.QuickRaceManager;
import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.Controllers.RaceVoteManager;
import dev.EfraGroup.formulaRacing.Controllers.SpectatorManager;
import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.Event.EventAnnouncements;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiListener;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiManager;
import dev.EfraGroup.formulaRacing.Gui.ReadyCheckManager;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.DrsManager;
import dev.EfraGroup.formulaRacing.Heat.Logic.ERSManager;
import dev.EfraGroup.formulaRacing.Heat.Logic.PTPManager;
import dev.EfraGroup.formulaRacing.Heat.Logic.RaceSession;
import dev.EfraGroup.formulaRacing.Heat.PitStopManager;
import dev.EfraGroup.formulaRacing.Listener.DuelProtectionListener;
import dev.EfraGroup.formulaRacing.Listener.FormulaRacingListener;
import dev.EfraGroup.formulaRacing.Listener.HotbarListener;
import dev.EfraGroup.formulaRacing.Listener.JoinListener;
import dev.EfraGroup.formulaRacing.Listener.PitStopListener;
import dev.EfraGroup.formulaRacing.Listener.RaceCheckpointListener;
import dev.EfraGroup.formulaRacing.Listener.RaceMovementListener;
import dev.EfraGroup.formulaRacing.Listener.RegionListener;
import dev.EfraGroup.formulaRacing.Listener.SpawnBoatCleanupListener;
import dev.EfraGroup.formulaRacing.PlaceHolder.PlaceholderRegister;
import dev.EfraGroup.formulaRacing.Participant.DriverLookup;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.TimeTrial.TimeTrialController;
import dev.EfraGroup.formulaRacing.TVCamera.TVCameraController;
import dev.EfraGroup.formulaRacing.TVCamera.TVCameraListener;
import dev.EfraGroup.formulaRacing.Utils.ClickableMessageUtil;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import dev.EfraGroup.formulaRacing.Utils.DiscordUtils;
import dev.EfraGroup.formulaRacing.Utils.RaceActionBarManager;
import dev.EfraGroup.formulaRacing.Utils.LightningRodListener;
import dev.EfraGroup.formulaRacing.Utils.MojangApiClient;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.RaceScoreboardService;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardDuelsTimeUtils;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeDefaults;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialDuelsAction;
import dev.EfraGroup.formulaRacing.Utils.TimeUtils;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import dev.EfraGroup.formulaRacing.Utils.TranslationUtil;
import dev.EfraGroup.formulaRacing.Utils.WorldEditSelect;
import dev.EfraGroup.formulaRacing.Utils.trackexchange.TrackExchangeManager;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.ScoreboardOwnershipCoordinator;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.RaceScoreboardV2Manager;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.provider.MegavexAdapter;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.provider.ScoreboardAdapter;
import dev.EfraGroup.formulaRacing.Api.ApiManager;
import dev.EfraGroup.formulaRacing.Collisionless.NMSHandlerImpl;
import dev.EfraGroup.formulaRacing.Controllers.LeagueManager;
import dev.EfraGroup.formulaRacing.Heat.GimmickManager;
import dev.EfraGroup.formulaRacing.Hologram.HologramManager;
import dev.EfraGroup.formulaRacing.Visuals.TrackVisualizer;
import dev.EfraGroup.formulaRacing.Weather.WeatherManager;
import dev.EfraGroup.formulaRacing.BoatUtils.OpenBoatUtilsVersion;
import dev.EfraGroup.formulaRacing.Ghost.GhostManager;
import dev.EfraGroup.formulaRacing.WolfMod.WolfMOD;
import dev.EfraGroup.formulaRacing.Medals.MedalManager;
import me.clip.placeholderapi.PlaceholderAPI;
import java.io.File;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
// ServerShutdownEvent is not available in Paper 1.21.8; shutdown logic is in onDisable()
import org.bukkit.plugin.java.JavaPlugin;

public final class FormulaRacing extends JavaPlugin implements Listener {

    private static FormulaRacing instance;
    private boolean papiAvailable = false;
    private final Map<UUID, String> lastTimeTrialTrack = new HashMap();
    private final Map<UUID, String> lastDuelTrack = new HashMap();
    private final Map<UUID, Boolean> lastDuelLonelyStatus = new HashMap();
    private final Map<String, YamlConfiguration> langConfigCache = new ConcurrentHashMap();
    private static final Map<UUID, Boolean> playersWithMod = new HashMap();
    private static final Map<UUID, Integer> playersModVersion = new HashMap();
    private ScoreboardTimeTrialUtils stt;
    private LonelyController lonelyController;
    private PacketSender packetSender;
    private FileManager fileManager;
    public DatabaseManager dm;
    private WorldEditSelect worldEditSelect;
    private TrackExchangeManager trackExchangeManager;
    private MojangApiClient mojangApiClient;
    private TimerUtils timerUtils;
    private RegionListener rcl;
    private TimeUtils tu;
    private NMSHandler nmshandler;
    private APIFormulaRacing api;
    private TVCameraController tvCameraController;
    private TVCameraListener tvCameraListener;
    private DiscordUtils dcu;
    private TimeTrialDuelsAction ttda;
    private TimeTrialDuels ttd;
    private DebugManager debugManager;
    private Metrics metrics;
    private HotbarController hotbarController;
    private RaceEventManager raceEventManager;
    private TrackIntegrationManager trackIntegrationManager;
    private PitStopManager pitStopManager;
    private PitStopConfigManager pitStopConfigManager;
    private SpectatorManager spectatorManager;
    private QuickRaceManager quickRaceManager;
    private PartyRaceManager partyRaceManager;
    private RaceVoteManager raceVoteManager;
    private DrsManager drsManager;
    private PTPManager ptpManager;
    private ERSManager ersManager;
    private TranslationUtil translationUtil;
    private DailyRaceManager dailyRaceManager;
    private RaceActionBarManager raceActionBarManager;
    private RaceScoreboardService raceScoreboardManager;
    private ScoreboardOwnershipCoordinator scoreboardOwnershipCoordinator;
    private MegavexAdapter sharedScoreboardAdapter;
    private TrackVisualizer trackVisualizer;
    private dev.EfraGroup.formulaRacing.Visuals.AILineVisualizer aiLineVisualizer;
    private AIRacingLineRecorderListener recorderListener;
    private EventAnnouncements eventAnnouncements;
    private TimeTrialController timeTrialController;
    private PlaceholderRegister placeholderRegister;
    private PaperCommandManager commandManager;
    private TaskChainFactory taskChainFactory;
    private GuiManager guiManager;
    private ReadyCheckManager readyCheckManager;
    private RaceCheckpointListener raceCheckpointListener;
    private PodiumManager podiumManager;
    private DriverLookup driverLookup;
    private AIOpponentManager aiOpponentManager;
    private AIRacingLineManager aiRacingLineManager;
    private ApiManager apiManager;
    private GimmickManager gimmickManager;
    private LeagueManager leagueManager;
    private dev.EfraGroup.formulaRacing.League.Hologram.LeagueHologramService leagueHologramService;
    private WeatherManager weatherManager;
    private LightningRodListener lightningRodListener;
    private HologramManager hologramManager;
    private GhostManager ghostManager;
    private WolfMOD wolfMod;
    private MedalManager medalManager;

    public static FormulaRacing getInstance() {
        return instance;
    }

    public PaperCommandManager getCommandManager() {
        return this.commandManager;
    }

    public <T> TaskChain<T> newChain() {
        if (this.taskChainFactory == null) return null;
        return this.taskChainFactory.newChain();
    }

    public <T> TaskChain<T> newSharedChain(String name) {
        if (this.taskChainFactory == null) return null;
        return this.taskChainFactory.newSharedChain(name);
    }

    public TVCameraController getTVCameraController() {
        return this.tvCameraController;
    }

    public WorldEditSelect getWorldEditSelect() {
        return this.worldEditSelect;
    }

    public PitStopConfigManager getPitStopConfigManager() {
        return this.pitStopConfigManager;
    }

    public ReadyCheckManager getReadyCheckManager() {
        return this.readyCheckManager;
    }

    public TrackVisualizer getTrackVisualizer() {
        return this.trackVisualizer;
    }

    public dev.EfraGroup.formulaRacing.Visuals.AILineVisualizer getAILineVisualizer() {
        return this.aiLineVisualizer;
    }

    public EventAnnouncements getEventAnnouncements() {
        return this.eventAnnouncements;
    }

    public TranslationUtil getTranslationUtil() {
        return this.translationUtil;
    }

    public LonelyController getLonelyController() {
        return this.lonelyController;
    }

    public PodiumManager getPodiumManager() {
        return this.podiumManager;
    }

    public DriverLookup getDriverLookup() {
        return this.driverLookup;
    }

    public AIOpponentManager getAIOpponentManager() {
        return this.aiOpponentManager;
    }

    public AIRacingLineManager getAIRacingLineManager() {
        return this.aiRacingLineManager;
    }

    public ApiManager getApiManager() {
        if (this.apiManager == null) {
            this.apiManager = new ApiManager(this);
        }
        return this.apiManager;
    }

    public GimmickManager getGimmickManager() {
        if (this.gimmickManager == null) {
            this.gimmickManager = new GimmickManager(this);
        }
        return this.gimmickManager;
    }

    public LeagueManager getLeagueManager() {
        if (this.leagueManager == null) {
            this.leagueManager = new LeagueManager(this);
        }
        return this.leagueManager;
    }

    public dev.EfraGroup.formulaRacing.League.Hologram.LeagueHologramService getLeagueHologramService() {
        if (this.leagueHologramService == null) {
            this.leagueHologramService = new dev.EfraGroup.formulaRacing.League.Hologram.LeagueHologramService(this);
        }
        return this.leagueHologramService;
    }

    public WeatherManager getWeatherManager() {
        if (this.weatherManager == null) {
            this.weatherManager = new WeatherManager(this);
        }
        return this.weatherManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public GhostManager getGhostManager() {
        return ghostManager;
    }

    public WolfMOD getWolfMod() {
        return wolfMod;
    }

    public MedalManager getMedalManager() {
        if (this.medalManager == null) {
            this.medalManager = new MedalManager(this);
        }
        return this.medalManager;
    }

    public Map<String, TrackLeaderboard> getTrackLeaderboards() {
        return this.trackLeaderboards;
    }

    public void onEnable() {
        instance = this;
        FRThemeDefaults.load(this);
        SchedulerHelper.init(this);
        try {
            this.fileManager = new FileManager(this);
            this.timeTrialController = new TimeTrialController(this);
            this.debugManager = new DebugManager(this, this.fileManager);
            this.driverLookup = new DriverLookup();
            this.dm = new DatabaseManager(this, this.fileManager);
            this.dm.migrateNullPlayerColors();
            this.hologramManager = new HologramManager(this);
            this.translationUtil = new TranslationUtil(this, this.dm);
            this.tu = new TimeUtils();
            this.worldEditSelect = new WorldEditSelect();
            this.trackExchangeManager = new TrackExchangeManager(this, this.dm);
            this.mojangApiClient = new MojangApiClient();
            this.dcu = new DiscordUtils();
            DiscordUtils.init(
                this.getConfig().getString("discord.webhook-url", ""),
                this.getConfig().getString("discord.role-id", "")
            );
            this.hotbarController = new HotbarController(this, this.dm);
            this.scoreboardOwnershipCoordinator =
                new ScoreboardOwnershipCoordinator();
            this.sharedScoreboardAdapter = new MegavexAdapter(
                this,
                this.getConfig().getInt("scoreboard.max-rows", 15)
            );
            this.stt = new ScoreboardTimeTrialUtils(
                this,
                this.dm,
                this.sharedScoreboardAdapter,
                this.scoreboardOwnershipCoordinator
            );
            this.packetSender = new PacketSender(this.dm, this);
            this.timerUtils = new TimerUtils(this, this.dm);
            this.tvCameraController = new TVCameraController(this, this.dm);
            this.tvCameraController.loadCameras();
            this.tvCameraListener = new TVCameraListener(this, this.tvCameraController);
            this.lonelyController = new LonelyController(this.dm, this);
            this.nmshandler = new NMSHandlerImpl();
            this.api = new APIFormulaRacing(this, this.dm, this.nmshandler);
            this.ttda = new TimeTrialDuelsAction(this, this.dm);
            this.trackIntegrationManager = new TrackIntegrationManager(this);
            this.pitStopConfigManager = new PitStopConfigManager(this);
            this.pitStopManager = new PitStopManager(
                this,
                this.dm,
                this.pitStopConfigManager
            );
            this.raceEventManager = new RaceEventManager(this);
            this.spectatorManager = new SpectatorManager(this);
            this.readyCheckManager = new ReadyCheckManager(this);
            this.raceCheckpointListener = new RaceCheckpointListener(this);
            this.raceActionBarManager = new RaceActionBarManager(this);
            this.raceScoreboardManager = new RaceScoreboardV2Manager(
                this,
                this.sharedScoreboardAdapter,
                this.scoreboardOwnershipCoordinator
            );
            this.getLogger().info(
                "[FormulaRacing] Unified scoreboard enabled (Megavex)."
            );
            this.trackVisualizer = new TrackVisualizer(this);
            this.aiLineVisualizer = new dev.EfraGroup.formulaRacing.Visuals.AILineVisualizer(this);
            this.eventAnnouncements = new EventAnnouncements(this);
            this.quickRaceManager = new QuickRaceManager(
                this,
                this.raceEventManager,
                this.dm
            );
            this.partyRaceManager = new PartyRaceManager(
                this,
                this.raceEventManager,
                this.dm
            );
            this.drsManager = new DrsManager(
                new RaceSession(this),
                this,
                this.packetSender
            );
            this.ptpManager = new PTPManager(this);
            this.ersManager = new ERSManager(this);
            this.raceVoteManager = new RaceVoteManager(
                this,
                this.dm,
                this.quickRaceManager
            );
            this.podiumManager = new PodiumManager(this);
            this.aiOpponentManager = new AIOpponentManager(this);
            this.aiRacingLineManager = new AIRacingLineManager(this);
            this.aiRacingLineManager.initialize();
            this.apiManager = new ApiManager(this);
            this.apiManager.init();
            this.gimmickManager = new GimmickManager(this);
            this.leagueManager = new LeagueManager(this);
            this.weatherManager = new WeatherManager(this);
            this.ghostManager = new GhostManager(this);
            this.wolfMod = new WolfMOD(this);
            this.raceEventManager.loadActiveEventsFromDatabase();
            SchedulerHelper.runTask(this, () -> {
                List<Integer> staleEventIds = new java.util.ArrayList<>();
                for (Events evt : this.raceEventManager.getActiveEvents()) {
                    String name = evt.getDisplayName();
                    if (name.startsWith("QuickRace_") || name.startsWith("PartyRace_") || name.startsWith("DuelRace_")) {
                        staleEventIds.add(evt.getId());
                    }
                }
                for (int id : staleEventIds) {
                    this.raceEventManager.removeEvent(id);
                }
                try {
                    if (this.dm != null) {
                        this.dm.deleteAllParties();
                    }
                } catch (java.sql.SQLException e) {
                    if (this.debugManager != null) {
                        this.debugManager.logRaceSystem(                    "[FormulaRacing] Error cleaning party races on startup: " + e.getMessage());
                    }
                }
            });
            this.dailyRaceManager = new DailyRaceManager(this);
            this.dailyRaceManager.start();
            ScoreboardDuelsTimeUtils scoreboardDuelsUtils =
                new ScoreboardDuelsTimeUtils(
                    this,
                    this.dm,
                    this.ttda,
                    null,
                    this.sharedScoreboardAdapter,
                    this.scoreboardOwnershipCoordinator
                );
            this.ttd = new TimeTrialDuels(
                this,
                this.dm,
                this.packetSender,
                this.ttda,
                scoreboardDuelsUtils
            );
            this.ttda.setTimeTrialDuels(this.ttd);
            scoreboardDuelsUtils.setTimeTrialDuels(this.ttd);
            this.rcl = new RegionListener(
                this,
                this.dm,
                this.timerUtils,
                this.packetSender,
                this.stt,
                this.ttda,
                this.ttd,
                this.timeTrialController
            );
            this.stt.startAutoUpdate();
            this.getServer()
                .getMessenger()
                .registerOutgoingPluginChannel(this, "openboatutils:settings");
            this.registerModChannel();
            this.commandManager = new PaperCommandManager(this);
            try {
                this.taskChainFactory = BukkitTaskChainFactory.create(this);
            } catch (Throwable e) {
                this.getLogger().warning("[FormulaRacing] TaskChainFactory not available on this server version (Folia). TaskChain disabled.");
                this.taskChainFactory = null;
            }
            this.registerCommandContexts();
            this.registerCommandCompletions();
            this.guiManager = new GuiManager();
            this.registerListeners();
            this.registerCommands();
            this.getCommand("resetcp").setExecutor(new ResetCheckpointCommand(this, this.dm));
            this.registerPlaceholders();
            this.loadLeaderboards();
            this.startLeaderboardUpdater();
            SchedulerHelper.runAsync(this, () ->
                this.dm.cleanOrphanedCheckpoints()
            );
            // Delayed safety net: remove any orphan hologram armor stands left from failed shutdown cleanup
            SchedulerHelper.runDelayedTask(this, () ->
                HologramManager.removeOrphanStands(), 100L
            );
            // bStats telemetry — the plugin ID now comes from config.yml (bstats.plugin-id).
            // Register the plugin at https://bstats.org to get an ID, then set it there.
            // ID 0 or absent disables telemetry (the old hardcoded 00000 placeholder
            // silently submitted nothing).
            int bStatsPluginId = this.getConfig().getInt("bstats.plugin-id", 0);
            if (bStatsPluginId > 0) {
                try {
                    this.metrics = new Metrics(this, bStatsPluginId);
                    this.metrics.addCustomChart(new SimplePie("database_type",
                        () -> this.dm.getDatabaseType().name().toLowerCase()));
                    this.metrics.addCustomChart(new SingleLineChart("tracks",
                        () -> this.dm.getAllTracks().size()));
                    this.metrics.addCustomChart(new SingleLineChart("online_players",
                        () -> Bukkit.getOnlinePlayers().size()));
                    this.metrics.addCustomChart(new SingleLineChart("league_count",
                        () -> this.getLeagueManager().getAllLeagues().size()));
                } catch (Exception e) {
                    this.getLogger().warning("[FormulaRacing] Falha ao iniciar bStats: " + e.getMessage());
                }
            } else {
                this.getLogger().info(
                    "[FormulaRacing] bStats desativado. Defina 'bstats.plugin-id' no config.yml para ativar telemetria."
                );
            }
            if (this.debugManager != null) {
                this.getLogger().info(
                    "[FormulaRacing] Plugin enabled successfully!"
                );
                this.getLogger().info(
                    "[FormulaRacing] Database connected successfully!"
                );
            }
        } catch (Exception var2) {
            if (this.debugManager != null) {
                this.debugManager.logRaceSystem(
                    "[FormulaRacing] Error initializing plugin: " +
                        var2.getMessage()
                );
            } else {
                this.getLogger().severe(
                    "[FormulaRacing] Critical error initializing plugin (DebugManager null): " +
                        var2.getMessage()
                );
            }

            var2.printStackTrace();
            this.setEnabled(false);
        }
    }

    public void onDisable() {
        if (this.debugManager != null) {
            this.getLogger().info("[FormulaRacing] Disabling plugin...");
        }

        if (this.dm != null) {
            this.dm.closePool();
        }

        if (this.apiManager != null) {
            this.apiManager.shutdown();
        }

        if (this.placeholderRegister != null) {
            this.placeholderRegister.stop();
            this.placeholderRegister = null;
        }

        if (this.guiManager != null) {
            this.guiManager.closeAll();
        }
        try {
            this.trackLeaderboards.values().forEach(TrackLeaderboard::removeHologram);
        } catch (Throwable t) {
            if (this.debugManager != null) {
                this.debugManager.logRaceSystem("[FormulaRacing] Error removing leaderboard holograms on disable: " + t);
            }
        }
        this.trackLeaderboards.clear();
        HologramManager.removeAllHologramStands();
        if (this.dailyRaceManager != null) {
            this.dailyRaceManager.stop();
        }

        if (this.pitStopManager != null) {
            this.pitStopManager.clear();
        }

        if (this.spectatorManager != null) {
            this.spectatorManager.shutdown();
        }

        if (this.raceScoreboardManager != null) {
            this.raceScoreboardManager.shutdown();
        }

        if (this.sharedScoreboardAdapter != null) {
            this.sharedScoreboardAdapter.shutdown();
        }

        if (this.aiRacingLineManager != null) {
            this.aiRacingLineManager.saveAllRacingLines();
            this.aiRacingLineManager.getRecorder().cleanup();
        }

        if (this.aiLineVisualizer != null) {
            this.aiLineVisualizer.shutdown();
        }

        if (this.recorderListener != null) {
            this.recorderListener.cleanup();
        }

        if (this.aiOpponentManager != null) {
            this.aiOpponentManager.clearAll();
        }

        if (this.quickRaceManager != null) {
            this.quickRaceManager.shutdown();
        }

        if (this.api != null) {
            this.api.clearAllBoats();
        }

        if (this.raceEventManager != null) {
            for (Events event : this.raceEventManager.getActiveEvents()) {
                String name = event.getDisplayName();
                if (name.startsWith("QuickRace_") || name.startsWith("PartyRace_") || name.startsWith("DuelRace_")) {
                    this.raceEventManager.unloadEvent(event.getId());
                } else {
                    this.raceEventManager.removeEvent(event.getId());
                }
            }
            this.raceEventManager.shutdown();
        }

        if (this.lightningRodListener != null) {
            this.lightningRodListener.shutdown();
        }

        if (this.ghostManager != null) {
            this.ghostManager.shutdown();
        }

        if (this.tvCameraListener != null) {
            this.tvCameraListener.shutdown();
        }

        if (this.rcl != null) {
            this.rcl.shutdown();
        }

        if (this.weatherManager != null) {
            this.weatherManager.stopUpdateTask();
        }

        if (this.raceActionBarManager != null) {
            this.raceActionBarManager.shutdown();
        }

        dev.EfraGroup.formulaRacing.Utils.LightningRodManager.clearAllToggles();

        playersWithMod.clear();
        playersModVersion.clear();

        try {
            if (this.dm != null) {
                this.dm.deleteAllParties();
            }
        } catch (SQLException var2) {
            if (this.debugManager != null) {
                this.debugManager.logRaceSystem(
                    "Erro ao limpar dados no banco durante o desligamento."
                );
            }
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new GuiListener(this.guiManager), this);
        Bukkit.getPluginManager().registerEvents(
            new HotbarListener(this, this.hotbarController),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new FormulaRacingListener(
                this,
                this.timerUtils,
                this.api,
                this.dm,
                this.packetSender
            ),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new JoinListener(
                this,
                this.dm,
                this.packetSender,
                this.hotbarController
            ),
            this
        );
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(this.rcl, this);
        Bukkit.getPluginManager().registerEvents(
            this.tvCameraListener,
            this
        );
        Bukkit.getPluginManager().registerEvents(this.lonelyController, this);
        Bukkit.getPluginManager().registerEvents(
            new DuelCommand(this, dm, ttd, ttda, packetSender),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new DuelProtectionListener(this, this.dm),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new SpawnBoatCleanupListener(this),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            this.raceCheckpointListener,
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new RaceMovementListener(this, this.raceEventManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new dev.EfraGroup.formulaRacing.Listener.JumpStartListener(this),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new PitStopListener(
                this,
                this.raceEventManager,
                this.pitStopManager
            ),
            this
        );
        this.recorderListener = new AIRacingLineRecorderListener(this);
        Bukkit.getPluginManager().registerEvents(
            this.recorderListener,
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new dev.EfraGroup.formulaRacing.Listener.AINpcListener(this),
            this
        );
        this.lightningRodListener = new LightningRodListener(this);
        this.lightningRodListener.start();
    }

    private void registerPlaceholders() {
        this.papiAvailable = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        if (!this.papiAvailable) {
            this.getLogger().info(
                "[FormulaRacing] PlaceholderAPI não encontrado. Placeholder %open_tracks_count% não será registrado."
            );
            return;
        }

        this.placeholderRegister = new PlaceholderRegister(this);
        if (this.placeholderRegister.registerExpansion()) {
            this.getLogger().info(
                "[FormulaRacing] Placeholder %open_tracks_count% registrado com sucesso."
            );
            return;
        }

        this.getLogger().warning(
            "[FormulaRacing] Não foi possível registrar o placeholder %open_tracks_count%."
        );
        this.placeholderRegister = null;
    }

    private void registerCommands() {
        try {
            this.commandManager.registerCommand(new AdminCommand(this));
            this.commandManager.registerCommand(new BoatCommand(api));
            this.commandManager.registerCommand(new LonelyCommand(this));
            this.commandManager.registerCommand(
                new CamCommand(this, this.tvCameraController, this.tvCameraListener)
            );
            this.commandManager.registerCommand(new EventCommand(this));
            this.commandManager.registerCommand(new RoundCommand(this));
            this.commandManager.registerCommand(new HeatCommand(this));
            this.commandManager.registerCommand(new TrackCommand(this));
            this.commandManager.registerCommand(new SettingsCommand(this));
            this.commandManager.registerCommand(new LanguageCommand(this));
            this.commandManager.registerCommand(
                    new AnnounceCommand()
            );
            this.commandManager.registerCommand(new TimeTrialCommand(this));
            this.commandManager.registerCommand(
                new TrackEditorCommand(
                    this,
                    this.dm,
                    this.packetSender,
                    this.worldEditSelect,
                    this.trackExchangeManager
                )
            );
            this.commandManager.registerCommand(new PartyCommand(this));
            this.commandManager.registerCommand(
                new DuelCommand(this, dm, ttd, ttda, packetSender)
            );
            this.commandManager.registerCommand(new RaceCommand(this));
            this.commandManager.registerCommand(new VoteRaceCommand(this));
            this.commandManager.registerCommand(new DailyRaceCommand(this));
            this.commandManager.registerCommand(new PitCommand(this));
            this.commandManager.registerCommand(new PodiumCommand(this));
            this.commandManager.registerCommand(new HotbarItemsCommand(this));
            this.commandManager.registerCommand(new GhostCommand(this));
            this.commandManager.registerCommand(new UnghostCommand(this));
            this.commandManager.registerCommand(new AICommand(this));
            this.commandManager.registerCommand(new ToggleRodsCommand(this));
            this.commandManager.registerCommand(new LeagueCommand(this));
            this.commandManager.registerCommand(new ApiCommand());
            this.commandManager.registerCommand(new DebugCommand(this));
            this.commandManager.registerCommand(new EliminationCommand(this));
            this.commandManager.registerCommand(new GimmickCommand(this));
            this.commandManager.registerCommand(new OpenBoatUtilsCommand(this));
            this.commandManager.registerCommand(new ReverseGridCommand(this));
            this.commandManager.registerCommand(new WeatherCommand(this));
        } catch (Exception e) {
            if (this.debugManager != null) {
                this.debugManager.logRaceSystem(
                    "[FormulaRacing] Erro ao registrar comandos: " +
                        e.getMessage()
                );
            }
        }
    }

    public String getDirectTranslation(String key, String langCode) {
        YamlConfiguration config = (YamlConfiguration) this.langConfigCache.get(
            langCode
        );
        if (config == null) {
            File langFile = new File(
                this.getDataFolder(),
                "lang/" + langCode + ".yml"
            );
            if (!langFile.exists()) {
                langFile = new File(this.getDataFolder(), "lang/en_US.yml");
                if (!langFile.exists()) {
                    return "§c[Lang Error] File not found: " + langCode;
                }
            }

            config = YamlConfiguration.loadConfiguration(langFile);
            this.langConfigCache.put(langCode, config);
        }

        String message = config.getString(key);
        return message == null
            ? "§c[Lang Error] Key '" +
              key +
              "' not found in " +
              langCode +
              ".yml"
            : ChatColor.translateAlternateColorCodes('&', message);
    }

    public List<String> getTranslationList(
        String key,
        String langCode,
        String... placeholders
    ) {
        YamlConfiguration config = (YamlConfiguration) this.langConfigCache.get(
            langCode
        );
        if (config == null) {
            File langFile = new File(
                this.getDataFolder(),
                "lang/" + langCode + ".yml"
            );
            if (!langFile.exists()) {
                langFile = new File(this.getDataFolder(), "lang/en_US.yml");
                if (!langFile.exists()) {
                    return Collections.singletonList(
                        "§c[Lang Error] File not found: " + langCode
                    );
                }
            }

            config = YamlConfiguration.loadConfiguration(langFile);
            this.langConfigCache.put(langCode, config);
        }

        List<String> list = config.getStringList(key);
        if (list == null || list.isEmpty()) {
            String single = config.getString(key);
            if (single == null) {
                return Collections.singletonList(
                    "§c[Lang Error] Key '" +
                        key +
                        "' not found in " +
                        langCode +
                        ".yml"
                );
            }

            list = Collections.singletonList(single);
        }

        List<String> translated = new ArrayList();

        for (String line : list) {
            String msg = line;
            if (placeholders != null && placeholders.length > 0) {
                for (int i = 0; i < placeholders.length - 1; i += 2) {
                    msg = msg.replace(placeholders[i], placeholders[i + 1]);
                }

                msg = this.applyLegacyStringPlaceholders(msg, placeholders);
            }

            translated.add(ChatColor.translateAlternateColorCodes('&', msg));
        }

        return translated;
    }

    public void reloadLangCache() {
        this.langConfigCache.clear();
    }

    public String getTranslation(
        String key,
        String langCode,
        String... placeholders
    ) {
        String message = this.getDirectTranslation(key, langCode);
        if (placeholders != null && placeholders.length > 0) {
            for (int i = 0; i < placeholders.length - 1; i += 2) {
                String placeholder = placeholders[i];
                String value = placeholders[i + 1];
                if (message.contains(placeholder)) {
                    message = message.replace(placeholder, value != null ? value : "");
                }
            }

            message = this.applyLegacyStringPlaceholders(message, placeholders);
        }

        return message;
    }

    private String applyLegacyStringPlaceholders(
        String message,
        String... placeholders
    ) {
        if (placeholders == null || placeholders.length < 2) {
            return message;
        }

        List<String> values = new ArrayList();

        for (int i = 1; i < placeholders.length; i += 2) {
            values.add(placeholders[i]);
        }

        for (int i = 0; i < values.size(); ++i) {
            CharSequence value = values.get(i);
            message = message.replace(
                "%" + (i + 1) + "$s",
                value != null ? value : ""
            );
        }

        for (String value : values) {
            int idx = message.indexOf("%s");
            if (idx < 0) {
                break;
            }

            message =
                message.substring(0, idx) + (value != null ? value : "") + message.substring(idx + 2);
        }

        return message;
    }

    public String applyPapi(Player player, String message) {
        if (message == null) return null;
        if (this.papiAvailable) {
            try {
                message = PlaceholderAPI.setPlaceholders(player, message);
            } catch (Exception ignored) {}
        }
        message = message.replace("%img_java%", "\uE000").replace("%img_bedrock%", "\uE001");
        return message;
    }

    /**
     * Resolves WolfLang/PAPI placeholders first, then applies FR {placeholder} substitution.
     * This allows WolfLang values to contain {track}, {player}, etc. markers.
     */
    private String resolveForPlayer(Player player, String key, String langCode, String... placeholders) {
        String raw = this.getDirectTranslation(key, langCode);
        String papiResolved = applyPapi(player, raw);
        // Apply FR placeholder substitution on the PAPI-resolved text
        if (placeholders != null && placeholders.length > 0) {
            for (int i = 0; i < placeholders.length - 1; i += 2) {
                String ph = placeholders[i];
                String val = placeholders[i + 1];
                if (papiResolved.contains(ph)) {
                    papiResolved = papiResolved.replace(ph, val);
                }
            }
            papiResolved = this.applyLegacyStringPlaceholders(papiResolved, placeholders);
        }
        return ChatColor.translateAlternateColorCodes('&', papiResolved);
    }

    public void sendMessage(Player player, String key, String... placeholders) {
        String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
        player.sendMessage(resolveForPlayer(player, key, langCode, placeholders));
    }

    public void sendMessage(
        CommandSender sender,
        String key,
        String... placeholders
    ) {
        if (sender instanceof Player player) {
            sendMessage(player, key, placeholders);
            return;
        }
        String langCode = "en_US";
        String message = this.getTranslation(key, langCode, placeholders);
        sender.sendMessage(message);
    }

    public void startLeaderboardUpdater() {
        // Pulls interval from config (e.g. 18000 ticks) or defaults to 300
        long ticks = this.getConfig().getLong("leaderboards.updateticks", 300L);

        SchedulerHelper.runTaskTimer(
                this,
                () -> {
                    // Only process if players are online to save database resources
                    if (!Bukkit.getOnlinePlayers().isEmpty()) {
                        this.trackLeaderboards.values().forEach(leaderboard -> {
                            // Update both holograms for each registered track
                            leaderboard.updateJavaLeaderboard();
                            leaderboard.updateBedrockLeaderboard();
                        });
                    }
                },
                100L, // Initial delay
                ticks  // Repeat interval
        );
    }

    private final Map<String, TrackLeaderboard> trackLeaderboards = new HashMap<>();
    private boolean leaderboardsLoaded = false;

    public TrackLeaderboard getOrCreateLeaderboard(String trackName, Location defaultLocation) {
        return trackLeaderboards.computeIfAbsent(trackName, name -> {
            return new TrackLeaderboard(this, name, defaultLocation, this.getDatabaseManager());
        });
    }

    private void loadLeaderboards() {
        if (this.leaderboardsLoaded) {
            return;
        }
        this.leaderboardsLoaded = true;
        
        if (this.debugManager != null) {
            this.debugManager.logDatabaseOperations("[FormulaRacing] Loading leaderboards...");
        }

        try {
            List<String> tracks = this.dm.getAllTracks();

            for (String trackName : tracks) {
                // Load each board's own location (fallback to the legacy shared
                // column when the per-type column is empty).
                Location savedJava = this.dm.getHologramLocation(trackName, "java");
                Location savedBedrock = this.dm.getHologramLocation(trackName, "bedrock");
                Location fallback = savedJava != null ? savedJava
                        : this.dm.getHologramLocation(trackName);
                Location baseLoc = savedJava != null ? savedJava
                        : (savedBedrock != null ? savedBedrock : fallback);

                if (baseLoc != null) {
                    // Use only ONE instance:
                    // The getOrCreateLeaderboard method returns the single instance
                    TrackLeaderboard leaderboard = this.getOrCreateLeaderboard(trackName, baseLoc);

                    // Apply each board's own location independently.
                    if (savedJava != null) {
                        leaderboard.setLocation(savedJava, "java");
                    }
                    if (savedBedrock != null) {
                        leaderboard.setLocation(savedBedrock, "bedrock");
                    }

                    // Call specific updates within the same instance
                    leaderboard.updateJavaLeaderboard();
                    leaderboard.updateBedrockLeaderboard();

                    if (this.debugManager != null) {
                        this.debugManager.logDatabaseOperations("[FormulaRacing] Leaderboards (Java/Bedrock) carregados para: " + trackName);
                    }

                } else if (this.debugManager != null) {
                    this.debugManager.logDatabaseOperations(
                            "[FormulaRacing] ⚠️ Pista '" + trackName + "' sem localização definida."
                    );
                }
            }
        } catch (Exception e) {
            if (this.debugManager != null) {
                this.debugManager.logRaceSystem("[FormulaRacing] Erro ao carregar leaderboards: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    private void registerModChannel() {
        this.getServer()
            .getMessenger()
            .registerIncomingPluginChannel(
                this,
                "openboatutils:settings",
                (channel, player, message) -> {
                    if (channel.equals("openboatutils:settings")) {
                        ByteBuffer buf = ByteBuffer.wrap(message);
                        short packetId = buf.getShort();
                        int versionId = buf.getInt();
                        if (packetId == 0) {
                            setPlayerHasMod(player.getUniqueId(), true);
                            setPlayerModVersion(
                                player.getUniqueId(),
                                versionId
                            );
                            OpenBoatUtilsVersion.setPlayerVersion(player.getUniqueId(), versionId);
                            if (this.debugManager != null) {
                                DebugManager var10000 = this.debugManager;
                                String var10001 = player.getName();
                                var10000.logPacketHandling(
                                    "[FormulaRacing] Player " +
                                        var10001 +
                                        " entrou com OpenBoatUtils v" +
                                        versionId
                                );
                            }
                        }
                    }
                }
            );
    }

    public static void setPlayerHasMod(UUID uuid, boolean hasMod) {
        playersWithMod.put(uuid, hasMod);
        if (!hasMod) {
            playersModVersion.remove(uuid);
        }
    }

    public static boolean hasOpenBoatUtilsMod(Player player) {
        return (Boolean) playersWithMod.getOrDefault(
            player.getUniqueId(),
            false
        );
    }

    public static void setPlayerModVersion(UUID uuid, int version) {
        playersModVersion.put(uuid, version);
    }

    public static int getPlayerModVersion(Player player) {
        return (Integer) playersModVersion.getOrDefault(
            player.getUniqueId(),
            -1
        );
    }

    public TimerUtils getTimerUtils() {
        return this.timerUtils;
    }

    public ScoreboardTimeTrialUtils getScoreboardTimeTrialUtils() {
        return this.stt;
    }

    public FileManager getFileManager() {
        return this.fileManager;
    }

    public DebugManager getDebugManager() {
        if (this.debugManager == null) {
            if (this.fileManager == null) {
                this.fileManager = new FileManager(this);
            }

            this.debugManager = new DebugManager(this, this.fileManager);
        }

        return this.debugManager;
    }

    public DatabaseManager getDatabaseManager() {
        return this.dm;
    }

    public MojangApiClient getMojangApiClient() {
        return this.mojangApiClient;
    }

    public TrackIntegrationManager getTrackIntegrationManager() {
        return this.trackIntegrationManager;
    }

    public PitStopManager getPitStopManager() {
        return this.pitStopManager;
    }

    public RaceEventManager getRaceEventManager() {
        return this.raceEventManager;
    }

    public SpectatorManager getSpectatorManager() {
        return this.spectatorManager;
    }

    public QuickRaceManager getQuickRaceManager() {
        return this.quickRaceManager;
    }

    public PartyRaceManager getPartyRaceManager() {
        return this.partyRaceManager;
    }

    public RaceActionBarManager getRaceActionBarManager() {
        return this.raceActionBarManager;
    }

    public RaceScoreboardService getRaceScoreboardManager() {
        return this.raceScoreboardManager;
    }

    public ScoreboardOwnershipCoordinator getScoreboardOwnershipCoordinator() {
        return this.scoreboardOwnershipCoordinator;
    }

    public ScoreboardAdapter getSharedScoreboardAdapter() {
        return this.sharedScoreboardAdapter;
    }

    public RaceVoteManager getRaceVoteManager() {
        return this.raceVoteManager;
    }

    public DailyRaceManager getDailyRaceManager() {
        return this.dailyRaceManager;
    }

    public RaceCheckpointListener getRaceCheckpointListener() {
        return this.raceCheckpointListener;
    }

    public RegionListener getRegionListener() {
        return this.rcl;
    }

    public APIFormulaRacing getAPI() {
        return this.api;
    }

    public LightningRodListener getLightningRodListener() {
        return this.lightningRodListener;
    }

    public PacketSender getPacketSender() {
        return this.packetSender;
    }

    public TimeTrialDuelsAction getTimeTrialDuelsAction() {
        return this.ttda;
    }

    public TimeTrialController getTimeTrialController() {
        return this.timeTrialController;
    }

    public void setLastTimeTrialTrack(UUID playerId, String track) {
        this.lastTimeTrialTrack.put(playerId, track);
    }

    public String getLastTimeTrialTrack(UUID playerId) {
        return (String) this.lastTimeTrialTrack.get(playerId);
    }

    public void setLastDuelTrack(UUID playerId, String track) {
        this.lastDuelTrack.put(playerId, track);
    }

    public String getLastDuelTrack(UUID playerId) {
        return (String) this.lastDuelTrack.get(playerId);
    }

    public void clearLastDuelTrack(UUID playerId) {
        this.lastDuelTrack.remove(playerId);
    }

    /**
     * Applies the track's configured game time (day/night cycle) to a player.
     * Uses player.setPlayerTime so each player sees their own time (client-side only).
     * If the track has no game_time configured, resets to the world's normal time.
     */
    public void applyTrackGameTime(Player player, String trackName) {
        if (player == null || trackName == null) return;
        long gameTime = this.dm.getTrackGameTime(trackName);
        if (gameTime >= 0) {
            player.setPlayerTime(gameTime, false);
        } else {
            player.resetPlayerTime();
        }
    }

    /**
     * Resets the player's time back to the world's normal time.
     * Should be called when a player leaves a time trial or heat.
     */
    public void resetTrackGameTime(Player player) {
        if (player == null) return;
        player.resetPlayerTime();
    }

    public void clearLastTimeTrialTrack(UUID playerId) {
        this.lastTimeTrialTrack.remove(playerId);
    }

    public void clearLastDuelLonelyStatus(UUID playerId) {
        this.lastDuelLonelyStatus.remove(playerId);
    }

    public TimeTrialDuels getTimeTrialDuels() {
        return this.ttd;
    }

    public HotbarController getHotbarController() {
        return this.hotbarController;
    }

    public int getOpenBoatUtilsVersion(UUID playerUUID) {
        return dev.EfraGroup.formulaRacing.BoatUtils.OpenBoatUtilsVersion.getPlayerVersion(playerUUID);
    }

    public void setOpenBoatUtilsVersion(UUID playerUUID, int version) {
        dev.EfraGroup.formulaRacing.BoatUtils.OpenBoatUtilsVersion.setPlayerVersion(playerUUID, version);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        setPlayerHasMod(uuid, false);
        playersWithMod.remove(uuid);
        playersModVersion.remove(uuid);

        // Clean up per-player caches to prevent memory leaks
        this.clearLastTimeTrialTrack(uuid);
        this.clearLastDuelTrack(uuid);
        this.clearLastDuelLonelyStatus(uuid);

        if (this.trackVisualizer != null) {
            this.trackVisualizer.stopView(player);
        }

        if (this.spectatorManager != null) {
            this.spectatorManager.removeSpectator(player);
        }

        if (this.raceEventManager != null) {
            this.raceEventManager.removePlayerFromEvent(uuid);
        }

        if (this.timeTrialController != null) {
            this.timeTrialController.endSession(uuid);
        }

        if (this.timerUtils != null) {
            this.timerUtils.stopTimer(player);
        }

        if (this.raceActionBarManager != null) {
            this.raceActionBarManager.removePlayer(player);
        }

        if (this.scoreboardOwnershipCoordinator != null) {
            this.scoreboardOwnershipCoordinator.clear(uuid);
        }

        if (this.leagueManager != null) {
            this.leagueManager.deselectPlayer(uuid);
        }

        this.rcl.cleanupPlayer(uuid);
        if (this.raceCheckpointListener != null) {
            this.raceCheckpointListener.cleanupPlayer(uuid);
        }
        if (this.driverLookup != null) {
            this.driverLookup.unregister(uuid);
        }
        // Reset player time back to world time
        this.resetTrackGameTime(player);

        if (this.ghostManager != null) {
            this.ghostManager.cleanupPlayer(uuid);
        }

        if (this.medalManager != null) {
            this.medalManager.clearPending(uuid);
        }

        this.api.removePlayerBoat(uuid);
    }

    private void registerCommandContexts() {
        this.commandManager.getCommandContexts().registerContext(
            Events.class,
            c -> {
                String arg = c.popFirstArg();
                return (arg.equalsIgnoreCase("quickrace") ||
                        arg.equalsIgnoreCase("qr")) &&
                    this.quickRaceManager != null &&
                    this.quickRaceManager.isQuickRaceActive()
                    ? (Events) this.quickRaceManager.getCurrentQuickRace().orElseThrow(
                          () ->
                              new InvalidCommandArgument(
                                  "Nenhuma Quick Race ativa no momento."
                              )
                      )
                    : (Events) this.raceEventManager.getEventByName(
                          arg
                      ).orElseThrow(() ->
                          new InvalidCommandArgument(
                              "Evento não encontrado: " + arg
                          )
                      );
            }
        );
        this.commandManager.getCommandContexts().registerContext(
            Rounds.class,
            c -> {
                String arg1 = c.popFirstArg();
                Events event = null;
                String roundArg = null;
                if (!c.getArgs().isEmpty()) {
                    event = (Events) this.raceEventManager.getEventByName(
                        arg1
                    ).orElse(null);
                    if (event != null) {
                        roundArg = c.popFirstArg();
                    }
                }

                if (event == null) {
                    roundArg = arg1;
                    if (c.getPlayer() != null) {
                        event = (Events) this.dm.getPlayerSelectedEvent(
                            c.getPlayer().getUniqueId()
                        ).orElse(null);
                    }
                }

                if (event == null) {
                    throw new InvalidCommandArgument(
                        "Evento não encontrado ou não selecionado."
                    );
                } else if (roundArg == null) {
                    throw new InvalidCommandArgument("Round não especificado.");
                } else {
                    String finalRoundArg = roundArg;

                    try {
                        String cleanRound = finalRoundArg
                            .toUpperCase()
                            .replace("R", "");
                        int roundIdx = Integer.parseInt(cleanRound);
                        return (Rounds) event
                            .getSchedule()
                            .getRound(roundIdx)
                            .orElseThrow(() ->
                                new InvalidCommandArgument(
                                    "Round não encontrado: " + finalRoundArg
                                )
                            );
                    } catch (NumberFormatException var8) {
                        throw new InvalidCommandArgument(
                            "Número do round inválido: " + roundArg
                        );
                    }
                }
            }
        );
        this.commandManager.getCommandContexts().registerContext(
            Heats.class,
            c -> {
                String arg1 = c.popFirstArg();
                Events event = null;
                Rounds round = null;
                String heatArg = null;
                if (arg1.toUpperCase().matches("R\\d+(?:SQ|S|P|Q|F|E|H)\\d+")) {
                    this.getLogger().info(
                        "[HEAT RESOLVER DEBUG] Tentando resolver código de heat: " +
                            arg1
                    );
                    if (c.getPlayer() != null) {
                        event = (Events) this.dm.getPlayerSelectedEvent(
                            c.getPlayer().getUniqueId()
                        ).orElse(null);
                        Logger var10000 = this.getLogger();
                        String var10001 =
                            event != null ? event.getDisplayName() : "null";
                        var10000.info(
                            "[HEAT RESOLVER DEBUG] Evento selecionado do jogador: " +
                                var10001
                        );
                    }

                    if (event == null) {
                        event = (Events) this.raceEventManager.getAllEvents()
                            .stream()
                            .filter(Events::isActive)
                            .findFirst()
                            .orElse(null);
                        Logger var24 = this.getLogger();
                        String var26 =
                            event != null ? event.getDisplayName() : "null";
                        var24.info(
                            "[HEAT RESOLVER DEBUG] Evento ativo global: " +
                                var26
                        );
                    }

                    if (event != null) {
                        this.getLogger().info(
                            "[HEAT RESOLVER DEBUG] Iterando por " +
                                event
                                    .getSchedule()
                                    .getRoundsCollection()
                                    .size() +
                                " rounds"
                        );

                        for (Rounds r : event
                            .getSchedule()
                            .getRoundsCollection()) {
                            Logger var25 = this.getLogger();
                            int var27 = r.getRoundIndex();
                            var25.info(
                                "[HEAT RESOLVER DEBUG] Verificando round " +
                                    var27 +
                                    " (" +
                                    String.valueOf(r.getType()) +
                                    ") com " +
                                    r.getHeats().size() +
                                    " heats"
                            );

                            for (Heats h : r.getHeats().values()) {
                                String heatName = h.getName();
                                this.getLogger().info(
                                    "[HEAT RESOLVER DEBUG]   Heat: " +
                                        heatName +
                                        " vs " +
                                        arg1
                                );
                                if (heatName.equalsIgnoreCase(arg1)) {
                                    this.getLogger().info(
                                        "[HEAT RESOLVER DEBUG] ✓ Match encontrado!"
                                    );
                                    return h;
                                }
                            }
                        }

                        this.getLogger().info(
                            "[HEAT RESOLVER DEBUG] Nenhum heat encontrado com código " +
                                arg1
                        );
                    } else {
                        this.getLogger().info(
                            "[HEAT RESOLVER DEBUG] Nenhum evento encontrado"
                        );
                    }
                }

                if (!c.getArgs().isEmpty()) {
                    event = (Events) this.raceEventManager.getEventByName(
                        arg1
                    ).orElse(null);
                    if (event != null) {
                        String roundArg = c.popFirstArg();
                        if (!c.getArgs().isEmpty()) {
                            try {
                                String cleanRound = roundArg
                                    .toUpperCase()
                                    .replace("R", "");
                                int roundIdx = Integer.parseInt(cleanRound);
                                round = (Rounds) event
                                    .getSchedule()
                                    .getRound(roundIdx)
                                    .orElse(null);
                                if (round != null) {
                                    heatArg = c.popFirstArg();
                                }
                            } catch (NumberFormatException var13) {}
                        }
                    }
                }

                if (round == null && c.getPlayer() != null) {
                    event = (Events) this.dm.getPlayerSelectedEvent(
                        c.getPlayer().getUniqueId()
                    ).orElse(null);
                    if (event != null) {
                        String roundArg = arg1;
                        if (!c.getArgs().isEmpty()) {
                            try {
                                String cleanRound = roundArg
                                    .toUpperCase()
                                    .replace("R", "");
                                int roundIdx = Integer.parseInt(cleanRound);
                                round = (Rounds) event
                                    .getSchedule()
                                    .getRound(roundIdx)
                                    .orElse(null);
                                if (round != null) {
                                    heatArg = c.popFirstArg();
                                }
                            } catch (NumberFormatException var12) {}
                        } else {
                            round = (Rounds) event
                                .getSchedule()
                                .getCurrentRound()
                                .orElse(null);
                            if (round != null) {
                                heatArg = arg1;
                            }
                        }
                    }
                }

                if (round == null) {
                    throw new InvalidCommandArgument(
                        "Round não encontrado ou não selecionado."
                    );
                } else {
                    String finalHeatArg = heatArg;

                    try {
                        int heatIdx = Integer.parseInt(finalHeatArg);
                        return (Heats) round
                            .getHeat(heatIdx)
                            .orElseThrow(() ->
                                new InvalidCommandArgument(
                                    "Heat não encontrado: " + finalHeatArg
                                )
                            );
                    } catch (NumberFormatException var11) {
                        throw new InvalidCommandArgument(
                            "Número do heat inválido: " + heatArg
                        );
                    }
                }
            }
        );
    }

    private void registerCommandCompletions() {
        this.commandManager.getCommandCompletions().registerCompletion(
            "tracks",
            c ->
                this.dm.getAllTracks()
                    .stream()
                    .filter(trackName -> this.dm.isTrackOpen(trackName))
                    .map(t -> t.replace(" ", ""))
                    .toList()
        );
        this.commandManager.getCommandCompletions().registerCompletion(
            "partyMembers",
            c -> {
                if (!(c.getSender() instanceof Player player)) return List.of();
                try {
                    if (!dm.hasParty(player.getUniqueId())) return List.of();
                    UUID owner = dm.getOwner(player.getUniqueId());
                    String raw = dm.getMembers(owner);
                    return Arrays.stream(raw.split(","))
                            .filter(s -> !s.isEmpty())
                            .map(s -> {
                                try {
                                    Player p = Bukkit.getPlayer(UUID.fromString(s));
                                    return p != null ? p.getName() : "offline";
                                } catch (IllegalArgumentException e) {
                                    return null;
                                }
                            })
                            .filter(n -> n != null && !n.equals("offline"))
                            .toList();
                } catch (SQLException e) {
                    return List.of();
                }
            }
        );
        this.commandManager.getCommandCompletions().registerCompletion(
            "boatutils_settings",
            c ->
                Arrays.asList(
                    "defaultslipperiness",
                    "jumpforce",
                    "yawacceleration",
                    "forwardacceleration",
                    "backwardacceleration",
                    "turningforwardacceleration",
                    "swimforce",
                    "stepheight",
                    "gravity",
                    "falldamage",
                    "waterelevation",
                    "aircontrol",
                    "allowaccelerationstacking",
                    "underwatercontrol",
                    "surfacewatercontrol",
                    "waterjumping",
                    "airstepping",
                    "tenstepinterpolation",
                    "collisionmode",
                    "collisionresolution",
                    "coyotime",
                    "walltapmultiplier",
                    "jumps",
                    "scale",
                    "stepupslipperiness",
                    "fixdoublewaterelevation",
                    "lateralslipperiness",
                    "brakeslipperiness",
                    "multistepping",
                    "maxspeed",
                    "maxspeedresistance",
                    "honeycompatibility",
                    "collisionfilter",
                    "customslipperiness",
                    "perblocksetting"
                )
        );
        this.commandManager.getCommandCompletions().registerCompletion(
            "boatutils_group_modes",
            c ->
                Arrays.stream(BoatUtilsGroupMode.values())
                    .map(Enum::name)
                    .toList()
        );
        this.commandManager.getCommandCompletions().registerCompletion(
            "materials",
            c ->
                Arrays.stream(Material.values())
                    .filter(Material::isBlock)
                    .map(m -> m.getKey().getKey())
                    .toList()
        );
        this.commandManager.getCommandCompletions().registerCompletion(
            "languages",
            c -> {
                File langFolder = new File(this.getDataFolder(), "lang");
                if (!langFolder.exists()) {
                    return Collections.emptyList();
                } else {
                    File[] files = langFolder.listFiles((dir, name) ->
                        name.endsWith(".yml")
                    );
                    return files == null
                        ? Collections.emptyList()
                        : Arrays.stream(files)
                              .map(f -> f.getName().replace(".yml", ""))
                              .toList();
                }
            }
        );
        this.commandManager.getCommandCompletions().registerCompletion(
            "boats",
            c ->
                List.of(
                    "oak_boat",
                    "birch_boat",
                    "spruce_boat",
                    "jungle_boat",
                    "acacia_boat",
                    "dark_oak_boat",
                    "mangrove_boat",
                    "cherry_boat",
                    "bamboo_raft",
                    "oak_chest_boat",
                    "birch_chest_boat",
                    "spruce_chest_boat",
                    "jungle_chest_boat",
                    "acacia_chest_boat",
                    "dark_oak_chest_boat",
                    "mangrove_chest_boat",
                    "cherry_chest_boat",
                    "bamboo_chest_raft"
                )
        );
        this.commandManager.getCommandCompletions().registerCompletion(
            "event",
            c -> {
                List<String> events = new ArrayList(
                    this.raceEventManager.getAllEvents()
                        .stream()
                        .map(Events::getDisplayName)
                        .toList()
                );
                if (
                    this.quickRaceManager != null &&
                    this.quickRaceManager.isQuickRaceActive()
                ) {
                    events.add("quickrace");
                }

                return events;
            }
        );
        this.commandManager.getCommandCompletions().registerCompletion(
            "round",
            c -> {
                Events event = null;

                try {
                    event = (Events) c.getContextValue(Events.class);
                } catch (Exception var6) {}

                if (event == null) {
                    String input = c.getInput();
                    String config = c.getConfig();
                    if (config != null && !config.isBlank()) {
                        String[] configs = config.split(" ");
                        if (configs.length > 2) {
                            String eventName = configs[2];
                            event =
                                (Events) this.raceEventManager.getEventByName(
                                    eventName
                                ).orElse(null);
                        }
                    }
                }

                if (event == null && c.getPlayer() != null) {
                    event = (Events) this.dm.getPlayerSelectedEvent(
                        c.getPlayer().getUniqueId()
                    ).orElse(null);
                }

                return event == null
                    ? Collections.emptyList()
                    : event
                          .getSchedule()
                          .getRoundsCollection()
                          .stream()
                          .map(r -> "R" + r.getRoundNumber())
                          .toList();
            }
        );
        this.commandManager.getCommandCompletions().registerAsyncCompletion(
            "trackexchangeFiles",
            c -> {
                if (trackExchangeManager == null) return List.of();
                return trackExchangeManager.listFiles();
            }
        );
        this.commandManager.getCommandCompletions().registerCompletion(
            "heat",
            c -> {
                Rounds round = null;

                try {
                    round = (Rounds) c.getContextValue(Rounds.class);
                } catch (Exception var10) {}

                if (round == null && c.getConfig() != null) {
                    String[] configs = c.getConfig().split(" ");
                    if (configs.length > 3) {
                        String eventName = configs[2];
                        String roundName = configs[3];
                        Events event =
                             this.raceEventManager.getEventByName(
                                eventName
                            ).orElse(null);
                        if (event != null) {
                            String cleanRound = roundName
                                .toUpperCase()
                                .replace("R", "");

                            try {
                                int roundIdx = Integer.parseInt(cleanRound);
                                round = (Rounds) event
                                    .getSchedule()
                                    .getRound(roundIdx)
                                    .orElse(null);
                            } catch (NumberFormatException var9) {}
                        }
                    }
                }

                // Create a final copy of the variable for the Lambda
                final Rounds finalRound = round;

                if (finalRound == null && c.getPlayer() != null) {
                    Events selected = (Events) this.dm.getPlayerSelectedEvent(
                        c.getPlayer().getUniqueId()
                    ).orElse(null);
                    if (selected != null) {
                        return selected
                            .getSchedule()
                            .getRoundsCollection()
                            .stream()
                            .flatMap(r -> {
                                // 'r' já é final por ser parâmetro da lambda, mas o prefixo depende dele
                                String typePrefix;
                                switch (r.getType()) {
                                    case QUALIFICATION -> typePrefix = "Q";
                                    case SPRINT_QUALIFICATION -> typePrefix = "SQ";
                                    case FINAL -> typePrefix = "F";
                                    case ELIMINATION -> typePrefix = "E";
                                    case PRACTICE -> typePrefix = "P";
                                    case SPRINT_RACE -> typePrefix = "S";
                                    default -> typePrefix = "H";
                                }
                                return r
                                    .getHeats()
                                    .values()
                                    .stream()
                                    .map(
                                        h ->
                                            "R" +
                                            r.getRoundIndex() +
                                            typePrefix +
                                            h.getHeatNumber()
                                    );
                            })
                            .toList();
                    }
                }

                // Use the final copy here too
                return finalRound == null
                    ? Collections.emptyList()
                    : finalRound
                          .getHeats()
                          .values()
                          .stream()
                          .map(h -> {
                               String typePrefix;
                                switch (finalRound.getType()) {
                                    case QUALIFICATION -> typePrefix = "Q";
                                    case SPRINT_QUALIFICATION -> typePrefix = "SQ";
                                    case FINAL -> typePrefix = "F";
                                    case ELIMINATION -> typePrefix = "E";
                                    case PRACTICE -> typePrefix = "P";
                                    case SPRINT_RACE -> typePrefix = "S";
                                    default -> typePrefix = "H";
                                }
                              return (
                                  "R" +
                                  finalRound.getRoundIndex() +
                                  typePrefix +
                                  h.getHeatNumber()
                              );
                          })
                          .toList();
            }
        );
    }

    public DrsManager getDRS() {
        return this.drsManager;
    }

    public PTPManager getPTP() {
        return this.ptpManager;
    }
    public ERSManager getERS() {
        return this.ersManager;
    }

    public GuiManager getGuiManager() {
        return this.guiManager;
    }

    public void checkAndWarnOBU(Player player, String trackName) {
        SchedulerHelper.runAsync(this, () -> {
            String trackWS = trackName.replaceAll("\\s+", "");
            Map<String, Object> data = this.dm.getBoatUtilsRaw(trackWS);
            if (data != null && !data.isEmpty()) {
                boolean requiresOBU = false;
                Object stepHeightObj = data.get("stepHeight");
                if (
                    stepHeightObj instanceof Number &&
                    ((Number) stepHeightObj).floatValue() > 0.6F
                ) {
                    requiresOBU = true;
                }

                if (!requiresOBU) {
                    requiresOBU = (Boolean) data.getOrDefault(
                        "waterElevation",
                        false
                    );
                }

                if (requiresOBU && !hasOpenBoatUtilsMod(player)) {
                    SchedulerHelper.runTask(this, () -> {
                        player.sendMessage(" ");
                        player.sendMessage(
                            "§c§l⚠ ATENÇÃO: §fEsta pista requer que seu barco suba blocos!"
                        );
                        player.sendMessage(
                            "§fVocê está sem o §b§lOpenBoatUtils§f, então não conseguirá subir as elevações."
                        );
                        ClickableMessageUtil.sendClickableUrl(
                            player,
                            "§b[CLIQUE AQUI PARA BAIXAR O MOD]",
                            "https://modrinth.com/mod/openboatutils",
                            "Clique para abrir a página de download"
                        );
                        player.sendMessage(" ");
                    });
                }
            }
        });
    }
    public boolean isBedrockPlayer(Player player) {
        // 1. Verifica se o plugin Floodgate está ativo no servidor para evitar crash
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            // 2. Usa a instância do FloodgateApi para checar o UUID
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        }
        // If Floodgate is not installed, technically no one is Bedrock
        return false;
    }
}
