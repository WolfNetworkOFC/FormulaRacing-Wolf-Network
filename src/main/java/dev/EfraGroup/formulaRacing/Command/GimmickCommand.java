package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.GimmickConfig;
import dev.EfraGroup.formulaRacing.Heat.GimmickException;
import dev.EfraGroup.formulaRacing.Heat.GimmickManager;
import dev.EfraGroup.formulaRacing.Heat.GimmickSchematics;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.SenderUtils;
import java.util.List;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Admin command for the gimmicks: an admin copies a build with //copy, runs
 * {@code /gimmick save <nome> <pista> [-a]} standing where it must be pasted,
 * and then schedules it in a heat with {@code /heat set gimmick add <nome> <lap>}.
 */
@CommandAlias("gimmick|gm")
@CommandPermission("formularacing.admin")
public class GimmickCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final GimmickManager gimmickManager;

    public GimmickCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.gimmickManager = plugin.getGimmickManager();
    }

    @Default
    @Description("Mostra os comandos do sistema de gimmicks")
    public void onDefault(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "  Sistema de Gimmicks");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "  Faça //copy na área que quer colar e fique");
        sender.sendMessage(ChatColor.GRAY + "  parado onde a gimmick deve aparecer.");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "  /gimmick save <nome> <pista> [-a]");
        sender.sendMessage(ChatColor.GRAY + "    Salva o clipboard na sua posição.");
        sender.sendMessage(ChatColor.GRAY + "    -a: cola o ar junto (senão o ar é ignorado).");
        sender.sendMessage(ChatColor.WHITE + "  /gimmick list [pista]");
        sender.sendMessage(ChatColor.WHITE + "  /gimmick gui [pista] - menu de gimmicks");
        sender.sendMessage(ChatColor.WHITE + "  /gimmick paste <nome> [pista] - cola agora (teste)");
        sender.sendMessage(ChatColor.WHITE + "  /gimmick toggle <nome> [pista] - ativa/desativa");
        sender.sendMessage(ChatColor.WHITE + "  /gimmick setmessage <nome> [pista] <mensagem>");
        sender.sendMessage(ChatColor.WHITE + "  /gimmick remove <nome> [pista]");
        sender.sendMessage(ChatColor.WHITE + "  /gimmick restore - desfaz tudo que está colado");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "  /heat set gimmick add <nome> <volta>");
        sender.sendMessage(ChatColor.WHITE + "  /heat set gimmick remove <nome>");
        sender.sendMessage(ChatColor.WHITE + "  /heat set gimmick list");
        sender.sendMessage(ChatColor.WHITE + "  /heat set gimmick clear");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("save")
    @Syntax("<nome> <pista> [-a]")
    @CommandCompletion("<nome> @tracks -a")
    @Description("Salva o clipboard do WorldEdit como gimmick da pista, na sua posição")
    public void onSave(Player player, String[] args) {
        boolean withAir = false;
        int end = args.length;
        if (end > 0 && isAirFlag(args[end - 1])) {
            withAir = true;
            end--;
        }

        if (end != 2) {
            player.sendMessage(ChatColor.RED + "✗ Uso: /gimmick save <nome> <pista> [-a]");
            return;
        }

        String name = args[0];
        String track = args[1];

        try {
            GimmickConfig gimmick = gimmickManager.saveFromClipboard(player, name, track, withAir);
            player.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + gimmick.getName() + "' salva na pista '" +
                gimmick.getTrackNameWS() + "'.");
            player.sendMessage(ChatColor.GRAY + "  Posição: " + formatLocation(player));
            player.sendMessage(ChatColor.GRAY + "  Colagem: " +
                (withAir ? "com o ar do schematic (-a)" : "sem o ar do schematic"));
            player.sendMessage(ChatColor.GRAY + "  Agende com: /heat set gimmick add " +
                gimmick.getName() + " <volta>");
            if (GimmickSchematics.hasPendingTransform(player)) {
                player.sendMessage(ChatColor.YELLOW +
                    "⚠ O clipboard estava rotacionado/invertido e isso NÃO é salvo: " +
                    "faça //copy de novo (já com a seleção como quer colar) e salve de novo.");
            }
        } catch (GimmickException e) {
            player.sendMessage(ChatColor.RED + "✗ " + e.getMessage());
        }
    }

    @Subcommand("list")
    @Syntax("[pista]")
    @CommandCompletion("@tracks")
    @Description("Lista as gimmicks salvas de uma pista")
    public void onList(CommandSender sender, @Optional String track) {
        String trackName = resolveTrack(sender, track);
        if (trackName == null) {
            sender.sendMessage(ChatColor.RED + "✗ Informe a pista: /gimmick list <pista>");
            return;
        }

        List<GimmickConfig> gimmicks = gimmickManager.getGimmicksForTrack(trackName);
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "  Gimmicks de " + trackName);
        sender.sendMessage("");

        if (gimmicks.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  Nenhuma gimmick salva nessa pista.");
        } else {
            for (GimmickConfig gimmick : gimmicks) {
                String status = gimmick.isEnabled() ? ChatColor.GREEN + "●" : ChatColor.RED + "●";
                sender.sendMessage(status + " " + ChatColor.WHITE + gimmick.getName() +
                    ChatColor.GRAY + " | " + (gimmick.isPasteWithAir() ? "com ar" : "sem ar"));
                sender.sendMessage(ChatColor.GRAY + "    " + gimmick.getWorldName() + " " +
                    (int) gimmick.getX() + ", " + (int) gimmick.getY() + ", " + (int) gimmick.getZ());
                if (gimmick.getAnnounceMessage() != null) {
                    sender.sendMessage(ChatColor.GRAY + "    Msg: " +
                        ChatColor.translateAlternateColorCodes('&', gimmick.getAnnounceMessage()));
                }
            }
        }

        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("gui|menu")
    @Syntax("[pista]")
    @CommandCompletion("@tracks")
    @Description("Abre o menu de gimmicks da pista (e do heat selecionado)")
    public void onGui(Player player, @Optional String track) {
        String trackName = resolveTrack(player, track);
        if (trackName == null) {
            player.sendMessage(ChatColor.RED + "✗ Informe a pista: /gimmick gui <pista>");
            return;
        }

        Heats heat = gimmickManager.resolveSelectedHeat(player);
        new dev.EfraGroup.formulaRacing.Gui.GimmickGui(plugin, player, trackName, heat).show(player);
    }

    @Subcommand("paste")
    @Syntax("<nome> [pista]")
    @CommandCompletion("@gimmicks @tracks")
    @Description("Cola a gimmick agora, para teste")
    public void onPaste(CommandSender sender, String name, @Optional String track) {
        GimmickConfig gimmick = find(sender, name, track);
        if (gimmick == null) return;

        try {
            gimmickManager.pasteNow(gimmick);
            sender.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + gimmick.getName() + "' colada.");
            sender.sendMessage(ChatColor.GRAY + "  Desfaça com /gimmick restore");
        } catch (GimmickException e) {
            sender.sendMessage(ChatColor.RED + "✗ " + e.getMessage());
        }
    }

    @Subcommand("toggle")
    @Syntax("<nome> [pista]")
    @CommandCompletion("@gimmicks @tracks")
    @Description("Ativa ou desativa uma gimmick")
    public void onToggle(CommandSender sender, String name, @Optional String track) {
        GimmickConfig gimmick = find(sender, name, track);
        if (gimmick == null) return;

        boolean enabled = gimmickManager.toggleGimmick(gimmick);
        sender.sendMessage(
            (enabled ? ChatColor.GREEN + "✓ Gimmick '" + gimmick.getName() + "' ativada."
                     : ChatColor.YELLOW + "⚠ Gimmick '" + gimmick.getName() + "' desativada.")
        );
    }

    @Subcommand("setmessage")
    @Syntax("<nome> [pista] <mensagem>")
    @Description("Define a mensagem anunciada quando a gimmick é colada no heat")
    public void onSetMessage(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "✗ Uso: /gimmick setmessage <nome> [pista] <mensagem>");
            return;
        }

        String name = args[0];
        String track = null;
        int messageStart = 1;
        if (args.length >= 3) {
            track = args[1];
            messageStart = 2;
        }

        GimmickConfig gimmick = find(sender, name, track);
        if (gimmick == null) return;

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, messageStart, args.length));
        gimmickManager.setAnnounceMessage(gimmick, message);
        sender.sendMessage(ChatColor.GREEN + "✓ Mensagem de '" + gimmick.getName() + "' definida.");
        sender.sendMessage(ChatColor.GRAY + "  Preview: " +
            ChatColor.translateAlternateColorCodes('&', message));
    }

    @Subcommand("remove|delete")
    @Syntax("<nome> [pista]")
    @CommandCompletion("@gimmicks @tracks")
    @Description("Apaga a gimmick (arquivo, definição e agendamentos)")
    public void onRemove(CommandSender sender, String name, @Optional String track) {
        GimmickConfig gimmick = find(sender, name, track);
        if (gimmick == null) return;

        gimmickManager.deleteGimmick(gimmick);
        sender.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + gimmick.getName() + "' apagada de '" +
            gimmick.getTrackNameWS() + "'.");
    }

    @Subcommand("restore")
    @Description("Desfaz todas as gimmicks coladas (emergência)")
    public void onRestore(CommandSender sender) {
        int restored = gimmickManager.restoreEverything();
        if (restored == 0) {
            sender.sendMessage(ChatColor.GRAY + "Nada colado no momento.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "✓ " + restored + " gimmick(s) desfeita(s).");
    }

    private boolean isAirFlag(String arg) {
        return arg.equalsIgnoreCase("-a") || arg.equalsIgnoreCase("--air");
    }

    /** Uses the given track, or the heat the admin has selected, so [pista] stays optional. */
    private String resolveTrack(CommandSender sender, String track) {
        if (track != null && !track.isBlank()) return GimmickConfig.normalizeTrack(track);

        Player player = SenderUtils.player(sender);
        if (player == null) return null;

        var selectedHeat = plugin.getDatabaseManager().getPlayerSelectedHeat(player.getUniqueId());
        if (selectedHeat.isPresent()) {
            var heat = plugin.getRaceEventManager().getHeat(selectedHeat.get());
            if (heat.isPresent() && heat.get().getTrackNameWS() != null) {
                return GimmickConfig.normalizeTrack(heat.get().getTrackNameWS());
            }
        }

        var event = plugin.getDatabaseManager().getPlayerSelectedEvent(player.getUniqueId()).orElse(null);
        if (event != null) {
            var round = event.getSchedule().getCurrentRound().orElse(null);
            if (round != null) {
                var heat = round.getCurrentHeat().orElse(null);
                if (heat != null && heat.getTrackNameWS() != null) {
                    return GimmickConfig.normalizeTrack(heat.getTrackNameWS());
                }
            }
        }

        return null;
    }

    private GimmickConfig find(CommandSender sender, String name, String track) {
        String trackName = resolveTrack(sender, track);
        if (trackName == null) {
            sender.sendMessage(ChatColor.RED + "✗ Informe a pista: /gimmick " +
                "<comando> <nome> <pista>");
            return null;
        }

        GimmickConfig gimmick = gimmickManager.findGimmick(trackName, name);
        if (gimmick == null) {
            sender.sendMessage(ChatColor.RED + "✗ Gimmick '" + name + "' não encontrada em '" +
                trackName + "'.");
        }
        return gimmick;
    }

    private String formatLocation(Player player) {
        return String.format(
            "%s %d, %d, %d",
            player.getWorld().getName(),
            player.getLocation().getBlockX(),
            player.getLocation().getBlockY(),
            player.getLocation().getBlockZ()
        );
    }
}
