package dev.EfraGroup.formulaRacing.Utils;

public class TimeFormatter {

    public static String formatTime(double elapsed) {
        long totalMillis = Math.round(elapsed * 1000.0);
        long minutes = totalMillis / 60000L;
        long seconds = totalMillis % 60000L / 1000L;
        long millis = totalMillis % 1000L;
        return minutes > 0L
            ? String.format("%02d:%02d.%03d", minutes, seconds, millis)
            : String.format("%02d.%03d", seconds, millis);
    }
}
