package dev.EfraGroup.formulaRacing.League.scoring;

import dev.EfraGroup.formulaRacing.Pontuation.PointsConfig;

public class CustomScaleScoring implements ScoringSystem {

    private final PointsConfig scale;

    public CustomScaleScoring(PointsConfig scale) {
        this.scale = scale;
    }

    @Override
    public String id() {
        return "CUSTOM";
    }

    @Override
    public int pointsForPosition(int position, int driverCount) {
        if (scale == null || position < 1) {
            return 0;
        }
        return scale.getRacePoints().getOrDefault(position, 0);
    }
}
