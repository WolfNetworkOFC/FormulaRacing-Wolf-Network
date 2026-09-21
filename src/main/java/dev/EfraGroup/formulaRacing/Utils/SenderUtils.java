package dev.EfraGroup.formulaRacing.Utils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Helpers for command handlers that may be invoked from the console.
 *
 * <p>Commands that do not require a physical player (configuration, management,
 * read-only info) should accept a {@link CommandSender} and use the helpers here
 * to obtain a {@link Player} only when one is actually available.</p>
 */
public final class SenderUtils {

    private SenderUtils() {
    }

    /**
     * @return the sender as a {@link Player}, or {@code null} when the command
     * was executed from the console.
     */
    public static Player player(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

    /**
     * Guards a block that genuinely needs a player (GUI, teleport, inventory).
     *
     * @return {@code true} when the sender is a player; otherwise sends a short
     * error to the sender and returns {@code false}.
     */
    public static boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        sender.sendMessage("§cThis command must be run by a player.");
        return false;
    }
}
