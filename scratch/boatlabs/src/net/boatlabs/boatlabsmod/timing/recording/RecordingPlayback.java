/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.boatlabs.boatlabsmod.protocol.recording.RecordingBatchPacket$Node
 *  net.minecraft.class_1297
 *  net.minecraft.class_1937
 *  net.minecraft.class_310
 *  net.minecraft.class_638
 */
package net.boatlabs.boatlabsmod.timing.recording;

import java.util.LinkedList;
import java.util.Queue;
import net.boatlabs.boatlabsmod.ghost.GhostBoat;
import net.boatlabs.boatlabsmod.ghost.GhostPlayer;
import net.boatlabs.boatlabsmod.protocol.recording.RecordingBatchPacket;
import net.boatlabs.boatlabsmod.timing.recording.Recording;
import net.boatlabs.boatlabsmod.util.TimeUtil;
import net.minecraft.class_1297;
import net.minecraft.class_1937;
import net.minecraft.class_310;
import net.minecraft.class_638;

public final class RecordingPlayback {
    private final GhostBoat ghostBoat;
    private final GhostPlayer ghostPlayer;
    private final Queue<RecordingBatchPacket.Node> nodes;
    private long ticksUntilNextMovement;

    public RecordingPlayback(Queue<RecordingBatchPacket.Node> nodes) {
        this.nodes = nodes;
        class_638 world = class_310.method_1551().field_1687;
        this.ghostBoat = new GhostBoat((class_1937)world);
        RecordingBatchPacket.Node first = nodes.peek();
        this.ghostBoat.method_5814(first.position().x(), first.position().y(), first.position().z());
        this.ghostBoat.method_36456(first.yaw());
        world.method_53875((class_1297)this.ghostBoat);
        this.ghostPlayer = new GhostPlayer(world);
        this.ghostPlayer.method_36457(20.0f);
        world.method_53875((class_1297)this.ghostPlayer);
        this.ghostPlayer.method_5873((class_1297)this.ghostBoat, true);
    }

    public boolean tick() {
        if (this.nodes.isEmpty()) {
            return true;
        }
        --this.ticksUntilNextMovement;
        if (this.ticksUntilNextMovement > 0L) {
            return false;
        }
        RecordingBatchPacket.Node node = this.nodes.poll();
        this.ghostBoat.method_5814(node.position().x(), node.position().y(), node.position().z());
        this.ghostBoat.method_36456(node.yaw());
        this.ghostPlayer.method_36456(node.yaw());
        this.ghostPlayer.method_5814(node.position().x(), node.position().y() - this.ghostBoat.method_17682(), node.position().z());
        this.ticksUntilNextMovement = TimeUtil.getToTicks(node.millisSinceLastTick());
        if (this.ticksUntilNextMovement == 0L) {
            return this.tick();
        }
        return false;
    }

    public void clear() {
        this.ghostPlayer.method_31472();
        this.ghostBoat.method_31472();
    }

    public static RecordingPlayback ofOffset(Recording recording, int offsetTicks) {
        LinkedList<RecordingBatchPacket.Node> nodes = new LinkedList<RecordingBatchPacket.Node>(recording.nodes());
        if (nodes.isEmpty()) {
            return new RecordingPlayback(nodes);
        }
        if (offsetTicks >= 0) {
            RecordingBatchPacket.Node node;
            for (int ticksLeft = offsetTicks; ticksLeft > 0 && !nodes.isEmpty(); ticksLeft -= TimeUtil.getToTicks(node.millisSinceLastTick())) {
                node = nodes.poll();
            }
            return new RecordingPlayback(nodes);
        }
        RecordingBatchPacket.Node first = nodes.poll();
        RecordingBatchPacket.Node second = nodes.poll();
        if (second == null) {
            return new RecordingPlayback(nodes);
        }
        nodes.offerFirst(new RecordingBatchPacket.Node(-offsetTicks * 50 + second.millisSinceLastTick(), second.position(), second.yaw()));
        nodes.offerFirst(first);
        return new RecordingPlayback(nodes);
    }
}

