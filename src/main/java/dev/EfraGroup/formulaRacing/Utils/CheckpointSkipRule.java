package dev.EfraGroup.formulaRacing.Utils;

import java.util.List;

/**
 * Decides whether a checkpoint region a driver just entered counts as a skip.
 *
 * <p>Kept free of Bukkit types so the rule is unit-testable: {@code RaceCheckpointListener}
 * implements {@code org.bukkit.event.Listener}, so loading that class in a plain JUnit
 * run drags in the server API.
 *
 * <p>Only checkpoints AHEAD of the expected one count as a skip. Re-crossing a checkpoint
 * already passed this lap is free, so wide checkpoint regions re-entered across several
 * ticks (Y-axis bobbing) never reset the driver.
 */
public final class CheckpointSkipRule {

    private CheckpointSkipRule() {
    }

    /**
     * @param checkpointId        id of the region the driver just entered
     * @param checkpointsReached  how many checkpoints were passed this lap
     * @param orderedCheckpointIds track checkpoint ids in track order (may contain gaps)
     * @param expectedCheckpointId id the driver must hit next, or null when every
     *     checkpoint has already been passed
     * @return true when the crossing is a skip
     */
    public static boolean isSkip(
        int checkpointId,
        int checkpointsReached,
        List<Integer> orderedCheckpointIds,
        Integer expectedCheckpointId
    ) {
        if (expectedCheckpointId == null) {
            // Lap already complete: there is no next checkpoint to have skipped.
            return false;
        }
        if (checkpointId == expectedCheckpointId) {
            return false;
        }
        int ordinal = orderedCheckpointIds.indexOf(checkpointId);
        // ordinal >= checkpointsReached means the checkpoint is ahead of where the
        // driver currently is. indexOf returns -1 for an unknown id, which is
        // below every real ordinal and so is not a skip.
        return ordinal >= checkpointsReached;
    }
}
