/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.boatlabs.boatlabsmod.protocol.Vec
 *  net.boatlabs.boatlabsmod.protocol.recording.RecordingBatchPacket
 *  net.boatlabs.boatlabsmod.protocol.recording.RecordingBatchPacket$Node
 *  net.minecraft.class_243
 */
package net.boatlabs.boatlabsmod.timing.recording;

import java.util.LinkedList;
import java.util.List;
import net.boatlabs.boatlabsmod.protocol.Vec;
import net.boatlabs.boatlabsmod.protocol.recording.RecordingBatchPacket;
import net.minecraft.class_243;

public record Recording(List<RecordingBatchPacket.Node> nodes) {
    private static final int MAX_CAPACITY = 500000;

    public Recording() {
        this(new LinkedList<RecordingBatchPacket.Node>());
    }

    public void add(class_243 pos, float yaw) {
        if (this.nodes.size() == 500000) {
            return;
        }
        if (this.nodes.isEmpty()) {
            this.nodes.add(new RecordingBatchPacket.Node(0, new Vec((float)pos.field_1352, (float)pos.field_1351, (float)pos.field_1350), yaw));
        } else {
            this.nodes.add(new RecordingBatchPacket.Node(50, new Vec((float)pos.field_1352, (float)pos.field_1351, (float)pos.field_1350), yaw));
        }
    }

    public void add(List<RecordingBatchPacket.Node> nodes) {
        this.nodes.addAll(nodes);
    }

    public List<RecordingBatchPacket> intoRecordingBatchPackets() {
        int batchSize = 200;
        LinkedList<RecordingBatchPacket> packets = new LinkedList<RecordingBatchPacket>();
        LinkedList<RecordingBatchPacket.Node> vecs = new LinkedList<RecordingBatchPacket.Node>();
        int count = 1;
        for (RecordingBatchPacket.Node node : this.nodes) {
            if (count > 200) {
                packets.add(new RecordingBatchPacket(vecs));
                vecs = new LinkedList();
                count = 1;
            }
            vecs.add(node);
            ++count;
        }
        packets.add(new RecordingBatchPacket(vecs));
        return packets;
    }
}

