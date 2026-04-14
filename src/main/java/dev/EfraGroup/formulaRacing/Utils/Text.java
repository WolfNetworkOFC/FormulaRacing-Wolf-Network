package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Text {

    public static void send(CommandSender sender, String key, String... placeholders) {
        FRTheme theme = FRThemeResolver.resolveTheme(sender);
        String langCode = resolveLang(sender);
        String raw = FormulaRacing.getInstance().getTranslation(key, langCode, placeholders);
        Component component = FRThemeParser.parseWithLegacy(raw, theme);
        sendComponent(sender, component);
    }

    public static void sendComponent(CommandSender sender, Component component) {
        BaseComponent[] bungeeComponents = TextComponent.fromLegacyText(
                PlainTextComponentSerializer.plainText().serialize(component));
        if (sender instanceof Player player) {
            player.spigot().sendMessage(ChatMessageType.CHAT, bungeeComponents);
        } else {
            sender.sendMessage(PlainTextComponentSerializer.plainText().serialize(component));
        }
    }

    public static void sendComponentToPlayer(Player player, Component component) {
        BaseComponent[] bungeeComponents = TextComponent.fromLegacyText(
                PlainTextComponentSerializer.plainText().serialize(component));
        player.spigot().sendMessage(ChatMessageType.CHAT, bungeeComponents);
    }

    public static Component get(Player player, String key, String... placeholders) {
        FRTheme theme = FRThemeResolver.resolveTheme(player);
        String langCode = FormulaRacing.getInstance().getDatabaseManager().getPlayerLanguage(player.getUniqueId());
        String raw = FormulaRacing.getInstance().getTranslation(key, langCode, placeholders);
        return FRThemeParser.parseWithLegacy(raw, theme);
    }

    public static Component getRaw(String text, FRTheme theme) {
        return FRThemeParser.parseWithLegacy(text, theme);
    }

    private static String resolveLang(CommandSender sender) {
        if (sender instanceof Player p) {
            return FormulaRacing.getInstance().getDatabaseManager().getPlayerLanguage(p.getUniqueId());
        }
        return "en_US";
    }
}
