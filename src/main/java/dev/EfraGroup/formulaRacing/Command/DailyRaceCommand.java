package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
import dev.EfraGroup.formulaRacing.Controllers.DailyRaceManager;
import dev.EfraGroup.formulaRacing.Controllers.DailyRaceManager.Phase;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

@CommandAlias("daily")
@CommandPermission("formularacing.admin.daily")
@Description("Comandos de Daily Race")
public class DailyRaceCommand extends BaseCommand {

    private final DailyRaceManager dailyRaceManager;

    public DailyRaceCommand(FormulaRacing plugin) {
        this.dailyRaceManager = plugin.getDailyRaceManager();
    }

    @Default
    @CatchUnknown
    public void onDefault(CommandSender sender) {
        sendHelp(sender);
    }

    @Subcommand("help|ajuda|?")
    @Description("Mostra a ajuda do comando daily")
    public void onHelp(CommandSender sender) {
        sendHelp(sender);
    }

    @Subcommand("force")
    @Description("Força o início de uma corrida diária")
    public void onForce(CommandSender sender) {
        if (dailyRaceManager.getPhase() != Phase.IDLE) {
            sender.sendMessage(ChatColor.RED + "Já existe uma Daily Race em andamento!");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Forçando o início da Daily Race...");
        dailyRaceManager.forceStart();
    }

    @Subcommand("stop|end")
    @Description("Encerra a corrida diária atual")
    public void onStop(CommandSender sender) {
        dailyRaceManager.stopDaily();
        sender.sendMessage(ChatColor.GREEN + "Daily Race encerrada com sucesso!");
    }

    @Subcommand("status")
    @Description("Mostra o status da corrida diária")
    public void onStatus(CommandSender sender) {
        Phase phase = dailyRaceManager.getPhase();
        sender.sendMessage(ChatColor.GOLD + "=== Status Daily Race ===");
        sender.sendMessage(ChatColor.GRAY + "Fase atual: " + ChatColor.WHITE + phase.name());

        dailyRaceManager.getActiveDailyEvent().ifPresentOrElse(event -> {
            sender.sendMessage(ChatColor.GRAY + "Evento Ativo: " + ChatColor.WHITE + event.getDisplayName());
            sender.sendMessage(ChatColor.GRAY + "Pista: " + ChatColor.WHITE + event.getTrackNameWS());
            sender.sendMessage(ChatColor.GRAY + "Inscritos: " + ChatColor.WHITE + event.getSubscriberCount());
        }, () -> sender.sendMessage(ChatColor.GRAY + "Nenhum evento ativo no momento."));
    }

    @Subcommand("reload")
    @Description("Recarrega as configurações")
    public void onReload(CommandSender sender) {
        dailyRaceManager.reload();
        sender.sendMessage(ChatColor.GREEN + "Configurações da Daily Race recarregadas!");
    }

    @Subcommand("skip")
    @Description("Pula a etapa atual da corrida diária")
    public void onSkip(CommandSender sender) {
        if (dailyRaceManager.getPhase() == Phase.IDLE) {
            sender.sendMessage(ChatColor.RED + "Não há uma Daily Race ativa para pular etapas.");
            return;
        }

        if (dailyRaceManager.skipPhase()) {
            sender.sendMessage(ChatColor.GREEN + "Etapa pulada com sucesso!");
        } else {
            sender.sendMessage(ChatColor.RED + "Falha ao pular etapa.");
        }
    }

    @Subcommand("exclude add")
    @Description("Exclui uma pista da Daily Race")
    @CommandCompletion("@tracks")
    public void onExcludeAdd(CommandSender sender, String trackName) {
        if (dailyRaceManager.addExcludedTrack(trackName)) {
            sender.sendMessage(ChatColor.GREEN + "Pista " + ChatColor.WHITE + trackName + ChatColor.GREEN + " adicionada à exclusão.");
        } else {
            sender.sendMessage(ChatColor.RED + "Esta pista já está na lista ou o nome é inválido.");
        }
    }

    @Subcommand("exclude remove")
    @Description("Remove uma pista da lista de exclusão")
    @CommandCompletion("@tracks")
    public void onExcludeRemove(CommandSender sender, String trackName) {
        if (dailyRaceManager.removeExcludedTrack(trackName)) {
            sender.sendMessage(ChatColor.GREEN + "Pista " + ChatColor.WHITE + trackName + ChatColor.GREEN + " removida da exclusão.");
        } else {
            sender.sendMessage(ChatColor.RED + "Esta pista não está na lista de exclusão.");
        }
    }

    @Subcommand("exclude list")
    @Description("Lista pistas excluídas")
    public void onExcludeList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Pistas Excluídas ===");
        dailyRaceManager.getExcludedTracks().forEach(t ->
                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + t));
    }

    private void sendHelp(CommandSender sender) {
        CommandHelpService.sendHelp(sender, this, "/daily");
    }
}
