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

    @Default
    @Description("Mostra informações do sistema de IA")
    public void onDefault(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Sistema de IA de Oponentes");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Pistas com linha: " + ChatColor.WHITE + racingLineManager.getTrackCount());
        player.sendMessage(ChatColor.GRAY + "  Oponentes ativos: " + ChatColor.WHITE + aiManager.getAIOpponents().size());
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Comandos disponíveis:");
        player.sendMessage(ChatColor.WHITE + "    /ai line <pista> - Gerenciar linha de corrida");
        player.sendMessage(ChatColor.WHITE + "    /ai record <pista> - Gravar linha por volta");
        player.sendMessage(ChatColor.WHITE + "    /ai add <heat> <dificuldade> [qtd] - Adicionar IAs");
        player.sendMessage(ChatColor.WHITE + "    /ai remove <heat> - Remover IAs");
        player.sendMessage(ChatColor.WHITE + "    /ai difficulty <piloto> <dificuldade> - Definir dificuldade");
        player.sendMessage(ChatColor.WHITE + "    /ai list - Listar oponentes IA");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("record")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Grava uma linha de corrida dando uma volta")
    public void onRecord(Player player, String trackName) {
        AIRacingLineRecorder recorder = racingLineManager.getRecorder();

        if (recorder.isRecording(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "✗ Você já está gravando uma linha de corrida!");
            player.sendMessage(ChatColor.GRAY + "  Use /ai record stop para cancelar");
            return;
        }

        if (!recorder.startRecording(player, trackName)) {
            player.sendMessage(ChatColor.RED + "✗ Não foi possível iniciar a gravação!");
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
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Linha de Corrida: " + ChatColor.WHITE + trackName);
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Pontos na linha: " + ChatColor.WHITE + line.getIdealLineSize());
        player.sendMessage(ChatColor.GRAY + "  Pontos de frenagem: " + ChatColor.WHITE + line.getBrakingPoints().size());
        player.sendMessage(ChatColor.GRAY + "  Pontos de aceleração: " + ChatColor.WHITE + line.getAccelerationPoints().size());
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Comandos:");
        player.sendMessage(ChatColor.WHITE + "    /ai line " + trackName + " add - Adicionar ponto atual");
        player.sendMessage(ChatColor.WHITE + "    /ai line " + trackName + " addbrake - Adicionar ponto de frenagem");
        player.sendMessage(ChatColor.WHITE + "    /ai line " + trackName + " addaccel - Adicionar ponto de aceleração");
        player.sendMessage(ChatColor.WHITE + "    /ai line " + trackName + " clear - Limpar linha");
        player.sendMessage(ChatColor.WHITE + "    /ai line " + trackName + " generate - Gerar linha automática");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Subcommand("line add")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Adiciona o ponto atual à linha de corrida")
    public void onLineAdd(Player player, String trackName, @Default("0.5") Double speed) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);
        line.addIdealLinePoint(player.getLocation(), speed);

        player.sendMessage(ChatColor.GREEN + "✓ Ponto adicionado à linha de " + trackName +
                " (velocidade: " + String.format("%.2f", speed) + ")");
        player.sendMessage(ChatColor.GRAY + "  Total de pontos: " + line.getIdealLineSize());
    }

    @Subcommand("line addbrake")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Adiciona o ponto atual como ponto de frenagem")
    public void onLineAddBrake(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);
        line.addBrakingPoint(player.getLocation());

        player.sendMessage(ChatColor.GREEN + "✓ Ponto de frenagem adicionado à linha de " + trackName);
        player.sendMessage(ChatColor.GRAY + "  Total de pontos de frenagem: " + line.getBrakingPoints().size());
    }

    @Subcommand("line addaccel")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Adiciona o ponto atual como ponto de aceleração")
    public void onLineAddAccel(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);
        line.addAccelerationPoint(player.getLocation());

        player.sendMessage(ChatColor.GREEN + "✓ Ponto de aceleração adicionado à linha de " + trackName);
        player.sendMessage(ChatColor.GRAY + "  Total de pontos de aceleração: " + line.getAccelerationPoints().size());
    }

    @Subcommand("line clear")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Limpa a linha de corrida de uma pista")
    public void onLineClear(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);
        line.clear();
        player.sendMessage(ChatColor.YELLOW + "⚠ Linha de corrida de " + trackName + " limpa!");
    }

    @Subcommand("line generate")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Gera automaticamente uma linha de corrida básica")
    public void onLineGenerate(Player player, String trackName) {
        racingLineManager.generateBasicRacingLine(trackName);
        player.sendMessage(ChatColor.GREEN + "✓ Linha de corrida básica gerada para " + trackName);
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
            player.sendMessage(ChatColor.RED + "✗ Dificuldade inválida! Use: easy, medium, hard");
            return;
        }

        List<AIOpponentManager.AIOpponent> matches = new ArrayList<>(aiManager.findByDisplayName(targetName));
        if (matches.isEmpty()) {
            player.sendMessage(ChatColor.RED + "✗ Nenhuma IA encontrada com o nome " + targetName);
            return;
        }
        if (matches.size() > 1) {
            player.sendMessage(ChatColor.RED + "✗ Há mais de uma IA com esse nome. Use um nome mais específico.");
            return;
        }

        AIOpponentManager.AIOpponent ai = matches.get(0);
        ai.setDifficulty(difficulty);

        player.sendMessage(ChatColor.GREEN + "✓ Dificuldade definida para " + ai.getDisplayName() + ": " + difficulty.name());
        player.sendMessage(ChatColor.GRAY + "  Velocidade: " + String.format("%.0f%%", difficulty.getSpeedMultiplier() * 100));
        player.sendMessage(ChatColor.GRAY + "  Taxa de erro: " + String.format("%.0f%%", difficulty.getErrorRate() * 100));
        player.sendMessage(ChatColor.GRAY + "  Precisão: " + String.format("%.0f%%", difficulty.getLineAccuracy() * 100));
    }

    @Subcommand("list")
    @CommandPermission("formularacing.admin")
    @Description("Lista todos os oponentes IA ativos")
    public void onList(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Oponentes IA Ativos");
        player.sendMessage("");

        if (aiManager.getAIOpponents().isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  Nenhum oponente IA ativo no momento.");
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
                player.sendMessage(ChatColor.GRAY + "    Dificuldade: " + ChatColor.AQUA + difficultyName);
                player.sendMessage(ChatColor.GRAY + "    Aprendizado: " + progressColor + String.format("%.0f%%", learningProgress));
                player.sendMessage(ChatColor.GRAY + "    Voltas: " + ChatColor.WHITE + lapsCompleted);
                if (bestLapTime < Double.MAX_VALUE) {
                    player.sendMessage(ChatColor.GRAY + "    Melhor volta: " + ChatColor.WHITE + String.format("%.2f", bestLapTime) + "s");
                }
                player.sendMessage("");
            }
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
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
            player.sendMessage(ChatColor.RED + "✗ Dificuldade inválida! Use: easy, medium, hard");
            return;
        }

        if (count < 1 || count > 20) {
            player.sendMessage(ChatColor.RED + "✗ Quantidade inválida! Use entre 1 e 20");
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
            player.sendMessage(ChatColor.GREEN + "✓ Adicionado(s) " + added + " oponente(s) IA (" + difficulty.name() + ") ao heat " + heat.getId());
            player.sendMessage(ChatColor.GRAY + "  Total de pilotos no heat: " + heat.getDrivers().size());
            player.sendMessage(ChatColor.GRAY + "  As IAs físicas serão spawnadas no mundo quando o heat iniciar.");
        } else {
            player.sendMessage(ChatColor.RED + "✗ Não foi possível adicionar oponentes IA ao heat");
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
            player.sendMessage(ChatColor.GREEN + "✓ Removido(s) " + removed + " oponente(s) IA do heat " + heat.getId());
            player.sendMessage(ChatColor.GRAY + "  Total de pilotos no heat: " + heat.getDrivers().size());
        } else {
            player.sendMessage(ChatColor.YELLOW + "⚠ Nenhum oponente IA encontrado no heat " + heat.getId());
        }
    }

    @Subcommand("info")
    @CommandCompletion("@tracks")
    @CommandPermission("formularacing.admin")
    @Description("Mostra informações detalhadas da linha de corrida")
    public void onInfo(Player player, String trackName) {
        AIRacingLine line = racingLineManager.getRacingLine(trackName);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  Informações da Linha: " + ChatColor.WHITE + trackName);
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Pontos na linha ideal: " + ChatColor.WHITE + line.getIdealLineSize());
        player.sendMessage(ChatColor.GRAY + "  Pontos de frenagem: " + ChatColor.WHITE + line.getBrakingPoints().size());
        player.sendMessage(ChatColor.GRAY + "  Pontos de aceleração: " + ChatColor.WHITE + line.getAccelerationPoints().size());

        if (line.getIdealLineSize() > 0) {
            Location currentLoc = player.getLocation();
            Location closest = line.getClosestIdealLinePoint(currentLoc);
            if (closest != null) {
                double distance = currentLoc.distance(closest);
                double idealSpeed = line.getIdealSpeedAt(currentLoc);
                double idealDirection = line.getIdealDirection(currentLoc);

                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "  Status atual:");
                player.sendMessage(ChatColor.WHITE + "    Distância da linha: " + ChatColor.AQUA + String.format("%.2f", distance) + " blocos");
                player.sendMessage(ChatColor.WHITE + "    Velocidade ideal: " + ChatColor.AQUA + String.format("%.0f%%", idealSpeed * 100));
                player.sendMessage(ChatColor.WHITE + "    Direção ideal: " + ChatColor.AQUA + String.format("%.1f°", idealDirection));
            }
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }
}
