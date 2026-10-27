package com.interceptors.network;

import com.interceptors.Interceptors;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent every few seconds while a handheld device's screen is open, so the server can drain its battery. */
public record DeviceUsePayload(int device) implements CustomPacketPayload {
    public static final Type<DeviceUsePayload> TYPE = new Type<>(Interceptors.id("device_use"));
    public static final StreamCodec<ByteBuf, DeviceUsePayload> CODEC = ByteBufCodecs.VAR_INT.map(DeviceUsePayload::new, DeviceUsePayload::device);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
