package dev.EfraGroup.formulaRacing.Participant;

public enum DriverState {
    SETUP,
    LOADED,
    STARTING,
    RUNNING,
    LAPRESET,
    FINISHED,
    DNF,
    DSQ;

    public boolean isRacing() {
        return this == RUNNING || this == STARTING || this == LAPRESET || this == FINISHED;
    }
}
