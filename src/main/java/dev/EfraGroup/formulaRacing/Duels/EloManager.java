package dev.EfraGroup.formulaRacing.Duels;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ELO rating system for ranked duels. Uses the standard chess ELO formula with a
 * configurable K-factor and per-result change clamping (min/max).
 */
@RequiredArgsConstructor
@Getter
public class EloManager {

    private final FormulaRacing plugin;
    private final DatabaseManager dm;

    private int defaultElo = 1200;
    private int kFactor = 32;
    private int minChange = 1;
    private int maxChange = 50;
    private boolean configLoaded = false;

    private void loadConfig() {
        if (configLoaded) return;
        var config = plugin.getConfig();
        if (config != null && config.isConfigurationSection("elo")) {
            this.defaultElo = config.getInt("elo.default", 1200);
            this.kFactor = config.getInt("elo.kfactor", 32);
            this.minChange = config.getInt("elo.minchange", 1);
            this.maxChange = config.getInt("elo.maxchange", 50);
        }
        this.configLoaded = true;
    }

    public int getElo(UUID uuid) {
        return dm.getElo(uuid);
    }

    public List<Map<String, Object>> getLeaderboard(int limit) {
        return dm.getEloLeaderboard(limit);
    }

    /**
     * Applies the result of a duel: updates winner/loser ELO (clamped), persists wins/losses.
     *
     * @param winner the winning player's UUID
     * @param loser  the losing player's UUID
     * @return a double[] {winnerEloBefore, winnerEloAfter, loserEloBefore, loserEloAfter}
     */
    public double[] applyDuelResult(UUID winner, UUID loser) {
        loadConfig();
        int ra = getElo(winner);
        int rb = getElo(loser);

        int[] next = computeElo(ra, rb, kFactor, minChange, maxChange);
        int newRa = next[0];
        int newRb = next[1];

        dm.setElo(winner, newRa, true);
        dm.setElo(loser, newRb, false);

        return new double[] { ra, newRa, rb, newRb };
    }

    /**
     * Pure ELO computation. Given two ratings and tuning parameters, returns the
     * new ratings after the first player beats the second. Each change is clamped
     * to [minChange, maxChange] (in absolute value).
     *
     * @return int[] { newRatingA, newRatingB }
     */
    public static int[] computeElo(int ra, int rb, int kFactor, int minChange, int maxChange) {
        double ea = 1.0 / (1.0 + Math.pow(10.0, (rb - ra) / 400.0));
        double eb = 1.0 - ea;

        int rawWinner = (int) Math.round(kFactor * (1.0 - ea));
        int rawLoser = (int) Math.round(kFactor * (0.0 - eb));

        int winnerDelta = clamp(rawWinner, minChange, maxChange);
        int loserDelta = clamp(rawLoser, minChange, maxChange);

        return new int[] { ra + winnerDelta, rb + loserDelta };
    }

    private static int clamp(int value, int minChange, int maxChange) {
        if (value == 0) return 0;
        if (value > 0) return Math.min(value, maxChange);
        return -Math.min(-value, maxChange);
    }

    /**
     * Asynchronously applies a ranked duel result (safe for the main thread).
     */
    public void applyDuelResultAsync(UUID winner, UUID loser) {
        SchedulerHelper.runAsync(plugin, () -> applyDuelResult(winner, loser));
    }
}
