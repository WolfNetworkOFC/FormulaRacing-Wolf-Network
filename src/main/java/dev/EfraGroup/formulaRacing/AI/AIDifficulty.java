package dev.EfraGroup.formulaRacing.AI;

/**
 * Difficulty levels for AI opponents.
 */
public enum AIDifficulty {
    EASY(0.8, 0.3, 0.5, 0.8, 0.4),
    MEDIUM(0.95, 0.15, 0.7, 0.9, 0.7),
    HARD(1.15, 0.05, 0.9, 0.95, 0.9);

    private final double speedMultiplier;
    private final double errorRate;
    private final double lineAccuracy;
    private final double reactionTime;
    private final double aggressionLevel;

    AIDifficulty(double speedMultiplier, double errorRate, double lineAccuracy,
                 double reactionTime, double aggressionLevel) {
        this.speedMultiplier = speedMultiplier;
        this.errorRate = errorRate;
        this.lineAccuracy = lineAccuracy;
        this.reactionTime = reactionTime;
        this.aggressionLevel = aggressionLevel;
    }

    public String getName() { return name(); }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public double getErrorRate() { return errorRate; }
    public double getLineAccuracy() { return lineAccuracy; }
    public double getReactionTime() { return reactionTime; }
    public double getAggressionLevel() { return aggressionLevel; }
}