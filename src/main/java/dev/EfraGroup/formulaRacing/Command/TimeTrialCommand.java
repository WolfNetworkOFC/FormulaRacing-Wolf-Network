    //
    // Source code recreated from a .class file by IntelliJ IDEA
    // (powered by Fernflower decompiler)
    //

    package dev.EfraGroup.formulaRacing.Command;

    import dev.EfraGroup.formulaRacing.APIFormulaRacing;
    import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
    import dev.EfraGroup.formulaRacing.FormulaRacing;
    import dev.EfraGroup.formulaRacing.PacketSender;
    import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
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
    @Description("Comandos do sistema de Time Trial")
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
        @Description("Mostra a ajuda do comando timetrial")
        public void onHelp(Player player) {
            CommandHelpService.sendHelp(player, this, "/timetrial");
        }

        @Subcommand("solo")
        @CommandCompletion("@tracks")
        @Description("Inicia um Time Trial solo em uma pista")
        public void onSolo(Player player, String[] args) {
            this.onDefault(player, args);
        }

        private void startTrack(Player player, String trackName, String ownerName) {
            String lastTrack = this.plugin.getLastTimeTrialTrack(player.getUniqueId());
            if (lastTrack != null) {
                TimerUtils.PlayerTimerData data = this.timerUtils.getTimerData(player, lastTrack);
                if (data != null) {
                    double elapsedTime = this.timerUtils.getPlayerElapsedTimeUntilLastCheckpoint(player, lastTrack);
                    int checkpoints = data.getCheckpointsReached();
                    if (checkpoints > 0) {
                        this.mysql.savePartialTime(player.getUniqueId(), player.getName(), lastTrack, elapsedTime, checkpoints);
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
                    if (!this.mysql.getTimeTrialEnabled(player.getUniqueId())) {
                        this.mysql.setTimeTrialEnabled(player.getUniqueId(), true);
                        this.plugin.sendMessage(player, "tt_auto_enabled", new String[0]);
                    }

                    this.timerUtils.stopTimer(player);
                    this.timeTrialController.endSession(player);
                    player.teleport(loc);
                    this.plugin.setLastTimeTrialTrack(player.getUniqueId(), trackName);
                    this.plugin.getDebugManager().logTimeTrialSystem("[TT] Starting track '" + trackName + "' for player " + player.getName());
                    this.plugin.sendMessage(player, "timetrial_teleport", new String[]{"{track}", trackName});

                    try {
                        this.stt.setPlayerTrack(player, trackName, ownerName);
                    } catch (Exception e) {
                        this.plugin.getDebugManager().logTimeTrialSystem("[ERROR] Failed to set player track for scoreboard: " + e.getMessage());
                        e.printStackTrace();
                    }

                    this.api.spawnBoat(player, false, false, false);
                }
            }
        }

        @CommandAlias("timetrialcancel|ttc|timetrialc|ttcancel")
        @Description("Cancela o Time Trial atual")
        public void onCancel(Player player) {
            this.timerUtils.stopTimer(player);
            this.timeTrialController.endSession(player);
            this.plugin.sendMessage(player, "tt_cancelled", new String[0]);
            if (this.plugin.getLonelyController() != null) {
                this.plugin.getLonelyController().updatePlayersVisibility(player);
                this.plugin.getLonelyController().updatePlayerVisibility(player);
            }

        }

        @CommandAlias("timetrialrandom|ttr|timetrialr|ttrandom")
        @Description("Entra em uma Time Trial aleatória")
        public void onRandom(Player player) {
            UUID uuid = player.getUniqueId();

            // 1. Verificações de Segurança (Early Returns)
            if (isBusy(player)) {
                return;
            }

            // 2. Obtenção de Pistas
            List<String> availableTracks = this.mysql.getAllTracks();
            if (availableTracks == null || availableTracks.isEmpty()) {
                this.plugin.sendMessage(player, "tt_no_tracks_avail");
                return;
            }

            // 3. Filtragem de Pistas Compatíveis
            boolean hasBoatUtils = FormulaRacing.hasOpenBoatUtilsMod(player);

            List<String> validTracks = availableTracks.stream()
                    .filter(this.mysql::isTrackOpen) // Filtra pistas abertas
                    .filter(track -> {
                        boolean trackRequiresBoatUtils = this.mysql.trackHaveBoatUtils(track);
                        // Se o player tem o mod, pode correr em qualquer uma.
                        // Se não tem, só nas que não requerem.
                        return hasBoatUtils || !trackRequiresBoatUtils;
                    })
                    .collect(Collectors.toList());

            if (validTracks.isEmpty()) {
                this.plugin.sendMessage(player, "tt_no_tracks_compatible");
                return;
            }

            // 4. Seleção e Início
            String trackName = validTracks.get(this.random.nextInt(validTracks.size()));
            DatabaseManager.TrackData trackData = this.mysql.getTrackData(trackName);
            String owner = (trackData != null) ? trackData.getOwnerName() : null;

            this.startTrack(player, trackName, owner);
        }

        /**
         * Verifica se o jogador está ocupado em qualquer outro modo de jogo.
         */
        private boolean isBusy(Player player) {
            UUID uuid = player.getUniqueId();
            Location loc = player.getLocation();

            // Verifica Duelo
            if (this.plugin.getTimeTrialDuels() != null && this.plugin.getTimeTrialDuels().isPlayerInDuel(uuid)) {
                this.plugin.sendMessage(player, "tt_error_duel_active");
                player.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                return true;
            }

            // Verifica QuickRace
            if (this.plugin.getQuickRaceManager() != null && this.plugin.getQuickRaceManager().isPlayerInActiveRace(uuid)) {
                this.plugin.sendMessage(player, "tt_error_quickrace");                player.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                return true;
            }

            // Verifica Eventos Oficiais (Heats)
            if (this.plugin.getRaceEventManager() != null && this.plugin.getRaceEventManager().getPlayerActiveHeat(uuid).isPresent()) {
                this.plugin.sendMessage(player, "tt_error_event");
                player.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                return true;
            }

            return false;
        }
    
        @CommandAlias("reset")
        @Description("Reseta sua Time Trial atual")
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

                            this.api.recoverPlayerBoatState(player);
                            player.teleport(spawn);
                            this.api.spawnBoat(player, false, false, false);
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

                        }
                    }
                }
            }
        }
    }
