/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.boatlabs.boatlabsmod.protocol.timing.ComparingTimingPacket
 *  net.boatlabs.boatlabsmod.protocol.timing.RegionPassedPacket
 *  net.boatlabs.utils.debug.log.Blogger
 *  org.jetbrains.annotations.Nullable
 */
package net.boatlabs.boatlabsmod.timing;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.protocol.timing.ComparingTimingPacket;
import net.boatlabs.boatlabsmod.protocol.timing.RegionPassedPacket;
import net.boatlabs.boatlabsmod.region.Region;
import net.boatlabs.boatlabsmod.region.RegionType;
import net.boatlabs.boatlabsmod.timing.TimingSession;
import net.boatlabs.boatlabsmod.timing.recording.Recording;
import net.boatlabs.boatlabsmod.util.TimeUtil;
import net.boatlabs.utils.debug.log.Blogger;
import org.jetbrains.annotations.Nullable;

public final class Timing {
    public final TimingSession timingSession;
    final Recording recording = new Recording();
    public long startTick;
    long startDelta;
    public final List<Long> checkpointTimes = new LinkedList<Long>();
    @Nullable
    public Long endTime;

    public Timing(TimingSession timingSession, long startTick, long startDelta) {
        this.timingSession = timingSession;
        this.startTick = startTick;
        this.startDelta = startDelta;
    }

    public void sendStartPacket(Region region) {
        RegionPassedPacket packet = new RegionPassedPacket(region.regionType().id, region.index(), 0L);
        BoatlabsMod.sendPacket(packet);
    }

    public boolean allCheckpointsPassed() {
        int checkpointCount = (int)this.timingSession.track.getRegions(RegionType.CHECKPOINT).stream().mapToInt(Region::index).distinct().count();
        return checkpointCount == this.checkpointTimes.size();
    }

    public int getNextCheckpointIndex() {
        return this.checkpointTimes.size() + 1;
    }

    public int getCurrentCheckpointIndex() {
        return this.checkpointTimes.size();
    }

    public void passCheckpoint(Region region, long tick, long tickDelta) {
        long checkpointPassTime = (tick - this.startTick) * 50L + tickDelta - this.startDelta;
        this.checkpointTimes.add(checkpointPassTime);
        Blogger.get().info("Checkpoint {} in {}", (Object)this.checkpointTimes.size(), (Object)TimeUtil.formatAsTime(checkpointPassTime));
        RegionPassedPacket packet = new RegionPassedPacket(RegionType.CHECKPOINT.id, region.index(), checkpointPassTime);
        BoatlabsMod.sendPacket(packet);
    }

    public void finish(Region region, long tick, long tickDelta) {
        long lapTime = (tick - this.startTick) * 50L + tickDelta - this.startDelta;
        this.endTime = lapTime;
        Blogger.get().info("Finished in {}", (Object)TimeUtil.formatAsTime(lapTime));
        CompletableFuture.runAsync(() -> {
            this.recording.intoRecordingBatchPackets().forEach(BoatlabsMod::sendPacket);
            this.recording.nodes().clear();
            RegionPassedPacket packet = new RegionPassedPacket(region.regionType().id, region.index(), lapTime);
            BoatlabsMod.sendPacket(packet);
        });
    }

    public long calculateTimeSinceStart(long tickNow) {
        return (tickNow - this.startTick) * 50L;
    }

    @Nullable
    public Long deltaTo(ComparingTimingPacket comparingTiming) {
        if (comparingTiming.lapTime().isPresent() && this.endTime != null) {
            return (Long)comparingTiming.lapTime().get() - this.endTime;
        }
        Long otherCheckpointTime = (Long)comparingTiming.checkpointTimes().get(this.getCurrentCheckpointIndex());
        if (otherCheckpointTime == null) {
            return null;
        }
        long currentCheckpointTime = this.checkpointTimes.getLast();
        return otherCheckpointTime - currentCheckpointTime;
    }
}

