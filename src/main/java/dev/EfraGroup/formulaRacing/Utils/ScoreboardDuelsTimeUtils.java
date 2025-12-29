package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardDuelsTimeUtils {

    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final TimeTrialDuelsAction ttda; // Necessário para pegar o tempo real
    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<>();

    // Armazena dados contextuais para a task saber o que exibir
    private final Map<UUID, DuelContext> duelContexts = new ConcurrentHashMap<>();

    public ScoreboardDuelsTimeUtils(FormulaRacing plugin, DatabaseManager mysql, TimeTrialDuelsAction ttda) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.ttda = ttda;
        startAutoUpdateTask();
    }

    /**
     * Task que roda em repetição para atualizar todas as boards ativas
     */
    private void startAutoUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : boards.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    DuelContext ctx = duelContexts.get(uuid);

                    if (player == null || !player.isOnline() || ctx == null) {
                        removeBoard(uuid);
                        continue;
                    }

                    // Pega o tempo milissegundos do cronômetro real no TTDA
                    //long currentMillis = ttda.getPlayerTimeMillis(player);
                    String formattedTime = formatTimeFromMillis(13223);

                    // Chama o update (que já lida com o banco de forma async internamente)
                    update(player, ctx.duelId, formattedTime, ctx.currentLap, ctx.totalLaps, ctx.trackName);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // Rodando a cada 2 ticks para performance balanceada
    }

    public void applyDuelBoard(Player player, int duelId, int totalLaps, String trackName) {
        FastBoard board = new FastBoard(player);
        board.updateTitle("§b§lDUEL §f§lRACING");
        boards.put(player.getUniqueId(), board);

        // Registra o contexto para a task saber os dados fixos
        duelContexts.put(player.getUniqueId(), new DuelContext(duelId, totalLaps, trackName));
    }

    /**
     * Atualiza a volta atual do jogador (chamado quando ele passa num CP/Finish)
     */
    public void updatePlayerLap(Player player, int lap) {
        DuelContext ctx = duelContexts.get(player.getUniqueId());
        if (ctx != null) ctx.currentLap = lap;
    }

    public void update(Player player, int duelId, String currentFormattedTime, int lap, int totalLaps, String trackName) {
        FastBoard board = boards.get(player.getUniqueId());
        if (board == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int pos = mysql.getplayerpositiononduel(duelId, player);
            Object[] data = mysql.getPlayerBestTimeOnDuel(player.getUniqueId(), duelId);

            String pbDisplay = "§7--:--.---";
            if (data != null) {
                pbDisplay = "§f" + formatTime((double) data[0]);
            }

            final String finalPB = pbDisplay;
            final String posDisplay = formatPosition(pos);

            Bukkit.getScheduler().runTask(plugin, () -> {
                board.updateLines(
                        "§8------------------",
                        " §fPosição: " + posDisplay,
                        " §fVolta: §b" + lap + "§7/§b" + totalLaps,
                        "",
                        " §fTempo: §e" + currentFormattedTime,
                        " §fRecorde: " + finalPB,
                        "",
                        " §fPista: §a" + trackName,
                        "§8------------------",
                        "§ewolfnetwork.com.br"
                );
            });
        });
    }

    public void removeBoard(Player player) {
        removeBoard(player.getUniqueId());
    }

    private void removeBoard(UUID uuid) {
        FastBoard board = boards.remove(uuid);
        if (board != null) board.delete();
        duelContexts.remove(uuid);
    }

    public void clearAll() {
        boards.values().forEach(FastBoard::delete);
        boards.clear();
        duelContexts.clear();
    }

    private String formatPosition(int pos) {
        return switch (pos) {
            case 1 -> "§a§l1º LUGAR";
            case 2 -> "§e§l2º LUGAR";
            case 3 -> "§6§l3º LUGAR";
            default -> "§f§l" + pos + "º LUGAR";
        };
    }

    private String formatTime(double seconds) {
        return formatTimeFromMillis((long)(seconds * 1000));
    }

    private String formatTimeFromMillis(long millis) {
        long minutes = (millis / 60000);
        long secs = (millis % 60000) / 1000;
        long ms = millis % 1000;
        if (minutes > 0) return String.format("%d:%02d.%03d", minutes, secs, ms);
        return String.format("%d.%03d", secs, ms);
    }

    // Classe auxiliar para manter o estado do duelo do jogador
    private static class DuelContext {
        int duelId;
        int totalLaps;
        int currentLap = 0;
        String trackName;

        DuelContext(int duelId, int totalLaps, String trackName) {
            this.duelId = duelId;
            this.totalLaps = totalLaps;
            this.trackName = trackName;
        }
    }
}