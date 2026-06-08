package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.GimmickConfig;
import dev.EfraGroup.formulaRacing.Heat.GimmickManager;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

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
    @Description("Mostra info e comandos disponíveis de gimmick")
    public void onDefault(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Sistema de Gimmicks");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Use //schem save <nome> no WorldEdit");
        player.sendMessage(ChatColor.GRAY + "  para salvar um schematic primeiro.");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "  /gm save <nome> - Salva gimmick na sua posição");
        player.sendMessage(ChatColor.WHITE + "  /gm setlap <nome> <volta> - Define volta do gatilho");
        player.sendMessage(ChatColor.WHITE + "  /gm setremove <nome> <voltas|permanent> - Duração");
        player.sendMessage(ChatColor.WHITE + "  /gm setmessage <nome> <msg> - Mensagem de announce");
        player.sendMessage(ChatColor.WHITE + "  /gm enable <nome> / disable <nome> - Ativa/desativa");
        player.sendMessage(ChatColor.WHITE + "  /gm list - Lista gimmicks do heat");
        player.sendMessage(ChatColor.WHITE + "  /gm remove <nome> - Remove uma gimmick");
        player.sendMessage(ChatColor.WHITE + "  /gm clear - Remove todas as gimmicks");
        player.sendMessage(ChatColor.WHITE + "  /gm paste <nome> - Cola gimmick agora (teste)");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("save")
    @Syntax("<nome>")
    @Description("Salva uma gimmick com o schematic e sua posição atual")
    public void onSave(Player player, String schematicName) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado. Use /heat select <id> primeiro.");
            return;
        }

        GimmickConfig config = new GimmickConfig(schematicName, player.getLocation().clone());
        gimmickManager.addGimmick(heat.getId(), config);

        player.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + schematicName + "' salva para o heat " + heat.getId() + ".");
        player.sendMessage(ChatColor.GRAY + "  Posição: " + formatLoc(player.getLocation()));
        player.sendMessage(ChatColor.GRAY + "  Use /gm setlap " + schematicName + " <volta> para definir quando colar.");
    }

    @Subcommand("setlap")
    @Syntax("<nome> <volta>")
    @Description("Define a volta que dispara a gimmick")
    public void onSetLap(Player player, String schematicName, int lap) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado.");
            return;
        }

        GimmickConfig gimmick = findGimmick(heat.getId(), schematicName);
        if (gimmick == null) {
            player.sendMessage(ChatColor.RED + "✗ Gimmick '" + schematicName + "' não encontrada no heat " + heat.getId() + ".");
            return;
        }

        gimmick.setTriggerLap(lap);
        player.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + schematicName + "' configurada para colar na volta " + lap + ".");
    }

    @Subcommand("setremove")
    @Syntax("<nome> <voltas|permanent>")
    @Description("Define se a gimmick é permanente ou quantas voltas dura")
    public void onSetRemove(Player player, String schematicName, String value) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado.");
            return;
        }

        GimmickConfig gimmick = findGimmick(heat.getId(), schematicName);
        if (gimmick == null) {
            player.sendMessage(ChatColor.RED + "✗ Gimmick '" + schematicName + "' não encontrada no heat " + heat.getId() + ".");
            return;
        }

        if (value.equalsIgnoreCase("permanent") || value.equalsIgnoreCase("p")) {
            gimmick.setPermanent(true);
            player.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + schematicName + "' configurada como permanente.");
        } else {
            try {
                int laps = Integer.parseInt(value);
                gimmick.setPermanent(false);
                gimmick.setRemoveAfterLaps(laps);
                player.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + schematicName + "' será removida após " + laps + " voltas.");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "✗ Valor inválido! Use um número de voltas ou 'permanent'.");
            }
        }
    }

    @Subcommand("setmessage")
    @Syntax("<nome> <mensagem>")
    @Description("Define a mensagem estilo announce quando a gimmick for colada")
    public void onSetMessage(Player player, String schematicName, String message) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado.");
            return;
        }

        GimmickConfig gimmick = findGimmick(heat.getId(), schematicName);
        if (gimmick == null) {
            player.sendMessage(ChatColor.RED + "✗ Gimmick '" + schematicName + "' não encontrada no heat " + heat.getId() + ".");
            return;
        }

        gimmick.setAnnounceMessage(message);
        player.sendMessage(ChatColor.GREEN + "✓ Mensagem configurada para '" + schematicName + "'.");
        player.sendMessage(ChatColor.GRAY + "  Preview: " + message.replace("&", "§"));
    }

    @Subcommand("enable")
    @Syntax("<nome>")
    @Description("Ativa uma gimmick")
    public void onEnable(Player player, String schematicName) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado.");
            return;
        }

        GimmickConfig gimmick = findGimmick(heat.getId(), schematicName);
        if (gimmick == null) {
            player.sendMessage(ChatColor.RED + "✗ Gimmick '" + schematicName + "' não encontrada no heat " + heat.getId() + ".");
            return;
        }

        gimmick.setEnabled(true);
        player.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + schematicName + "' ativada.");
    }

    @Subcommand("disable")
    @Syntax("<nome>")
    @Description("Desativa uma gimmick")
    public void onDisable(Player player, String schematicName) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado.");
            return;
        }

        GimmickConfig gimmick = findGimmick(heat.getId(), schematicName);
        if (gimmick == null) {
            player.sendMessage(ChatColor.RED + "✗ Gimmick '" + schematicName + "' não encontrada no heat " + heat.getId() + ".");
            return;
        }

        gimmick.setEnabled(false);
        player.sendMessage(ChatColor.YELLOW + "⚠ Gimmick '" + schematicName + "' desativada.");
    }

    @Subcommand("list")
    @Description("Lista todas as gimmicks do heat selecionado")
    public void onList(Player player) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado.");
            return;
        }

        List<GimmickConfig> gimmicks = gimmickManager.getGimmicksForHeat(heat.getId());

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Gimmicks do Heat " + heat.getId());
        player.sendMessage("");

        if (gimmicks.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  Nenhuma gimmick configurada.");
        } else {
            for (GimmickConfig g : gimmicks) {
                String status = g.isEnabled() ? ChatColor.GREEN + "●" : ChatColor.RED + "●";
                String duration = g.isPermanent() ? "Permanente" : "Remove em " + g.getRemoveAfterLaps() + " voltas";
                player.sendMessage(status + " " + ChatColor.WHITE + g.getSchematicName());
                player.sendMessage(ChatColor.GRAY + "    Volta: " + g.getTriggerLap() + " | " + duration);
                if (g.getAnnounceMessage() != null) {
                    player.sendMessage(ChatColor.GRAY + "    Msg: " + g.getAnnounceMessage().replace("&", "§"));
                }
                player.sendMessage("");
            }
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("remove")
    @Syntax("<nome>")
    @Description("Remove uma gimmick do heat")
    public void onRemove(Player player, String schematicName) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado.");
            return;
        }

        if (gimmickManager.removeGimmick(heat.getId(), schematicName)) {
            player.sendMessage(ChatColor.GREEN + "✓ Gimmick '" + schematicName + "' removida do heat " + heat.getId() + ".");
        } else {
            player.sendMessage(ChatColor.RED + "✗ Gimmick '" + schematicName + "' não encontrada no heat " + heat.getId() + ".");
        }
    }

    @Subcommand("clear")
    @Description("Remove todas as gimmicks do heat")
    public void onClear(Player player) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado.");
            return;
        }

        gimmickManager.clearGimmicks(heat.getId());
        player.sendMessage(ChatColor.GREEN + "✓ Todas as gimmicks do heat " + heat.getId() + " foram removidas.");
    }

    @Subcommand("paste")
    @Syntax("<nome>")
    @Description("Cola uma gimmick imediatamente (para teste)")
    public void onPaste(Player player, String schematicName) {
        Heats heat = resolveHeat(player);
        if (heat == null) {
            player.sendMessage(ChatColor.RED + "✗ Nenhum heat selecionado.");
            return;
        }

        GimmickConfig gimmick = findGimmick(heat.getId(), schematicName);
        if (gimmick == null) {
            player.sendMessage(ChatColor.RED + "✗ Gimmick '" + schematicName + "' não encontrada no heat " + heat.getId() + ".");
            return;
        }

        gimmickManager.pasteGimmick(gimmick);
        player.sendMessage(ChatColor.GREEN + "✓ Colando gimmick '" + schematicName + "'...");
    }

    private Heats resolveHeat(Player player) {
        var selectedHeatId = plugin.getDatabaseManager().getPlayerSelectedHeat(player.getUniqueId());
        if (selectedHeatId.isPresent()) {
            Optional<Heats> heat = plugin.getRaceEventManager().getHeat(selectedHeatId.get());
            if (heat.isPresent()) return heat.get();
        }

        var event = plugin.getDatabaseManager().getPlayerSelectedEvent(player.getUniqueId()).orElse(null);
        if (event != null) {
            var round = event.getSchedule().getCurrentRound().orElse(null);
            if (round != null) return round.getCurrentHeat().orElse(null);
        }

        return null;
    }

    private GimmickConfig findGimmick(int heatId, String schematicName) {
        return gimmickManager.getGimmicksForHeat(heatId).stream()
                .filter(g -> g.getSchematicName().equalsIgnoreCase(schematicName))
                .findFirst()
                .orElse(null);
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("%s %d, %d, %d",
                loc.getWorld() != null ? loc.getWorld().getName() : "?",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
