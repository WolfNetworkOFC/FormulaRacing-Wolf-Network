package dev.EfraGroup.formulaRacing.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Integração com WolfLang - Sistema de multilíngue
 * Usa reflection para não depender diretamente do WolfLang
 */
public class WolfLangIntegration {

    private static Object api;
    private static Method translateMethod;
    private static Method getLanguageMethod;
    private static Method setLanguageMethod;
    private static Method registerTranslationsMethod;
    private static boolean enabled = false;

    /**
     * Inicializa a integração com WolfLang
     */
    public static void init(Plugin plugin) {
        Plugin wolfLang = plugin.getServer().getPluginManager().getPlugin("WolfLang");
        if (wolfLang == null) {
            plugin.getLogger().info("WolfLang não encontrado. Usando sistema de tradução padrão.");
            return;
        }

        try {
            // Carrega a API do WolfLang via reflection
            Class<?> wolfLangAPI = Class.forName("dev.wolfstudios.wolflang.api.WolfLangAPI");
            Method getInstance = wolfLangAPI.getMethod("getInstance");
            api = getInstance.invoke(null);

            // Cache dos métodos
            translateMethod = wolfLangAPI.getMethod("translate", String.class, UUID.class, Map.class);
            getLanguageMethod = wolfLangAPI.getMethod("getLanguage", UUID.class);
            setLanguageMethod = wolfLangAPI.getMethod("setLanguage", UUID.class, String.class);
            registerTranslationsMethod = wolfLangAPI.getMethod("registerTranslations", String.class, Map.class);

            enabled = true;
            plugin.getLogger().info("WolfLang integrado com sucesso!");
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao integrar WolfLang: " + e.getMessage());
        }
    }

    /**
     * Verifica se WolfLang está disponível
     */
    public static boolean isEnabled() {
        return enabled && api != null;
    }

    /**
     * Traduz uma chave para o jogador
     */
    public static String translate(String key, Player player, Map<String, String> placeholders) {
        if (!enabled || api == null) return key;
        try {
            return (String) translateMethod.invoke(api, key, player.getUniqueId(), placeholders);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Traduz uma chave (sem placeholders)
     */
    public static String translate(String key, Player player) {
        return translate(key, player, new HashMap<>());
    }

    /**
     * Traduz com idioma específico
     */
    public static String translateWithLang(String key, String lang) {
        if (!enabled || api == null) return key;
        try {
            Method method = api.getClass().getMethod("translate", String.class, java.util.Locale.class);
            return (String) method.invoke(api, key, java.util.Locale.forLanguageTag(lang));
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Obtém o idioma do jogador
     */
    public static String getLanguage(Player player) {
        if (!enabled || api == null) return "en";
        try {
            return (String) getLanguageMethod.invoke(api, player.getUniqueId());
        } catch (Exception e) {
            return "en";
        }
    }

    /**
     * Define o idioma do jogador
     */
    public static void setLanguage(Player player, String language) {
        if (!enabled || api == null) return;
        try {
            setLanguageMethod.invoke(api, player.getUniqueId(), language);
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Registra traduções do plugin
     */
    public static void registerTranslations(String pluginName, Map<String, Map<String, String>> translations) {
        if (!enabled || api == null) return;
        try {
            registerTranslationsMethod.invoke(api, pluginName, translations);
        } catch (Exception e) {
            // ignore
        }
    }
}
