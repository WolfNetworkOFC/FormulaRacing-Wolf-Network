package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder;

import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardContext;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class BuilderSupport {
    private static final String[] SPACERS = new String[]{
            "§0",
            "§1",
            "§2",
            "§3",
            "§4",
            "§5",
            "§6",
            "§7",
            "§8",
            "§9",
            "§a",
            "§b",
            "§c",
            "§d",
            "§e",
            "§f"
    };

    private BuilderSupport() {
    }

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

        for (int i = start; i < end; i++) {
            Driver current = sorted.get(i);
            Driver reference;
            if (context.spectator()) {
                if (qualifyingMode) {
                    reference = i == 0 ? null : leader;
                } else {
                    reference = i > 0 ? sorted.get(i - 1) : null;
                }
            } else {
                reference = referenceDriver;
            }
            lines.add(formatLine(context, i + 1, current, reference, qualifyingMode));
        }

        return lines;
    }

    static String stateLabel(HeatState state) {
        return switch (state) {
            case SETUP -> "§fPreparando";
            case IDLE -> "§fAguardando";
            case PRACTICE -> "§fPratica";
            case QUALIFYING -> "§fQualifying";
            case LOADED -> "§fGrid montado";
            case STARTING -> "§fLargada";
            case RACING -> "§fCorrida";
            case FINISHED -> "§fFinalizada";
        };
    }

    static String formatBestLap(ScoreboardContext context, Driver driver) {
        Lap bestLap = driver == null ? null : driver.getFastestLap();
        if (bestLap == null) {
            return tr(context, "scoreboard_v2_best_lap", "{time}", "§7--.---");
        }
        return tr(context, "scoreboard_v2_best_lap", "{time}", "§b" + formatTime(bestLap.getLapTime()));
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
        return "§e" + heatName + (eventName.isEmpty() ? "" : " §7/ §f" + eventName);
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

    private static String formatLine(ScoreboardContext context, int pos, Driver current, Driver reference, boolean qualifyingMode) {
        Player p = Bukkit.getPlayer(current.getUuid());
        String name = p != null ? p.getName() : tr(context, "scoreboard_v2_offline");
        if (name.length() > 11) {
            name = name.substring(0, 11);
        }

        boolean self = context.viewerDriver() != null && context.viewerDriver().getUuid().equals(current.getUuid());
        String gap = "-";
        String gapColor = "§f";
        if (!self && reference != null) {
            if (qualifyingMode) {
                Lap currentLap = current.getFastestLap();
                Lap referenceLap = reference.getFastestLap();
                if (currentLap != null && referenceLap != null) {
                    long diff = currentLap.getLapTime() - referenceLap.getLapTime();
                    if (diff >= 0) {
                        gapColor = "§a";
                        gap = "+" + formatTime(diff);
                    } else {
                        gapColor = "§c";
                        gap = "-" + formatTime(Math.abs(diff));
                    }
                }
            } else {
                int lapDelta = current.getLapCount() - reference.getLapCount();
                if (lapDelta != 0) {
                    if (lapDelta > 0) {
                        gapColor = "§c";
                        gap = "-" + lapDelta + "L";
                    } else {
                        gapColor = "§a";
                        gap = "+" + Math.abs(lapDelta) + "L";
                    }
                } else {
                    long diff = current.getTotalTime() - reference.getTotalTime();
                    if (diff < 0L) {
                        gapColor = "§c";
                        gap = "-" + formatTime(Math.abs(diff));
                    } else if (diff > 0L) {
                        gapColor = "§a";
                        gap = "+" + formatTime(diff);
                    }
                }
            }
        }

        String prefix = self ? "§e> " : "§f  ";
        String status = current.isDnf() ? " " + tr(context, "scoreboard_v2_status_dnf") : "";
        String rank = rankTag(pos);
        String pilotName = self ? "§e" + padRight(name, 12) : "§f" + padRight(name, 12);
        String pits = formatPits(context, current);
        String gapFixed = padLeft(gap, 8);

        return prefix + rank + " " + gapColor + gapFixed + " §8// " + pilotName + pits + status;
    }

    private static String rankTag(int pos) {
        if (pos == 1) {
            return "§6" + pos;
        }
        if (pos == 2) {
            return "§7" + pos;
        }
        if (pos == 3) {
            return "§c" + pos;
        }
        return "§f" + pos;
    }

    private static String padRight(String value, int size) {
        if (value.length() >= size) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value);
        while (sb.length() < size) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String padLeft(String value, int size) {
        if (value.length() >= size) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() + value.length() < size) {
            sb.append(' ');
        }
        sb.append(value);
        return sb.toString();
    }

    private static String formatPits(ScoreboardContext context, Driver driver) {
        Integer requiredPits = context.heat().getTotalPits();
        int pits = driver.getPitstops();

        String pitsColor = "§f";
        if (requiredPits != null && requiredPits > 0) {
            if (pits >= requiredPits) {
                pitsColor = "§a";
            } else if (pits > 0) {
                pitsColor = "§6";
            } else {
                pitsColor = "§c";
            }
        }

        return " " + tr(context, "scoreboard_v2_pits", "{count}", pitsColor + pits);
    }

    static String scoreboardTitle(ScoreboardContext context, String baseKey) {
        String base = context.plugin().getTranslationUtil().getTranslated(context.viewer(), baseKey);
        String heatName = context.heat().getName();
        String eventName = "";
        if (context.heat().getRound() != null && context.heat().getRound().getEvent() != null) {
            eventName = normalizeEventName(context.heat().getRound().getEvent().getDisplayName());
        }
        if (eventName.length() > 14) {
            eventName = eventName.substring(0, 14);
        }

        if (eventName.isEmpty()) {
            return base + " §8| §7" + heatName;
        }
        return base + " §8| §7" + heatName + " §8| §f" + eventName;
    }

    private static String normalizeEventName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }

        if (rawName.startsWith("QuickRace_") || rawName.equalsIgnoreCase("QuickRace") || rawName.toLowerCase().startsWith("quickrace")) {
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

    static String viewerPositionSummary(ScoreboardContext context) {
        if (context.viewerDriver() == null) {
            return tr(context, "scoreboard_v2_position", "{position}", "-");
        }
        return tr(context, "scoreboard_v2_position", "{position}", "P" + context.viewerDriver().getPosition());
    }

    private static String tr(ScoreboardContext context, String key, String... placeholders) {
        return context.plugin().getTranslationUtil().getTranslated(context.viewer(), key, placeholders);
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

    static int minHeightForClassification(int driverCount) {
        if (driverCount <= 1) {
            return 6;
        }
        if (driverCount <= 3) {
            return 7;
        }
        if (driverCount <= 7) {
            return 9;
        }
        return 11;
    }

    static int minHeightForWaiting(int driverCount) {
        if (driverCount <= 1) {
            return 5;
        }
        if (driverCount <= 3) {
            return 6;
        }
        if (driverCount <= 7) {
            return 8;
        }
        return 10;
    }
}
