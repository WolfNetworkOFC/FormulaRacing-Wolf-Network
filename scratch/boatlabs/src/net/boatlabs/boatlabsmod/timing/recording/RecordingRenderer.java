/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.boatlabs.boatlabsmod.protocol.recording.RecordingBatchPacket$Node
 *  net.minecraft.class_1921
 *  net.minecraft.class_4184
 *  net.minecraft.class_4587
 *  net.minecraft.class_4588
 *  net.minecraft.class_4597
 *  org.joml.Vector3f
 */
package net.boatlabs.boatlabsmod.timing.recording;

import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.protocol.recording.RecordingBatchPacket;
import net.boatlabs.boatlabsmod.timing.recording.Recording;
import net.minecraft.class_1921;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4597;
import org.joml.Vector3f;

public final class RecordingRenderer {
    public static void render(Recording recording, class_4587 matrices, class_4597 vertexConsumers, class_4184 camera) {
        if (recording.nodes().size() < 2) {
            return;
        }
        Vector3f previousPosition = new Vector3f(0.0f, 0.0f, 0.0f);
        matrices.method_22903();
        matrices.method_61958(camera.method_19326().method_22882());
        class_4588 vertexConsumer = vertexConsumers.getBuffer(class_1921.method_34572());
        for (RecordingBatchPacket.Node node : recording.nodes()) {
            Vector3f position = new Vector3f(node.position().x(), node.position().y(), node.position().z());
            vertexConsumer.method_61032(matrices.method_23760(), position).method_39415(BoatlabsMod.config.personalBestLineColor).method_22914(position.x - previousPosition.x, position.y - previousPosition.y, position.z - previousPosition.z);
            previousPosition = position;
        }
        matrices.method_22909();
    }
}

