package com.interceptors.network;

import com.interceptors.Interceptors;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A player's hail injury, for everyone who can see them: the stuck stone (part, or -1) and the bleeding wound. */
public record InjuryPayload(int entityId, int lodgedPart, int lodgedTicks, int woundPart, int bleedTicks) implements CustomPacketPayload {
    public static final Type<InjuryPayload> TYPE = new Type<>(Interceptors.id("injury"));
    public static final StreamCodec<ByteBuf, InjuryPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, InjuryPayload::entityId, ByteBufCodecs.INT, InjuryPayload::lodgedPart,
            ByteBufCodecs.VAR_INT, InjuryPayload::lodgedTicks, ByteBufCodecs.VAR_INT, InjuryPayload::woundPart,
            ByteBufCodecs.VAR_INT, InjuryPayload::bleedTicks, InjuryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
