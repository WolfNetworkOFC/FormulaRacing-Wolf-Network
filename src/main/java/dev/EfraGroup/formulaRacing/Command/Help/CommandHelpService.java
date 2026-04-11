package dev.EfraGroup.formulaRacing.Command.Help;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CommandHelpService {

    private CommandHelpService() {
    }

    public static void sendHelp(CommandSender sender, BaseCommand command) {
        String rootAlias = getRootAlias(command.getClass());
        String title = "/" + rootAlias;
        sendHelp(sender, command, title);
    }

    public static void sendHelp(CommandSender sender, BaseCommand command, String title) {
        Map<String, HelpEntry> entries = new LinkedHashMap<>();

        for (Method method : getAllMethods(command.getClass())) {
            Subcommand subcommand = method.getAnnotation(Subcommand.class);
            Default defaultAnnotation = method.getAnnotation(Default.class);
            if (subcommand == null && defaultAnnotation == null) {
                continue;
            }

            CommandPermission permissionAnnotation = method.getAnnotation(CommandPermission.class);
            String permission = permissionAnnotation == null ? "" : permissionAnnotation.value();
            if (!permission.isBlank() && !permission.startsWith("%") && !sender.hasPermission(permission)) {
                continue;
            }

            Description descriptionAnnotation = method.getAnnotation(Description.class);
            Syntax syntaxAnnotation = method.getAnnotation(Syntax.class);

            String normalizedSubcommand = subcommand == null ? "" : normalizeSubcommand(subcommand.value());
            String syntax = syntaxAnnotation == null ? "" : syntaxAnnotation.value().trim();
            String description = descriptionAnnotation == null ? "Sem descricao." : descriptionAnnotation.value().trim();

            HelpEntry entry = new HelpEntry(normalizedSubcommand, syntax, description, permission);
            entries.putIfAbsent(entry.identity(), entry);
        }

        List<HelpEntry> sortedEntries = new ArrayList<>(entries.values());
        sortedEntries.sort(Comparator
                .comparingInt((HelpEntry e) -> e.subcommand().split(" ").length)
                .thenComparing(HelpEntry::subcommand)
                .thenComparing(HelpEntry::syntax));

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "========================================");
        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "Ajuda de comando " + ChatColor.YELLOW + ChatColor.BOLD + title);
        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "========================================");

        if (sortedEntries.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Nenhum subcomando disponivel para voce.");
            return;
        }

        for (HelpEntry entry : sortedEntries) {
            String usage = ChatColor.YELLOW + "/" + title.replaceFirst("^/", "");
            if (!entry.subcommand().isBlank()) {
                usage += " " + entry.subcommand();
            }
            if (!entry.syntax().isBlank()) {
                usage += " " + entry.syntax();
            }

            sender.sendMessage(usage + ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + entry.description());
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_GRAY + "Dica: use TAB para completar argumentos.");
        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "========================================");
    }

    private static String getRootAlias(Class<?> commandClass) {
        CommandAlias commandAlias = commandClass.getAnnotation(CommandAlias.class);
        if (commandAlias == null || commandAlias.value().isBlank()) {
            return "command";
        }

        String aliasRaw = commandAlias.value().trim();
        String[] aliases = aliasRaw.split("\\|");
        return aliases.length == 0 ? aliasRaw : aliases[0].trim();
    }

    private static List<Method> getAllMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                methods.add(method);
            }
            current = current.getSuperclass();
        }
        return methods;
    }

    private static String normalizeSubcommand(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String[] parts = value.trim().split("\\s+");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String[] aliases = part.split("\\|");
            normalized.add(aliases.length == 0 ? part : aliases[0]);
        }
        return String.join(" ", normalized).trim();
    }

    private record HelpEntry(String subcommand, String syntax, String description, String permission) {
        private String identity() {
            return (subcommand + "|" + syntax).toLowerCase();
        }
    }
}
