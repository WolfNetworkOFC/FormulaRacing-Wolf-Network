package dev.EfraGroup.formulaRacing.Weather;

/**
 * Tipos de clima disponíveis no sistema
 */
public enum WeatherType {
    CLEAR("Céu Limpo", 1.0, 1.0, 0),
    SUNNY("Sol", 1.0, 1.0, 0),
    SUNNY_INTENSE("Sol Intenso", 1.0, 0.95, 0),
    CLOUDY("Nublado", 0.95, 0.95, 0),
    LIGHT_RAIN("Chuva Fraca", 0.85, 0.90, 10),
    RAIN("Chuva", 0.75, 0.85, 25),
    HEAVY_RAIN("Chuva Forte", 0.65, 0.75, 40),
    STORM("Tempestade", 0.55, 0.65, 60);

    private final String displayName;
    private final double dryGripModifier;      // Modificador de grip em pista seca
    private final double wetGripModifier;       // Modificador de grip em pista molhada
    private final int wetnessAccumulation;       // Quanto rápido a pista fica molhada (0-100)

    WeatherType(String displayName, double dryGripModifier, double wetGripModifier, int wetnessAccumulation) {
        this.displayName = displayName;
        this.dryGripModifier = dryGripModifier;
        this.wetGripModifier = wetGripModifier;
        this.wetnessAccumulation = wetnessAccumulation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getDryGripModifier() {
        return dryGripModifier;
    }

    public double getWetGripModifier() {
        return wetGripModifier;
    }

    public int getWetnessAccumulation() {
        return wetnessAccumulation;
    }

    /**
     * Verifica se este tipo de clima é considerado "molhado"
     */
    public boolean isWet() {
        return this == LIGHT_RAIN || this == RAIN || this == HEAVY_RAIN || this == STORM;
    }

    /**
     * Verifica se este tipo de clima é considerado "seco"
     */
    public boolean isDry() {
        return this == CLEAR || this == SUNNY || this == SUNNY_INTENSE || this == CLOUDY;
    }

    /**
     * Obtém o modificador de grip baseado no nível de umidade da pista
     */
    public double getGripModifierForTrackWetness(int trackWetness) {
        if (trackWetness <= 0) {
            return dryGripModifier;
        }

        // Interpola entre grip seco e molhado baseado na umidade da pista
        double wetnessFraction = Math.min(1.0, trackWetness / 100.0);
        return dryGripModifier + (wetGripModifier - dryGripModifier) * wetnessFraction;
    }
}
