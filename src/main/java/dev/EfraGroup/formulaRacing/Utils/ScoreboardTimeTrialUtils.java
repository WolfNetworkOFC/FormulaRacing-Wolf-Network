package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager.TrackRecord;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ScoreboardTimeTrialUtils {

    private final Map<UUID, String> playerTracks = new HashMap<>();
    private final Map<UUID, FastBoard> boards = new HashMap<>();
    private final DatabaseManager mysql;
    private boolean running = false;

    public ScoreboardTimeTrialUtils(DatabaseManager mysql) {
        this.mysql = mysql;
    }

    public void startAutoUpdate() {
        if (running) return;
        running = true;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String trackName = playerTracks.get(player.getUniqueId());
                    if (trackName != null) show(player, trackName);
                }
            }
        }.runTaskTimer(FormulaRacing.getInstance(), 0L, 120L);
    }

    public void setPlayerTrack(Player player, String trackName) {
        playerTracks.put(player.getUniqueId(), trackName);
    }

    public void clearPlayerTrack(Player player) {
        playerTracks.remove(player.getUniqueId());
        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null) board.delete();
    }

    // Atualiza scoreboard
    public boolean show(Player player, String trackName) {
        if (player == null || !player.isOnline()) return false;

        boolean enabled = mysql.getTimeTrialScoreboard(player.getUniqueId());
        FastBoard board = boards.get(player.getUniqueId());

        // Se estiver desativado, remove a board
        if (!enabled) {
            if (board != null) {
                board.delete();
                boards.remove(player.getUniqueId());
            }
            return false;
        }

        // Criar board se não existir
        if (board == null) {
            board = new FastBoard(player);
            boards.put(player.getUniqueId(), board);
        }

        // Pega records e ordena (Melhores primeiro)
        List<TrackRecord> allRecords = mysql.getTopTimes(trackName);
        if (allRecords == null) allRecords = new ArrayList<>();

        allRecords.sort(
                Comparator.<TrackRecord>comparingInt(tr -> tr.isFinished() ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(TrackRecord::getCheckpointsReached).reversed())
                        .thenComparingDouble(TrackRecord::getTime)
        );

        // Encontra a posição exata do jogador na lista
        int myPos = -1;
        for (int i = 0; i < allRecords.size(); i++) {
            if (allRecords.get(i).getPlayerName().equals(player.getName())) {
                myPos = i;
                break;
            }
        }

        board.updateTitle("§e§l-TimeTrial-");

        List<String> lines = new ArrayList<>();
        lines.add("§e§lTrack: §f" + trackName);
        String creator = mysql.getTrackOwner(trackName);
        if (creator != null) lines.add("§7By " + creator);
        lines.add("");
        lines.add("§6Leaderboard:");

        // --- LÓGICA DE SELEÇÃO DE LINHAS ---
        TrackRecord firstPlace = null;
        boolean includeSeparator = false;
        List<TrackRecord> neighbors = new ArrayList<>();

        if (myPos == -1) {
            // Se o jogador não tem tempo, mostra apenas os 5 primeiros
            int end = Math.min(allRecords.size(), 5);
            for (int i = 0; i < end; i++) neighbors.add(allRecords.get(i));
        } else if (myPos < 5) {
            // Se o jogador já está no Top 5, mostra do 1 ao 5 direto
            int end = Math.min(allRecords.size(), 5);
            for (int i = 0; i < end; i++) neighbors.add(allRecords.get(i));
        } else {
            // Se o jogador está abaixo do 5º lugar (6th, 7th...)
            firstPlace = allRecords.get(0); // Garante o 1st no topo
            includeSeparator = true;

            // Pega os vizinhos: 1 acima, ele mesmo, e 1 abaixo (ou conforme houver espaço)
            int start = Math.max(1, myPos - 1); // Começa no 1 para não repetir o líder que já pegamos
            int end = Math.min(allRecords.size() - 1, myPos + 1);
            for (int i = start; i <= end; i++) {
                neighbors.add(allRecords.get(i));
            }
        }

        // --- MONTAGEM FINAL DA SCOREBOARD ---

        // 1. Adiciona o líder se ele estiver isolado
        if (firstPlace != null) {
            lines.add(formatRecordLine(firstPlace, 1, player.getName()));
        }

        // 2. Adiciona o separador se o jogador estiver longe do topo
        if (includeSeparator) {
            lines.add("§8-----------------");
        }

        // 3. Adiciona os vizinhos (ou o Top 5 normal)
        for (TrackRecord tr : neighbors) {
            int actualPos = allRecords.indexOf(tr) + 1;
            lines.add(formatRecordLine(tr, actualPos, player.getName()));
        }

        lines.add("");
        lines.add("§ewolfnetwork.com.br");

        board.updateLines(lines);
        return true;
    }

    /**
     * Método auxiliar para formatar cada linha da Leaderboard
     */
    private String formatRecordLine(TrackRecord tr, int pos, String observerName) {
        boolean isMe = tr.getPlayerName().equals(observerName);

        // Sufixo da posição (1st, 2nd, 3rd, 4th...)
        String suffix;
        if (pos == 1) suffix = "st";
        else if (pos == 2) suffix = "nd";
        else if (pos == 3) suffix = "rd";
        else suffix = "th";

        // Cores baseadas na posição e se é o próprio jogador
        String color = isMe ? "§f§l" : switch (pos) {
            case 1 -> "§6"; // Ouro
            case 2 -> "§7"; // Prata
            case 3 -> "§c"; // Bronze (Cobre)
            default -> "§7"; // Cinza para os demais
        };

        // Formatação do tempo/CP
        String timeDisplay = tr.isFinished()
                ? formatTime(tr.getTime())
                : tr.getCheckpointsReached() + "CP(" + formatTime(tr.getTime()) + ")";

        String nameDisplay = isMe ? "§r§lVocê" : "§r" + tr.getPlayerName();

        // Exemplo: §61st §f0.45.231 §6PlayerName
        return color + pos + suffix + " §f" + nameDisplay + " " + timeDisplay;
    }

    public String formatTime(double timeInSeconds) {
        long minutes = (long) (timeInSeconds / 60);
        long seconds = (long) (timeInSeconds % 60);
        long millis = (long) ((timeInSeconds - Math.floor(timeInSeconds)) * 1000);

        if (minutes > 0) return String.format("%d:%02d.%03d", minutes, seconds, millis);
        return String.format("%d.%03d", seconds, millis);
    }

    public void clearAll() {
        for (FastBoard board : boards.values()) board.delete();
        boards.clear();
        playerTracks.clear();
    }
}
