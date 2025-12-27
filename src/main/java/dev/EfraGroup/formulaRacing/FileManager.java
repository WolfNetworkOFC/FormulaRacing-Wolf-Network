package dev.EfraGroup.formulaRacing;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class FileManager {

    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    public FileManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setup();
        copyLangFiles(); // 🔹 Copia os arquivos de idioma ao iniciar
    }

    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("❌ Não foi possível salvar o config.yml!");
            e.printStackTrace();
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    // ==============================
    // 🔧 Métodos de Banco de Dados
    // ==============================

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getMysqlHost() {
        return config.getString("database.mysql.host");
    }

    public int getMysqlPort() {
        return config.getInt("database.mysql.port");
    }

    public String getMysqlDatabase() {
        return config.getString("database.mysql.database");
    }

    public String getMysqlUser() {
        return config.getString("database.mysql.username");
    }

    public String getMysqlPassword() {
        return config.getString("database.mysql.password");
    }

    public String getSQLiteFile() {
        return config.getString("database.sqlite.file", "formularacing.db");
    }

    public String getArchiveFile() {
        return config.getString("database.sqlite.archive.file", "archive.db");
    }

    public boolean isAutoCreateArchiveEnabled() {
        return config.getBoolean("database.sqlite.archive.auto-create-archive", true);
    }

    public String getSQLitePragma() {
        return config.getString("database.sqlite.pragma", "foreign_keys = ON");
    }

    // ===========================================
    // 🌍 SISTEMA DE LÍNGUAS (lang)
    // ===========================================

    /**
     * Copia os arquivos de idioma (lang) da pasta resources/lang/
     * para plugins/FormulaRacing/lang/, se ainda não existirem.
     */
    private void copyLangFiles() {
        String folderName = "lang";
        String[] langFiles = {"en_US.yml", "pt_BR.yml", "pt_PT.yml"};

        File langFolder = new File(plugin.getDataFolder(), folderName);
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        for (String fileName : langFiles) {
            copyResourceIfNotExists(folderName + "/" + fileName);
        }
    }

    /**
     * Copia um arquivo do resources para a pasta de dados do plugin, se ainda não existir.
     *
     * @param resourcePath Caminho dentro do resources (ex: "lang/pt_BR.yml")
     */
    private void copyResourceIfNotExists(String resourcePath) {
        File outFile = new File(plugin.getDataFolder(), resourcePath);
        if (outFile.exists()) return;

        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                plugin.getLogger().warning("⚠ Arquivo não encontrado no JAR: " + resourcePath);
                return;
            }

            Files.copy(in, outFile.toPath());
            plugin.getLogger().info("✅ Arquivo copiado: " + resourcePath);
        } catch (IOException e) {
            plugin.getLogger().severe("❌ Falha ao copiar o arquivo: " + resourcePath);
            e.printStackTrace();
        }
    }
}
