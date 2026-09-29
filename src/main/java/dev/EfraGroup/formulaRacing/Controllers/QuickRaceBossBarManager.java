package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Green "Race on &lt;track&gt;" bossbar shown to players while they are competing
 * in a quick race.
 *
 * <p>One bar per viewer: the track name is baked into the title, so a player
 * who leaves one quick race and joins another on a different track must not keep
 * a stale title. Bars are stored per player and rebuilt whenever the race they
 * belong to ends.
 *
 * <p>Folia: bar creation and removal are dispatched through
 * {@link SchedulerHelper#runTaskFor} on the viewer's region thread, since
 * touching a player's boss bars off-thread is not safe.
 */
public class QuickRaceBossBarManager {

    private final FormulaRacing plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public QuickRaceBossBarManager(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    /** Shows (or refreshes) the race bar for a player on the given track. */
    public void showFor(Player player, String trackName) {
        if (player == null || !player.isOnline() || trackName == null || trackName.isEmpty()) {
            return;
        }
        SchedulerHelper.runTaskFor(this.plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            String title = buildTitle(player, trackName);
            BossBar existing = this.bars.get(player.getUniqueId());
            if (existing != null) {
                if (existing.getTitle().equals(title)) {
                    return;
                }
                existing.removeAll();
                existing.addPlayer(player);
                existing.setTitle(title);
                return;
            }
            BossBar bar = org.bukkit.Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SOLID);
            bar.setProgress(1.0);
            bar.addPlayer(player);
            this.bars.put(player.getUniqueId(), bar);
        });
    }

    /** Removes the bar from a single player (left the race, DNF, quit). */
    public void hideFrom(Player player) {
        if (player == null) {
            return;
        }
        BossBar bar = this.bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    /** Forgets a bar whose player is no longer online, without touching any bar. */
    public void hideOffline(UUID uuid) {
        this.bars.remove(uuid);
    }

    /** Removes the bar from every player tracking it. */
    public void hideAll() {
        for (UUID uuid : this.bars.keySet()) {
            BossBar bar = this.bars.remove(uuid);
            if (bar != null) {
                bar.removeAll();
            }
        }
    }

    public boolean hasBar(Player player) {
        return player != null && this.bars.containsKey(player.getUniqueId());
    }

    public void shutdown() {
        this.hideAll();
    }

    private String buildTitle(Player player, String trackName) {
        String lang = this.plugin.getTranslationUtil().getPlayerLanguage(player.getUniqueId());
        return this.plugin.getTranslation("quickrace_bossbar", lang, new String[]{"{track}", trackName});
    }
}
