package dev.EfraGroup.formulaRacing.Heat;

/**
 * Configurações avançadas de Heat.
 * Nota: o gamemode agora é definido pelo RoundType do round, não mais por heat individual.
 */
public class HeatConfig {
    private boolean isTimeBased = false;
    private int timeLimitSeconds = 0;
    private boolean enableCheckeredFlagFlow = false;
    private boolean lastLapTriggered = false;
    private boolean raceFinishedForAll = false;
    private boolean fuelSystemEnabled = false;
    private double startingFuel = 100.0D;
    private double fuelConsumptionPerSecond = 0.45D;

    public HeatConfig() {
    }

    public HeatConfig(boolean isTimeBased, int timeLimitSeconds, boolean enableCheckeredFlagFlow) {
        this.isTimeBased = isTimeBased;
        this.timeLimitSeconds = timeLimitSeconds;
        this.enableCheckeredFlagFlow = enableCheckeredFlagFlow;
    }

    public boolean isTimeBased() {
        return isTimeBased;
    }

    public void setTimeBased(boolean timeBased) {
        isTimeBased = timeBased;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public void setTimeLimitSeconds(int timeLimitSeconds) {
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public boolean isEnableCheckeredFlagFlow() {
        return enableCheckeredFlagFlow;
    }

    public void setEnableCheckeredFlagFlow(boolean enableCheckeredFlagFlow) {
        this.enableCheckeredFlagFlow = enableCheckeredFlagFlow;
    }

    public boolean isLastLapTriggered() {
        return lastLapTriggered;
    }

    public void setLastLapTriggered(boolean lastLapTriggered) {
        this.lastLapTriggered = lastLapTriggered;
    }

    public boolean isRaceFinishedForAll() {
        return raceFinishedForAll;
    }

    public void setRaceFinishedForAll(boolean raceFinishedForAll) {
        this.raceFinishedForAll = raceFinishedForAll;
    }

    public boolean isFuelSystemEnabled() {
        return fuelSystemEnabled;
    }

    public void setFuelSystemEnabled(boolean fuelSystemEnabled) {
        this.fuelSystemEnabled = fuelSystemEnabled;
    }

    public double getStartingFuel() {
        return startingFuel;
    }

    public void setStartingFuel(double startingFuel) {
        this.startingFuel = Math.max(1.0D, Math.min(100.0D, startingFuel));
    }

    public double getFuelConsumptionPerSecond() {
        return fuelConsumptionPerSecond;
    }

    public void setFuelConsumptionPerSecond(double fuelConsumptionPerSecond) {
        this.fuelConsumptionPerSecond = Math.max(0.01D, fuelConsumptionPerSecond);
    }

    public void reset() {
        this.isTimeBased = false;
        this.timeLimitSeconds = 0;
        this.enableCheckeredFlagFlow = false;
        this.lastLapTriggered = false;
        this.raceFinishedForAll = false;
        this.fuelSystemEnabled = false;
        this.startingFuel = 100.0D;
        this.fuelConsumptionPerSecond = 0.45D;
    }

    public HeatConfig copy() {
        HeatConfig copy = new HeatConfig();
        copy.isTimeBased = this.isTimeBased;
        copy.timeLimitSeconds = this.timeLimitSeconds;
        copy.enableCheckeredFlagFlow = this.enableCheckeredFlagFlow;
        copy.fuelSystemEnabled = this.fuelSystemEnabled;
        copy.startingFuel = this.startingFuel;
        copy.fuelConsumptionPerSecond = this.fuelConsumptionPerSecond;
        return copy;
    }
}
