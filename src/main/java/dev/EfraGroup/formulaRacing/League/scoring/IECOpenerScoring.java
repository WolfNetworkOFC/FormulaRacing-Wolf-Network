package dev.EfraGroup.formulaRacing.League.scoring;

import java.util.List;

public class IECOpenerScoring implements ScoringSystem {

    private static final List<Integer> DISTRIBUTION = java.util.Arrays.asList(
            15, 12, 10, 8, 6, 5, 4, 3, 2, 1
    );

    @Override
    public String id() {
        return "IECOPENER";
    }

    @Override
    public int pointsForPosition(int position, int driverCount) {
        if (position >= 1 && position <= DISTRIBUTION.size()) {
            return DISTRIBUTION.get(position - 1);
        }
        return 0;
    }
}
