package dev.EfraGroup.formulaRacing.League.scoring;

import java.util.List;

public class IECDoubleScoring implements ScoringSystem {

    private static final List<Integer> DISTRIBUTION = java.util.Arrays.asList(
            60, 48, 40, 32, 26, 22, 18, 14, 10, 8, 6, 4, 2
    );

    @Override
    public String id() {
        return "IECDOUBLE";
    }

    @Override
    public int pointsForPosition(int position, int driverCount) {
        if (position >= 1 && position <= DISTRIBUTION.size()) {
            return DISTRIBUTION.get(position - 1);
        }
        return 0;
    }
}
