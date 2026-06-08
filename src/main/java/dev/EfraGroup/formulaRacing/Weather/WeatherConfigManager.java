package dev.EfraGroup.formulaRacing.Weather;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerencia o arquivo de configuração de clima
 */
public class WeatherConfigManager {

    private final FormulaRacing plugin;
    private final File configFile;
    private FileConfiguration config;

    public WeatherConfigManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "weather.yml");
        loadConfig();
    }

    /**
     * Carrega o arquivo de configuração de clima
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Cria o arquivo de configuração padrão
     */
    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();

            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(configFile);

            // Configuração padrão de clima
            defaultConfig.set("enabled", true);
            defaultConfig.set("track_drying_rate", 2); // Quanto rápido a pista seca (0-100 por volta)
            defaultConfig.set("track_wetting_rate", 5); // Quanto rápido a pista molha (0-100 por volta)

            // Clima padrão para cada pista
            defaultConfig.set("default_weather", "CLEAR:999");

            // Exemplo de clima dinâmico para uma pista
            List<String> dynamicWeather = new ArrayList<>();
            dynamicWeather.add("CLEAR:3");
            dynamicWeather.add("CLOUDY:2");
            dynamicWeather.add("LIGHT_RAIN:3");
            dynamicWeather.add("RAIN:2");
            dynamicWeather.add("HEAVY_RAIN:2");
            dynamicWeather.add("RAIN:3");
            dynamicWeather.add("LIGHT_RAIN:2");
            dynamicWeather.add("CLOUDY:2");
            dynamicWeather.add("CLEAR:999");
            defaultConfig.set("dynamic_weather.ExampleTrack", dynamicWeather);

            defaultConfig.save(configFile);
            plugin.getLogger().info("Arquivo de clima padrão criado: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao criar arquivo de clima: " + e.getMessage());
        }
    }

    /**
     * Obtém a lista de clima dinâmico para uma pista
     */
    public List<WeatherCondition> getDynamicWeather(String trackName) {
        List<WeatherCondition> conditions = new ArrayList<>();

        if (config.contains("dynamic_weather." + trackName)) {
            List<String> weatherList = config.getStringList("dynamic_weather." + trackName);
            for (String weatherStr : weatherList) {
                conditions.add(WeatherCondition.fromString(weatherStr));
            }
        } else {
            // Usa clima padrão
            conditions.add(WeatherCondition.fromString(config.getString("default_weather", "CLEAR:999")));
        }

        return conditions;
    }

    /**
     * Define o clima dinâmico para uma pista
     */
    public void setDynamicWeather(String trackName, List<WeatherCondition> conditions) {
        List<String> weatherList = new ArrayList<>();
        for (WeatherCondition condition : conditions) {
            weatherList.add(condition.toString());
        }
        config.set("dynamic_weather." + trackName, weatherList);
        saveConfig();
    }

    /**
     * Obtém a taxa de secagem da pista
     */
    public int getTrackDryingRate() {
        return config.getInt("track_drying_rate", 2);
    }

    /**
     * Obtém a taxa de molhamento da pista
     */
    public int getTrackWettingRate() {
        return config.getInt("track_wetting_rate", 5);
    }

    /**
     * Verifica se o sistema de clima está habilitado
     */
    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    /**
     * Salva a configuração
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao salvar configuração de clima: " + e.getMessage());
        }
    }

    /**
     * Recarrega a configuração
     */
    public void reloadConfig() {
        loadConfig();
    }
}
