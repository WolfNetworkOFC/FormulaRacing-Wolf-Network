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

    private static Method getHandleMethod;
    private static Method setInputMethod;
    private static Field deltaRotationField;
    private static boolean reflectionFailed;
    private static boolean reflectionInitialized;

    private AIBoatController() {
    }

    /**
     * Applies WASD inputs to the boat using vanilla controlBoat math.
     * Must be called on the boat's region thread.
     *
     * @return false when NMS reflection is unavailable (boat is left untouched)
     */
    public static boolean drive(Entity boat, boolean left, boolean right, boolean up, boolean down) {
        if (boat == null) {
            return false;
        }
        if (!initReflection(boat)) {
            return false;
        }

        try {
            Object handle = getHandleMethod.invoke(boat);
            setInputMethod.invoke(handle, left, right, up, down);

            float deltaRotation = deltaRotationField.getFloat(handle);
            float thrust = 0.0F;

            // Same order as vanilla AbstractBoat.controlBoat().
            if (left) {
                deltaRotation -= 1.0F;
            }
            if (right) {
                deltaRotation += 1.0F;
            }
            if (right != left && !up && !down) {
                thrust += TURN_ONLY_THRUST;
            }

            Location location = boat.getLocation();
            float yaw = location.getYaw() + deltaRotation;
            if (up) {
                thrust += FORWARD_THRUST;
            }
            if (down) {
                thrust -= BACKWARD_THRUST;
            }

            deltaRotationField.setFloat(handle, deltaRotation);
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
