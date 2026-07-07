package dev.EfraGroup.formulaRacing.Heat.Logic;

/**
 * Available tire types
 * Each type has different grip and durability characteristics
 */
public enum TireCompound {
    // Dry tires
    SOFT("SOFT", "&cS", 1.08D, 0.42D, 0.28D, 2800L, 1.0, 0.6),
    MEDIUM("MEDIUM", "&eM", 1.00D, 0.25D, 0.19D, 3200L, 0.9, 0.7),
    HARD("HARD", "&fH", 0.95D, 0.16D, 0.13D, 3600L, 0.8, 0.8),

    // Intermediate tires
    INTERMEDIATE("INTERMEDIATE", "&bI", 0.96D, 0.20D, 0.17D, 3400L, 0.7, 0.9),

    // Wet tires
    WET("WET", "&3W", 0.88D, 0.16D, 0.14D, 3800L, 0.5, 1.0),
    WET_SOFT("WET_SOFT", "&dWS", 0.92D, 0.22D, 0.17D, 3500L, 0.6, 0.95);

    private final String displayName;
    private final String shortDisplay;
    private final double freshGripMultiplier;    // Grip quando novo (100%)
    private final double wearLoss;               // Perda de grip por 100% de desgaste
    private final double wearPerSecond;          // Desgaste por segundo
    private final long serviceTimeMs;           // Tempo de troca em pit
    private final double dryPerformance;         // Performance em pista seca (0-1)
    private final double wetPerformance;         // Performance em pista molhada (0-1)

    TireCompound(
            String displayName,
            String shortDisplay,
            double freshGripMultiplier,
            double wearLoss,
            double wearPerSecond,
            long serviceTimeMs,
            double dryPerformance,
            double wetPerformance
    ) {
        this.displayName = displayName;
        this.shortDisplay = shortDisplay;
        this.freshGripMultiplier = freshGripMultiplier;
        this.wearLoss = wearLoss;
        this.wearPerSecond = wearPerSecond;
        this.serviceTimeMs = serviceTimeMs;
        this.dryPerformance = dryPerformance;
        this.wetPerformance = wetPerformance;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortDisplay() {
        return shortDisplay;
    }

    public double getFreshGripMultiplier() {
        return freshGripMultiplier;
    }

    public double getWearLoss() {
        return wearLoss;
    }

    public double getWearPerSecond() {
        return wearPerSecond;
    }

    public long getServiceTimeMs() {
        return serviceTimeMs;
    }

    public double getDryPerformance() {
        return dryPerformance;
    }

    public double getWetPerformance() {
        return wetPerformance;
    }

    /**
     * Checks if it is a dry tire
     */
    public boolean isDry() {
        return this == SOFT || this == MEDIUM || this == HARD;
    }

    /**
     * Checks if it is an intermediate tire
     */
    public boolean isIntermediate() {
        return this == INTERMEDIATE;
    }

    /**
     * Checks if it is a wet tire
     */
    public boolean isWet() {
        return this == WET || this == WET_SOFT;
    }

    /**
     * Gets the grip modifier based on wear
     */
    public double getGripMultiplier(int wearPercent) {
        double wearFraction = Math.max(0.0D, Math.min(1.0D, wearPercent / 100.0D));
        double progressiveWear = Math.pow(wearFraction, 1.35D);
        return Math.max(0.58D, freshGripMultiplier - progressiveWear * wearLoss);
    }

    /**
     * Gets the grip modifier based on wear and track wetness
     */
    public double getGripMultiplier(int wearPercent, int trackWetness) {
        double baseGrip = getGripMultiplier(wearPercent);

        // Adjust grip based on track wetness
        double wetnessFraction = trackWetness / 100.0;

        if (isWet()) {
            // Wet tires perform better on wet tracks
            double wetBonus = wetnessFraction * 0.15; // Up to 15% bonus
            return Math.min(1.0, baseGrip + wetBonus);
        } else if (isIntermediate()) {
            // Intermediate tires have moderate performance
            double wetPenalty = wetnessFraction * 0.20; // Up to 20% penalty
            return Math.max(0.70, baseGrip - wetPenalty);
        } else {
            // Dry tires suffer greatly on wet tracks
            double wetPenalty = wetnessFraction * 0.50; // Up to 50% penalty
            return Math.max(0.50, baseGrip - wetPenalty);
        }
    }

    /**
     * Gets the wear per second based on track wetness
     */
    public double getWearPerSecond(int trackWetness) {
        double wetnessFraction = trackWetness / 100.0;

        if (isWet()) {
            // Wet tires wear less on dry tracks
            double dryPenalty = (1.0 - wetnessFraction) * 0.30; // Up to 30% more wear on dry
            return wearPerSecond * (1.0 + dryPenalty);
        } else if (isIntermediate()) {
            // Intermediate tires have moderate wear
            return wearPerSecond;
        } else {
            // Dry tires wear more on wet tracks
            double wetBonus = wetnessFraction * 0.50; // Up to 50% more wear on wet
            return wearPerSecond * (1.0 + wetBonus);
        }
    }

    /**
     * Gets the performance index for a specific condition
     */
    public double getPerformanceIndex(int trackWetness) {
        double wetnessFraction = trackWetness / 100.0;

        if (isWet()) {
            return wetPerformance;
        } else if (isIntermediate()) {
            // Interpolates between dry and wet performance
            return dryPerformance + (wetPerformance - dryPerformance) * wetnessFraction;
        } else {
            return dryPerformance;
        }
    }

    /**
     * Gets the recommended tire based on track wetness
     */
    public static TireCompound getRecommendedTire(int trackWetness) {
        if (trackWetness >= 60) {
            return WET;
        } else if (trackWetness >= 30) {
            return INTERMEDIATE;
        } else {
            return MEDIUM;
        }
    }

    /**
     * Gets the tire from the hotbar slot
     */
    public static TireCompound fromHotbarSlot(int slot) {
        return switch (slot) {
            case 0 -> SOFT;
            case 1 -> MEDIUM;
            case 2 -> HARD;
            case 3 -> INTERMEDIATE;
            case 4 -> WET;
            case 5 -> WET_SOFT;
            default -> null;
        };
    }

    /**
     * Gets the hotbar slot for this tire
     */
    public int getHotbarSlot() {
        return switch (this) {
            case SOFT -> 0;
            case MEDIUM -> 1;
            case HARD -> 2;
            case INTERMEDIATE -> 3;
            case WET -> 4;
            case WET_SOFT -> 5;
        };
    }
}
