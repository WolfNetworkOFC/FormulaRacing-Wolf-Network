//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class FileManager {
    private final FormulaRacing plugin;
    private File configFile;
    private FileConfiguration config;

    public FileManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.setup();
        this.copyLangFiles();
    }

    private void setup() {
        if (!this.plugin.getDataFolder().exists()) {
            this.plugin.getDataFolder().mkdirs();
        }

        this.configFile = new File(this.plugin.getDataFolder(), "config.yml");
        if (!this.configFile.exists()) {
            this.plugin.saveResource("config.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(this.configFile);
    }

    public FileConfiguration getConfig() {
        return this.config;
    }

    public void saveConfig() {
        try {
            this.config.save(this.configFile);
        } catch (IOException e) {
            this.plugin.getDebugManager().logFileSystem("❌ Não foi possível salvar o config.yml: " + e.getMessage());
        }

    }

    public void reloadConfig() {
        this.config = YamlConfiguration.loadConfiguration(this.configFile);
    }

    public String getDatabaseType() {
        return this.config.getString("database.type", "sqlite");
    }

    public String getMysqlHost() {
        return this.config.getString("database.mysql.host");
    }

    public int getMysqlPort() {
        return this.config.getInt("database.mysql.port");
    }

    public String getMysqlDatabase() {
        return this.config.getString("database.mysql.database");
    }

    public String getMysqlUser() {
        return this.config.getString("database.mysql.username");
    }

    public String getMysqlPassword() {
        return this.config.getString("database.mysql.password");
    }

    public String getSQLiteFile() {
        return this.config.getString("database.sqlite.file", "formularacing.db");
    }

    public String getArchiveFile() {
        return this.config.getString("database.sqlite.archive.file", "archive.db");
    }

    public boolean isAutoCreateArchiveEnabled() {
        return this.config.getBoolean("database.sqlite.archive.auto-create-archive", true);
    }

    public String getSQLitePragma() {
        return this.config.getString("database.sqlite.pragma", "foreign_keys = ON");
    }

    private void copyLangFiles() {
        String folderName = "lang";
        String[] langFiles = new String[]{"en_US.yml", "pt_BR.yml", "pt_PT.yml"};
        File langFolder = new File(this.plugin.getDataFolder(), folderName);
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        for(String fileName : langFiles) {
            this.copyResourceIfNotExists(folderName + "/" + fileName);
        }

    }

    private void copyResourceIfNotExists(String resourcePath) {
        File outFile = new File(this.plugin.getDataFolder(), resourcePath);
        if (!outFile.exists()) {
            try {
                this.plugin.saveResource(resourcePath, false);
                this.plugin.getDebugManager().logFileSystem("✔ Arquivo copiado: " + resourcePath);
            } catch (Exception e) {
                this.plugin.getDebugManager().logFileSystem("❌ Falha ao copiar o arquivo: " + resourcePath + " (" + e.getMessage() + ")");
            }

        }
    }
}
