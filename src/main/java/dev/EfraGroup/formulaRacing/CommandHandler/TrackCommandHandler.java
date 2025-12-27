package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TrackCommandHandler implements CommandExecutor {

    private final DatabaseManager dbManager;

    public TrackCommandHandler(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eUse: §a/track <times|mytimes|deletebesttime|deletealltimes|deleteallplayertimes>");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // ========================
            // 📜 /track times
            // ========================
            case "times": {
                if (args.length < 2) {
                    player.sendMessage("§cUso correto: /track times <pista> [página]");
                    return true;
                }

                String trackName = args[1];
                int page = 1;

                if (args.length >= 3) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cNúmero de página inválido. Use apenas números.");
                        return true;
                    }
                }

                List<Map<String, Object>> times = dbManager.getAllTimesOnTrack(trackName, page);

                if (times.isEmpty()) {
                    player.sendMessage("§7Nenhum tempo encontrado para a pista §e" + trackName + "§7.");
                    return true;
                }

                player.sendMessage("§f📜 §3Tempos da pista §f" + trackName + " §3(Página " + page + ")§f:");
                for (Map<String, Object> entry : times) {
                    int pos = (int) entry.get("pos");
                    String pname = (String) entry.get("player");
                    double time = (double) entry.get("time");
                    int cp = (int) entry.get("checkpoints");
                    boolean finished = (boolean) entry.get("finished");

                    String formatted = finished
                            ? String.format("§e#%d §7» §a%s §8— §f%.3fs §8(✓)", pos, pname, time)
                            : String.format("§e#%d §7» §a%s §8— §f%.3fs §7(%dCP)", pos, pname, time, cp);

                    player.sendMessage(formatted);
                }
                return true;
            }

            // ========================
            // 🧍 /track mytimes
            // ========================
            case "mytimes": {
                if (args.length < 2) {
                    player.sendMessage("§cUso correto: /track mytimes <pista> [página]");
                    return true;
                }

                String trackName = args[1];
                int page = 1;

                if (args.length >= 3) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cNúmero de página inválido. Use apenas números.");
                        return true;
                    }
                }

                List<Map<String, Object>> times = dbManager.getAllTimesOnTrackByPlayer(trackName, player.getName(), page);

                if (times.isEmpty()) {
                    player.sendMessage("§7Você ainda não registrou tempos na pista §e" + trackName + "§7.");
                    return true;
                }

                player.sendMessage("§f📜 §3Seus tempos na pista §f" + trackName + " §3(Página " + page + ")§f:");
                for (Map<String, Object> entry : times) {
                    int pos = (int) entry.get("pos");
                    double time = (double) entry.get("time");
                    int cp = (int) entry.get("checkpoints");
                    boolean finished = (boolean) entry.get("finished");
                    String date = (String) entry.get("date");

                    String formatted = finished
                            ? String.format("§e#%d §8— §f%.3fs §8(✓) §7[%s]", pos, time, date)
                            : String.format("§e#%d §8— §f%.3fs §7(%dCP) §7[%s]", pos, time, cp, date);

                    player.sendMessage(formatted);
                }
                return true;
            }

            // ========================
            // ❌ /track deletebesttime <track> <player>
            // ========================
            case "deletebesttime": {
                if (!player.hasPermission("formularacing.admin")) {
                    player.sendMessage("§cVocê não tem permissão para usar este comando.");
                    return true;
                }

                if (args.length < 3) {
                    player.sendMessage("§cUso correto: /track deletebesttime <pista> <jogador>");
                    return true;
                }

                String track = args[1];
                String targetPlayer = args[2];

                boolean success = dbManager.deletePlayerBestTimeOnTrack(track, targetPlayer);
                if (success) {
                    player.sendMessage("§aMelhor tempo de §e" + targetPlayer + " §ana pista §e" + track + " §afoi removido com sucesso!");
                } else {
                    player.sendMessage("§cNenhum tempo encontrado para §e" + targetPlayer + " §cna pista §e" + track + "§c.");
                }
                return true;
            }

            // ========================
            // ❌ /track deletealltimes <track> [player]
            // ========================
            case "deletealltimes": {
                if (!player.hasPermission("formularacing.admin")) {
                    player.sendMessage("§cVocê não tem permissão para usar este comando.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage("§cUso correto: /track deletealltimes <pista> [jogador]");
                    return true;
                }

                String track = args[1];
                String targetPlayer = args.length >= 3 ? args[2] : null;
                boolean success = dbManager.deleteAllTimes(track, targetPlayer);

                if (success) {
                    if (targetPlayer != null)
                        player.sendMessage("§aTodos os tempos de §e" + targetPlayer + " §ana pista §e" + track + " §aforam removidos!");
                    else
                        player.sendMessage("§aTodos os tempos da pista §e" + track + " §aforam removidos!");
                } else {
                    player.sendMessage("§cNenhum tempo encontrado para a pista §e" + track + "§c.");
                }
                return true;
            }

            // ========================
            // ❌ /track deleteallplayertimes <player>
            // ========================
            case "deleteallplayertimes": {
                if (!player.hasPermission("formularacing.admin")) {
                    player.sendMessage("§cVocê não tem permissão para usar este comando.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage("§cUso correto: /track deleteallplayertimes <jogador>");
                    return true;
                }

                String targetPlayer = args[1];
                boolean success = dbManager.deletePlayerAllTimes(targetPlayer);

                if (success) {
                    player.sendMessage("§aTodos os tempos de §e" + targetPlayer + " §aforam removidos com sucesso!");
                } else {
                    player.sendMessage("§cNenhum tempo encontrado para o jogador §e" + targetPlayer + "§c.");
                }
                return true;
            }

            default:
                player.sendMessage("§cSubcomando desconhecido. Use: /track <times|mytimes|deletebesttime|deletealltimes|deleteallplayertimes>");
                return true;
        }
    }
}
