package dev.EfraGroup.formulaRacing.Config;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public class PitStopConfigManager {
    private final FormulaRacing plugin;
    private final File configFile;
    private FileConfiguration yaml;
    private final Map<String, PitConfig> cache = new HashMap();
    private static final String DEFAULT_KEY = "default";

    public PitStopConfigManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "pitstop_config.yml");
        this.load();
    }

    private void load() {
        if (!this.configFile.exists()) {
            try {
                this.configFile.getParentFile().mkdirs();
                this.configFile.createNewFile();
            } catch (IOException e) {
                this.plugin.getDebugManager().logPitStopSystem("[PitStopConfig] Could not create pitstop_config.yml: " + e.getMessage());
            }
        }

        this.yaml = YamlConfiguration.loadConfiguration(this.configFile);
        this.addDefaults();
        this.yaml.options().copyDefaults(true);
        this.save();
        this.reloadCache();
    }

    private void addDefaults() {
        if (!this.yaml.isConfigurationSection("default")) {
            this.yaml.set("default.inventory_size", 27);
            this.yaml.set("default.min_targets", 5);
            this.yaml.set("default.max_targets", 9);
            this.yaml.set("default.target_items", Collections.singletonList(new ItemStack(Material.REDSTONE)));
            this.yaml.set("default.wrong_item", new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            this.yaml.set("default.shuffle_on_error", true);
        }

    }

    public void save() {
        try {
            this.yaml.save(this.configFile);
        } catch (IOException e) {
            this.plugin.getDebugManager().logPitStopSystem("[PitStopConfig] Failed to save pitstop_config.yml: " + e.getMessage());
        }

    }

    private void reloadCache() {
        for(String key : this.yaml.getKeys(false)) {
            ConfigurationSection section = this.yaml.getConfigurationSection(key);
            if (section != null) {
                ItemStack wrongItem;
                if (section.isItemStack("wrong_item")) {
                    wrongItem = section.getItemStack("wrong_item");
                } else {
                    String matName = section.getString("wrong_item", "GRAY_STAINED_GLASS_PANE");
                    Material mat = Material.matchMaterial(matName);
                    if (mat == null) {
                        mat = Material.GRAY_STAINED_GLASS_PANE;
                    }

                    wrongItem = new ItemStack(mat);
                }

                List<ItemStack> targetItems = new ArrayList();
                if (section.contains("target_items")) {
                    List<?> rawList = section.getList("target_items");
                    if (rawList != null) {
                        for(Object obj : rawList) {
                            if (obj instanceof ItemStack) {
                                targetItems.add((ItemStack)obj);
                            } else if (obj instanceof String) {
                                Material mat = Material.matchMaterial((String)obj);
                                if (mat != null) {
                                    targetItems.add(new ItemStack(mat));
                                }
                            } else if (obj instanceof Map) {
                                try {
                                    targetItems.add(ItemStack.deserialize((Map)obj));
                                } catch (Exception e) {
                                    this.plugin.getDebugManager().logPitStopSystem("[PitConfig] Erro ao deserializar item em " + key + ": " + e.getMessage());
                                }
                            }
                        }
                    }
                }

                if (targetItems.isEmpty()) {
                    String matName = section.getString("target_item", "REDSTONE");
                    Material mat = Material.matchMaterial(matName);
                    if (mat != null) {
                        targetItems.add(new ItemStack(mat));
                    }
                }

                if (targetItems.isEmpty()) {
                    targetItems.add(new ItemStack(Material.REDSTONE));
                }

                PitConfig cfg = new PitConfig(section.getInt("inventory_size", 27), section.getInt("min_targets", 5), section.getInt("max_targets", 9), targetItems, wrongItem, section.getBoolean("shuffle_on_error", true));
                this.cache.put(key.toLowerCase(), cfg);
                this.plugin.getDebugManager().logPitStopSystem("[PitConfig] Loaded config for '" + key + "' with " + targetItems.size() + " targets.");
            }
        }

    }

    public boolean updateConfig(String track, String field, String value) {
        String key = track == null ? "default" : track.toLowerCase();
        ConfigurationSection section = this.yaml.getConfigurationSection(key);
        if (section == null) {
            section = this.yaml.createSection(key);
        }

        switch (field.toLowerCase()) {
            case "inventory_size":
                section.set(field, Integer.parseInt(value));
                break;
            case "min_targets":
            case "max_targets":
                section.set(field, Integer.parseInt(value));
                break;
            case "target_item":
            case "wrong_item":
                if (Material.matchMaterial(value.toUpperCase()) == null) {
                    return false;
                }

                section.set(field, value.toUpperCase());
                break;
            case "shuffle_on_error":
                section.set(field, Boolean.parseBoolean(value));
                break;
            default:
                return false;
        }

        this.save();
        this.reloadCache();
        return true;
    }

    public void setTargetItems(String track, List<ItemStack> items) {
        String key = track == null ? "default" : track.toLowerCase();
        ConfigurationSection section = this.yaml.getConfigurationSection(key);
        if (section == null) {
            section = this.yaml.createSection(key);
        }

        section.set("target_items", items);
        section.set("target_item", (Object)null);
        this.save();
        this.reloadCache();
    }

    public void setWrongItem(String track, ItemStack item) {
        String key = track == null ? "default" : track.toLowerCase();
        ConfigurationSection section = this.yaml.getConfigurationSection(key);
        if (section == null) {
            section = this.yaml.createSection(key);
        }

        section.set("wrong_item", item);
        this.save();
        this.reloadCache();
    }

    public PitConfig getConfig(String trackNameWS) {
        return trackNameWS == null ? (PitConfig)this.cache.get("default") : (PitConfig)this.cache.getOrDefault(trackNameWS.toLowerCase(), (PitConfig)this.cache.get("default"));
    }

    public static record PitConfig(int inventorySize, int minTargets, int maxTargets, List<ItemStack> targetItems, ItemStack wrongItem, boolean shuffleOnError) {
    }
}
