package dev.EfraGroup.formulaRacing.PlaceHolder;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PlaceholderRegister extends PlaceholderExpansion {

    private static final long UPDATE_INTERVAL_TICKS = 600L;

    private final FormulaRacing plugin;
    private volatile int cachedOpenTracksCount;
    private FRTask refreshTask;

    public PlaceholderRegister(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public boolean registerExpansion() {
        this.refreshCache();

        if (!this.register()) {
            return false;
        }

        this.startRefreshTask();
        return true;
    }

    public void stop() {
        if (this.refreshTask != null) {
            this.refreshTask.cancel();
            this.refreshTask = null;
        }
    }

    private void startRefreshTask() {
        this.stop();
        this.refreshTask = SchedulerHelper.runTaskTimer(this.plugin, this::refreshCache, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    private void refreshCache() {
        try {
            this.cachedOpenTracksCount =
                this.plugin.getDatabaseManager().getOpenTracksCount();
        } catch (Exception ex) {
            this.plugin
                .getDebugManager()
                .logDatabaseOperation(
                    "[PlaceholderAPI] Falha ao atualizar open_tracks_count: " +
                    ex.getMessage()
                );
        }
    }

    public @NotNull String getIdentifier() {
        return "open";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", this.plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if ("tracks_count".equalsIgnoreCase(params)) {
            return Integer.toString(this.cachedOpenTracksCount);
        }

        if (player != null) {
            if ("player_hex_color1".equalsIgnoreCase(params)) {
                return toHexCode(this.plugin.getDatabaseManager().getPlayerColor1(player.getUniqueId()));
            }
            if ("player_hex_color2".equalsIgnoreCase(params)) {
                return toHexCode(this.plugin.getDatabaseManager().getPlayerColor2(player.getUniqueId()));
            }
        }

        return null;
    }

    private static String toHexCode(String hex) {
        if (hex == null) return "";
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        return "&" + "#" + clean.toUpperCase();
    }
}
