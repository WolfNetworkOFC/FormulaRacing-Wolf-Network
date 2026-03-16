package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Default;
import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@CommandAlias("timetrialrandom|ttr|timetrialr|ttrandom")
@Description("Entra em uma Time Trial aleatória")
public class TimeTrialRandomCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final APIFormulaRacing api;
    private final PacketSender packetsender;
    private final TimerUtils timerUtils;
    private final ScoreboardTimeTrialUtils stt;
    private final Random random = new Random();

    public TimeTrialRandomCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.mysql = plugin.getDatabaseManager();
        this.packetsender = plugin.getPacketSender();
        this.timerUtils = plugin.getTimerUtils();
        this.api = plugin.getAPI();
        this.stt = plugin.getScoreboardTimeTrialUtils();
    }

    @Default
    public void onRandom(Player player) {

        // ⛔ BLOQUEIO DE DUELO ATIVO
        if (mysql.isPlayerInActiveDuel(player.getUniqueId())) {
            player.sendMessage("§c§lERRO §8» §7Você não pode usar o comando aleatório enquanto estiver em um §b§lDUELO §7ativo!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        List<String> availableTracks = mysql.getAllTracks();
        if (availableTracks == null || availableTracks.isEmpty()) {
            player.sendMessage("§cNão há pistas disponíveis no momento.");
            return;
        }

        boolean hasBoatUtils = FormulaRacing.hasOpenBoatUtilsMod(player);

        // Filtro de pistas compatíveis
        List<String> validTracks = availableTracks.stream()
                .filter(mysql::isTrackOpen)
                .filter(trackName -> hasBoatUtils || !mysql.trackHaveBoatUtils(trackName))
                .collect(Collectors.toList());

        if (validTracks.isEmpty()) {
            player.sendMessage("§cNão há pistas compatíveis disponíveis para você no momento.");
            return;
        }

        // Seleção aleatória
        String trackName = validTracks.get(random.nextInt(validTracks.size()));

        // Configurações de pacotes e Scoreboard
        packetsender.sendBoatSetting(player, 0);
        packetsender.applyBoatUtilsToPlayer(player, trackName);

        Location loc = mysql.getTrackSpawn(trackName);
        if (loc == null) {
            player.sendMessage("§cA pista selecionada não tem spawn definido.");
            return;
        }

        // Limpeza de sessão anterior
        timerUtils.stopTimer(player);
        if (plugin.getTimeTrialController() != null) {
            plugin.getTimeTrialController().endSession(player);
        }

        stt.setPlayerTrack(player, trackName, mysql.getTrackOwner(trackName));
        stt.show(player, trackName);

        // 🚤 Gestão do Veículo
        if (player.getVehicle() instanceof Boat oldBoat) {
            player.leaveVehicle();
            api.deleteBoat(oldBoat);
        }

        // Teleporte e Mensagens
        player.teleport(loc);
        String langCode = mysql.getPlayerLanguage(player.getUniqueId());
        player.sendMessage(plugin.getTranslation("timetrial_teleport", langCode, "{track}", trackName));

        // Spawn do novo barco e persistência
        api.spawnBoat(player, false, false, false);
        plugin.setLastTimeTrialTrack(player.getUniqueId(), trackName);
    }
}