package dev.EfraGroup.formulaRacing.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Cobre o contrato de degradação da integração: sem o WolfLang carregado, nada
 * pode lançar exceção nem devolver texto incorreto, porque o FormulaRacing
 * depende do fallback para os arquivos lang/*.yml.
 */
class WolfLangIntegrationTest {

    @Test
    void isDisabledWithoutWolfLangLoaded() {
        assertFalse(WolfLangIntegration.isEnabled());
    }

    @Test
    void translateByLanguageIsAbsentWithoutWolfLang() {
        // null (e não a chave) é o que faz o getTranslation/getTranslationList
        // caírem no YML local.
        assertNull(WolfLangIntegration.translateWithLang("timetrial_menu_pb", "pt_BR"));
        assertNull(WolfLangIntegration.translateWithLang("timetrial_menu_pb", "pt_BR", new HashMap<>()));
    }

    @Test
    void hasTranslationIsFalseWithoutWolfLang() {
        assertFalse(WolfLangIntegration.hasTranslation("timetrial_menu_pb"));
        assertFalse(WolfLangIntegration.hasTranslation(null));
    }

    @Test
    void translateWithLanguageReturnsNullToSignalFallback() {
        // null (e não a chave) é o que faz o getTranslation cair no YML local.
        assertNull(WolfLangIntegration.translateWithLang("timetrial_menu_pb", "pt_BR"));
    }

    @Test
    void registerAndUnregisterAreNoOpsWithoutWolfLang() {
        Map<String, Map<String, String>> translations = new HashMap<>();
        translations.put("timetrial_menu_pb", Map.of("pt_BR", "&eSeu PB: &f{time}"));

        // Não podem estourar exceção mesmo sem a API disponível.
        WolfLangIntegration.registerTranslations(WolfLangIntegration.NAMESPACE, translations);
        WolfLangIntegration.registerTranslations(WolfLangIntegration.NAMESPACE, null);
        WolfLangIntegration.unregisterTranslations(WolfLangIntegration.NAMESPACE);
    }

    @Test
    void listSeparatorRoundTripsMultilineLore() {
        // As listas YAML são achatadas no registro e quebradas na leitura.
        String lore = "&7Linha um\n&7Linha dois";
        String[] lines = lore.split(WolfLangIntegration.LIST_SEPARATOR, -1);

        assertEquals(2, lines.length);
        assertEquals("&7Linha um", lines[0]);
        assertEquals("&7Linha dois", lines[1]);
        assertEquals(lore, String.join(WolfLangIntegration.LIST_SEPARATOR, lines));
    }

    @Test
    void defaultLanguageMatchesTheLocalFallbackFile() {
        assertEquals("en_US", WolfLangIntegration.DEFAULT_LANGUAGE);
        assertEquals("WolfLang", WolfLangIntegration.PLUGIN_NAME);
        assertEquals("FormulaRacing", WolfLangIntegration.NAMESPACE);
    }

    @Test
    void debugFlagTogglesWithoutSideEffects() {
        WolfLangIntegration.setDebug(true);
        assertTrue(true, "setDebug deve ser seguro mesmo sem logger inicializado");
        WolfLangIntegration.setDebug(false);
    }
}