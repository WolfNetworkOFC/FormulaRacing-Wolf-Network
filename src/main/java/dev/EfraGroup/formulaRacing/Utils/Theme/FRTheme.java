package dev.EfraGroup.formulaRacing.Utils.Theme;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class FRTheme {

    // ── Tokens derived from the player's color ─────────────────────────────
    private final TextColor primary;   // &p  main highlight (player's color1)
    private final TextColor accent;    // &a  secondary highlight (player's color2)

    // ── Fixed tokens — readable body, independent of the player ─────────────
    private final TextColor headline;  // &h  titles / scoreboard header
    private final TextColor text;      // &x  main text body
    private final TextColor muted;     // &m  labels, separators, units
    private final TextColor success;   // &s  OK / positive
    private final TextColor warning;   // &w  warning
    private final TextColor error;     // &e  error / negative
    private final TextColor info;      // &i  neutral info
    private final TextColor award;     // &v  podium / awards
    private final TextColor broadcast; // &b  global announcements

    // ── Legacy compatibility aliases (old tokens) ──────────────────────────
    /** @deprecated use {@link #getPrimary()} via &amp;p */
    @Deprecated public TextColor getSecondary()  { return accent; }
    /** @deprecated use {@link #getMuted()} via &amp;m */
    @Deprecated public TextColor getTitle()      { return muted; }

    public FRTheme(TextColor primary, TextColor accent,
                   TextColor headline, TextColor text, TextColor muted,
                   TextColor success, TextColor warning, TextColor error,
                   TextColor info, TextColor award, TextColor broadcast) {
        this.primary   = primary;
        this.accent    = accent;
        this.headline  = headline;
        this.text      = text;
        this.muted     = muted;
        this.success   = success;
        this.warning   = warning;
        this.error     = error;
        this.info      = info;
        this.award     = award;
        this.broadcast = broadcast;
    }

    public TextColor getPrimary()   { return primary; }
    public TextColor getAccent()    { return accent; }
    public TextColor getHeadline()  { return headline; }
    public TextColor getText()      { return text; }
    public TextColor getMuted()     { return muted; }
    public TextColor getSuccess()   { return success; }
    public TextColor getWarning()   { return warning; }
    public TextColor getError()     { return error; }
    public TextColor getInfo()      { return info; }
    public TextColor getAward()     { return award; }
    public TextColor getBroadcast() { return broadcast; }

    // ── Fixed tokens (shared between all instances) ─────────────────────────
    private static final TextColor FIXED_HEADLINE  = TextColor.color(0xf0f0f0);
    private static final TextColor FIXED_TEXT      = TextColor.color(0xe6e6e6);
    private static final TextColor FIXED_MUTED     = TextColor.color(0x9aa0a6);
    private static final TextColor FIXED_SUCCESS   = TextColor.color(0x7bf200);
    private static final TextColor FIXED_WARNING   = TextColor.color(0xffc93a);
    private static final TextColor FIXED_ERROR     = TextColor.color(0xff5757);
    private static final TextColor FIXED_INFO      = TextColor.color(0x6cc3ff);
    private static final TextColor FIXED_AWARD     = TextColor.color(0xffd700);
    private static final TextColor FIXED_BROADCAST = TextColor.color(0x00e5ff);

    private static final TextColor DEFAULT_PRIMARY = TextColor.color(0x7bf200);
    private static final TextColor DEFAULT_ACCENT  = TextColor.color(0x00cc99);

    // ── Luminance clamping ───────────────────────────────────────────────────
    /** Minimum perceived luminance (0–1, ITU-R BT.709) for a readable accent on dark BG. */
    private static final float MIN_LUMINANCE = 0.12f;
    /** Maximum perceived luminance — avoids blinding white-ish colours. */
    private static final float MAX_LUMINANCE = 0.90f;

    public static FRTheme defaultTheme() {
        return new FRTheme(
                DEFAULT_PRIMARY, DEFAULT_ACCENT,
                FIXED_HEADLINE, FIXED_TEXT, FIXED_MUTED,
                FIXED_SUCCESS, FIXED_WARNING, FIXED_ERROR,
                FIXED_INFO, FIXED_AWARD, FIXED_BROADCAST
        );
    }

    /**
     * Build a theme from the player's chosen colours.
     * primary/accent are clamped to a safe luminance range so they always
     * stay visible against a dark scoreboard / action-bar background.
     */
    public static FRTheme forPlayer(String primaryHex, String accentHex) {
        TextColor primary = clampLuminance(parseHex(primaryHex, DEFAULT_PRIMARY));
        TextColor accent  = resolveAccent(primaryHex, accentHex, primary);
        return new FRTheme(
                primary, accent,
                FIXED_HEADLINE, FIXED_TEXT, FIXED_MUTED,
                FIXED_SUCCESS, FIXED_WARNING, FIXED_ERROR,
                FIXED_INFO, FIXED_AWARD, FIXED_BROADCAST
        );
    }

    /** Legacy compatibility — maps old (color1, color2) call to forPlayer(). */
    public static FRTheme fromPlayerColors(String primaryHex, String secondaryHex) {
        return forPlayer(primaryHex, secondaryHex);
    }

    // ── Parsing & luminance helpers ──────────────────────────────────────────

    private static TextColor parseHex(String hex, TextColor fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return TextColor.fromHexString("#" + clean);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Clamp a colour's perceived luminance (ITU-R BT.709) to [MIN_LUMINANCE, MAX_LUMINANCE]
     * by scaling the linear RGB values uniformly, so it stays visible on a dark background
     * without being blinding-white.
     */
    private static TextColor clampLuminance(TextColor color) {
        float lum = relativeLuminance(color);
        if (lum >= MIN_LUMINANCE && lum <= MAX_LUMINANCE) return color;

        float target = lum < MIN_LUMINANCE ? MIN_LUMINANCE : MAX_LUMINANCE;
        float scale  = (target + 0.05f) / (lum + 0.05f);

        float r = linearize(color.red())   * scale;
        float g = linearize(color.green()) * scale;
        float b = linearize(color.blue())  * scale;

        r = Math.max(0f, Math.min(1f, r));
        g = Math.max(0f, Math.min(1f, g));
        b = Math.max(0f, Math.min(1f, b));

        return TextColor.color(delinearize(r), delinearize(g), delinearize(b));
    }

    private static TextColor resolveAccent(String accentHex, String color2Hex, TextColor primary) {
        TextColor parsed = parseHex(color2Hex, null);
        if (parsed == null || parsed.equals(primary)) {
            parsed = parseHex(accentHex, null);
        }
        if (parsed == null) return DEFAULT_ACCENT;
        return clampLuminance(parsed);
    }

    /** ITU-R BT.709 relative luminance of an sRGB colour, in [0, 1]. */
    private static float relativeLuminance(TextColor c) {
        return 0.2126f * linearize(c.red())
             + 0.7152f * linearize(c.green())
             + 0.0722f * linearize(c.blue());
    }

    /** sRGB channel (0–255) → linear [0, 1]. */
    private static float linearize(int channel) {
        float v = channel / 255f;
        return v <= 0.04045f ? v / 12.92f : (float) Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Linear [0, 1] → sRGB channel (0–255). */
    private static int delinearize(float linear) {
        float v = linear <= 0.0031308f
                ? linear * 12.92f
                : (float) (1.055 * Math.pow(linear, 1.0 / 2.4) - 0.055);
        return Math.round(v * 255f);
    }

    // ── Component builder helpers ────────────────────────────────────────────

    public Component label(String textStr) {
        return Component.text(textStr).color(primary);
    }

    public Component value(String textStr) {
        return Component.text(textStr).color(accent);
    }

    public Component highlight(String textStr) {
        return Component.text(textStr).color(success);
    }

    public Component warn(String textStr) {
        return Component.text(textStr).color(warning);
    }

    public Component err(String textStr) {
        return Component.text(textStr).color(error);
    }

    public Component bracket(String textStr) {
        return Component.text("[").color(primary)
                .append(Component.text(textStr).color(accent))
                .append(Component.text("]").color(primary));
    }

    public Component bracket(Component content) {
        return Component.text("[").color(primary)
                .append(content.color(accent))
                .append(Component.text("]").color(primary));
    }

    public Component getViewButton() {
        return Component.text("[»]").color(muted);
    }

    public Component getEditButton(String val) {
        return Component.text("[").color(primary)
                .append(Component.text(val).color(accent))
                .append(Component.text("]").color(primary));
    }

    public Component getAddButton() {
        return Component.text("[+]").color(success);
    }

    public Component getRemoveButton() {
        return Component.text("[-]").color(error);
    }

    public Component getRefreshButton() {
        return Component.text("↻").color(muted);
    }

    public Component getSeparator() {
        return Component.text("------------------------").color(muted);
    }

    public Component getSpacersStart() {
        return Component.text("--- ").color(muted);
    }

    public Component getSpacersEnd() {
        return Component.text(" ---").color(muted);
    }

    public Component getTitleLine(String titleText) {
        return getSpacersStart()
                .append(Component.text(titleText).color(primary))
                .append(getSpacersEnd());
    }

    public Component getPageSelector(int page, int total) {
        return Component.text("--- <<<  page ")
                .color(muted)
                .append(Component.text(String.valueOf(page)).color(primary))
                .append(Component.text(" of ").color(muted))
                .append(Component.text(String.valueOf(total)).color(primary))
                .append(Component.text(" >>> ---").color(muted));
    }
}
