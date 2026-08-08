package dev.EfraGroup.formulaRacing.League.scoring;

import java.util.HashMap;
import java.util.Map;

public final class ScoringRegistry {

    private static final Map<String, ScoringSystem> REGISTRY = new HashMap<>();

    static {
        register(new BasicScoring());
        register(new FC1Scoring());
        register(new FC2Scoring());
        register(new F1Scoring());
        register(new WIBRSScoring());
        register(new IECScoring());
        register(new IECDoubleScoring());
        register(new IECOpenerScoring());
        register(new LinearScoring());
    }

    private ScoringRegistry() {}

    public static void register(ScoringSystem system) {
        REGISTRY.put(system.id().toUpperCase(), system);
    }

    public static ScoringSystem get(String id) {
        if (id == null) {
            return REGISTRY.get("BASIC");
        }
        return REGISTRY.getOrDefault(id.toUpperCase(), REGISTRY.get("BASIC"));
    }

    public static Map<String, ScoringSystem> all() {
        return new HashMap<>(REGISTRY);
    }

    public static boolean exists(String id) {
        return id != null && REGISTRY.containsKey(id.toUpperCase());
    }
}
