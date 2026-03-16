//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Command;

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
@Description("Muda a linguagem do FormulaRacing")
public class LanguageCommand extends BaseCommand {
    private final FormulaRacing plugin;
    private final DatabaseManager db;

    public LanguageCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    @Default
    @CatchUnknown
    @Subcommand("menu")
    @Description("Abre o menu de idiomas")
    public void onMenu(Player player) {
        (new LanguageGui(this.plugin, player)).show(player);
    }

    @Subcommand("set")
    @CommandCompletion("@languages")
    @Description("Define seu idioma")
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
    @Description("Lista os idiomas disponíveis")
    public void onList(Player player) {
        File langFolder = new File(this.plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            player.sendMessage("§cPasta de idiomas não encontrada.");
        } else {
            File[] files = langFolder.listFiles((dir, namex) -> namex.endsWith(".yml"));
            if (files != null && files.length != 0) {
                player.sendMessage("§eIdiomas disponíveis:");

                for(File file : files) {
                    String name = file.getName().replace(".yml", "");
                    player.sendMessage("§7- §f" + name);
                }

            } else {
                player.sendMessage("§cNenhum idioma encontrado.");
            }
        }
    }

    @Subcommand("help|ajuda")
    @Description("Mostra ajuda do comando de idioma")
    public void onHelp(Player player) {
        player.sendMessage("§e§l--- FormulaRacing Language ---");
        player.sendMessage("§f/lang menu §7- Abre o menu de idiomas");
        player.sendMessage("§f/lang set <idioma> §7- Define seu idioma");
        player.sendMessage("§f/lang list §7- Lista idiomas disponíveis");
    }
}
