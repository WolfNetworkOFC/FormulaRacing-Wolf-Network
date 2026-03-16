//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Round;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.RaceSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.SessionLogic;

public class RaceRound extends Rounds {
    public RaceRound(FormulaRacing plugin, int id, Events event, int roundIndex, RoundType roundType) {
        super(plugin, id, event, roundIndex, roundType);
    }

    public Heats createHeat(int heatNumber) {
        Heats heat = new Heats(this.plugin, 0, this, heatNumber);
        heat.setCollisionMode(CollisionMode.HIGH);
        heat.setStartDelay(5);
        this.heats.put(heatNumber, heat);
        return heat;
    }

    protected void startHeat(Heats heat) {
        heat.loadHeat();
        if (this.plugin.getReadyCheckManager() != null) {
            this.plugin.getReadyCheckManager().startAutoReadyCheck(heat, () -> heat.startCountdown());
        } else {
            heat.startCountdown();
        }

    }

    public void broadcastResults() {
        this.plugin.getDebugManager().logRaceSystem("Anunciando resultados da Corrida...");
    }

    public SessionLogic getSessionLogic() {
        return new RaceSession(this.plugin);
    }
}
