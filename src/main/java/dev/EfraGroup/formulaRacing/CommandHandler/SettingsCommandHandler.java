package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Gui.BoatSelectGui;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class SettingsCommandHandler implements CommandExecutor {

    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private final BoatSelectGui boatSelectGui;

    public SettingsCommandHandler(FormulaRacing plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.boatSelectGui = new BoatSelectGui(databaseManager, plugin);
        plugin.getCommand("settings").setExecutor(this); // registra comando
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.YELLOW + "Uso: /settings <timetrial|timetrialscoreboard|boat> [valor]");
            return true;
        }

        String setting = args[0].toLowerCase();
        UUID uuid = player.getUniqueId();

        boolean newValue;
        boolean hasArg = args.length >= 2;

        switch (setting) {
            case "boat" -> {
                // /settings boat → abre a GUI
                if (!hasArg) {
                    boatSelectGui.open(player);
                    player.sendMessage(ChatColor.AQUA + "🚤 Selecione seu barco na interface.");
                    return true;
                }

                // /settings boat <nome>
                String input = args[1].toLowerCase();

                // Mapa simples nome -> ID (igual ao BoatSelectGui)
                Map<String, Integer> boatMap = new HashMap<>() {{
                    put("oak_boat", 1);
                    put("birch_boat", 2);
                    put("spruce_boat", 3);
                    put("jungle_boat", 4);
                    put("acacia_boat", 5);
                    put("dark_oak_boat", 6);
                    put("mangrove_boat", 7);
                    put("cherry_boat", 8);
                    put("bamboo_raft", 9);

                    put("oak_chest_boat", 10);
                    put("birch_chest_boat", 11);
                    put("spruce_chest_boat", 12);
                    put("jungle_chest_boat", 13);
                    put("acacia_chest_boat", 14);
                    put("dark_oak_chest_boat", 15);
                    put("mangrove_chest_boat", 16);
                    put("cherry_chest_boat", 17);
                    put("bamboo_chest_raft", 18);
                }};

                Integer id = boatMap.get(input);
                if (id == null) {
                    player.sendMessage(ChatColor.RED + "🚫 Tipo de barco inválido. Exemplos:");
                    player.sendMessage(ChatColor.YELLOW + " - /settings boat oak_boat");
                    player.sendMessage(ChatColor.YELLOW + " - /settings boat oak_chest_boat");
                    return true;
                }

                databaseManager.setPlayerBoatType(uuid, id);
                player.sendMessage(ChatColor.GREEN + "✅ Seu barco foi definido para: " + ChatColor.AQUA + input);
                return true;
            }

            case "timetrial" -> {
                boolean current = databaseManager.getTimeTrialEnabled(uuid);

                if (hasArg) {
                    String value = args[1].toLowerCase();
                    if (value.equals("true")) newValue = true;
                    else if (value.equals("false")) newValue = false;
                    else {
                        player.sendMessage(ChatColor.YELLOW + "Valor inválido. Use true ou false.");
                        return true;
                    }
                } else {
                    newValue = !current; // alterna
                }

                databaseManager.setTimeTrialEnabled(uuid, newValue);
                player.sendMessage(ChatColor.GREEN + "⏱ TimeTrial foi " +
                        (newValue ? ChatColor.AQUA + "ativado" : ChatColor.RED + "desativado") + ChatColor.GREEN + ".");
                return true;
            }

            case "timetrialscoreboard" -> {
                boolean current = databaseManager.getTimeTrialScoreboard(uuid);

                if (hasArg) {
                    String value = args[1].toLowerCase();
                    if (value.equals("true")) newValue = true;
                    else if (value.equals("false")) newValue = false;
                    else {
                        player.sendMessage(ChatColor.YELLOW + "Valor inválido. Use true ou false.");
                        return true;
                    }
                } else {
                    newValue = !current; // alterna
                }

                databaseManager.setTimeTrialScoreboard(uuid, newValue);
                player.sendMessage(ChatColor.GREEN + "📊 TimeTrialScoreboard foi " +
                        (newValue ? ChatColor.AQUA + "ativado" : ChatColor.RED + "desativado") + ChatColor.GREEN + ".");
                return true;
            }

            default -> {
                player.sendMessage(ChatColor.RED + "Configuração desconhecida: " + setting);
                player.sendMessage(ChatColor.RED + "Use: /settings <timetrial|timetrialscoreboard|boat>");
                return true;
            }
        }
    }
}
