package dev.EfraGroup.formulaRacing.TabCompleter;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.EventsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class EventTabCompleter implements TabCompleter {


    private final EventsManager eventManager;
    private final DatabaseManager databaseManager;

    public EventTabCompleter(EventsManager eventManager, DatabaseManager databaseManager) {
        this.eventManager = eventManager;
        this.databaseManager = databaseManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {

        List<String> suggestions = new ArrayList<>();
        Player player = (sender instanceof Player) ? (Player) sender : null;

        if (args.length == 1) {
            suggestions.addAll(Arrays.asList("create","select","set","delete","sign","reserve","signs","countdown"));
            return filter(suggestions, args[0]);
        }

        // /event create <name> [track]
        if (args[0].equalsIgnoreCase("create")) {
            if (args.length == 2) return filter(Arrays.asList("<Event Name>"), args[1]);
            if (args.length == 3) return filter(databaseManager.getAllTracks(), args[2]);
        }

        // /event select <eventname>
        if (args[0].equalsIgnoreCase("select") && args.length == 2) {
            return filter(eventManager.getAllEvents(), args[1]);
        }

        // /event set <option> <value> [eventName]
        if (args[0].equalsIgnoreCase("set")) {
            Map<String, Function<Player, List<String>>> optionsMap = Map.of(
                    "track", p -> databaseManager.getAllTracks(),
                    "signs", p -> Arrays.asList("true","false")
            );

            if (args.length == 2) return filter(new ArrayList<>(optionsMap.keySet()), args[1]);

            String option = args[1].toLowerCase();
            if (optionsMap.containsKey(option)) {
                if (args.length == 3) return filter(optionsMap.get(option).apply(player), args[2]);
                if (args.length == 4) {
                    List<String> eventNames = new ArrayList<>();
                    if (player != null) {
                        String selected = eventManager.getSelectedEvent(player.getUniqueId());
                        if (selected != null) eventNames.add(selected);
                    }
                    eventNames.addAll(eventManager.getAllEvents());
                    return filter(eventNames, args[3]);
                }
            }
        }

        // /event delete <eventname>
        if (args[0].equalsIgnoreCase("delete") && args.length == 2) {
            return filter(eventManager.getAllEvents(), args[1]);
        }

        // /event sign/reserve <eventName> [playerName]
        if (args[0].equalsIgnoreCase("sign") || args[0].equalsIgnoreCase("reserve")) {
            if (args.length == 2) return filter(eventManager.getAllEvents(), args[1]);
            if (args.length == 3) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) playerNames.add(p.getName());
                return filter(playerNames, args[2]);
            }
        }

        // /event signs <eventName>
        if (args[0].equalsIgnoreCase("signs") && args.length == 2) {
            return filter(eventManager.getAllEvents(), args[1]);
        }

        // /event countdown <h/m/s> <tempo> <texto> [eventName]
        if (args[0].equalsIgnoreCase("countdown")) {
            if (args.length == 2) return filter(Arrays.asList("h","m","s"), args[1]);
            if (args.length == 3) return filter(Arrays.asList("<tempo>"), args[2]);
            if (args.length >= 4) return filter(Arrays.asList("<texto>"), args[3]); // apenas placeholder
        }

        return new ArrayList<>();
    }

    // utilitário para filtrar sugestões
    private List<String> filter(List<String> list, String arg) {
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(arg.toLowerCase())) out.add(s);
        }
        return out;
    }

}
