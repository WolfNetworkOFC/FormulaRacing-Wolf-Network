package dev.EfraGroup.formulaRacing.TabCompleter;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LonelyTabCompleter implements TabCompleter {

    private final DatabaseManager mysql;

    public LonelyTabCompleter(DatabaseManager mysql) {
        this.mysql = mysql;
    }


    private static final List<String> LANGUAGES = Arrays.asList("true","false");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return LANGUAGES.stream()
                    .filter(lang -> lang.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
