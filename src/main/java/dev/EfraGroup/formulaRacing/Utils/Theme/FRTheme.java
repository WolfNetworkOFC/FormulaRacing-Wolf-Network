package dev.EfraGroup.formulaRacing.Utils.Theme;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

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
}
