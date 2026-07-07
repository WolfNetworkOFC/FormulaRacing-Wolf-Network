package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@CommandAlias("openboatutils|obu")
@CommandPermission("formularacing.admin")
@Description("Commands to send OpenBoatUtils packets")
public class OpenBoatUtilsCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public OpenBoatUtilsCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    @Description("Sends an OpenBoatUtils packet to a player")
    @CommandCompletion("@players")
    public void onDefault(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§cUsage: /openboatutils <packetId> <value1> [value2] ... [player]");
            player.sendMessage("§eExemplos:");
            player.sendMessage("§f  /openboatutils 11 0.05");
            player.sendMessage("§f  /openboatutils 11 0.05 EfraMLG");
            player.sendMessage("§f  /openboatutils 3 0.5 stone,dirt");
            player.sendMessage("§f  /openboatutils 4 true");
            player.sendMessage("§f  /openboatutils 9 -0.04");
            player.sendMessage("§f  /openboatutils 26 0 0.1 minecraft:ice");
            player.sendMessage("§f  /openboatutils 27 2");
            player.sendMessage("§f  /openboatutils 34 0.5");
            player.sendMessage("§f  /openboatutils 35 2");
            player.sendMessage("§f  /openboatutils 36 1.5");
            player.sendMessage("§f  /openboatutils 40 0.8");
            player.sendMessage("§f  /openboatutils 44 true");
            player.sendMessage("§f  /openboatutils 47 true");
            return;
        }

        try {
            short packetId = Short.parseShort(args[0]);

            // Determina o alvo
            Player target;
            if (args.length > 1) {
                // Try to find a player in the last argument
                String lastArg = args[args.length - 1];
                Player potentialTarget = Bukkit.getPlayerExact(lastArg);
                if (potentialTarget != null) {
                    target = potentialTarget;
                    // Remove the player name from arguments
                    String[] newArgs = new String[args.length - 1];
                    System.arraycopy(args, 0, newArgs, 0, args.length - 1);
                    args = newArgs;
                } else {
                    target = player;
                }
            } else {
                target = player;
            }

            // Parse values
            Object[] values = parseValues(args, 1);

            // Send the packet
            if (plugin.getPacketSender() != null) {
                plugin.getPacketSender().sendBoatSetting(target, packetId, values);
                player.sendMessage("§aPacket §f" + packetId + " §asent to §f" + target.getName() + "§a.");
            } else {
                player.sendMessage("§cPacketSender is not available.");
            }

        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid packet ID. Must be a number.");
        } catch (Exception e) {
            player.sendMessage("§cErro ao enviar pacote: " + e.getMessage());
        }
    }

    @Subcommand("send")
    @Description("Sends a specific packet")
    @CommandCompletion("@players")
    public void onSend(Player player, short packetId, @co.aikar.commands.annotation.Optional Player target, String[] values) {
        Player actualTarget = target != null ? target : player;

        try {
            Object[] parsedValues = parseValues(values, 0);

            if (plugin.getPacketSender() != null) {
                plugin.getPacketSender().sendBoatSetting(actualTarget, packetId, parsedValues);
                player.sendMessage("§aPacket §f" + packetId + " §asent to §f" + actualTarget.getName() + "§a.");
            } else {
                player.sendMessage("§cPacketSender is not available.");
            }
        } catch (Exception e) {
            player.sendMessage("§cError sending packet: " + e.getMessage());
        }
    }

    @Subcommand("reset")
    @Description("Resets OpenBoatUtils settings to default")
    @CommandCompletion("@players")
    public void onReset(Player player, @co.aikar.commands.annotation.Optional Player target) {
        Player actualTarget = target != null ? target : player;

        if (plugin.getPacketSender() != null) {
            plugin.getPacketSender().resetBoatUtilsToVanilla(actualTarget);
            player.sendMessage("§aOpenBoatUtils settings reset for §f" + actualTarget.getName() + "§a.");
        } else {
            player.sendMessage("§cPacketSender is not available.");
        }
    }

    @Subcommand("list")
    @Description("Lists all available OpenBoatUtils packets")
    public void onList(Player player) {
        player.sendMessage("§e═══════════════════════════════════");
        player.sendMessage("§6§lOpenBoatUtils Packets");
        player.sendMessage("§e═══════════════════════════════════");
        player.sendMessage("§f1  §7Step Height (float)");
        player.sendMessage("§f2  §7Default Slipperiness (float)");
        player.sendMessage("§f3  §7Custom Slipperiness (float, string)");
        player.sendMessage("§f4  §7Fall Damage (boolean)");
        player.sendMessage("§f5  §7Water Elevation (boolean)");
        player.sendMessage("§f6  §7Air Control (boolean)");
        player.sendMessage("§f7  §7Jump Force (float)");
        player.sendMessage("§f9  §7Gravity (double)");
        player.sendMessage("§f10 §7Yaw Acceleration (float)");
        player.sendMessage("§f11 §7Forward Acceleration (float)");
        player.sendMessage("§f12 §7Backward Acceleration (float)");
        player.sendMessage("§f13 §7Turning Forward Acceleration (float)");
        player.sendMessage("§f14 §7Acceleration Stacking (boolean)");
        player.sendMessage("§f16 §7Underwater Control (boolean)");
        player.sendMessage("§f17 §7Surface Water Control (boolean)");
        player.sendMessage("§f19 §7Coyote Time (int)");
        player.sendMessage("§f20 §7Water Jumping (boolean)");
        player.sendMessage("§f21 §7Swim Force (float)");
        player.sendMessage("§f26 §7Per Block Setting (short, float, string)");
        player.sendMessage("§f28 §7Air Stepping (boolean)");
        player.sendMessage("§f34 §7Walltap Multiplier (float)");
        player.sendMessage("§f35 §7Jumps (int)");
        player.sendMessage("§f36 §7Scale (float)");
        player.sendMessage("§f37 §7Step Up Slipperiness (float)");
        player.sendMessage("§f41 §7Brake Slipperiness (float)");
        player.sendMessage("§f44 §7Multi Stepping (boolean)");
        player.sendMessage("§f45 §7Max Speed (float)");
        player.sendMessage("§f46 §7Max Speed Resistance (float)");
        player.sendMessage("§f47 §7Honey Compatibility (boolean)");
        player.sendMessage("§e═══════════════════════════════════");
        player.sendMessage("§eUsage: §f/openboatutils <packetId> <value1> [value2] ... [player]");
    }

    private Object[] parseValues(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return new Object[0];
        }

        Object[] values = new Object[args.length - startIndex];
        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];

            // Try boolean first
            if (arg.equalsIgnoreCase("true")) {
                values[i - startIndex] = true;
            } else if (arg.equalsIgnoreCase("false")) {
                values[i - startIndex] = false;
            }
            // Try byte
            else if (arg.matches("-?\\d{1,3}")) {
                try {
                    byte b = Byte.parseByte(arg);
                    values[i - startIndex] = b;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // Try short
            else if (arg.matches("-?\\d{1,5}")) {
                try {
                    short s = Short.parseShort(arg);
                    values[i - startIndex] = s;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // Try int
            else if (arg.matches("-?\\d+")) {
                try {
                    int intValue = Integer.parseInt(arg);
                    values[i - startIndex] = intValue;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // Try float
            else if (arg.matches("-?\\d+\\.\\d+[fF]?")) {
                try {
                    float f = Float.parseFloat(arg);
                    values[i - startIndex] = f;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // Try double
            else if (arg.matches("-?\\d+\\.\\d+[dD]?")) {
                try {
                    double d = Double.parseDouble(arg);
                    values[i - startIndex] = d;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // If not a number, keep as string
            else {
                values[i - startIndex] = arg;
            }
        }

        return values;
    }
}
