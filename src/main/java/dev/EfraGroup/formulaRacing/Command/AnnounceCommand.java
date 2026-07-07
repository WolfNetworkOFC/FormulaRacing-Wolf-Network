package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.annotation.*;
import co.aikar.commands.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

@CommandAlias("announce|anuncio") // Defines the command and an optional alias
public class AnnounceCommand extends BaseCommand {

    @Default // Defines that this method will run when using /announce directly
    @CommandPermission("formularacing.admin") // ACF handles permission automatically
    @Description("Sends a global announcement to the entire server.")
    @Syntax("<message>") // Automatic error message if the argument is missing
    public void onAnnounce(CommandSender sender, String message) {

        // ACF already does String.join automatically if the last parameter is String

        Bukkit.broadcastMessage("§6=============== §c§lAnnouncement §6===============");
        Bukkit.broadcastMessage("§f" + message.replace("&", "§")); // Allows colors in the announcement
        Bukkit.broadcastMessage("§6=======================================");
    }
}