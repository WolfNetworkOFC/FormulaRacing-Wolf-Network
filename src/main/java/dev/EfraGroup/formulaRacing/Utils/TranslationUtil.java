package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.integration.WolfLangIntegration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TranslationUtil {
    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, String> languageCache = new ConcurrentHashMap<UUID, String>();

    public TranslationUtil(FormulaRacing plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Carrega o idioma do jogador ao entrar.
     * <p>Quando o WolfLang está ativo ele é a fonte da verdade (pode ter sido
     * alterado fora do FormulaRacing, por exemplo por um comando do próprio
     * WolfLang). O banco continua sendo o fallback permanente.</p>
     */
    public void loadPlayerLanguage(UUID uuid) {
        String lang = this.databaseManager.getPlayerLanguage(uuid);

        if (WolfLangIntegration.isEnabled()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String wolfLang = WolfLangIntegration.getLanguage(player);
                if (wolfLang != null && this.plugin.hasLangFile(wolfLang)) {
                    lang = wolfLang;
                }
            }
        }

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

    /**
     * Sender-aware overload: uses the player's language when available, otherwise
     * falls back to the default language so console invocations still get text.
     */
    public String getTranslated(CommandSender sender, String key, String ... placeholders) {
        String lang = sender instanceof Player player ? this.getPlayerLanguage(player.getUniqueId()) : "en_US";
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
