package dev.EfraGroup.formulaRacing.TabCompleter;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TrackTabCompleter implements TabCompleter {

    private final DatabaseManager dbManager;

    private final List<String> subcommands = List.of(
            "deletebesttime",
            "deletealltimes",
            "deleteallplayertimes",
            "times",
            "mytimes"
    );

    // Subcomandos "perigosos" que exigem permissão
    private final List<String> restrictedSubs = List.of(
            "deletebesttime",
            "deletealltimes",
            "deleteallplayertimes"
    );

    private final String requiredPermission = "formularacing.admin";

    public TrackTabCompleter(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    private List<String> getAllTracks() {
        return dbManager.getAllTracks(); // Puxa do MySQL
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            // 🔹 Subcomando
            for (String sub : subcommands) {

                // 🔒 Bloqueia subcomandos restritos se o jogador não tiver permissão
                if (restrictedSubs.contains(sub) && !sender.hasPermission(requiredPermission)) {
                    continue;
                }

                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(sub);
                }
            }

        } else if (args.length == 2) {
            // 🔹 Segundo argumento: pistas ou jogadores
            switch (args[0].toLowerCase()) {
                case "deletebesttime":
                case "deletealltimes":
                case "times":
                case "mytimes":
                    if (isAllowed(sender, args[0])) {
                        suggestions.addAll(getAllTracks().stream()
                                .filter(track -> track.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList()));
                    }
                    break;

                case "deleteallplayertimes":
                    if (isAllowed(sender, args[0])) {
                        suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList()));
                    }
                    break;
            }

        } else if (args.length == 3) {
            // 🔹 Terceiro argumento: jogador (para deletebesttime ou deletealltimes)
            switch (args[0].toLowerCase()) {
                case "deletebesttime":
                case "deletealltimes":
                    if (isAllowed(sender, args[0])) {
                        suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList()));
                    }
                    break;
            }
        }

        return suggestions.isEmpty() ? Collections.emptyList() : suggestions;
    }

    // 🔒 Método auxiliar: checa se o sender pode usar o subcomando
    private boolean isAllowed(CommandSender sender, String subcommand) {
        return !restrictedSubs.contains(subcommand) || sender.hasPermission(requiredPermission);
    }
}
