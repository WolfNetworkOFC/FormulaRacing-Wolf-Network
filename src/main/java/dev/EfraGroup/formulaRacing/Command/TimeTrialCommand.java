    //
    // Source code recreated from a .class file by IntelliJ IDEA
    // (powered by Fernflower decompiler)
    //

    package dev.EfraGroup.formulaRacing.Command;

    import dev.EfraGroup.formulaRacing.APIFormulaRacing;
    import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialTeleportMessage;
import dev.EfraGroup.formulaRacing.PacketSender;
    import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
    import dev.EfraGroup.formulaRacing.AI.AIRacingLineManager;
    import dev.EfraGroup.formulaRacing.Heat.HeatState;
    import dev.EfraGroup.formulaRacing.Heat.Heats;
    import dev.EfraGroup.formulaRacing.Heat.Lap;
    import dev.EfraGroup.formulaRacing.Participant.Driver;
    import dev.EfraGroup.formulaRacing.Participant.DriverState;
    import dev.EfraGroup.formulaRacing.TimeTrial.TimeTrialController;
    import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
    import dev.EfraGroup.formulaRacing.Utils.TimeTrialMenuUtilsV2;
    import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
    import co.aikar.commands.BaseCommand;
    import co.aikar.commands.annotation.CatchUnknown;
    import co.aikar.commands.annotation.CommandAlias;
    import co.aikar.commands.annotation.CommandCompletion;
    import co.aikar.commands.annotation.Default;
    import co.aikar.commands.annotation.Description;
    import co.aikar.commands.annotation.Subcommand;

    import java.util.*;
    import java.util.stream.Collectors;
    import java.util.stream.Stream;
    import org.bukkit.Location;
    import org.bukkit.Sound;
    import org.bukkit.entity.Player;

    @CommandAlias("timetrial|tt|timett")
    @Description("Time Trial system commands")
    public class TimeTrialCommand extends BaseCommand {
        private final FormulaRacing plugin;
        private final DatabaseManager mysql;
        private final PacketSender packetsender;
        private final TimerUtils timerUtils;
        private final APIFormulaRacing api;
        private final ScoreboardTimeTrialUtils stt;
        private final TimeTrialMenuUtilsV2 menuUtils;
        private final TimeTrialController timeTrialController;
        private final Random random = new Random();

        public TimeTrialCommand(FormulaRacing plugin) {
            this.plugin = plugin;
            this.mysql = plugin.getDatabaseManager();
            this.packetsender = plugin.getPacketSender();
            this.timerUtils = plugin.getTimerUtils();
            this.api = plugin.getAPI();
            this.stt = plugin.getScoreboardTimeTrialUtils();
            this.timeTrialController = plugin.getTimeTrialController();
            this.menuUtils = new TimeTrialMenuUtilsV2(plugin, this.mysql, this.api, this.packetsender, this.timerUtils, this.stt);
        }

        @Default
        @CommandCompletion("@tracks")
        public void onDefault(Player player, String[] args) {
            if (this.plugin.getTimeTrialDuels() != null && this.plugin.getTimeTrialDuels().isPlayerInDuel(player.getUniqueId())) {
                this.plugin.sendMessage(player, "tt_error_duel_active", new String[0]);
                this.plugin.sendMessage(player, "tt_error_finish_current", new String[0]);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            } else if (args.length == 0) {
                this.menuUtils.open(player);
            } else {
                String trackName;
                if (args[0].equalsIgnoreCase("solo")) {
                    if (args.length == 1) {
                        this.menuUtils.open(player);
                        return;
                    }

                    trackName = String.join(" ", (CharSequence[])Arrays.copyOfRange(args, 1, args.length));
                } else {
                    trackName = String.join(" ", args);
                }

                DatabaseManager.TrackData trackData = this.mysql.getTrackData(trackName);
                if (trackData == null) {
                    this.plugin.sendMessage(player, "tt_track_not_found", new String[]{"{track}", trackName});
                } else if (!this.mysql.isTrackOpen(trackData.getTrackName())) {
                    String lang = this.mysql.getPlayerLanguage(player.getUniqueId());
                    String msg = this.plugin.getDirectTranslation("track_is_closed", lang).replace("{track}", trackData.getTrackName());
                    player.sendMessage(msg);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                } else {
                    this.startTrack(player, trackData.getTrackName(), trackData.getOwnerName());
                }
            }
        }

        @CatchUnknown
        public void onUnknown(Player player) {
            CommandHelpService.sendHelp(player, this, "/timetrial");
        }

        @Subcommand("help|ajuda|?")
        @Description("Shows the timetrial command help")
        public void onHelp(Player player) {
            CommandHelpService.sendHelp(player, this, "/timetrial");
        }

        @Subcommand("solo")
        @CommandCompletion("@tracks")
        @Description("Starts a solo Time Trial on a track")
        public void onSolo(Player player, String[] args) {
            this.onDefault(player, args);
        }

        private void startTrack(Player player, String trackName, String ownerName) {
            if (isBusy(player)) return;

            // Save partial time asynchronously (non-blocking)
            String lastTrack = this.plugin.getLastTimeTrialTrack(player.getUniqueId());
            if (lastTrack != null) {
                TimerUtils.PlayerTimerData data = this.timerUtils.getTimerData(player, lastTrack);
                if (data != null) {
                    double elapsedTime = this.timerUtils.getPlayerElapsedTimeUntilLastCheckpoint(player, lastTrack);
                    int checkpoints = data.getCheckpointsReached();
                    if (checkpoints > 0) {
                        final double finalElapsed = elapsedTime;
                        final int finalCheckpoints = checkpoints;
                        SchedulerHelper.runAsync(this.plugin, () ->
                            this.mysql.savePartialTime(player.getUniqueId(), player.getName(), lastTrack, finalElapsed, finalCheckpoints)
                        );
                    }
                }
            }

            if (this.mysql.trackHaveBoatUtils(trackName) && !FormulaRacing.hasOpenBoatUtilsMod(player)) {
                this.plugin.sendMessage(player, "obu_mandatory_warning", new String[]{"{track}", trackName});
            } else {
                this.packetsender.sendBoatSetting(player, 0, new Object[0]);
                this.packetsender.applyBoatUtilsToPlayer(player, trackName);
                Location loc = this.mysql.getTrackSpawn(trackName);
                if (loc == null) {
                    this.plugin.sendMessage(player, "tt_track_no_spawn", new String[0]);
                } else {
                    // Enable time trial asynchronously (non-blocking)
                    if (!this.mysql.getTimeTrialEnabled(player.getUniqueId())) {
                        SchedulerHelper.runAsync(this.plugin, () ->
                            this.mysql.setTimeTrialEnabled(player.getUniqueId(), true)
                        );
                        this.plugin.sendMessage(player, "tt_auto_enabled", new String[0]);
                    }

                    this.timerUtils.stopTimer(player);
                    this.timeTrialController.endSession(player);
                    if (this.plugin.getWolfTimingService() != null) {
                        this.plugin.getWolfTimingService().prepareTrack(player, trackName);
                    }
                    this.plugin.setLastTimeTrialTrack(player.getUniqueId(), trackName);
                    this.plugin.getDebugManager().logTimeTrialSystem("[TT] Starting track '" + trackName + "' for player " + player.getName());
                    sendTimeTrialTeleportMessage(player, trackName);

                    try {
                        this.stt.setPlayerTrack(player, trackName, ownerName);
                    } catch (Exception e) {
                        this.plugin.getDebugManager().logTimeTrialSystem("[ERROR] Failed to set player track for scoreboard: " + e.getMessage());
                        e.printStackTrace();
                    }

                    this.api.recoverPlayerBoatState(player);
                    final String trackNameWS = trackName.replaceAll("\\s+", "");
                    SchedulerHelper.teleportAsync(player, loc).thenAccept(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            this.api.spawnBoatAt(player, loc, false, false, false);
                            // Apply track game time (day/night cycle)
                            this.plugin.applyTrackGameTime(player, trackName);
                            // Ensure giveTimeTrialHotbar runs on the correct thread
                            SchedulerHelper.runTaskFor(this.plugin, player, () ->
                                this.plugin.getHotbarController().giveTimeTrialHotbar(player)
                            );
                            // --- Ghost System: start recording ---
                            if (this.plugin.getGhostManager() != null) {
                                this.plugin.getGhostManager().startRecording(player);
                            }                             // --- Ghost System: load and start replay if ghost exists ---
                             if (this.plugin.getGhostManager() != null) {
                                 this.plugin.getGhostManager().loadGhostAsync(
                                         player.getUniqueId(), trackNameWS, frames -> {
                                     if (frames != null && !frames.isEmpty() && player.isOnline()) {
                                         this.plugin.getGhostManager().startReplay(player, frames);
                                     }
                                 });
                             }
                             // --- Medal System: start colored medal line replay only if the
                             // player's PB is slower than the medal time ---
                             if (this.plugin.getMedalManager() != null) {
                                 this.plugin.getMedalManager().startMedalReplayIfBetter(player, trackNameWS);
                             }
                             // --- WolfMOD: Send track racing line to client ---
                             var wolfMod = this.plugin.getWolfMod();
                             if (wolfMod != null) {
                                 SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                                     wolfMod.sendGhostClear(player);
                                     AIRacingLineManager aiManager = this.plugin.getAIRacingLineManager();
                                     if (aiManager != null && aiManager.hasRacingLine(trackNameWS)) {
                                         var line = aiManager.getRacingLine(trackNameWS);
                                         if (line != null && line.isUsable()) {
                                             wolfMod.sendTrackLine(player, trackNameWS, line);
                                             wolfMod.sendGhostStart(player);
                                         }
                                     }
                                 });
                             }
                         }
                    });
                }
            }
        }

        /**
         * Sends the {@code /tt} "Teleported to [track]" message, appending the
         * player's leaderboard position when they already have a finished time on
         * that track.
         *
         * <p>The rank lives in the database, so it is fetched asynchronously and the
         * message is sent from the completion callback — the main thread is never
         * blocked on a query. {@code getPlayerRank} returns {@code 0} when the player
         * has no finished run on this track, in which case the message renders
         * exactly as before (the position suffix is empty).
         *
         * @param player    the teleported player
         * @param trackName the track display name; normalized for the lookup query
         */
        private void sendTimeTrialTeleportMessage(Player player, String trackName) {
            String trackWS = trackName.replaceAll("\\s+", "");
            this.mysql.getPlayerRankAsync(player.getUniqueId(), trackWS).thenAccept(rank -> {
                // Never touch a player from a worker thread: they may have logged
                // off while the query was still in flight.
                if (!player.isOnline()) {
                    return;
                }
                this.plugin.sendMessage(
                        player,
                        "timetrial_teleport",
                        new String[]{"{track}", trackName, "{position}", TimeTrialTeleportMessage.rankSuffix(rank)}
                );
            });
        }

        @CommandAlias("timetrialcancel|ttc|timetrialc|ttcancel")
        @Description("Cancels the current Time Trial")
        public void onCancel(Player player) {
            this.timerUtils.stopTimer(player);
            this.timeTrialController.endSession(player);
            if (this.plugin.getWolfTimingService() != null) {
                this.plugin.getWolfTimingService().abort(player.getUniqueId(), true);
            }
            // NOTE: o /ttc NÃO faz resetTrackGameTime — o tempo do jogador
            // permanece como está (aplica-se apenas ao sair para spawn/mudar de modo).
            // Clean up ghost recording and replay
            if (this.plugin.getGhostManager() != null) {
                this.plugin.getGhostManager().cleanupPlayer(player);
            }
            // Clean up WolfMOD ghost rendering
            var wolfMod = this.plugin.getWolfMod();
            if (wolfMod != null) {
                wolfMod.sendGhostStop(player);
                wolfMod.sendGhostClear(player);
            }
            this.plugin.sendMessage(player, "tt_cancelled", new String[0]);
            if (this.plugin.getLonelyController() != null) {
                this.plugin.getLonelyController().updatePlayersVisibility(player);
                this.plugin.getLonelyController().updatePlayerVisibility(player);
            }

        }

        @CommandAlias("timetrialrandom|ttr|timetrialr|ttrandom")
        @Description("Joins a random Time Trial")
        public void onRandom(Player player) {
            // 1. Safety Checks (Early Returns) — only the player's own thread may read this.
            if (isBusy(player)) {
                return;
            }

            final UUID uuid = player.getUniqueId();
            final String playerName = player.getName();
            final boolean hasBoatUtils = FormulaRacing.hasOpenBoatUtilsMod(player);
            final String currentTrackWS = normalizeTrackName(this.plugin.getLastTimeTrialTrack(uuid));

            // 2. Building the pool and picking the track reads the database, so it runs off
            //    the main thread. The old implementation did one `isTrackOpen` plus one
            //    `trackHaveBoatUtils` query per track right on the main thread.
            SchedulerHelper.runAsync(this.plugin, () -> {
                Set<String> openTracks = this.mysql.getOpenTracks();
                if (openTracks.isEmpty()) {
                    SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                        if (player.isOnline()) this.plugin.sendMessage(player, "tt_no_tracks_avail");
                    });
                    return;
                }

                List<String> validTracks = this.collectValidRandomTracks(openTracks, currentTrackWS);
                if (validTracks.isEmpty() && currentTrackWS != null) {
                    // The player's current track is the only playable one: repeating it is
                    // better than telling them there is nothing left to race on.
                    validTracks = this.collectValidRandomTracks(openTracks, null);
                }

                // 3. Rerolling instead of pre-filtering keeps the player without the mod from
                //    paying one query per track: a mod-less pick costs a single extra query.
                final int poolSize = validTracks.size();
                String trackName = null;
                while (trackName == null && !validTracks.isEmpty()) {
                    int index = this.random.nextInt(validTracks.size());
                    String candidate = validTracks.get(index);
                    if (hasBoatUtils || !this.mysql.trackHaveBoatUtils(candidate)) {
                        trackName = candidate;
                    } else {
                        validTracks.remove(index);
                    }
                }

                if (trackName == null) {
                    SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                        if (player.isOnline()) this.plugin.sendMessage(player, "tt_no_tracks_compatible");
                    });
                    return;
                }

                this.plugin.getDebugManager().logTimeTrialSystem(
                    "[TTR] " + playerName + " rolled '" + trackName + "' out of " + poolSize + " valid tracks"
                );

                DatabaseManager.TrackData trackData = this.mysql.getTrackData(trackName);
                String owner = (trackData != null) ? trackData.getOwnerName() : null;
                final String finalTrackName = trackName;

                // 4. Teleport/hotbar/scoreboard only touch Bukkit state, so they go back to
                //    the player's own thread.
                SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                    if (player.isOnline()) this.startTrack(player, finalTrackName, owner);
                });
            });
        }

        /**
         * Builds the pool of tracks that /ttr (and the hotbar's random item, which runs
         * the same command) can pick from: only open tracks whose world is loaded, that
         * have a spawn and at least one checkpoint, skipping the track the player is
         * already on. Call it off the main thread — it queries the database.
         */
        private List<String> collectValidRandomTracks(Set<String> openTracks, String excludedTrackWS) {
            Set<String> openNormalized = openTracks.stream()
                    .map(TimeTrialCommand::normalizeTrackName)
                    .collect(Collectors.toSet());

            // A single query for every track (the database layer already skips tracks whose
            // world is not loaded) instead of one query per track.
            Map<String, DatabaseManager.TrackData> tracksData = this.mysql.getAllTracksWithData();

            List<String> validTracks = new ArrayList<>();
            for (Map.Entry<String, DatabaseManager.TrackData> entry : tracksData.entrySet()) {
                String trackName = entry.getKey();
                String trackWS = normalizeTrackName(trackName);
                if (!openNormalized.contains(trackWS)) continue;
                if (excludedTrackWS != null && excludedTrackWS.equals(trackWS)) continue;

                DatabaseManager.TrackData data = entry.getValue();
                Location spawn = data.getSpawnLocation();
                // Starting on a track without spawn or checkpoints would either fail the
                // teleport ("tt_track_no_spawn") or leave the player on a track the timer
                // can never finish, so those are never drawn.
                if (spawn == null || spawn.getWorld() == null) continue;
                if (data.getTotalCheckpoints() <= 0) continue;

                validTracks.add(trackName);
            }
            return validTracks;
        }

        /** Track names are compared without spaces and case-insensitively, as the database does. */
        private static String normalizeTrackName(String trackName) {
            return trackName == null ? null : trackName.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        }

        /**
         * Checks if the player is busy in any other game mode.
         */
        private boolean isBusy(Player player) {
            UUID uuid = player.getUniqueId();
            Location loc = player.getLocation();

            // Check Duel
            if (this.plugin.getTimeTrialDuels() != null && this.plugin.getTimeTrialDuels().isPlayerInDuel(uuid)) {
                this.plugin.sendMessage(player, "tt_error_duel_active");
                player.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                return true;
            }

            // Check QuickRace
            if (this.plugin.getQuickRaceManager() != null && this.plugin.getQuickRaceManager().isPlayerInActiveRace(uuid)) {
                this.plugin.sendMessage(player, "tt_error_quickrace");
                player.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                return true;
            }

            // Check Official Events (Heats)
            if (this.plugin.getRaceEventManager() != null && this.plugin.getRaceEventManager().getPlayerActiveHeat(uuid).isPresent()) {
                this.plugin.sendMessage(player, "tt_error_event");
                player.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                return true;
            }

            return false;
        }
    
        @CommandAlias("reset")
        @Description("Resets your current Time Trial")
        public void onReset(Player player) {
            String trackName = null;
            Heats activeHeat = null;
            if (this.plugin.getQuickRaceManager() != null && this.plugin.getQuickRaceManager().isPlayerInActiveRace(player.getUniqueId())) {
                this.plugin.sendMessage(player, "tt_error_reset_quickrace", new String[0]);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            } else {
                if (this.plugin.getRaceEventManager() != null) {
                    activeHeat = this.plugin.getRaceEventManager().getPlayerActiveHeat(player.getUniqueId()).orElse(null);
                    if (activeHeat != null) {
                        Driver driver = activeHeat.getDriver(player.getUniqueId());
                        if (driver == null || !driver.isFinished() && !driver.isDnf()) {
                            if (activeHeat.getHeatState() != HeatState.PRACTICE) {
                                this.plugin.sendMessage(player, "tt_error_reset_mode", new String[0]);
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                                return;
                            }

                            trackName = activeHeat.getTrackNameWS();
                        } else {
                            activeHeat = null;
                        }
                    }
                }

                if (trackName == null && this.plugin.getTimeTrialDuels() != null && this.plugin.getTimeTrialDuels().isPlayerInDuel(player.getUniqueId())) {
                    this.plugin.sendMessage(player, "tt_error_reset_duel", new String[0]);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                } else {
                    if (trackName == null) {
                        trackName = this.plugin.getLastTimeTrialTrack(player.getUniqueId());
                    }

                    if (trackName == null) {
                        this.plugin.sendMessage(player, "tt_error_no_session", new String[0]);
                    } else {
                        Location spawn = this.mysql.getTrackSpawn(trackName);
                        if (spawn == null) {
                            this.plugin.sendMessage(player, "tt_error_spawn_not_found", new String[]{"{track}", trackName});
                        } else {
                            if (activeHeat == null) {
                                String activeTimerTrack = this.timerUtils.getActiveTrack(player);
                                if (activeTimerTrack == null) {
                                    activeTimerTrack = trackName.replaceAll("\\s+", "");
                                }

                                TimerUtils.PlayerTimerData data = this.timerUtils.getTimerData(player, activeTimerTrack);
                                if (data != null) {
                                    int lastCheckpointIndex = data.getCheckpointsReached();
                                    if (lastCheckpointIndex > 0) {
                                        double elapsedTime = this.timerUtils.getPlayerElapsedTimeUntilLastCheckpoint(player, activeTimerTrack);
                                        this.mysql.savePartialTime(player.getUniqueId(), player.getName(), trackName, elapsedTime, lastCheckpointIndex);
                                        this.plugin.sendMessage(player, "tt_save_partial", new String[]{"{count}", String.valueOf(lastCheckpointIndex), "{time}", this.timerUtils.formatTime(elapsedTime, true, false)});
                                        this.timerUtils.resetTempCheckpoints(player.getUniqueId());
                                    }
                                }

                                this.timerUtils.stopTimer(player);
                                this.timeTrialController.endSession(player);
                            } else {
                                this.timerUtils.stopTimer(player, trackName);
                                this.timeTrialController.endSession(player);
                                Driver driver = activeHeat.getDriver(player.getUniqueId());
                                if (driver != null) {
                                    driver.setCurrentLap((Lap)null);
                                    driver.setCheckpointsReached(0);
                                    driver.setCachedDelta("");
                                    driver.setLastProcessedCheckpointId(-1);
                                    driver.setFinished(false);
                                    driver.setDnf(false);
                                    driver.setState(DriverState.RUNNING);
                                }
                            }

                            // --- Ghost System: hide PB and medal lines when resetting ---
                            // They reappear when the player crosses START/END again (startSoloTimer).
                            if (this.plugin.getGhostManager() != null) {
                                this.plugin.getGhostManager().stopReplay(player);
                            }

                            this.api.recoverPlayerBoatState(player);
                            final String finalTrackName = trackName;
                            final String finalTrackNameWS = trackName != null ? trackName.replaceAll("\\s+", "") : null;
                            SchedulerHelper.teleportAsync(player, spawn).thenAccept(success -> {
                                if (Boolean.TRUE.equals(success)) {
                                    this.api.spawnBoatAt(player, spawn, false, false, false);
                                    // O barco novo nasce com física vanilla: o OpenBoatUtils perde
                                    // a config da pista quando a entidade do barco troca. Reenvia o
                                    // pacote (mesmo efeito do /tt) ou o reset deixa o barco vanilla.
                                    if (this.packetsender != null) {
                                        this.packetsender.resetBoatUtilsToVanilla(player);
                                        this.packetsender.applyBoatUtilsToPlayer(player, finalTrackName);
                                    }
                                    // Apply track game time (day/night cycle)
                                    this.plugin.applyTrackGameTime(player, finalTrackName);
                                    // Set time trial hotbar after teleport + boat spawn
                                    SchedulerHelper.runTaskFor(this.plugin, player, () ->
                                        this.plugin.getHotbarController().giveTimeTrialHotbar(player)
                                    );
                                    // --- Ghost System: start recording ---
                                    if (this.plugin.getGhostManager() != null
                                            && finalTrackNameWS != null) {
                                        this.plugin.getGhostManager().startRecording(player);
                                    }
                                    // NOTE: PB/medal line replays are NOT restarted here on purpose —
                                    // they must stay hidden after a reset and only reappear when the
                                    // player crosses START/END again (startSoloTimer handles that).
                                }
                            });
                                    if (activeHeat == null) {
                                String owner = null;
                                if (trackName != null) {
                                    DatabaseManager.TrackData td = this.mysql.getTrackData(trackName);
                                    if (td != null) {
                                        owner = td.getOwnerName();
                                    }
                                }

                                this.stt.setPlayerTrack(player, trackName, owner);
                            }
                            // Clear WolfMOD ghost when resetting
                            var wolfMod = this.plugin.getWolfMod();
                            if (wolfMod != null) {
                                wolfMod.sendGhostStop(player);
                                wolfMod.sendGhostClear(player);
                            }

                        }
                    }
                }
            }
        }
    }
