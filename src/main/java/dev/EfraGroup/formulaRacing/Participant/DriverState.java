package dev.EfraGroup.formulaRacing.Participant;

public enum DriverState {
    SETUP,
    LOADED,
    STARTING,
    RUNNING,
    FINISHED,
    DNF,
    DSQ;

    public boolean isRacing() {
        return this == RUNNING || this == STARTING || this == FINISHED;
    }
}
