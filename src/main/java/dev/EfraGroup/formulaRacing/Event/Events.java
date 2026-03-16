//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Event;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.Controllers.TrackIntegrationManager;
import dev.EfraGroup.formulaRacing.Database.Track;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Participant.Spectator;
import dev.EfraGroup.formulaRacing.Participant.Subscriber;
import dev.EfraGroup.formulaRacing.Round.RoundState;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class Events {
    private final FormulaRacing plugin;
    private final RaceEventManager raceEventManager;
    private int id;
    private UUID uuid;
    private UUID creatorUUID;
    private String displayName;
    private String trackNameWS;
    private long creationTime;
    private EventState state;
    private boolean openSign;
    private String league;
    private final EventSchedule eventSchedule;
    private final EventCountdown eventCountdown;
    private final EventAnnouncements announcements;
    private final Map<UUID, Subscriber> subscribers;
    private final Map<UUID, Subscriber> reserves;
    private final Map<UUID, Spectator> spectators;

    public Events(FormulaRacing plugin, RaceEventManager raceEventManager, int id, UUID creatorUUID, String displayName) {
        this.plugin = plugin;
        this.raceEventManager = raceEventManager;
        this.id = id;
        this.uuid = UUID.randomUUID();
        this.creatorUUID = creatorUUID;
        this.displayName = displayName;
        this.creationTime = System.currentTimeMillis();
        this.state = EventState.SETUP;
        this.openSign = true;
        this.eventSchedule = new EventSchedule(plugin, this);
        this.eventCountdown = new EventCountdown(plugin, this);
        this.announcements = new EventAnnouncements(plugin);
        this.subscribers = new HashMap();
        this.reserves = new HashMap();
        this.spectators = new HashMap();
    }

    public boolean start() {
        if (this.state != EventState.SETUP) {
            this.plugin.getDebugManager().logRaceSystem("Evento " + this.id + " já está em andamento ou finalizado!");
            return false;
        } else if (this.trackNameWS != null && !this.trackNameWS.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("Iniciando evento: " + this.displayName);
            boolean started = this.eventSchedule.start();
            if (started) {
                this.setState(EventState.RUNNING);
            }

            return started;
        } else {
            this.plugin.getDebugManager().logRaceSystem("Evento " + this.id + " não possui pista configurada!");
            return false;
        }
    }

    public boolean finish() {
        if (this.state != EventState.FINISHED && this.state != EventState.SETUP) {
            if (this.eventSchedule.isLastRound()) {
                Optional<Rounds> currentRound = this.eventSchedule.getCurrentRound();
                if (currentRound.isPresent() && ((Rounds)currentRound.get()).getRoundState() != RoundState.FINISHED) {
                    this.plugin.getDebugManager().logRaceSystem("Evento " + this.id + " não pode ser finalizado: o último round ainda não terminou.");
                    return false;
                }
            } else if (this.eventSchedule.hasMoreRounds()) {
                this.plugin.getDebugManager().logRaceSystem("Evento " + this.id + " não pode ser finalizado: ainda existem rounds pendentes.");
                return false;
            }

            this.setState(EventState.FINISHED);
            this.setOpenSign(false);
            this.plugin.getDebugManager().logRaceSystem("Evento finalizado: " + this.displayName);
            this.announcements.broadcastEventFinish(this);
            if (this.eventSchedule.getCurrentRound().isPresent()) {
                Rounds lastRound = (Rounds)this.eventSchedule.getCurrentRound().get();
                List<Heats> heats = new ArrayList(lastRound.getHeats().values());
                new ArrayList();
                if (lastRound.getType() == RoundType.FINAL) {
                    List<Driver> results = EventResults.generateRoundResults(heats);
                    this.announcements.broadcastFinalStandings((Heats)heats.get(0), results, lastRound.getRoundState() == RoundState.FINISHED ? HeatState.FINISHED : HeatState.RACING);
                    if (!results.isEmpty()) {
                        Driver winner = (Driver)results.get(0);
                        String winnerName = Bukkit.getOfflinePlayer(winner.getUuid()).getName();
                        this.plugin.getDebugManager().logRaceSystem("Vencedor do evento " + this.displayName + ": " + winnerName);
                    }

                    if (this.plugin.getPodiumManager() != null) {
                        this.plugin.getPodiumManager().startCeremony(this, results);
                    }
                }
            }

            return true;
        } else {
            this.plugin.getDebugManager().logRaceSystem("Evento " + this.id + " já está finalizado ou ainda está em SETUP!");
            return false;
        }
    }

    public boolean addSubscriber(UUID playerUUID) {
        if (this.subscribers.containsKey(playerUUID)) {
            return false;
        } else if (!this.openSign && this.state != EventState.SETUP) {
            this.plugin.getDebugManager().logRaceSystem("Inscrições fechadas para o evento " + this.displayName);
            return false;
        } else {
            Subscriber subscriber = new Subscriber(playerUUID, this.id);
            this.subscribers.put(playerUUID, subscriber);
            DebugManager var10000 = this.plugin.getDebugManager();
            String var10001 = this.displayName;
            var10000.logRaceSystem("Piloto inscrito no evento " + var10001 + ": " + String.valueOf(playerUUID));
            if (!this.spectators.containsKey(playerUUID)) {
                this.addSpectator(playerUUID);
            }

            if (this.state == EventState.RUNNING) {
                for(Rounds round : this.eventSchedule.getRounds().values()) {
                    if (round.getType() == RoundType.PRACTICE && round.getState() == RoundState.RUNNING) {
                        for(Heats heat : round.getHeats().values()) {
                            if (heat.getHeatState() == HeatState.PRACTICE) {
                                heat.addDriver(playerUUID, heat.getDriverCount() + 1);
                                Player player = Bukkit.getPlayer(playerUUID);
                                if (player != null && player.isOnline()) {
                                    heat.getScoreboardManager().addPlayer(player, heat);
                                    heat.getActionBarManager().addPlayer(player, heat);
                                }
                            }
                        }
                    }
                }
            }

            return true;
        }
    }

    public boolean isSubscriber(UUID playerUUID) {
        return this.subscribers.containsKey(playerUUID);
    }

    public boolean hasRunningHeat() {
        return this.getRunningHeat().isPresent();
    }

    public Optional<Heats> getRunningHeat() {
        return this.state != EventState.RUNNING ? Optional.empty() : this.eventSchedule.getCurrentRound().flatMap(Rounds::getActiveHeat);
    }

    public boolean isReserve(UUID playerUUID) {
        return this.reserves.containsKey(playerUUID);
    }

    public boolean removeSubscriber(UUID playerUUID) {
        Subscriber removed = (Subscriber)this.subscribers.remove(playerUUID);
        if (removed != null) {
            DebugManager var10000 = this.plugin.getDebugManager();
            String var10001 = this.displayName;
            var10000.logRaceSystem("Piloto removido do evento " + var10001 + ": " + String.valueOf(playerUUID));
            return true;
        } else {
            return false;
        }
    }

    public boolean moveToReserves(UUID playerUUID) {
        Subscriber subscriber = (Subscriber)this.subscribers.remove(playerUUID);
        if (subscriber == null) {
            return false;
        } else {
            this.reserves.put(playerUUID, subscriber);
            this.plugin.getDebugManager().logRaceSystem("Piloto movido para reservas: " + String.valueOf(playerUUID));
            return true;
        }
    }

    public boolean moveFromReserves(UUID playerUUID) {
        Subscriber reserve = (Subscriber)this.reserves.remove(playerUUID);
        if (reserve == null) {
            return false;
        } else {
            this.subscribers.put(playerUUID, reserve);
            this.plugin.getDebugManager().logRaceSystem("Piloto movido de reservas para inscritos: " + String.valueOf(playerUUID));
            return true;
        }
    }

    public boolean addSpectator(UUID playerUUID) {
        if (this.spectators.containsKey(playerUUID)) {
            return false;
        } else {
            Spectator spectator = new Spectator(playerUUID, String.valueOf(this.id));
            this.spectators.put(playerUUID, spectator);
            return true;
        }
    }

    public boolean removeSpectator(UUID playerUUID) {
        return this.spectators.remove(playerUUID) != null;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getCreatorUUID() {
        return this.creatorUUID;
    }

    public void setCreatorUUID(UUID creatorUUID) {
        this.creatorUUID = creatorUUID;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTrackNameWS() {
        return this.trackNameWS;
    }

    public Track getTrack() {
        return this.trackNameWS != null && !this.trackNameWS.isEmpty() ? this.plugin.getTrackIntegrationManager().getTrack(this.trackNameWS) : null;
    }

    public void setTrackNameWS(String trackNameWS) {
        this.trackNameWS = trackNameWS != null ? trackNameWS.replaceAll("\\s+", "") : null;
    }

    public boolean addReserve(UUID playerUUID) {
        if (this.reserves.containsKey(playerUUID)) {
            return false;
        } else {
            Subscriber reserve = new Subscriber(playerUUID, this.id);
            this.reserves.put(playerUUID, reserve);
            if (!this.spectators.containsKey(playerUUID)) {
                this.addSpectator(playerUUID);
            }

            return true;
        }
    }

    public boolean removeReserve(UUID playerUUID) {
        return this.reserves.remove(playerUUID) != null;
    }

    public long getCreationTime() {
        return this.creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public EventState getState() {
        return this.state;
    }

    public void setState(EventState state) {
        this.state = state;
        if (this.id > 0 && this.raceEventManager != null) {
            this.raceEventManager.getDatabaseManager().updateEventState(this.id, state);
        }

    }

    public boolean isOpenSign() {
        return this.openSign;
    }

    public void setOpenSign(boolean openSign) {
        this.openSign = openSign;
        if (this.id > 0 && this.raceEventManager != null) {
            this.raceEventManager.getDatabaseManager().updateEventOpenSign(this.id, openSign);
        }

    }

    public String getLeague() {
        return this.league;
    }

    public void setLeague(String league) {
        this.league = league;
    }

    public EventSchedule getEventSchedule() {
        return this.eventSchedule;
    }

    public EventCountdown getEventCountdown() {
        return this.eventCountdown;
    }

    public EventAnnouncements getAnnouncements() {
        return this.announcements;
    }

    public Map<UUID, Subscriber> getSubscribers() {
        return this.subscribers;
    }

    public Map<UUID, Subscriber> getReserves() {
        return this.reserves;
    }

    public Map<UUID, Spectator> getSpectators() {
        return this.spectators;
    }

    public int getSubscriberCount() {
        return this.subscribers.size();
    }

    public int getReserveCount() {
        return this.reserves.size();
    }

    public int getSpectatorCount() {
        return this.spectators.size();
    }

    public boolean isDriver(UUID playerUUID) {
        for(Rounds round : this.eventSchedule.getRounds().values()) {
            for(Heats heat : round.getHeats().values()) {
                if (heat.getDriver(playerUUID) != null) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isActivelyRacing(UUID playerUUID) {
        for(Rounds round : this.eventSchedule.getRounds().values()) {
            for(Heats heat : round.getHeats().values()) {
                if (heat.isPlayerActivelyRacing(playerUUID)) {
                    Driver d = heat.getDriver(playerUUID);
                    if (d != null && !d.isFinished() && !d.isDnf()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean isActive() {
        return this.state != EventState.FINISHED;
    }

    public String getCreatorName() {
        return Bukkit.getOfflinePlayer(this.creatorUUID).getName();
    }

    public long getDate() {
        return this.creationTime;
    }

    public boolean setTrack(String trackName) {
        String normalizedTrackName = trackName.replaceAll("\\s+", "");
        TrackIntegrationManager trackManager = this.plugin.getTrackIntegrationManager();
        if (trackManager.getTrack(normalizedTrackName) == null) {
            return false;
        } else {
            this.trackNameWS = normalizedTrackName;
            if (this.id > 0 && this.raceEventManager != null) {
                this.raceEventManager.getDatabaseManager().updateEventTrack(this.id, normalizedTrackName);
            }

            return true;
        }
    }

    public EventSchedule getSchedule() {
        return this.eventSchedule;
    }

    public String toString() {
        int var10000 = this.id;
        return "Events{id=" + var10000 + ", displayName='" + this.displayName + "', track='" + this.trackNameWS + "', state=" + String.valueOf(this.state) + ", subscribers=" + this.subscribers.size() + ", rounds=" + this.eventSchedule.getRoundCount() + "}";
    }
}
