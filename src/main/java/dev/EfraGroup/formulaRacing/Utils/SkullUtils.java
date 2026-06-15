package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class SkullUtils {
    public static ItemStack createSkull(String textureValue) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (textureValue == null || textureValue.isEmpty()) {
            FormulaRacing.getInstance().getLogger().warning("[SkullUtils] Texture value is null or empty!");
            return skull;
        }
        SkullMeta meta = (SkullMeta)skull.getItemMeta();
        if (meta == null) {
            FormulaRacing.getInstance().getLogger().warning("[SkullUtils] SkullMeta is null!");
            return skull;
        }
        try {
            if (SkullUtils.applyTextureModern(meta, textureValue)) {
                skull.setItemMeta((ItemMeta)meta);
                return skull;
            }
        } catch (Exception exception) {
            // empty catch block
        }
        try {
            if (SkullUtils.applyTextureLegacy(meta, textureValue)) {
                skull.setItemMeta((ItemMeta)meta);
                return skull;
            }
        } catch (Exception e) {
            FormulaRacing.getInstance().getLogger().log(Level.WARNING, "[SkullUtils] Falha ao aplicar textura customizada: {0}", e.getMessage());
        }
        return skull;
    }

    private static boolean applyTextureModern(SkullMeta meta, String textureValue) {
        try {
            String textureUrl = SkullUtils.extractTextureUrl(textureValue);
            if (textureUrl == null) {
                return false;
            }
            Method createProfileMethod = Class.forName("org.bukkit.Bukkit").getMethod("createPlayerProfile", UUID.class, String.class);
            Object profile = createProfileMethod.invoke(null, UUID.randomUUID(), null);
            Method getTexturesMethod = profile.getClass().getMethod("getTextures", new Class[0]);
            Object textures = getTexturesMethod.invoke(profile, new Object[0]);
            URL skinUrl = URI.create(textureUrl).toURL();
            Method setSkinMethod = textures.getClass().getMethod("setSkin", URL.class);
            setSkinMethod.invoke(textures, skinUrl);
            Method setOwnerProfileMethod = meta.getClass().getMethod("setOwnerProfile", Class.forName("org.bukkit.profile.PlayerProfile"));
            setOwnerProfileMethod.invoke(meta, profile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean applyTextureLegacy(SkullMeta meta, String textureValue) {
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), null);
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", textureValue);
            Method getPropertiesMethod = profileClass.getMethod("getProperties", new Class[0]);
            Object properties = getPropertiesMethod.invoke(profile, new Object[0]);
            Method putMethod = properties.getClass().getMethod("put", Object.class, Object.class);
            putMethod.invoke(properties, "textures", property);
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static ItemStack getPlayerHead(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta)skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer((OfflinePlayer)player);
            meta.setDisplayName(String.valueOf(ChatColor.YELLOW) + player.getName());
            skull.setItemMeta((ItemMeta)meta);
        }
        return skull;
    }

    private static String extractTextureUrl(String textureValue) {
        try {
            String decoded = new String(Base64.getDecoder().decode(textureValue));
            int urlStart = decoded.indexOf("\"url\":\"") + 7;
            int urlEnd = decoded.indexOf("\"", urlStart);
            if (urlStart > 7 && urlEnd > urlStart) {
                return decoded.substring(urlStart, urlEnd);
            }
        } catch (Exception e) {
            FormulaRacing.getInstance().getLogger().log(Level.WARNING, "[SkullUtils] Erro ao decodificar textura: {0}", e.getMessage());
        }
        return null;
    }
}