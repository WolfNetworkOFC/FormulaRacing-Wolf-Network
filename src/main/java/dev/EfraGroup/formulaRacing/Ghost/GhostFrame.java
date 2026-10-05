package dev.EfraGroup.formulaRacing.Ghost;

/**
 * A single frame in a ghost recording: the boat's position at a point in time,
 * plus the yaw it was facing.
 *
 * <p>Records use the boat rather than the driver: on drifts the hull slides and the
 * driver sits off-axis, so the boat is the reference the player actually races against.
 *
 * <p>{@code yaw} is optional in the JSON so ghosts recorded before it existed still
 * deserialise (Gson leaves it 0 when the field is absent).
 */
public class GhostFrame {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;

    public GhostFrame(double x, double y, double z) {
        this(x, y, z, 0.0F);
    }

    public GhostFrame(double x, double y, double z, float yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }

    @Override
    public String toString() {
        return String.format("GhostFrame{%.2f, %.2f, %.2f, yaw=%.1f}", x, y, z, yaw);
    }
}
