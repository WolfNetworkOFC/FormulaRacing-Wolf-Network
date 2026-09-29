package dev.EfraGroup.formulaRacing.Weather;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the weather configuration file
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
     * Loads the weather configuration file
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Creates the default configuration file from the bundled resource
     */
    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            // Defaults vêm do recurso empacotado (fonte da verdade)
            plugin.saveResource("weather.yml", false);
            plugin.getLogger().info("Default weather file created: " + configFile.getAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating weather file: " + e.getMessage());
        }
    }

    /**
     * Gets the dynamic weather list for a track
     */
    public List<WeatherCondition> getDynamicWeather(String trackName) {
        List<WeatherCondition> conditions = new ArrayList<>();

        if (config.contains("dynamic_weather." + trackName)) {
            List<String> weatherList = config.getStringList("dynamic_weather." + trackName);
            for (String weatherStr : weatherList) {
                conditions.add(WeatherCondition.fromString(weatherStr));
            }
        } else {
            // Use default weather
            conditions.add(WeatherCondition.fromString(config.getString("default_weather", "CLEAR:999")));
        }

        return conditions;
    }

    /**
     * Sets the dynamic weather for a track
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
     * Gets the track drying rate
     */
    public int getTrackDryingRate() {
        return config.getInt("track_drying_rate", 2);
    }

    /**
     * Gets the track wetting rate
     */
    public int getTrackWettingRate() {
        return config.getInt("track_wetting_rate", 5);
    }

    /**
     * Checks if the weather system is enabled
     */
    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    /**
     * Move a sequência de clima de uma pista antiga para a nova, quando a pista
     * é renomeada. As chaves em {@code dynamic_weather} podem estar em WS ou em
     * nome de exibição, por isso a comparação ignora espaços/caixa.
     */
    public void renameDynamicWeather(String oldTrackName, String newTrackName) {
        if (oldTrackName == null || newTrackName == null) return;
        String oldNorm = oldTrackName.replaceAll("\\s+", "").toLowerCase();
        if (oldNorm.isEmpty()) return;
        String newWS = newTrackName.replaceAll("\\s+", "");

        org.bukkit.configuration.ConfigurationSection section =
                config.getConfigurationSection("dynamic_weather");
        if (section == null) return;

        boolean changed = false;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            if (key.replaceAll("\\s+", "").equalsIgnoreCase(oldNorm)) {
                List<String> list = config.getStringList("dynamic_weather." + key);
                config.set("dynamic_weather." + key, null);
                config.set("dynamic_weather." + newWS, list);
                changed = true;
            }
        }
        if (changed) saveConfig();
    }

    /**
     * Saves the configuration
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error saving weather configuration: " + e.getMessage());
        }
    }

    /**
     * Reloads the configuration
     */
    public void reloadConfig() {
        loadConfig();
    }
}
