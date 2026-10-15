/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.annotations.SerializedName
 *  net.boatlabs.utils.debug.log.Blogger
 */
package net.boatlabs.boatlabsmod.config;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.boatlabs.boatlabsmod.ghost.GhostBoatType;
import net.boatlabs.boatlabsmod.region.render.RegionRenderMode;
import net.boatlabs.utils.debug.log.Blogger;

public final class BoatlabsModConfig {
    private static final Path CONFIG_PATH = Path.of("config", "boatlabsmod.json");
    @SerializedName(value="region_render_mode")
    public RegionRenderMode regionRenderMode = RegionRenderMode.NONE;
    @SerializedName(value="use_time_trial_timer")
    public boolean useTimeTrialTimer = true;
    @SerializedName(value="time_trial_timer_y")
    public float timeTrialTimerYPosition = 0.85f;
    @SerializedName(value="time_trial_timer_scale")
    public float timeTrialTimerScale = 1.0f;
    @SerializedName(value="time_trial_timer_background_opacity")
    public float timeTrialTimerBackgroundOpacity = 0.7f;
    @SerializedName(value="render_personal_best_line")
    public boolean renderPersonalBestLine = true;
    @SerializedName(value="personal_best_line_color")
    public int personalBestLineColor = -1610547456;
    @SerializedName(value="render_personal_best_ghost")
    public boolean renderPersonalBestGhost = false;
    @SerializedName(value="ghost_boat_type")
    public GhostBoatType ghostBoatType = GhostBoatType.CHERRY;
    @SerializedName(value="ghost_player_visible")
    public boolean ghostPlayerVisible = true;
    @SerializedName(value="ghost_opacity")
    public float ghostOpacity = 0.5f;
    @SerializedName(value="ghost_playback_offset")
    public int ghostPlaybackOffset = 0;
    @SerializedName(value="ghost_collision")
    public boolean allowGhostCollision = false;

    public void validate() {
        this.timeTrialTimerYPosition = Math.clamp(this.timeTrialTimerYPosition, 0.0f, 1.0f);
        this.timeTrialTimerBackgroundOpacity = Math.clamp(this.timeTrialTimerBackgroundOpacity, 0.0f, 1.0f);
        this.ghostOpacity = Math.clamp(this.ghostOpacity, 0.0f, 1.0f);
        if (this.timeTrialTimerScale <= 0.0f) {
            this.timeTrialTimerScale = 1.0f;
        }
    }

    public void saveGuarded() {
        try {
            this.save();
        }
        catch (Exception e) {
            Blogger.get().error("Could not save config", (Throwable)e);
        }
    }

    public void save() throws IOException {
        if (Files.notExists(CONFIG_PATH, new LinkOption[0])) {
            Files.createFile(CONFIG_PATH, new FileAttribute[0]);
        }
        Gson gson = new Gson();
        Files.writeString(CONFIG_PATH, (CharSequence)gson.toJson((Object)this), new OpenOption[0]);
    }

    public static BoatlabsModConfig load() throws IOException {
        if (Files.notExists(CONFIG_PATH, new LinkOption[0])) {
            return new BoatlabsModConfig();
        }
        Gson gson = new Gson();
        BoatlabsModConfig config = (BoatlabsModConfig)gson.fromJson(Files.readString(CONFIG_PATH), BoatlabsModConfig.class);
        config.validate();
        return config;
    }

    public int ghostOpacityAsArgb() {
        return (int)(this.ghostOpacity * 255.0f) << 24;
    }
}

