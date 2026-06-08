package dev.EfraGroup.formulaRacing.Weather;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import org.bukkit.Bukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gerencia o clima durante as corridas
 */
public class WeatherManager {

    private final FormulaRacing plugin;
    private final WeatherConfigManager configManager;
    private final Map<Integer, WeatherSession> activeSessions;
    private ScheduledTask updateTask;

    public WeatherManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.configManager = new WeatherConfigManager(plugin);
        this.activeSessions = new HashMap<>();
    }

    /**
     * Inicia uma sessão de clima para um heat
     */
    public void startWeatherSession(Heats heat) {
        if (!configManager.isEnabled()) {
            return;
        }

        String trackName = heat.getTrackNameWS();
        List<WeatherCondition> weatherConditions = configManager.getDynamicWeather(trackName);

        WeatherSession session = new WeatherSession(heat.getId(), weatherConditions);
        activeSessions.put(heat.getId(), session);

        plugin.getLogger().info("Sessão de clima iniciada para heat " + heat.getId() +
                " na pista " + trackName + " com " + weatherConditions.size() + " condições");
    }

    /**
     * Para uma sessão de clima
     */
    public void stopWeatherSession(int heatId) {
        WeatherSession session = activeSessions.remove(heatId);
        if (session != null) {
            plugin.getLogger().info("Sessão de clima parada para heat " + heatId);
        }
    }

    /**
     * Obtém a sessão de clima de um heat
     */
    public WeatherSession getWeatherSession(int heatId) {
        return activeSessions.get(heatId);
    }

    /**
     * Atualiza o clima de um heat (chamado a cada volta)
     */
    public void updateWeatherOnLapComplete(int heatId) {
        WeatherSession session = activeSessions.get(heatId);
        if (session != null) {
            session.advanceLap();
        }
    }

    /**
     * Inicia a tarefa de atualização periódica
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
        }, 20L, 20L); // Atualiza a cada segundo
    }

    /**
     * Para a tarefa de atualização
     */
    public void stopUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /**
     * Limpa todas as sessões
     */
    public void clearAllSessions() {
        activeSessions.clear();
    }

    /**
     * Obtém o gerenciador de configuração
     */
    public WeatherConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Sessão de clima para um heat específico
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
         * Avança para a nova condição de clima
         */
        public void advanceLap() {
            lapsInCurrentCondition++;

            WeatherCondition currentCondition = getCurrentCondition();
            if (currentCondition != null && lapsInCurrentCondition >= currentCondition.getDurationLaps()) {
                // Avança para a próxima condição
                currentConditionIndex++;
                lapsInCurrentCondition = 0;

                if (currentConditionIndex >= weatherConditions.size()) {
                    currentConditionIndex = weatherConditions.size() - 1; // Fica na última condição
                }
            }
        }

        /**
         * Atualiza a umidade da pista
         */
        public void updateTrackWetness(int wettingRate, int dryingRate) {
            WeatherType currentWeather = getCurrentWeatherType();
            if (currentWeather == null) {
                return;
            }

            if (currentWeather.isWet()) {
                // Aumenta a umidade
                trackWetness = Math.min(100, trackWetness + wettingRate);
            } else if (currentWeather.isDry()) {
                // Diminui a umidade
                trackWetness = Math.max(0, trackWetness - dryingRate);
            }
        }

        /**
         * Obtém a condição de clima atual
         */
        public WeatherCondition getCurrentCondition() {
            if (weatherConditions.isEmpty()) {
                return null;
            }
            return weatherConditions.get(currentConditionIndex);
        }

        /**
         * Obtém o tipo de clima atual
         */
        public WeatherType getCurrentWeatherType() {
            WeatherCondition condition = getCurrentCondition();
            return condition != null ? condition.getWeatherType() : WeatherType.CLEAR;
        }

        /**
         * Obtém a umidade atual da pista (0-100)
         */
        public int getTrackWetness() {
            return trackWetness;
        }

        /**
         * Define a umidade da pista
         */
        public void setTrackWetness(int wetness) {
            this.trackWetness = Math.max(0, Math.min(100, wetness));
        }

        /**
         * Obtém o modificador de grip atual baseado no clima e umidade da pista
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
