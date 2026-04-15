package dev.EfraGroup.formulaRacing.Round;

import java.util.Map;
import java.util.Set;

public class RoundStateMachine {
    private static final Map<RoundState, Set<RoundState>> TRANSITIONS = Map.of(
        RoundState.SETUP, Set.of(RoundState.RUNNING),
        RoundState.RUNNING, Set.of(RoundState.FINISHED),
        RoundState.FINISHED, Set.of(RoundState.SETUP)
    );

    public static boolean canTransition(RoundState from, RoundState to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(RoundState from, RoundState to) {
        if (from == to) {
            return;
        }
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                "Illegal RoundState transition: " + from + " -> " + to
            );
        }
    }
}
