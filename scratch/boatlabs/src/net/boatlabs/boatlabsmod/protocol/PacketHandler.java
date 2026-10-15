/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.boatlabs.boatlabsmod.protocol.BoatlabsByteBuf
 *  net.boatlabs.boatlabsmod.protocol.acknowledgement.AcknowledgementPacket
 *  net.boatlabs.boatlabsmod.protocol.ghost.GhostCollisionPacket
 *  net.boatlabs.boatlabsmod.protocol.recording.RecordingBatchPacket
 *  net.boatlabs.boatlabsmod.protocol.timing.ComparingTimingPacket
 *  net.boatlabs.boatlabsmod.protocol.track.TrackPacket
 *  net.boatlabs.utils.debug.log.Blogger
 *  net.minecraft.class_310
 */
package net.boatlabs.boatlabsmod.protocol;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.protocol.BoatlabsByteBuf;
import net.boatlabs.boatlabsmod.protocol.BoatlabsPayload;
import net.boatlabs.boatlabsmod.protocol.acknowledgement.AcknowledgementPacket;
import net.boatlabs.boatlabsmod.protocol.ghost.GhostCollisionPacket;
import net.boatlabs.boatlabsmod.protocol.recording.RecordingBatchPacket;
import net.boatlabs.boatlabsmod.protocol.timing.ComparingTimingPacket;
import net.boatlabs.boatlabsmod.protocol.track.TrackPacket;
import net.boatlabs.boatlabsmod.timing.TimingSession;
import net.boatlabs.boatlabsmod.timing.recording.Recording;
import net.boatlabs.boatlabsmod.track.Track;
import net.boatlabs.utils.debug.log.Blogger;
import net.minecraft.class_310;

public final class PacketHandler {
    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static void onPacketReceived(BoatlabsPayload payload) {
        BoatlabsByteBuf byteBuf = new BoatlabsByteBuf(payload.byteBuf);
        try {
            String identifier;
            switch (identifier = byteBuf.readVarString()) {
                case "acknowledgement:acknowledgement": {
                    PacketHandler.handleAcknowledgementPacket(new AcknowledgementPacket(byteBuf));
                    return;
                }
                case "track:track": {
                    PacketHandler.handleTrackPacket(new TrackPacket(byteBuf));
                    return;
                }
                case "timing:cancel_timing": {
                    PacketHandler.handleStopTimePacket();
                    return;
                }
                case "timing:clear_session": {
                    PacketHandler.handleClearSessionPacket();
                    return;
                }
                case "timing:comparing_timing": {
                    PacketHandler.handleComparingCheckpointsPacket(new ComparingTimingPacket(byteBuf));
                    return;
                }
                case "track:remove": {
                    PacketHandler.handleTrackRemovePacket();
                    return;
                }
                case "recording:clear": {
                    PacketHandler.handleRecordingClearPacket();
                    return;
                }
                case "recording:batch": {
                    PacketHandler.handleRecordingBatchPacket(new RecordingBatchPacket(byteBuf));
                    return;
                }
                case "ghost:collision": {
                    PacketHandler.handleGhostCollisionPacket(new GhostCollisionPacket(byteBuf));
                    return;
                }
                default: {
                    Blogger.get().warn("Unknown packet {}", (Object)identifier);
                    return;
                }
            }
        }
        finally {
            byteBuf.discard();
        }
    }

    static void handleAcknowledgementPacket(AcknowledgementPacket packet) {
        Blogger.get().info("Server has acknowledged our existence, yippee! ({})", (Object)packet.version());
    }

    static void handleTrackPacket(TrackPacket trackPacket) {
        PacketHandler.handleClearSessionPacket();
        Track track = new Track(trackPacket);
        BoatlabsMod.timingSession = new TimingSession(class_310.method_1551(), track);
    }

    static void handleStopTimePacket() {
        if (BoatlabsMod.timingSession != null) {
            BoatlabsMod.timingSession.stopTimer();
        }
    }

    static void handleClearSessionPacket() {
        if (BoatlabsMod.timingSession != null) {
            BoatlabsMod.timingSession.stopRecordingPlayback();
            BoatlabsMod.timingSession = null;
        }
    }

    static void handleComparingCheckpointsPacket(ComparingTimingPacket packet) {
        if (BoatlabsMod.timingSession != null) {
            BoatlabsMod.timingSession.comparingTiming = packet;
        }
    }

    static void handleTrackRemovePacket() {
        BoatlabsMod.timingSession = null;
    }

    static void handleRecordingClearPacket() {
        if (BoatlabsMod.timingSession != null) {
            BoatlabsMod.timingSession.recording = new Recording();
            BoatlabsMod.timingSession.stopRecordingPlayback();
        }
    }

    static void handleRecordingBatchPacket(RecordingBatchPacket packet) {
        if (BoatlabsMod.timingSession != null) {
            BoatlabsMod.timingSession.recording.add(packet.nodes());
            if (BoatlabsMod.timingSession.currentTiming != null) {
                BoatlabsMod.timingSession.stopRecordingPlayback();
                BoatlabsMod.timingSession.beginRecordingPlayback();
            }
        }
    }

    static void handleGhostCollisionPacket(GhostCollisionPacket packet) {
        BoatlabsMod.ghostCollisionAllowed = packet.enabled();
        System.out.println(packet.enabled());
    }
}

