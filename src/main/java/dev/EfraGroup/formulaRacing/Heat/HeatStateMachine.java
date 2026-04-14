package dev.EfraGroup.formulaRacing.Heat;

import java.util.Map;
import java.util.Set;

public class HeatStateMachine {
    private static final Map<HeatState, Set<HeatState>> TRANSITIONS = Map.of(
        HeatState.IDLE, Set.of(HeatState.SETUP, HeatState.PRACTICE),
        HeatState.SETUP, Set.of(HeatState.LOADED, HeatState.PRACTICE),
        HeatState.PRACTICE, Set.of(HeatState.LOADED, HeatState.FINISHED),
        HeatState.LOADED, Set.of(HeatState.STARTING, HeatState.SETUP),
        HeatState.STARTING, Set.of(HeatState.RACING, HeatState.LOADED),
        HeatState.QUALIFYING, Set.of(HeatState.FINISHED),
        HeatState.RACING, Set.of(HeatState.FINISHED),
        HeatState.FINISHED, Set.of(HeatState.SETUP)
    );

    public static boolean canTransition(HeatState from, HeatState to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(HeatState from, HeatState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                "Illegal HeatState transition: " + from + " -> " + to
            );
        }
    }
}
