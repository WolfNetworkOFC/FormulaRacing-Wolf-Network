package dev.EfraGroup.formulaRacing.Utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Utilitário para criar cabeças de jogador com texturas customizadas
 * Compatível com Spigot/Paper 1.18+
 */
public class SkullUtils {

    /**
     * Cria uma cabeça com textura customizada a partir de um valor Base64
     * @param textureValue O valor Base64 da textura (ex: eyJ0ZXh0dXJlcy...)
     * @return ItemStack com a cabeça customizada
     */
    public static ItemStack createSkull(String textureValue) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);

        if (textureValue == null || textureValue.isEmpty()) {
            System.err.println("[SkullUtils] Texture value is null or empty!");
            return skull;
        }

        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) {
            System.err.println("[SkullUtils] SkullMeta is null!");
            return skull;
        }

        try {
            // Método 1: Tenta usar a API moderna do Paper/Spigot (1.18+)
            if (applyTextureModern(meta, textureValue)) {
                skull.setItemMeta(meta);
                return skull;
            }
        } catch (Exception e) {
            // Ignora e tenta o método legado
        }

        try {
            // Método 2: Usa GameProfile (funciona em todas as versões)
            if (applyTextureLegacy(meta, textureValue)) {
                skull.setItemMeta(meta);
                return skull;
            }
        } catch (Exception e) {
            System.err.println("[SkullUtils] Falha ao aplicar textura customizada: " + e.getMessage());
        }

        return skull;
    }

    /**
     * Aplica textura usando a API moderna do Bukkit (PlayerProfile)
     */
    private static boolean applyTextureModern(SkullMeta meta, String textureValue) {
        try {
            // Decodifica a textura Base64 para obter a URL
            String textureUrl = extractTextureUrl(textureValue);
            if (textureUrl == null) {
                return false;
            }

            // Obtém o método createPlayerProfile do Bukkit
            Method createProfileMethod = Class.forName("org.bukkit.Bukkit")
                .getMethod("createPlayerProfile", UUID.class, String.class);

            // Cria um profile com UUID aleatório
            Object profile = createProfileMethod.invoke(null, UUID.randomUUID(), null);

            // Obtém o objeto textures
            Method getTexturesMethod = profile.getClass().getMethod("getTextures");
            Object textures = getTexturesMethod.invoke(profile);

            // Define a URL da skin
            java.net.URL skinUrl = new java.net.URL(textureUrl);
            Method setSkinMethod = textures.getClass().getMethod("setSkin", java.net.URL.class);
            setSkinMethod.invoke(textures, skinUrl);

            // Aplica o profile ao SkullMeta
            Method setOwnerProfileMethod = meta.getClass().getMethod("setOwnerProfile",
                Class.forName("org.bukkit.profile.PlayerProfile"));
            setOwnerProfileMethod.invoke(meta, profile);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Aplica textura usando GameProfile (método legado mas universal)
     */
    private static boolean applyTextureLegacy(SkullMeta meta, String textureValue) {
        try {
            // Acessa as classes do Mojang AuthLib
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

            // Cria o GameProfile
            Object profile = profileClass.getConstructor(UUID.class, String.class)
                .newInstance(UUID.randomUUID(), null);

            // Cria a Property com a textura
            Object property = propertyClass.getConstructor(String.class, String.class)
                .newInstance("textures", textureValue);

            // Adiciona a property ao profile
            Method getPropertiesMethod = profileClass.getMethod("getProperties");
            Object properties = getPropertiesMethod.invoke(profile);

            // Usa put para adicionar a textura
            Method putMethod = properties.getClass().getMethod("put", Object.class, Object.class);
            putMethod.invoke(properties, "textures", property);

            // Injeta o profile no SkullMeta usando reflection
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extrai a URL da textura do valor Base64
     */
    private static String extractTextureUrl(String textureValue) {
        try {
            // Decodifica o Base64
            String decoded = new String(java.util.Base64.getDecoder().decode(textureValue));

            // Procura pela URL no JSON
            // Formato: {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/..."}}}
            int urlStart = decoded.indexOf("\"url\":\"") + 7;
            int urlEnd = decoded.indexOf("\"", urlStart);

            if (urlStart > 7 && urlEnd > urlStart) {
                return decoded.substring(urlStart, urlEnd);
            }
        } catch (Exception e) {
            System.err.println("[SkullUtils] Erro ao decodificar textura: " + e.getMessage());
        }
        return null;
    }
}

