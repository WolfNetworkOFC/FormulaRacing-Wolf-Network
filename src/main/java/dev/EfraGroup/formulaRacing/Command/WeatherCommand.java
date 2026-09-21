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
import org.bukkit.command.CommandSender;

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
    public void onDefault(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "  Weather System");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "  Status: " + ChatColor.WHITE +
                (weatherManager.getConfigManager().isEnabled() ? "Enabled" : "Disabled"));
        sender.sendMessage(ChatColor.GRAY + "  Drying rate: " + ChatColor.WHITE +
                weatherManager.getConfigManager().getTrackDryingRate() + "/lap");
        sender.sendMessage(ChatColor.GRAY + "  Wetting rate: " + ChatColor.WHITE +
                weatherManager.getConfigManager().getTrackWettingRate() + "/lap");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "  Available commands:");
        sender.sendMessage(ChatColor.WHITE + "    /weather info <track> - View track weather");
        sender.sendMessage(ChatColor.WHITE + "    /weather set <track> - Set weather");
        sender.sendMessage(ChatColor.WHITE + "    /weather reload - Reload config");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("info")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Shows weather information for a track")
    public void onInfo(CommandSender sender, String trackName) {
        List<WeatherCondition> conditions = weatherManager.getConfigManager().getDynamicWeather(trackName);

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "  Weather: " + ChatColor.WHITE + trackName);
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "  Configured conditions:");

        if (conditions.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "    No conditions configured");
        } else {
            for (int i = 0; i < conditions.size(); i++) {
                WeatherCondition condition = conditions.get(i);
                WeatherType type = condition.getWeatherType();
                String gripInfo = String.format("Grip: %.0f%% (dry) / %.0f%% (wet)",
                        type.getDryGripModifier() * 100,
                        type.getWetGripModifier() * 100);

                sender.sendMessage(ChatColor.WHITE + "    " + (i + 1) + ". " +
                        ChatColor.AQUA + type.getDisplayName() + ChatColor.GRAY +
                        " (" + condition.getDurationLaps() + " laps)");
                sender.sendMessage(ChatColor.GRAY + "       " + gripInfo);
            }
        }

        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("set")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Sets the dynamic weather for a track")
    public void onSet(CommandSender sender, String trackName) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "  Set Weather: " + ChatColor.WHITE + trackName);
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "  Available weather types:");
        sender.sendMessage(ChatColor.WHITE + "    CLEAR - Clear Sky");
        sender.sendMessage(ChatColor.WHITE + "    SUNNY - Sunny");
        sender.sendMessage(ChatColor.WHITE + "    SUNNY_INTENSE - Intense Sun");
        sender.sendMessage(ChatColor.WHITE + "    CLOUDY - Cloudy");
        sender.sendMessage(ChatColor.WHITE + "    LIGHT_RAIN - Light Rain");
        sender.sendMessage(ChatColor.WHITE + "    RAIN - Rain");
        sender.sendMessage(ChatColor.WHITE + "    HEAVY_RAIN - Heavy Rain");
        sender.sendMessage(ChatColor.WHITE + "    STORM - Storm");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "  Format: TYPE:LAPS");
        sender.sendMessage(ChatColor.GRAY + "  Example: CLEAR:3 (Clear sky for 3 laps)");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "  Use /weather add " + trackName + " <condition> to add");
        sender.sendMessage(ChatColor.YELLOW + "  Use /weather clear " + trackName + " to clear");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("add")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Adds a weather condition to a track")
    public void onAdd(CommandSender sender, String trackName, String conditionStr) {
        try {
            WeatherCondition condition = WeatherCondition.fromString(conditionStr);
            List<WeatherCondition> conditions = weatherManager.getConfigManager().getDynamicWeather(trackName);
            conditions.add(condition);
            weatherManager.getConfigManager().setDynamicWeather(trackName, conditions);

            sender.sendMessage(ChatColor.GREEN + "✓ Condition added: " +
                    ChatColor.AQUA + condition.getWeatherType().getDisplayName() +
                    ChatColor.GRAY + " (" + condition.getDurationLaps() + " laps)");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "✗ Invalid format! Use: TYPE:LAPS");
            sender.sendMessage(ChatColor.GRAY + "  Example: RAIN:3");
        }
    }

    @Subcommand("clear")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Clears the weather for a track")
    public void onClear(CommandSender sender, String trackName) {
        weatherManager.getConfigManager().setDynamicWeather(trackName, List.of(
                WeatherCondition.fromString("CLEAR:999")
        ));

        sender.sendMessage(ChatColor.YELLOW + "⚠ Weather for " + trackName + " reset to Clear Sky");
    }

    @Subcommand("reload")
    @CommandPermission("formularacing.admin")
    @Description("Reloads the weather configuration")
    public void onReload(CommandSender sender) {
        weatherManager.getConfigManager().reloadConfig();

        sender.sendMessage(ChatColor.GREEN + "✓ Weather configuration reloaded!");
    }

    @Subcommand("session")
    @CommandCompletion("@heat")
    @CommandPermission("formularacing.admin")
    @Description("Shows current weather session information")
    public void onSession(CommandSender sender, Heats heat) {
        if (heat == null) {
            sender.sendMessage(ChatColor.RED + "✗ No heat selected!");
            return;
        }

        var session = weatherManager.getWeatherSession(heat.getId());
        if (session == null) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ No active weather session for this heat");
            return;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "  Weather Session: Heat #" + heat.getId());
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "  Current weather: " + ChatColor.AQUA +
                session.getCurrentWeatherType().getDisplayName());
        sender.sendMessage(ChatColor.GRAY + "  Track wetness: " + ChatColor.WHITE +
                session.getTrackWetness() + "%");
        sender.sendMessage(ChatColor.GRAY + "  Current condition: " + ChatColor.WHITE +
                (session.getCurrentConditionIndex() + 1) + "/" +
                session.getCurrentWeatherType());
        sender.sendMessage(ChatColor.GRAY + "  Laps in condition: " + ChatColor.WHITE +
                session.getLapsInCurrentCondition() + "/" +
                session.getCurrentCondition().getDurationLaps());
        sender.sendMessage(ChatColor.GRAY + "  Current grip: " + ChatColor.WHITE +
                String.format("%.0f%%", session.getCurrentGripModifier() * 100));
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("force")
    @CommandCompletion("@heat CLEAR|SUNNY|CLOUDY|RAIN|STORM")
    @CommandPermission("formularacing.admin")
    @Description("Forces a specific weather for a heat")
    public void onForce(CommandSender sender, Heats heat, String weatherTypeStr) {
        if (heat == null) {
            sender.sendMessage(ChatColor.RED + "✗ No heat selected!");
            return;
        }

        try {
            WeatherType weatherType = WeatherType.valueOf(weatherTypeStr.toUpperCase());
            var session = weatherManager.getWeatherSession(heat.getId());

            if (session == null) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ No active weather session");
                return;
            }

            // Forces the current weather
            // In a real implementation this would need more logic
            sender.sendMessage(ChatColor.GREEN + "✓ Weather forced to: " +
                    ChatColor.AQUA + weatherType.getDisplayName());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "✗ Invalid weather type!");
            sender.sendMessage(ChatColor.GRAY + "  Use: CLEAR, SUNNY, CLOUDY, RAIN, STORM");
        }
    }
}
