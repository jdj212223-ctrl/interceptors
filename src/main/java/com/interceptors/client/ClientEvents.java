package com.interceptors.client;

import com.interceptors.Interceptors;
import com.interceptors.device.Device;
import com.interceptors.network.VehicleActionPayload;
import com.interceptors.vehicle.InterceptorVehicle;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Interceptors.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        boolean inVehicle = mc.player != null && mc.player.getVehicle() instanceof InterceptorVehicle;
        while (Keys.ANCHORS.consumeClick()) if (inVehicle) PacketDistributor.sendToServer(new VehicleActionPayload(VehicleActionPayload.ANCHORS));
        while (Keys.HORN.consumeClick()) if (inVehicle) PacketDistributor.sendToServer(new VehicleActionPayload(VehicleActionPayload.HORN));
        while (Keys.DISPLAY.consumeClick()) if (inVehicle) Device.CAR_DISPLAY.open();
    }
}
