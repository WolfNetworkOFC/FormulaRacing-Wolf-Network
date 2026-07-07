package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Gui.LanguageGui;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Values;
import java.io.File;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

@CommandAlias("language|lang|l")
@Description("Changes the FormulaRacing language")
public class LanguageCommand extends BaseCommand {
    private final FormulaRacing plugin;
    private final DatabaseManager db;

    public LanguageCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    @Default
    public void onDefault(Player player) {
        (new LanguageGui(this.plugin, player)).show(player);
    }

    @CatchUnknown
    public void onUnknown(Player player) {
        this.onHelp(player);
    }

    @Subcommand("menu")
    @Description("Opens the language menu")
    public void onMenu(Player player) {
        this.onDefault(player);
    }

    @Subcommand("set")
    @CommandCompletion("@languages")
    @Description("Sets your language")
    public void onSet(Player player, @Values("@languages") String langCode) {
        File langFile = new File(this.plugin.getDataFolder(), "lang/" + langCode + ".yml");
        if (!langFile.exists()) {
            String currentLang = this.db.getPlayerLanguage(player.getUniqueId());
            File checkFile = new File(this.plugin.getDataFolder(), "lang/" + currentLang + ".yml");
            if (!checkFile.exists()) {
                currentLang = "en_US";
            }

            player.sendMessage(this.plugin.getTranslation("lang_not_found", currentLang, new String[]{"{lang}", langCode}));
        } else {
            this.db.setPlayerLanguage(player.getUniqueId(), langCode);
            this.plugin.getTranslationUtil().updatePlayerLanguage(player.getUniqueId(), langCode);
            YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
            String prefix = langConfig.getString("lang_set", "§aSeu idioma foi alterado para:");
            prefix = ChatColor.translateAlternateColorCodes('&', prefix);
            player.sendMessage(prefix + " §f" + langCode);
        }
    }

    @Subcommand("list")
    @Description("Lists available languages")
    public void onList(Player player) {
        File langFolder = new File(this.plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            player.sendMessage("§cLanguage folder not found.");
        } else {
            File[] files = langFolder.listFiles((dir, namex) -> namex.endsWith(".yml"));
            if (files != null && files.length != 0) {
                player.sendMessage("§eAvailable languages:");

                for(File file : files) {
                    String name = file.getName().replace(".yml", "");
                    player.sendMessage("§7- §f" + name);
                }

            } else {
                player.sendMessage("§cNo languages found.");
            }
        }
    }

    @Subcommand("help|ajuda")
    @Description("Shows language command help")
    public void onHelp(Player player) {
        CommandHelpService.sendHelp(player, this, "/lang");
    }
}
