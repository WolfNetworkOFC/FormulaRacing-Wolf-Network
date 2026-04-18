package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeDefaults;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.style.MinecraftFontMetrics;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.style.TimingScoreboardStyle;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardContext;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class BuilderSupport {
    private static final String[] SPACERS = new String[]{
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
    };
    // Pixel-based widths for precise alignment (~6px per char default)
    private static final int MIDDLE_PIXEL_WIDTH = 66;  // ~11 chars
    private static final int NAME_PIXEL_WIDTH = 78;    // ~13 chars
    private static final int NAME_PIXEL_WIDTH_WITH_PITS = 66;  // ~11 chars
    private static final int COMPACT_NAME_PIXEL_WIDTH = 24;    // ~4 chars

    private BuilderSupport() {
    }

    private static final Map<String, String> SCOREBOARD_FALLBACKS = createScoreboardFallbacks();

    static List<String> buildClassificationLines(ScoreboardContext context, boolean qualifyingMode) {
        return buildClassificationLines(context, qualifyingMode, 5);
    }

    static List<String> buildClassificationLines(ScoreboardContext context, boolean qualifyingMode, int fixedLines) {
        List<Driver> sorted = context.sortedDrivers();
        if (sorted.isEmpty()) {
            return List.of(tr(context, "scoreboard_v2_no_active_drivers"));
        }

        int availableLines = Math.max(4, context.maxRows() - fixedLines - 2);
        int total = sorted.size();

        int start;
        int end;
        int viewerIndex = -1;
        int desiredWindow = Math.min(total, availableLines);
        if (context.spectator() || context.viewerDriver() == null) {
            start = 0;
            end = desiredWindow;
        } else {
            viewerIndex = findViewerIndex(sorted, context.viewerDriver());
            if (viewerIndex < 0) {
                start = 0;
                end = desiredWindow;
            } else if (total <= desiredWindow) {
                start = 0;
                end = total;
            } else if (viewerIndex == 0) {
                start = 0;
                end = desiredWindow;
            } else {
                int fixedMargin = Math.min(3, Math.max(1, (desiredWindow - 1) / 2));
                start = viewerIndex - fixedMargin;
                end = viewerIndex + fixedMargin + 1;

                int currentWindow = end - start;
                if (currentWindow < desiredWindow) {
                    int missing = desiredWindow - currentWindow;
                    int leftExtra = missing / 2;
                    int rightExtra = missing - leftExtra;
                    start -= leftExtra;
                    end += rightExtra;
                }

                if (start < 0) {
                    end = Math.min(total, end - start);
                    start = 0;
                }

                if (end > total) {
                    int overflow = end - total;
                    start = Math.max(0, start - overflow);
                    end = total;
                }
            }
        }

        List<String> lines = new ArrayList<>();

        Driver leader = sorted.get(0);
        Driver referenceDriver = context.spectator() ? null : context.viewerDriver();
        String marker = accentMarker(context);
        for (int i = start; i < end; i++) {
            Driver current = sorted.get(i);
            Driver reference;
            if (qualifyingMode) {
                reference = i == 0 ? null : leader;
            } else if (context.spectator()) {
                reference = i > 0 ? sorted.get(i - 1) : null;
            } else {
                reference = referenceDriver;
            }
            lines.add(formatLine(context, marker, i + 1, current, reference, qualifyingMode));
        }

        return lines;
    }

    static String stateLabel(ScoreboardContext context, HeatState state) {
        return switch (state) {
            case SETUP -> "&x" + tr(context, "scoreboard_state_setup");
            case IDLE -> "&x" + tr(context, "scoreboard_state_idle");
            case PRACTICE -> "&x" + tr(context, "scoreboard_state_practice");
            case QUALIFYING -> "&x" + tr(context, "scoreboard_state_qualifying");
            case LOADED -> "&x" + tr(context, "scoreboard_state_loaded");
            case STARTING -> "&x" + tr(context, "scoreboard_state_starting");
            case RACING -> "&x" + tr(context, "scoreboard_state_racing");
            case FINISHED -> "&x" + tr(context, "scoreboard_state_finished");
        };
    }

    static String formatBestLap(ScoreboardContext context, Driver driver) {
        Lap bestLap = driver == null ? null : driver.getFastestLap();
        if (bestLap == null) {
            return tr(context, "scoreboard_v2_best_lap", "{time}", "&m--.---");
        }
        return tr(context, "scoreboard_v2_best_lap", "{time}", "&i" + formatTime(bestLap.getLapTime()));  // &i = info colour
    }

    static String heatContext(ScoreboardContext context) {
        String eventName = "";
        if (context.heat().getRound() != null && context.heat().getRound().getEvent() != null) {
            eventName = normalizeEventName(context.heat().getRound().getEvent().getDisplayName());
        }
        if (eventName.length() > 22) {
            eventName = eventName.substring(0, 22);
        }
        String heatName = context.heat().getName();
        return "&x" + heatName + (eventName.isEmpty() ? "" : " &m/ &x" + eventName);
    }

    static String formatTime(long timeMs) {
        long minutes = timeMs / 60000L;
        long seconds = (timeMs % 60000L) / 1000L;
        long millis = timeMs % 1000L;
        if (minutes > 0L) {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
        }
        return String.format("%d.%03d", seconds, millis);
    }

    private static int findViewerIndex(List<Driver> sorted, Driver viewerDriver) {
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUuid().equals(viewerDriver.getUuid())) {
                return i;
            }
        }
        return -1;
    }

    private static String formatLine(ScoreboardContext context, String accentMarker, int pos, Driver current, Driver reference, boolean qualifyingMode) {
        Player p = Bukkit.getPlayer(current.getUuid());
        String name = p != null ? p.getName() : tr(context, "scoreboard_v2_offline");
        String rank = rankTag(pos, current, context);
        String middle = middleBlock(context, current, reference, p, qualifyingMode);
        String marker = teamMarker(accentMarker, pos);
        boolean showPits = hasRequiredPits(context);
        boolean compact = context.compact();
        int namePixelWidth = compact ? COMPACT_NAME_PIXEL_WIDTH : (showPits ? NAME_PIXEL_WIDTH_WITH_PITS : NAME_PIXEL_WIDTH);
        String pilotName = formatPilotName(name, namePixelWidth);
        String pits = formatPits(context, current);
        String divider = compact ? " " : " &m| ";

        return rank + divider + middle + " " + marker + " " + pilotName + pits;
    }

    private static String formatPilotName(String name, int pixelWidth) {
        // Truncate name if too long for the allocated space
        String truncated = MinecraftFontMetrics.truncateToPixels(name, pixelWidth, false);
        return "&x" + TimingScoreboardStyle.padRightPixels(truncated, pixelWidth);
    }

    private static boolean hasRequiredPits(ScoreboardContext context) {
        Integer requiredPits = context.heat().getTotalPits();
        return requiredPits != null && requiredPits > 0;
    }

    private static String middleBlock(ScoreboardContext context, Driver current, Driver reference, Player player, boolean qualifyingMode) {
        String status = statusBlock(context, current, player);
        if (!status.isEmpty()) {
            return status;
        }

        if (qualifyingMode) {
            if (reference == null || reference.getUuid().equals(current.getUuid())) {
                Lap best = current.getFastestLap();
                return best == null ? middleCell("&m", "--.---") : middleCell("&x", formatTime(best.getLapTime()));
            }
            return gapBlock(context, current, reference, true);
        }

        if (reference == null || reference.getUuid().equals(current.getUuid())) {
            if (current.isDrsActive()) {
                return middleCell("&s", "DRS");
            }
            if (current.hasDrsPermission()) {
                return middleCell("&x", "DRS");
            }
            return middleCell("&m", "");
        }

        return gapBlock(context, current, reference, false);
    }

    private static String gapBlock(ScoreboardContext context, Driver current, Driver reference, boolean qualifyingMode) {
        if (qualifyingMode) {
            Lap currentBest = current.getFastestLap();
            Lap referenceBest = reference.getFastestLap();
            if (currentBest == null || referenceBest == null) {
                return middleCell("&m", "--");
            }
            long diff = currentBest.getLapTime() - referenceBest.getLapTime();
            return formatSignedGap(diff);
        }

        Long raceDiff = computeRaceGap(current, reference, context);
        if (raceDiff != null) {
            return formatSignedGap(raceDiff);
        }

        return middleCell("&m", "--");
    }

    /**
     * Compute live race gap between current driver and reference driver.
     * Uses TimingSystem algorithm with live interpolation for smoother updates.
     *
     * Algorithm:
     * 1. Determine which driver is behind (slower) based on position
     * 2. Find the most recent common progress point (lap, checkpoint) where both have data
     * 3. For the slower driver, use "live" elapsed time (System.currentTimeMillis() - startTime)
     *    instead of the static checkpoint timestamp, creating a continuously updating gap
     * 4. For finished drivers, use finish times like TimingSystem
     */
    private static Long computeRaceGap(Driver current, Driver reference, ScoreboardContext context) {
        // Same driver - no gap
        if (current.getUuid().equals(reference.getUuid())) {
            return 0L;
        }

        // Check if live gap is enabled in config
        boolean liveGapEnabled = context.plugin().getConfig().getBoolean("scoreboard.v2.live-gap", true);
        if (!liveGapEnabled) {
            return fallbackGapCalculation(current, reference);
        }

        // Handle finished drivers using finish times (TimingSystem pattern)
        if (current.isFinished()) {
            if (reference.getEndTime() == null || current.getEndTime() == null) {
                return null;
            }
            // Time difference between finish times
            return current.getEndTime() - reference.getEndTime();
        }
        if (reference.isFinished()) {
            if (reference.getEndTime() == null) {
                return null;
            }
            // Current is still racing, reference finished - use "live" time for current
            // at reference's progress point
            Long referenceElapsedAtLastCp = getElapsedAtLastCheckpoint(reference);
            if (referenceElapsedAtLastCp == null) {
                return null;
            }
            Long currentElapsedLive = getLiveElapsedTime(current);
            if (currentElapsedLive == null) {
                return null;
            }
            // Gap = how much longer current took to reach reference's position
            return currentElapsedLive - referenceElapsedAtLastCp;
        }

        // Both racing: use "live" gap calculation
        // Determine who is behind (slower) - we evaluate at the slower driver's progress
        Driver slower, faster;
        boolean currentIsSlower = current.getPosition() > reference.getPosition();

        if (currentIsSlower) {
            slower = current;
            faster = reference;
        } else {
            slower = reference;
            faster = current;
        }

        // Get the most recent common progress point (lap, checkpoint)
        int[] slowerProgress = slower.getCurrentProgress();
        int slowerLap = slowerProgress[0];
        int slowerCp = slowerProgress[1];

        // Get faster driver's elapsed time at slower's progress point
        Long fasterElapsedAtSlowerProgress = faster.getElapsedAtProgress(slowerLap, slowerCp);

        if (fasterElapsedAtSlowerProgress == null) {
            // Faster hasn't reached this point yet, use fallback
            return fallbackGapCalculation(current, reference);
        }

        // Get slower driver's "live" elapsed time (continuously updating)
        Long slowerElapsedLive = getLiveElapsedTime(slower);
        if (slowerElapsedLive == null) {
            return fallbackGapCalculation(current, reference);
        }

        // Calculate gap: difference in elapsed times
        long rawGap = slowerElapsedLive - fasterElapsedAtSlowerProgress;

        // Return with sign based on who is slower
        return currentIsSlower ? rawGap : -rawGap;
    }

    /**
     * Get live elapsed time for a driver (continuously updating even when stationary).
     * Returns time from heat start to now, or to finish time if finished.
     */
    private static Long getLiveElapsedTime(Driver driver) {
        Long startTime = driver.getStartTime();
        if (startTime == null) {
            return null;
        }
        if (driver.isFinished() && driver.getEndTime() != null) {
            return driver.getEndTime() - startTime;
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Get elapsed time at the driver's last crossed checkpoint.
     */
    private static Long getElapsedAtLastCheckpoint(Driver driver) {
        int[] progress = driver.getCurrentProgress();
        return driver.getElapsedAtProgress(progress[0], progress[1]);
    }

    /**
     * Fallback gap calculation when live method doesn't have enough data.
     * Uses the old checkpoint-based approach as a safety net.
     */
    private static Long fallbackGapCalculation(Driver current, Driver reference) {
        // Try to find any common checkpoint
        int maxComparableLap = Math.min(latestComparableLap(current), latestComparableLap(reference));
        if (maxComparableLap < 0) {
            return null;
        }

        for (int lap = maxComparableLap; lap >= 0; lap--) {
            int maxCpCurrent = maxCheckpointAtLap(current, lap);
            int maxCpReference = maxCheckpointAtLap(reference, lap);
            int maxCommonCp = Math.min(maxCpCurrent, maxCpReference);

            for (int cp = maxCommonCp; cp >= 0; cp--) {
                Long currentAt = current.getElapsedAtProgress(lap, cp);
                Long referenceAt = reference.getElapsedAtProgress(lap, cp);
                if (currentAt != null && referenceAt != null) {
                    return currentAt - referenceAt;
                }
            }
        }

        // Final fallback: use time at last checkpoint
        long currentProgressMs = current.getTimeAtLastCheckpoint();
        long referenceProgressMs = reference.getTimeAtLastCheckpoint();
        if (currentProgressMs > 0L && referenceProgressMs > 0L) {
            return currentProgressMs - referenceProgressMs;
        }

        return null;
    }

    private static String formatSignedGap(long diffMs) {
        if (diffMs > 0L) {
            return middleCell("&s", "+" + formatTime(diffMs));
        }
        if (diffMs < 0L) {
            return middleCell("&e", "-" + formatTime(Math.abs(diffMs)));
        }
        return middleCell("&w", "=" + "0.000");
    }

    private static String middleCell(String color, String content) {
        // Pad to pixel width for consistent alignment
        String padded = TimingScoreboardStyle.padRightPixels(content, MIDDLE_PIXEL_WIDTH);
        return " " + color + padded;
    }

    private static int latestComparableLap(Driver driver) {
        int lapCount = driver.getLapCount();
        if (driver.getAbsoluteTimeAtProgress(lapCount, 0) != null) {
            return lapCount;
        }
        if (lapCount > 0 && driver.getAbsoluteTimeAtProgress(lapCount - 1, 0) != null) {
            return lapCount - 1;
        }
        return -1;
    }

    private static int maxCheckpointAtLap(Driver driver, int lapIndex) {
        if (lapIndex < 0) {
            return 0;
        }

        if (lapIndex < driver.getLapCount()) {
            Lap lap = driver.getLaps().get(lapIndex);
            return lap.getCheckpointTimes().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        if (lapIndex == driver.getLapCount() && driver.getCurrentLap() != null) {
            return driver.getCurrentLap().getCheckpointTimes().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        return 0;
    }

    private static String statusBlock(ScoreboardContext context, Driver driver, Player player) {
        if (driver.isDnf()) {
            return middleCell("&m", tr(context, "scoreboard_status_dnf_short"));
        }
        if (player == null || !player.isOnline()) {
            return middleCell("&m", tr(context, "scoreboard_status_offline"));
        }
        if (context.plugin().getPitStopManager() != null && context.plugin().getPitStopManager().isPlayerInPitRegion(driver.getUuid())) {
            return middleCell("&m", tr(context, "scoreboard_status_in_pit"));
        }
        return "";
    }

    private static String rankTag(int pos, Driver driver, ScoreboardContext context) {
        boolean fastestLap = context.heat().getFastestLapUUID() != null && context.heat().getFastestLapUUID().equals(driver.getUuid());
        return TimingScoreboardStyle.rankTag(pos, fastestLap, driver.isFinished());
    }

    private static String teamMarker(String accentMarker, int pos) {
        return TimingScoreboardStyle.teamMarker(accentMarker, pos);
    }

    private static String accentMarker(ScoreboardContext context) {
        return TimingScoreboardStyle.normalizeAccentMarker(context.plugin().getConfig().getString("scoreboard.style.accent-marker", "┃"));
    }

    private static String padRight(String value, int size) {
        return TimingScoreboardStyle.padRight(value, size);
    }

    private static String formatPits(ScoreboardContext context, Driver driver) {
        Integer requiredPits = context.heat().getTotalPits();
        if (requiredPits == null || requiredPits <= 0) {
            return "";
        }
        int pits = driver.getPitstops();

        String pitsColor = "&x";
        if (pits >= requiredPits) {
            pitsColor = "&s";
        } else if (pits > 0) {
            pitsColor = "&v";
        } else {
            pitsColor = "&e";
        }

        return " &mP: " + pitsColor + pits;
    }

    static String scoreboardTitle(ScoreboardContext context, String baseKey) {
        String base = context.plugin().getTranslationUtil().getTranslated(context.viewer(), baseKey);
        if (context.compact() && base.length() > 8) {
            base = base.substring(0, 8);
        }
        String heatName = context.heat().getName();
        String eventName = "";
        if (context.heat().getRound() != null && context.heat().getRound().getEvent() != null) {
            eventName = normalizeEventName(context.heat().getRound().getEvent().getDisplayName());
        }
        if (eventName.length() > 14) {
            eventName = eventName.substring(0, 14);
        }

        if (eventName.isEmpty()) {
            return base + " &m| &x" + heatName;
        }
        return base + " &m| &x" + heatName + " &m| &x" + eventName;
    }

    private static String normalizeEventName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }

        if (rawName.startsWith("QuickRace_") || rawName.toLowerCase().startsWith("quickrace")) {
            return "QuickRace";
        }

        return rawName;
    }

    static String viewersSummary(ScoreboardContext context) {
        return tr(context, "scoreboard_v2_drivers", "{count}", String.valueOf(context.heat().getDriverCount()));
    }

    static String trackSummary(ScoreboardContext context) {
        return tr(context, "scoreboard_v2_track", "{track}", context.heat().getTrackNameWS());
    }

    static String lapsSummary(ScoreboardContext context) {
        return tr(context, "scoreboard_v2_laps", "{laps}", String.valueOf(context.heat().getTotalLaps()));
    }

    static String commonSeparator(ScoreboardContext context) {
        if (context.compact()) return spacer(0);
        return tr(context, "scoreboard_common_separator");
    }

    static String commonFooter(ScoreboardContext context) {
        if (context.compact()) return spacer(1);
        return tr(context, "scoreboard_common_footer");
    }

    static String viewerPositionSummary(ScoreboardContext context) {
        if (context.viewerDriver() == null) {
            return tr(context, "scoreboard_v2_position", "{position}", "-");
        }
        return tr(context, "scoreboard_v2_position", "{position}", "P" + context.viewerDriver().getPosition());
    }

    private static String tr(ScoreboardContext context, String key, String... placeholders) {
        String translated = context.plugin().getTranslationUtil().getTranslated(context.viewer(), key, placeholders);
        if (isMissingTranslation(translated)) {
            translated = context.plugin().getTranslation(key, "en_US", placeholders);
        }
        if (isMissingTranslation(translated)) {
            translated = applyFallback(key, placeholders);
        }
        return translated;
    }

    static Component trComponent(ScoreboardContext context, String key, String... placeholders) {
        String text = tr(context, key, placeholders);
        var theme = FRThemeResolver.resolveTheme(context.viewer());
        return FRThemeParser.parse(text, theme);
    }

    private static boolean isMissingTranslation(String translated) {
        return translated == null || translated.contains("[Lang Error]");
    }

    private static String applyFallback(String key, String... placeholders) {
        String langKey = SCOREBOARD_FALLBACKS.getOrDefault(key, key);
        String langCode = "en_US";
        String translated = langKey;

        try {
            FormulaRacing plugin = FormulaRacing.getInstance();
            if (plugin != null) {
                translated = plugin.getTranslation(langKey, langCode, placeholders);
            }
        } catch (Exception ignored) {
        }

        if (translated == null || translated.contains("[Lang Error]") || translated.equals(langKey)) {
            translated = MINIMAL_FALLBACK.getOrDefault(key, key);
            if (placeholders != null) {
                for (int i = 0; i < placeholders.length - 1; i += 2) {
                    translated = translated.replace(placeholders[i], placeholders[i + 1]);
                }
            }
        }

        return translated;
    }

    private static final Map<String, String> MINIMAL_FALLBACK;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("scoreboard_title_practice", "&d&l FREE PRACTICE");
        m.put("scoreboard_title_qualifying", "&b&l QUALIFYING");
        m.put("scoreboard_title_waiting", "&6&l WAITING");
        m.put("scoreboard_title_race", "&c&l RACE");
        m.put("scoreboard_title_finished", "&s&l FINISHED");
        m.put("scoreboard_v2_no_active_drivers", "&2No active drivers");
        m.put("scoreboard_v2_best_lap", "&2Best lap: &b{time}");
        m.put("scoreboard_v2_offline", "&7Offline");
        m.put("scoreboard_v2_drivers", "&2Drivers: &b{count}");
        m.put("scoreboard_v2_track", "&2Track: &b{track}");
        m.put("scoreboard_v2_laps", "&2Laps: &b{laps}");
        m.put("scoreboard_v2_position", "&2Position: &b{position}");
        m.put("scoreboard_v2_time", "&2Time: &b{time}");
        m.put("scoreboard_status_dnf_short", "DNF");
        m.put("scoreboard_status_offline", "Offline");
        m.put("scoreboard_status_in_pit", "In Pit");
        m.put("scoreboard_common_separator", "&7----------------------------------------");
        m.put("scoreboard_common_footer", "&ewolfnetwork.com.br");
        MINIMAL_FALLBACK = Map.copyOf(m);
    }

    private static Map<String, String> createScoreboardFallbacks() {
        Map<String, String> fallback = new HashMap<>();
        fallback.put("scoreboard_title_practice", "scoreboard_title_practice");
        fallback.put("scoreboard_title_qualifying", "scoreboard_title_qualifying");
        fallback.put("scoreboard_title_waiting", "scoreboard_title_waiting");
        fallback.put("scoreboard_title_race", "scoreboard_title_race");
        fallback.put("scoreboard_title_finished", "scoreboard_title_finished");

        fallback.put("scoreboard_common_separator", "scoreboard_common_separator");
        fallback.put("scoreboard_common_footer", "scoreboard_common_footer");

        fallback.put("scoreboard_v2_no_active_drivers", "scoreboard_v2_no_active_drivers");
        fallback.put("scoreboard_v2_best_lap", "scoreboard_v2_best_lap");
        fallback.put("scoreboard_v2_offline", "scoreboard_v2_offline");
        fallback.put("scoreboard_v2_drivers", "scoreboard_v2_drivers");
        fallback.put("scoreboard_v2_track", "scoreboard_v2_track");
        fallback.put("scoreboard_v2_laps", "scoreboard_v2_laps");
        fallback.put("scoreboard_v2_position", "scoreboard_v2_position");
        fallback.put("scoreboard_v2_time", "scoreboard_v2_time");

        fallback.put("scoreboard_status_dnf_short", "scoreboard_status_dnf_short");
        fallback.put("scoreboard_status_offline", "scoreboard_status_offline");
        fallback.put("scoreboard_status_in_pit", "scoreboard_status_in_pit");
        return fallback;
    }

    static String spacer(int index) {
        return SPACERS[Math.floorMod(index, SPACERS.length)];
    }

    static void padToMinHeight(List<String> lines, int minLines) {
        int start = 6;
        while (lines.size() < minLines) {
            lines.add(spacer(start++));
        }
    }

    static int minHeightForRacing(int driverCount) {
        if (driverCount <= 1) {
            return 5;
        }
        if (driverCount <= 3) {
            return 6;
        }
        if (driverCount <= 7) {
            return 7;
        }
        if (driverCount <= 12) {
            return 8;
        }
        return 9;
    }

    static int minHeightForPractice(int driverCount) {
        if (driverCount <= 1) {
            return 6;
        }
        if (driverCount <= 3) {
            return 7;
        }
        if (driverCount <= 7) {
            return 8;
        }
        return 9;
    }

    static int minHeightForQualifying(int driverCount) {
        if (driverCount <= 1) {
            return 7;
        }
        if (driverCount <= 3) {
            return 8;
        }
        if (driverCount <= 7) {
            return 9;
        }
        return 10;
    }

    static int minHeightForFinished(int driverCount) {
        if (driverCount <= 1) {
            return 5;
        }
        if (driverCount <= 3) {
            return 6;
        }
        if (driverCount <= 7) {
            return 7;
        }
        return 8;
    }

    static int minHeightForWaiting(int driverCount) {
        if (driverCount <= 1) {
            return 4;
        }
        if (driverCount <= 3) {
            return 5;
        }
        if (driverCount <= 7) {
            return 6;
        }
        return 7;
    }
}
