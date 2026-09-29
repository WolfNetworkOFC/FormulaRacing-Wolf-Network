package dev.EfraGroup.formulaRacing.AI;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server-side replication of vanilla {@code AbstractBoat.controlBoat()}.
 *
 * <p>On the server, {@code controlBoat()} never runs for AI boats: it is gated
 * behind {@code level.isClientSide}, and even the input flags written by
 * {@code setInput} do nothing without that call. This helper writes the NMS
 * input flags (for consistency) and applies the exact same thrust/yaw math the
 * client uses, so AI boats accelerate, turn, and coast with vanilla dynamics
 * (including {@code deltaRotation} damping inside {@code floatBoat}).
 */
public final class AIBoatController {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float TURN_ONLY_THRUST = 0.005F;
    private static final float FORWARD_THRUST = 0.04F;
    private static final float BACKWARD_THRUST = 0.005F;

    /**
     * Yaw change (deg) a single held turn key contributes in one tick, matching
     * vanilla {@code controlBoat()}. Exposed so the AI's steering deadzone can
     * compensate for the turn it is already committing this tick.
     */
    public static final float TURN_PER_TICK_DEG = 1.0F;

    /**
     * Upper bound on {@code turnScale}. Vanilla turns 1 deg/tick, which is far
     * too slow to hold a racing line through a corner at ice speed, so the AI
     * scales it up — but an unbounded gain turns any overshoot into a full spin,
     * so the scale is capped here.
     */
    public static final double MAX_TURN_SCALE = 4.0D;
    /** Absolute cap on the yaw change (deg) a single tick may apply. */
    private static final float MAX_TURN_DEG_PER_TICK = TURN_PER_TICK_DEG * (float) MAX_TURN_SCALE;

    private static float clampTurn(float deg) {
        return Math.max(-MAX_TURN_DEG_PER_TICK, Math.min(MAX_TURN_DEG_PER_TICK, deg));
    }

    private static Method getHandleMethod;
    private static Method setInputMethod;
    private static Field deltaRotationField;
    private static boolean reflectionFailed;
    private static boolean reflectionInitialized;

    private AIBoatController() {
    }

    /**
     * Applies WASD inputs to the boat using vanilla controlBoat math, with the
     * turn rate scaled up so tight corners are drivable at speed.
     * Must be called on the boat's region thread.
     *
     * @param turnScale multiplier on the vanilla 1 deg/tick turn rate, clamped
     *                  to {@link #MAX_TURN_SCALE} so a corner can never become
     *                  an instant spin
     * @return false when NMS reflection is unavailable (boat is left untouched)
     */
    public static boolean drive(Entity boat, boolean left, boolean right, boolean up, boolean down,
                                 double turnScale) {
        if (boat == null) {
            return false;
        }
        if (!initReflection(boat)) {
            return false;
        }

        try {
            Object handle = getHandleMethod.invoke(boat);
            setInputMethod.invoke(handle, left, right, up, down);

            float thrust = 0.0F;

            // Vanilla controlBoat() folds the turn input into deltaRotation and
            // rotates the boat by that value ONCE per tick, relative to the
            // previous tick's yaw. The old code added the *accumulated*
            // deltaRotation to the already-rotated yaw, so every tick re-applied
            // the whole pending rotation on top of the last one: one held key
            // compounded without bound (1 deg, then 2, then 3...), any correction
            // massively overshot, and the boat span in circles on the racing line.
            float turn = 0.0F;
            if (left) {
                turn -= TURN_PER_TICK_DEG;
            }
            if (right) {
                turn += TURN_PER_TICK_DEG;
            }
            if (right != left && !up && !down) {
                thrust += TURN_ONLY_THRUST;
            }

            // A gain above 1 is what makes a tight corner drivable (one vanilla
            // key is only 1 deg/tick); the clamp is what stops a large gain from
            // turning into a spin. Callers must still release the key inside
            // their deadzone instead of riding the clamp.
            float appliedTurn = clampTurn(turn * (float) turnScale);

            Location location = boat.getLocation();
            float yaw = location.getYaw() + appliedTurn;
            if (up) {
                thrust += FORWARD_THRUST;
            }
            if (down) {
                thrust -= BACKWARD_THRUST;
            }

            // Persist only the rotation actually applied, so the value read back
            // next tick reports the boat's real turn rate instead of a sum that
            // grows every tick.
            deltaRotationField.setFloat(handle, appliedTurn);
            boat.setRotation(yaw, location.getPitch());

            if (thrust != 0.0F) {
                double yawRad = yaw * DEG_TO_RAD;
                double addX = Math.sin(-yawRad) * thrust;
                double addZ = Math.cos(yawRad) * thrust;
                Vector velocity = boat.getVelocity();
                boat.setVelocity(new Vector(
                        velocity.getX() + addX,
                        velocity.getY(),
                        velocity.getZ() + addZ
                ));
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!reflectionFailed) {
                reflectionFailed = true;
                Logger.getLogger("FormulaRacing").log(
                        Level.WARNING,
                        "[AI] Failed to drive boat via NMS controlBoat replication",
                        e
                );
            }
            return false;
        }
    }

    /**
     * Convenience overload using the default turn scale.
     *
     * @see #drive(Entity, boolean, boolean, boolean, boolean, double)
     */
    public static boolean drive(Entity boat, boolean left, boolean right, boolean up, boolean down) {
        return drive(boat, left, right, up, down, 1.0D);
    }

    /** Current NMS {@code deltaRotation} (damped by vanilla {@code floatBoat}). */
    public static float getDeltaRotation(Entity boat) {
        if (boat == null || !initReflection(boat)) {
            return 0.0F;
        }
        try {
            Object handle = getHandleMethod.invoke(boat);
            return deltaRotationField.getFloat(handle);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0.0F;
        }
    }

    private static synchronized boolean initReflection(Entity boat) {
        if (reflectionInitialized) {
            return !reflectionFailed;
        }
        reflectionInitialized = true;
        try {
            getHandleMethod = boat.getClass().getMethod("getHandle");
            Object handle = getHandleMethod.invoke(boat);

            Class<?> boatClass = handle.getClass();
            setInputMethod = findMethod(boatClass, "setInput",
                    boolean.class, boolean.class, boolean.class, boolean.class);
            if (setInputMethod == null) {
                throw new NoSuchMethodException("setInput(boolean,boolean,boolean,boolean)");
            }
            setInputMethod.setAccessible(true);

            deltaRotationField = findField(boatClass, "deltaRotation");
            if (deltaRotationField == null) {
                throw new NoSuchFieldException("deltaRotation");
            }
            deltaRotationField.setAccessible(true);
            reflectionFailed = false;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            Logger.getLogger("FormulaRacing").log(
                    Level.SEVERE,
                    "[AI] NMS boat control reflection init failed — AI boats cannot drive",
                    e
            );
            return false;
        }
    }

    private static Method findMethod(Class<?> start, String name, Class<?>... params) {
        Class<?> current = start;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        try {
            return start.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Field findField(Class<?> start, String name) {
        Class<?> current = start;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
