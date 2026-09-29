package dev.EfraGroup.formulaRacing.Utils;

/**
 * Formats the optional leaderboard-position suffix of the {@code /tt}
 * "Teleported to [track]" message.
 *
 * <p>Shared by both entry points that start a time trial (the {@code /tt
 * <track>} command and the track-selection GUI) so the two can never drift
 * apart in wording or colour.
 */
public final class TimeTrialTeleportMessage {

    private TimeTrialTeleportMessage() {
    }

    /**
     * Builds the {@code {position}} placeholder value for the given rank.
     *
     * <p>Returns only the bare number, e.g. {@code "14"}. The surrounding spacing
     * and the {@code &7} (grey) colour live in the language string, so each
     * language file controls how the position is presented. An empty string is
     * returned when the player has no finished time on the track, which leaves
     * the rendered message byte-for-byte identical to the previous behaviour.
     *
     * @param rank the player's leaderboard position, or {@code 0} / negative
     *             when they have no qualifying time
     * @return the bare position number, e.g. {@code "14"}, or {@code ""}
     */
    public static String rankSuffix(Integer rank) {
        if (rank == null || rank <= 0) {
            return "";
        }
        return String.valueOf(rank);
    }
}
