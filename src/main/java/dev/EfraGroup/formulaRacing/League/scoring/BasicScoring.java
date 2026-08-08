package dev.EfraGroup.formulaRacing.League.scoring;

import java.util.ArrayList;
import java.util.List;

public class BasicScoring implements ScoringSystem {

    @Override
    public String id() {
        return "BASIC";
    }

    public List<Integer> getPointsDistribution(int driverCount) {
        int topN = Math.max(10, driverCount / 3);
        List<Integer> points = new ArrayList<>();
        for (int i = 0; i < topN; i++) {
            points.add(Math.max(1, (topN - i) * 2));
        }
        return points;
    }

    @Override
    public int pointsForPosition(int position, int driverCount) {
        List<Integer> distribution = getPointsDistribution(driverCount);
        if (position - 1 < distribution.size()) {
            return distribution.get(position - 1);
        }
        return 0;
    }

    public List<Integer> getDistribution(int driverCount) {
        return getPointsDistribution(driverCount);
    }
}
