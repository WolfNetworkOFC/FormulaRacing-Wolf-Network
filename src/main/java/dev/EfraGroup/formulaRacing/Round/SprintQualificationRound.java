package dev.EfraGroup.formulaRacing.Round;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.QualifyingSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.SessionLogic;

public class SprintQualificationRound extends Rounds {
    public SprintQualificationRound(FormulaRacing plugin, int id, Events event, int roundIndex, RoundType roundType) {
        super(plugin, id, event, roundIndex, roundType);
    }

    public Heats createHeat(int heatNumber) {
        Heats heat = new Heats(this.plugin, 0, this, heatNumber);
        heat.setCollisionMode(CollisionMode.DISABLED);
        heat.setStartDelay(5);
        heat.setTotalLaps(1);
        this.heats.put(heatNumber, heat);
        return heat;
    }

    protected void startHeat(Heats heat) {
        heat.loadHeat();
        heat.startQualifying();
    }

    public void broadcastResults() {
        this.plugin.getDebugManager().logRaceSystem("Anunciando resultados da Qualificação Sprint...");
    }

    public SessionLogic getSessionLogic() {
        return new QualifyingSession();
    }
}
