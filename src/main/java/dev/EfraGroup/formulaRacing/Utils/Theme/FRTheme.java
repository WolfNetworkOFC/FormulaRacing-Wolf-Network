package dev.EfraGroup.formulaRacing.Utils.Theme;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class FRTheme {
    private final TextColor primary;
    private final TextColor secondary;
    private final TextColor success;
    private final TextColor warning;
    private final TextColor error;
    private final TextColor broadcast;
    private final TextColor award;
    private final TextColor title;
    private final TextColor info;
    private final TextColor accent;

    public FRTheme(TextColor primary, TextColor secondary, TextColor success,
                   TextColor warning, TextColor error, TextColor broadcast,
                   TextColor award, TextColor title, TextColor info, TextColor accent) {
        this.primary = primary;
        this.secondary = secondary;
        this.success = success;
        this.warning = warning;
        this.error = error;
        this.broadcast = broadcast;
        this.award = award;
        this.title = title;
        this.info = info;
        this.accent = accent;
    }

    public TextColor getPrimary() { return primary; }
    public TextColor getSecondary() { return secondary; }
    public TextColor getSuccess() { return success; }
    public TextColor getWarning() { return warning; }
    public TextColor getError() { return error; }
    public TextColor getBroadcast() { return broadcast; }
    public TextColor getAward() { return award; }
    public TextColor getTitle() { return title; }
    public TextColor getInfo() { return info; }
    public TextColor getAccent() { return accent; }

    public static FRTheme defaultTheme() {
        return new FRTheme(
                TextColor.color(0x7bf200),
                TextColor.color(NamedTextColor.WHITE),
                TextColor.color(0x7bf200),
                TextColor.color(NamedTextColor.YELLOW),
                TextColor.color(0xff7a75),
                TextColor.color(NamedTextColor.AQUA),
                TextColor.color(NamedTextColor.GOLD),
                TextColor.color(NamedTextColor.DARK_GRAY),
                TextColor.color(0xcc99ff),
                TextColor.color(0x00cc99)
        );
    }

    public static FRTheme fromPlayerColors(String primaryHex, String secondaryHex) {
        TextColor primary = parseHex(primaryHex);
        TextColor secondary = parseHex(secondaryHex);

        TextColor success = shiftHue(primary, 0.33f);
        TextColor warning = shiftHue(primary, 0.15f);
        TextColor error = shiftHue(primary, 0.0f, 0.85f);
        TextColor broadcast = shiftHue(primary, 0.5f);
        TextColor award = shiftSaturation(primary, 1.0f);
        TextColor title = adjustLightness(primary, -0.25f);
        TextColor info = shiftHue(primary, 0.75f);
        TextColor accent = shiftHue(primary, 0.58f);

        return new FRTheme(primary, secondary, success, warning, error, broadcast, award, title, info, accent);
    }

    private static TextColor parseHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return TextColor.color(0x7bf200);
        }
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return TextColor.fromHexString("#" + clean);
        } catch (IllegalArgumentException e) {
            return TextColor.color(0x7bf200);
        }
    }

    private static TextColor shiftHue(TextColor base, float hueShift) {
        return shiftHue(base, hueShift, -1f);
    }

    private static TextColor shiftHue(TextColor base, float hueShift, float saturationOverride) {
        int r = (base.red() * 255);
        int g = (base.green() * 255);
        int b = (base.blue() * 255);

        float[] hsv = rgbToHsv(r, g, b);
        hsv[0] = (hsv[0] + hueShift) % 1.0f;
        if (hsv[0] < 0) hsv[0] += 1.0f;
        if (saturationOverride >= 0) hsv[1] = saturationOverride;

        int[] rgb = hsvToRgb(hsv[0], hsv[1], hsv[2]);
        return TextColor.color(rgb[0], rgb[1], rgb[2]);
    }

    private static TextColor shiftSaturation(TextColor base, float saturation) {
        int r = (base.red() * 255);
        int g = (base.green() * 255);
        int b = (base.blue() * 255);

        float[] hsv = rgbToHsv(r, g, b);
        hsv[1] = Math.min(1.0f, saturation);

        int[] rgb = hsvToRgb(hsv[0], hsv[1], hsv[2]);
        return TextColor.color(rgb[0], rgb[1], rgb[2]);
    }

    private static TextColor adjustLightness(TextColor base, float delta) {
        int r = (base.red() * 255);
        int g = (base.green() * 255);
        int b = (base.blue() * 255);

        float[] hsv = rgbToHsv(r, g, b);
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] + delta));

        int[] rgb = hsvToRgb(hsv[0], hsv[1], hsv[2]);
        return TextColor.color(rgb[0], rgb[1], rgb[2]);
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float h = 0f;
        float s = max == 0 ? 0f : delta / max;
        float v = max;

        if (delta != 0) {
            if (max == rf) {
                h = ((gf - bf) / delta) % 6f;
            } else if (max == gf) {
                h = (bf - rf) / delta + 2f;
            } else {
                h = (rf - gf) / delta + 4f;
            }
            h /= 6f;
            if (h < 0) h += 1f;
        }

        return new float[]{h, s, v};
    }

    private static int[] hsvToRgb(float h, float s, float v) {
        int r, g, b;

        int i = (int) Math.floor(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        switch (i % 6) {
            case 0 -> { r = Math.round(v * 255); g = Math.round(t * 255); b = Math.round(p * 255); }
            case 1 -> { r = Math.round(q * 255); g = Math.round(v * 255); b = Math.round(p * 255); }
            case 2 -> { r = Math.round(p * 255); g = Math.round(v * 255); b = Math.round(t * 255); }
            case 3 -> { r = Math.round(p * 255); g = Math.round(q * 255); b = Math.round(v * 255); }
            case 4 -> { r = Math.round(t * 255); g = Math.round(p * 255); b = Math.round(v * 255); }
            default -> { r = Math.round(v * 255); g = Math.round(p * 255); b = Math.round(q * 255); }
        }

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return new int[]{r, g, b};
    }

    public Component label(String text) {
        return Component.text(text).color(primary);
    }

    public Component value(String text) {
        return Component.text(text).color(secondary);
    }

    public Component highlight(String text) {
        return Component.text(text).color(success);
    }

    public Component warn(String text) {
        return Component.text(text).color(warning);
    }

    public Component err(String text) {
        return Component.text(text).color(error);
    }

    public Component bracket(String text) {
        return Component.text("[").color(primary)
                .append(Component.text(text).color(secondary))
                .append(Component.text("]").color(primary));
    }

    public Component bracket(Component content) {
        return Component.text("[").color(primary)
                .append(content.color(secondary))
                .append(Component.text("]").color(primary));
    }

    public Component getViewButton() {
        return Component.text("[»]").color(title);
    }

    public Component getEditButton(String value) {
        return Component.text("[").color(primary)
                .append(Component.text(value).color(secondary))
                .append(Component.text("]").color(primary));
    }

    public Component getAddButton() {
        return Component.text("[+]").color(success);
    }

    public Component getRemoveButton() {
        return Component.text("[-]").color(error);
    }

    public Component getRefreshButton() {
        return Component.text("↻").color(title);
    }

    public Component getSeparator() {
        return Component.text("------------------------").color(title);
    }

    public Component getSpacersStart() {
        return Component.text("--- ").color(title);
    }

    public Component getSpacersEnd() {
        return Component.text(" ---").color(title);
    }

    public Component getTitleLine(String titleText) {
        return getSpacersStart()
                .append(Component.text(titleText).color(primary))
                .append(getSpacersEnd());
    }

    public Component getPageSelector(int page, int total) {
        return Component.text("--- <<<  page ")
                .color(title)
                .append(Component.text(String.valueOf(page)).color(primary))
                .append(Component.text(" of ").color(title))
                .append(Component.text(String.valueOf(total)).color(primary))
                .append(Component.text(" >>> ---").color(title));
    }
}
