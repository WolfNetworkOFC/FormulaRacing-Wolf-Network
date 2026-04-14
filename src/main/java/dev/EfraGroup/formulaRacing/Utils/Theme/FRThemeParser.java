package dev.EfraGroup.formulaRacing.Utils.Theme;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

public class FRThemeParser {

    public static Component parse(String text, FRTheme theme) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        TextColor color = NamedTextColor.WHITE;
        List<TextDecoration> decorations = new ArrayList<>();
        Component result = Component.empty();

        String[] parts = text.split("&", -1);
        boolean first = true;

        for (String part : parts) {
            if (part.isEmpty()) {
                first = false;
                continue;
            }

            if (first) {
                result = result.append(Component.text(part));
                first = false;
                continue;
            }

            String option = part.substring(0, 1);
            String content = part.length() > 1 ? part.substring(1) : "";

            switch (option) {
                case "1" -> color = theme.getPrimary();
                case "2" -> color = theme.getSecondary();
                case "s" -> color = theme.getSuccess();
                case "w" -> color = theme.getWarning();
                case "e" -> color = theme.getError();
                case "b" -> color = theme.getBroadcast();
                case "a" -> color = theme.getAward();
                case "t" -> color = theme.getTitle();
                case "i" -> color = theme.getInfo();
                case "n" -> color = theme.getAccent();
                case "l" -> decorations.add(TextDecoration.BOLD);
                case "o" -> decorations.add(TextDecoration.ITALIC);
                case "r" -> {
                    decorations.clear();
                    color = NamedTextColor.WHITE;
                }
                default -> {
                    color = NamedTextColor.WHITE;
                    content = "&" + part;
                }
            }

            Component built = Component.text(content).color(color);
            for (TextDecoration dec : decorations) {
                built = built.decorate(dec);
            }
            result = result.append(built);
        }

        return result;
    }
}
