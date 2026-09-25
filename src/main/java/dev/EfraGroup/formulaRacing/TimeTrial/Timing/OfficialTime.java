package dev.EfraGroup.formulaRacing.TimeTrial.Timing;

import java.util.UUID;

public record OfficialTime(
    int officialTicks,
    int displayMillis,
    String source,
    UUID runId
) {
    public static final int TICK_MILLIS = 50;
    private static final int TICK_BUCKET_TOLERANCE_MILLIS = 50;

    public OfficialTime {
        if (officialTicks < 0) {
            officialTicks = 0;
        }
        if (displayMillis < 0) {
            displayMillis = officialTicks * TICK_MILLIS;
        }
        if (source == null || source.isBlank()) {
            source = "SERVER";
        }
    }

    public static OfficialTime fromServerMillis(long elapsedMillis, UUID runId) {
        long safeMillis = Math.max(0L, elapsedMillis);
        int ticks = (int) Math.max(0L, (safeMillis + 25L) / TICK_MILLIS);
        return new OfficialTime(ticks, ticks * TICK_MILLIS, "SERVER", runId);
    }

    public static OfficialTime fromLegacySeconds(double seconds) {
        return fromServerMillis(Math.round(seconds * 1000.0), null);
    }

    public static OfficialTime resolve(
        long serverObservedMillis,
        Long proposedDisplayMillis,
        int maxDeltaMillis,
        UUID runId
    ) {
        OfficialTime fallback = fromServerMillis(serverObservedMillis, runId);
        if (proposedDisplayMillis == null || proposedDisplayMillis < 0L || proposedDisplayMillis > 86_400_000L) {
            return fallback;
        }

        long proposed = proposedDisplayMillis;
        long officialMillis = fallback.getOfficialMillis();
        long absoluteDelta = Math.abs(proposed - Math.max(0L, serverObservedMillis));
        long bucketDelta = Math.abs(proposed - officialMillis);
        if (absoluteDelta > maxDeltaMillis || bucketDelta > TICK_BUCKET_TOLERANCE_MILLIS) {
            return fallback;
        }

        return new OfficialTime(
            fallback.officialTicks,
            (int) proposed,
            "WOLF",
            runId
        );
    }

    public long getOfficialMillis() {
        return (long) this.officialTicks * TICK_MILLIS;
    }

    public double getDisplaySeconds() {
        return this.displayMillis / 1000.0;
    }

    public boolean isBetterThan(OfficialTime other) {
        if (other == null) {
            return true;
        }
        int tickComparison = Integer.compare(this.officialTicks, other.officialTicks);
        if (tickComparison != 0) {
            return tickComparison < 0;
        }
        return this.displayMillis < other.displayMillis;
    }
}
