package dev.EfraGroup.formulaRacing.AI;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the ideal racing line for a track.
 */
public class AIRacingLine {

    private final String trackName;
    private final List<Location> idealLine;
    private final List<Double> idealSpeeds;
    /** Navigable corridor, measured from the recorded line in travel-relative directions. */
    private final List<Double> leftWidths;
    private final List<Double> rightWidths;
    private final List<Location> brakingPoints;
    private final List<Location> accelerationPoints;

    /**
     * Guards mutations that must keep {@link #idealLine} and {@link #idealSpeeds}
     * in lockstep (same size, index-aligned). Without it, a save triggered from
     * another thread (e.g. startAIForHeat) could serialize the two lists at
     * different sizes mid-recording and crash with IndexOutOfBounds.
     */
    private final Object mutationLock = new Object();

    /**
     * True when {@link #idealSpeeds} holds ABSOLUTE blocks/tick instead of the
     * legacy 0.1..1.0 fraction of the local surface max.
     *
     * <p>Normalizing by the surface destroyed the pace profile of any track
     * whose surface never changes: on pure blue ice the recorder did ~3.6 b/t
     * against a 3.6365 b/t max, so every single point normalized to 1.0 and the
     * saved line was flat — the AI drove at one constant speed everywhere
     * because the line literally said "1.0" everywhere. Absolute speeds keep the
     * real variation the driver actually recorded.
     *
     * <p>Lines loaded from v1/v2 files keep the old fraction semantics so their
     * existing recordings keep behaving exactly as before; only newly recorded
     * lines are absolute.
     */
    private boolean absoluteSpeeds;

    public AIRacingLine(String trackName) {
        this.trackName = trackName;
        this.idealLine = new CopyOnWriteArrayList<>();
        this.idealSpeeds = new CopyOnWriteArrayList<>();
        this.leftWidths = new CopyOnWriteArrayList<>();
        this.rightWidths = new CopyOnWriteArrayList<>();
        this.brakingPoints = new CopyOnWriteArrayList<>();
        this.accelerationPoints = new CopyOnWriteArrayList<>();
    }

    /** True when speeds are absolute blocks/tick (v3) rather than surface fractions (v1/v2). */
    public boolean hasAbsoluteSpeeds() {
        return absoluteSpeeds;
    }

    /** Marks this line's speeds as absolute blocks/tick. Call before adding points. */
    public void setAbsoluteSpeeds(boolean absoluteSpeeds) {
        this.absoluteSpeeds = absoluteSpeeds;
    }

    public void addIdealLinePoint(Location location, double idealSpeed) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        synchronized (mutationLock) {
            idealLine.add(location.clone());
            idealSpeeds.add(clampSpeed(idealSpeed));
            // Conservative legacy/default corridor. A hybrid bounds scan can
            // replace these values without requiring the line to be re-recorded.
            leftWidths.add(3.0D);
            rightWidths.add(3.0D);
        }
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

    public int getClosestIdealLineIndex(Location location, int hintIndex, int window) {
        if (location == null || location.getWorld() == null || idealLine.isEmpty()) {
            return -1;
        }

        int pointCount = idealLine.size();
        if (hintIndex < 0) {
            return getClosestIdealLineIndex(location);
        }

        int half = Math.max(1, Math.min(window, pointCount));
        int bestIndex = -1;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (int delta = -half; delta <= half; delta++) {
            int index = Math.floorMod(hintIndex + delta, pointCount);
            Location point = idealLine.get(index);
            if (point.getWorld() == null || !point.getWorld().equals(location.getWorld())) {
                continue;
            }

            double distanceSquared = point.distanceSquared(location);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestIndex = index;
            }
        }

        if (bestIndex < 0) {
            return getClosestIdealLineIndex(location);
        }

        return bestIndex;
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

    public List<Double> getIdealSpeeds() {
        return new ArrayList<>(idealSpeeds);
    }

    public List<Double> getLeftWidths() {
        return new ArrayList<>(leftWidths);
    }

    public List<Double> getRightWidths() {
        return new ArrayList<>(rightWidths);
    }

    public double getLeftWidthAtIndex(int index) {
        if (leftWidths.isEmpty()) return 3.0D;
        return leftWidths.get(Math.floorMod(index, leftWidths.size()));
    }

    public double getRightWidthAtIndex(int index) {
        if (rightWidths.isEmpty()) return 3.0D;
        return rightWidths.get(Math.floorMod(index, rightWidths.size()));
    }

    public void setWidthsAtIndex(int index, double left, double right) {
        synchronized (mutationLock) {
            if (index < 0 || index >= idealLine.size()) return;
            ensureWidthAlignment();
            leftWidths.set(index, clampWidth(left));
            rightWidths.set(index, clampWidth(right));
        }
    }

    /** Median-smooths scanned widths while preserving genuine narrow sections. */
    public void smoothWidths(int radius) {
        synchronized (mutationLock) {
            ensureWidthAlignment();
            if (idealLine.isEmpty()) return;
            int window = Math.max(1, radius);
            List<Double> smoothedLeft = new ArrayList<>(idealLine.size());
            List<Double> smoothedRight = new ArrayList<>(idealLine.size());
            for (int i = 0; i < idealLine.size(); i++) {
                List<Double> leftWindow = new ArrayList<>();
                List<Double> rightWindow = new ArrayList<>();
                for (int delta = -window; delta <= window; delta++) {
                    int sample = Math.floorMod(i + delta, idealLine.size());
                    leftWindow.add(leftWidths.get(sample));
                    rightWindow.add(rightWidths.get(sample));
                }
                leftWindow.sort(Double::compareTo);
                rightWindow.sort(Double::compareTo);
                smoothedLeft.add(leftWindow.get(leftWindow.size() / 2));
                smoothedRight.add(rightWindow.get(rightWindow.size() / 2));
            }
            leftWidths.clear();
            rightWidths.clear();
            leftWidths.addAll(smoothedLeft);
            rightWidths.addAll(smoothedRight);
        }
    }

    /** Applies a manual editor correction to points inside a horizontal radius. */
    public int setWidthsNear(Location center, double radius, double left, double right) {
        if (center == null || center.getWorld() == null) return 0;
        double radiusSquared = radius * radius;
        int changed = 0;
        synchronized (mutationLock) {
            ensureWidthAlignment();
            for (int i = 0; i < idealLine.size(); i++) {
                Location point = idealLine.get(i);
                if (point.getWorld() == null || !point.getWorld().equals(center.getWorld())) continue;
                double dx = point.getX() - center.getX();
                double dz = point.getZ() - center.getZ();
                if (dx * dx + dz * dz <= radiusSquared) {
                    leftWidths.set(i, clampWidth(left));
                    rightWidths.set(i, clampWidth(right));
                    changed++;
                }
            }
        }
        return changed;
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

    /**
     * Keeps only the points in {@code [startIndex, endIndexExclusive)} and
     * drops all markers. Used to trim a race-length recording (grid prefix +
     * several laps + mid-track tail) down to a single closed lap, so the line's
     * last point meets its first point at the start/finish line.
     */
    public void keepRange(int startIndex, int endIndexExclusive) {
        synchronized (mutationLock) {
            int size = idealLine.size();
            if (startIndex < 0 || endIndexExclusive > size || startIndex >= endIndexExclusive) {
                return;
            }
            ensureWidthAlignment();
            List<Location> keptPoints = new ArrayList<>(idealLine.subList(startIndex, endIndexExclusive));
            List<Double> keptSpeeds = new ArrayList<>(idealSpeeds.subList(startIndex, endIndexExclusive));
            List<Double> keptLeft = new ArrayList<>(leftWidths.subList(startIndex, endIndexExclusive));
            List<Double> keptRight = new ArrayList<>(rightWidths.subList(startIndex, endIndexExclusive));
            idealLine.clear();
            idealSpeeds.clear();
            leftWidths.clear();
            rightWidths.clear();
            idealLine.addAll(keptPoints);
            idealSpeeds.addAll(keptSpeeds);
            leftWidths.addAll(keptLeft);
            rightWidths.addAll(keptRight);
        }
        brakingPoints.clear();
        accelerationPoints.clear();
    }

    public boolean isUsable() {
        return idealLine.size() >= 2;
    }

    public void clear() {
        synchronized (mutationLock) {
            idealLine.clear();
            idealSpeeds.clear();
            leftWidths.clear();
            rightWidths.clear();
        }
        brakingPoints.clear();
        accelerationPoints.clear();
    }

    public void clearMarkers() {
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

    /**
     * Geometry and pace visible in front of an AI driver. The line remains the
     * recorded Minecraft boat trajectory; this preview merely lets the driver
     * anticipate bends instead of reacting only after reaching a marker.
     */
    public record Preview(double minimumRecordedSpeed, double maximumTurnDegrees, int endIndex) {
    }

    /** Horizontal distance squared from a position to one line point. */
    public double getHorizontalDistanceSquared(Location location, int index) {
        Location point = getPointAtWrapped(index);
        if (location == null || point == null || location.getWorld() == null
                || !location.getWorld().equals(point.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = location.getX() - point.getX();
        double dz = location.getZ() - point.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Signed cross-track error in blocks. Positive means the boat is to the
     * right of the recorded travel direction, negative means it is to the left.
     */
    public double getSignedLateralError(Location location, int index) {
        if (location == null || idealLine.size() < 2) {
            return 0.0D;
        }
        Location center = getPointAtWrapped(index);
        Vector tangent = getTangentAt(index);
        if (center == null || tangent.lengthSquared() < 0.0001D
                || center.getWorld() == null || !center.getWorld().equals(location.getWorld())) {
            return 0.0D;
        }
        double offsetX = location.getX() - center.getX();
        double offsetZ = location.getZ() - center.getZ();
        // Y component of tangent x offset in the X/Z plane.
        return tangent.getZ() * offsetX - tangent.getX() * offsetZ;
    }

    /** Unit horizontal tangent of the recorded line at an index. */
    public Vector getTangentAt(int index) {
        if (idealLine.size() < 2) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        Location before = getPointAtWrapped(index - 1);
        Location after = getPointAtWrapped(index + 1);
        if (before == null || after == null || before.getWorld() == null
                || !before.getWorld().equals(after.getWorld())) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        Vector tangent = after.toVector().subtract(before.toVector()).setY(0.0D);
        return tangent.lengthSquared() < 0.0001D
                ? new Vector(0.0D, 0.0D, 1.0D) : tangent.normalize();
    }

    /**
     * Looks a physical distance ahead and reports the slowest recorded pace and
     * sharpest direction change in that window. Distances are used rather than
     * point counts because high-speed ice recordings have wider point spacing.
     */
    public Preview previewAhead(int startIndex, double distanceBlocks) {
        if (!isUsable()) {
            return new Preview(0.5D, 0.0D, startIndex);
        }

        int count = idealLine.size();
        int index = Math.floorMod(startIndex, count);
        Location previousPoint = getPointAtWrapped(index);
        Vector previousDirection = getTangentAt(index);
        Vector initialDirection = previousDirection.clone();
        double accumulated = 0.0D;
        double minSpeed = getIdealSpeedAtIndex(index);
        double maxTurn = 0.0D;
        int endIndex = index;

        for (int step = 1; step < count && accumulated < Math.max(1.0D, distanceBlocks); step++) {
            int nextIndex = Math.floorMod(index + step, count);
            Location nextPoint = getPointAtWrapped(nextIndex);
            if (previousPoint == null || nextPoint == null || previousPoint.getWorld() == null
                    || !previousPoint.getWorld().equals(nextPoint.getWorld())) {
                break;
            }

            Vector segment = nextPoint.toVector().subtract(previousPoint.toVector()).setY(0.0D);
            double segmentLength = segment.length();
            if (segmentLength > 0.0001D) {
                Vector direction = segment.clone().multiply(1.0D / segmentLength);
                double localDot = Math.max(-1.0D, Math.min(1.0D, previousDirection.dot(direction)));
                double totalDot = Math.max(-1.0D, Math.min(1.0D, initialDirection.dot(direction)));
                maxTurn = Math.max(maxTurn, Math.max(
                        Math.toDegrees(Math.acos(localDot)),
                        Math.toDegrees(Math.acos(totalDot))));
                previousDirection = direction;
                accumulated += segmentLength;
            }
            minSpeed = Math.min(minSpeed, getIdealSpeedAtIndex(nextIndex));
            previousPoint = nextPoint;
            endIndex = nextIndex;
        }
        return new Preview(minSpeed, maxTurn, endIndex);
    }

    private void ensureWidthAlignment() {
        while (leftWidths.size() < idealLine.size()) leftWidths.add(3.0D);
        while (rightWidths.size() < idealLine.size()) rightWidths.add(3.0D);
        while (leftWidths.size() > idealLine.size()) leftWidths.remove(leftWidths.size() - 1);
        while (rightWidths.size() > idealLine.size()) rightWidths.remove(rightWidths.size() - 1);
    }

    private double clampSpeed(double speed) {
        if (absoluteSpeeds) {
            // Absolute blocks/tick. The ceiling is a safety net for corrupt
            // recordings only — a real blue-ice boat reaches ~3.6 b/t, and
            // clamping at 1.0 here would recreate the flat-line bug this format
            // exists to fix.
            return Math.max(0.01D, Math.min(8.0D, speed));
        }
        return Math.max(0.1, Math.min(1.0, speed));
    }

    private double clampWidth(double width) {
        return Math.max(0.0D, Math.min(32.0D, width));
    }

    // ---- Binary serialization ----

    private static final int FORMAT_VERSION = 3;

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
        // Snapshot both lists atomically: a recording on another thread may be
        // appending points while this save runs, and the two lists must stay
        // index-aligned or the read side gets a corrupted line.
        List<Location> pointsSnapshot;
        List<Double> speedsSnapshot;
        List<Double> leftSnapshot;
        List<Double> rightSnapshot;
        List<Location> brakingSnapshot;
        List<Location> accelSnapshot;
        synchronized (mutationLock) {
            ensureWidthAlignment();
            pointsSnapshot = new ArrayList<>(idealLine);
            speedsSnapshot = new ArrayList<>(idealSpeeds);
            leftSnapshot = new ArrayList<>(leftWidths);
            rightSnapshot = new ArrayList<>(rightWidths);
            brakingSnapshot = new ArrayList<>(brakingPoints);
            accelSnapshot = new ArrayList<>(accelerationPoints);
        }

        // The written version must match the SEMANTICS of the stored values, not
        // just the current constant: a v1/v2 line (surface fractions) that is
        // re-saved untouched would otherwise be labelled v3 and later read back
        // as absolute blocks/tick, shrinking every speed ~3.6x.
        out.writeInt(absoluteSpeeds ? 3 : 2);

        out.writeInt(pointsSnapshot.size());
        for (int i = 0; i < pointsSnapshot.size(); i++) {
            writeLocation(out, pointsSnapshot.get(i));
            out.writeDouble(speedsSnapshot.get(i));
            out.writeDouble(leftSnapshot.get(i));
            out.writeDouble(rightSnapshot.get(i));
        }

        out.writeInt(brakingSnapshot.size());
        for (Location loc : brakingSnapshot) {
            writeLocation(out, loc);
        }

        out.writeInt(accelSnapshot.size());
        for (Location loc : accelSnapshot) {
            writeLocation(out, loc);
        }
    }

    public static AIRacingLine readFrom(DataInputStream in, String trackName) throws IOException {
        return readFrom(in, trackName, null);
    }

    /**
     * @param droppedOut optional single-slot array (index 0) that receives how
     *                   many points were dropped because their world was not
     *                   loaded, so callers can warn instead of silently losing
     *                   parts of the line.
     */
    public static AIRacingLine readFrom(DataInputStream in, String trackName, int[] droppedOut) throws IOException {
        int version = in.readInt();
        if (version < 1 || version > FORMAT_VERSION) {
            throw new IOException("Unsupported AI racing line format version " + version
                    + " (supported 1-" + FORMAT_VERSION + ") for track " + trackName);
        }

        AIRacingLine line = new AIRacingLine(trackName);
        // Must be set BEFORE the points are added, because addIdealLinePoint
        // clamps through the mode (0.1..1.0 fraction vs absolute blocks/tick).
        line.absoluteSpeeds = version >= 3;

        int idealCount = in.readInt();
        for (int i = 0; i < idealCount; i++) {
            Location loc = readLocation(in);
            double speed = in.readDouble();
            double left = version >= 2 ? in.readDouble() : 3.0D;
            double right = version >= 2 ? in.readDouble() : 3.0D;
            if (loc != null && loc.getWorld() != null) {
                line.addIdealLinePoint(loc, speed);
                line.setWidthsAtIndex(line.getIdealLineSize() - 1, left, right);
            } else if (droppedOut != null) {
                droppedOut[0]++;
            }
        }

        int brakingCount = in.readInt();
        for (int i = 0; i < brakingCount; i++) {
            Location loc = readLocation(in);
            if (loc != null) {
                line.addBrakingPoint(loc);
            } else if (droppedOut != null) {
                droppedOut[0]++;
            }
        }

        int accelCount = in.readInt();
        for (int i = 0; i < accelCount; i++) {
            Location loc = readLocation(in);
            if (loc != null) {
                line.addAccelerationPoint(loc);
            } else if (droppedOut != null) {
                droppedOut[0]++;
            }
        }

        return line;
    }
}
