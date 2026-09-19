package dev.EfraGroup.formulaRacing.Heat;

/**
 * A gimmick scheduled in a heat: "paste this gimmick when the heat reaches this lap".
 * Persisted in {@code fr_heat_gimmicks}; the {@code triggered} flag is runtime only
 * and is reset every time the heat goes back to RACING.
 */
public class GimmickSchedule {

    private final int heatId;
    private final GimmickConfig gimmick;
    private final int triggerLap;
    private volatile boolean triggered;

    public GimmickSchedule(int heatId, GimmickConfig gimmick, int triggerLap) {
        this.heatId = heatId;
        this.gimmick = gimmick;
        this.triggerLap = triggerLap;
    }

    public int getHeatId() {
        return heatId;
    }

    public GimmickConfig getGimmick() {
        return gimmick;
    }

    public int getTriggerLap() {
        return triggerLap;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }
}
