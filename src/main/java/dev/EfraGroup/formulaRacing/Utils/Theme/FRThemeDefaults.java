package dev.EfraGroup.formulaRacing.Utils.Theme;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;

public class FRThemeDefaults {
    private static FRTheme instance;

    public static void load(FormulaRacing plugin) {
        FileConfiguration config = plugin.getConfig();
        instance = new FRTheme(
                parseHex(config.getString("theme.primary", "#7bf200")),
                parseHex(config.getString("theme.secondary", "#ffffff")),
                parseHex(config.getString("theme.success", "#7bf200")),
                parseHex(config.getString("theme.warning", "#ffff00")),
                parseHex(config.getString("theme.error", "#ff7a75")),
                parseHex(config.getString("theme.broadcast", "#00ffff")),
                parseHex(config.getString("theme.award", "#ffd700")),
                parseHex(config.getString("theme.title", "#555555")),
                parseHex(config.getString("theme.info", "#cc99ff")),
                parseHex(config.getString("theme.accent", "#00cc99"))
        );
    }

    public static FRTheme getDefaultTheme() {
        if (instance == null) {
            instance = FRTheme.defaultTheme();
        }
        return instance;
    }

    private static TextColor parseHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return TextColor.color(0x7bf200);
        }
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return TextColor.fromHexString("#" + clean);
        } catch (IllegalArgumentException e) {
            return TextColor.color(0x7bf200);
        }
    }
}
