//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Event;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Round.EliminationRound;
import dev.EfraGroup.formulaRacing.Round.PracticeRound;
import dev.EfraGroup.formulaRacing.Round.QualificationRound;
import dev.EfraGroup.formulaRacing.Round.RaceRound;
import dev.EfraGroup.formulaRacing.Round.RoundState;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EventSchedule {
    private final FormulaRacing plugin;
    private final Events event;
    private final Map<Integer, Rounds> rounds;
    private Integer currentRoundIndex;

    public EventSchedule(FormulaRacing plugin, Events event) {
        this.plugin = plugin;
        this.event = event;
        this.rounds = new HashMap();
        this.currentRoundIndex = null;
    }

    public Rounds createRound(int roundIndex, RoundType roundType) {
        if (this.rounds.containsKey(roundIndex)) {
            this.plugin.getDebugManager().logEventSystem("Round " + roundIndex + " já existe no Evento " + this.event.getId());
            return (Rounds)this.rounds.get(roundIndex);
        } else {
            Rounds round;
            switch (roundType) {
                case PRACTICE:
                    round = new PracticeRound(this.plugin, 0, this.event, roundIndex, roundType);
                    break;
                case QUALIFICATION:
                    round = new QualificationRound(this.plugin, 0, this.event, roundIndex, roundType);
                    break;
                case ELIMINATION:
                    round = new EliminationRound(this.plugin, 0, this.event, roundIndex, roundType);
                    break;
                case FINAL:
                default:
                    round = new RaceRound(this.plugin, 0, this.event, roundIndex, roundType);
            }

            this.rounds.put(roundIndex, round);
            this.plugin.getDebugManager().logEventSystem("Round " + roundIndex + " (" + String.valueOf(roundType) + ") criado no Evento " + this.event.getId());
            return round;
        }
    }

    public boolean removeRound(int roundIndex) {
        Rounds removed = (Rounds)this.rounds.remove(roundIndex);
        if (removed != null) {
            this.plugin.getDebugManager().logEventSystem("Round " + roundIndex + " removido do Evento " + this.event.getId());
            return true;
        } else {
            return false;
        }
    }

    public Optional<Rounds> getRound(int roundIndex) {
        return Optional.ofNullable((Rounds)this.rounds.get(roundIndex));
    }

    public Optional<Rounds> getCurrentRound() {
        return this.currentRoundIndex == null ? Optional.empty() : this.getRound(this.currentRoundIndex);
    }

    public Optional<Rounds> getNextRound() {
        return this.currentRoundIndex == null ? Optional.empty() : this.getRound(this.currentRoundIndex + 1);
    }

    public Optional<Rounds> getRound(String name) {
        return this.rounds.values().stream().filter((round) -> round.getName() != null && round.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<Heats> getHeat(String name) {
        for(Rounds round : this.rounds.values()) {
            Optional<Heats> heat = round.getHeat(name);
            if (heat.isPresent()) {
                return heat;
            }
        }

        return Optional.empty();
    }

    public void setCurrentRoundAutomatically() {
        int lastFinished = 0;
        List<Rounds> ordered = this.getRoundsOrdered();

        for(Rounds round : ordered) {
            if (round.getRoundState() != RoundState.FINISHED) {
                break;
            }

            lastFinished = round.getRoundIndex();
        }

        if (lastFinished < ordered.size()) {
            this.currentRoundIndex = lastFinished + 1;
        } else {
            this.currentRoundIndex = lastFinished;
        }

    }

    public boolean hasMoreRounds() {
        if (this.currentRoundIndex == null) {
            return !this.rounds.isEmpty();
        } else {
            return this.currentRoundIndex < this.rounds.size();
        }
    }

    public boolean start() {
        if (this.rounds.isEmpty()) {
            this.plugin.getDebugManager().logEventSystem("Evento " + this.event.getId() + " não possui rounds!");
            return false;
        } else {
            this.currentRoundIndex = 1;
            Optional<Rounds> firstRound = this.getRound(this.currentRoundIndex);
            if (firstRound.isEmpty()) {
                this.plugin.getDebugManager().logEventSystem("Round 1 não encontrado no Evento " + this.event.getId());
                return false;
            } else {
                this.event.setState(EventState.RUNNING);
                this.event.getAnnouncements().broadcastEventStart(this.event);
                return ((Rounds)firstRound.get()).start();
            }
        }
    }

    public boolean nextRound() {
        if (this.currentRoundIndex == null) {
            this.plugin.getDebugManager().logEventSystem("Nenhum round atual no Evento " + this.event.getId());
            return false;
        } else {
            Integer var1 = this.currentRoundIndex;
            this.currentRoundIndex = this.currentRoundIndex + 1;
            Optional<Rounds> nextRound = this.getRound(this.currentRoundIndex);
            if (nextRound.isEmpty()) {
                this.plugin.getDebugManager().logEventSystem("Não há mais rounds no Evento " + this.event.getId() + ". Finalizando evento.");
                this.event.finish();
                return false;
            } else {
                return ((Rounds)nextRound.get()).start();
            }
        }
    }

    public void scheduleNextRound(int delaySeconds) {
        if (this.getNextRound().isEmpty()) {
            this.nextRound();
        } else if (delaySeconds <= 0) {
            this.nextRound();
        } else {
            this.plugin.getDebugManager().logEventSystem("Agendando próximo round em " + delaySeconds + " segundos...");
            this.event.getAnnouncements().broadcastToEvent(this.event, "event_intermission_next_round", new String[]{"{time}", String.valueOf(delaySeconds)});
            SchedulerHelper.runTaskLater(this.plugin, () -> {
                if (this.activeEventsContains(this.event.getId())) {
                    this.nextRound();
                }

            }, delaySeconds * 20L);
        }
    }

    private boolean activeEventsContains(int id) {
        return this.plugin.getRaceEventManager().getEventById(id).isPresent();
    }

    public boolean isLastRound() {
        if (this.currentRoundIndex == null) {
            return false;
        } else {
            return this.currentRoundIndex >= this.rounds.size();
        }
    }

    public List<Rounds> getRoundsOrdered() {
        return this.rounds.values().stream().sorted(Comparator.comparingInt(Rounds::getRoundIndex)).toList();
    }

    public Map<Integer, Rounds> getRounds() {
        return this.rounds;
    }

    public Collection<Rounds> getRoundsCollection() {
        return this.rounds.values().stream().sorted(Comparator.comparingInt(Rounds::getRoundIndex)).toList();
    }

    public List<Rounds> getRoundsList() {
        return this.rounds.values().stream().sorted(Comparator.comparingInt(Rounds::getRoundIndex)).toList();
    }

    public Integer getCurrentRoundIndex() {
        return this.currentRoundIndex;
    }

    public void setCurrentRoundIndex(Integer currentRoundIndex) {
        this.currentRoundIndex = currentRoundIndex;
    }

    public int getRoundCount() {
        return this.rounds.size();
    }
}
