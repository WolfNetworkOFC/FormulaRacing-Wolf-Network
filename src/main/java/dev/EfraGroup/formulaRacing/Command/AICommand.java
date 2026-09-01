package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.AI.AIOpponentManager;
import dev.EfraGroup.formulaRacing.AI.AIRacingLine;
import dev.EfraGroup.formulaRacing.AI.AIRacingLineManager;
import dev.EfraGroup.formulaRacing.AI.AIRacingLineRecorder;
import dev.EfraGroup.formulaRacing.Controllers.HeatDriverCommandService;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comandos para gerenciar o sistema de IA de oponentes.
 */
@CommandAlias("ai|opponent")
public class AICommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final AIOpponentManager aiManager;
    private final AIRacingLineManager racingLineManager;
    private final HeatDriverCommandService heatDriverService;

    public AICommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.aiManager = plugin.getAIOpponentManager();
        this.racingLineManager = plugin.getAIRacingLineManager();
        this.heatDriverService = new HeatDriverCommandService(plugin);
    }

    private String tr(Player player, String key, String... placeholders) {
        return plugin.getTranslationUtil().getTranslated(player, key, placeholders);
    }

    @Default
    @Description("Mostra informações do sistema de IA")
    public void onDefault(Player player) {
        player.sendMessage("");
        player.sendMessage(tr(player, "ai_separator_gold"));
        player.sendMessage(tr(player, "ai_info_title"));
        player.sendMessage("");
        player.sendMessage(tr(player, "ai_info_tracks", "{count}", String.valueOf(racingLineManager.getTrackCount())));
        player.sendMessage(tr(player, "ai_info_opponents", "{count}", String.valueOf(aiManager.getAIOpponents().size())));
        player.sendMessage("");
        player.sendMessage(tr(player, "ai_info_commands"));
        player.sendMessage(tr(player, "ai_info_cmd_line"));
        player.sendMessage(tr(player, "ai_info_cmd_record"));
        player.sendMessage(tr(player, "ai_info_cmd_add"));
        player.sendMessage(tr(player, "ai_info_cmd_remove"));
        player.sendMessage(tr(player, "ai_info_cmd_difficulty"));
        player.sendMessage(tr(player, "ai_info_cmd_list"));
        player.sendMessage(tr(player, "ai_separator_gold"));
    }

    @Subcommand("record")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Grava uma linha de corrida dando uma volta")
    public void onRecord(Player player, String trackName) {
        AIRacingLineRecorder recorder = racingLineManager.getRecorder();

        if (recorder.isRecording(player.getUniqueId())) {
            player.sendMessage(tr(player, "ai_record_already"));
            player.sendMessage(tr(player, "ai_record_stop_hint"));
            return;
        }

        if (!recorder.startRecording(player, trackName)) {
            player.sendMessage(tr(player, "ai_record_start_failed"));
        }
    }

    @Subcommand("record stop")
    @CommandPermission("formularacing.admin")
    @Description("Para a gravação de linha de corrida")
    public void onRecordStop(Player player) {
        racingLineManager.getRecorder().stopRecording(player);
    }

    @Subcommand("line")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Gerencia a linha de corrida de uma pista")
    public void onLine(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);

        player.sendMessage("");
        player.sendMessage(tr(player, "ai_separator_gold"));
        player.sendMessage(tr(player, "ai_line_title", "{track}", trackName));
        player.sendMessage("");
        player.sendMessage(tr(player, "ai_line_points", "{count}", String.valueOf(line.getIdealLineSize())));
        player.sendMessage(tr(player, "ai_line_braking", "{count}", String.valueOf(line.getBrakingPoints().size())));
        player.sendMessage(tr(player, "ai_line_accel", "{count}", String.valueOf(line.getAccelerationPoints().size())));
        player.sendMessage("");
        player.sendMessage(tr(player, "ai_line_commands"));
        player.sendMessage(tr(player, "ai_line_cmd_add", "{track}", trackName));
        player.sendMessage(tr(player, "ai_line_cmd_addbrake", "{track}", trackName));
        player.sendMessage(tr(player, "ai_line_cmd_addaccel", "{track}", trackName));
        player.sendMessage(tr(player, "ai_line_cmd_clear", "{track}", trackName));
        player.sendMessage(tr(player, "ai_line_cmd_generate", "{track}", trackName));
        player.sendMessage(tr(player, "ai_separator_gold"));
    }

    @Subcommand("line add")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Adiciona o ponto atual à linha de corrida")
    public void onLineAdd(Player player, String trackName, @Default("0.5") Double speed) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);
        line.addIdealLinePoint(player.getLocation(), speed);

        player.sendMessage(tr(player, "ai_line_added", "{track}", trackName,
                "{speed}", String.format("%.2f", speed)));
        player.sendMessage(tr(player, "ai_line_total", "{count}", String.valueOf(line.getIdealLineSize())));
    }

    @Subcommand("line addbrake")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Adiciona o ponto atual como ponto de frenagem")
    public void onLineAddBrake(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);
        line.addBrakingPoint(player.getLocation());

        player.sendMessage(tr(player, "ai_line_brake_added", "{track}", trackName));
        player.sendMessage(tr(player, "ai_line_brake_total", "{count}", String.valueOf(line.getBrakingPoints().size())));
    }

    @Subcommand("line addaccel")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Adiciona o ponto atual como ponto de aceleração")
    public void onLineAddAccel(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);
        line.addAccelerationPoint(player.getLocation());

        player.sendMessage(tr(player, "ai_line_accel_added", "{track}", trackName));
        player.sendMessage(tr(player, "ai_line_accel_total", "{count}", String.valueOf(line.getAccelerationPoints().size())));
    }

    @Subcommand("line clear")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Limpa a linha de corrida de uma pista")
    public void onLineClear(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);
        line.clear();
        player.sendMessage(tr(player, "ai_line_cleared", "{track}", trackName));
    }

    @Subcommand("line generate")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Gera automaticamente uma linha de corrida básica")
    public void onLineGenerate(Player player, String trackName) {
        racingLineManager.generateBasicRacingLine(trackName);
        player.sendMessage(tr(player, "ai_line_generated", "{track}", trackName));
    }

    @Subcommand("line trim")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Ajusta a linha gravada para exatamente uma volta (fecha o loop)")
    public void onLineTrim(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);
        if (!line.isUsable()) {
            player.sendMessage(tr(player, "ai_line_trim_no_line", "{track}", trackName));
            return;
        }

        boolean trimmed = racingLineManager.trimLineToSingleLap(line, trackName);
        if (trimmed) {
            racingLineManager.saveRacingLine(trackName, line);
            player.sendMessage(tr(player, "ai_line_trim_done",
                    "{track}", trackName, "{count}", String.valueOf(line.getIdealLineSize())));
        } else {
            player.sendMessage(tr(player, "ai_line_trim_failed", "{track}", trackName));
        }
    }

    @Subcommand("difficulty")
    @CommandCompletion("@players easy|medium|hard")
    @CommandPermission("formularacing.admin")
    @Description("Define a dificuldade de um oponente IA")
    public void onDifficulty(Player player, String targetName, String difficultyStr) {
        AIOpponentManager.AIDifficulty difficulty;
        try {
            difficulty = AIOpponentManager.AIDifficulty.valueOf(difficultyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(tr(player, "ai_difficulty_invalid"));
            return;
        }

        List<AIOpponentManager.AIOpponent> matches = new ArrayList<>(aiManager.findByDisplayName(targetName));
        if (matches.isEmpty()) {
            player.sendMessage(tr(player, "ai_difficulty_not_found", "{name}", targetName));
            return;
        }
        if (matches.size() > 1) {
            player.sendMessage(tr(player, "ai_difficulty_ambiguous"));
            return;
        }

        AIOpponentManager.AIOpponent ai = matches.get(0);
        ai.setDifficulty(difficulty);

        player.sendMessage(tr(player, "ai_difficulty_set",
                "{name}", ai.getDisplayName(), "{difficulty}", difficulty.name()));
        player.sendMessage(tr(player, "ai_difficulty_speed",
                "{value}", String.format("%.0f%%", difficulty.getSpeedMultiplier() * 100)));
        player.sendMessage(tr(player, "ai_difficulty_error",
                "{value}", String.format("%.0f%%", difficulty.getErrorRate() * 100)));
        player.sendMessage(tr(player, "ai_difficulty_accuracy",
                "{value}", String.format("%.0f%%", difficulty.getLineAccuracy() * 100)));
    }

    @Subcommand("list")
    @CommandPermission("formularacing.admin")
    @Description("Lista todos os oponentes IA ativos")
    public void onList(Player player) {
        player.sendMessage("");
        player.sendMessage(tr(player, "ai_separator_gold"));
        player.sendMessage(tr(player, "ai_list_title"));
        player.sendMessage("");

        if (aiManager.getAIOpponents().isEmpty()) {
            player.sendMessage(tr(player, "ai_list_empty"));
        } else {
            for (AIOpponentManager.AIOpponent ai : aiManager.getAIOpponents().values()) {
                String difficultyName = ai.getDifficulty().getName();
                double learningProgress = ai.getLearningProgress() * 100;
                int lapsCompleted = ai.getLapsCompleted();
                double bestLapTime = ai.getBestLapTime();

                String progressColor = learningProgress >= 80 ? ChatColor.GREEN.toString()
                        : learningProgress >= 50 ? ChatColor.YELLOW.toString()
                        : ChatColor.RED.toString();

                player.sendMessage(ChatColor.WHITE + "  " + ai.getDisplayName());
                player.sendMessage(tr(player, "ai_list_difficulty", "{difficulty}", difficultyName));
                player.sendMessage(tr(player, "ai_list_learning", "{progress}", progressColor + String.format("%.0f%%", learningProgress)));
                player.sendMessage(tr(player, "ai_list_laps", "{count}", String.valueOf(lapsCompleted)));
                if (bestLapTime < Double.MAX_VALUE) {
                    player.sendMessage(tr(player, "ai_list_best_lap", "{time}", String.format("%.2f", bestLapTime)));
                }
                player.sendMessage("");
            }
        }

        player.sendMessage(tr(player, "ai_separator_gold"));
    }

    @Subcommand("add")
    @CommandCompletion("@heats easy|medium|hard")
    @CommandPermission("formularacing.admin")
    @Description("Adiciona um oponente IA a um heat")
    public void onAdd(Player player, Heats heat, String difficultyStr, @Default("1") Integer count) {
        AIOpponentManager.AIDifficulty difficulty;
        try {
            difficulty = AIOpponentManager.AIDifficulty.valueOf(difficultyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(tr(player, "ai_difficulty_invalid"));
            return;
        }

        if (count < 1 || count > 20) {
            player.sendMessage(tr(player, "ai_add_count_invalid"));
            return;
        }

        int added = 0;
        for (int i = 0; i < count; i++) {
            UUID aiUuid = UUID.randomUUID();
            String aiName = "AI-" + difficulty.name() + "-" + aiUuid.toString().substring(0, 8);
            int startPosition = heat.getDrivers().size() + 1;

            var result = heatDriverService.addDriverSync(heat, aiUuid, aiName, startPosition);
            if (result.getStatus().name().equals("SUCCESS")) {
                Driver aiDriver = heat.getDriver(aiUuid);
                if (aiDriver != null) {
                    aiManager.createAIOpponent(aiDriver, aiName, difficulty);
                    added++;
                }
            }
        }

        if (added > 0) {
            aiManager.startAIForHeat(heat);
            player.sendMessage(tr(player, "ai_add_success",
                    "{count}", String.valueOf(added),
                    "{difficulty}", difficulty.name(),
                    "{heat}", String.valueOf(heat.getId())));
            player.sendMessage(tr(player, "ai_add_total_drivers", "{count}", String.valueOf(heat.getDrivers().size())));
            player.sendMessage(tr(player, "ai_add_spawn_hint"));
        } else {
            player.sendMessage(tr(player, "ai_add_failed"));
        }
    }

    @Subcommand("remove")
    @CommandCompletion("@heats")
    @CommandPermission("formularacing.admin")
    @Description("Remove todos os oponentes IA de um heat")
    public void onRemove(Player player, Heats heat) {
        List<Driver> aiDrivers = heat.getDrivers().values().stream()
                .filter(driver -> aiManager.isAIOpponent(driver.getUuid()))
                .toList();

        int removed = 0;
        for (Driver driver : aiDrivers) {
            AIOpponentManager.AIOpponent ai = aiManager.getAIOpponent(driver.getUuid());
            String aiName = ai != null ? ai.getDisplayName() : String.valueOf(driver.getUuid());
            var result = heatDriverService.removeDriverSync(heat, driver.getUuid(), aiName);
            if (result.getStatus().name().equals("SUCCESS")) {
                aiManager.removeAIOpponent(driver.getUuid());
                removed++;
            }
        }

        if (removed > 0) {
            player.sendMessage(tr(player, "ai_remove_success",
                    "{count}", String.valueOf(removed),
                    "{heat}", String.valueOf(heat.getId())));
            player.sendMessage(tr(player, "ai_add_total_drivers", "{count}", String.valueOf(heat.getDrivers().size())));
        } else {
            player.sendMessage(tr(player, "ai_remove_none", "{heat}", String.valueOf(heat.getId())));
        }
    }

    @Subcommand("info")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Mostra informações detalhadas da linha de corrida")
    public void onInfo(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);

        player.sendMessage("");
        player.sendMessage(tr(player, "ai_separator_gold"));
        player.sendMessage(tr(player, "ai_info_line_title", "{track}", trackName));
        player.sendMessage("");
        player.sendMessage(tr(player, "ai_line_points", "{count}", String.valueOf(line.getIdealLineSize())));
        player.sendMessage(tr(player, "ai_line_braking", "{count}", String.valueOf(line.getBrakingPoints().size())));
        player.sendMessage(tr(player, "ai_line_accel", "{count}", String.valueOf(line.getAccelerationPoints().size())));

        if (line.getIdealLineSize() > 0) {
            Location currentLoc = player.getLocation();
            Location closest = line.getClosestIdealLinePoint(currentLoc);
            if (closest != null) {
                double distance = currentLoc.distance(closest);
                double idealSpeed = line.getIdealSpeedAt(currentLoc);
                double idealDirection = line.getIdealDirection(currentLoc);

                player.sendMessage("");
                player.sendMessage(tr(player, "ai_info_status"));
                player.sendMessage(tr(player, "ai_info_distance", "{distance}", String.format("%.2f", distance)));
                player.sendMessage(tr(player, "ai_info_ideal_speed", "{value}", String.format("%.0f%%", idealSpeed * 100)));
                player.sendMessage(tr(player, "ai_info_ideal_direction", "{value}", String.format("%.1f°", idealDirection)));
            }
        }

        player.sendMessage(tr(player, "ai_separator_gold"));
    }
}
