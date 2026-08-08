package dev.EfraGroup.formulaRacing.AI;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the ideal racing line for a track.
 */
public class AIRacingLine {

    private final String trackName;
    private final List<Location> idealLine;
    private final List<Double> idealSpeeds;
    private final List<Location> brakingPoints;
    private final List<Location> accelerationPoints;

    public AIRacingLine(String trackName) {
        this.trackName = trackName;
        this.idealLine = new ArrayList<>();
        this.idealSpeeds = new ArrayList<>();
        this.brakingPoints = new ArrayList<>();
        this.accelerationPoints = new ArrayList<>();
    }

    public void addIdealLinePoint(Location location, double idealSpeed) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        idealLine.add(location.clone());
        idealSpeeds.add(clampSpeed(idealSpeed));
    }

    public void addBrakingPoint(Location location) {
        if (location != null && location.getWorld() != null) {
            brakingPoints.add(location.clone());
        }
    }

    public void addAccelerationPoint(Location location) {
        if (location != null && location.getWorld() != null) {
            accelerationPoints.add(location.clone());
        }
    }

    public Location getClosestIdealLinePoint(Location location) {
        int closestIndex = getClosestIdealLineIndex(location);
        return closestIndex < 0 ? null : idealLine.get(closestIndex).clone();
    }

    public int getClosestIdealLineIndex(Location location) {
        if (location == null || location.getWorld() == null || idealLine.isEmpty()) {
            return -1;
        }

        int closestIndex = -1;
        double minDistanceSquared = Double.MAX_VALUE;

        for (int i = 0; i < idealLine.size(); i++) {
            Location point = idealLine.get(i);
            if (point.getWorld() == null || !point.getWorld().equals(location.getWorld())) {
                continue;
            }

            double distanceSquared = point.distanceSquared(location);
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    public double getIdealSpeedAt(Location location) {
        return getIdealSpeedAtIndex(getClosestIdealLineIndex(location));
    }

    public double getIdealSpeedAtIndex(int index) {
        if (index < 0 || index >= idealSpeeds.size()) {
            return 0.5;
        }
        return idealSpeeds.get(index);
    }

    public boolean isNearBrakingPoint(Location location, double threshold) {
        return isNearAnyPoint(location, brakingPoints, threshold);
    }

    public boolean isNearAccelerationPoint(Location location, double threshold) {
        return isNearAnyPoint(location, accelerationPoints, threshold);
    }

    public Location getNextIdealLinePoint(Location location, int lookAhead) {
        int closestIndex = getClosestIdealLineIndex(location);
        if (closestIndex < 0) {
            return idealLine.isEmpty() ? null : idealLine.get(0).clone();
        }
        return getPointAtWrapped(closestIndex + Math.max(1, lookAhead));
    }

    public Location getPointAtWrapped(int index) {
        if (idealLine.isEmpty()) {
            return null;
        }
        int wrappedIndex = Math.floorMod(index, idealLine.size());
        return idealLine.get(wrappedIndex).clone();
    }

    public int advanceIndex(int currentIndex, int amount) {
        if (idealLine.isEmpty()) {
            return -1;
        }
        return Math.floorMod(currentIndex + amount, idealLine.size());
    }

    public double getIdealDirection(Location location) {
        int currentIndex = getClosestIdealLineIndex(location);
        if (currentIndex < 0) {
            return location != null ? location.getYaw() : 0.0;
        }

        Location current = idealLine.get(currentIndex);
        Location next = idealLine.get(advanceIndex(currentIndex, 3));

        double dx = next.getX() - current.getX();
        double dz = next.getZ() - current.getZ();
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    public String getTrackName() {
        return trackName;
    }

    public List<Location> getIdealLine() {
        return new ArrayList<>(idealLine);
    }

    public List<Location> getBrakingPoints() {
        return new ArrayList<>(brakingPoints);
    }

    public List<Location> getAccelerationPoints() {
        return new ArrayList<>(accelerationPoints);
    }

    public int getIdealLineSize() {
        return idealLine.size();
    }

    public boolean isUsable() {
        return idealLine.size() >= 2;
    }

    public void clear() {
        idealLine.clear();
        idealSpeeds.clear();
        brakingPoints.clear();
        accelerationPoints.clear();
    }

    private boolean isNearAnyPoint(Location location, List<Location> points, double threshold) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        double thresholdSquared = threshold * threshold;
        for (Location point : points) {
            if (point.getWorld() == null || !point.getWorld().equals(location.getWorld())) {
                continue;
            }
            if (location.distanceSquared(point) <= thresholdSquared) {
                return true;
            }
        }
        return false;
    }

    private double clampSpeed(double speed) {
        return Math.max(0.1, Math.min(1.0, speed));
    }

    // ---- Binary serialization ----

    private static final int FORMAT_VERSION = 1;

    private static void writeLocation(DataOutputStream out, Location loc) throws IOException {
        out.writeDouble(loc.getX());
        out.writeDouble(loc.getY());
        out.writeDouble(loc.getZ());
        out.writeUTF(loc.getWorld() != null ? loc.getWorld().getName() : "");
    }

    private static Location readLocation(DataInputStream in) throws IOException {
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        String worldName = in.readUTF();
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(FORMAT_VERSION);

        out.writeInt(idealLine.size());
        for (int i = 0; i < idealLine.size(); i++) {
            writeLocation(out, idealLine.get(i));
            out.writeDouble(idealSpeeds.get(i));
        }

        out.writeInt(brakingPoints.size());
        for (Location loc : brakingPoints) {
            writeLocation(out, loc);
        }

        out.writeInt(accelerationPoints.size());
        for (Location loc : accelerationPoints) {
            writeLocation(out, loc);
        }
    }

    public static AIRacingLine readFrom(DataInputStream in, String trackName) throws IOException {
        in.readInt(); // version

        AIRacingLine line = new AIRacingLine(trackName);

        int idealCount = in.readInt();
        for (int i = 0; i < idealCount; i++) {
            Location loc = readLocation(in);
            double speed = in.readDouble();
            if (loc != null && loc.getWorld() != null) {
                line.addIdealLinePoint(loc, speed);
            }
        }

        int brakingCount = in.readInt();
        for (int i = 0; i < brakingCount; i++) {
            Location loc = readLocation(in);
            if (loc != null) {
                line.addBrakingPoint(loc);
            }
        }

        int accelCount = in.readInt();
        for (int i = 0; i < accelCount; i++) {
            Location loc = readLocation(in);
            if (loc != null) {
                line.addAccelerationPoint(loc);
            }
        }

        return line;
    }
}
