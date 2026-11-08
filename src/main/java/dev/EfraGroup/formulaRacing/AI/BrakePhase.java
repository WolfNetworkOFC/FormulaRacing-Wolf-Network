package dev.EfraGroup.formulaRacing.AI;

/**
 * Ice-boat brake phases. Real ice racing does not use S (barely any grip);
 * drivers spin ~180° against the velocity vector and hold W so thrust
 * scrubs speed, then realign with the racing line.
 */
public enum BrakePhase {
    NONE,
    TURN_AROUND,
    BRAKING,
    RECOVERY
}