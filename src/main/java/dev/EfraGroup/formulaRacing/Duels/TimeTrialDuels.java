//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Duels;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardDuelsTimeUtils;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialDuelsAction;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Boat.Type;
import org.bukkit.event.Listener;

public class TimeTrialDuels implements Listener {
    private final FormulaRacing plugin;
    private final DatabaseManager dm;
    private final PacketSender packet;
    private final TimeTrialDuelsAction ttda;
    private final ScoreboardDuelsTimeUtils scoreboardDuelsUtils;
    private final Map<Integer, DuelState> activeDuels = new ConcurrentHashMap();
    private final Map<UUID, PlayerDuelState> playerStates = new ConcurrentHashMap();
    private static final Set<UUID> playersBeingLapReset = ConcurrentHashMap.newKeySet();

    public TimeTrialDuels(FormulaRacing plugin, DatabaseManager dm, PacketSender packet, TimeTrialDuelsAction ttda, ScoreboardDuelsTimeUtils scoreboardDuelsUtils) {
        this.plugin = plugin;
        this.dm = dm;
        this.packet = packet;
        this.ttda = ttda;
        this.scoreboardDuelsUtils = scoreboardDuelsUtils;
    }

    public boolean isPlayerInActiveDuelCached(UUID playerUUID) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(playerUUID);
        if (state == null) {
            return false;
        } else {
            DuelState duelState = (DuelState)this.activeDuels.get(state.getDuelId());
            return duelState != null;
        }
    }

    public int getActiveDuelIdCached(UUID playerUUID) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(playerUUID);
        if (state == null) {
            return -1;
        } else {
            DuelState duelState = (DuelState)this.activeDuels.get(state.getDuelId());
            if (duelState == null) {
                this.playerStates.remove(playerUUID);
                return -1;
            } else {
                return state.getDuelId();
            }
        }
    }

    public String getDuelTrackNameCached(int duelId) {
        DuelState state = (DuelState)this.activeDuels.get(duelId);
        return state != null ? state.getTrackName() : null;
    }

    public DuelState getDuelState(int duelId) {
        return (DuelState)this.activeDuels.get(duelId);
    }

    public void startDuelPreparation(Player p1, Player p2, String trackName, int laps, int timeLimit, boolean lonely, boolean isTimeTrialMode) {
        Location spawnLoc = this.dm.getTrackSpawn(trackName);
        if (spawnLoc == null) {
            String lang1 = this.dm.getPlayerLanguage(p1.getUniqueId());
            String lang2 = this.dm.getPlayerLanguage(p2.getUniqueId());
            p1.sendMessage(this.plugin.getDirectTranslation("duel_error_spawn", lang1));
            p2.sendMessage(this.plugin.getDirectTranslation("duel_error_spawn", lang2));
        } else {
            if (this.dm.trackHaveBoatUtils(trackName)) {
                boolean p1HasObu = FormulaRacing.hasOpenBoatUtilsMod(p1);
                boolean p2HasObu = FormulaRacing.hasOpenBoatUtilsMod(p2);
                if (!p1HasObu) {
                    this.plugin.sendMessage(p1, "obu_mandatory_warning", new String[]{"{track}", trackName});
                }

                if (!p2HasObu) {
                    this.plugin.sendMessage(p2, "obu_mandatory_warning", new String[]{"{track}", trackName});
                }
            }

            this.cleanupSoloTimeTrial(p1);
            this.cleanupSoloTimeTrial(p2);
            String trackNameWS = trackName.replace(" ", "");
            List<Player> participants = Arrays.asList(p1, p2);
            String modeStr = isTimeTrialMode ? "TIME TRIAL" : "CORRIDA";
            this.plugin.getDebugManager().logDuelSystem("[SETUP] Modo de duelo: " + modeStr);
            int[] duelIdHolder = new int[]{-1};
SchedulerHelper.runTaskFor(this.plugin, p1, () -> {
                this.dm.createDuel(p1, participants, trackNameWS, laps, timeLimit, lonely);
                int duelId = this.dm.getActiveDuelId(p1.getUniqueId());
                duelIdHolder[0] = duelId;
                if (duelId == -1) {
                    this.plugin.getDebugManager().logDuelSystem("§c[ERRO CRÍTICO] Duelo criado mas ID não encontrado!");
                    String lang1 = this.dm.getPlayerLanguage(p1.getUniqueId());
                    String lang2 = this.dm.getPlayerLanguage(p2.getUniqueId());
                    p1.sendMessage(this.plugin.getDirectTranslation("duel_error_create", lang1));
                    p2.sendMessage(this.plugin.getDirectTranslation("duel_error_create", lang2));
                } else {
                    DuelState duelState = new DuelState(duelId, trackNameWS, laps, timeLimit, lonely, isTimeTrialMode);
                    duelState.addPlayer(p1.getUniqueId());
                    duelState.addPlayer(p2.getUniqueId());
                    this.activeDuels.put(duelId, duelState);
                    this.playerStates.put(p1.getUniqueId(), new PlayerDuelState(p1.getUniqueId(), duelId));
                    this.playerStates.put(p2.getUniqueId(), new PlayerDuelState(p2.getUniqueId(), duelId));
                    this.plugin.getDebugManager().logDuelSystem("§a[DUEL] Duelo #" + duelId + " criado: " + p1.getName() + " vs " + p2.getName());
                    if (p1.getVehicle() != null && p1.getVehicle() instanceof Boat) {
                        Boat oldBoat = (Boat)p1.getVehicle();
                        oldBoat.eject();
                        oldBoat.remove();
                        this.plugin.getDebugManager().logDuelSystem("[PREP] Removeu barco antigo de " + p1.getName());
                    }

                    if (p2.getVehicle() != null && p2.getVehicle() instanceof Boat) {
                        Boat oldBoat = (Boat)p2.getVehicle();
                        oldBoat.eject();
                        oldBoat.remove();
                        this.plugin.getDebugManager().logDuelSystem("[PREP] Removeu barco antigo de " + p2.getName());
                    }

                    this.packet.resetBoatUtilsToVanilla(p1);
                    this.packet.resetBoatUtilsToVanilla(p2);
                    this.packet.applyBoatUtilsToPlayer(p1, trackNameWS);
                    this.packet.applyBoatUtilsToPlayer(p2, trackNameWS);
                    SchedulerHelper.runTaskLater(this.plugin, () -> {
                        this.ttda.toggleVisuals(p1, duelId, true);
                        this.ttda.toggleVisuals(p2, duelId, true);
                        this.scoreboardDuelsUtils.applyDuelBoard(p1, duelId, laps, trackName);
                        this.scoreboardDuelsUtils.applyDuelBoard(p2, duelId, laps, trackName);
                        this.plugin.getLonelyController().updatePlayersVisibility(p1);
                    }, 1L);
                }
            });
        }
    }

    private void startFullCountdownSequence(final Player p1, final Player p2, final int duelId, final int timeLimit) {
        ScheduledTask[] cdTask = {null};
        cdTask[0] = SchedulerHelper.runTaskTimer(this.plugin, new Runnable() {
            int countdown = 5;

            public void run() {
                if (p1.isOnline() && p2.isOnline()) {
                    if (this.countdown > 0) {
                        String number = "" + this.countdown;
                        TitleHelper.sendThemedTitle(p1, number, "", 0, 20, 5);
                        TitleHelper.sendThemedTitle(p2, number, "", 0, 20, 5);
                        p1.playSound(p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0F, 0.5F);
                        p2.playSound(p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0F, 0.5F);
                        --this.countdown;
                    } else {
                        String p1Lang = TimeTrialDuels.this.dm.getPlayerLanguage(p1.getUniqueId());
                        String p2Lang = TimeTrialDuels.this.dm.getPlayerLanguage(p2.getUniqueId());
                        TitleHelper.sendThemedTitle(p1, TimeTrialDuels.this.plugin.getTranslation("duel_countdown_go", p1Lang, new String[0]), "", 0, 20, 5);
                        TitleHelper.sendThemedTitle(p2, TimeTrialDuels.this.plugin.getTranslation("duel_countdown_go", p2Lang, new String[0]), "", 0, 20, 5);
                        p1.playSound(p1.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                        p2.playSound(p2.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                        TimeTrialDuels.this.releasePlayers(p1, p2);
                        DuelState state = (DuelState)TimeTrialDuels.this.activeDuels.get(duelId);
                        if (state != null) {
                            state.setRaceStarted(true);
                            state.setRaceStartTime(System.currentTimeMillis());
                        }

                        String timeLimitStr;
                        if (timeLimit >= 60) {
                            int minutes = timeLimit / 60;
                            timeLimitStr = minutes + " minuto" + (minutes != 1 ? "s" : "");
                        } else {
                            timeLimitStr = timeLimit + " segundo" + (timeLimit != 1 ? "s" : "");
                        }

                        TimeTrialDuels.this.plugin.getDebugManager().logDuelSystem("Duelo #" + duelId + " iniciado! Tempo limite: " + timeLimitStr);
                        if (timeLimit > 0) {
                            TimeTrialDuels.this.startTimeLimitTimer(duelId, timeLimit);
                        }

                        if (cdTask[0] != null) cdTask[0].cancel();
                    }

                } else {
                    if (cdTask[0] != null) cdTask[0].cancel();
                    TimeTrialDuels.this.endDuelByDisconnect(duelId);
                }
            }
        }, 0L, 20L);
    }

    private void startTimeLimitTimer(final int duelId, final int timeLimitSeconds) {
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (duelState != null) {
            ScheduledTask[] tlimitTask = {null};
            tlimitTask[0] = SchedulerHelper.runTaskTimer(this.plugin, new Runnable() {
                int secondsRemaining = timeLimitSeconds;

                public void run() {
                    DuelState state = (DuelState)TimeTrialDuels.this.activeDuels.get(duelId);
                    if (state == null) {
                        if (tlimitTask[0] != null) tlimitTask[0].cancel();
                    } else if (state.getFinishCount() >= state.getPlayerCount()) {
                        if (tlimitTask[0] != null) tlimitTask[0].cancel();
                    } else {
                        --this.secondsRemaining;
                        if (this.secondsRemaining == 60 || this.secondsRemaining == 30 || this.secondsRemaining == 10 || this.secondsRemaining == 5) {
                            for(UUID uuid : state.getPlayers()) {
                                Player p = Bukkit.getPlayer(uuid);
                                if (p != null && p.isOnline()) {
                                    String langCode = TimeTrialDuels.this.dm.getPlayerLanguage(uuid);
                                    String message = TimeTrialDuels.this.plugin.getTranslation("duel_time_remaining", langCode, new String[]{"{time}", String.valueOf(this.secondsRemaining)});
                                    p.sendMessage(message);
                                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.5F);
                                }
                            }
                        }

                        if (this.secondsRemaining <= 0) {
                            TimeTrialDuels.this.endDuelByTimeLimit(duelId);
                            if (tlimitTask[0] != null) tlimitTask[0].cancel();
                        }

                    }
                }
            }, 20L, 20L);
        }
    }

    private void endDuelByTimeLimit(int duelId) {
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (duelState != null) {
            this.plugin.getDebugManager().logDuelSystem("§c[DUEL] Tempo limite atingido no duelo #" + duelId + " - Aguardando jogadores completarem a volta atual");
            duelState.setTimeLimitReached(true);

            for(UUID uuid : duelState.getPlayers()) {
                PlayerDuelState playerState = (PlayerDuelState)this.playerStates.get(uuid);
                if (playerState != null && !playerState.isFinished()) {
                    int currentLap = playerState.getCurrentLap();
                    playerState.setLapWhenTimeLimitReached(currentLap);
                    DebugManager var10000 = this.plugin.getDebugManager();
                    String var10001 = Bukkit.getOfflinePlayer(uuid).getName();
                    var10000.logDuelSystem("§e[TIME LIMIT] " + var10001 + " estava na volta " + currentLap + " quando tempo limite foi atingido");
                }
            }

            for(UUID uuid : duelState.getPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    PlayerDuelState playerState = (PlayerDuelState)this.playerStates.get(uuid);
                    if (playerState != null && !playerState.isFinished()) {
                        String langCode = this.dm.getPlayerLanguage(uuid);
                        String finalLapTitle = this.plugin.getDirectTranslation("duel_final_lap_title", langCode);
                        String finalLapSubtitle = this.plugin.getDirectTranslation("duel_final_lap_subtitle", langCode);
                        TitleHelper.sendThemedTitle(player, finalLapTitle, finalLapSubtitle, 10, 60, 20);
                        player.sendMessage(" ");
                        player.sendMessage(this.plugin.getDirectTranslation("duel_time_up_message", langCode));
                        player.sendMessage(this.plugin.getDirectTranslation("duel_complete_current_lap", langCode));
                        player.sendMessage(" ");
                        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0F, 0.8F);
                    }
                }
            }

            this.startFinalLapChecker(duelId);
        }
    }

    private void startFinalLapChecker(final int duelId) {
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (duelState != null) {
            double bestLapTime = Double.MAX_VALUE;

            for(UUID uuid : duelState.getPlayers()) {
                double playerBest = duelState.getBestLapTime(uuid);
                if (playerBest > (double)0.0F && playerBest < bestLapTime) {
                    bestLapTime = playerBest;
                }
            }

            final int timeoutSeconds;

            if (bestLapTime != Double.MAX_VALUE) {
                // Calculamos em uma variável auxiliar primeiro
                int calculated = (int) Math.ceil(bestLapTime * 3.0);
                // Aplicamos os limites (mínimo 60s, máximo 180s)
                timeoutSeconds = Math.max(60, Math.min(180, calculated));
            } else {
                timeoutSeconds = 120;
            }

            this.plugin.getDebugManager().logDuelSystem("§e[TIME LIMIT] Timeout configurado: " + timeoutSeconds + "s (baseado no melhor tempo: " + String.format("%.1f", bestLapTime) + "s)");
            ScheduledTask[] checkTask = {null};
            checkTask[0] = SchedulerHelper.runTaskTimer(this.plugin, new Runnable() {
                int checksRemaining = timeoutSeconds;

                public void run() {
                    DuelState duelState = (DuelState)TimeTrialDuels.this.activeDuels.get(duelId);
                    if (duelState == null) {
                        if (checkTask[0] != null) checkTask[0].cancel();
                    } else {
                        boolean allPlayersReady = true;

                        for(UUID uuid : duelState.getPlayers()) {
                            PlayerDuelState playerState = (PlayerDuelState)TimeTrialDuels.this.playerStates.get(uuid);
                            if (playerState != null) {
                                boolean playerReady = playerState.isFinished() || playerState.hasCompletedCurrentLapAfterTimeLimit();
                                if (!playerReady) {
                                    allPlayersReady = false;
                                    break;
                                }
                            }
                        }

                        if (allPlayersReady) {
                            TimeTrialDuels.this.plugin.getDebugManager().logDuelSystem("§a[TIME LIMIT] Todos os jogadores completaram a volta atual - finalizando duelo #" + duelId);
                            TimeTrialDuels.this.finalizeDuelAfterTimeLimit(duelId);
                            if (checkTask[0] != null) checkTask[0].cancel();
                        } else if (this.checksRemaining <= 0) {
                            TimeTrialDuels.this.plugin.getDebugManager().logDuelSystem("§c[TIME LIMIT] Timeout esgotado (" + timeoutSeconds + "s) - finalizando duelo #" + duelId + " (possível AFK/trollagem)");
                            TimeTrialDuels.this.finalizeDuelAfterTimeLimit(duelId);
                            if (checkTask[0] != null) checkTask[0].cancel();
                        } else {
                            --this.checksRemaining;
                        }
                    }
                }
            }, 20L, 20L);
        }
    }

    private void checkIfAllPlayersCompletedAfterTimeLimit(int duelId) {
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (duelState != null && duelState.isTimeLimitReached()) {
            boolean allPlayersReady = true;

            for(UUID uuid : duelState.getPlayers()) {
                PlayerDuelState playerState = (PlayerDuelState)this.playerStates.get(uuid);
                if (playerState != null) {
                    int lapWhenTimeLimitReached = playerState.getLapWhenTimeLimitReached();
                    int currentLap = playerState.getCurrentLap();
                    boolean playerReady = playerState.isFinished() || lapWhenTimeLimitReached >= 0 && currentLap > lapWhenTimeLimitReached;
                    if (!playerReady) {
                        allPlayersReady = false;
                        this.plugin.getDebugManager().logDuelSystem("§e[TIME LIMIT] " + Bukkit.getOfflinePlayer(uuid).getName() + " ainda não está pronto (volta quando tempo esgotou: " + lapWhenTimeLimitReached + ", volta atual: " + currentLap + ", finished=" + playerState.isFinished() + ")");
                        break;
                    }

                    this.plugin.getDebugManager().logDuelSystem("§a[TIME LIMIT] " + Bukkit.getOfflinePlayer(uuid).getName() + " está pronto (volta quando tempo esgotou: " + lapWhenTimeLimitReached + ", volta atual: " + currentLap + ", finished=" + playerState.isFinished() + ")");
                }
            }

            if (allPlayersReady) {
                this.plugin.getDebugManager().logDuelSystem("§a[TIME LIMIT] Todos os jogadores completaram a volta atual - finalizando duelo #" + duelId);
                SchedulerHelper.runTask(this.plugin, () -> this.finalizeDuelAfterTimeLimit(duelId));
            }

        }
    }

    private void finalizeDuelAfterTimeLimit(int duelId) {
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (duelState == null) {
            this.plugin.getDebugManager().logDuelSystem("§e[DUEL] finalizeDuelAfterTimeLimit() chamado mas duelo #" + duelId + " já foi finalizado");
        } else {
            this.activeDuels.remove(duelId);
            this.plugin.getDebugManager().logDuelSystem("§a[DUEL] Finalizando duelo #" + duelId + " após tempo limite");
            String trackName = duelState.getTrackName();

            for(UUID uuid : duelState.getPlayers()) {
                this.plugin.setLastDuelTrack(uuid, trackName);
                this.plugin.getDebugManager().logDuelSystem("§a[AUTO TT] Registrado lastDuelTrack=" + trackName + " para " + Bukkit.getOfflinePlayer(uuid).getName());
            }

            UUID winnerUUID = this.determineWinnerAfterTimeLimit(duelState);

            for(UUID uuid : duelState.getPlayers()) {
                PlayerDuelState playerState = (PlayerDuelState)this.playerStates.get(uuid);
                if (playerState != null && !playerState.isFinished()) {
                    playerState.setFinished(true);
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        this.ttda.toggleTimer(player, duelId, false);
                    }
                }
            }

            for(UUID uuid : duelState.getPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    String langCode = this.dm.getPlayerLanguage(uuid);
                    if (uuid.equals(winnerUUID)) {
                        player.sendMessage(" ");
                        player.sendMessage(this.plugin.getDirectTranslation("duel_victory", langCode));
                        player.sendMessage(" ");
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);
                    } else {
                        player.sendMessage(" ");
                        player.sendMessage(this.plugin.getDirectTranslation("duel_defeat", langCode));
                        player.sendMessage(" ");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                    }
                }
            }

            if (winnerUUID != null) {
                this.dm.setDuelStateWithWinner(duelId, "FINISHED", winnerUUID);
            } else {
                this.dm.setDuelState(duelId, "FINISHED");
            }

            for(UUID uuid : duelState.getPlayers()) {
                this.cleanupPlayer(uuid, duelId);
            }

        }
    }

    private UUID determineWinnerAfterTimeLimit(DuelState duelState) {
        if (duelState == null) {
            return null;
        } else {
            UUID winnerByFinish = duelState.getWinner();
            return winnerByFinish != null ? winnerByFinish : this.determineWinnerByProgress(duelState);
        }
    }

    private UUID determineWinnerByProgress(DuelState duelState) {
        if (duelState == null) {
            return null;
        } else {
            UUID winner = null;
            int maxLap = -1;
            long bestTime = Long.MAX_VALUE;

            for(UUID uuid : duelState.getPlayers()) {
                PlayerDuelState state = (PlayerDuelState)this.playerStates.get(uuid);
                if (state != null) {
                    int currentLap = state.getCurrentLap();
                    long totalTime = 0L;
                    if (state.getFirstLapStartTime() > 0L) {
                        totalTime = System.currentTimeMillis() - state.getFirstLapStartTime();
                    }

                    if (currentLap > maxLap) {
                        maxLap = currentLap;
                        bestTime = totalTime;
                        winner = uuid;
                    } else if (currentLap == maxLap && totalTime < bestTime) {
                        bestTime = totalTime;
                        winner = uuid;
                    }
                }
            }

            this.plugin.getDebugManager().logDuelSystem("[PROGRESS] Vencedor por progresso: " + String.valueOf(winner) + " (volta " + maxLap + ", tempo: " + bestTime + "ms)");
            return winner;
        }
    }

    private void releasePlayers(Player p1, Player p2) {
        for(Player p : Arrays.asList(p1, p2)) {
            SchedulerHelper.runTaskFor(this.plugin, p, player -> {
                Entity var6 = player.getVehicle();
                if (var6 instanceof Boat boat) {
                    Entity var7 = boat.getVehicle();
                    if (var7 instanceof ArmorStand stand) {
                        stand.remove();
                    }
                }
            });
        }

    }

    private void setupPlayerInGrid(Player player, Location baseLoc) {
        SchedulerHelper.teleportAsync(this.plugin, player, baseLoc);
        Location asLoc = baseLoc.clone().add((double)0.0F, (double)1.0F, (double)0.0F);
        ArmorStand stand = (ArmorStand)baseLoc.getWorld().spawnEntity(asLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setMarker(true);
        Boat boat = (Boat)baseLoc.getWorld().spawnEntity(baseLoc, EntityType.OAK_BOAT);
        stand.addPassenger(boat);
        boat.addPassenger(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5F, 1.5F);
    }

    public void onPlayerCrossStart(Player player, int duelId) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(player.getUniqueId());
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (state != null && duelState != null) {
            if (!duelState.isRaceStarted()) {
                this.plugin.getDebugManager().logDuelSystem("§e[START] Ignorado - corrida ainda não começou para " + player.getName());
            } else if (state.isFinished()) {
                this.plugin.getDebugManager().logDuelSystem("§e[DUEL] START ignorado - jogador já finalizou");
            } else {
                int currentLap = state.getCurrentLap();
                int totalLaps = duelState.getTotalLaps();
                this.plugin.getDebugManager().logDuelSystem("§b[START] " + player.getName() + " cruzou START - Volta atual: " + currentLap + "/" + totalLaps + " | raceStarted=" + duelState.isRaceStarted());
                if (currentLap == 0) {
                    long now = System.currentTimeMillis();
                    state.setCurrentLap(1);
                    state.setLastCrossTime(now);
                    state.setFirstLapStartTime(now);
                    this.ttda.toggleTimer(player, duelId, true);
                    this.scoreboardDuelsUtils.updatePlayerLap(player, 1);
                    this.dm.clearDuelCheckpointTimes(player.getUniqueId(), duelId);
                    this.plugin.getDebugManager().logDuelSystem("[DEBUG] Limpou checkpoints da volta anterior para " + player.getName());
                    this.plugin.getDebugManager().logDuelSystem("[LAP RESET] Ignorando lap reset na primeira cruz de START para " + player.getName() + " (evitar loop)");
                    this.ttda.resetLapTimer(player);
                    this.plugin.getDebugManager().logDuelSystem("[LAP TIMER] Timer resetado para " + player.getName());
                    String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
                    TitleHelper.sendThemedTitle(player, "", this.plugin.getTranslation("duel_lap_first_title", langCode, new String[0]), 0, 15, 5);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 2.0F);
                    DebugManager var33 = this.plugin.getDebugManager();
                    String var38 = player.getName();
                    var33.logDuelSystem(var38 + " iniciou volta 1 no duelo #" + duelId);
                } else {
                    if (duelState.isTimeTrialMode() && state.needsLapTimerReset()) {
                        this.ttda.resetLapTimer(player);
                        state.setNeedsLapTimerReset(false);
                        this.plugin.getDebugManager().logDuelSystem("[LAP TIMER] Timer resetado para " + player.getName() + " ao cruzar START (pós lap reset)");
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 2.0F);
                        return;
                    }

                    long timeSinceLastCross = System.currentTimeMillis() - state.getLastCrossTime();
                    if (timeSinceLastCross < 3000L) {
                        this.plugin.getDebugManager().logDuelSystem("§e[DUEL] START ignorado por debounce (< 3s)");
                        return;
                    }

                    if (currentLap >= totalLaps) {
                        this.plugin.getDebugManager().logDuelSystem("§e[DUEL] START ignorado - já atingiu o máximo de voltas (" + currentLap + "/" + totalLaps + ")");
                        this.plugin.getDebugManager().logDuelSystem("[DEBUG] Redirecionando para onPlayerCrossFinish (START = FINISH na última volta)");
                        this.onPlayerCrossFinish(player, duelId);
                        return;
                    }

                    if (duelState.isTimeLimitReached()) {
                        if (state.hasCompletedCurrentLapAfterTimeLimit()) {
                            this.plugin.getDebugManager().logDuelSystem("§e[TIME LIMIT] " + player.getName() + " já completou volta atual - ignorando cruz de START");
                            return;
                        }

                        int totalCheckpoints = this.dm.getCheckpointCount(duelState.getTrackName());
                        Map<Integer, Double> playerCheckpoints = this.dm.getDuelCheckpointTimes(player.getUniqueId(), duelId);
                        int checkpointsPassed = playerCheckpoints.size();
                        if (checkpointsPassed >= totalCheckpoints) {
                            long lapTimeMillis = System.currentTimeMillis() - state.getLastCrossTime();
                            double lapTime = (double)lapTimeMillis / (double)1000.0F;
                            if (lapTime > (double)0.0F) {
                                this.dm.saveDuelLapTime(player.getUniqueId(), player.getName(), duelId, currentLap, lapTime, duelState.getTrackName());
                                DebugManager var37 = this.plugin.getDebugManager();
                                String var42 = player.getName();
                                var37.logDuelSystem("§a[DUEL] " + var42 + " completou volta " + currentLap + " em " + String.format("%.3f", lapTime) + "s (após tempo limite)");
                                this.ttda.updateBestLapTime(player, lapTime);
                                duelState.updateBestLapTime(player.getUniqueId(), lapTime);
                                String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
                                String lapCompleteTitle = this.plugin.getDirectTranslation("duel_lap_completed_title", langCode);
                                String lapCompleteSubtitle = this.plugin.getTranslation("duel_lap_completed_subtitle", langCode, new String[]{"{time}", this.formatTime(lapTime)});
                                TitleHelper.sendThemedTitle(player, lapCompleteTitle, lapCompleteSubtitle, 10, 60, 20);
                                String separator = this.plugin.getDirectTranslation("duel_separator_line", langCode);
                                String lapComplete = this.plugin.getTranslation("duel_lap_complete_after_time_limit", langCode, new String[]{"{lap}", String.valueOf(currentLap)});
                                String lapTime_msg = this.plugin.getTranslation("duel_lap_time_after_time_limit", langCode, new String[]{"{time}", this.formatTime(lapTime)});
                                String waitingOthers = this.plugin.getDirectTranslation("duel_waiting_others_time_limit", langCode);
                                player.sendMessage(separator);
                                player.sendMessage(lapComplete);
                                player.sendMessage(lapTime_msg);
                                player.sendMessage(waitingOthers);
                                player.sendMessage(separator);
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
                            }

                            int newLap = currentLap + 1;
                            state.setCurrentLap(newLap);
                            state.setLastCrossTime(System.currentTimeMillis());
                            this.dm.clearDuelCheckpointTimes(player.getUniqueId(), duelId);
                            this.plugin.getDebugManager().logDuelSystem("§a[TIME LIMIT] " + player.getName() + " completou volta " + currentLap + " após tempo limite - Avançou para volta " + newLap + "/" + totalLaps);
                        } else {
                            this.plugin.getDebugManager().logDuelSystem("§e[TIME LIMIT] " + player.getName() + " cruzou START mas faltam checkpoints (" + checkpointsPassed + "/" + totalCheckpoints + ") - Cruz ignorada");
                        }

                        this.checkIfAllPlayersCompletedAfterTimeLimit(duelId);
                        return;
                    }

                    int totalCheckpoints = this.dm.getCheckpointCount(duelState.getTrackName());
                    Map<Integer, Double> playerCheckpoints = this.dm.getDuelCheckpointTimes(player.getUniqueId(), duelId);
                    int checkpointsPassed = playerCheckpoints.size();
                    this.plugin.getDebugManager().logDuelSystem("[CHECKPOINT VALIDATION] " + player.getName() + " - Checkpoints: " + checkpointsPassed + "/" + totalCheckpoints);
                    if (checkpointsPassed < totalCheckpoints) {
                        this.plugin.getDebugManager().logDuelSystem("§e[INVALID LAP] " + player.getName() + " NÃO passou por todos os checkpoints (" + checkpointsPassed + "/" + totalCheckpoints + ") - Cruz de START IGNORADA");
                        return;
                    }

                    long lapTimeMillis = System.currentTimeMillis() - state.getLastCrossTime();
                    double lapTime = (double)lapTimeMillis / (double)1000.0F;
                    if (lapTime > (double)0.0F) {
                        this.dm.saveDuelLapTime(player.getUniqueId(), player.getName(), duelId, currentLap, lapTime, duelState.getTrackName());
                        DebugManager var34 = this.plugin.getDebugManager();
                        String var39 = player.getName();
                        var34.logDuelSystem("§a[DUEL] " + var39 + " completou volta " + currentLap + " em " + String.format("%.3f", lapTime) + "s");
                        this.ttda.updateBestLapTime(player, lapTime);
                        if (duelState.isTimeTrialMode()) {
                            duelState.updateBestLapTime(player.getUniqueId(), lapTime);
                        }
                    }

                    int newLap = currentLap + 1;
                    state.setCurrentLap(newLap);
                    state.setLastCrossTime(System.currentTimeMillis());
                    this.scoreboardDuelsUtils.updatePlayerLap(player, newLap);
                    this.dm.clearDuelCheckpointTimes(player.getUniqueId(), duelId);
                    this.plugin.getDebugManager().logDuelSystem(player.getName() + " - checkpoints da volta limpos");
                    if (duelState.isTimeTrialMode()) {
                        Location spawnLoc = this.dm.getTrackSpawn(duelState.getTrackName());
                        this.plugin.getDebugManager().logDuelSystem("[TIME TRIAL] Tentando lap reset para " + player.getName() + " volta " + newLap + " - Spawn: " + (spawnLoc != null ? "OK" : "NULL"));

                        if (spawnLoc != null) {
                            // Capturamos newLap como final para o uso em lambdas aninhados
                            final int finalNewLap = newLap;

                            SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                                this.ttda.pauseLapTimer(player);
                                this.plugin.getDebugManager().logDuelSystem("[LAP RESET] Timer pausado para " + player.getName());

                                playersBeingLapReset.add(player.getUniqueId());

                                Boat.Type boatWoodType = Boat.Type.OAK;

                                if (player.getVehicle() instanceof Boat) {
                                    Boat oldBoat = (Boat) player.getVehicle();
                                    boatWoodType = oldBoat.getBoatType();
                                    oldBoat.eject();
                                    oldBoat.remove();
                                    this.plugin.getDebugManager().logDuelSystem("[LAP RESET] Removeu barco antigo de " + player.getName());
                                }

                                state.setNeedsLapTimerReset(true);

                                final Boat.Type finalWoodType = boatWoodType;

                                SchedulerHelper.teleportAsync(this.plugin, player, spawnLoc).thenAccept(success -> {
                                    if (Boolean.TRUE.equals(success)) {
                                        SchedulerHelper.runTaskFor(this.plugin, player, pl -> {
                                            Location boatLoc = spawnLoc.clone().add(0, 0.5, 0);

                                            Boat newBoat = (Boat) spawnLoc.getWorld().spawnEntity(boatLoc, EntityType.OAK_BOAT);
                                            newBoat.setBoatType(finalWoodType);
                                            newBoat.addPassenger(pl);

                                            plugin.getDebugManager().logDuelSystem("[LAP RESET] Spawnou novo barco (" + finalWoodType + ") para " +
                                                    pl.getName() + " na volta " + finalNewLap);

                                            if (duelState.isLonely()) {
                                                plugin.getLonelyController().updatePlayersVisibility((Player) pl);
                                            }

                                            SchedulerHelper.runTaskFor(plugin, pl, e -> {
                                                playersBeingLapReset.remove(e.getUniqueId());
                                            plugin.getDebugManager().logDuelSystem("[LAP RESET] Proteção de ejeção removida.");
                                        }, 3L);
                                    }, 2L);
                                } else {
                                    SchedulerHelper.runTaskFor(this.plugin, player, e ->
                                            playersBeingLapReset.remove(e.getUniqueId()), 3L);
                                }
                            });
                        });

                            String langCode4 = this.dm.getPlayerLanguage(player.getUniqueId());
                                TitleHelper.sendThemedTitle(player, "",
                                    this.plugin.getTranslation("duel_lap_prefix", langCode4) + newLap, 0, 15, 5);
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 2.0F);
                            this.plugin.getDebugManager().logDuelSystem(player.getName() + " iniciou volta " + newLap + "/" + totalLaps);
                        } else {
                            this.plugin.getDebugManager().logDuelSystem("§c[DUEL] Spawn location é NULL para " + duelState.getTrackName());
                            this.ttda.resetLapTimer(player);
                        }
                    } else {
                        this.ttda.resetLapTimer(player);
                        DebugManager var35 = this.plugin.getDebugManager();
                        String var40 = player.getName();
                        var35.logDuelSystem("[CORRIDA] " + var40 + " completou volta " + currentLap + " - Continuando sem lap reset");
                    }

                    String langCode5 = this.dm.getPlayerLanguage(player.getUniqueId());
                        TitleHelper.sendThemedTitle(player, "",
                            this.plugin.getTranslation("duel_lap_prefix", langCode5) + newLap, 0, 15, 5);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.5F);
                    this.plugin.getDebugManager().logDuelSystem(player.getName() + " iniciou volta " + newLap + "/" + totalLaps);
                }

                DebugManager var36 = this.plugin.getDebugManager();
                String var41 = player.getName();
                var36.logDuelSystemVerbose("§a[START CROSSED] " + var41 + " cruzou START no duelo #" + duelId);
            }
        } else {
            DebugManager var10000 = this.plugin.getDebugManager();
            String var10001 = player.getName();
            var10000.logDuelSystem("§c[START] Estado não encontrado para " + var10001 + " no duelo #" + duelId);
        }
    }

    public void onPlayerCrossFinish(Player player, int duelId) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(player.getUniqueId());
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (state != null && duelState != null) {
            if (duelState.isRaceStarted() && !state.isFinished()) {
                int currentLap = state.getCurrentLap();
                int totalLaps = duelState.getTotalLaps();
                if (currentLap == 0) {
                    this.plugin.getDebugManager().logDuelSystem("§e[FINISH] Ignorado - jogador ainda não cruzou START (currentLap=0)");
                } else {
                    this.plugin.getDebugManager().logDuelSystem("§6[FINISH DETECTADO] " + player.getName() + " cruzou FINISH - Volta: " + currentLap + "/" + totalLaps);
                    if (currentLap >= totalLaps) {
                        long timeSinceLastCross = System.currentTimeMillis() - state.getLastCrossTime();
                        if (timeSinceLastCross < 2000L) {
                            this.plugin.getDebugManager().logDuelSystem("§e[FINISH] Ignorado por debounce (< 2s)");
                            return;
                        }

                        this.plugin.getDebugManager().logDuelSystem("§a[FINISH] " + player.getName() + " COMPLETOU TODAS AS VOLTAS! Finalizando duelo...");
                        this.finishPlayerInDuel(player, duelId);
                    } else {
                        DebugManager var10 = this.plugin.getDebugManager();
                        String var12 = player.getName();
                        var10.logDuelSystem("§e[FINISH] " + var12 + " ainda precisa completar " + (totalLaps - currentLap) + " volta(s)");
                    }

                }
            } else {
                DebugManager var9 = this.plugin.getDebugManager();
                boolean var11 = duelState.isRaceStarted();
                var9.logDuelSystem("§e[FINISH] Ignorado - corrida=" + var11 + " finished=" + state.isFinished());
            }
        } else {
            DebugManager var10000 = this.plugin.getDebugManager();
            String var10001 = player.getName();
            var10000.logDuelSystem("§c[FINISH] Estado não encontrado para " + var10001 + " no duelo #" + duelId);
        }
    }

    private void finishPlayerInDuel(Player player, int duelId) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(player.getUniqueId());
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (state != null && duelState != null) {
            if (!state.isFinished()) {
                state.setFinished(true);
                long totalTimeMillis = System.currentTimeMillis() - state.getFirstLapStartTime();
                double totalTime = (double)totalTimeMillis / (double)1000.0F;
                this.dm.saveDuelFinalTime(player.getUniqueId(), player.getName(), duelId, totalTime, duelState.getTrackName());
                this.ttda.toggleTimer(player, duelId, false);
                duelState.addFinisher(player.getUniqueId());
                if (duelState.isTimeTrialMode()) {
                    if (!duelState.isTimeLimitReached()) {
                        String langCode2 = this.dm.getPlayerLanguage(player.getUniqueId());
                        TitleHelper.sendThemedTitle(player,
                            this.plugin.getTranslation("duel_finish_title", langCode2),
                            this.plugin.getTranslation("duel_finish_waiting", langCode2),
                            10, 70, 20);
                        String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
                        String totalTimeMessage = this.plugin.getTranslation("duel_total_time", langCode, new String[]{"{time}", this.formatTime(totalTime)});
                        String waitingMessage = this.plugin.getDirectTranslation("duel_waiting_others_finish", langCode);
                        player.sendMessage(totalTimeMessage);
                        player.sendMessage(waitingMessage);
                    }

                    DebugManager var10000 = this.plugin.getDebugManager();
                    String var10001 = player.getName();
                    var10000.logDuelSystem("§a[PLAYER FINISH] " + var10001 + " terminou no duelo #" + duelId + " - Tempo: " + String.format("%.3f", totalTime) + "s (posição será calculada ao final)");
                    if (duelState.isTimeLimitReached()) {
                        this.plugin.getDebugManager().logDuelSystem(player.getName() + " completou após tempo limite - aguardando outros jogadores");
                        this.checkIfAllPlayersCompletedAfterTimeLimit(duelId);
                    }
                } else {
                    int finishPosition = duelState.getFinishCount();
                    String langCode3 = this.dm.getPlayerLanguage(player.getUniqueId());
                        TitleHelper.sendThemedTitle(player,
                            this.plugin.getTranslation("duel_finish_title", langCode3),
                            this.plugin.getTranslation("duel_finish_position", langCode3, new String[]{"{position}", finishPosition + "º"}),
                            10, 70, 20);
                    String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
                    String totalTimeMessage = this.plugin.getTranslation("duel_total_time", langCode, new String[]{"{time}", this.formatTime(totalTime)});
                    player.sendMessage(totalTimeMessage);
                    DebugManager var22 = this.plugin.getDebugManager();
                    String var23 = player.getName();
                    var22.logDuelSystem("§a[RACE FINISH] " + var23 + " finalizou em " + finishPosition + "º lugar no duelo #" + duelId + " - Tempo: " + String.format("%.3f", totalTime) + "s");
                }

                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);
                if (duelState.isTimeLimitReached()) {
                    this.plugin.getDebugManager().logDuelSystem(player.getName() + " completou após tempo limite - aguardando outros jogadores");
                    String langCode = this.dm.getPlayerLanguage(player.getUniqueId());
                    String separator = this.plugin.getDirectTranslation("duel_separator_line", langCode);
                    String lapComplete = this.plugin.getDirectTranslation("duel_lap_complete_after_time_limit", langCode).replace("{lap}", "");
                    String waitingOthers = this.plugin.getDirectTranslation("duel_waiting_others_finish", langCode);
                    String completedTitle = this.plugin.getDirectTranslation("duel_lap_completed_title", langCode);
                    String waitingSubtitle = this.plugin.getDirectTranslation("duel_waiting_others", langCode);
                    player.sendMessage(separator);
                    player.sendMessage(lapComplete);
                    player.sendMessage("§e§l⏳ §f" + waitingOthers);
                    player.sendMessage(separator);
                    TitleHelper.sendThemedTitle(player, completedTitle, waitingSubtitle, 10, 100, 20);
                } else {
                    if (duelState.isTimeTrialMode()) {
                        boolean allFinished = duelState.getPlayers().stream().allMatch((uuid) -> {
                            PlayerDuelState pState = (PlayerDuelState)this.playerStates.get(uuid);
                            return pState != null && pState.isFinished();
                        });
                        if (allFinished) {
                            this.plugin.getDebugManager().logDuelSystem("[TIME TRIAL] Todos os jogadores finalizaram - encerrando duelo");
                            SchedulerHelper.runTaskLater(this.plugin, () -> this.endDuel(duelId), 20L);
                        } else {
                            this.plugin.getDebugManager().logDuelSystem("[TIME TRIAL] " + player.getName() + " finalizou - aguardando outros jogadores");
                        }
                    } else if (duelState.getFinishCount() == 1) {
                        SchedulerHelper.runTaskLater(this.plugin, () -> {
                            DuelState currentState = (DuelState)this.activeDuels.get(duelId);
                            if (currentState != null && !currentState.isTimeLimitReached()) {
                                this.endDuel(duelId);
                            } else {
                                this.plugin.getDebugManager().logDuelSystem("§e[DUEL] Cancelando endDuel() - tempo limite foi atingido durante a espera");
                            }

                        }, 40L);
                    }

                }
            }
        }
    }

    private String formatTime(double seconds) {
        long totalMillis = (long)(seconds * (double)1000.0F);
        long minutes = totalMillis / 60000L;
        long secs = totalMillis % 60000L / 1000L;
        long millis = totalMillis % 1000L;
        return minutes > 0L ? String.format("%d:%02d.%03d", minutes, secs, millis) : String.format("%d.%03d", secs, millis);
    }

    private void endDuel(int duelId) {
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (duelState == null) {
            this.plugin.getDebugManager().logDuelSystem("§e[DUEL] endDuel() chamado mas duelo #" + duelId + " já foi finalizado");
        } else {
            this.activeDuels.remove(duelId);
            this.plugin.getDebugManager().logDuelSystem("§a[DUEL] Finalizando duelo #" + duelId);
            String trackName = duelState.getTrackName();

            for(UUID uuid : duelState.getPlayers()) {
                this.plugin.setLastDuelTrack(uuid, trackName);
                this.plugin.getDebugManager().logDuelSystem("§a[AUTO TT] Registrado lastDuelTrack=" + trackName + " para " + Bukkit.getOfflinePlayer(uuid).getName());
            }

            UUID winnerUUID;
            if (duelState.isTimeTrialMode()) {
                winnerUUID = this.determineTimeTrialWinner(duelState);
                this.plugin.getDebugManager().logDuelSystem("[TIME TRIAL] Vencedor determinado por melhor tempo: " + String.valueOf(winnerUUID));
            } else {
                winnerUUID = duelState.getWinner();
                this.plugin.getDebugManager().logDuelSystem("[CORRIDA] Vencedor determinado por ordem de chegada: " + String.valueOf(winnerUUID));
            }

            if (winnerUUID != null) {
                this.dm.setDuelStateWithWinner(duelId, "FINISHED", winnerUUID);
                if (duelState.isTimeTrialMode()) {
                    for(UUID uuid : duelState.getPlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            int finalPosition = this.getTimeTrialPosition(duelState, uuid);
                            String langCode = this.dm.getPlayerLanguage(uuid);
                            if (uuid.equals(winnerUUID)) {
                                TitleHelper.sendThemedTitle(p,
                                    this.plugin.getTranslation("duel_victory_title", langCode),
                                    this.plugin.getTranslation("duel_finish_position", langCode, new String[]{"{position}", String.valueOf(finalPosition)}),
                                    10, 70, 20);
                                p.sendMessage(" ");
                                p.sendMessage(this.plugin.getDirectTranslation("duel_victory", langCode));
                                p.sendMessage(" ");
                            } else {
                                TitleHelper.sendThemedTitle(p,
                                    this.plugin.getTranslation("duel_defeat_title", langCode),
                                    this.plugin.getTranslation("duel_finish_position", langCode, new String[]{"{position}", String.valueOf(finalPosition)}),
                                    10, 70, 20);
                                p.sendMessage(" ");
                                p.sendMessage(this.plugin.getDirectTranslation("duel_defeat_second", langCode));
                                p.sendMessage(" ");
                            }
                        }
                    }
                } else {
                    Player winner = Bukkit.getPlayer(winnerUUID);
                    if (winner != null && winner.isOnline()) {
                        String langCode = this.dm.getPlayerLanguage(winnerUUID);
                        winner.sendMessage(" ");
                        winner.sendMessage(this.plugin.getDirectTranslation("duel_victory", langCode));
                        winner.sendMessage(" ");
                    }

                    for(UUID uuid : duelState.getPlayers()) {
                        if (!uuid.equals(winnerUUID)) {
                            Player loser = Bukkit.getPlayer(uuid);
                            if (loser != null && loser.isOnline()) {
                                PlayerDuelState loserState = (PlayerDuelState)this.playerStates.get(uuid);
                                String langCode = this.dm.getPlayerLanguage(uuid);
                                if (loserState != null && !loserState.isFinished()) {
                                    String defeatTitle = this.plugin.getDirectTranslation("duel_defeat_title", langCode);
                                    String defeatSubtitle = this.plugin.getDirectTranslation("duel_defeat_subtitle", langCode);
                                    TitleHelper.sendThemedTitle(loser, defeatTitle, defeatSubtitle, 10, 70, 20);
                                    loser.sendMessage(" ");
                                    loser.sendMessage(this.plugin.getDirectTranslation("duel_defeat", langCode));
                                    loser.sendMessage(" ");
                                    loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                                } else {
                                    loser.sendMessage(" ");
                                    loser.sendMessage(this.plugin.getDirectTranslation("duel_defeat_second", langCode));
                                    loser.sendMessage(" ");
                                }
                            }
                        }
                    }
                }
            } else {
                this.dm.setDuelState(duelId, "FINISHED");
            }

            for(UUID uuid : duelState.getPlayers()) {
                this.cleanupPlayer(uuid, duelId);
            }

        }
    }

    private UUID determineTimeTrialWinner(DuelState duelState) {
        UUID bestPlayer = null;
        double bestTime = Double.MAX_VALUE;

        for(UUID uuid : duelState.getPlayers()) {
            double playerBest = duelState.getBestLapTime(uuid);
            if (playerBest < bestTime) {
                bestTime = playerBest;
                bestPlayer = uuid;
            }
        }

        if (bestPlayer != null && bestTime != Double.MAX_VALUE) {
            return bestPlayer;
        } else {
            return duelState.getWinner();
        }
    }

    private void cleanupPlayer(UUID uuid, int duelId) {
        Player player = Bukkit.getPlayer(uuid);
        this.playerStates.remove(uuid);
        playersBeingLapReset.remove(uuid);
        if (player != null && player.isOnline()) {
            this.ttda.stopAll(player);
            this.scoreboardDuelsUtils.removeBoard(player);
            boolean dbLonely = this.dm.getLonelyModePlayer(uuid);
            this.plugin.getLonelyController().setLonelyMode(player, dbLonely);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }

    }

    private void endDuelByDisconnect(int duelId) {
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (duelState == null) {
            this.plugin.getDebugManager().logDuelSystem("§e[DUEL] endDuelByDisconnect() chamado mas duelo #" + duelId + " já foi finalizado");
        } else {
            this.activeDuels.remove(duelId);
            this.plugin.getDebugManager().logDuelSystem("§c[DUEL] Duelo #" + duelId + " cancelado por desconexão");
            this.dm.setDuelState(duelId, "CANCELLED");

            for(UUID uuid : duelState.getPlayers()) {
                this.cleanupPlayer(uuid, duelId);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    String langCode = this.dm.getPlayerLanguage(uuid);
                    player.sendMessage(this.plugin.getDirectTranslation("duel_cancelled_disconnect", langCode));
                }
            }

        }
    }

    public void removePlayerFromDuel(UUID playerUUID, int duelId) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(playerUUID);
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (state != null && duelState != null) {
            this.cleanupPlayer(playerUUID, duelId);
            duelState.removePlayer(playerUUID);
            if (duelState.getPlayerCount() == 1) {
                UUID winnerUUID = (UUID)duelState.getPlayers().iterator().next();
                this.dm.setDuelStateWithWinner(duelId, "FINISHED", winnerUUID);
                Player winner = Bukkit.getPlayer(winnerUUID);
                if (winner != null && winner.isOnline()) {
                    String langCode = this.dm.getPlayerLanguage(winnerUUID);
                    winner.sendMessage(" ");
                    winner.sendMessage(this.plugin.getDirectTranslation("duel_victory_forfeit", langCode));
                    winner.sendMessage(" ");
                    this.cleanupPlayer(winnerUUID, duelId);
                }

                this.activeDuels.remove(duelId);
            }

        }
    }

    private void cleanupSoloTimeTrial(Player player) {
        if (player != null && player.isOnline()) {
            UUID uuid = player.getUniqueId();
            this.plugin.getTimerUtils().stopTimer(player);
            this.dm.setTimeTrialEnabled(uuid, false);
            SchedulerHelper.runTask(this.plugin, () -> {
                if (player.isOnline()) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                }

            });
            this.plugin.getDebugManager().logDuelSystem("[CLEANUP] Limpou time trial solo de " + player.getName());
        }
    }

    public boolean isPlayerInDuel(UUID playerUUID) {
        return this.playerStates.containsKey(playerUUID);
    }

    public boolean isPlayerActivelyInDuel(UUID playerUUID) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(playerUUID);
        return state != null && !state.isFinished();
    }

    public boolean hasPlayerCompletedCurrentLapAfterTimeLimit(UUID playerUUID, int duelId) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(playerUUID);
        return state != null && state.getDuelId() == duelId ? state.hasCompletedCurrentLapAfterTimeLimit() : false;
    }

    public int getPlayerDuelId(UUID playerUUID) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(playerUUID);
        return state != null ? state.getDuelId() : -1;
    }

    public int getPlayerCurrentLap(UUID uuid, int duelId) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(uuid);
        return state != null && state.getDuelId() == duelId ? state.getCurrentLap() : 0;
    }

    public int getPlayerCurrentLap(Player player, int duelId) {
        return this.getPlayerCurrentLap(player.getUniqueId(), duelId);
    }

    public int getLapWhenTimeLimitReached(UUID uuid, int duelId) {
        PlayerDuelState state = (PlayerDuelState)this.playerStates.get(uuid);
        return state != null && state.getDuelId() == duelId ? state.getLapWhenTimeLimitReached() : -1;
    }

    public boolean isTimeLimitReached(int duelId) {
        DuelState state = (DuelState)this.activeDuels.get(duelId);
        return state == null ? false : state.isTimeLimitReached();
    }

    public int getDuelTotalLaps(int duelId) {
        DuelState state = (DuelState)this.activeDuels.get(duelId);
        return state == null ? 0 : state.getTotalLaps();
    }

    public String getDuelTrackName(int duelId) {
        DuelState state = (DuelState)this.activeDuels.get(duelId);
        return state != null ? state.getTrackName() : null;
    }

    public static boolean isPlayerBeingLapReset(UUID playerUUID) {
        return playersBeingLapReset.contains(playerUUID);
    }

    public int getPlayerPosition(int duelId, UUID playerUUID) {
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (duelState == null) {
            return 1;
        } else {
            PlayerDuelState playerState = (PlayerDuelState)this.playerStates.get(playerUUID);
            if (playerState == null) {
                return duelState.getPlayerCount();
            } else if (!duelState.isRaceStarted()) {
                return 1;
            } else {
                return duelState.isTimeTrialMode() ? this.getTimeTrialPosition(duelState, playerUUID) : this.getRacePosition(duelState, playerUUID, playerState);
            }
        }
    }

    private int getTimeTrialPosition(DuelState duelState, UUID playerUUID) {
        double playerBestTime = duelState.getBestLapTime(playerUUID);
        int position = 1;
        if (playerBestTime == Double.MAX_VALUE) {
            return duelState.getPlayerCount();
        } else {
            for(UUID otherUUID : duelState.getPlayers()) {
                if (!otherUUID.equals(playerUUID)) {
                    double otherBestTime = duelState.getBestLapTime(otherUUID);
                    if (otherBestTime < playerBestTime) {
                        ++position;
                    }
                }
            }

            PlayerDuelState playerState = (PlayerDuelState)this.playerStates.get(playerUUID);
            if (playerState != null) {
                int lastPosition = playerState.getLastKnownPosition();
                if (lastPosition != position) {
                    playerState.setLastKnownPosition(position);
                    DebugManager var10000 = this.plugin.getDebugManager();
                    String var10001 = String.valueOf(playerUUID);
                    var10000.logDuelSystem("[TIME TRIAL] " + var10001 + " mudou posição: " + lastPosition + " -> " + position + " (melhor tempo: " + String.format("%.3f", playerBestTime) + "s)");
                }
            }

            return position;
        }
    }

    private int getRacePosition(DuelState duelState, UUID playerUUID, PlayerDuelState playerState) {
        int position = 1;
        int playerLap = playerState.getCurrentLap();
        if (playerLap == 0) {
            DebugManager var23 = this.plugin.getDebugManager();
            String var26 = String.valueOf(playerUUID);
            var23.logDuelSystemVerbose(var26 + " posição = " + duelState.getPlayerCount() + " (não começou)");
            return duelState.getPlayerCount();
        } else {
            Map<Integer, Double> playerCheckpoints = this.dm.getDuelCheckpointTimes(playerUUID, duelState.getDuelId());
            int playerCheckpointCount = playerCheckpoints.size();
            double playerLastCheckpointTime = (double)0.0F;
            if (!playerCheckpoints.isEmpty()) {
                int lastCheckpointId = (Integer)playerCheckpoints.keySet().stream().max(Integer::compareTo).orElse(0);
                playerLastCheckpointTime = (Double)playerCheckpoints.get(lastCheckpointId);
            }

            for(UUID otherUUID : duelState.getPlayers()) {
                if (!otherUUID.equals(playerUUID)) {
                    PlayerDuelState otherState = (PlayerDuelState)this.playerStates.get(otherUUID);
                    if (otherState != null) {
                        int otherLap = otherState.getCurrentLap();
                        if (otherLap != 0) {
                            if (otherLap > playerLap) {
                                ++position;
                                DebugManager var10000 = this.plugin.getDebugManager();
                                String var10001 = String.valueOf(otherUUID);
                                var10000.logDuelSystemVerbose(var10001 + " à frente de " + String.valueOf(playerUUID) + " (volta " + otherLap + " vs " + playerLap + ")");
                            } else if (otherLap == playerLap) {
                                Map<Integer, Double> otherCheckpoints = this.dm.getDuelCheckpointTimes(otherUUID, duelState.getDuelId());
                                int otherCheckpointCount = otherCheckpoints.size();
                                if (otherCheckpointCount > playerCheckpointCount) {
                                    ++position;
                                    DebugManager var21 = this.plugin.getDebugManager();
                                    String var24 = String.valueOf(otherUUID);
                                    var21.logDuelSystemVerbose(var24 + " à frente de " + String.valueOf(playerUUID) + " (mesma volta, checkpoints: " + otherCheckpointCount + " vs " + playerCheckpointCount + ")");
                                } else if (otherCheckpointCount == playerCheckpointCount && otherCheckpointCount > 0) {
                                    int otherLastCheckpointId = (Integer)otherCheckpoints.keySet().stream().max(Integer::compareTo).orElse(0);
                                    double otherLastCheckpointTime = (Double)otherCheckpoints.get(otherLastCheckpointId);
                                    if (otherLastCheckpointTime < playerLastCheckpointTime) {
                                        ++position;
                                        DebugManager var22 = this.plugin.getDebugManager();
                                        String var25 = String.valueOf(otherUUID);
                                        var22.logDuelSystemVerbose(var25 + " à frente de " + String.valueOf(playerUUID) + " (mesmo checkpoint " + otherCheckpointCount + ", tempo: " + String.format("%.3f", otherLastCheckpointTime) + "s vs " + String.format("%.3f", playerLastCheckpointTime) + "s)");
                                    }
                                }
                            }
                        }
                    }
                }
            }

            int lastPosition = playerState.getLastKnownPosition();
            if (lastPosition != position) {
                playerState.setLastKnownPosition(position);
                this.plugin.getDebugManager().logDuelSystem("[CORRIDA] " + String.valueOf(playerUUID) + " mudou posição: " + lastPosition + " -> " + position + " (volta " + playerLap + ")");
            }

            return position;
        }
    }

    public int getTimeRemaining(int duelId) {
        DuelState duelState = (DuelState)this.activeDuels.get(duelId);
        if (duelState == null) {
            return -1;
        } else if (duelState.getTimeLimit() <= 0) {
            return -1;
        } else if (!duelState.isRaceStarted()) {
            return duelState.getTimeLimit();
        } else {
            long elapsedMillis = System.currentTimeMillis() - duelState.getRaceStartTime();
            long elapsedSeconds = elapsedMillis / 1000L;
            long totalSeconds = (long)duelState.getTimeLimit();
            long remainingSeconds = totalSeconds - elapsedSeconds;
            return (int)Math.max(0L, remainingSeconds);
        }
    }

    private EntityType getEntityTypeFromBoatType(Boat.Type boatType) {
        switch (boatType) {
            case OAK -> {
                return EntityType.OAK_BOAT;
            }
            case SPRUCE -> {
                return EntityType.SPRUCE_BOAT;
            }
            case BIRCH -> {
                return EntityType.BIRCH_BOAT;
            }
            case JUNGLE -> {
                return EntityType.JUNGLE_BOAT;
            }
            case ACACIA -> {
                return EntityType.ACACIA_BOAT;
            }
            case DARK_OAK -> {
                return EntityType.DARK_OAK_BOAT;
            }
            case MANGROVE -> {
                return EntityType.MANGROVE_BOAT;
            }
            case CHERRY -> {
                return EntityType.CHERRY_BOAT;
            }
            case BAMBOO -> {
                return EntityType.BAMBOO_RAFT;
            }
            default -> {
                return EntityType.OAK_BOAT;
            }
        }
    }

    public static class DuelState {
        private final int duelId;
        private final String trackName;
        private final int totalLaps;
        private final int timeLimit;
        private final boolean lonely;
        private final boolean timeTrialMode;
        private final Set<UUID> players;
        private final List<UUID> finishOrder;
        private boolean raceStarted;
        private long raceStartTime;
        private boolean timeLimitReached;
        private final Map<UUID, Double> bestLapTimes;

        public DuelState(int duelId, String trackName, int totalLaps, int timeLimit, boolean lonely, boolean timeTrialMode) {
            this.duelId = duelId;
            this.trackName = trackName;
            this.totalLaps = totalLaps;
            this.timeLimit = timeLimit;
            this.lonely = lonely;
            this.timeTrialMode = timeTrialMode;
            this.players = new HashSet();
            this.finishOrder = new ArrayList();
            this.raceStarted = false;
            this.raceStartTime = 0L;
            this.timeLimitReached = false;
            this.bestLapTimes = new HashMap();
        }

        public void addPlayer(UUID uuid) {
            this.players.add(uuid);
        }

        public void removePlayer(UUID uuid) {
            this.players.remove(uuid);
        }

        public Set<UUID> getPlayers() {
            return this.players;
        }

        public int getPlayerCount() {
            return this.players.size();
        }

        public int getDuelId() {
            return this.duelId;
        }

        public int getTotalLaps() {
            return this.totalLaps;
        }

        public int getTimeLimit() {
            return this.timeLimit;
        }

        public String getTrackName() {
            return this.trackName;
        }

        public boolean isLonely() {
            return this.lonely;
        }

        public boolean isTimeTrialMode() {
            return this.timeTrialMode;
        }

        public boolean isRaceMode() {
            return !this.timeTrialMode;
        }

        public boolean isRaceStarted() {
            return this.raceStarted;
        }

        public void setRaceStarted(boolean started) {
            this.raceStarted = started;
        }

        public long getRaceStartTime() {
            return this.raceStartTime;
        }

        public void setRaceStartTime(long time) {
            this.raceStartTime = time;
        }

        public boolean isTimeLimitReached() {
            return this.timeLimitReached;
        }

        public void setTimeLimitReached(boolean reached) {
            this.timeLimitReached = reached;
        }

        public void updateBestLapTime(UUID uuid, double lapTime) {
            Double current = (Double)this.bestLapTimes.get(uuid);
            if (current == null || lapTime < current) {
                this.bestLapTimes.put(uuid, lapTime);
            }

        }

        public double getBestLapTime(UUID uuid) {
            return (Double)this.bestLapTimes.getOrDefault(uuid, Double.MAX_VALUE);
        }

        public Map<UUID, Double> getAllBestLapTimes() {
            return new HashMap(this.bestLapTimes);
        }

        public int getFinishCount() {
            return this.finishOrder.size();
        }

        public UUID getWinner() {
            return this.finishOrder.isEmpty() ? null : (UUID)this.finishOrder.get(0);
        }

        public void addFinisher(UUID uuid) {
            if (!this.finishOrder.contains(uuid)) {
                this.finishOrder.add(uuid);
            }

        }
    }

    private static class PlayerDuelState {
        private final UUID playerUUID;
        private final int duelId;
        private int currentLap;
        private boolean finished;
        private long lastCrossTime;
        private long firstLapStartTime;
        private int lastKnownPosition;
        private boolean needsLapTimerReset;
        private boolean completedCurrentLapAfterTimeLimit;
        private int lapWhenTimeLimitReached = -1;

        public PlayerDuelState(UUID playerUUID, int duelId) {
            this.playerUUID = playerUUID;
            this.duelId = duelId;
            this.currentLap = 0;
            this.finished = false;
            this.lastCrossTime = 0L;
            this.firstLapStartTime = 0L;
            this.lastKnownPosition = 1;
            this.needsLapTimerReset = false;
            this.completedCurrentLapAfterTimeLimit = false;
        }

        public int getDuelId() {
            return this.duelId;
        }

        public int getCurrentLap() {
            return this.currentLap;
        }

        public void setCurrentLap(int lap) {
            this.currentLap = lap;
        }

        public boolean isFinished() {
            return this.finished;
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }

        public long getLastCrossTime() {
            return this.lastCrossTime;
        }

        public void setLastCrossTime(long time) {
            this.lastCrossTime = time;
        }

        public long getFirstLapStartTime() {
            return this.firstLapStartTime;
        }

        public void setFirstLapStartTime(long time) {
            this.firstLapStartTime = time;
        }

        public int getLastKnownPosition() {
            return this.lastKnownPosition;
        }

        public void setLastKnownPosition(int position) {
            this.lastKnownPosition = position;
        }

        public boolean needsLapTimerReset() {
            return this.needsLapTimerReset;
        }

        public void setNeedsLapTimerReset(boolean needs) {
            this.needsLapTimerReset = needs;
        }

        public boolean hasCompletedCurrentLapAfterTimeLimit() {
            return this.completedCurrentLapAfterTimeLimit;
        }

        public void setCompletedCurrentLapAfterTimeLimit(boolean completed) {
            this.completedCurrentLapAfterTimeLimit = completed;
        }

        public int getLapWhenTimeLimitReached() {
            return this.lapWhenTimeLimitReached;
        }

        public void setLapWhenTimeLimitReached(int lap) {
            this.lapWhenTimeLimitReached = lap;
        }
    }
}
