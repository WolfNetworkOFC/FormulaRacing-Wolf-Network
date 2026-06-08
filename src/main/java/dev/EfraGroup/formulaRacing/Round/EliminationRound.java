package dev.EfraGroup.formulaRacing.Round;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.EliminationSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.SessionLogic;

public class EliminationRound extends Rounds {
    private int eliminationIntervalSeconds = 30; // Default: 30 seconds
    private int minimumDrivers = 2; // Minimum drivers to continue elimination

    public EliminationRound(FormulaRacing plugin, int id, Events event, int roundIndex, RoundType roundType) {
        super(plugin, id, event, roundIndex, roundType);
    }

    public Heats createHeat(int heatNumber) {
        Heats heat = new Heats(this.plugin, 0, this, heatNumber);
        heat.setCollisionMode(CollisionMode.HIGH);
        heat.setStartDelay(5);
        heat.setTotalLaps(999); // Unlimited laps for elimination
        this.heats.put(heatNumber, heat);
        return heat;
    }

    protected void startHeat(Heats heat) {
        heat.loadHeat();
        heat.startCountdown();
    }

    public void broadcastResults() {
        this.plugin.getDebugManager().logRaceSystem("Anunciando resultados da Eliminação...");
    }

    public SessionLogic getSessionLogic() {
        EliminationSession session = new EliminationSession();
        session.setEliminationInterval(eliminationIntervalSeconds);
        session.setMinimumDrivers(minimumDrivers);
        return session;
    }

    public int getEliminationIntervalSeconds() {
        return eliminationIntervalSeconds;
    }

    public void setEliminationIntervalSeconds(int eliminationIntervalSeconds) {
        this.eliminationIntervalSeconds = eliminationIntervalSeconds;
    }

    public int getMinimumDrivers() {
        return minimumDrivers;
    }

    public void setMinimumDrivers(int minimumDrivers) {
        this.minimumDrivers = minimumDrivers;
    }
}
