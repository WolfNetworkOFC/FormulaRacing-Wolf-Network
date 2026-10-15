/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  net.boatlabs.boatlabsmod.protocol.BoatlabsByteBuf
 *  net.boatlabs.boatlabsmod.protocol.BoatlabsPacket
 *  net.fabricmc.fabric.api.networking.v1.PacketByteBufs
 *  net.minecraft.class_2540
 *  net.minecraft.class_2960
 *  net.minecraft.class_8710
 *  net.minecraft.class_8710$class_9154
 *  net.minecraft.class_9139
 */
package net.boatlabs.boatlabsmod.protocol;

import io.netty.buffer.ByteBuf;
import net.boatlabs.boatlabsmod.protocol.BoatlabsByteBuf;
import net.boatlabs.boatlabsmod.protocol.BoatlabsPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;

public final class BoatlabsPayload
implements class_8710 {
    public static final class_9139<class_2540, BoatlabsPayload> CODEC = class_8710.method_56484(BoatlabsPayload::write, BoatlabsPayload::new);
    public static final class_8710.class_9154<BoatlabsPayload> ID = new class_8710.class_9154(class_2960.method_60655((String)"boatlabsmod", (String)"protocol"));
    public final ByteBuf byteBuf;

    public BoatlabsPayload(class_2540 byteBuf) {
        this.byteBuf = byteBuf.copy();
        byteBuf.method_52988(byteBuf.writerIndex());
    }

    public <T> BoatlabsPayload(BoatlabsPacket<T> packet) {
        class_2540 packetByteBuf = PacketByteBufs.create();
        this.byteBuf = packetByteBuf;
        packetByteBuf.method_10814(packet.identifier());
        BoatlabsByteBuf boatlabsByteBuf = (BoatlabsByteBuf)packet.codec().encoder().apply(packet);
        packetByteBuf.method_52975(boatlabsByteBuf.byteBuf());
        boatlabsByteBuf.discard();
    }

    void write(class_2540 buf) {
        buf.method_52975(this.byteBuf);
    }

    public class_8710.class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}

