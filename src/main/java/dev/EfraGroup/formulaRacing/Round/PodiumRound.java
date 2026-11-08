package dev.EfraGroup.formulaRacing.Round;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.EventState;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.PodiumSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.SessionLogic;
import java.util.UUID;

public class PodiumRound extends Rounds {

    public PodiumRound(FormulaRacing plugin, int id, Events event, int roundIndex, RoundType roundType) {
        super(plugin, id, event, roundIndex, roundType);
    }

    @Override
    public Heats createHeat(int heatNumber) {
        Heats heat = new Heats(this.plugin, 0, this, heatNumber);
        this.heats.put(heatNumber, heat);
        return heat;
    }

    @Override
    public void broadcastResults() {
        this.plugin.getDebugManager().logRaceSystem("Anunciando resultados do Podio...");
    }

    @Override
    protected void startHeat(Heats heat) {
        if (this.event != null && !this.event.getSubscribers().isEmpty()) {
            int pos = 1;
            for (UUID uuid : this.event.getSubscribers().keySet()) {
                if (heat.getDriver(uuid) == null) {
                    heat.addDriver(uuid, pos++);
                }
            }
        }

        if (this.roundState == RoundState.SETUP) {
            this.roundState = RoundState.RUNNING;
        }
        Events ev = this.event;
        if (ev != null && ev.getState() == EventState.SETUP) {
            ev.ensureRunning(this);
        }

        heat.setHeatState(HeatState.LOADED);
        heat.startCountdown();
    }

    @Override
    public SessionLogic getSessionLogic() {
        return new PodiumSession(this.plugin);
    }
}
