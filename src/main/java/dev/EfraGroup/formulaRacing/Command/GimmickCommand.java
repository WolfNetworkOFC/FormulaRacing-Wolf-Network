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
import java.util.List;
import net.md_5.bungee.api.ChatColor;
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
    public void onDefault(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Sistema de Gimmicks");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Faça //copy na área que quer colar e fique");
        player.sendMessage(ChatColor.GRAY + "  parado onde a gimmick deve aparecer.");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "  /gimmick save <nome> <pista> [-a]");
        player.sendMessage(ChatColor.GRAY + "    Salva o clipboard na sua posição.");
        player.sendMessage(ChatColor.GRAY + "    -a: cola o ar junto (senão o ar é ignorado).");
        player.sendMessage(ChatColor.WHITE + "  /gimmick list [pista]");
        player.sendMessage(ChatColor.WHITE + "  /gimmick gui [pista] - menu de gimmicks");
        player.sendMessage(ChatColor.WHITE + "  /gimmick paste <nome> [pista] - cola agora (teste)");
        player.sendMessage(ChatColor.WHITE + "  /gimmick toggle <nome> [pista] - ativa/desativa");
        player.sendMessage(ChatColor.WHITE + "  /gimmick setmessage <nome> [pista] <mensagem>");
        player.sendMessage(ChatColor.WHITE + "  /gimmick remove <nome> [pista]");
        player.sendMessage(ChatColor.WHITE + "  /gimmick restore - desfaz tudo que está colado");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "  /heat set gimmick add <nome> <volta>");
        player.sendMessage(ChatColor.WHITE + "  /heat set gimmick remove <nome>");
        player.sendMessage(ChatColor.WHITE + "  /heat set gimmick list");
        player.sendMessage(ChatColor.WHITE + "  /heat set gimmick clear");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
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
    public void onList(Player player, @Optional String track) {
        String trackName = resolveTrack(player, track);
        if (trackName == null) {
            player.sendMessage(ChatColor.RED + "✗ Informe a pista: /gimmick list <pista>");
            return;
        }

        List<GimmickConfig> gimmicks = gimmickManager.getGimmicksForTrack(trackName);
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Gimmicks de " + trackName);
        player.sendMessage("");

        if (gimmicks.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  Nenhuma gimmick salva nessa pista.");
        } else {
            for (GimmickConfig gimmick : gimmicks) {
                String status = gimmick.isEnabled() ? ChatColor.GREEN + "●" : ChatColor.RED + "●";
                player.sendMessage(status + " " + ChatColor.WHITE + gimmick.getName() +
                    ChatColor.GRAY + " | " + (gimmick.isPasteWithAir() ? "com ar" : "sem ar"));
                player.sendMessage(ChatColor.GRAY + "    " + gimmick.getWorldName() + " " +
                    (int) gimmick.getX() + ", " + (int) gimmick.getY() + ", " + (int) gimmick.getZ());
                if (gimmick.getAnnounceMessage() != null) {
                    player.sendMessage(ChatColor.GRAY + "    Msg: " +
                        ChatColor.translateAlternateColorCodes('&', gimmick.getAnnounceMessage()));
                }
            }
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
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
    public void onPaste(Player player, String name, @Optional String track) {
        GimmickConfig gimmick = find(player, name, track);
        if (gimmick == null) return;

        try {
            gimmickManager.pasteNow(gimmick);
            player.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + gimmick.getName() + "' colada.");
            player.sendMessage(ChatColor.GRAY + "  Desfaça com /gimmick restore");
        } catch (GimmickException e) {
            player.sendMessage(ChatColor.RED + "✗ " + e.getMessage());
        }
    }

    @Subcommand("toggle")
    @Syntax("<nome> [pista]")
    @CommandCompletion("@gimmicks @tracks")
    @Description("Ativa ou desativa uma gimmick")
    public void onToggle(Player player, String name, @Optional String track) {
        GimmickConfig gimmick = find(player, name, track);
        if (gimmick == null) return;

        boolean enabled = gimmickManager.toggleGimmick(gimmick);
        player.sendMessage(
            (enabled ? ChatColor.GREEN + "✓ Gimmick '" + gimmick.getName() + "' ativada."
                     : ChatColor.YELLOW + "⚠ Gimmick '" + gimmick.getName() + "' desativada.")
        );
    }

    @Subcommand("setmessage")
    @Syntax("<nome> [pista] <mensagem>")
    @Description("Define a mensagem anunciada quando a gimmick é colada no heat")
    public void onSetMessage(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✗ Uso: /gimmick setmessage <nome> [pista] <mensagem>");
            return;
        }

        String name = args[0];
        String track = null;
        int messageStart = 1;
        if (args.length >= 3) {
            track = args[1];
            messageStart = 2;
        }

        GimmickConfig gimmick = find(player, name, track);
        if (gimmick == null) return;

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, messageStart, args.length));
        gimmickManager.setAnnounceMessage(gimmick, message);
        player.sendMessage(ChatColor.GREEN + "✓ Mensagem de '" + gimmick.getName() + "' definida.");
        player.sendMessage(ChatColor.GRAY + "  Preview: " +
            ChatColor.translateAlternateColorCodes('&', message));
    }

    @Subcommand("remove|delete")
    @Syntax("<nome> [pista]")
    @CommandCompletion("@gimmicks @tracks")
    @Description("Apaga a gimmick (arquivo, definição e agendamentos)")
    public void onRemove(Player player, String name, @Optional String track) {
        GimmickConfig gimmick = find(player, name, track);
        if (gimmick == null) return;

        gimmickManager.deleteGimmick(gimmick);
        player.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + gimmick.getName() + "' apagada de '" +
            gimmick.getTrackNameWS() + "'.");
    }

    @Subcommand("restore")
    @Description("Desfaz todas as gimmicks coladas (emergência)")
    public void onRestore(Player player) {
        int restored = gimmickManager.restoreEverything();
        if (restored == 0) {
            player.sendMessage(ChatColor.GRAY + "Nada colado no momento.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "✓ " + restored + " gimmick(s) desfeita(s).");
    }

    private boolean isAirFlag(String arg) {
        return arg.equalsIgnoreCase("-a") || arg.equalsIgnoreCase("--air");
    }

    /** Uses the given track, or the heat the admin has selected, so [pista] stays optional. */
    private String resolveTrack(Player player, String track) {
        if (track != null && !track.isBlank()) return GimmickConfig.normalizeTrack(track);

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

    private GimmickConfig find(Player player, String name, String track) {
        String trackName = resolveTrack(player, track);
        if (trackName == null) {
            player.sendMessage(ChatColor.RED + "✗ Informe a pista: /gimmick " +
                "<comando> <nome> <pista>");
            return null;
        }

        GimmickConfig gimmick = gimmickManager.findGimmick(trackName, name);
        if (gimmick == null) {
            player.sendMessage(ChatColor.RED + "✗ Gimmick '" + name + "' não encontrada em '" +
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
