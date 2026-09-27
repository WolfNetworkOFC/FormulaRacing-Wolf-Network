package dev.EfraGroup.formulaRacing.Utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckpointSkipRuleTest {

    /** Track with evenly spaced ids. */
    private static final List<Integer> SEQUENTIAL = List.of(1, 2, 3, 4);

    /** Track whose ids have gaps, e.g. after a delete + re-create in the editor. */
    private static final List<Integer> WITH_GAPS = List.of(1, 3, 5, 7);

    @Test
    void crossingTheExpectedCheckpointIsNotASkip() {
        assertFalse(CheckpointSkipRule.isSkip(2, 1, SEQUENTIAL, 2));
    }

    @Test
    void crossingACheckpointAheadIsASkip() {
        // passed 1, so 2 is expected. Touching 3 skipped 2.
        assertTrue(CheckpointSkipRule.isSkip(3, 1, SEQUENTIAL, 2));
    }

    @Test
    void reCrossingACheckpointAlreadyPassedIsFree() {
        // passed 1 and 2, expected 3. Driving back over CP1 must not punish.
        assertFalse(CheckpointSkipRule.isSkip(1, 2, SEQUENTIAL, 3));
        assertFalse(CheckpointSkipRule.isSkip(2, 2, SEQUENTIAL, 3));
    }

    @Test
    void reCrossingTheFirstCheckpointIsFreeAtEveryLapPosition() {
        // The regression this guards: a wide checkpoint region re-entered while the
        // driver is further along used to teleport them back. Every position of the
        // lap must tolerate re-crossing CP1.
        for (int reached = 1; reached <= SEQUENTIAL.size(); reached++) {
            Integer expected = reached < SEQUENTIAL.size() ? SEQUENTIAL.get(reached) : null;
            assertFalse(
                CheckpointSkipRule.isSkip(1, reached, SEQUENTIAL, expected),
                "re-crossing CP1 must stay free with " + reached + " checkpoints reached"
            );
        }
    }

    @Test
    void checkpointsAheadOfTheDriverAreStillPunished() {
        for (int reached = 0; reached < SEQUENTIAL.size() - 1; reached++) {
            Integer expected = SEQUENTIAL.get(reached);
            for (int cpId : SEQUENTIAL) {
                if (cpId <= expected) continue;
                assertTrue(
                    CheckpointSkipRule.isSkip(cpId, reached, SEQUENTIAL, expected),
                    "CP" + cpId + " ahead of expected CP" + expected + " must be a skip"
                );
            }
        }
    }

    @Test
    void ordinalsFollowTrackOrderNotRawId() {
        // Ids are 1,3,5,7. Having passed two checkpoints the driver expects 5.
        // Crossing 3 (ordinal 1, already passed) is free even though its id is
        // numerically less than 5; crossing 7 is a skip.
        assertFalse(CheckpointSkipRule.isSkip(3, 2, WITH_GAPS, 5));
        assertTrue(CheckpointSkipRule.isSkip(7, 2, WITH_GAPS, 5));
    }

    @Test
    void withOnlyTheLastCheckpointLeftNothingElseIsASkip() {
        // reached 3 of 4, so 4 is expected. Every other checkpoint is behind the
        // driver and free to re-cross; only reaching 4 itself is progress.
        for (int cpId : SEQUENTIAL) {
            assertFalse(
                CheckpointSkipRule.isSkip(cpId, 3, SEQUENTIAL, 4),
                "CP" + cpId + " must not be a skip when only CP4 remains"
            );
        }
    }

    @Test
    void nothingIsASkipOnceEveryCheckpointHasBeenPassed() {
        // expectedCheckpointId is null: the lap is complete. No region may be
        // treated as a skip — this used to be guarded only by the 2s cooldown.
        for (int cpId : SEQUENTIAL) {
            assertFalse(
                CheckpointSkipRule.isSkip(cpId, SEQUENTIAL.size(), SEQUENTIAL, null),
                "CP" + cpId + " must not be a skip after the lap is complete"
            );
        }
    }

    @Test
    void unknownCheckpointIdIsNotASkip() {
        // Defensive: an id not on this track should not reset the driver.
        assertFalse(CheckpointSkipRule.isSkip(99, 0, SEQUENTIAL, 1));
    }
}
