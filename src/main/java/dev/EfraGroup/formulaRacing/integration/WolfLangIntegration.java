package dev.EfraGroup.formulaRacing.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Integração com WolfLang - Sistema de multilíngue
 * Usa reflection para não depender diretamente do WolfLang
 */
public class WolfLangIntegration {

    /** Nome do plugin externo procurado no servidor. */
    public static final String PLUGIN_NAME = "WolfLang";

    /** Namespace usado para registrar as traduções do FormulaRacing. */
    public static final String NAMESPACE = "FormulaRacing";

    /** Classe da API pública do WolfLang. */
    private static final String API_CLASS = "dev.wolfstudios.wolflang.api.WolfLangAPI";

    /**
     * Separador usado para achatar listas YAML em uma string só, porque a API do
     * WolfLang trabalha com valores escalares. Na leitura a string é quebrada
     * novamente em lista.
     */
    public static final String LIST_SEPARATOR = "\n";

    /** Idioma usado quando o WolfLang está ativo mas não devolve nenhum valor. */
    public static final String DEFAULT_LANGUAGE = "en_US";

    private static Object api;
    private static Method translateMethod;
    private static Method getLanguageMethod;
    private static Method setLanguageMethod;
    private static Method registerTranslationsMethod;
    private static Method hasTranslationMethod;
    private static Method unregisterTranslationsMethod;

    /**
     * Tradução por idioma ({@code translate(String, Locale)} e variantes).
     * O WolfLang pode expor a assinatura com {@link Locale} ou com {@link String},
     * com ou sem mapa de placeholders; resolvemos a primeira que existir e
     * reutilizamos em todas as chamadas.
     */
    private static Method translateLangMethod;
    private static Class<?> translateLangType;
    private static boolean translateLangTakesMap;

    private static boolean enabled = false;
    private static boolean debug = false;
    private static Logger logger;

    /**
     * Habilita o log detalhado das falhas de reflection.
     */
    public static void setDebug(boolean value) {
        debug = value;
    }

    private static void debug(String message, Throwable error) {
        if (debug && logger != null) {
            // INFO e não FINE: o logger do Bukkit filtra FINE por padrão, então
            // o usuário que ligou debug.wolflang não veria nada.
            logger.log(Level.INFO, "[WolfLang] " + message, error);
        }
    }

    /**
     * Inicializa a integração com WolfLang.
     * <p>Deve ser chamado no {@code onEnable} do FormulaRacing. O plugin precisa
     * estar declarado em {@code softdepend} para o Bukkit garantir a ordem de
     * carregamento.</p>
     */
    public static void init(Plugin plugin) {
        logger = plugin.getLogger();
        enabled = false;
        api = null;
        translateMethod = null;
        getLanguageMethod = null;
        setLanguageMethod = null;
        registerTranslationsMethod = null;
        hasTranslationMethod = null;
        unregisterTranslationsMethod = null;
        translateLangMethod = null;
        translateLangType = null;
        translateLangTakesMap = false;

        Plugin wolfLang = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (wolfLang == null || !wolfLang.isEnabled()) {
            plugin.getLogger().info("WolfLang não encontrado. Usando sistema de tradução padrão.");
            return;
        }

        try {
            // Carrega a API do WolfLang via reflection
            Class<?> wolfLangAPI = Class.forName(API_CLASS);
            api = wolfLangAPI.getMethod("getInstance").invoke(null);

            if (api == null) {
                plugin.getLogger().warning("WolfLang: getInstance() retornou null. Integração desativada.");
                return;
            }

            // Métodos obrigatórios: sem eles não há como traduzir.
            translateMethod = requireMethod(wolfLangAPI, "translate", String.class, UUID.class, Map.class);
            getLanguageMethod = requireMethod(wolfLangAPI, "getLanguage", UUID.class);
            setLanguageMethod = requireMethod(wolfLangAPI, "setLanguage", UUID.class, String.class);
            registerTranslationsMethod = requireMethod(wolfLangAPI, "registerTranslations", String.class, Map.class);

            // Métodos opcionais: versões antigas do WolfLang podem não expor.
            hasTranslationMethod = optionalMethod(wolfLangAPI, "hasTranslation", String.class);
            unregisterTranslationsMethod = optionalMethod(wolfLangAPI, "unregisterTranslations", String.class);
            resolveLangTranslate(wolfLangAPI);

            enabled = true;
            plugin.getLogger().info("WolfLang integrado com sucesso!");
            plugin.getLogger().info("WolfLang: tradução por idioma "
                + (translateLangMethod == null ? "indisponível" : "disponível")
                + (hasTranslationMethod == null ? " | hasTranslation ausente" : ""));
        } catch (Exception e) {
            enabled = false;
            api = null;
            plugin.getLogger().warning("Erro ao integrar WolfLang: " + e);
            debug("falha ao inicializar", e);
        }
    }

    private static Method requireMethod(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        Method method = owner.getMethod(name, params);
        method.setAccessible(true);
        return method;
    }

    private static Method optionalMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method method = owner.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            debug("método opcional ausente: " + name, e);
            return null;
        }
    }

    /**
     * Procura a assinatura de tradução por idioma compatível com esta versão do
     * WolfLang. Tenta {@code Locale} antes de {@code String} e a variante com
     * mapa de placeholders antes da simplificada.
     */
    private static void resolveLangTranslate(Class<?> apiClass) {
        translateLangMethod = null;
        translateLangType = null;
        translateLangTakesMap = false;

        Class<?>[][] candidates = {
            {Locale.class, Map.class},
            {String.class, Map.class},
            {Locale.class},
            {String.class}
        };

        for (Class<?>[] extra : candidates) {
            Class<?>[] params = new Class<?>[extra.length + 1];
            params[0] = String.class;
            System.arraycopy(extra, 0, params, 1, extra.length);

            Method method = optionalMethod(apiClass, "translate", params);
            if (method == null) {
                continue;
            }

            translateLangMethod = method;
            translateLangType = extra[0];
            translateLangTakesMap = extra.length > 1;
            return;
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
        if (!enabled || api == null || player == null) return key;
        try {
            return (String) translateMethod.invoke(api, key, player.getUniqueId(), placeholders);
        } catch (Exception e) {
            debug("falha em translate para " + key, e);
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
     * Traduz com idioma específico, usando a assinatura {@code translate} que
     * aceita {@link Locale} ou {@link String}.
     *
     * @return o texto traduzido, ou {@code null} quando a integração está
     *         desativada, não há esse método disponível, ou a chave não existe.
     */
    public static String translateWithLang(String key, String lang, Map<String, String> placeholders) {
        if (!enabled || api == null || translateLangMethod == null || key == null) return null;

        Object langArg = translateLangType == Locale.class ? Locale.forLanguageTag(normalizeTag(lang)) : lang;
        Object result;
        try {
            result = translateLangTakesMap
                ? translateLangMethod.invoke(api, key, langArg, placeholders)
                : translateLangMethod.invoke(api, key, langArg);
        } catch (Exception e) {
            debug("falha em translateWithLang para " + key, e);
            return null;
        }

        if (!(result instanceof String)) {
            return null;
        }

        String value = (String) result;
        return value.equals(key) ? null : value;
    }

    /**
     * Variante sem placeholders de {@link #translateWithLang(String, String, Map)}.
     */
    public static String translateWithLang(String key, String lang) {
        return translateWithLang(key, lang, new HashMap<>());
    }

    /**
     * Converte "pt_BR" em "pt-BR", formato aceito por {@link Locale#forLanguageTag}.
     */
    private static String normalizeTag(String lang) {
        if (lang == null || lang.isEmpty()) {
            return "en";
        }
        return lang.replace('_', '-');
    }

    /**
     * Obtém o idioma do jogador
     */
    public static String getLanguage(Player player) {
        if (!enabled || api == null || player == null) return DEFAULT_LANGUAGE;
        try {
            String lang = (String) getLanguageMethod.invoke(api, player.getUniqueId());
            return lang == null || lang.isEmpty() ? DEFAULT_LANGUAGE : lang;
        } catch (Exception e) {
            debug("falha em getLanguage", e);
            return DEFAULT_LANGUAGE;
        }
    }

    /**
     * Define o idioma do jogador
     */
    public static void setLanguage(Player player, String language) {
        if (!enabled || api == null || player == null || language == null) return;
        try {
            setLanguageMethod.invoke(api, player.getUniqueId(), language);
        } catch (Exception e) {
            debug("falha em setLanguage", e);
        }
    }

    /**
     * Registra traduções do plugin
     */
    public static void registerTranslations(String pluginName, Map<String, Map<String, String>> translations) {
        if (!enabled || api == null || translations == null || translations.isEmpty()) return;
        try {
            registerTranslationsMethod.invoke(api, pluginName, translations);
        } catch (Exception e) {
            debug("falha em registerTranslations", e);
        }
    }

    /**
     * Verifica se uma chave de tradução existe no WolfLang.
     * <p>Quando a API não expõe {@code hasTranslation}, assume {@code true} e
     * deixa a checagem final por igualdade com a chave decidir.</p>
     */
    public static boolean hasTranslation(String key) {
        if (!enabled || api == null || key == null) return false;
        if (hasTranslationMethod == null) return true;
        try {
            Object result = hasTranslationMethod.invoke(api, key);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            debug("falha em hasTranslation para " + key, e);
            return true;
        }
    }

    /**
     * Remove as traduções do plugin (usar no onDisable)
     */
    public static void unregisterTranslations(String pluginName) {
        if (!enabled || api == null || unregisterTranslationsMethod == null) return;
        try {
            unregisterTranslationsMethod.invoke(api, pluginName);
        } catch (Exception e) {
            debug("falha em unregisterTranslations", e);
        }
    }
}
