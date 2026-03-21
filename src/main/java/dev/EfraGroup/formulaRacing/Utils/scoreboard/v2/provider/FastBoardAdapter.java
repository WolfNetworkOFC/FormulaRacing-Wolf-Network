package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.provider;

import fr.mrmicky.fastboard.FastBoard;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public class FastBoardAdapter implements ScoreboardAdapter {
    private final Map<UUID, FastBoard> boards = new HashMap<>();

    @Override
    public void create(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        FastBoard board = this.boards.get(player.getUniqueId());
        if (board != null) {
            return;
        }

        this.boards.put(player.getUniqueId(), new FastBoard(player));
    }

    @Override
    public void updateTitle(Player player, String title) {
        if (player == null || !player.isOnline()) {
            return;
        }
        FastBoard board = this.boards.get(player.getUniqueId());
        if (board == null) {
            this.create(player);
            board = this.boards.get(player.getUniqueId());
        }
        if (board != null) {
            board.updateTitle(title);
        }
    }

    @Override
    public void updateLines(Player player, List<String> lines) {
        if (player == null || !player.isOnline()) {
            return;
        }
        FastBoard board = this.boards.get(player.getUniqueId());
        if (board == null) {
            this.create(player);
            board = this.boards.get(player.getUniqueId());
        }
        if (board != null) {
            board.updateLines(lines);
        }
    }

    @Override
    public void delete(Player player) {
        FastBoard board = this.boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    @Override
    public boolean isHealthy(Player player) {
        return player != null && player.isOnline();
    }

    public void shutdown() {
        for (FastBoard board : this.boards.values()) {
            board.delete();
        }
        this.boards.clear();
    }
}
