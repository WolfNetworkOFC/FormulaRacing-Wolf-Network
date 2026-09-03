package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FileManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Event.EventState;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Round.RoundState;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.ClickableMessageUtil;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.BoatUtils.OpenBoatUtilsVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.chat.hover.content.Content;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import dev.EfraGroup.formulaRacing.Utils.FRTask;

public class DailyRaceManager {
    private static final UUID DAILY_CREATOR_UUID = new UUID(0L, 0L);
    private static final DateTimeFormatter DATE_FORMAT;
    private static final DateTimeFormatter TIME_FORMAT;
    private final FormulaRacing plugin;
    private final EventSignupService signupService;
    private final Random random = new Random();
    private FRTask scheduleTickTask;
    private FRTask phaseTask;
    private FRTask monitorTask;
    private volatile Integer activeEventId;
    private volatile String activeEventName;
    private volatile Phase phase;
    private volatile Long practiceStartTime;

    public DailyRaceManager(FormulaRacing plugin) {
        this.phase = DailyRaceManager.Phase.IDLE;
        this.practiceStartTime = null;
        this.plugin = plugin;
        this.signupService = new EventSignupService(plugin);
        this.ensureDefaults();
        this.tryResume();
    }

    public void start() {
        this.stop();
        if (this.isEnabled()) {
            this.scheduleTickTask = SchedulerHelper.runTaskTimer(this.plugin, this::tickSchedule, 40L, 1200L);
        }
    }

    public void stop() {
        if (this.scheduleTickTask != null) {
            this.scheduleTickTask.cancel();
            this.scheduleTickTask = null;
        }

        if (this.phaseTask != null) {
            this.phaseTask.cancel();
            this.phaseTask = null;
        }

        if (this.monitorTask != null) {
            this.monitorTask.cancel();
            this.monitorTask = null;
        }

    }

    public void reload() {
        this.ensureDefaults();
        this.start();
        this.tryResume();
    }

    public Optional<Events> getActiveDailyEvent() {
        Integer id = this.activeEventId;
        return id == null ? Optional.empty() : this.plugin.getRaceEventManager().getEventById(id);
    }

    public Phase getPhase() {
        return this.phase;
    }

    public Long getPracticeStartTime() {
        return this.practiceStartTime;
    }

    public long getPracticeTimeRemaining() {
        if (this.phase != DailyRaceManager.Phase.PRACTICE) {
            return -1L;
        } else {
            int practiceMinutes = this.readPracticeMinutes();
            long limitMs = (long)practiceMinutes * 60L * 1000L;
            if (this.practiceStartTime == null) {
                return limitMs;
            } else {
                long elapsedMs = System.currentTimeMillis() - this.practiceStartTime;
                return Math.max(0L, limitMs - elapsedMs);
            }
        }
    }

    public void signUpAndTeleport(Player player, Events event) {
        EventSignupService.SignupResult result = this.signupService.signPlayer(player, event, false);
        this.handleDailySignup(player, event, result);
    }

    public void handleDailySignup(Player player, Events event, EventSignupService.SignupResult result) {
        UUID playerUUID = player.getUniqueId();
        switch (result.getStatus()) {
            case SIGNED:
                if (result.isMovedFromReserve()) {
                    this.plugin.sendMessage(player, "event_moved_from_reserve", new String[0]);
                }

                player.sendMessage("");
                this.plugin.sendMessage(player, "daily_signup_success", new String[0]);
                this.plugin.sendMessage(player, "daily_signup_event", new String[]{"{event}", event.getDisplayName()});
                if (event.getTrack() != null) {
                    this.plugin.sendMessage(player, "daily_signup_track", new String[]{"{track}", event.getTrack().getTrackName()});
                }

                this.plugin.sendMessage(player, "daily_signup_leave_hint", new String[0]);
                player.sendMessage("");
                this.plugin.getDatabaseManager().setPlayerSelectedEvent(playerUUID, event);
                if (this.phase == DailyRaceManager.Phase.PRACTICE) {
                    this.teleportToPractice(player, event);
                } else {
                    this.plugin.sendMessage(player, "daily_signup_wait", new String[0]);
                }

                return;
            case ALREADY_SUBSCRIBED:
            case ALREADY_SUBSCRIBED_DAILY:
                if (this.phase == DailyRaceManager.Phase.PRACTICE) {
                    this.teleportToPractice(player, event);
                } else {
                    this.plugin.sendMessage(player, "event_already_subscribed", new String[0]);
                }

                return;
            case SIGN_CLOSED:
                this.plugin.sendMessage(player, "event_sign_closed", new String[0]);
                return;
            case FINISHED:
                this.plugin.sendMessage(player, "event_already_finished", new String[0]);
                return;
            case NO_EVENT:
                this.plugin.sendMessage(player, "event_none_selected", new String[0]);
                return;
            case OBU_REQUIRED:
                return;
            case ERROR:
            default:
                this.plugin.sendMessage(player, "daily_signup_error", new String[0]);
        }
    }

    private void teleportToPractice(Player player, Events event) {
        String trackName = event.getTrackNameWS();
        if (trackName != null && !trackName.isEmpty()) {
            if (this.plugin.getTimeTrialController() != null) {
                this.plugin.getTimeTrialController().endSession(player);
            }

            Location loc = this.plugin.getDatabaseManager().getTrackSpawn(trackName);
            if (loc == null) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Track spawn location not found.");
            } else {
                if (this.plugin.getDatabaseManager().trackHaveBoatUtils(trackName)) {
                    if (!FormulaRacing.hasOpenBoatUtilsMod(player)) {
                        this.plugin.sendMessage(player, "obu_mandatory_warning", new String[]{"{track}", trackName});
                        return;
                    }
                    int minVersion = this.plugin.getDatabaseManager().getTrackMinObuVersion(trackName);
                    if (!OpenBoatUtilsVersion.hasMinVersion(player.getUniqueId(), minVersion)) {
                        this.plugin.sendMessage(player, "obu_version_warning", new String[]{"{track}", trackName, "{required}", String.valueOf(minVersion), "{current}", String.valueOf(OpenBoatUtilsVersion.getPlayerVersion(player.getUniqueId()))});
                        return;
                    }
                }

                this.plugin.getAPI().recoverPlayerBoatState(player);

                // Folia: teleportAsync é assíncrono — spawnar o barco na posição atual
                // antes do teleport concluir quebraria o teleport (player dentro de
                // veículo). Spawnamos apenas depois, no destino.
                SchedulerHelper.teleportAsync(player, loc).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success) && player.isOnline()) {
                        this.plugin.sendMessage(player, "daily_teleport_practice", new String[0]);
                        this.plugin.getAPI().spawnBoatAt(player, loc, false, false, false);
                        this.notifyPlayerJoinPractice(player);
                    }
                });
            }
        } else {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Track not configured for this event.");
        }
    }

    private void tickSchedule() {
        if (this.isEnabled()) {
            ZoneId zoneId = this.readZoneId();
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            LocalTime scheduled = this.readScheduledTime();
            LocalDate today = now.toLocalDate();
            if (this.phase == DailyRaceManager.Phase.IDLE) {
                if (!now.toLocalTime().isBefore(scheduled)) {
                    LocalDate lastDate = this.readLastRunDate();
                    if (!today.equals(lastDate)) {
                        this.startDaily(today, now);
                    }
                }
            }
        }
    }

    public void forceStart() {
        ZoneId zoneId = this.readZoneId();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalDate today = now.toLocalDate();
        this.startDaily(today, now);
    }

    private void startDaily(LocalDate date, ZonedDateTime now) {
        if (this.phase != DailyRaceManager.Phase.IDLE) return;

        this.phase = DailyRaceManager.Phase.STARTING;
        List<String> tracks = getEligibleTracks();

        if (tracks.isEmpty()) {
            plugin.getDebugManager().logRaceSystem("[DailyRace] No eligible tracks found.");
            finalizeFailedStart(date);
            return;
        }

        String lastTrack = readLastTrackNameWS();
        String trackNameWS = chooseTrack(tracks, lastTrack);

        if (trackNameWS == null) {
            plugin.getDebugManager().logRaceSystem("[DailyRace] Failed to choose track.");
            finalizeFailedStart(date);
            return;
        }

        // Generate event name and avoid duplicates
        String eventName = formatEventName(date);
        if (plugin.getRaceEventManager().getEventByName(eventName).isPresent()) {
            eventName += "-" + (1000 + random.nextInt(9000));
        }

        // Settings based on config file
        int practiceMinutes = readPracticeMinutes();
        int qualMinutes = readQualifyingMinutes();
        int raceMinutes = readRaceMinutes();
        int pits = readPitStops();

        // Lap calculation logic (Estimate based on Track Record)
        Double bestLap = plugin.getDatabaseManager().getBestTime(trackNameWS);
        double referenceLap = (bestLap != null && bestLap > 0) ? bestLap : 60.0;

        // Dynamic calculation: (Desired time in seconds / Reference lap time)
        int qualLaps = Math.max(1, (int) Math.ceil((qualMinutes * 60.0) / referenceLap));
        int raceLaps = Math.max(1, (int) Math.ceil((raceMinutes * 60.0) / referenceLap));

        // Validation for Point-to-Point (Sprint) tracks
        if (!plugin.getDatabaseManager().isCircuit(trackNameWS)) {
            plugin.getDebugManager().logRaceSystem("[DailyRace] Track " + trackNameWS + " is Sprint. Forcing linear mode.");
            qualLaps = 1;
            raceLaps = 1;
            pits = 0;
        }

        plugin.getDebugManager().logRaceSystem(String.format("[DailyRace] Criando: %s | Pista: %s", eventName, trackNameWS));

        plugin.getRaceEventManager().createDailyEvent(
                DAILY_CREATOR_UUID, eventName, trackNameWS,
                practiceMinutes * 60, qualLaps, qualMinutes * 60, raceLaps, pits
        ).thenAccept((Object obj) -> {
            Events event = (Events) obj;
            if (event == null) {
                finalizeFailedStart(date);
                return;
            }
            setupActiveDaily(event, date, trackNameWS, practiceMinutes);
        });
    }

    // Helper methods to clean up the main code
    private void finalizeFailedStart(LocalDate date) {
        writeLastRunDate(date);
        this.phase = DailyRaceManager.Phase.IDLE;
    }

    private void setupActiveDaily(Events event, LocalDate date, String trackNameWS, int practiceMinutes) {
        event.setOpenSign(true);
        this.activeEventId = event.getId();
        this.activeEventName = event.getDisplayName();
        this.phase = DailyRaceManager.Phase.PRACTICE;

        writeRuntime(event.getId(), this.phase);
        writeLastRunDate(date);
        writeLastTrack(trackNameWS);

        broadcastPracticeStart(event, practiceMinutes);
        event.start();

        this.practiceStartTime = System.currentTimeMillis();

        if (practiceMinutes <= 0) {
            endPracticeAndStartQualification();
        } else {
            startMonitor();
        }
    }

    public boolean skipPhase() {
        if (this.phase == DailyRaceManager.Phase.IDLE) {
            return false;
        } else {
            Optional<Events> eventOpt = this.getActiveDailyEvent();
            if (eventOpt.isEmpty()) {
                this.resetRuntime();
                return false;
            } else {
                Events event = (Events)eventOpt.get();
                switch (this.phase.ordinal()) {
                    case 2:
                        this.endPracticeAndStartQualification();
                        return true;
                    case 3:
                        this.advanceToFinal(event);
                        return true;
                    case 4:
                        this.finishDaily(event);
                        return true;
                    default:
                        return false;
                }
            }
        }
    }

    private void endPracticeAndStartQualification() {
        Optional<Events> eventOpt = this.getActiveDailyEvent();
        if (eventOpt.isEmpty()) {
            this.resetRuntime();
        } else {
            Events event = (Events)eventOpt.get();
            if (this.phase == DailyRaceManager.Phase.PRACTICE) {
                this.phase = DailyRaceManager.Phase.QUALIFICATION;
                this.writeRuntime(event.getId(), this.phase);
                this.plugin.getDebugManager().logRaceSystem("[DailyRace] Ending practice. Starting qualification round explicitly.");
                Rounds practiceRound = event.getSchedule().getRound(1).orElse(null);
                if (practiceRound != null) {
                    for(Heats heat : practiceRound.getHeats().values()) {
                        if (heat.getHeatState() != HeatState.FINISHED) {
                            heat.finishHeat(false);
                        }
                    }
                }
                // Rounds no longer auto-schedule the next round — the scripted
                // daily race advances its own phases explicitly.
                event.getSchedule().nextRound();
            }
        }
    }

    private void startMonitor() {
        if (this.monitorTask == null) {
            this.monitorTask = SchedulerHelper.runTaskTimer(this.plugin, this::tickMonitor, 40L, 40L);
        }
    }

    private void tickMonitor() {
        Optional<Events> eventOpt = this.getActiveDailyEvent();
        if (eventOpt.isEmpty()) {
            this.resetRuntime();
        } else {
            Events event = (Events)eventOpt.get();
            if (event.getState() == EventState.FINISHED) {
                this.unloadFromMemory(event);
                this.resetRuntime();
            } else {
                Phase currentPhase = this.phase;
                if (currentPhase == DailyRaceManager.Phase.PRACTICE) {
                    if (this.isRoundFinished(event, 1)) {
                        this.endPracticeAndStartQualification();
                    } else {
                        this.checkPracticeTimeout();
                    }
                } else if (currentPhase == DailyRaceManager.Phase.QUALIFICATION) {
                    if (this.isRoundFinished(event, 2)) {
                        this.advanceToFinal(event);
                    } else {
                        this.checkQualificationTimeout(event);
                    }
                } else {
                    if (currentPhase == DailyRaceManager.Phase.FINAL) {
                        if (this.isRoundFinished(event, 3)) {
                            this.finishDaily(event);
                            return;
                        }

                        this.checkFinalTimeout(event);
                    }

                }
            }
        }
    }

    private void checkPracticeTimeout() {
        if (this.practiceStartTime != null) {
            int practiceMinutes = this.readPracticeMinutes();
            long elapsedMs = System.currentTimeMillis() - this.practiceStartTime;
            long limitMs = (long)practiceMinutes * 60L * 1000L;
            if (elapsedMs >= limitMs) {
                this.endPracticeAndStartQualification();
            }

        }
    }

    private void checkQualificationTimeout(Events event) {
        event.getSchedule().getRound(2).ifPresent((round) -> round.getHeat(1).ifPresent((heat) -> {
            if (heat.getSessionTimeRemaining() <= 0L) {
                this.plugin.getDebugManager().logRaceSystem("[DailyRace] Qualification time expired (Timeout Check). Advancing.");
                this.advanceToFinal(event);
            }

        }));
    }

    private void checkFinalTimeout(Events event) {
        event.getSchedule().getRound(3).ifPresent((round) -> round.getHeat(1).ifPresent((heat) -> {
            if (heat.getSessionTimeRemaining() <= 0L) {
                this.plugin.getDebugManager().logRaceSystem("[DailyRace] Race time expired (Timeout Check). Finishing.");
                this.finishDaily(event);
            }

        }));
    }

    public void notifyPlayerJoinPractice(Player player) {
        if (this.phase == DailyRaceManager.Phase.PRACTICE) {
            this.plugin.getDatabaseManager().setTimeTrialEnabled(player.getUniqueId(), false);
            this.plugin.getTimerUtils().stopTimer(player);
            this.plugin.getScoreboardTimeTrialUtils().clearPlayerTrack(player);
            if (this.plugin.getPitStopManager() != null) {
                this.plugin.getPitStopManager().clearPitStopState(player.getUniqueId());
            }

            this.getActiveDailyEvent().ifPresent((event) -> {
                if (this.plugin.getPacketSender() != null) {
                    this.plugin.getPacketSender().applyBoatUtilsToPlayer(player, event.getTrackNameWS());
                    DebugManager var10000 = this.plugin.getDebugManager();
                    String var10001 = player.getName();
                    var10000.logRaceSystem("§a[DailyRace] OBU applied for " + var10001 + " on track " + event.getTrackNameWS());
                }

                event.getSchedule().getRound(1).ifPresent((round) -> round.getHeat(1).ifPresent((heat) -> {
                    if (heat.getDriver(player.getUniqueId()) == null) {
                        heat.addDriver(player.getUniqueId(), heat.getDriverCount() + 1);
                        this.plugin.getDebugManager().logRaceSystem("§a[DailyRace] Driver " + player.getName() + " added to Practice Heat.");
                    }

                    if (heat.getActionBarManager() != null) {
                        heat.getActionBarManager().addPlayer(player, heat);
                    }

                    if (this.plugin.getRaceScoreboardManager() != null) {
                        this.plugin.getRaceScoreboardManager().addPlayer(player, heat);
                    }

                    if (heat.getScoreboardManager() != null) {
                        heat.getScoreboardManager().addPlayer(player, heat);
                    }

                }));
            });
        }
    }

    public void notifyPlayerCrossStartLine(Player player) {
        if (this.phase == DailyRaceManager.Phase.PRACTICE) {
            ;
        }
    }

    private boolean isRoundFinished(Events event, int roundIndex) {
        Rounds round = (Rounds)event.getSchedule().getRound(roundIndex).orElse(null);
        if (round == null) {
            return false;
        } else {
            Optional<Heats> heatOpt = round.getHeat(1);
            if (heatOpt.isEmpty()) {
                return false;
            } else {
                Heats heat = (Heats)heatOpt.get();
                return heat.getHeatState() == HeatState.FINISHED;
            }
        }
    }

    private void advanceToFinal(Events event) {
        if (this.phase == DailyRaceManager.Phase.QUALIFICATION) {
            this.phase = DailyRaceManager.Phase.FINAL;
            this.writeRuntime(event.getId(), this.phase);
            this.plugin.getDebugManager().logRaceSystem("[DailyRace] Ending qualification. Starting final round explicitly.");
            Rounds qualRound = (Rounds)event.getSchedule().getRound(2).orElse(null);
            if (qualRound != null) {
                for(Heats heat : qualRound.getHeats().values()) {
                    if (heat.getHeatState() != HeatState.FINISHED) {
                        heat.finishHeat(false);
                    }
                }
            }
            // Rounds no longer auto-schedule the next round — the scripted
            // daily race advances its own phases explicitly.
            event.getSchedule().nextRound();
        }
    }

    public void stopDaily() {
        List<Events> eventsToStop = new ArrayList();

        for(Events event : this.plugin.getRaceEventManager().getActiveEvents()) {
            if (event.getCreatorUUID().equals(DAILY_CREATOR_UUID)) {
                eventsToStop.add(event);
            }
        }

        if (eventsToStop.isEmpty()) {
            Optional<Events> activeOpt = this.getActiveDailyEvent();
            Objects.requireNonNull(eventsToStop);
            activeOpt.ifPresent(eventsToStop::add);
        }

        for(Events event : eventsToStop) {
            this.plugin.getDebugManager().logRaceSystem("[DailyRace] Stopping event: " + event.getDisplayName() + " (Ghost Cleanup)");
            event.setState(EventState.FINISHED);

            try {
                this.finishAllActiveHeats(event);
            } catch (Exception e) {
                this.plugin.getDebugManager().logRaceSystem("[DailyRace] Error finishing heats in stopDaily: " + e.getMessage());
            }

            try {
                event.finish();
            } catch (Exception e) {
                this.plugin.getDebugManager().logRaceSystem("[DailyRace] Error finishing event in stopDaily: " + e.getMessage());
            }

            this.unloadFromMemory(event);
        }

        this.resetRuntime();

        for(Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "\ud83d\uded1 Daily Race forcefully ended by an administrator.");
        }

    }

    private void finishDaily(Events event) {
        event.setState(EventState.FINISHED);

        try {
            this.finishAllActiveHeats(event);
        } catch (Exception e) {
            this.plugin.getDebugManager().logRaceSystem("[DailyRace] Error finishing heats: " + e.getMessage());
        }

        try {
            event.finish();
        } catch (Exception e) {
            this.plugin.getDebugManager().logRaceSystem("[DailyRace] Error finishing event: " + e.getMessage());
        }

        this.unloadFromMemory(event);
        this.resetRuntime();
    }

    private void finishAllActiveHeats(Events event) {
        if (event != null && event.getSchedule() != null) {
            for(int i = 1; i <= 3; ++i) {
                event.getSchedule().getRound(i).ifPresent((round) -> {
                    for(Heats heat : round.getHeats().values()) {
                        if (heat.getHeatState() != HeatState.FINISHED && heat.getHeatState() != HeatState.IDLE && heat.getHeatState() != HeatState.SETUP) {
                            try {
                                heat.finishHeat();
                            } catch (Exception e) {
                                DebugManager var10000 = this.plugin.getDebugManager();
                                int var10001 = heat.getId();
                                var10000.logRaceSystem("[DailyRace] Error finishing heat " + var10001 + ": " + e.getMessage());
                            }
                        }
                    }

                    if (round.getState() != RoundState.FINISHED) {
                        try {
                            round.finishRound();
                        } catch (Exception e) {
                            DebugManager var7 = this.plugin.getDebugManager();
                            int var8 = round.getId();
                            var7.logRaceSystem("[DailyRace] Error finishing round " + var8 + ": " + e.getMessage());
                        }
                    }

                });
            }

        }
    }

    private void unloadFromMemory(Events event) {
        try {
            // Não remove eventos automaticamente - apenas log
            this.plugin.getDebugManager().logRaceSystem("[DailyRace] Evento finalizado (não removido): " + event.getDisplayName());
        } catch (Throwable t) {
            this.plugin.getDebugManager().logRaceSystem("[DailyRace] Error: " + t.getMessage());
        }
    }

    }

    public void notifyPlayerOfAllActiveEvents(Player player) {
        this.notifyDailyRace(player);
        this.notifyManualEvents(player);
    }

    private String getOBUWarning(Player player, String track) {
        if (track == null) {
            return null;
        } else if (this.plugin.getDatabaseManager().trackHaveBoatUtils(track)) {
            if (!FormulaRacing.hasOpenBoatUtilsMod(player)) {
                String lang = this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
                return this.plugin.getTranslation("obu_mandatory_warning", lang, new String[]{"{track}", track});
            }
            int minVersion = this.plugin.getDatabaseManager().getTrackMinObuVersion(track);
            if (!OpenBoatUtilsVersion.hasMinVersion(player.getUniqueId(), minVersion)) {
                String lang = this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
                return this.plugin.getTranslation("obu_version_warning", lang, new String[]{"{track}", track, "{required}", String.valueOf(minVersion), "{current}", String.valueOf(OpenBoatUtilsVersion.getPlayerVersion(player.getUniqueId()))});
            }
        }
        return null;
    }

    private void notifyDailyRace(Player player) {
        Optional<Events> eventOpt = this.getActiveDailyEvent();
        if (!eventOpt.isEmpty()) {
            Events event = (Events)eventOpt.get();
            // Só notifica se o evento NÃO estiver finished
            if (event.getState() == EventState.FINISHED) {
                return;
            }
            if (event.getState() != EventState.RUNNING) {
                if (this.activeEventId != null && this.activeEventId == event.getId()) {
                    this.plugin.getDebugManager().logRaceSystem("[DailyRace] Auto-Correction: Resetting non-existent/finished Daily.");

                    try {
                        this.resetRuntime();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } else {
                String track = event.getTrackNameWS();
                String eventName = event.getDisplayName();
                String lang = this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());

                player.sendMessage("");
                String box = ChatColor.GOLD + "" + ChatColor.BOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
                player.sendMessage(box);

                if (this.phase == DailyRaceManager.Phase.PRACTICE) {
                    player.sendMessage(this.plugin.getTranslation("daily_msg_header", lang));
                    player.sendMessage(this.plugin.getTranslation("daily_msg_track", lang, "{track}", track));
                    player.sendMessage(this.plugin.getTranslation("daily_msg_phase", lang, "{phase}", "Free Practice"));
                    String obuWarning = this.getOBUWarning(player, track);
                    if (obuWarning != null) {
                        player.sendMessage("");
                        player.sendMessage(obuWarning);
                    }
                    player.sendMessage("");
                    ClickableMessageUtil.sendEventSignBroadcast(player, this.plugin.getTranslation("daily_sign_click", lang, "{event}", eventName), this.plugin.getTranslation("daily_sign_hover", lang, "{event}", eventName), eventName);
                } else if (this.phase == DailyRaceManager.Phase.QUALIFICATION) {
                    player.sendMessage(this.plugin.getTranslation("daily_qual_header", lang));
                    player.sendMessage(this.plugin.getTranslation("daily_msg_track", lang, "{track}", track));
                    player.sendMessage(this.plugin.getTranslation("daily_msg_phase", lang, "{phase}", "Qualification"));
                    String obuWarning = this.getOBUWarning(player, track);
                    if (obuWarning != null) {
                        player.sendMessage("");
                        player.sendMessage(obuWarning);
                    }
                    player.sendMessage("");
                    ClickableMessageUtil.sendEventSignBroadcast(player, this.plugin.getTranslation("daily_sign_click", lang, "{event}", eventName), this.plugin.getTranslation("daily_sign_hover", lang, "{event}", eventName), eventName);
                } else if (this.phase == DailyRaceManager.Phase.FINAL) {
                    player.sendMessage(this.plugin.getTranslation("daily_race_header", lang));
                    player.sendMessage(this.plugin.getTranslation("daily_msg_track", lang, "{track}", track));
                    String obuWarning = this.getOBUWarning(player, track);
                    if (obuWarning != null) {
                        player.sendMessage("");
                        player.sendMessage(obuWarning);
                    }
                    player.sendMessage("");
                    ClickableMessageUtil.sendEventSignBroadcast(player, this.plugin.getTranslation("daily_sign_click", lang, "{event}", eventName), this.plugin.getTranslation("daily_sign_hover", lang, "{event}", eventName), eventName);
                }

                player.sendMessage(box);
                player.sendMessage("");
            }
        }
    }

    private void notifyManualEvents(Player player) {
        Integer dailyId = this.activeEventId;
        String box = ChatColor.GOLD + "" + ChatColor.BOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

        for(Events event : this.plugin.getRaceEventManager().getActiveEvents()) {
            // Só notifica se o evento NÃO estiver finished
            if (event.getState() == EventState.FINISHED) {
                continue;
            }
            if ((dailyId == null || event.getId() != dailyId) && event.getState() == EventState.RUNNING) {
                String rawName = event.getDisplayName();
                if (rawName.startsWith("QuickRace_") || rawName.startsWith("PartyRace_") || rawName.startsWith("DuelRace_")) {
                    continue;
                }
                // Usa nome de exibição sem sufixo numérico
                String eventName = dev.EfraGroup.formulaRacing.FormulaRacing.getInstance() != null 
                    ? dev.EfraGroup.formulaRacing.FormulaRacing.getInstance().getEventDisplayName(rawName) 
                    : rawName;
                String track = event.getTrackNameWS();
                String lang = this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
                player.sendMessage("");
                player.sendMessage(box);
                player.sendMessage(this.plugin.getTranslation("event_formal_header", lang, "{event}", eventName));
                if (track != null) {
                    player.sendMessage(this.plugin.getTranslation("daily_msg_track", lang, "{track}", track));
                    String obuWarning = this.getOBUWarning(player, track);
                    if (obuWarning != null) {
                        player.sendMessage("");
                        player.sendMessage(obuWarning);
                    }
                }

                Rounds currentRound = (Rounds)event.getEventSchedule().getCurrentRound().orElse(null);
                if (currentRound != null) {
                    player.sendMessage(this.plugin.getTranslation("event_formal_phase", lang, "{phase}", currentRound.getType().toString()));
                }

                player.sendMessage("");
                if (event.isOpenSign()) {
                    ClickableMessageUtil.sendEventSignBroadcast(player, this.plugin.getTranslation("daily_sign_click", lang, "{event}", eventName), this.plugin.getTranslation("daily_sign_hover", lang, "{event}", eventName), eventName);
                }

                TextComponent specComp = new TextComponent(this.plugin.getTranslation("event_watch_click", lang));
                specComp.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, new Content[]{new Text(this.plugin.getTranslation("event_watch_hover", lang, "{event}", eventName))}));
                specComp.setClickEvent(new ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/spectate " + eventName));
                player.spigot().sendMessage(specComp);
                player.sendMessage(box);
                player.sendMessage("");
            }
        }

    }

    private void broadcastPracticeStart(Events event, int practiceMinutes) {
        String track = event.getTrackNameWS();
        String eventName = event.getDisplayName();
        String box = ChatColor.GOLD + "" + ChatColor.BOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        for(Player player : Bukkit.getOnlinePlayers()) {
            String lang = this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
            player.sendMessage("");
            player.sendMessage(box);
            player.sendMessage(this.plugin.getTranslation("daily_start_header", lang));
            player.sendMessage(this.plugin.getTranslation("daily_msg_track", lang, "{track}", track));
            player.sendMessage(this.plugin.getTranslation("daily_start_practice", lang, "{time}", String.valueOf(practiceMinutes)));
            player.sendMessage("");
            ClickableMessageUtil.sendEventSignBroadcast(player, this.plugin.getTranslation("daily_sign_click", lang, "{event}", eventName), this.plugin.getTranslation("daily_sign_hover", lang, "{event}", eventName), eventName);
            player.sendMessage(box);
            player.sendMessage("");
        }

    }

    private String formatEventName(LocalDate date) {
        FileConfiguration cfg = this.plugin.getFileManager().getConfig();
        String fmt = cfg.getString("daily-race.event-name-format", "Daily-{date}");
        return fmt.replace("{date}", DATE_FORMAT.format(date));
    }

    private String chooseTrack(List<String> tracks, String lastTrack) {
        if (tracks.isEmpty()) {
            return null;
        } else {
            List<String> candidates = new ArrayList(tracks);
            if (lastTrack != null && !lastTrack.isBlank() && candidates.size() > 1) {
                candidates.removeIf((t) -> t.equalsIgnoreCase(lastTrack));
            }

            if (candidates.isEmpty()) {
                candidates = tracks;
            }

            return (String)candidates.get(this.random.nextInt(candidates.size()));
        }
    }

    public boolean addExcludedTrack(String trackName) {
        if (trackName != null && !trackName.isBlank()) {
            String formatted = trackName.replaceAll("\\s+", "");
            List<String> excluded = new ArrayList(this.readExcludedTracks());
            if (excluded.stream().anyMatch((t) -> t.equalsIgnoreCase(formatted))) {
                return false;
            } else {
                excluded.add(formatted);
                this.saveExcludedTracks(excluded);
                return true;
            }
        } else {
            return false;
        }
    }

    public boolean removeExcludedTrack(String trackName) {
        if (trackName != null && !trackName.isBlank()) {
            String formatted = trackName.replaceAll("\\s+", "");
            List<String> excluded = new ArrayList(this.readExcludedTracks());
            boolean removed = excluded.removeIf((t) -> t.equalsIgnoreCase(formatted));
            if (removed) {
                this.saveExcludedTracks(excluded);
            }

            return removed;
        } else {
            return false;
        }
    }

    public List<String> getExcludedTracks() {
        return this.readExcludedTracks();
    }

    private void saveExcludedTracks(List<String> excluded) {
        FileManager fm = this.plugin.getFileManager();
        fm.getConfig().set("daily-race.exclude-tracks", excluded);
        fm.saveConfig();
    }

    private List<String> getEligibleTracks() {
        DatabaseManager db = this.plugin.getDatabaseManager();
        List<String> allTracks = db.getAllTracks();

        if (allTracks.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> excluded = this.readExcludedTracks();

        return allTracks.stream()
                // 1. Check if track is open in database
                .filter(db::isTrackOpen)
                // 2. Remove whitespace (Sanitization)
                .map(t -> t.replaceAll("\\s+", ""))
                // 3. Ensure it didn't become empty after the map
                .filter(t -> !t.isBlank())
                // 4. Filter if not in exclusion list (ignoring case)
                .filter(t -> excluded.stream().noneMatch(ex -> ex.equalsIgnoreCase(t)))
                // 5. Validate track technical integration (checkpoints, signs, etc)
                .filter(t -> this.plugin.getTrackIntegrationManager().validateTrack(t).isValid())
                // 6. Remove duplicates and collect
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> readExcludedTracks() {
        FileConfiguration cfg = this.plugin.getFileManager().getConfig();
        List<String> list = cfg.getStringList("daily-race.exclude-tracks");
        return list == null ? List.of() : list.stream().map((s) -> s == null ? "" : s.replaceAll("\\s+", "")).filter((s) -> !s.isBlank()).toList();
    }

    private boolean isEnabled() {
        return this.plugin.getFileManager().getConfig().getBoolean("daily-race.enabled", false);
    }

    private LocalTime readScheduledTime() {
        String raw = this.plugin.getFileManager().getConfig().getString("daily-race.time", "20:00");

        try {
            return LocalTime.parse(raw, TIME_FORMAT);
        } catch (Exception var3) {
            return LocalTime.of(20, 0);
        }
    }

    private ZoneId readZoneId() {
        String raw = this.plugin.getFileManager().getConfig().getString("daily-race.timezone", "system");
        if (raw != null && !raw.isBlank() && !raw.equalsIgnoreCase("system")) {
            try {
                return ZoneId.of(raw);
            } catch (Exception var3) {
                return ZoneId.systemDefault();
            }
        } else {
            return ZoneId.systemDefault();
        }
    }

    private int readPracticeMinutes() {
        return Math.max(0, this.plugin.getFileManager().getConfig().getInt("daily-race.practice-minutes", 5));
    }

    private int readQualifyingMinutes() {
        return Math.max(1, this.plugin.getFileManager().getConfig().getInt("daily-race.qualifying-minutes", 15));
    }

    private int readRaceMinutes() {
        return Math.max(1, this.plugin.getFileManager().getConfig().getInt("daily-race.race-minutes", 30));
    }

    private int readPitStops() {
        return Math.max(0, this.plugin.getFileManager().getConfig().getInt("daily-race.pit-stops", 0));
    }

    private LocalDate readLastRunDate() {
        String raw = this.plugin.getFileManager().getConfig().getString("daily-race.last-date", "");
        if (raw != null && !raw.isBlank()) {
            try {
                return LocalDate.parse(raw, DATE_FORMAT);
            } catch (Exception var3) {
                return null;
            }
        } else {
            return null;
        }
    }

    private void writeLastRunDate(LocalDate date) {
        FileManager fm = this.plugin.getFileManager();
        if (date == null) {
            fm.getConfig().set("daily-race.last-date", (Object)null);
        } else {
            fm.getConfig().set("daily-race.last-date", DATE_FORMAT.format(date));
        }

        fm.saveConfig();
    }

    private String readLastTrackNameWS() {
        String raw = this.plugin.getFileManager().getConfig().getString("daily-race.last-track", "");
        return raw != null && !raw.isBlank() ? raw.replaceAll("\\s+", "") : null;
    }

    private void writeLastTrack(String trackNameWS) {
        FileManager fm = this.plugin.getFileManager();
        fm.getConfig().set("daily-race.last-track", trackNameWS);
        fm.saveConfig();
    }

    private void writeRuntime(int eventId, Phase phase) {
        FileManager fm = this.plugin.getFileManager();
        fm.getConfig().set("daily-race.runtime.active-event-id", eventId);
        fm.getConfig().set("daily-race.runtime.phase", phase.name());
        fm.saveConfig();
    }

    private void resetRuntime() {
        this.activeEventId = null;
        this.activeEventName = null;
        this.phase = DailyRaceManager.Phase.IDLE;
        this.practiceStartTime = null;
        if (this.monitorTask != null) {
            this.monitorTask.cancel();
            this.monitorTask = null;
        }

        if (this.phaseTask != null) {
            this.phaseTask.cancel();
            this.phaseTask = null;
        }

        FileManager fm = this.plugin.getFileManager();
        fm.getConfig().set("daily-race.runtime.active-event-id", (Object)null);
        fm.getConfig().set("daily-race.runtime.phase", (Object)null);
        fm.saveConfig();
    }

    private void tryResume() {
        this.cleanGhosts();
        FileConfiguration cfg = this.plugin.getFileManager().getConfig();
        Integer eventId = cfg.getInt("daily-race.runtime.active-event-id", 0);
        if (eventId != null && eventId > 0) {
            Phase storedPhase = DailyRaceManager.Phase.IDLE;
            String phaseRaw = cfg.getString("daily-race.runtime.phase", "");
            if (phaseRaw != null && !phaseRaw.isBlank()) {
                try {
                    storedPhase = DailyRaceManager.Phase.valueOf(phaseRaw.toUpperCase(Locale.ROOT));
                } catch (Exception var14) {
                    storedPhase = DailyRaceManager.Phase.IDLE;
                }
            }

            Optional<Events> eventOpt = this.plugin.getRaceEventManager().getEventById(eventId);
            if (eventOpt.isEmpty()) {
                cfg.set("daily-race.runtime.active-event-id", (Object)null);
                cfg.set("daily-race.runtime.phase", (Object)null);
                this.plugin.getFileManager().saveConfig();
            } else {
                Events event = (Events)eventOpt.get();
                if (!event.isActive()) {
                    this.resetRuntime();
                } else {
                    String expectedName = this.formatEventName(LocalDate.now(this.readZoneId()));
                    long nowSeconds = Instant.now().getEpochSecond();
                    long createdSeconds = event.getDate() / 1000L;
                    LocalDate eventDate = Instant.ofEpochMilli(event.getDate()).atZone(this.readZoneId()).toLocalDate();
                    LocalDate today = LocalDate.now(this.readZoneId());
                    if (!eventDate.equals(today) && nowSeconds - createdSeconds > 43200L) {
                        this.plugin.getDebugManager().logRaceSystem("[DailyRace] Resumed event is from previous day (" + String.valueOf(eventDate) + "). Finishing.");
                        this.finishDaily(event);
                    } else if (nowSeconds - createdSeconds > 86400L) {
                        this.plugin.getDebugManager().logRaceSystem("[DailyRace] Resumed event is too old (>24h). Finishing.");
                        this.finishDaily(event);
                    } else {
                        this.activeEventId = event.getId();
                        this.activeEventName = event.getDisplayName();
                        this.phase = storedPhase == DailyRaceManager.Phase.IDLE ? this.inferPhase(event) : storedPhase;
                        if (this.phase == DailyRaceManager.Phase.IDLE && event.getState() == EventState.RUNNING) {
                            this.phase = DailyRaceManager.Phase.PRACTICE;
                        }

                        if (this.phase == DailyRaceManager.Phase.QUALIFICATION || this.phase == DailyRaceManager.Phase.FINAL) {
                            this.startMonitor();
                        }

                    }
                }
            }
        }
    }

    private Phase inferPhase(Events event) {
        if (event.getState() == EventState.SETUP) {
            return DailyRaceManager.Phase.PRACTICE;
        } else {
            Rounds r1 = (Rounds)event.getSchedule().getRound(1).orElse(null);
            if (r1 != null) {
                Heats h1 = (Heats)r1.getHeat(1).orElse(null);
                if (h1 != null && h1.getHeatState() != HeatState.FINISHED) {
                    return DailyRaceManager.Phase.PRACTICE;
                }
            }

            Rounds r2 = (Rounds)event.getSchedule().getRound(2).orElse(null);
            if (r2 != null) {
                Heats h2 = (Heats)r2.getHeat(1).orElse(null);
                if (h2 != null && h2.getHeatState() != HeatState.FINISHED) {
                    return DailyRaceManager.Phase.QUALIFICATION;
                }
            }

            Rounds r3 = (Rounds)event.getSchedule().getRound(3).orElse(null);
            if (r3 != null) {
                Heats h3 = (Heats)r3.getHeat(1).orElse(null);
                if (h3 != null && h3.getHeatState() != HeatState.FINISHED) {
                    return DailyRaceManager.Phase.FINAL;
                }
            }

            return DailyRaceManager.Phase.IDLE;
        }
    }

    private void ensureDefaults() {
        FileManager fm = this.plugin.getFileManager();
        FileConfiguration cfg = fm.getConfig();
        if (!cfg.isConfigurationSection("daily-race")) {
            cfg.createSection("daily-race");
        }

        this.setIfMissing(cfg, "daily-race.enabled", false);
        this.setIfMissing(cfg, "daily-race.time", "20:00");
        this.setIfMissing(cfg, "daily-race.timezone", "system");
        this.setIfMissing(cfg, "daily-race.practice-minutes", 5);
        this.setIfMissing(cfg, "daily-race.qualifying-laps", 3);
        this.setIfMissing(cfg, "daily-race.race-laps", 5);
        this.setIfMissing(cfg, "daily-race.pit-stops", 0);
        this.setIfMissing(cfg, "daily-race.event-name-format", "Daily-{date}");
        if (!cfg.isList("daily-race.exclude-tracks")) {
            cfg.set("daily-race.exclude-tracks", Collections.emptyList());
        }

        if (!cfg.isConfigurationSection("daily-race.runtime")) {
            cfg.createSection("daily-race.runtime");
        }

        fm.saveConfig();
    }

    private void setIfMissing(FileConfiguration cfg, String path, Object value) {
        if (!cfg.contains(path)) {
            cfg.set(path, value);
        }
    }

    private void cleanGhosts() {
        List<Events> eventsToStop = new ArrayList();
        long nowSeconds = Instant.now().getEpochSecond();
        LocalDate today = LocalDate.now(this.readZoneId());

        for(Events event : this.plugin.getRaceEventManager().getActiveEvents()) {
            boolean isDaily = event.getCreatorUUID().equals(DAILY_CREATOR_UUID) || event.getDisplayName().startsWith("Daily-");
            if (isDaily) {
                long createdSeconds = event.getDate() / 1000L;
                LocalDate eventDate = Instant.ofEpochMilli(event.getDate()).atZone(this.readZoneId()).toLocalDate();
                boolean stale = false;
                if (!eventDate.equals(today) && nowSeconds - createdSeconds > 43200L) {
                    stale = true;
                }

                if (nowSeconds - createdSeconds > 86400L) {
                    stale = true;
                }

                if (stale) {
                    eventsToStop.add(event);
                }
            }
        }

        for(Events event : eventsToStop) {
            this.plugin.getDebugManager().logRaceSystem("[DailyRace] Ghost Cleanup: Finishing old event " + event.getDisplayName());
            this.finishDaily(event);
        }

    }

    static {
        DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
        TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    }

    public static enum Phase {
        IDLE,
        STARTING,
        PRACTICE,
        QUALIFICATION,
        FINAL;
    }
}
