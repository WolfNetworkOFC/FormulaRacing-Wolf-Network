package dev.EfraGroup.formulaRacing.ScoreBoard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

public class TimeTrialScoreboard {

    public static class Record {
        public final String playerName;
        public final String time; // formato "00:00.00"
        public Record(String playerName, String time) {
            this.playerName = playerName;
            this.time = time;
        }
    }

    public void showScoreboard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective objective = scoreboard.registerNewObjective("timeTrial", "dummy", "§6- TimeTrials -");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        objective.getScore("Track:").setScore(10);
        // outros scores aqui

        player.setScoreboard(scoreboard);
    }

    /**
     * Atualiza a scoreboard do jogador com a lista de tempos e sua posição.
     * @param player Jogador
     * @param trackName Nome da pista
     * @param trackOwner Nome do dono da pista
     * @param leaderboard Lista do top 10 tempos (mínimo 1 elemento, ordenado do melhor para o pior)
     * @param yourPosition índice (0-based) da posição do jogador na lista leaderboard
     */
    public static void updateScoreboard(Player player, String trackName, String trackOwner, List<Record> leaderboard, int yourPosition) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective objective = scoreboard.registerNewObjective("timetrial", "dummy", ChatColor.BOLD + "- TimeTrials -");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = 15; // vai decrescendo, scoreboard desenha da maior para menor

        // Cabeçalho
        objective.getScore(ChatColor.YELLOW + "Track:").setScore(score--);
        objective.getScore(ChatColor.WHITE + trackName).setScore(score--);
        objective.getScore(ChatColor.GRAY + "by " + trackOwner).setScore(score--);

        objective.getScore(ChatColor.YELLOW + "Leaderboard:").setScore(score--);

        // 1st lugar do leaderboard (sempre tem pelo menos um)
        Record first = leaderboard.get(0);
        objective.getScore(ChatColor.GOLD + "1st " + first.time + " " + first.playerName).setScore(score--);

        objective.getScore(ChatColor.GRAY + "- - - - - - - - - - - -").setScore(score--);

        // Mostrar as posições perto do jogador
        // Vamos mostrar até 5 linhas (sua posição + 2 acima + 2 abaixo)
        // Ajustando se for último ou penúltimo

        int start = yourPosition - 2;
        int end = yourPosition + 2;

        if (start < 1) start = 1; // posição 0 é o primeiro (já mostrado)
        if (end >= leaderboard.size()) end = leaderboard.size() - 1;

        // Se você estiver entre os últimos 2, mostramos só até o seu máximo e menos linhas acima para não passar do início
        if (yourPosition >= leaderboard.size() - 2) {
            start = Math.max(1, leaderboard.size() - 5);
            end = leaderboard.size() - 1;
        }

        for (int i = start; i <= end; i++) {
            Record r = leaderboard.get(i);
            String posStr = getPositionString(i + 1); // i+1 pois é 0-based

            String name = r.playerName;
            // Se for o próprio jogador, mostra "yourname" e destaque
            if (i == yourPosition) {
                name = player.getName();
                objective.getScore(ChatColor.GREEN + posStr + " " + r.time + " " + name).setScore(score--);
            } else {
                objective.getScore(ChatColor.WHITE + posStr + " " + r.time + " " + name).setScore(score--);
            }
        }

        player.setScoreboard(scoreboard);
    }

    private static String getPositionString(int pos) {
        // Retorna posição com sufixo inglês (1st, 2nd, 3rd, 4th...)
        if (pos % 100 >= 11 && pos % 100 <= 13) return pos + "th";
        switch (pos % 10) {
            case 1: return pos + "st";
            case 2: return pos + "nd";
            case 3: return pos + "rd";
            default: return pos + "th";
        }
    }
}
