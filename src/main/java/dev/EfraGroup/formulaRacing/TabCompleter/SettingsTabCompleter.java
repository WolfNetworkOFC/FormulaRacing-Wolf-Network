package dev.EfraGroup.formulaRacing.TabCompleter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettingsTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.add("timetrial");
            suggestions.add("timetrialscoreboard");
            suggestions.add("boat");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("timetrial") || args[0].equalsIgnoreCase("timetrialscoreboard")) {
                suggestions.add("true");
                suggestions.add("false");
            } else if (args[0].equalsIgnoreCase("boat")) {
                // Lista manual de barcos
                String[] boats = {
                        "oak_boat", "birch_boat", "spruce_boat", "jungle_boat", "acacia_boat",
                        "dark_oak_boat", "mangrove_boat", "cherry_boat", "bamboo_raft",
                        "oak_chest_boat", "birch_chest_boat", "spruce_chest_boat", "jungle_chest_boat",
                        "acacia_chest_boat", "dark_oak_chest_boat", "mangrove_chest_boat", "cherry_chest_boat",
                        "bamboo_chest_raft"
                };
                for (String boat : boats) {
                    if (boat.startsWith(args[1].toLowerCase())) {
                        suggestions.add(boat);
                    }
                }
            }
        }

        return suggestions.isEmpty() ? Collections.emptyList() : suggestions;
    }
}
