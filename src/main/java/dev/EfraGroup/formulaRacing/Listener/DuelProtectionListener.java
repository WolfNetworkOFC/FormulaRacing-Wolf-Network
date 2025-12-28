package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Listener dedicado a proteger a integridade dos duelos,
 * impedindo que jogadores usem comandos ou ações que possam
 * dar vantagem injusta ou quebrar a experiência da corrida.
 */
public class DuelProtectionListener implements Listener {

    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;

    // Lista de comandos que são bloqueados durante duelos (exceto /duel sair)
    private static final List<String> BLOCKED_COMMANDS = Arrays.asList(
            "/spawn",
            "/home",
            "/tp",
            "/teleport",
            "/tpa",
            "/tpaccept",
            "/back",
            "/warp",
            "/hub",
            "/lobby",
            "/suicide",
            "/kill",
            "/fly",
            "/gm",
            "/gamemode",
            "/speed"
    );

    public DuelProtectionListener(FormulaRacing plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Bloqueia comandos que poderiam ser usados para escapar ou trapacear no duelo.
     * Prioridade HIGHEST para garantir que seja processado antes de outros plugins.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandDuringDuel(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Só verifica se o jogador está em duelo
        if (!databaseManager.isPlayerInActiveDuel(player.getUniqueId())) {
            return;
        }

        String command = event.getMessage().toLowerCase().split(" ")[0];

        // Permite o comando /duel (para sair do duelo)
        if (command.equals("/duel")) {
            return;
        }

        // Bloqueia comandos perigosos
        for (String blocked : BLOCKED_COMMANDS) {
            if (command.equals(blocked)) {
                event.setCancelled(true);
                player.sendMessage("§c§lDUELO §8» §7Este comando está bloqueado durante duelos!");
                player.sendMessage("§7Use §f/duel sair §7para abandonar a corrida.");
                return;
            }
        }
    }
}

