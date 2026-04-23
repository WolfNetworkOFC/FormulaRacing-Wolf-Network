package dev.EfraGroup.formulaRacing.PlaceHolder;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class PlaceholderRegister extends PlaceholderExpansion {

    private static final long UPDATE_INTERVAL_TICKS = 600L;

    private final FormulaRacing plugin;
    private volatile int cachedOpenTracksCount;
    private BukkitTask refreshTask;

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
        this.refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            this.plugin,
            this::refreshCache,
            UPDATE_INTERVAL_TICKS,
            UPDATE_INTERVAL_TICKS
        );
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

    @Override
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

        return null;
    }
}
