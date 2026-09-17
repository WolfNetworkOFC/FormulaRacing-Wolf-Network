package dev.EfraGroup.formulaRacing.Heat;

public enum HeatState {
    IDLE,
    SETUP,
    PRACTICE,
    QUALIFYING,
    LOADED,
    STARTING,
    RACING,
    FINISHED;

    /**
     * Whether the heat is still in use: players are loaded on its grid and/or a session is
     * running. Heats in one of these states have to be reset when their event is deleted and
     * sent back to SETUP when the server stops, otherwise they come back loaded/running on the
     * next start. SETUP, IDLE and FINISHED are at rest and must be left alone.
     */
    public boolean isInUse() {
        return this == PRACTICE
            || this == QUALIFYING
            || this == LOADED
            || this == STARTING
            || this == RACING;
    }
}
