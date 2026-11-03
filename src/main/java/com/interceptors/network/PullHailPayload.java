package com.interceptors.network;

import com.interceptors.Interceptors;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The player pulls the hailstone stuck in them out (a bad idea). */
public record PullHailPayload() implements CustomPacketPayload {
    public static final Type<PullHailPayload> TYPE = new Type<>(Interceptors.id("pull_hail"));
    public static final StreamCodec<ByteBuf, PullHailPayload> CODEC = StreamCodec.unit(new PullHailPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
