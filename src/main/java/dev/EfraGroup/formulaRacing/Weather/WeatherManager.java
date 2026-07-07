package dev.EfraGroup.formulaRacing.Weather;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import org.bukkit.Bukkit;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages weather during races
 */
public class WeatherManager {

    private final FormulaRacing plugin;
    private final WeatherConfigManager configManager;
    private final Map<Integer, WeatherSession> activeSessions;
    private FRTask updateTask;

    public WeatherManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.configManager = new WeatherConfigManager(plugin);
        this.activeSessions = new HashMap<>();
    }

    /**
     * Starts a weather session for a heat
     */
    public void startWeatherSession(Heats heat) {
        if (!configManager.isEnabled()) {
            return;
        }

        String trackName = heat.getTrackNameWS();
        List<WeatherCondition> weatherConditions = configManager.getDynamicWeather(trackName);

        WeatherSession session = new WeatherSession(heat.getId(), weatherConditions);
        activeSessions.put(heat.getId(), session);

        plugin.getLogger().info("Weather session started for heat " + heat.getId() +
                " on track " + trackName + " with " + weatherConditions.size() + " conditions");
    }

    /**
     * Stops a weather session
     */
    public void stopWeatherSession(int heatId) {
        WeatherSession session = activeSessions.remove(heatId);
        if (session != null) {
            plugin.getLogger().info("Weather session stopped for heat " + heatId);
        }
    }

    /**
     * Gets the weather session for a heat
     */
    public WeatherSession getWeatherSession(int heatId) {
        return activeSessions.get(heatId);
    }

    /**
     * Updates weather for a heat (called each lap)
     */
    public void updateWeatherOnLapComplete(int heatId) {
        WeatherSession session = activeSessions.get(heatId);
        if (session != null) {
            session.advanceLap();
        }
    }

    /**
     * Starts the periodic update task
     */
    public void startUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            return;
        }

        updateTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            for (WeatherSession session : activeSessions.values()) {
                session.updateTrackWetness(configManager.getTrackWettingRate(),
                        configManager.getTrackDryingRate());
            }
        }, 20L, 20L); // Updates every second
    }

    /**
     * Stops the update task
     */
    public void stopUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /**
     * Clears all sessions
     */
    public void clearAllSessions() {
        activeSessions.clear();
    }

    /**
     * Gets the configuration manager
     */
    public WeatherConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Weather session for a specific heat
     */
    public static class WeatherSession {
        private final int heatId;
        private final List<WeatherCondition> weatherConditions;
        private int currentConditionIndex;
        private int lapsInCurrentCondition;
        private int trackWetness; // 0-100

        public WeatherSession(int heatId, List<WeatherCondition> weatherConditions) {
            this.heatId = heatId;
            this.weatherConditions = weatherConditions;
            this.currentConditionIndex = 0;
            this.lapsInCurrentCondition = 0;
            this.trackWetness = 0;
        }

        /**
         * Advances to the next weather condition
         */
        public void advanceLap() {
            lapsInCurrentCondition++;

            WeatherCondition currentCondition = getCurrentCondition();
            if (currentCondition != null && lapsInCurrentCondition >= currentCondition.getDurationLaps()) {
                // Avança para a próxima condição
                currentConditionIndex++;
                lapsInCurrentCondition = 0;

                if (currentConditionIndex >= weatherConditions.size()) {
                    currentConditionIndex = weatherConditions.size() - 1; // Stay on last condition
                }
            }
        }

        /**
         * Updates the track wetness
         */
        public void updateTrackWetness(int wettingRate, int dryingRate) {
            WeatherType currentWeather = getCurrentWeatherType();
            if (currentWeather == null) {
                return;
            }

            if (currentWeather.isWet()) {
                // Increase wetness
                trackWetness = Math.min(100, trackWetness + wettingRate);
            } else if (currentWeather.isDry()) {
                // Decrease wetness
                trackWetness = Math.max(0, trackWetness - dryingRate);
            }
        }

        /**
         * Gets the current weather condition
         */
        public WeatherCondition getCurrentCondition() {
            if (weatherConditions.isEmpty()) {
                return null;
            }
            return weatherConditions.get(currentConditionIndex);
        }

        /**
         * Gets the current weather type
         */
        public WeatherType getCurrentWeatherType() {
            WeatherCondition condition = getCurrentCondition();
            return condition != null ? condition.getWeatherType() : WeatherType.CLEAR;
        }

        /**
         * Gets the current track wetness (0-100)
         */
        public int getTrackWetness() {
            return trackWetness;
        }

        /**
         * Sets the track wetness
         */
        public void setTrackWetness(int wetness) {
            this.trackWetness = Math.max(0, Math.min(100, wetness));
        }

        /**
         * Gets the current grip modifier based on weather and track wetness
         */
        public double getCurrentGripModifier() {
            WeatherType weatherType = getCurrentWeatherType();
            if (weatherType == null) {
                return 1.0;
            }
            return weatherType.getGripModifierForTrackWetness(trackWetness);
        }

        public int getHeatId() {
            return heatId;
        }

        public int getCurrentConditionIndex() {
            return currentConditionIndex;
        }

        public int getLapsInCurrentCondition() {
            return lapsInCurrentCondition;
        }

        public int getTotalConditions() {
            return weatherConditions.size();
        }
    }
}
