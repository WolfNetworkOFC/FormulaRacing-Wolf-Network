package dev.EfraGroup.formulaRacing.Heat.Logic;

/**
 * Tipos de pneus disponíveis
 * Cada tipo tem características diferentes de grip e durabilidade
 */
public enum TireCompound {
    // Pneus secos
    SOFT("SOFT", "&cS", 1.08D, 0.42D, 0.28D, 2800L, 1.0, 0.6),
    MEDIUM("MEDIUM", "&eM", 1.00D, 0.25D, 0.19D, 3200L, 0.9, 0.7),
    HARD("HARD", "&fH", 0.95D, 0.16D, 0.13D, 3600L, 0.8, 0.8),

    // Pneus intermediários
    INTERMEDIATE("INTERMEDIATE", "&bI", 0.96D, 0.20D, 0.17D, 3400L, 0.7, 0.9),

    // Pneus molhados
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
     * Verifica se é um pneu seco
     */
    public boolean isDry() {
        return this == SOFT || this == MEDIUM || this == HARD;
    }

    /**
     * Verifica se é um pneu intermediário
     */
    public boolean isIntermediate() {
        return this == INTERMEDIATE;
    }

    /**
     * Verifica se é um pneu molhado
     */
    public boolean isWet() {
        return this == WET || this == WET_SOFT;
    }

    /**
     * Obtém o modificador de grip baseado no desgaste
     */
    public double getGripMultiplier(int wearPercent) {
        double wearFraction = Math.max(0.0D, Math.min(1.0D, wearPercent / 100.0D));
        double progressiveWear = Math.pow(wearFraction, 1.35D);
        return Math.max(0.58D, freshGripMultiplier - progressiveWear * wearLoss);
    }

    /**
     * Obtém o modificador de grip baseado no desgaste e umidade da pista
     */
    public double getGripMultiplier(int wearPercent, int trackWetness) {
        double baseGrip = getGripMultiplier(wearPercent);

        // Ajusta grip baseado na umidade da pista
        double wetnessFraction = trackWetness / 100.0;

        if (isWet()) {
            // Pneus molhados funcionam melhor em pista molhada
            double wetBonus = wetnessFraction * 0.15; // Até 15% de bônus
            return Math.min(1.0, baseGrip + wetBonus);
        } else if (isIntermediate()) {
            // Pneus intermediários têm performance moderada
            double wetPenalty = wetnessFraction * 0.20; // Até 20% de penalidade
            return Math.max(0.70, baseGrip - wetPenalty);
        } else {
            // Pneus secos sofrem muito em pista molhada
            double wetPenalty = wetnessFraction * 0.50; // Até 50% de penalidade
            return Math.max(0.50, baseGrip - wetPenalty);
        }
    }

    /**
     * Obtém o desgaste por segundo baseado na umidade da pista
     */
    public double getWearPerSecond(int trackWetness) {
        double wetnessFraction = trackWetness / 100.0;

        if (isWet()) {
            // Pneus molhados desgastam menos em pista seca
            double dryPenalty = (1.0 - wetnessFraction) * 0.30; // Até 30% mais desgaste em seco
            return wearPerSecond * (1.0 + dryPenalty);
        } else if (isIntermediate()) {
            // Pneus intermediários têm desgaste moderado
            return wearPerSecond;
        } else {
            // Pneus secos desgastam mais em pista molhada
            double wetBonus = wetnessFraction * 0.50; // Até 50% mais desgaste em molhado
            return wearPerSecond * (1.0 + wetBonus);
        }
    }

    /**
     * Obtém o índice de performance para uma condição específica
     */
    public double getPerformanceIndex(int trackWetness) {
        double wetnessFraction = trackWetness / 100.0;

        if (isWet()) {
            return wetPerformance;
        } else if (isIntermediate()) {
            // Interpola entre performance seca e molhada
            return dryPerformance + (wetPerformance - dryPerformance) * wetnessFraction;
        } else {
            return dryPerformance;
        }
    }

    /**
     * Obtém o pneu recomendado baseado na umidade da pista
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
     * Obtém o pneu a partir do slot do hotbar
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
     * Obtém o slot do hotbar para este pneu
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
