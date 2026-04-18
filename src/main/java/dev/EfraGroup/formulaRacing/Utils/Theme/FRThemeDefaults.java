package dev.EfraGroup.formulaRacing.Utils.Theme;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.configuration.file.FileConfiguration;

public class FRThemeDefaults {
    private static FRTheme instance;

    public static void load(FormulaRacing plugin) {
        FileConfiguration config = plugin.getConfig();
        String primaryHex = config.getString("theme.primary", "#7bf200");
        String accentHex  = config.getString("theme.accent",  "#00cc99");
        instance = FRTheme.forPlayer(primaryHex, accentHex);
    }

    public static FRTheme getDefaultTheme() {
        if (instance == null) {
            instance = FRTheme.defaultTheme();
        }
        return instance;
    }
}
