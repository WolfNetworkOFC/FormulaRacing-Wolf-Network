package dev.EfraGroup.formulaRacing.League.scoring;

import java.util.Arrays;
import java.util.List;

public class F1Scoring implements ScoringSystem {

    private static final List<Integer> DISTRIBUTION = Arrays.asList(
            25, 18, 15, 12, 10, 8, 6, 4, 2, 1
    );

    @Override
    public String id() {
        return "F1";
    }

    @Override
    public int pointsForPosition(int position, int driverCount) {
        if (position >= 1 && position <= DISTRIBUTION.size()) {
            return DISTRIBUTION.get(position - 1);
        }
        return 0;
    }
}
