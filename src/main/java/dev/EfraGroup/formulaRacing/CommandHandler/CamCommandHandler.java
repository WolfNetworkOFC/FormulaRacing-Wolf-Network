package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.CamUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CamCommandHandler implements CommandExecutor {

    private final FormulaRacing plugin;
    private final DatabaseManager database;
    private final CamUtils camUtils;

    private final Map<UUID, Boolean> editingMode = new HashMap<>();

    public CamCommandHandler(FormulaRacing plugin, DatabaseManager database, CamUtils camUtils) {
        this.plugin = plugin;
        this.database = database;
        this.camUtils = camUtils;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.sendMessage("§cVocê precisa estar no modo §lEspectador §cpara usar câmeras.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eUse: /cam <follow|followz|stop>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "follow" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUse: /cam follow <jogador>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cJogador não encontrado.");
                    return true;
                }
                camUtils.startFollowingNormal(player, target);
            }

            case "stop" -> {
                if (camUtils.stopFollowingNormal(player)) {
                    player.sendMessage("§aVocê parou de seguir.");
                } else {
                    player.sendMessage("§cVocê não estava seguindo ninguém.");
                }
            }
        }
        return false;
    }
}