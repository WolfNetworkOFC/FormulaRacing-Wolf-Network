package dev.EfraGroup.formulaRacing.TabCompleter;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FRLanguageTabCompleter implements TabCompleter {

    private final FormulaRacing plugin;

    public FRLanguageTabCompleter(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Subcomandos principais no primeiro argumento
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("set", "list", "reload", "menu", "gui");
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Sugestão de idiomas apenas para o comando "set"
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            File langDir = new File(plugin.getDataFolder(), "lang");

            if (!langDir.exists() || !langDir.isDirectory()) {
                return Collections.emptyList();
            }

            File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return Collections.emptyList();

            List<String> availableLangs = new ArrayList<>();
            for (File file : files) {
                // Remove o ".yml" para sugerir apenas o código (ex: pt-BR)
                availableLangs.add(file.getName().replace(".yml", ""));
            }

            return availableLangs.stream()
                    .filter(lang -> lang.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}