package dev.EfraGroup.formulaRacing.Utils.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class FRThemeParser {

    private static final Map<Character, NamedTextColor> LEGACY_COLORS =
        Map.ofEntries(
            Map.entry('0', NamedTextColor.BLACK),
            Map.entry('1', NamedTextColor.DARK_BLUE),
            Map.entry('2', NamedTextColor.DARK_GREEN),
            Map.entry('3', NamedTextColor.DARK_AQUA),
            Map.entry('4', NamedTextColor.DARK_RED),
            Map.entry('5', NamedTextColor.DARK_PURPLE),
            Map.entry('6', NamedTextColor.GOLD),
            Map.entry('7', NamedTextColor.GRAY),
            Map.entry('8', NamedTextColor.DARK_GRAY),
            Map.entry('9', NamedTextColor.BLUE),
            Map.entry('a', NamedTextColor.GREEN),
            Map.entry('b', NamedTextColor.AQUA),
            Map.entry('c', NamedTextColor.RED),
            Map.entry('d', NamedTextColor.LIGHT_PURPLE),
            Map.entry('e', NamedTextColor.YELLOW),
            Map.entry('f', NamedTextColor.WHITE)
        );

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

            char code = part.charAt(0);
            String content = part.length() > 1 ? part.substring(1) : "";

            TextColor themeColor = getThemeColor(Character.toLowerCase(code), theme);
            if (themeColor != null) {
                color = themeColor;
                decorations.clear();
            } else {
                switch (Character.toLowerCase(code)) {
                    case 'l' -> decorations.add(TextDecoration.BOLD);
                    case 'o' -> decorations.add(TextDecoration.ITALIC);
                    case 'u' -> decorations.add(TextDecoration.UNDERLINED);
                    case 'r' -> {
                        decorations.clear();
                        color = NamedTextColor.WHITE;
                    }
                    default -> {
                        NamedTextColor mc = LEGACY_COLORS.get(Character.toLowerCase(code));
                        if (mc != null) {
                            color = mc;
                            decorations.clear();
                        } else {
                            color = NamedTextColor.WHITE;
                            content = "&" + part;
                        }
                    }
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

    public static Component parseLegacy(String text) {
        return parseWithLegacy(text, FRThemeDefaults.getDefaultTheme());
    }

    public static Component parseWithLegacy(String text, FRTheme theme) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        TextColor currentColor = NamedTextColor.WHITE;
        List<TextDecoration> decorations = new ArrayList<>();
        Component result = Component.empty();

        int length = text.length();
        int i = 0;

        while (i < length) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < length) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                NamedTextColor mcColor = LEGACY_COLORS.get(code);
                if (mcColor != null) {
                    currentColor = mcColor;
                    decorations.clear();
                    i += 2;
                    continue;
                } else if (code == 'l') {
                    addDecoration(decorations, TextDecoration.BOLD);
                    i += 2;
                    continue;
                } else if (code == 'o') {
                    addDecoration(decorations, TextDecoration.ITALIC);
                    i += 2;
                    continue;
                } else if (code == 'n') {
                    addDecoration(decorations, TextDecoration.UNDERLINED);
                    i += 2;
                    continue;
                } else if (code == 'm') {
                    addDecoration(decorations, TextDecoration.STRIKETHROUGH);
                    i += 2;
                    continue;
                } else if (code == 'k') {
                    addDecoration(decorations, TextDecoration.OBFUSCATED);
                    i += 2;
                    continue;
                } else if (code == 'r') {
                    decorations.clear();
                    currentColor = NamedTextColor.WHITE;
                    i += 2;
                    continue;
                } else {
                    result = result.append(
                        Component.text("§").color(currentColor)
                    );
                    i++;
                    continue;
                }
            }

            if (c == '&' && i + 1 < length) {
                char code = Character.toLowerCase(text.charAt(i + 1));

                TextColor themeColor = getThemeColor(code, theme);
                if (themeColor != null) {
                    currentColor = themeColor;
                    decorations.clear();
                    i += 2;
                    continue;
                }

                if (code == 'l') {
                    addDecoration(decorations, TextDecoration.BOLD);
                    i += 2;
                    continue;
                }
                if (code == 'o') {
                    addDecoration(decorations, TextDecoration.ITALIC);
                    i += 2;
                    continue;
                }
                if (code == 'r') {
                    decorations.clear();
                    currentColor = NamedTextColor.WHITE;
                    i += 2;
                    continue;
                }

                NamedTextColor mcColor = LEGACY_COLORS.get(code);
                if (mcColor != null) {
                    currentColor = mcColor;
                    decorations.clear();
                    i += 2;
                    continue;
                }

                result = result.append(Component.text("&").color(currentColor));
                i++;
                continue;
            }

            Component built = Component.text(String.valueOf(c)).color(
                currentColor
            );
            for (TextDecoration dec : decorations) {
                built = built.decorate(dec);
            }
            result = result.append(built);
            i++;
        }

        return result;
    }

    private static TextColor getThemeColor(char code, FRTheme theme) {
        return switch (code) {
            // ── New semantic tokens ─────────────────────────────────────────
            case 'p' -> theme.getPrimary();    // &p  primary / brand (player colour 1)
            case 'a' -> theme.getAccent();     // &a  accent (player colour 2)
            case 'h' -> theme.getHeadline();   // &h  headline / scoreboard title
            case 'x' -> theme.getText();       // &x  body text
            case 'm' -> theme.getMuted();      // &m  muted / labels / separators
            case 's' -> theme.getSuccess();    // &s  success / positive
            case 'w' -> theme.getWarning();    // &w  warning / attention
            case 'e' -> theme.getError();      // &e  error / negative  (NOT legacy §e=yellow)
            case 'i' -> theme.getInfo();       // &i  info / neutral
            case 'v' -> theme.getAward();      // &v  award / podium / gold
            case 'b' -> theme.getBroadcast();  // &b  broadcast announcement  (NOT legacy §b=aqua)
            // ── Legacy aliases (kept for backwards compat) ──────────────────
            case '1' -> theme.getPrimary();    // &1  was "primary"
            case '2' -> theme.getText();       // &2  was "secondary/white" → now body text
            case 'n' -> theme.getAccent();     // &n  was "accent"
            case 't' -> theme.getMuted();      // &t  was "title/dark-gray" → now muted
            default -> null;
        };
    }

    private static void addDecoration(
        List<TextDecoration> decorations,
        TextDecoration dec
    ) {
        if (!decorations.contains(dec)) {
            decorations.add(dec);
        }
    }
}
