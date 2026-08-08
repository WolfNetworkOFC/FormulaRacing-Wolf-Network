package dev.EfraGroup.formulaRacing.League.scoring;

import java.util.Arrays;
import java.util.List;

public class FC1Scoring implements ScoringSystem {

    private static final List<Integer> DISTRIBUTION = Arrays.asList(
            40, 36, 30, 27, 26, 21, 19, 17, 15, 13, 11, 10, 9, 8, 7, 6, 5, 4, 3, 3, 2, 2, 1, 1, 1
    );

    @Override
    public String id() {
        return "FC1";
    }

    @Override
    public int pointsForPosition(int position, int driverCount) {
        if (position >= 1 && position <= DISTRIBUTION.size()) {
            return DISTRIBUTION.get(position - 1);
        }
        return 0;
    }
}
