package dev.EfraGroup.formulaRacing.Heat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Configurações avançadas de Heat.
 * Nota: o gamemode agora é definido pelo RoundType do round, não mais por heat individual.
 */
public class HeatConfig {
    /** Persistência: serializado para a coluna fr_heats.heatconfig (JSON). */
    private static final Gson GSON = new GsonBuilder().create();

    /** Callback do heat dono — dispara markConfigDirty() para o flush no banco. */
    private transient Runnable dirtyCallback;

    // Runtime/config flags are written by one thread (command, global monitor)
    // and read by others (player region threads on Folia) — volatile so the
    // flip is always visible (no missing happens-before).
    private volatile boolean isTimeBased = false;
    private int timeLimitSeconds = 0;
    private volatile boolean enableCheckeredFlagFlow = false;
    // Estado de runtime — NÃO persiste (transient fica fora do JSON).
    private transient volatile boolean lastLapTriggered = false;
    private transient volatile boolean raceFinishedForAll = false;
    private volatile boolean fuelSystemEnabled = false;
    private double startingFuel = 100.0D;
    private double fuelConsumptionPerSecond = 0.45D;
    private volatile boolean finalRaceBossbarEnabled = true;
    private volatile int finalRaceBossbarTimeoutSeconds = 120;
    /** F1 start: random hold after the 5th light + jump-start penalty. */
    private volatile boolean f1StartEnabled = false;
    private volatile int f1StartPenaltySeconds = 3;

    // ERS (Energy Recovery System)
    private volatile double ersRechargeSpeed = 0.4;      // Velocidade de recarga em modo Recharging
    private volatile double ersDrainSpeed = 0.6;         // Velocidade de gasto em modo Deploy
    private volatile double ersDeployPower = 0.047F;     // Potência do boost em modo Deploy

    public HeatConfig() {
    }

    public HeatConfig(boolean isTimeBased, int timeLimitSeconds, boolean enableCheckeredFlagFlow) {
        this.isTimeBased = isTimeBased;
        this.timeLimitSeconds = timeLimitSeconds;
        this.enableCheckeredFlagFlow = enableCheckeredFlagFlow;
    }

    /** Liga o heat dono: qualquer mudança de config marca o heat como "sujo" para o flush. */
    public void setDirtyCallback(Runnable dirtyCallback) {
        this.dirtyCallback = dirtyCallback;
    }

    private void markDirty() {
        if (this.dirtyCallback != null) {
            this.dirtyCallback.run();
        }
    }

    /** Serializa as flags persistíveis (fr_heats.heatconfig). */
    public String toJson() {
        try {
            return GSON.toJson(this);
        } catch (Exception e) {
            return null;
        }
    }

    /** Restaura as flags salvas; retorna null se o JSON estiver vazio/inválido. */
    public static HeatConfig fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(json, HeatConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isF1StartEnabled() {
        return f1StartEnabled;
    }

    public void setF1StartEnabled(boolean f1StartEnabled) {
        this.f1StartEnabled = f1StartEnabled;
        this.markDirty();
    }

    public int getF1StartPenaltySeconds() {
        return f1StartPenaltySeconds;
    }

    public void setF1StartPenaltySeconds(int f1StartPenaltySeconds) {
        this.f1StartPenaltySeconds = Math.max(1, Math.min(30, f1StartPenaltySeconds));
        this.markDirty();
    }

    // ERS (Energy Recovery System)
    public double getErsRechargeSpeed() {
        return ersRechargeSpeed;
    }

    public void setErsRechargeSpeed(double ersRechargeSpeed) {
        this.ersRechargeSpeed = Math.max(0.01, Math.min(2.0, ersRechargeSpeed));
        this.markDirty();
    }

    public double getErsDrainSpeed() {
        return ersDrainSpeed;
    }

    public void setErsDrainSpeed(double ersDrainSpeed) {
        this.ersDrainSpeed = Math.max(0.01, Math.min(2.0, ersDrainSpeed));
        this.markDirty();
    }

    public double getErsDeployPower() {
        return ersDeployPower;
    }

    public void setErsDeployPower(double ersDeployPower) {
        this.ersDeployPower = Math.max(0.01, Math.min(0.1, ersDeployPower));
        this.markDirty();
    }

    public boolean isTimeBased() {
        return isTimeBased;
    }

    public void setTimeBased(boolean timeBased) {
        isTimeBased = timeBased;
        this.markDirty();
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public void setTimeLimitSeconds(int timeLimitSeconds) {
        this.timeLimitSeconds = timeLimitSeconds;
        this.markDirty();
    }

    public boolean isEnableCheckeredFlagFlow() {
        return enableCheckeredFlagFlow;
    }

    public void setEnableCheckeredFlagFlow(boolean enableCheckeredFlagFlow) {
        this.enableCheckeredFlagFlow = enableCheckeredFlagFlow;
        this.markDirty();
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
        this.markDirty();
    }

    public double getStartingFuel() {
        return startingFuel;
    }

    public void setStartingFuel(double startingFuel) {
        this.startingFuel = Math.max(1.0D, Math.min(100.0D, startingFuel));
        this.markDirty();
    }

    public double getFuelConsumptionPerSecond() {
        return fuelConsumptionPerSecond;
    }

    public void setFuelConsumptionPerSecond(double fuelConsumptionPerSecond) {
        this.fuelConsumptionPerSecond = Math.max(0.01D, fuelConsumptionPerSecond);
        this.markDirty();
    }

    public boolean isFinalRaceBossbarEnabled() {
        return finalRaceBossbarEnabled;
    }

    public void setFinalRaceBossbarEnabled(boolean enabled) {
        finalRaceBossbarEnabled = enabled;
        this.markDirty();
    }

    public int getFinalRaceBossbarTimeoutSeconds() {
        return finalRaceBossbarTimeoutSeconds;
    }

    public void setFinalRaceBossbarTimeoutSeconds(int seconds) {
        this.finalRaceBossbarTimeoutSeconds = Math.max(10, seconds);
        this.markDirty();
    }

    /** Limpa somente o estado de runtime — a config persistida é mantida. */
    public void resetRuntimeState() {
        this.lastLapTriggered = false;
        this.raceFinishedForAll = false;
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
        this.f1StartEnabled = false;
        this.f1StartPenaltySeconds = 3;
        this.ersRechargeSpeed = 0.4;
        this.ersDrainSpeed = 0.6;
        this.ersDeployPower = 0.047;
        this.finalRaceBossbarEnabled = true;
        this.finalRaceBossbarTimeoutSeconds = 120;
    }

    public HeatConfig copy() {
        HeatConfig copy = new HeatConfig();
        copy.isTimeBased = this.isTimeBased;
        copy.timeLimitSeconds = this.timeLimitSeconds;
        copy.enableCheckeredFlagFlow = this.enableCheckeredFlagFlow;
        copy.fuelSystemEnabled = this.fuelSystemEnabled;
        copy.startingFuel = this.startingFuel;
        copy.fuelConsumptionPerSecond = this.fuelConsumptionPerSecond;
        copy.f1StartEnabled = this.f1StartEnabled;
        copy.f1StartPenaltySeconds = this.f1StartPenaltySeconds;
        copy.ersRechargeSpeed = this.ersRechargeSpeed;
        copy.ersDrainSpeed = this.ersDrainSpeed;
        copy.ersDeployPower = this.ersDeployPower;
        copy.finalRaceBossbarEnabled = this.finalRaceBossbarEnabled;
        copy.finalRaceBossbarTimeoutSeconds = this.finalRaceBossbarTimeoutSeconds;
        return copy;
    }
}
