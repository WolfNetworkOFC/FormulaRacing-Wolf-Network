/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 *
 * Could not load the following classes:
 *  dev.EfraGroup.formulaRacing.Database.DatabaseManager
 *  org.bukkit.entity.Player
 */
package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public class TranslationUtil {
    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, String> languageCache = new ConcurrentHashMap<UUID, String>();

    public TranslationUtil(FormulaRacing plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void loadPlayerLanguage(UUID uuid) {
        String lang = this.databaseManager.getPlayerLanguage(uuid);
        this.languageCache.put(uuid, lang);
    }

    public void updatePlayerLanguage(UUID uuid, String lang) {
        this.languageCache.put(uuid, lang);
    }

    public void removePlayer(UUID uuid) {
        this.languageCache.remove(uuid);
    }

    public String getPlayerLanguage(UUID uuid) {
        return this.languageCache.getOrDefault(uuid, "en_US");
    }

    public void sendTranslated(Player player, String key, String ... placeholders) {
        String lang = this.getPlayerLanguage(player.getUniqueId());
        String message = this.plugin.getTranslation(key, lang, placeholders);
        player.sendMessage(message);
    }

    public String getTranslated(Player player, String key, String ... placeholders) {
        String lang = this.getPlayerLanguage(player.getUniqueId());
        return this.plugin.getTranslation(key, lang, placeholders);
    }

    public String getTranslated(String key, String lang, String ... placeholders) {
        return this.plugin.getTranslation(key, lang, placeholders);
    }

    public List<String> getTranslationList(Player player, String key, String ... placeholders) {
        String lang = this.getPlayerLanguage(player.getUniqueId());
        return this.plugin.getTranslationList(key, lang, placeholders);
    }
}
