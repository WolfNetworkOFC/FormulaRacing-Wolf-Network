//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiUtilities {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static String formatDate(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    public static String formatDuration(long millis) {
        long seconds = millis / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        if (hours > 0L) {
            minutes %= 60L;
            return hours + "h " + minutes + "m";
        } else if (minutes > 0L) {
            seconds %= 60L;
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    public static Integer parseDurationToMillis(String duration) {
        if (duration != null && !duration.isEmpty()) {
            Pattern pattern = Pattern.compile("(\\d+)([hms])");
            Matcher matcher = pattern.matcher(duration.toLowerCase());
            int totalMillis = 0;

            while(matcher.find()) {
                int value = Integer.parseInt(matcher.group(1));
                switch (matcher.group(2)) {
                    case "h":
                        totalMillis += value * 3600000;
                        break;
                    case "m":
                        totalMillis += value * '\uea60';
                        break;
                    case "s":
                        totalMillis += value * 1000;
                }
            }

            return totalMillis > 0 ? totalMillis : null;
        } else {
            return null;
        }
    }

    public static String formatRaceTime(long millis) {
        long minutes = millis / 60000L;
        long seconds = millis % 60000L / 1000L;
        long milliseconds = millis % 1000L;
        return String.format("%02d:%02d.%03d", minutes, seconds, milliseconds);
    }

    public static String formatCompactTime(long millis) {
        long minutes = millis / 60000L;
        long seconds = millis % 60000L / 1000L;
        return String.format("%d:%02d", minutes, seconds);
    }

    public static String formatLapTime(long millis) {
        long minutes = millis / 60000L;
        long seconds = millis % 60000L / 1000L;
        long milliseconds = millis % 1000L;
        return String.format("%d:%02d.%03d", minutes, seconds, milliseconds);
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static long elapsedTime(long start, long end) {
        return end - start;
    }

    public static String formatPosition(int position) {
        if (position <= 0) {
            return "N/A";
        } else {
            String var10000;
            switch (position % 10) {
                case 1 -> var10000 = position == 11 ? "th" : "st";
                case 2 -> var10000 = position == 12 ? "th" : "nd";
                case 3 -> var10000 = position == 13 ? "th" : "rd";
                default -> var10000 = "th";
            }

            String suffix = var10000;
            return position + suffix;
        }
    }

    public static String createProgressBar(int current, int max, int length) {
        if (max <= 0) {
            return "[" + "▌".repeat(length) + "]";
        } else {
            int filled = (int)((double)current / (double)max * (double)length);
            filled = Math.min(filled, length);
            String filledBar = "▌".repeat(filled);
            String emptyBar = " ".repeat(length - filled);
            return "[" + filledBar + emptyBar + "]";
        }
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        } else if (text.length() <= maxLength) {
            return text;
        } else {
            String var10000 = text.substring(0, maxLength - 3);
            return var10000 + "...";
        }
    }
}
