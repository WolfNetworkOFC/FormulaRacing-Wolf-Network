package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.BoatUtils.NocolManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.EventsManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
//import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Listener.RegionListener;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialMenuUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public class TimeTrialCommandHandler implements CommandExecutor {

    public enum HeatState {SETUP, LOADED, STARTING, STARTED, FINISHED}

    private final DatabaseManager mysql;
    private final FormulaRacing plugin;
    private final PacketSender packetsender;
    private final TimerUtils timerUtils;
    private final RegionListener rcl;
    private final APIFormulaRacing api;
    private final ScoreboardTimeTrialUtils stt;
    private final EventsManager ev;

    public TimeTrialCommandHandler(DatabaseManager mysql, FormulaRacing plugin, PacketSender packetsender, TimerUtils timerUtils, RegionListener rcl, APIFormulaRacing api, ScoreboardTimeTrialUtils stt, EventsManager ev) {
        this.mysql = mysql;
        this.plugin = plugin;
        this.packetsender = packetsender;
        this.timerUtils = timerUtils;
        this.rcl = rcl;
        this.api = api;
        this.stt = stt;
        this.ev = ev;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        String lang_code = mysql.getPlayerLanguage(player.getUniqueId());


        // ⛔ NOVA VALIDAÇÃO: Bloqueia se o jogador estiver em um duelo ativo
        if (mysql.isPlayerInActiveDuel(player.getUniqueId())) {
            player.sendMessage("§c§lERRO §8» §7Você não pode iniciar um Time Trial enquanto estiver em um §b§lDUELO §7ativo!");
            player.sendMessage("§7Termine sua corrida atual primeiro.");
            // Opcional: tocar um som de erro
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // O código original continua abaixo...
        if (args.length == 0) {
            new TimeTrialMenuUtils(plugin, mysql, api, packetsender, timerUtils, stt).open(player);
            return true;
        }

        String trackName = args[0];


        // ================= SALVA TEMPO PARCIAL =================
        String lastTrack = plugin.getLastTimeTrialTrack(player.getUniqueId());
        if (lastTrack != null) {
            TimerUtils.PlayerTimerData data = timerUtils.getTimerData(player, lastTrack);
            if (data != null) {
                double currentTime = timerUtils.getPlayerElapsedTime(player, lastTrack);
                int checkpoints = data.getCheckpointsReached().size();

                Object[] bestData = mysql.getPlayerBestTime(player.getUniqueId().toString(), lastTrack);
                if (bestData != null) {
                    double bestTime = (Double) bestData[0];
                    int bestCheckpoints = (Integer) bestData[1];
                    boolean finished = (Boolean) bestData[2];

                    if (checkpoints > 0) {
                        if (Objects.equals(lang_code, "pt_BR")) {
                            mysql.savePartialTime(player.getUniqueId(), player.getName(), lastTrack, currentTime, checkpoints);
                            player.sendMessage("§2Você completou §f" + checkpoints + "§2checkpoints na §f" + trackName + "§2em §f" + currentTime);
                        } else if (Objects.equals(lang_code, "en_US")) {
                            mysql.savePartialTime(player.getUniqueId(), player.getName(), lastTrack, currentTime, checkpoints);
                            player.sendMessage("§2You completed §f" + checkpoints + "§2checkpoints on §f" + trackName + "§2in §f" + currentTime);
                        }
                    } else {
                        return false;
                    }
                }
                NocolManager.setCollisionMode(player, false);
                timerUtils.stopTimer(player, lastTrack);
            }
        }
            if (mysql.trackHaveBoatUtils(trackName) && !FormulaRacing.hasOpenBoatUtilsMod(player)) {
                player.sendMessage("§c " + plugin.getDirectTranslation("does_not_have_boatutils", lang_code));
                return true;
            }

            // ================= VERIFICAÇÕES DE PISTA =================
            Location loc = mysql.getTrackSpawn(trackName);
            if (loc == null) {
                player.sendMessage("§c" + plugin.getDirectTranslation("track_not_found", lang_code));
                return true;
            }

            if (!mysql.isTrackOpen(trackName)) {
                player.sendMessage("§c" + plugin.getDirectTranslation("track_is_closed", lang_code));
                return true;
            }

            packetsender.sendBoatSetting(player, 0);
            packetsender.applyBoatUtilsToPlayer(player, trackName);

            // ================= TELEPORTA E INICIA =================
            timerUtils.stopTimer(player);
            player.teleport(loc);
            player.sendMessage(plugin.getTranslation("timetrial_teleport", lang_code, "{track}", trackName));

            api.spawnBoat(player, false, false, false);
            timerUtils.stopTimer(player);

            plugin.setLastTimeTrialTrack(player.getUniqueId(), trackName);
            stt.setPlayerTrack(player, trackName);
            stt.show(player, trackName);

            return true;
        }

    private String formatTime(double time) {
        int minutes = (int) (time / 60);
        int seconds = (int) (time % 60);
        int millis = (int) ((time - Math.floor(time)) * 1000);

        if (minutes > 0) {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
        } else {
            return String.format("%d.%03d", seconds, millis);
        }
    }
}
