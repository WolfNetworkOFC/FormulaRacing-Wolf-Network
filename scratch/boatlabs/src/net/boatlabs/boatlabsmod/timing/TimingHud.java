/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_2960
 *  net.minecraft.class_310
 *  net.minecraft.class_332
 */
package net.boatlabs.boatlabsmod.timing;

import java.util.Objects;
import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.timing.Timing;
import net.boatlabs.boatlabsmod.util.TimeUtil;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;

public final class TimingHud {
    public static final class_2960 ID = class_2960.method_60655((String)"boatlabsmod", (String)"timing_hud");

    public static void render(class_332 ctx) {
        int deltaColor;
        Object formattedDelta;
        int opacity;
        int gray = opacity = (int)(BoatlabsMod.config.timeTrialTimerBackgroundOpacity * 255.0f) << 24;
        int green = 65280 + opacity;
        int yellow = 0xBBBB00 + opacity;
        int red = 0xFF0000 + opacity;
        int padding = class_310.method_1551().field_1772.method_1727(":");
        Objects.requireNonNull(class_310.method_1551().field_1772);
        int textHeight = 9;
        long currentTick = BoatlabsMod.timingSession.tick;
        Timing currentTiming = BoatlabsMod.timingSession.currentTiming;
        long timingTime = currentTiming.calculateTimeSinceStart(currentTick);
        String currentTime = TimeUtil.formatAsTime2dp(TimeUtil.roundToTick(timingTime));
        Long delta = null;
        if (BoatlabsMod.timingSession.comparingTiming != null) {
            if (currentTiming.getNextCheckpointIndex() != 1) {
                delta = currentTiming.deltaTo(BoatlabsMod.timingSession.comparingTiming);
            } else {
                Timing previousTiming = BoatlabsMod.timingSession.previousTiming;
                if (previousTiming != null && previousTiming.endTime != null) {
                    delta = previousTiming.deltaTo(BoatlabsMod.timingSession.comparingTiming);
                }
            }
        }
        if (delta == null) {
            formattedDelta = "";
            deltaColor = gray;
        } else if (delta > 0L) {
            formattedDelta = "-" + TimeUtil.formatAsTime(delta);
            deltaColor = green;
        } else if (delta < 0L) {
            formattedDelta = "+" + TimeUtil.formatAsTime(-delta.longValue());
            deltaColor = red;
        } else {
            formattedDelta = "=" + TimeUtil.formatAsTime(0L);
            deltaColor = yellow;
        }
        int scaledWidth = ctx.method_51421();
        int scaledHeight = ctx.method_51443();
        int centerX = scaledWidth / 2;
        int centerY = (int)((float)scaledHeight * BoatlabsMod.config.timeTrialTimerYPosition);
        int currentTimeWidth = class_310.method_1551().field_1772.method_1727(currentTime);
        ctx.method_51448().method_22903();
        ctx.method_51448().method_46416((float)centerX, (float)centerY, 0.0f);
        ctx.method_51448().method_22905(BoatlabsMod.config.timeTrialTimerScale, BoatlabsMod.config.timeTrialTimerScale, 1.0f);
        int halfHeight = textHeight / 2;
        if (delta == null) {
            int halfWidth = currentTimeWidth / 2;
            ctx.method_25294(-halfWidth - padding, -halfHeight - padding, halfWidth + padding, halfHeight + padding, gray);
            ctx.method_51433(class_310.method_1551().field_1772, currentTime, -halfWidth, -halfHeight, 0xFFFFFF, false);
        } else {
            int deltaWidth = class_310.method_1551().field_1772.method_1727((String)formattedDelta);
            ctx.method_25294(-currentTimeWidth - padding * 2, -halfHeight - padding, 0, halfHeight + padding, gray);
            ctx.method_25294(0, -(textHeight / 2) - padding, deltaWidth + padding * 2, halfHeight + padding, deltaColor);
            ctx.method_51433(class_310.method_1551().field_1772, currentTime, -currentTimeWidth - padding, -halfHeight, 0xFFFFFF, false);
            ctx.method_51433(class_310.method_1551().field_1772, (String)formattedDelta, padding, -halfHeight, 0xFFFFFF, false);
        }
        ctx.method_51448().method_22909();
    }
}

