package dev.EfraGroup.formulaRacing.Utils.scoreboard.style;

import net.md_5.bungee.api.ChatColor;

public final class TimingScoreboardStyle {
    private TimingScoreboardStyle() {
    }

    public static String positionColor(int pos) {
        return switch (pos) {
            case 1 -> "&v";
            case 2 -> "&2";
            case 3 -> "&e";
            default -> "&2";
        };
    }

    public static String rankTag(int pos, boolean fastestLap, boolean finished) {
        StringBuilder rank = new StringBuilder(positionColor(pos)).append(pos);
        if (pos < 10) {
            rank.append(' ');
        }
        if (fastestLap) {
            rank.append("§n");
        }
        if (finished) {
            rank.append("§o");
        }
        rank.append("§r");
        return rank.toString();
    }

    public static String teamMarker(String accentMarker, int pos) {
        String marker = normalizeAccentMarker(accentMarker);
        return positionColor(pos) + "§l" + marker + marker + "§r";
    }

    public static String normalizeAccentMarker(String accentMarker) {
        if (accentMarker == null || accentMarker.isBlank()) {
            return "┃";
        }
        return accentMarker;
    }

    public static String padRight(String value, int width) {
        return padRight(value, width, null);
    }

    public static String padRight(String value, int width, String colorPrefix) {
        if (value == null) {
            value = "";
        }
        int visibleLength = value.length();
        if (colorPrefix != null) {
            visibleLength = ChatColor.stripColor(value).length();
        }
        if (visibleLength >= width) {
            return colorPrefix != null ? colorPrefix + value : value;
        }
        StringBuilder builder = new StringBuilder();
        if (colorPrefix != null) {
            builder.append(colorPrefix);
        }
        builder.append(value);
        int currentLength = ChatColor.stripColor(builder.toString()).length();
        while (currentLength < width) {
            builder.append(' ');
            currentLength++;
        }
        return builder.toString();
    }
}
