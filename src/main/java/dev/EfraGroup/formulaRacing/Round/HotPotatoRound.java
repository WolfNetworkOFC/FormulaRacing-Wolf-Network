package dev.EfraGroup.formulaRacing.Round;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.HotPotatoSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.SessionLogic;

public class HotPotatoRound extends Rounds {

    public HotPotatoRound(FormulaRacing plugin, int id, Events event, int roundIndex, RoundType roundType) {
        super(plugin, id, event, roundIndex, roundType);
    }

    @Override
    public Heats createHeat(int heatNumber) {
        Heats heat = new Heats(this.plugin, 0, this, heatNumber);
        heat.setCollisionMode(CollisionMode.HIGH);
        heat.setStartDelay(5);
        this.heats.put(heatNumber, heat);
        return heat;
    }

    @Override
    public void broadcastResults() {
        this.plugin.getDebugManager().logRaceSystem("Anunciando resultados da Batata Quente...");
    }

    @Override
    public SessionLogic getSessionLogic() {
        return new HotPotatoSession();
    }
}
