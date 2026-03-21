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
import org.geysermc.geyser.api.GeyserApi;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@CommandAlias("admin|fradmin|fra")
@CommandPermission("formularacing.admin")
public class AdminCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final Random random = new Random();
    private final List<String> debugMessages = Arrays.asList(
            "§e[Debug] §fSincronização de pacotes Bedrock está estável.",
            "§e[Debug] §fGeyser detectou sua conexão como Pocket Edition/Console.",
            "§e[Debug] §fTeste de renderização de UI customizada para Bedrock iniciado.",
            "§e[Debug] §fVerificando latência do protocolo Floodgate..."
    );

    public AdminCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @CommandAlias("frdebug")
    @Description("Comandos de debug exclusivos para Bedrock")
    public void onDebug(CommandSender sender) {
        // Envia mensagem de tradução se configurada no plugin
        this.plugin.sendMessage(sender, "admin_debug_active");

        String randomMsg = debugMessages.get(random.nextInt(debugMessages.size()));
        int count = 0;

        // Itera sobre jogadores online verificando Bedrock via Geyser
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
                player.sendMessage(randomMsg);
                count++;
            }
        }

        // Logs no console
        Bukkit.getLogger().info("[FormulaRacing] Debug enviado para " + count + " jogadores Bedrock.");
        Bukkit.getLogger().info("[FormulaRacing] Mensagem enviada: " + randomMsg);

        // Feedback para o executor do comando
        sender.sendMessage(ChatColor.GREEN + "Debug enviado para " + ChatColor.WHITE + count + ChatColor.GREEN + " jogadores Bedrock.");
    }
}
