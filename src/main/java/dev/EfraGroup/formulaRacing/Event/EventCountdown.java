//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Event;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Participant.Subscriber;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeDefaults;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class EventCountdown {
    private final FormulaRacing plugin;
    private final Events event;
    private BossBar bossBar;
    private ScheduledTask countdownTask;
    private int totalSeconds;
    private int remainingSeconds;
    private String label;
    private Consumer<Events> onComplete;
    private boolean active = false;

    public EventCountdown(FormulaRacing plugin, Events event) {
        this.plugin = plugin;
        this.event = event;
    }

    public void start(int seconds, String label, Consumer<Events> onComplete) {
        if (this.active) {
            this.stop();
        }

        this.totalSeconds = seconds;
        this.remainingSeconds = seconds;
        this.label = label;
        this.onComplete = onComplete;
        this.active = true;
        this.bossBar = Bukkit.createBossBar(this.formatTitle(), BarColor.GREEN, BarStyle.SOLID, new BarFlag[0]);
        this.showToAll();
        this.countdownTask = SchedulerHelper.runTaskTimer(this.plugin, () -> {
            if (this.remainingSeconds <= 0) {
                this.finish();
            } else {
                this.updateBossBar();
                --this.remainingSeconds;
            }
        }, 0L, 20L);
    }

    private void updateBossBar() {
        if (this.bossBar != null) {
            double progress = (double)this.remainingSeconds / (double)this.totalSeconds;
            this.bossBar.setProgress(Math.max((double)0.0F, Math.min((double)1.0F, progress)));
            this.bossBar.setTitle(this.formatTitle());
            if (progress < 0.2) {
                this.bossBar.setColor(BarColor.RED);
                this.playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F);
            } else if (progress < (double)0.5F) {
                this.bossBar.setColor(BarColor.YELLOW);
                if (this.remainingSeconds % 10 == 0) {
                    this.playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F);
                }
            }

        }
    }

    private void playSound(Sound sound, float pitch) {
        for(Player player : this.bossBar.getPlayers()) {
            player.playSound(player.getLocation(), sound, 1.0F, pitch);
        }

    }

    private String formatTitle() {
        int minutes = this.remainingSeconds / 60;
        int seconds = this.remainingSeconds % 60;
        String displayLabel = this.label;
        if (this.label.startsWith("event_")) {
            displayLabel = this.plugin.getTranslationUtil().getTranslated(this.label, "pt_BR", new String[0]);
        }

        FRTheme defaultTheme = FRThemeDefaults.getDefaultTheme();
        String raw = String.format("%s &w%02d:%02d", displayLabel, minutes, seconds);
        return LegacyComponentSerializer.legacySection().serialize(
            FRThemeParser.parseWithLegacy(raw, defaultTheme));
    }

    private void showToAll() {
        if (this.bossBar != null) {
            for(Subscriber subscriber : this.event.getSubscribers().values()) {
                Player player = Bukkit.getPlayer(subscriber.getUuid());
                if (player != null && player.isOnline()) {
                    this.bossBar.addPlayer(player);
                }
            }

            if (this.plugin.getSpectatorManager() != null) {
                for(UUID specId : this.plugin.getSpectatorManager().getSpectatorsInEvent(this.event.getId())) {
                    Player player = Bukkit.getPlayer(specId);
                    if (player != null && player.isOnline()) {
                        this.bossBar.addPlayer(player);
                    }
                }
            }

        }
    }

    private void finish() {
        this.stop();
        if (this.onComplete != null) {
            this.onComplete.accept(this.event);
        }

    }

    public void stop() {
        if (this.countdownTask != null) {
            this.countdownTask.cancel();
            this.countdownTask = null;
        }

        if (this.bossBar != null) {
            this.bossBar.removeAll();
            this.bossBar = null;
        }

        this.active = false;
    }

    public boolean isActive() {
        return this.active;
    }

    public int getRemainingSeconds() {
        return this.remainingSeconds;
    }

    public void addPlayer(Player player) {
        if (this.bossBar != null && this.active) {
            this.bossBar.addPlayer(player);
        }

    }
}
