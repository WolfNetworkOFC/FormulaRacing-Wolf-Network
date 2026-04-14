package dev.EfraGroup.formulaRacing.Utils.Theme;

public enum FRThemeColor {
    PRIMARY("theme.primary"),
    SECONDARY("theme.secondary"),
    SUCCESS("theme.success"),
    WARNING("theme.warning"),
    ERROR("theme.error"),
    BROADCAST("theme.broadcast"),
    AWARD("theme.award"),
    TITLE("theme.title"),
    INFO("theme.info"),
    ACCENT("theme.accent");

    private final String configKey;

    FRThemeColor(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }
}
