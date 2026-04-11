package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.CamUtils;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@CommandAlias("cam")
@Description("Comandos de controle de câmera para espectadores")
public class CamCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final DatabaseManager database;
    private final CamUtils camUtils;
    private final Map<UUID, Boolean> editingMode = new HashMap<>();

    public CamCommand(FormulaRacing plugin, DatabaseManager database, CamUtils camUtils) {
        this.plugin = plugin;
        this.database = database;
        this.camUtils = camUtils;
    }

    @Default
    @CatchUnknown
    @Description("Exibe a ajuda dos comandos de câmera")
    public void onHelp(Player player) {
        CommandHelpService.sendHelp(player, this, "/cam");
    }

    @Subcommand("follow")
    @CommandCompletion("@players")
    @Description("Segue um jogador específico")
    @Syntax("<jogador>")
    public void onFollow(Player player, @Flags("other") Player target) {
        if (!isSpectator(player)) return;

        // O ACF já valida se o player existe se você usar o objeto Player como argumento
        camUtils.startFollowingNormal(player, target);
        player.sendMessage("§aSeguindo §f" + target.getName() + "§a...");
    }

    @Subcommand("stop")
    @Description("Para de seguir o jogador atual")
    public void onStop(Player player) {
        if (!isSpectator(player)) return;

        if (camUtils.stopFollowingNormal(player)) {
            player.sendMessage("§aVocê parou de seguir.");
        } else {
            player.sendMessage("§cVocê não estava seguindo ninguém.");
        }
    }

    /**
     * Validação interna para garantir que o comando só rode em modo espectador
     */
    private boolean isSpectator(Player player) {
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.sendMessage("§cVocê precisa estar no modo §lEspectador §cpara usar câmeras.");
            return false;
        }
        return true;
    }
}
