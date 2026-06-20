package dev.EfraGroup.formulaRacing.Round;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.EventState;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.SessionLogic;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public abstract class Rounds {
    protected final FormulaRacing plugin;
    protected int id;
    protected int eventId;
    protected Events event;
    protected int roundIndex;
    protected RoundType roundType;
    protected RoundState roundState;
    protected final Map<Integer, Heats> heats;

    public Rounds(FormulaRacing plugin, int id, Events event, int roundIndex, RoundType roundType) {
        this.plugin = plugin;
        this.id = id;
        this.event = event;
        this.eventId = event != null ? event.getId() : 0;
        this.roundIndex = roundIndex;
        this.roundType = roundType;
        this.roundState = RoundState.SETUP;
        this.heats = new HashMap();
    }

    public Rounds() {
        this.plugin = null;
        this.heats = new HashMap();
    }

    public abstract Heats createHeat(int var1);

    public abstract void broadcastResults();

    public abstract SessionLogic getSessionLogic();

    public void addHeat(Heats heat) {
        this.heats.put(heat.getHeatNumber(), heat);
    }

    public boolean removeHeat(int heatNumber) {
        Heats removed = (Heats)this.heats.remove(heatNumber);
        if (removed != null) {
            this.plugin.getDebugManager().logRaceSystem("Heat " + heatNumber + " removido do Round " + this.id);
            return true;
        } else {
            return false;
        }
    }

    public Optional<Heats> getHeat(int heatNumber) {
        return Optional.ofNullable((Heats)this.heats.get(heatNumber));
    }

    public Optional<Heats> getHeat(String heatCode) {
        for(Heats heat : this.heats.values()) {
            if (heat.getName().equalsIgnoreCase(heatCode)) {
                return Optional.of(heat);
            }
        }

        return Optional.empty();
    }

    public boolean start() {
        if (this.heats.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("Round " + this.id + " não possui heats!");
            return false;
        } else {
            this.roundState = RoundState.RUNNING;
            DebugManager var10000 = this.plugin.getDebugManager();
            int var10001 = this.id;
            var10000.logRaceSystem("Round " + var10001 + " (" + String.valueOf(this.roundType) + ") iniciado.");
            Optional<Heats> firstHeat = this.heats.values().stream().min(Comparator.comparingInt(Heats::getHeatNumber));
            firstHeat.ifPresent(this::startHeat);
            return true;
        }
    }

    protected void startHeat(Heats heat) {
        if ((this.roundType == RoundType.PRACTICE || this.roundType == RoundType.QUALIFICATION || this.roundType == RoundType.SPRINT_QUALIFICATION) && this.event != null && !this.event.getSubscribers().isEmpty()) {
            int pos = 1;

            for(UUID uuid : this.event.getSubscribers().keySet()) {
                if (heat.getDriver(uuid) == null) {
                    heat.addDriver(uuid, pos++);
                }
            }
        }

        if (this.roundType == RoundType.PRACTICE) {
            heat.startPractice();
        } else {
            heat.loadHeat();
            heat.startCountdown();
        }

    }

    public boolean nextHeat() {
        Optional<Heats> currentHeat = this.heats.values().stream().filter((h) -> h.getHeatState() == HeatState.RACING || h.getHeatState() == HeatState.FINISHED || h.getHeatState() == HeatState.QUALIFYING || h.getHeatState() == HeatState.PRACTICE).findFirst();
        if (currentHeat.isEmpty()) {
            return false;
        } else {
            int currentHeatNumber = ((Heats)currentHeat.get()).getHeatNumber();
            Optional<Heats> nextHeat = this.getHeat(currentHeatNumber + 1);
            if (nextHeat.isEmpty()) {
                this.finishRound();
                return false;
            } else {
                this.startHeat((Heats)nextHeat.get());
                return true;
            }
        }
    }

    public void finishRound() {
        this.roundState = RoundState.FINISHED;
        this.plugin.getDebugManager().logRaceSystem("Round " + this.id + " finalizado!");
        this.broadcastResults();
        if (this.event != null && this.event.getState() != EventState.FINISHED && this.event.getEventSchedule() != null) {
            this.event.getEventSchedule().scheduleNextRound(15);
        }

    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Events getEvent() {
        return this.event;
    }

    public void setEvent(Events event) {
        this.event = event;
    }

    public int getRoundIndex() {
        return this.roundIndex;
    }

    public void setRoundIndex(int roundIndex) {
        this.roundIndex = roundIndex;
    }

    public RoundType getRoundType() {
        return this.roundType;
    }

    public void setRoundType(RoundType roundType) {
        this.roundType = roundType;
    }

    public RoundState getRoundState() {
        return this.roundState;
    }

    public void setRoundState(RoundState roundState) {
        if (this.roundState != null) {
            RoundStateMachine.validateTransition(this.roundState, roundState);
        }
        this.roundState = roundState;
        if (this.plugin != null && this.id > 0) {
            this.plugin.getRaceEventManager().getDatabaseManager().updateRoundState(this.id, roundState);
        }

    }

    public void setState(RoundState state) {
        this.setRoundState(state);
    }

    public Map<Integer, Heats> getHeats() {
        return this.heats;
    }

    public Collection<Heats> getHeatsCollection() {
        return this.heats.values();
    }

    public Heats getFirstHeat() {
        return (Heats)this.heats.get(1);
    }

    public Optional<Heats> getActiveHeat() {
        return this.heats.values().stream().filter((h) -> h.getHeatState() != HeatState.FINISHED && h.getHeatState() != HeatState.IDLE && h.getHeatState() != HeatState.SETUP).findFirst();
    }

    public String getName() {
        return "Round " + this.roundIndex;
    }

    public String getDisplayName() {
        int var10000 = this.roundIndex;
        return "Round " + var10000 + " (" + String.valueOf(this.roundType) + ")";
    }

    public RoundType getType() {
        return this.roundType;
    }

    public RoundState getState() {
        return this.roundState;
    }

    public int getRoundNumber() {
        return this.roundIndex;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getEventId() {
        return this.eventId;
    }

    public void finish() {
        this.finishRound();
    }

    public Optional<Heats> getCurrentHeat() {
        return this.getActiveHeat();
    }

    public List<Heats> getHeatsOrdered() {
        return this.heats.values().stream().sorted(Comparator.comparingInt(Heats::getHeatNumber)).toList();
    }
}
