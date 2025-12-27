package dev.EfraGroup.formulaRacing.TabCompleter;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DuelTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        // /duel [TAB]
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // Sugestões de subcomandos
            completions.add("accept");
            completions.add("deny");
            completions.add("quit");
            completions.add("sair");

            // Sugestão de jogadores online (para desafiar)
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (!p.getName().equalsIgnoreCase(player.getName())) {
                    completions.add(p.getName());
                }
            });

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // /duel accept [TAB] -> Sugere o nome de quem enviou o convite
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            // Lógica para pegar quem mandou convite para este player
            // Como o pendingInvites está no CommandHandler, você precisaria de acesso a ele
            // Mas por padrão, sugerir todos os jogadores online já ajuda bastante
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}