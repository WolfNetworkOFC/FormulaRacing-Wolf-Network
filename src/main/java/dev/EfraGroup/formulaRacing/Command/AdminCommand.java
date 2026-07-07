package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@CommandAlias("admin")
@CommandPermission("formularacing.admin")
public class AdminCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final Random random = new Random();
    private final List<String> debugMessages = Arrays.asList(
            "§e[Debug] §fBedrock packet sync is stable.",
            "§e[Debug] §fGeyser detected your connection as Pocket Edition/Console.",
            "§e[Debug] §fCustom Bedrock UI rendering test started.",
            "§e[Debug] §fChecking Floodgate protocol latency..."
    );

    public AdminCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @CommandAlias("frdebug")
    @Description("Debug commands exclusive to Bedrock")
    public void onDebug(CommandSender sender) {
        // Sends translation message if configured in the plugin
        this.plugin.sendMessage(sender, "admin_debug_active");

        String randomMsg = debugMessages.get(random.nextInt(debugMessages.size()));
        int count = 0;



        // Console logs
        Bukkit.getLogger().info("[FormulaRacing] Debug sent to " + count + " Bedrock players.");
        Bukkit.getLogger().info("[FormulaRacing] Message sent: " + randomMsg);

        // Feedback for the command sender
        sender.sendMessage(ChatColor.GREEN + "Debug sent to " + ChatColor.WHITE + count + ChatColor.GREEN + " Bedrock players.");
    }
}
