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
@Description("Comandos para enviar pacotes do OpenBoatUtils")
public class OpenBoatUtilsCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public OpenBoatUtilsCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    @Description("Envia um pacote do OpenBoatUtils para um jogador")
    @CommandCompletion("@players")
    public void onDefault(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§cUso: /openboatutils <packetId> <valor1> [valor2] ... [jogador]");
            player.sendMessage("§eExemplos:");
            player.sendMessage("§f  /openboatutils 11 0.05");
            player.sendMessage("§f  /openboatutils 11 0.05 EfraMLG");
            player.sendMessage("§f  /openboatutils 3 0.5 stone,dirt");
            player.sendMessage("§f  /openboatutils 4 true");
            player.sendMessage("§f  /openboatutils 9 -0.04");
            return;
        }

        try {
            short packetId = Short.parseShort(args[0]);

            // Determina o alvo
            Player target;
            if (args.length > 1) {
                // Tenta encontrar um jogador no último argumento
                String lastArg = args[args.length - 1];
                Player potentialTarget = Bukkit.getPlayerExact(lastArg);
                if (potentialTarget != null) {
                    target = potentialTarget;
                    // Remove o nome do jogador dos argumentos
                    String[] newArgs = new String[args.length - 1];
                    System.arraycopy(args, 0, newArgs, 0, args.length - 1);
                    args = newArgs;
                } else {
                    target = player;
                }
            } else {
                target = player;
            }

            // Parse os valores
            Object[] values = parseValues(args, 1);

            // Envia o pacote
            if (plugin.getPacketSender() != null) {
                plugin.getPacketSender().sendBoatSetting(target, packetId, values);
                player.sendMessage("§aPacote §f" + packetId + " §aenviado para §f" + target.getName() + "§a.");
            } else {
                player.sendMessage("§cPacketSender não está disponível.");
            }

        } catch (NumberFormatException e) {
            player.sendMessage("§cID do pacote inválido. Deve ser um número.");
        } catch (Exception e) {
            player.sendMessage("§cErro ao enviar pacote: " + e.getMessage());
        }
    }

    @Subcommand("send")
    @Description("Envia um pacote específico")
    @CommandCompletion("@players")
    public void onSend(Player player, short packetId, @co.aikar.commands.annotation.Optional Player target, String[] values) {
        Player actualTarget = target != null ? target : player;

        try {
            Object[] parsedValues = parseValues(values, 0);

            if (plugin.getPacketSender() != null) {
                plugin.getPacketSender().sendBoatSetting(actualTarget, packetId, parsedValues);
                player.sendMessage("§aPacote §f" + packetId + " §aenviado para §f" + actualTarget.getName() + "§a.");
            } else {
                player.sendMessage("§cPacketSender não está disponível.");
            }
        } catch (Exception e) {
            player.sendMessage("§cErro ao enviar pacote: " + e.getMessage());
        }
    }

    @Subcommand("reset")
    @Description("Reseta as configurações do OpenBoatUtils para o padrão")
    @CommandCompletion("@players")
    public void onReset(Player player, @co.aikar.commands.annotation.Optional Player target) {
        Player actualTarget = target != null ? target : player;

        if (plugin.getPacketSender() != null) {
            plugin.getPacketSender().resetBoatUtilsToVanilla(actualTarget);
            player.sendMessage("§aConfigurações do OpenBoatUtils resetadas para §f" + actualTarget.getName() + "§a.");
        } else {
            player.sendMessage("§cPacketSender não está disponível.");
        }
    }

    @Subcommand("list")
    @Description("Lista todos os pacotes disponíveis do OpenBoatUtils")
    public void onList(Player player) {
        player.sendMessage("§e═══════════════════════════════════");
        player.sendMessage("§6§lPacotes do OpenBoatUtils");
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
        player.sendMessage("§f27 §7Collision Mode (short)");
        player.sendMessage("§f28 §7Air Stepping (boolean)");
        player.sendMessage("§f29 §7Ten Step Interpolation (boolean)");
        player.sendMessage("§f30 §7Collision Resolution (byte)");
        player.sendMessage("§e═══════════════════════════════════");
        player.sendMessage("§eUso: §f/openboatutils <packetId> <valor1> [valor2] ... [jogador]");
    }

    private Object[] parseValues(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return new Object[0];
        }

        Object[] values = new Object[args.length - startIndex];
        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];

            // Tenta boolean primeiro
            if (arg.equalsIgnoreCase("true")) {
                values[i - startIndex] = true;
            } else if (arg.equalsIgnoreCase("false")) {
                values[i - startIndex] = false;
            }
            // Tenta byte
            else if (arg.matches("-?\\d{1,3}")) {
                try {
                    byte b = Byte.parseByte(arg);
                    values[i - startIndex] = b;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // Tenta short
            else if (arg.matches("-?\\d{1,5}")) {
                try {
                    short s = Short.parseShort(arg);
                    values[i - startIndex] = s;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // Tenta int
            else if (arg.matches("-?\\d+")) {
                try {
                    int intValue = Integer.parseInt(arg);
                    values[i - startIndex] = intValue;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // Tenta float
            else if (arg.matches("-?\\d+\\.\\d+[fF]?")) {
                try {
                    float f = Float.parseFloat(arg);
                    values[i - startIndex] = f;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // Tenta double
            else if (arg.matches("-?\\d+\\.\\d+[dD]?")) {
                try {
                    double d = Double.parseDouble(arg);
                    values[i - startIndex] = d;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            // Se não for número, mantém como string
            else {
                values[i - startIndex] = arg;
            }
        }

        return values;
    }
}
