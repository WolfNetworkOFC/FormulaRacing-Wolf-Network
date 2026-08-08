package dev.EfraGroup.formulaRacing.League.scoring;

import java.util.List;

public class IECScoring implements ScoringSystem {

    private static final List<Integer> DISTRIBUTION = java.util.Arrays.asList(
            30, 24, 20, 16, 13, 11, 9, 7, 5, 4, 3, 2, 1
    );

    @Override
    public String id() {
        return "IEC";
    }

    @Override
    public int pointsForPosition(int position, int driverCount) {
        if (position >= 1 && position <= DISTRIBUTION.size()) {
            return DISTRIBUTION.get(position - 1);
        }
        return 0;
    }
}
