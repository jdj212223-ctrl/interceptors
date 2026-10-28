package com.interceptors.network;

import com.interceptors.Interceptors;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Tells a rider their windows just blew in on them, so their screen flashes red. */
public record GlassBurstPayload() implements CustomPacketPayload {
    public static final Type<GlassBurstPayload> TYPE = new Type<>(Interceptors.id("glass_burst"));
    public static final StreamCodec<ByteBuf, GlassBurstPayload> CODEC = StreamCodec.unit(new GlassBurstPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
