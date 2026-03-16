//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

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
