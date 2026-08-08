package dev.EfraGroup.formulaRacing.League.scoring;

public class LinearScoring implements ScoringSystem {

    private final int top;
    private final int bottom;

    public LinearScoring(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
    }

    public LinearScoring() {
        this(50, 1);
    }

    @Override
    public String id() {
        return "LINEAR";
    }

    @Override
    public int pointsForPosition(int position, int driverCount) {
        if (position < 1) {
            return 0;
        }
        int span = Math.max(1, driverCount - 1);
        int drop = span > 0 ? (top - bottom) * (position - 1) / span : 0;
        return Math.max(bottom, top - drop);
    }
}
