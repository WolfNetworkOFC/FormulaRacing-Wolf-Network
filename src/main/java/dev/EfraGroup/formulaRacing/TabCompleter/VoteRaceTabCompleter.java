package dev.EfraGroup.formulaRacing.TabCompleter;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class VoteRaceTabCompleter implements TabCompleter {

    private final DatabaseManager mysql;

    public VoteRaceTabCompleter(DatabaseManager mysql) {
        this.mysql = mysql;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Retorna apenas as pistas abertas
            List<String> tracks = mysql.getAllTracks();
            for (String track : tracks) {
                if (mysql.isTrackOpen(track) && track.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(track);
                }
            }
        }

        return completions;
    }
}
