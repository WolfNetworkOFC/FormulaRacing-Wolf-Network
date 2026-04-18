package dev.EfraGroup.formulaRacing.Utils.scoreboard.style;

import java.util.HashMap;
import java.util.Map;

/**
 * Width metrics for the Minecraft Java Edition default font (Unifont/ascii.png).
 * Used for pixel-precise padding on the scoreboard.
 *
 * Based on the vanilla Minecraft font widths:
 * - Space (32) = 4px
 * - Most chars (A-Z, a-z, 0-9, etc.) = 6px
 * - Narrow chars (i, l, t, !, '.', ',', ':', ';', I) = 2-4px
 * - Bold adds +1px to each character
 */
public final class MinecraftFontMetrics {
    private MinecraftFontMetrics() {}

    // Standard widths for common characters
    private static final int DEFAULT_WIDTH = 6;
    private static final int SPACE_WIDTH = 4;
    private static final Map<Character, Integer> WIDTH_MAP = createWidthMap();

    private static Map<Character, Integer> createWidthMap() {
        Map<Character, Integer> map = new HashMap<>();
        
        // Space
        map.put(' ', SPACE_WIDTH);
        
        // Narrow chars (2-4px)
        map.put('i', 2);
        map.put('I', 4);
        map.put('l', 2);
        map.put('t', 4);
        map.put('!', 2);
        map.put('.', 2);
        map.put(',', 2);
        map.put(':', 2);
        map.put(';', 2);
        map.put('\'', 2);
        map.put('"', 4);
        map.put('[', 4);
        map.put(']', 4);
        map.put('(', 4);
        map.put(')', 4);
        map.put('{', 4);
        map.put('}', 4);
        map.put('<', 5);
        map.put('>', 5);
        map.put('|', 2);
        map.put('`', 3);
        map.put('f', 4);
        map.put('j', 4);
        map.put('k', 5);
        
        // Numbers (standard 6px except 1)
        map.put('1', 4);
        
        // Special handling for some symbols that appear commonly in usernames
        map.put('-', 4);
        map.put('_', 6);
        
        return map;
    }

    /**
     * Get the pixel width of a single character in the default Minecraft font.
     * @param c the character
     * @param bold whether the character is bold
     * @return width in pixels
     */
    public static int charWidth(char c, boolean bold) {
        int baseWidth = WIDTH_MAP.getOrDefault(c, DEFAULT_WIDTH);
        return bold ? baseWidth + 1 : baseWidth;
    }

    /**
     * Calculate the visible pixel width of a string, ignoring color codes.
     * @param text the string (may contain § color codes)
     * @return width in pixels
     */
    public static int stringWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int width = 0;
        boolean bold = false;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Handle color codes
            if (c == '§' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (code == 'l' || code == 'L') {
                    bold = true;
                } else if (code == 'r' || code == 'R' || (code >= '0' && code <= 'f') || (code >= 'A' && code <= 'F')) {
                    bold = false;
                }
                i++; // Skip the color code character
                continue;
            }
            
            width += charWidth(c, bold);
        }
        
        return width;
    }

    /**
     * Pad a string with trailing spaces to reach a target pixel width.
     * @param text the original string
     * @param targetWidth target width in pixels
     * @return padded string (may be slightly shorter if target is not divisible by space width)
     */
    public static String padToPixels(String text, int targetWidth) {
        if (text == null) {
            text = "";
        }
        
        int currentWidth = stringWidth(text);
        if (currentWidth >= targetWidth) {
            return text;
        }
        
        int neededPixels = targetWidth - currentWidth;
        int spacesNeeded = neededPixels / SPACE_WIDTH;
        
        StringBuilder result = new StringBuilder(text);
        for (int i = 0; i < spacesNeeded; i++) {
            result.append(' ');
        }
        
        return result.toString();
    }

    /**
     * Truncate a string to fit within a maximum pixel width.
     * @param text the original string
     * @param maxWidth maximum width in pixels
     * @param ellipsis if true, add ".." at the end when truncating
     * @return truncated string
     */
    public static String truncateToPixels(String text, int maxWidth, boolean ellipsis) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        int width = 0;
        boolean bold = false;
        int lastValidIndex = -1;
        
        // Find how many characters fit
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Handle color codes
            if (c == '§' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (code == 'l' || code == 'L') {
                    bold = true;
                } else if (code == 'r' || code == 'R' || (code >= '0' && code <= 'f') || (code >= 'A' && code <= 'F')) {
                    bold = false;
                }
                i++; // Skip the color code character
                continue;
            }
            
            int charW = charWidth(c, bold);
            if (width + charW > maxWidth) {
                break;
            }
            width += charW;
            lastValidIndex = i;
        }
        
        if (lastValidIndex < 0) {
            return "";
        }
        
        String result = text.substring(0, lastValidIndex + 1);
        
        if (ellipsis && lastValidIndex < text.length() - 1) {
            // Try to add ".." if there's room
            int ellipsisWidth = stringWidth("..");
            if (stringWidth(result) + ellipsisWidth <= maxWidth) {
                result = result + "..";
            }
        }
        
        return result;
    }
}
