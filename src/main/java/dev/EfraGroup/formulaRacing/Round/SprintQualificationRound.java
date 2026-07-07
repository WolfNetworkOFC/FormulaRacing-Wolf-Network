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
        heat.setTotalLaps((Integer)null);
        heat.setStartDelay(5);
        this.heats.put(heatNumber, heat);
        return heat;
    }

    public void broadcastResults() {
        this.plugin.getDebugManager().logRaceSystem("Processando resultados da Qualificação Sprint...");
        this.plugin.getRaceEventManager().processQualification(this.event, this);
        this.plugin.getDebugManager().logRaceSystem("Qualificação Sprint finalizada! Grid de largada definido.");
    }

    public SessionLogic getSessionLogic() {
        return new QualifyingSession();
    }
}

