package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import java.util.List;
import org.bukkit.entity.Player;

public record ScoreboardContext(
        FormulaRacing plugin,
        Heats heat,
        Player viewer,
        Driver viewerDriver,
        boolean spectator,
        List<Driver> sortedDrivers,
        int maxRows,
        boolean compact
) {
}
