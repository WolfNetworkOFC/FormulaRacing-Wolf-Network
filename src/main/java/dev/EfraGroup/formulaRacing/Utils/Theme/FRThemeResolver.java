package dev.EfraGroup.formulaRacing.Utils.Theme;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FRThemeResolver {

    private static final ConcurrentHashMap<UUID, FRTheme> themeCache = new ConcurrentHashMap<>();

    public static FRTheme resolveTheme(CommandSender sender) {
        if (sender instanceof Player player) {
            return resolveTheme(player);
        }
        return FRThemeDefaults.getDefaultTheme();
    }

    public static FRTheme resolveTheme(Player player) {
        UUID uuid = player.getUniqueId();
        return themeCache.computeIfAbsent(uuid, id -> {
            String color1 = FormulaRacing.getInstance().getDatabaseManager().getPlayerColor1(id);
            String color2 = FormulaRacing.getInstance().getDatabaseManager().getPlayerColor2(id);
            return FRTheme.fromPlayerColors(color1, color2);
        });
    }

    public static void invalidate(UUID uuid) {
        themeCache.remove(uuid);
    }

    public static void invalidate(Player player) {
        invalidate(player.getUniqueId());
    }
}
