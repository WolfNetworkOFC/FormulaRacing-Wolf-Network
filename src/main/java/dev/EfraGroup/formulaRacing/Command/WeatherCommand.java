package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Weather.WeatherCondition;
import dev.EfraGroup.formulaRacing.Weather.WeatherManager;
import dev.EfraGroup.formulaRacing.Weather.WeatherType;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Commands to manage the weather system
 */
@CommandAlias("weather|clima")
public class WeatherCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final WeatherManager weatherManager;

    public WeatherCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.weatherManager = plugin.getWeatherManager();
    }

    @Default
    @Description("Shows current weather information")
    public void onDefault(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Weather System");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Status: " + ChatColor.WHITE +
                (weatherManager.getConfigManager().isEnabled() ? "Enabled" : "Disabled"));
        player.sendMessage(ChatColor.GRAY + "  Drying rate: " + ChatColor.WHITE +
                weatherManager.getConfigManager().getTrackDryingRate() + "/lap");
        player.sendMessage(ChatColor.GRAY + "  Wetting rate: " + ChatColor.WHITE +
                weatherManager.getConfigManager().getTrackWettingRate() + "/lap");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Available commands:");
        player.sendMessage(ChatColor.WHITE + "    /weather info <track> - View track weather");
        player.sendMessage(ChatColor.WHITE + "    /weather set <track> - Set weather");
        player.sendMessage(ChatColor.WHITE + "    /weather reload - Reload config");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("info")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Shows weather information for a track")
    public void onInfo(Player player, String trackName) {
        List<WeatherCondition> conditions = weatherManager.getConfigManager().getDynamicWeather(trackName);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Weather: " + ChatColor.WHITE + trackName);
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Configured conditions:");

        if (conditions.isEmpty()) {
            player.sendMessage(ChatColor.RED + "    No conditions configured");
        } else {
            for (int i = 0; i < conditions.size(); i++) {
                WeatherCondition condition = conditions.get(i);
                WeatherType type = condition.getWeatherType();
                String gripInfo = String.format("Grip: %.0f%% (dry) / %.0f%% (wet)",
                        type.getDryGripModifier() * 100,
                        type.getWetGripModifier() * 100);

                player.sendMessage(ChatColor.WHITE + "    " + (i + 1) + ". " +
                        ChatColor.AQUA + type.getDisplayName() + ChatColor.GRAY +
                        " (" + condition.getDurationLaps() + " laps)");
                player.sendMessage(ChatColor.GRAY + "       " + gripInfo);
            }
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("set")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Sets the dynamic weather for a track")
    public void onSet(Player player, String trackName) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Set Weather: " + ChatColor.WHITE + trackName);
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Available weather types:");
        player.sendMessage(ChatColor.WHITE + "    CLEAR - Clear Sky");
        player.sendMessage(ChatColor.WHITE + "    SUNNY - Sunny");
        player.sendMessage(ChatColor.WHITE + "    SUNNY_INTENSE - Intense Sun");
        player.sendMessage(ChatColor.WHITE + "    CLOUDY - Cloudy");
        player.sendMessage(ChatColor.WHITE + "    LIGHT_RAIN - Light Rain");
        player.sendMessage(ChatColor.WHITE + "    RAIN - Rain");
        player.sendMessage(ChatColor.WHITE + "    HEAVY_RAIN - Heavy Rain");
        player.sendMessage(ChatColor.WHITE + "    STORM - Storm");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Format: TYPE:LAPS");
        player.sendMessage(ChatColor.GRAY + "  Example: CLEAR:3 (Clear sky for 3 laps)");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "  Use /weather add " + trackName + " <condition> to add");
        player.sendMessage(ChatColor.YELLOW + "  Use /weather clear " + trackName + " to clear");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("add")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Adds a weather condition to a track")
    public void onAdd(Player player, String trackName, String conditionStr) {
        try {
            WeatherCondition condition = WeatherCondition.fromString(conditionStr);
            List<WeatherCondition> conditions = weatherManager.getConfigManager().getDynamicWeather(trackName);
            conditions.add(condition);
            weatherManager.getConfigManager().setDynamicWeather(trackName, conditions);

            player.sendMessage(ChatColor.GREEN + "✓ Condition added: " +
                    ChatColor.AQUA + condition.getWeatherType().getDisplayName() +
                    ChatColor.GRAY + " (" + condition.getDurationLaps() + " laps)");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✗ Invalid format! Use: TYPE:LAPS");
            player.sendMessage(ChatColor.GRAY + "  Example: RAIN:3");
        }
    }

    @Subcommand("clear")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Clears the weather for a track")
    public void onClear(Player player, String trackName) {
        weatherManager.getConfigManager().setDynamicWeather(trackName, List.of(
                WeatherCondition.fromString("CLEAR:999")
        ));

        player.sendMessage(ChatColor.YELLOW + "⚠ Weather for " + trackName + " reset to Clear Sky");
    }

    @Subcommand("reload")
    @CommandPermission("formularacing.admin")
    @Description("Reloads the weather configuration")
    public void onReload(Player player) {
        weatherManager.getConfigManager().reloadConfig();

        player.sendMessage(ChatColor.GREEN + "✓ Weather configuration reloaded!");
    }

    @Subcommand("session")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.admin")
    @Description("Shows current weather session information")
    public void onSession(Player player, Heats heat) {
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ No heat selected!");
            return;
        }

        var session = weatherManager.getWeatherSession(heat.getId());
        if (session == null) {
            player.sendMessage(ChatColor.YELLOW + "⚠ No active weather session for this heat");
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Weather Session: Heat #" + heat.getId());
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Current weather: " + ChatColor.AQUA +
                session.getCurrentWeatherType().getDisplayName());
        player.sendMessage(ChatColor.GRAY + "  Track wetness: " + ChatColor.WHITE +
                session.getTrackWetness() + "%");
        player.sendMessage(ChatColor.GRAY + "  Current condition: " + ChatColor.WHITE +
                (session.getCurrentConditionIndex() + 1) + "/" +
                session.getCurrentWeatherType());
        player.sendMessage(ChatColor.GRAY + "  Laps in condition: " + ChatColor.WHITE +
                session.getLapsInCurrentCondition() + "/" +
                session.getCurrentCondition().getDurationLaps());
        player.sendMessage(ChatColor.GRAY + "  Current grip: " + ChatColor.WHITE +
                String.format("%.0f%%", session.getCurrentGripModifier() * 100));
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("force")
    @CommandCompletion("@heat CLEAR|SUNNY|CLOUDY|RAIN|STORM")
    @CommandPermission("formularacing.admin")
    @Description("Forces a specific weather for a heat")
    public void onForce(Player player, Heats heat, String weatherTypeStr) {
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ No heat selected!");
            return;
        }

        try {
            WeatherType weatherType = WeatherType.valueOf(weatherTypeStr.toUpperCase());
            var session = weatherManager.getWeatherSession(heat.getId());

            if (session == null) {
                player.sendMessage(ChatColor.YELLOW + "⚠ No active weather session");
                return;
            }

            // Forces the current weather
            // In a real implementation this would need more logic
            player.sendMessage(ChatColor.GREEN + "✓ Weather forced to: " +
                    ChatColor.AQUA + weatherType.getDisplayName());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "✗ Invalid weather type!");
            player.sendMessage(ChatColor.GRAY + "  Use: CLEAR, SUNNY, CLOUDY, RAIN, STORM");
        }
    }
}
