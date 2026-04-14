package dev.EfraGroup.formulaRacing.Utils.Theme;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FRThemeResolver {

    public static FRTheme resolveTheme(CommandSender sender) {
        if (sender instanceof Player player) {
            return resolveTheme(player);
        }
        return FRThemeDefaults.getDefaultTheme();
    }

    public static FRTheme resolveTheme(Player player) {
        return FRThemeDefaults.getDefaultTheme();
    }
}
