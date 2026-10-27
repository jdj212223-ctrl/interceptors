package com.interceptors.network;

import com.interceptors.Interceptors;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A vehicle key the rider pressed. */
public record VehicleActionPayload(int action) implements CustomPacketPayload {
    public static final int ANCHORS = 0, HORN = 1;
    public static final Type<VehicleActionPayload> TYPE = new Type<>(Interceptors.id("vehicle_action"));
    public static final StreamCodec<ByteBuf, VehicleActionPayload> CODEC = ByteBufCodecs.VAR_INT.map(VehicleActionPayload::new, VehicleActionPayload::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
