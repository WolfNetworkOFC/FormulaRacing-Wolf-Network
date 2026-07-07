package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

@CommandAlias("debug|frdebug")
@CommandPermission("formularacing.admin.debug")
@Description("Manages FormulaRacing debug modes")
public class DebugCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final DebugManager debugManager;

    private static final List<String> DEBUG_FLAGS = Arrays.asList(
        "region-detection",
        "duel-system",
        "duel-system-verbose",
        "time-trial-system",
        "boat-utils",
        "database-operations",
        "event-system",
        "packet-handling",
        "performance-metrics",
        "race-system",
        "race-system-verbose",
        "qualification-system",
        "pit-stop-system",
        "spectator-system",
        "gui-system",
        "file-system"
    );

    public DebugCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.debugManager = plugin.getDebugManager();
    }

    @Subcommand("list|status|ls")
    @Description("Lists all debug modes and their current states")
    public void onList(CommandSender sender) {
        sender.sendMessage("§6=== Debug Flags ===");
        sender.sendMessage(formatFlag("Region Detection", debugManager.isRegionDetectionEnabled()));
        sender.sendMessage(formatFlag("Duel System", debugManager.isDuelSystemEnabled()));
        sender.sendMessage(formatFlag("Duel System Verbose", debugManager.isDuelSystemVerboseEnabled()));
        sender.sendMessage(formatFlag("Time Trial System", debugManager.isTimeTrialSystemEnabled()));
        sender.sendMessage(formatFlag("Boat Utils", debugManager.isBoatUtilsEnabled()));
        sender.sendMessage(formatFlag("Database Operations", debugManager.isDatabaseOperationsEnabled()));
        sender.sendMessage(formatFlag("Event System", debugManager.isEventSystemEnabled()));
        sender.sendMessage(formatFlag("Packet Handling", debugManager.isPacketHandlingEnabled()));
        sender.sendMessage(formatFlag("Performance Metrics", debugManager.isPerformanceMetricsEnabled()));
        sender.sendMessage(formatFlag("Race System", debugManager.isRaceSystemEnabled()));
        sender.sendMessage(formatFlag("Race System Verbose", debugManager.isRaceSystemVerboseEnabled()));
        sender.sendMessage(formatFlag("Qualification System", debugManager.isQualificationSystemEnabled()));
        sender.sendMessage(formatFlag("Pit Stop System", debugManager.isPitStopSystemEnabled()));
        sender.sendMessage(formatFlag("Spectator System", debugManager.isSpectatorSystemEnabled()));
        sender.sendMessage(formatFlag("GUI System", debugManager.isGuiSystemEnabled()));
        sender.sendMessage(formatFlag("File System", debugManager.isFileSystemEnabled()));
        sender.sendMessage("§6==================");
    }

    @Subcommand("enable|on")
    @Syntax("<flag|all>")
    @Description("Enables a specific debug mode or all")
    public void onEnable(CommandSender sender, String flag) {
        if (flag.equalsIgnoreCase("all")) {
            setAllDebugFlags(true);
            sender.sendMessage("§aAll debug modes have been §2ENABLED§a!");
            logDebugChange(sender, "ALL", true);
            return;
        }

        String normalizedFlag = normalizeFlag(flag);
        if (normalizedFlag == null) {
            sender.sendMessage("§cInvalid debug flag: §f" + flag);
            sender.sendMessage("§7Use §f/debug list §7to see available flags.");
            return;
        }

        setDebugFlag(normalizedFlag, true);
        sender.sendMessage("§aDebug mode §f" + normalizedFlag + " §2ENABLED§a!");
        logDebugChange(sender, normalizedFlag, true);
    }

    @Subcommand("disable|off")
    @Syntax("<flag|all>")
    @Description("Disables a specific debug mode or all")
    public void onDisable(CommandSender sender, String flag) {
        if (flag.equalsIgnoreCase("all")) {
            setAllDebugFlags(false);
            sender.sendMessage("§cAll debug modes have been §4DISABLED§c!");
            logDebugChange(sender, "ALL", false);
            return;
        }

        String normalizedFlag = normalizeFlag(flag);
        if (normalizedFlag == null) {
            sender.sendMessage("§cInvalid debug flag: §f" + flag);
            sender.sendMessage("§7Use §f/debug list §7to see available flags.");
            return;
        }

        setDebugFlag(normalizedFlag, false);
        sender.sendMessage("§cDebug mode §f" + normalizedFlag + " §4DISABLED§c!");
        logDebugChange(sender, normalizedFlag, false);
    }

    @Subcommand("toggle|t")
    @Syntax("<flag>")
    @Description("Toggles the state of a debug mode")
    public void onToggle(CommandSender sender, String flag) {
        String normalizedFlag = normalizeFlag(flag);
        if (normalizedFlag == null) {
            sender.sendMessage("§cInvalid debug flag: §f" + flag);
            sender.sendMessage("§7Use §f/debug list §7to see available flags.");
            return;
        }

        boolean currentState = getDebugFlagState(normalizedFlag);
        boolean newState = !currentState;
        setDebugFlag(normalizedFlag, newState);

        String statusMsg = newState ? "§2ENABLED" : "§4DISABLED";
        sender.sendMessage("§eDebug mode §f" + normalizedFlag + " §etoggled to " + statusMsg + "§e!");
        logDebugChange(sender, normalizedFlag, newState);
    }

    @Subcommand("reload")
    @Description("Reloads debug configurations from config.yml")
    public void onReload(CommandSender sender) {
        debugManager.reload();
        sender.sendMessage("§aDebug configurations reloaded from §fconfig.yml§a!");
        plugin.getLogger().info("[Debug] Settings reloaded by: " + sender.getName());
    }

    private String formatFlag(String name, boolean enabled) {
        String status = enabled ? "§a[ON]" : "§c[OFF]";
        return "§7- " + name + ": " + status;
    }

    private String normalizeFlag(String input) {
        String lower = input.toLowerCase().replace("_", "-");
        for (String flag : DEBUG_FLAGS) {
            if (flag.equalsIgnoreCase(lower) || flag.replace("-", "").equalsIgnoreCase(lower.replace("-", ""))) {
                return flag;
            }
        }
        return null;
    }

    private boolean getDebugFlagState(String flag) {
        return switch (flag) {
            case "region-detection" -> debugManager.isRegionDetectionEnabled();
            case "duel-system" -> debugManager.isDuelSystemEnabled();
            case "duel-system-verbose" -> debugManager.isDuelSystemVerboseEnabled();
            case "time-trial-system" -> debugManager.isTimeTrialSystemEnabled();
            case "boat-utils" -> debugManager.isBoatUtilsEnabled();
            case "database-operations" -> debugManager.isDatabaseOperationsEnabled();
            case "event-system" -> debugManager.isEventSystemEnabled();
            case "packet-handling" -> debugManager.isPacketHandlingEnabled();
            case "performance-metrics" -> debugManager.isPerformanceMetricsEnabled();
            case "race-system" -> debugManager.isRaceSystemEnabled();
            case "race-system-verbose" -> debugManager.isRaceSystemVerboseEnabled();
            case "qualification-system" -> debugManager.isQualificationSystemEnabled();
            case "pit-stop-system" -> debugManager.isPitStopSystemEnabled();
            case "spectator-system" -> debugManager.isSpectatorSystemEnabled();
            case "gui-system" -> debugManager.isGuiSystemEnabled();
            case "file-system" -> debugManager.isFileSystemEnabled();
            default -> false;
        };
    }

    private void setDebugFlag(String flag, boolean value) {
        switch (flag) {
            case "region-detection" -> debugManager.setRegionDetection(value);
            case "duel-system" -> debugManager.setDuelSystem(value);
            case "duel-system-verbose" -> debugManager.setDuelSystemVerbose(value);
            case "time-trial-system" -> debugManager.setTimeTrialSystem(value);
            case "boat-utils" -> debugManager.setBoatUtils(value);
            case "database-operations" -> debugManager.setDatabaseOperations(value);
            case "event-system" -> debugManager.setEventSystem(value);
            case "packet-handling" -> debugManager.setPacketHandling(value);
            case "performance-metrics" -> debugManager.setPerformanceMetrics(value);
            case "race-system" -> debugManager.setRaceSystem(value);
            case "race-system-verbose" -> debugManager.setRaceSystemVerbose(value);
            case "qualification-system" -> debugManager.setQualificationSystem(value);
            case "pit-stop-system" -> debugManager.setPitStopSystem(value);
            case "spectator-system" -> debugManager.setSpectatorSystem(value);
            case "gui-system" -> debugManager.setGuiSystem(value);
            case "file-system" -> debugManager.setFileSystem(value);
        }
    }

    private void setAllDebugFlags(boolean value) {
        debugManager.setRegionDetection(value);
        debugManager.setDuelSystem(value);
        debugManager.setDuelSystemVerbose(value);
        debugManager.setTimeTrialSystem(value);
        debugManager.setBoatUtils(value);
        debugManager.setDatabaseOperations(value);
        debugManager.setEventSystem(value);
        debugManager.setPacketHandling(value);
        debugManager.setPerformanceMetrics(value);
        debugManager.setRaceSystem(value);
        debugManager.setRaceSystemVerbose(value);
        debugManager.setQualificationSystem(value);
        debugManager.setPitStopSystem(value);
        debugManager.setSpectatorSystem(value);
        debugManager.setGuiSystem(value);
        debugManager.setFileSystem(value);
    }

    private void logDebugChange(CommandSender sender, String flag, boolean enabled) {
        plugin.getLogger().info("[Debug] Flag '" + flag + "' " + (enabled ? "ENABLED" : "DISABLED") + " by: " + sender.getName());
    }
}
