//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class RaceCountdown {
    private final FormulaRacing plugin;
    private final Heats heat;
    private final Runnable onComplete;
    private ScheduledTask countdownTask;
    private int lightsOn;
    private boolean completed;
    private int maxLights;
    private static final int TICKS_PER_LIGHT = 20;
    private static final int LIGHTS_OUT_DELAY = 20;

    public RaceCountdown(FormulaRacing plugin, Heats heat, Runnable onComplete) {
        this(plugin, heat, 5, onComplete);
    }

    public RaceCountdown(FormulaRacing plugin, Heats heat, int seconds, Runnable onComplete) {
        this.lightsOn = 0;
        this.completed = false;
        this.maxLights = 5;
        this.plugin = plugin;
        this.heat = heat;
        this.maxLights = seconds;
        this.onComplete = onComplete;
    }

    public void start() {
        if (!this.completed) {
            this.lightsOn = 0;
            if (this.heat.isLonely()) {
                this.announceLocalizedToAll("quali_countdown_prepare");
            } else {
                this.announceLocalizedToAll("race_countdown_prepare");
            }

            int[] tick = {0};
            this.countdownTask = SchedulerHelper.runTaskTimer(this.plugin, () -> {
                if (RaceCountdown.this.heat.getHeatState() != HeatState.STARTING) {
                    if (RaceCountdown.this.countdownTask != null) {
                        RaceCountdown.this.countdownTask.cancel();
                    }
                } else {
                    tick[0]++;
                    if (tick[0] % 20 == 0 && RaceCountdown.this.lightsOn < RaceCountdown.this.maxLights) {
                        RaceCountdown.this.lightsOn++;
                        RaceCountdown.this.onLightOn(RaceCountdown.this.lightsOn);
                    }

                    if (tick[0] >= RaceCountdown.this.maxLights * 20 + 20) {
                        RaceCountdown.this.onLightsOut();
                        if (RaceCountdown.this.countdownTask != null) {
                            RaceCountdown.this.countdownTask.cancel();
                        }
                        if (RaceCountdown.this.heat.isPushtopass()) {
                            RaceCountdown.this.heat.getPlugin().getPTP().startPTPTask(RaceCountdown.this.heat);
                        }
                    }
                }
            }, 0L, 1L);
        }
    }

    private void onLightOn(int lightNumber) {
        String lights = this.buildLightsDisplay(lightNumber);

        for(Driver driver : this.heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                String subtitle = this.plugin.getTranslation("race_title_prepare_sub", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]);
                TitleHelper.sendThemedTitle(player, lights, subtitle, 0, 30, 5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.5F);
            }
        }

        this.plugin.getDebugManager().logRaceSystem(String.format("[Heat %d] Luz %d acesa", this.heat.getId(), lightNumber));
    }

    private void onLightsOut() {
        this.completed = true;

        for(Driver driver : this.heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                String langCode = this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
                if (this.heat.isLonely()) {
                    String title = this.plugin.getTranslation("quali_title_go", langCode, new String[0]);
                    String subtitle = this.plugin.getTranslation("quali_subtitle_go", langCode, new String[0]);
                    TitleHelper.sendThemedTitle(player, title, subtitle, 5, 20, 10);
                } else {
                    String title = this.plugin.getTranslation("race_title_go", langCode, new String[0]);
                    TitleHelper.sendThemedTitle(player, title, "", 5, 20, 10);
                }

                player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0F, 1.5F);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 2.0F);
            }
        }

        if (this.heat.isLonely()) {
            this.announceLocalizedToAll("quali_countdown_go");
        } else {
            this.announceLocalizedToAll("race_countdown_go");
        }

        this.plugin.getDebugManager().logRaceSystem(String.format("[Heat %d] LIGHTS OUT! %s iniciada!", this.heat.getId(), this.heat.isLonely() ? "Qualificatória" : "Corrida"));
        if (this.onComplete != null) {
            SchedulerHelper.runTask(this.plugin, this.onComplete);
        }

    }

    private String buildLightsDisplay(int lightsOn) {
        StringBuilder display = new StringBuilder();
        if (this.maxLights >= 10) {
            int lightsPerRow = (int)Math.ceil((double)this.maxLights / (double)2.0F);

            for(int i = 1; i <= lightsPerRow; ++i) {
                if (i <= lightsOn) {
                    display.append("§c●");
                } else {
                    display.append("§8●");
                }

                if (i < lightsPerRow) {
                    display.append(" ");
                }
            }

            display.append("\n");

            for(int i = lightsPerRow + 1; i <= this.maxLights; ++i) {
                if (i <= lightsOn) {
                    display.append("§c●");
                } else {
                    display.append("§8●");
                }

                if (i < this.maxLights) {
                    display.append(" ");
                }
            }
        } else {
            for(int i = 1; i <= this.maxLights; ++i) {
                if (i <= lightsOn) {
                    display.append("§c●");
                } else {
                    display.append("§8●");
                }

                if (i < this.maxLights) {
                    display.append(" ");
                }
            }
        }

        return display.toString();
    }

    public void cancel() {
        if (this.countdownTask != null && !this.countdownTask.isCancelled()) {
            this.countdownTask.cancel();
        }

        this.completed = true;
    }

    private void announceLocalizedToAll(String key) {
        for(Driver driver : this.heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                this.plugin.sendMessage(player, key, new String[0]);
            }
        }

    }

    public boolean isCompleted() {
        return this.completed;
    }
}
