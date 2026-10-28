package com.interceptors.network;

import com.interceptors.Interceptors;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A rider was cut by flying glass: show the cut on their head for {@code ticks}, to everyone who can see them. */
public record CutPayload(int entityId, int ticks) implements CustomPacketPayload {
    public static final Type<CutPayload> TYPE = new Type<>(Interceptors.id("cut"));
    public static final StreamCodec<ByteBuf, CutPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CutPayload::entityId, ByteBufCodecs.VAR_INT, CutPayload::ticks, CutPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
