//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Round;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.PracticeSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.SessionLogic;

public class PracticeRound extends Rounds {
    public PracticeRound(FormulaRacing plugin, int id, Events event, int roundIndex, RoundType roundType) {
        super(plugin, id, event, roundIndex, roundType);
    }

    public PracticeRound(FormulaRacing plugin) {
    }

    public Heats createHeat(int heatNumber) {
        Heats heat = new Heats(this.plugin, 0, this, heatNumber);
        heat.setCollisionMode(CollisionMode.DISABLED);
        heat.setTotalLaps((Integer)null);
        heat.setCanReset(true);
        this.heats.put(heatNumber, heat);
        return heat;
    }

    public void broadcastResults() {
        this.plugin.getDebugManager().logRaceSystem("Resultados do Treino Livre (Practice) - [Implementar Broadcast]");
    }

    public SessionLogic getSessionLogic() {
        return new PracticeSession();
    }
}
