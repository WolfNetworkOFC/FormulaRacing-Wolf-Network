package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

public class TitleHelper {

    public static void sendThemedTitle(Player player, String titleText, String subtitleText, int fadeIn, int stay, int fadeOut) {
        FRTheme theme = FRThemeResolver.resolveTheme(player);
        String titleLegacy = titleText != null && !titleText.isBlank()
                ? serializeToLegacy(titleText, theme)
                : "";
        String subtitleLegacy = subtitleText != null && !subtitleText.isBlank()
                ? serializeToLegacy(subtitleText, theme)
                : "";
        player.sendTitle(titleLegacy, subtitleLegacy, fadeIn, stay, fadeOut);
    }

    public static void sendThemedTitle(Player player, String titleText, String subtitleText) {
        sendThemedTitle(player, titleText, subtitleText, 10, 70, 20);
    }

    private static String serializeToLegacy(String text, FRTheme theme) {
        Component component = FRThemeParser.parseWithLegacy(text, theme);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
