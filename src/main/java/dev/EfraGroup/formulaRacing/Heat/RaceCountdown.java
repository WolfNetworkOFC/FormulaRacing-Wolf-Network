package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class RaceCountdown {
    private final FormulaRacing plugin;
    private final Heats heat;
    private final Runnable onComplete;
    private FRTask countdownTask;
    private int lightsOn;
    /** Written by the global countdown task, read by region threads (flagJumpStart) — volatile. */
    private volatile boolean completed;
    private int maxLights;
    private static final int TICKS_PER_LIGHT = 20;
    private static final int LIGHTS_OUT_DELAY = 20;
    /** F1 start: random hold between 5th light and lights out (ticks). */
    private static final int F1_HOLD_MIN_TICKS = 20;
    private static final int F1_HOLD_MAX_TICKS = 40;
    /**
     * Drivers already flagged for jumping the start (one penalty per start).
     * Jump starts are detected from the player's INPUT packets (see
     * JumpStartListener and pollHeldInputs), never from boat physics — lag
     * corrections and rubber-banding must not be able to cause a false penalty.
     * Concurrent: flagged from the input event thread and the countdown thread.
     */
    private final Set<UUID> jumpStarters = ConcurrentHashMap.newKeySet();
    private int lightsOutTick;

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

    private boolean isF1Start() {
        return this.heat.getHeatConfig() != null && this.heat.getHeatConfig().isF1StartEnabled();
    }

    public void start() {
        if (!this.completed) {
            this.lightsOn = 0;
            if (this.heat.isLonely()) {
                this.announceLocalizedToAll("quali_countdown_prepare");
            } else {
                this.announceLocalizedToAll("race_countdown_prepare");
            }

            // F1 start: the hold after the last light is random (like real F1), so
            // drivers cannot time the launch — they must react to lights out.
            this.lightsOutTick = this.maxLights * 20 + LIGHTS_OUT_DELAY;
            if (this.isF1Start()) {
                this.lightsOutTick += ThreadLocalRandom.current().nextInt(F1_HOLD_MIN_TICKS, F1_HOLD_MAX_TICKS + 1);
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

                    if (RaceCountdown.this.isF1Start()) {
                        // While all lights are on (the random hold), keep the display
                        // refreshed so it does not fade out before lights out.
                        if (RaceCountdown.this.lightsOn >= RaceCountdown.this.maxLights && tick[0] % 20 == 0 && tick[0] < RaceCountdown.this.lightsOutTick) {
                            RaceCountdown.this.refreshLightsDisplay();
                        }
                    }

                    if (tick[0] >= RaceCountdown.this.lightsOutTick) {
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

    /**
     * Flags a jump start for a driver whose movement input arrived while the
     * lights were still on. Called by JumpStartListener from PlayerInputEvent —
     * input-based, so network lag can only delay a packet, never fabricate one.
     */
    public void flagJumpStart(Player player) {
        if (this.completed || player == null || !player.isOnline()) {
            return;
        }
        if (this.heat.getHeatState() != HeatState.STARTING || !this.isF1Start()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Driver driver = this.heat.getDriver(uuid);
        if (driver == null || driver.isAiControlled() || !this.heat.getGridManager().isFrozen(uuid)) {
            return;
        }
        // Atomic add: guarantees a single penalty even when the flag comes from
        // both the input listener and a held-input poll.
        if (!this.jumpStarters.add(uuid)) {
            return;
        }
        int penaltySeconds = Math.max(1, this.heat.getHeatConfig().getF1StartPenaltySeconds());
        this.heat.getGridManager().penalizeJumpStart(uuid, penaltySeconds * 20L);
        this.plugin.sendMessage(player, "race_jump_start", new String[]{"{seconds}", String.valueOf(penaltySeconds)});
        this.plugin.getDebugManager().logRaceSystem(String.format("[Heat %d] JUMP START by %s — %ds penalty", this.heat.getId(), player.getName(), penaltySeconds));
    }

    private void refreshLightsDisplay() {
        String lights = this.buildLightsDisplay(this.maxLights);
        for (Driver driver : this.heat.getDrivers().values()) {
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null && player.isOnline()) {
                TitleHelper.sendThemedTitle(player, lights, "", 0, 30, 0);
            }
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

        // From light 3 on, also poll currently-held inputs: a driver holding a
        // movement key since BEFORE the countdown never fires an input CHANGE
        // event. The two-light grace lets players lift off muscle-memory W from
        // being teleported onto the grid.
        if (this.isF1Start() && lightNumber >= 3) {
            this.pollHeldInputs();
        }

        this.plugin.getDebugManager().logRaceSystem(String.format("[Heat %d] Luz %d acesa", this.heat.getId(), lightNumber));
    }

    /** Input-based (never physics): flags frozen drivers currently holding throttle keys. */
    private void pollHeldInputs() {
        for (Driver driver : this.heat.getDrivers().values()) {
            if (driver.isAiControlled()) {
                continue;
            }
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player == null || !player.isOnline()) {
                continue;
            }
            SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                Input input = player.getCurrentInput();
                if (input != null && (input.isForward() || input.isBackward() || input.isJump() || input.isSprint())) {
                    this.flagJumpStart(player);
                }
            });
        }
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
