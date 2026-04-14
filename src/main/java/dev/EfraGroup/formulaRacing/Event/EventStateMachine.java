package dev.EfraGroup.formulaRacing.Event;

import java.util.Map;
import java.util.Set;

public class EventStateMachine {
    private static final Map<EventState, Set<EventState>> TRANSITIONS = Map.of(
        EventState.SETUP, Set.of(EventState.RUNNING),
        EventState.RUNNING, Set.of(EventState.FINISHED, EventState.SETUP),
        EventState.FINISHED, Set.of(EventState.SETUP)
    );

    public static boolean canTransition(EventState from, EventState to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(EventState from, EventState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                "Illegal EventState transition: " + from + " -> " + to
            );
        }
    }
}
