package com.interceptors.network;

import com.interceptors.Interceptors;
import com.interceptors.vehicle.InterceptorVehicle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Interceptors.MODID)
public final class ModNetwork {
    private ModNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(VehicleActionPayload.TYPE, VehicleActionPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.getVehicle() instanceof InterceptorVehicle v) handle(sp, v, p.action());
        }));
    }

    private static void handle(ServerPlayer player, InterceptorVehicle vehicle, int action) {
        switch (action) {
            case VehicleActionPayload.ANCHORS -> {
                if (!vehicle.toggleAnchors()) player.displayClientMessage(Component.translatable("message.interceptors.stop_first"), true);
            }
            case VehicleActionPayload.HORN -> vehicle.honk();
            default -> {}
        }
    }
}
