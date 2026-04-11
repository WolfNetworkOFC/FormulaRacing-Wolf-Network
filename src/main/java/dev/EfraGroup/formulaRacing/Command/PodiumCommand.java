package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("podium")
@CommandPermission("formularacing.event.admin")
public class PodiumCommand extends BaseCommand {
    private final FormulaRacing plugin;

    public PodiumCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    @Description("Mostra o status da configuracao de podio")
    public void onDefault(CommandSender sender) {
        this.onShow(sender);
    }

    @Subcommand("set")
    @CommandCompletion("audience|p1|p2|p3|lobby")
    @Description("Define uma posicao do podio usando sua localizacao atual")
    public void onSet(Player player, String slot) {
        String normalized = slot == null ? "" : slot.toLowerCase();
        if (!normalized.equals("audience") && !normalized.equals("p1") && !normalized.equals("p2") && !normalized.equals("p3") && !normalized.equals("lobby")) {
            player.sendMessage(ChatColor.RED + "Slot invalido. Use: audience, p1, p2, p3 ou lobby.");
            return;
        }

        boolean saved = this.plugin.getPodiumManager().setLocation(normalized, player.getLocation());
        if (!saved) {
            player.sendMessage(ChatColor.RED + "Nao foi possivel salvar a localizacao de '" + normalized + "'.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Localizacao de '" + normalized + "' salva em config.yml.");
    }

    @Subcommand("show")
    @Description("Mostra as configuracoes atuais do podio")
    public void onShow(CommandSender sender) {
        for (String line : this.plugin.getPodiumManager().getConfigStatusLines()) {
            sender.sendMessage(line);
        }
    }

    @Subcommand("reload")
    @Description("Recarrega as configuracoes do podio")
    public void onReload(CommandSender sender) {
        this.plugin.getPodiumManager().reloadConfiguration();
        sender.sendMessage(ChatColor.GREEN + "Configuracao de podio recarregada.");
    }
}
