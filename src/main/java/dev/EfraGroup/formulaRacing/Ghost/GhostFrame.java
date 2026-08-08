package dev.EfraGroup.formulaRacing.Ghost;

/**
 * A single frame in a ghost recording: the boat's position at a point in time.
 * Lightweight — only x, y, z (no yaw/pitch needed for particle trail playback).
 */
public class GhostFrame {

    private final double x;
    private final double y;
    private final double z;

    public GhostFrame(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    @Override
    public String toString() {
        return String.format("GhostFrame{%.2f, %.2f, %.2f}", x, y, z);
    }
}
