//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Gui.BoatSelectGui;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

@CommandAlias("settings")
@Description("Gerenciamento de configurações pessoais")
public class SettingsCommand extends BaseCommand {
    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private static final Map<String, Integer> BOAT_MAP = new HashMap<String, Integer>() {
        {
            this.put("oak_boat", 1);
            this.put("birch_boat", 2);
            this.put("spruce_boat", 3);
            this.put("jungle_boat", 4);
            this.put("acacia_boat", 5);
            this.put("dark_oak_boat", 6);
            this.put("mangrove_boat", 7);
            this.put("cherry_boat", 8);
            this.put("bamboo_raft", 9);
            this.put("oak_chest_boat", 10);
            this.put("birch_chest_boat", 11);
            this.put("spruce_chest_boat", 12);
            this.put("jungle_chest_boat", 13);
            this.put("acacia_chest_boat", 14);
            this.put("dark_oak_chest_boat", 15);
            this.put("mangrove_chest_boat", 16);
            this.put("cherry_chest_boat", 17);
            this.put("bamboo_chest_raft", 18);
        }
    };

    public SettingsCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Default
    @CatchUnknown
    public void onDefault(Player player) {
        player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Uso: /settings <timetrial|timetrialscoreboard|boat> [valor]");
    }

    @Subcommand("boat")
    @CommandCompletion("@boats")
    @Description("Abre o menu de barcos ou define um barco")
    public void onBoat(Player player, @Optional String boatName) {
        if (boatName == null) {
            (new BoatSelectGui(this.databaseManager, this.plugin)).show(player);
        } else {
            Integer id = (Integer)BOAT_MAP.get(boatName.toLowerCase());
            if (id != null) {
                this.databaseManager.setPlayerBoatType(player.getUniqueId(), id);
            }
        }
    }

    @Subcommand("timetrial")
    @CommandCompletion("true|false")
    @Description("Ativa ou desativa o Time Trial")
    public void onTimeTrial(Player player, @Optional Boolean value) {
        UUID uuid = player.getUniqueId();
        boolean newValue = value != null ? value : !this.databaseManager.getTimeTrialEnabled(uuid);
        this.databaseManager.setTimeTrialEnabled(uuid, newValue);
        String var10001 = String.valueOf(ChatColor.GREEN);
        player.sendMessage(var10001 + "✅ Time Trial " + (newValue ? "ON" : "OFF"));
    }

    @Subcommand("timetrialscoreboard")
    @CommandCompletion("true|false")
    @Description("Ativa ou desativa o Scoreboard do Time Trial")
    public void onTimeTrialScoreboard(Player player, @Optional Boolean value) {
        UUID uuid = player.getUniqueId();
        boolean newValue = value != null ? value : !this.databaseManager.getTimeTrialScoreboard(uuid);
        this.databaseManager.setTimeTrialScoreboard(uuid, newValue);
        String var10001 = String.valueOf(ChatColor.GREEN);
        player.sendMessage(var10001 + "✅ Scoreboard do Time Trial " + (newValue ? "ATIVADO" : "DESATIVADO"));
    }
}
