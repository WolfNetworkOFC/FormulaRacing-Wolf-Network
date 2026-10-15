/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.boatlabs.boatlabsmod.protocol.timing.ComparingTimingPacket
 *  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
 *  net.minecraft.class_10209
 *  net.minecraft.class_10255
 *  net.minecraft.class_1297
 *  net.minecraft.class_243
 *  net.minecraft.class_310
 *  net.minecraft.class_332
 */
package net.boatlabs.boatlabsmod.timing;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.protocol.timing.ComparingTimingPacket;
import net.boatlabs.boatlabsmod.region.Region;
import net.boatlabs.boatlabsmod.region.RegionType;
import net.boatlabs.boatlabsmod.region.render.RegionRenderMode;
import net.boatlabs.boatlabsmod.timing.Timing;
import net.boatlabs.boatlabsmod.timing.TimingHud;
import net.boatlabs.boatlabsmod.timing.recording.Recording;
import net.boatlabs.boatlabsmod.timing.recording.RecordingPlayback;
import net.boatlabs.boatlabsmod.timing.recording.RecordingRenderer;
import net.boatlabs.boatlabsmod.track.Track;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.class_10209;
import net.minecraft.class_10255;
import net.minecraft.class_1297;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_332;

public final class TimingSession {
    final class_310 client;
    public final Track track;
    public Timing currentTiming;
    public Timing previousTiming;
    public ComparingTimingPacket comparingTiming;
    public Recording recording = new Recording();
    public RecordingPlayback recordingPlayback;
    class_243 previousPosition = class_243.field_1353;
    long tick;

    public TimingSession(class_310 client, Track track) {
        this.client = client;
        this.track = track;
    }

    public void tick() {
        class_1297 class_12972;
        ++this.tick;
        if (this.recordingPlayback != null && this.recordingPlayback.tick()) {
            this.recordingPlayback.clear();
            this.recordingPlayback = null;
        }
        if (!((class_12972 = this.client.field_1724.method_5854()) instanceof class_10255)) {
            return;
        }
        class_10255 boat = (class_10255)class_12972;
        class_243 pos = boat.method_19538();
        if (this.currentTiming != null) {
            this.currentTiming.recording.add(pos, boat.method_36454());
        }
        if (!pos.equals((Object)this.previousPosition)) {
            this.handleMovement(this.previousPosition, pos);
        }
        this.previousPosition = pos;
    }

    public void renderRegions(WorldRenderContext ctx, RegionRenderMode regionRenderMode) {
        class_10209.method_64146().method_15396("track_regions");
        switch (regionRenderMode) {
            case NONE: {
                break;
            }
            case NEXT: {
                if (this.currentTiming == null) {
                    for (Region region2 : this.track.getRegions(RegionType.START)) {
                        region2.render(ctx);
                    }
                } else if (this.currentTiming.allCheckpointsPassed()) {
                    if (this.track.getRegions(RegionType.END).isEmpty()) {
                        for (Region region3 : this.track.getRegions(RegionType.START)) {
                            region3.render(ctx);
                        }
                    } else {
                        for (Region region4 : this.track.getRegions(RegionType.END)) {
                            region4.render(ctx);
                        }
                    }
                } else {
                    int nextCheckpointNumber = this.currentTiming.getNextCheckpointIndex();
                    for (Region region5 : this.track.getRegions(RegionType.CHECKPOINT)) {
                        if (region5.index() != nextCheckpointNumber) continue;
                        region5.render(ctx);
                    }
                }
                break;
            }
            case ALL: {
                this.track.forEachRegion(region -> region.render(ctx));
            }
        }
        class_10209.method_64146().method_15407();
    }

    public void renderRecording(WorldRenderContext ctx) {
        if (BoatlabsMod.config.renderPersonalBestLine) {
            class_10209.method_64146().method_15396("pb_line");
            RecordingRenderer.render(this.recording, ctx.matrixStack(), ctx.consumers(), ctx.camera());
            class_10209.method_64146().method_15407();
        }
    }

    public void renderTimingHud(class_332 ctx) {
        if (this.currentTiming == null || !BoatlabsMod.config.useTimeTrialTimer) {
            return;
        }
        class_10209.method_64146().method_15396("timing_element");
        TimingHud.render(ctx);
        class_10209.method_64146().method_15407();
    }

    void onStart(Region region) {
        this.currentTiming.sendStartPacket(region);
        this.beginRecordingPlayback();
    }

    public void stopRecordingPlayback() {
        if (this.recordingPlayback != null) {
            this.recordingPlayback.clear();
            this.recordingPlayback = null;
        }
    }

    public void stopTimer() {
        this.currentTiming = null;
        this.previousTiming = null;
        this.stopRecordingPlayback();
    }

    public void beginRecordingPlayback() {
        if (this.recording.nodes().isEmpty() || !BoatlabsMod.config.renderPersonalBestGhost) {
            return;
        }
        this.recordingPlayback = RecordingPlayback.ofOffset(this.recording, BoatlabsMod.config.ghostPlaybackOffset);
    }

    void handleMovement(class_243 from, class_243 to) {
        class_1297 class_12972 = this.client.field_1724.method_5854();
        if (!(class_12972 instanceof class_10255)) {
            return;
        }
        class_10255 boat = (class_10255)class_12972;
        if (this.currentTiming == null) {
            if (this.track.timingAllowed) {
                for (Region region : this.track.getRegions(RegionType.START)) {
                    if (!region.contains(to)) continue;
                    long tickDelta = TimingSession.calculatePreciseTickOffsetMillisDeltaRegionPassed(region, from, to);
                    this.currentTiming = new Timing(this, this.tick, tickDelta);
                    this.onStart(region);
                    return;
                }
            }
            return;
        }
        if (this.currentTiming.allCheckpointsPassed()) {
            if (this.track.getRegions(RegionType.END).isEmpty()) {
                for (Region region : this.track.getRegions(RegionType.START)) {
                    if (!region.contains(to)) continue;
                    this.stopRecordingPlayback();
                    long tickDelta = TimingSession.calculatePreciseTickOffsetMillisDeltaRegionPassed(region, from, to);
                    this.currentTiming.finish(region, this.tick, tickDelta);
                    this.previousTiming = this.currentTiming;
                    this.currentTiming = new Timing(this, this.tick, tickDelta);
                    this.currentTiming.recording.add(boat.method_19538(), boat.method_36454());
                    this.beginRecordingPlayback();
                    return;
                }
            } else {
                for (Region region : this.track.getRegions(RegionType.END)) {
                    if (!region.contains(to)) continue;
                    long tickOffset = TimingSession.calculatePreciseTickOffsetMillisDeltaRegionPassed(region, from, to);
                    this.currentTiming.finish(region, this.tick, tickOffset);
                    this.previousTiming = null;
                    this.currentTiming = null;
                    this.stopRecordingPlayback();
                    return;
                }
            }
        }
        for (Region region : this.track.getRegions(RegionType.CHECKPOINT)) {
            if (this.currentTiming.getNextCheckpointIndex() != region.index() || !region.contains(to)) continue;
            long tickOffset = TimingSession.calculatePreciseTickOffsetMillisDeltaRegionPassed(region, from, to);
            this.currentTiming.passCheckpoint(region, this.tick, tickOffset);
            return;
        }
    }

    static long calculatePreciseTickOffsetMillisDeltaRegionPassed(Region region, class_243 outside, class_243 inside) {
        int iterations = 8;
        long insideMillis = 50L;
        long outsideMillis = 0L;
        for (int i = 0; i < 8; ++i) {
            class_243 midLocation = inside.method_1019(outside).method_1021(0.5);
            long midTime = (insideMillis + outsideMillis) / 2L;
            if (region.contains(midLocation)) {
                inside = midLocation;
                insideMillis = midTime;
                continue;
            }
            outside = midLocation;
            outsideMillis = midTime;
        }
        return (insideMillis + outsideMillis) / 2L;
    }
}

