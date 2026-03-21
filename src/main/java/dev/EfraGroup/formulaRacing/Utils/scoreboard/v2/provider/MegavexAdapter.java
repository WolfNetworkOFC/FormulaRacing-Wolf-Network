package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.provider;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.entity.Player;

public class MegavexAdapter implements ScoreboardAdapter {
    private final FormulaRacing plugin;
    private final ScoreboardLibrary scoreboardLibrary;
    private final int maxRows;
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();

    public MegavexAdapter(FormulaRacing plugin, int maxRows) {
        this.plugin = plugin;
        this.maxRows = maxRows;
        ScoreboardLibrary loaded;
        try {
            loaded = ScoreboardLibrary.loadScoreboardLibrary(plugin);
        } catch (NoPacketAdapterAvailableException e) {
            loaded = new NoopScoreboardLibrary();
            this.plugin.getDebugManager().logRaceSystem("[ScoreboardV2] No packet adapter for Megavex. Using Noop library.");
        }
        this.scoreboardLibrary = loaded;
    }

    @Override
    public void create(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (this.sidebars.containsKey(player.getUniqueId())) {
            return;
        }

        Sidebar sidebar = this.scoreboardLibrary.createSidebar(this.maxRows);
        sidebar.addPlayer(player);
        this.sidebars.put(player.getUniqueId(), sidebar);
    }

    @Override
    public void updateTitle(Player player, String title) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Sidebar sidebar = this.sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            this.create(player);
            sidebar = this.sidebars.get(player.getUniqueId());
        }

        if (sidebar != null) {
            sidebar.title(this.toComponent(title));
        }
    }

    @Override
    public void updateLines(Player player, List<String> lines) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Sidebar sidebar = this.sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            this.create(player);
            sidebar = this.sidebars.get(player.getUniqueId());
        }

        if (sidebar == null) {
            return;
        }

        sidebar.clearLines();
        int limit = Math.min(lines.size(), sidebar.maxLines());
        for (int i = 0; i < limit; i++) {
            sidebar.line(i, this.toComponent(lines.get(i)));
        }
    }

    @Override
    public void delete(Player player) {
        if (player == null) {
            return;
        }
        Sidebar sidebar = this.sidebars.remove(player.getUniqueId());
        if (sidebar != null) {
            sidebar.close();
        }
    }

    @Override
    public boolean isHealthy(Player player) {
        return player != null && player.isOnline() && !(this.scoreboardLibrary instanceof NoopScoreboardLibrary);
    }

    public void shutdown() {
        for (Sidebar sidebar : this.sidebars.values()) {
            sidebar.close();
        }
        this.sidebars.clear();
        this.scoreboardLibrary.close();
    }

    private Component toComponent(String line) {
        if (line == null) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacySection().deserialize(line);
    }
}
