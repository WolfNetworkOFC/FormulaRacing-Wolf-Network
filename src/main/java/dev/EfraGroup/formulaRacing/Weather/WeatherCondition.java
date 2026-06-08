package dev.EfraGroup.formulaRacing.Weather;

/**
 * Representa uma condição de clima com duração específica
 */
public class WeatherCondition {
    private final WeatherType weatherType;
    private final int durationLaps;  // Duração em voltas
    private final int durationSeconds; // Duração em segundos (para sessões sem voltas)

    public WeatherCondition(WeatherType weatherType, int durationLaps) {
        this.weatherType = weatherType;
        this.durationLaps = durationLaps;
        this.durationSeconds = 0;
    }

    public WeatherCondition(WeatherType weatherType, int durationLaps, int durationSeconds) {
        this.weatherType = weatherType;
        this.durationLaps = durationLaps;
        this.durationSeconds = durationSeconds;
    }

    public WeatherType getWeatherType() {
        return weatherType;
    }

    public int getDurationLaps() {
        return durationLaps;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Cria uma condição de clima a partir de uma string de configuração
     * Formato: "TIPO:VOLTAS" ou "TIPO:VOLTAS:SEGUNDOS"
     */
    public static WeatherCondition fromString(String config) {
        if (config == null || config.isEmpty()) {
            return new WeatherCondition(WeatherType.CLEAR, 999);
        }

        String[] parts = config.split(":");
        if (parts.length < 2) {
            return new WeatherCondition(WeatherType.CLEAR, 999);
        }

        try {
            WeatherType type = WeatherType.valueOf(parts[0].toUpperCase());
            int laps = Integer.parseInt(parts[1]);
            int seconds = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            return new WeatherCondition(type, laps, seconds);
        } catch (IllegalArgumentException e) {
            return new WeatherCondition(WeatherType.CLEAR, 999);
        }
    }

    @Override
    public String toString() {
        return weatherType.name() + ":" + durationLaps + (durationSeconds > 0 ? ":" + durationSeconds : "");
    }
}
