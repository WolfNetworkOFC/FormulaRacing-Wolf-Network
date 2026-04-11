package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Heat.Heats;
import org.bukkit.entity.Player;

public interface RaceScoreboardService {
    void addPlayer(Player player, Heats heat);

    void removePlayer(Player player);

    void removeHeat(Heats heat);

    void addSpectator(Player spectator, Heats heat);

    void removeSpectator(Player spectator);

    void shutdown();
}
