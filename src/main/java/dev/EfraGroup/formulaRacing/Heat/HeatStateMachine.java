package dev.EfraGroup.formulaRacing.Heat;

import java.util.Map;
import java.util.Set;

public class HeatStateMachine {
    private static final Map<HeatState, Set<HeatState>> TRANSITIONS = Map.ofEntries(
        Map.entry(HeatState.IDLE, Set.of(HeatState.SETUP, HeatState.PRACTICE)),
        Map.entry(HeatState.SETUP, Set.of(HeatState.LOADED, HeatState.PRACTICE, HeatState.QUALIFYING)),
        Map.entry(HeatState.PRACTICE, Set.of(HeatState.LOADED, HeatState.FINISHED)),
        Map.entry(HeatState.LOADED, Set.of(HeatState.STARTING, HeatState.SETUP, HeatState.QUALIFYING)),
        Map.entry(HeatState.STARTING, Set.of(HeatState.RACING, HeatState.LOADED, HeatState.QUALIFYING)),
        Map.entry(HeatState.QUALIFYING, Set.of(HeatState.FINISHED)),
        Map.entry(HeatState.RACING, Set.of(HeatState.FINISHED)),
        Map.entry(HeatState.FINISHED, Set.of(HeatState.SETUP))
    );

    public static boolean canTransition(HeatState from, HeatState to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(HeatState from, HeatState to) {
        if (from == to) {
            return;
        }
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                "Illegal HeatState transition: " + from + " -> " + to
            );
        }
    }
}
