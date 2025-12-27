package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class TimeTrialRandomCommandHandler implements CommandExecutor {

    private final DatabaseManager mysql;
    private final APIFormulaRacing api;
    private final FormulaRacing plugin;
    private final PacketSender packetsender;
    private final Random random = new Random();
    private final TimerUtils timerUtils;
    private final ScoreboardTimeTrialUtils stt;

    public TimeTrialRandomCommandHandler(DatabaseManager mysql, FormulaRacing plugin, PacketSender packetsender, TimerUtils timerUtils, APIFormulaRacing api, ScoreboardTimeTrialUtils stt) {
        this.mysql = mysql;
        this.plugin = plugin;
        this.packetsender = packetsender;
        this.timerUtils = timerUtils;
        this.api = api;
        this.stt = stt;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        // ⛔ BLOQUEIO DE DUELO ATIVO
        if (mysql.isPlayerInActiveDuel(player.getUniqueId())) {
            player.sendMessage("§c§lERRO §8» §7Você não pode usar o comando aleatório enquanto estiver em um §b§lDUELO §7ativo!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // O restante do código segue abaixo...
        List<String> availableTracks = mysql.getAllTracks();

        if (availableTracks == null) {
            player.sendMessage("§cNão há pistas disponíveis no momento.");
            return true;
        }

        // ... (filtro de pistas e teleporte)

        if (availableTracks == null || availableTracks.isEmpty()) {
            player.sendMessage("§cNão há pistas disponíveis no momento.");
            return true;
        }

        boolean hasBoatUtils = FormulaRacing.hasOpenBoatUtilsMod(player);

        List<String> validTracks = availableTracks.stream()
                .filter(trackName -> mysql.isTrackOpen(trackName)) // só abertas
                .filter(trackName -> {

                    boolean trackUsesBoatUtils = mysql.trackHaveBoatUtils(trackName);

                    if (!hasBoatUtils) {
                        // jogador SEM boatutils → só vanilla
                        return !trackUsesBoatUtils;
                    }

                    // jogador COM boatutils → pode qualquer pista
                    return true;
                })
                .collect(Collectors.toList());

        if (validTracks.isEmpty()) {
            player.sendMessage("§cNão há pistas compatíveis disponíveis para você no momento.");
            return true;
        }

        String trackName = validTracks.get(random.nextInt(validTracks.size()));
        packetsender.sendBoatSetting(player, 0);
        packetsender.applyBoatUtilsToPlayer(player, trackName);

        Location loc = mysql.getTrackSpawn(trackName);
        if (loc == null) {
            player.sendMessage("§cA pista selecionada não tem spawn definido.");
            return true;
        }

        timerUtils.stopTimer(player);
        stt.setPlayerTrack(player, trackName);
        stt.show(player,trackName);
        player.teleport(loc);
        String lang_code = mysql.getPlayerLanguage(player.getUniqueId());
        player.sendMessage("§e" + plugin.getDirectTranslation("timetrial_teleport", lang_code) +"[§f" + trackName + "§e]");

        api.spawnBoat(player, false, false, false);
        plugin.setLastTimeTrialTrack(player.getUniqueId(), trackName);

        return true;
    }

}
