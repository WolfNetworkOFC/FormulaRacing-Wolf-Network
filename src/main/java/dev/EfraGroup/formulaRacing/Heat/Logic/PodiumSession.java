package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.EventResults;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.RoundState;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PodiumSession implements SessionLogic {

    private final FormulaRacing plugin;

    public PodiumSession(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(Heats heat) {
        heat.setHeatState(HeatState.RACING);
        Rounds round = heat.getRound();
        Events event = round != null ? round.getEvent() : null;

        List<Driver> results = gatherResults(event, round);

        if (results.isEmpty()) {
            this.plugin.getDebugManager().logRaceSystem("Podium heat aborted: no results to reveal.");
            heat.finishHeat(false);
            if (round != null) {
                round.onHeatFinished();
            }
            return;
        }

        if (this.plugin.getPodiumManager() == null) {
            this.plugin.getDebugManager().logRaceSystem("PodiumManager unavailable.");
            heat.finishHeat(false);
            if (round != null) {
                round.onHeatFinished();
            }
            return;
        }

        this.plugin.getPodiumManager().startCeremony(event, results, () -> {
            if (round != null) {
                round.finishRound();
            }
            if (event != null) {
                if (this.plugin.getLeagueManager() != null) {
                    this.plugin.getLeagueManager().onEventFinished(event, results);
                }
                if (event.getAnnouncements() != null) {
                    Heats firstHeat = heat;
                    event.getAnnouncements().broadcastFinalStandings(firstHeat, results, HeatState.FINISHED);
                }
                event.finish();
            }
        });
    }

    private List<Driver> gatherResults(Events event, Rounds podiumRound) {
        List<Driver> results = new ArrayList<>();
        if (event == null || podiumRound == null) {
            return results;
        }

        // Find the last finished round before this podium round.
        Rounds sourceRound = null;
        for (Rounds r : event.getEventSchedule().getRoundsOrdered()) {
            if (r.getRoundIndex() >= podiumRound.getRoundIndex()) {
                break;
            }
            if (r.getRoundState() == RoundState.FINISHED) {
                sourceRound = r;
            }
        }

        if (sourceRound == null) {
            // Fallback: any finished round.
            for (Rounds r : event.getEventSchedule().getRoundsOrdered()) {
                if (r.getRoundState() == RoundState.FINISHED) {
                    sourceRound = r;
                }
            }
        }

        if (sourceRound != null && !sourceRound.getHeats().isEmpty()) {
            List<dev.EfraGroup.formulaRacing.Heat.Heats> heats = new ArrayList<>(sourceRound.getHeats().values());
            results = EventResults.generateRoundResults(heats);
        }

        return results;
    }

    @Override
    public boolean passLap(Heats heat, Driver driver) {
        return false;
    }
}
