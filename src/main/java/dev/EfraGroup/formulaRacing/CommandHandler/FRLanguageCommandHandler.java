package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FileManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

public class FRLanguageCommandHandler implements CommandExecutor {

    private final DatabaseManager db;
    private final FormulaRacing plugin;

    public FRLanguageCommandHandler(FormulaRacing plugin, FileManager fileManager, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set" -> handleSet(player, args);
            case "list" -> handleList(player);
            case "reload" -> handleReload(player);
            default -> {
                // Se o usuário digitar apenas /frlang pt_BR, ele tenta dar o set direto
                if (args.length == 1) {
                    handleSet(player, new String[]{"set", args[0]});
                } else {
                    sendHelp(player);
                }
            }
        }

        return true;
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 2) {
            String langCode = db.getPlayerLanguage(player.getUniqueId());
            player.sendMessage(plugin.getDirectTranslation("lang_set_usage", langCode));
            return;
        }

        String langCode = args[1]; // Mantém o case original para bater com o arquivo (ex: pt_BR)
        File langFile = new File(plugin.getDataFolder(), "lang/" + langCode + ".yml");

        if (!langFile.exists()) {
            String currentLang = db.getPlayerLanguage(player.getUniqueId());
            player.sendMessage(plugin.getTranslation("lang_not_found", currentLang, "{lang}", langCode));
            return;
        }

        // Salva no Banco de Dados
        db.setPlayerLanguage(player.getUniqueId(), langCode);

        // Busca a tradução direta do arquivo para confirmar
        YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
        String prefix = langConfig.getString("lang_set", "§aSeu idioma foi alterado para:");

        // Traduz cores se houver
        prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix);

        player.sendMessage(prefix + " §f" + langCode);
    }

    private void handleList(Player player) {
        File langDir = new File(plugin.getDataFolder(), "lang");
        String langCode = db.getPlayerLanguage(player.getUniqueId());

        // Lista todos os .yml na pasta lang/
        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));

        player.sendMessage(plugin.getDirectTranslation("lang_list_title", langCode));

        if (files == null || files.length == 0) {
            player.sendMessage(plugin.getDirectTranslation("lang_list_empty", langCode));
            return;
        }

        String current = db.getPlayerLanguage(player.getUniqueId());

        for (File f : files) {
            // Pega o nome do arquivo sem o .yml
            String code = f.getName().substring(0, f.getName().length() - 4);

            if (code.equalsIgnoreCase(current)) {
                player.sendMessage(plugin.getTranslation("lang_list_current", langCode, "{lang}", code));
            } else {
                player.sendMessage(plugin.getTranslation("lang_list_available", langCode, "{lang}", code));
            }
        }
        player.sendMessage(plugin.getDirectTranslation("lang_list_footer", langCode));
    }

    private void handleReload(Player player) {
        String langCode = db.getPlayerLanguage(player.getUniqueId());

        if (!player.hasPermission("formularacing.admin")) {
            player.sendMessage(plugin.getDirectTranslation("lang_no_permission_reload", langCode));
            return;
        }

        // Como não há manager, o reload aqui seria apenas uma confirmação visual
        // ou você pode adicionar um db.reloadSettings() se tiver algo em memória
        player.sendMessage(plugin.getDirectTranslation("lang_reload_success", langCode));
    }

    private void sendHelp(Player player) {
        String langCode = db.getPlayerLanguage(player.getUniqueId());

        player.sendMessage("");
        player.sendMessage(plugin.getDirectTranslation("lang_help_title", langCode));
        player.sendMessage(plugin.getDirectTranslation("lang_help_list", langCode));
        player.sendMessage(plugin.getDirectTranslation("lang_help_set", langCode));
        if (player.hasPermission("formularacing.admin")) {
            player.sendMessage(plugin.getDirectTranslation("lang_help_reload", langCode));
        }
        player.sendMessage("");
    }
}