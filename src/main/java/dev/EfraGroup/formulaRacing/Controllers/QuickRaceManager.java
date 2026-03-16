//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.EventsDatabaseManager;
import dev.EfraGroup.formulaRacing.Event.EventAnnouncements;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.ClickableMessageUtil;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class QuickRaceManager {
    private final FormulaRacing plugin;
    private final RaceEventManager eventManager;
    private final DatabaseManager database;
    private final EventsDatabaseManager eventsDb;
    private Events currentQuickRace;
    private Rounds currentRound;
    private Heats currentHeat;
    private volatile boolean creating = false;
    private BukkitTask lobbyTimerTask;
    private int lobbyTimerSeconds = 60;
    private static final int TIMER_LONG = 60;
    private static final int TIMER_SHORT = 15;
    private static final int PLAYERS_FOR_FAST_START = 4;

    public QuickRaceManager(FormulaRacing plugin, RaceEventManager eventManager, DatabaseManager database) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.database = database;
        this.eventsDb = new EventsDatabaseManager(database, plugin);
    }

    public boolean createQuickRace(Player creator, String trackName, int laps, int pits) {
        // 1. Limpeza de estados antigos
        if (currentHeat != null && currentHeat.getHeatState() == HeatState.FINISHED) {
            deleteQuickRace();
        }

        // 2. Verificações de segurança (Early Returns)
        if (currentQuickRace != null) {
            plugin.sendMessage(creator, "quickrace_already_active", "{track}", currentQuickRace.getTrackNameWS());
            plugin.sendMessage(creator, "quickrace_end_instruction");
            return false;
        }

        if (creating) {
            plugin.sendMessage(creator, "quickrace_already_creating");
            creator.sendMessage(ChatColor.RED + "Uma Quick Race já está sendo criada. Aguarde...");
            return false;
        }

        DatabaseManager.TrackData trackData = database.getTrackData(trackName);
        if (trackData == null) {
            plugin.sendMessage(creator, "track_not_found", "{track}", trackName);
            return false;
        }

        // 3. Preparação dos dados da pista
        creating = true;
        String finalTrackName = trackData.getTrackName();
        String trackNameWS = finalTrackName.replaceAll("\\s+", "").toLowerCase();

        // Validação de Circuito vs Point-to-Point
        if (!database.isCircuit(trackNameWS) && (laps > 1 || pits > 0)) {
            plugin.getDebugManager().logRaceSystem("[QuickRace] Pista " + finalTrackName + " não é circuito. Ajustando parâmetros.");
            creator.sendMessage("§eAjustando para 1 volta e 0 pits (pista Point-to-Point).");
            laps = 1;
            pits = 0;
        }

        // Normalização de valores
        int finalLaps = Math.max(1, laps);
        int finalPits = Math.min(Math.max(0, pits), finalLaps - 1);
        String eventName = "QuickRace_" + System.currentTimeMillis();

        // 1. Recebemos como Object, já que é o que o método fornece
        java.util.concurrent.CompletableFuture<Object> future = eventManager.createQuickRace(
                creator.getUniqueId(), eventName, finalTrackName, finalLaps, finalPits
        );

// 2. Processamos o resultado fazendo o cast manual
        future.thenAccept(obj -> {
            creating = false;

            // Verificamos se o retorno é nulo ou se não é do tipo Events
            if (obj == null || !(obj instanceof Events)) {
                plugin.sendMessage(creator, "quickrace_create_error");
                return;
            }

            // Cast manual seguro
            Events event = (Events) obj;
            this.currentQuickRace = event;

            // 3. Atribuição de referências (Rounds e Heats)
            // Buscamos o primeiro round do cronograma
            this.currentRound = event.getEventSchedule().getRounds().values().stream()
                    .findFirst()
                    .map(r -> (Rounds) r) // Cast para garantir o tipo correto
                    .orElse(null);

            if (currentRound == null) {
                plugin.sendMessage(creator, "quickrace_round_error");
                deleteQuickRace();
                return;
            }

            // Buscamos a primeira bateria (Heat) do round
            this.currentHeat = currentRound.getHeats().values().stream()
                    .findFirst()
                    .map(h -> (Heats) h) // Cast para garantir o tipo correto
                    .orElse(null);

            if (currentHeat == null) {
                plugin.sendMessage(creator, "quickrace_heat_error");
                deleteQuickRace();
                return;
            }

            // 4. Finalização e Logs
            database.setPlayerSelectedEvent(creator.getUniqueId(), currentQuickRace);
            startLobbyTimer();

            plugin.getDebugManager().logRaceSystem("Quick Race criada por " + creator.getName() + " na pista " + finalTrackName);

            // Notificar jogadores online usando uma lista mutável
            sendJoinMessage(new java.util.ArrayList<>(plugin.getServer().getOnlinePlayers()));
        });

        return true;
    }

    public boolean addPlayer(Player player) {
        if (this.currentHeat == null) {
            this.plugin.sendMessage(player, "quickrace_none_active", new String[0]);
            return false;
        } else {
            HeatState state = this.currentHeat.getHeatState();
            if (state != HeatState.RACING && state != HeatState.STARTING) {
                if (state == HeatState.FINISHED) {
                    this.plugin.sendMessage(player, "quickrace_already_started", new String[0]);
                    return false;
                } else if (this.currentHeat.getDriver(player.getUniqueId()) != null) {
                    this.plugin.sendMessage(player, "quickrace_already_in", new String[0]);
                    return false;
                } else if (this.currentHeat.getDriverCount() >= this.currentHeat.getMaxDrivers()) {
                    this.plugin.sendMessage(player, "quickrace_full", new String[]{"{max}", String.valueOf(this.currentHeat.getMaxDrivers())});
                    return false;
                } else {
                    if (this.plugin.getTimeTrialController() != null && this.plugin.getTimeTrialController().hasActiveSession(player)) {
                        this.plugin.getTimerUtils().stopTimer(player);
                        this.plugin.getTimeTrialController().endSession(player);
                        if (this.plugin.getPacketSender() != null) {
                            this.plugin.getPacketSender().resetBoatUtilsToVanilla(player);
                        }

                        this.plugin.getDebugManager().logRaceSystem("[QuickRace] Time Trial interrompido para " + player.getName() + " entrar na corrida.");
                    }

                    if (this.plugin.getPitStopManager() != null) {
                        this.plugin.getPitStopManager().clearPitStopState(player.getUniqueId());
                    }

                    this.plugin.checkAndWarnOBU(player, this.currentHeat.getTrackNameWS());
                    if (player.isInsideVehicle() && player.getVehicle() != null) {
                        Entity vehicle = player.getVehicle();
                        vehicle.eject();
                        vehicle.remove();
                    }

                    int startPosition = this.currentHeat.getDriverCount() + 1;
                    boolean added = this.currentHeat.addDriver(player.getUniqueId(), startPosition);
                    if (added) {
                        if (this.currentHeat.getHeatState() == HeatState.SETUP) {
                            this.currentHeat.loadHeat();
                        } else if (this.currentHeat.getHeatState() == HeatState.LOADED) {
                            this.currentHeat.getGridManager().teleportDriver(this.currentHeat.getDriver(player.getUniqueId()));
                            Player bPlayer = Bukkit.getPlayer(player.getUniqueId());
                            if (bPlayer != null && bPlayer.isOnline()) {
                                this.currentHeat.getScoreboardManager().addPlayer(bPlayer, this.currentHeat);
                                this.currentHeat.getActionBarManager().addPlayer(bPlayer, this.currentHeat);
                            }
                        }

                        this.checkFastStart();
                        this.plugin.sendMessage(player, "quickrace_joined", new String[0]);
                        this.database.setPlayerSelectedEvent(player.getUniqueId(), this.currentQuickRace);
                        this.plugin.sendMessage(player, "quickrace_info_track", new String[]{"{track}", this.currentQuickRace.getTrackNameWS()});
                        this.plugin.sendMessage(player, "quickrace_info_laps_pits", new String[]{"{laps}", String.valueOf(this.currentHeat.getTotalLaps()), "{pits}", String.valueOf(this.currentHeat.getTotalPits())});
                        this.plugin.sendMessage(player, "quickrace_info_drivers", new String[]{"{current}", String.valueOf(this.currentHeat.getDriverCount()), "{max}", String.valueOf(this.currentHeat.getMaxDrivers())});
                        this.plugin.getTranslationUtil().sendTranslated(player, "quickrace_leave_hint", new String[0]);
                        if (this.lobbyTimerSeconds > 0) {
                            this.plugin.sendMessage(player, "quickrace_timer_status", new String[]{"{time}", String.valueOf(this.lobbyTimerSeconds)});
                        }

                        EventAnnouncements announcements = this.currentQuickRace != null ? this.currentQuickRace.getAnnouncements() : this.plugin.getEventAnnouncements();
                        announcements.broadcastDriverJoin(this.currentHeat, player.getName(), this.currentHeat.getDriverCount(), this.currentHeat.getMaxDrivers());
                        return true;
                    } else {
                        this.plugin.sendMessage(player, "quickrace_join_error", new String[0]);
                        return false;
                    }
                }
            } else {
                if (this.plugin.getSpectatorManager() != null) {
                    boolean added = this.plugin.getSpectatorManager().addSpectator(player, this.currentQuickRace);
                    if (added) {
                        player.sendMessage("§eA corrida já começou! Você entrou como §6ESPECTADOR§e.");
                        player.sendTitle("§6ESPECTADOR", "§7Acompanhando a corrida...", 10, 60, 20);
                        return false;
                    }
                }

                this.plugin.sendMessage(player, "quickrace_already_started", new String[0]);
                return false;
            }
        }
    }

    public boolean removePlayer(Player player) {
        if (this.currentHeat == null) {
            this.plugin.sendMessage(player, "quickrace_none_active", new String[0]);
            return false;
        } else {
            Driver driver = this.currentHeat.getDriver(player.getUniqueId());
            if (driver == null) {
                this.plugin.sendMessage(player, "quickrace_not_in", new String[0]);
                return false;
            } else if (this.currentHeat.getHeatState() == HeatState.RACING) {
                driver.setDnf(true);
                EventAnnouncements announcements = this.currentQuickRace != null ? this.currentQuickRace.getAnnouncements() : this.plugin.getEventAnnouncements();
                announcements.broadcastDNF(this.currentHeat, driver, "Left race");
                if (this.plugin.getRaceActionBarManager() != null) {
                    this.plugin.getRaceActionBarManager().removePlayer(player);
                }

                if (this.plugin.getRaceScoreboardManager() != null) {
                    this.plugin.getRaceScoreboardManager().removePlayer(player);
                }

                if (this.plugin.getPacketSender() != null) {
                    this.plugin.getPacketSender().resetBoatUtilsToVanilla(player);
                }

                if (this.plugin.getPacketSender() != null) {
                    boolean dbLonely = this.plugin.getDatabaseManager().getLonelyModePlayer(player.getUniqueId());
                    this.plugin.getLonelyController().setLonelyMode(player, dbLonely);
                }

                Entity var5 = player.getVehicle();
                if (var5 instanceof Boat) {
                    Boat boat = (Boat)var5;
                    boat.remove();
                }

                Location respawn = player.getRespawnLocation();
                if (respawn == null) {
                    respawn = player.getWorld().getSpawnLocation();
                }

                player.teleport(respawn);
                this.plugin.sendMessage(player, "quickrace_left_dnf", new String[0]);
                return true;
            } else {
                boolean removed = this.currentHeat.removeDriver(player.getUniqueId());
                if (removed) {
                    this.currentHeat.resetHeat();
                    this.currentHeat.loadHeat();
                    if (this.plugin.getPacketSender() != null) {
                        this.plugin.getPacketSender().resetBoatUtilsToVanilla(player);
                    }

                    if (this.plugin.getPacketSender() != null) {
                        boolean dbLonely = this.plugin.getDatabaseManager().getLonelyModePlayer(player.getUniqueId());
                        this.plugin.getLonelyController().setLonelyMode(player, dbLonely);
                    }

                    this.plugin.sendMessage(player, "quickrace_left", new String[0]);
                    EventAnnouncements announcements = this.currentQuickRace != null ? this.currentQuickRace.getAnnouncements() : this.plugin.getEventAnnouncements();
                    announcements.broadcastDriverLeave(this.currentHeat, player.getName(), this.currentHeat.getDriverCount(), this.currentHeat.getMaxDrivers());
                    if (this.currentHeat.getDriverCount() == 0) {
                        this.lobbyTimerSeconds = 60;
                    }

                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    public boolean startQuickRace(Player starter) {
        if (this.currentHeat == null) {
            if (starter != null) {
                this.plugin.sendMessage(starter, "quickrace_none_active", new String[0]);
            }

            return false;
        } else if (this.currentHeat.getHeatState() != HeatState.LOADED) {
            if (starter != null) {
                this.plugin.sendMessage(starter, "quickrace_already_started", new String[0]);
            }

            return false;
        } else if (this.currentHeat.getDriverCount() < 1) {
            if (starter != null) {
                this.plugin.sendMessage(starter, "quickrace_not_enough_drivers", new String[0]);
            }

            return false;
        } else {
            this.currentHeat.startCountdown();
            this.stopLobbyTimer();
            if (this.currentHeat.getHeatState() != HeatState.RACING && this.currentHeat.getHeatState() != HeatState.STARTING) {
                if (starter != null) {
                    this.plugin.sendMessage(starter, "quickrace_start_error", new String[0]);
                }

                return false;
            } else {
                EventAnnouncements announcements = this.currentQuickRace != null ? this.currentQuickRace.getAnnouncements() : this.plugin.getEventAnnouncements();
                if (this.currentQuickRace != null) {
                    announcements.broadcastEventStart(this.currentQuickRace);
                } else {
                    announcements.broadcastRaceStarting(this.currentHeat);
                }

                return true;
            }
        }
    }

    public boolean endQuickRace(Player ender) {
        this.stopLobbyTimer();
        if (this.currentQuickRace == null) {
            this.plugin.sendMessage(ender, "quickrace_none_active", new String[0]);
            return false;
        } else {
            if (this.currentHeat != null) {
                for(UUID uuid : this.currentHeat.getDrivers().keySet()) {
                    Player p = this.plugin.getServer().getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        this.plugin.getAPI().releaseBoat(p);
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                        if (this.plugin.getPacketSender() != null) {
                            this.plugin.getPacketSender().resetBoatUtilsToVanilla(p);
                        }

                        Location respawnLoc = p.getRespawnLocation();
                        if (respawnLoc == null) {
                            respawnLoc = ((World)this.plugin.getServer().getWorlds().get(0)).getSpawnLocation();
                        }

                        p.teleport(respawnLoc);
                    }
                }

                HeatState state = this.currentHeat.getHeatState();
                if (state == HeatState.RACING) {
                    EventAnnouncements announcements = this.currentQuickRace != null ? this.currentQuickRace.getAnnouncements() : this.plugin.getEventAnnouncements();
                    if (this.currentQuickRace != null) {
                        announcements.broadcastEventFinish(this.currentQuickRace);
                    } else {
                        announcements.broadcastAdminFinish(this.currentHeat);
                    }

                    this.currentHeat.finishHeat();
                } else if (state == HeatState.LOADED || state == HeatState.STARTING) {
                    this.currentHeat.resetHeat();
                }
            }

            this.deleteQuickRace();
            this.plugin.sendMessage(ender, "quickrace_finished", new String[0]);
            return true;
        }
    }

    public void sendJoinMessage(List<Player> players) {
        if (this.currentHeat != null && this.currentQuickRace != null) {
            String trackName = this.currentQuickRace.getTrackNameWS();
            int laps = this.currentHeat.getTotalLaps();
            int pits = this.currentHeat.getTotalPits();
            int currentDrivers = this.currentHeat.getDriverCount();
            int maxDrivers = this.currentHeat.getMaxDrivers();

            for(Player player : players) {
                if (this.currentHeat.getDriver(player.getUniqueId()) == null) {
                    ClickableMessageUtil.sendQuickRaceInvite(player, trackName, laps, pits, currentDrivers, maxDrivers);
                }
            }

        }
    }

    private void deleteQuickRace() {
        if (this.currentQuickRace != null) {
            this.plugin.getRaceEventManager().unloadEvent(this.currentQuickRace.getId());
            this.eventsDb.deleteEvent(this.currentQuickRace.getId());
            this.plugin.getDebugManager().logRaceSystem("Quick Race removida: " + this.currentQuickRace.getDisplayName());
        }

        this.currentQuickRace = null;
        this.currentRound = null;
        this.currentHeat = null;
        this.stopLobbyTimer();
    }

    private void startLobbyTimer() {
        this.stopLobbyTimer();
        this.lobbyTimerSeconds = 60;
        this.lobbyTimerTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tickLobby, 20L, 20L);
    }

    private void stopLobbyTimer() {
        if (this.lobbyTimerTask != null) {
            this.lobbyTimerTask.cancel();
            this.lobbyTimerTask = null;
        }

    }

    private void checkFastStart() {
        if (this.currentHeat != null) {
            if (this.currentHeat.getDriverCount() >= 4 && this.lobbyTimerSeconds > 15) {
                this.lobbyTimerSeconds = 15;
                String msg = "§e§l⚠ Jogadores suficientes! A corrida iniciará em 15 segundos!";

                for(UUID uuid : this.currentHeat.getDrivers().keySet()) {
                    Player p = this.plugin.getServer().getPlayer(uuid);
                    if (p != null) {
                        p.sendMessage(msg);
                    }
                }
            }

        }
    }

    private void tickLobby() {
        if (this.currentQuickRace != null && this.currentHeat != null) {
            if (this.currentHeat.getDriverCount() == 0) {
                this.lobbyTimerSeconds = 60;
            } else {
                HeatState state = this.currentHeat.getHeatState();
                if (state != HeatState.SETUP && state != HeatState.LOADED) {
                    this.stopLobbyTimer();
                } else {
                    --this.lobbyTimerSeconds;
                    if (this.lobbyTimerSeconds == 30 || this.lobbyTimerSeconds == 15 || this.lobbyTimerSeconds <= 5 && this.lobbyTimerSeconds > 0) {
                        String msg = "§eIniciando em " + this.lobbyTimerSeconds + "s...";

                        for(UUID uuid : this.currentHeat.getDrivers().keySet()) {
                            Player p = this.plugin.getServer().getPlayer(uuid);
                            if (p != null) {
                                p.sendMessage(msg);
                                if (this.lobbyTimerSeconds <= 5) {
                                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 2.0F);
                                }
                            }
                        }
                    }

                    if (this.lobbyTimerSeconds <= 0) {
                        this.stopLobbyTimer();
                        if (this.plugin.getReadyCheckManager() != null) {
                            String msg = "§6§lIniciando verificação de presença...";

                            for(UUID uuid : this.currentHeat.getDrivers().keySet()) {
                                Player p = this.plugin.getServer().getPlayer(uuid);
                                if (p != null) {
                                    p.sendMessage(msg);
                                }
                            }

                            this.plugin.getReadyCheckManager().startAutoReadyCheck(this.currentHeat, () -> this.startQuickRace((Player)null));
                        } else {
                            this.startQuickRace((Player)null);
                        }
                    }

                }
            }
        } else {
            this.stopLobbyTimer();
        }
    }

    public boolean isPlayerInActiveRace(UUID playerUUID) {
        if (this.currentHeat == null) {
            return false;
        } else {
            HeatState state = this.currentHeat.getHeatState();
            return (state == HeatState.RACING || state == HeatState.STARTING || state == HeatState.LOADED) && this.currentHeat.getDriver(playerUUID) != null;
        }
    }

    public boolean isPlayerActivelyRacing(UUID playerUUID) {
        return this.currentHeat == null ? false : this.currentHeat.isPlayerActivelyRacing(playerUUID);
    }

    public Optional<Events> getCurrentQuickRace() {
        return Optional.ofNullable(this.currentQuickRace);
    }

    public Optional<Heats> getCurrentHeat() {
        return Optional.ofNullable(this.currentHeat);
    }

    public boolean isQuickRaceActive() {
        return this.currentHeat != null && this.currentHeat.getHeatState() != HeatState.FINISHED;
    }

    public boolean isQuickRaceRunning() {
        return this.currentHeat != null && this.currentHeat.getHeatState() == HeatState.RACING;
    }
}
