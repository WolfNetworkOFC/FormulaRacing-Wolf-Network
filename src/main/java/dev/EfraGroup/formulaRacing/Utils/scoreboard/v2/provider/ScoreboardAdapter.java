package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.provider;

import java.util.List;
import org.bukkit.entity.Player;

public interface ScoreboardAdapter {
    void create(Player player);

    void updateTitle(Player player, String title);

    void updateLines(Player player, List<String> lines);

    void delete(Player player);

    boolean isHealthy(Player player);
}
