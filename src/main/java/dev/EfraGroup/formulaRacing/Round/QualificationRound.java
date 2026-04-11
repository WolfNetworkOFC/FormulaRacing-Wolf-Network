//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Round;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.QualifyingSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.SessionLogic;

public class QualificationRound extends Rounds {
    public QualificationRound(FormulaRacing plugin, int id, Events event, int roundIndex, RoundType roundType) {
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
        this.plugin.getDebugManager().logRaceSystem("Processando resultados de qualificação...");
        this.plugin.getRaceEventManager().processQualification(this.event, this);
        this.plugin.getDebugManager().logRaceSystem("Qualificação finalizada! Grid de largada definido.");
    }

    public SessionLogic getSessionLogic() {
        return new QualifyingSession();
    }
}
