package com.interceptors.client;

import com.interceptors.Interceptors;
import com.interceptors.client.gui.BroadcastHud;
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

    /** How long the anchor key has been held while the computer is frozen. */
    private static int rebootHold;
    public static final int REBOOT_HOLD_TICKS = 40;

    public static float rebootHoldProgress() {
        return Math.min(1, rebootHold / (float) REBOOT_HOLD_TICKS);
    }

    /** Hail closes the view right in: thick fog that grows with the hail. */
    @SubscribeEvent
    public static void fog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog e) {
        float hail = StormAmbience.hailLevel();
        if (hail < 0.05f) return;
        float far = net.minecraft.util.Mth.lerp(Math.min(1, hail * 1.4f), e.getFarPlaneDistance(), 18);
        e.setFarPlaneDistance(Math.min(e.getFarPlaneDistance(), far));
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), far * 0.1f));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        boolean inVehicle = mc.player != null && mc.player.getVehicle() instanceof InterceptorVehicle;
        // Frozen computer: hold the anchor key for two seconds to reboot it.
        if (inVehicle && mc.player.getVehicle() instanceof InterceptorVehicle v && v.isFrozen() && v.getReboot() == 0 && Keys.ANCHORS.isDown()) {
            if (++rebootHold == REBOOT_HOLD_TICKS) PacketDistributor.sendToServer(new VehicleActionPayload(VehicleActionPayload.REBOOT));
        } else {
            rebootHold = 0;
        }
        while (Keys.ANCHORS.consumeClick()) if (inVehicle) PacketDistributor.sendToServer(new VehicleActionPayload(VehicleActionPayload.ANCHORS));
        while (Keys.HORN.consumeClick()) if (inVehicle) PacketDistributor.sendToServer(new VehicleActionPayload(VehicleActionPayload.HORN));
        while (Keys.LEXAN.consumeClick()) if (inVehicle) PacketDistributor.sendToServer(new VehicleActionPayload(VehicleActionPayload.LEXAN));
        while (Keys.LIGHTS.consumeClick()) if (inVehicle) PacketDistributor.sendToServer(new VehicleActionPayload(VehicleActionPayload.LIGHTS));
        while (Keys.BROADCAST.consumeClick()) BroadcastHud.enabled = !BroadcastHud.enabled;
        StormAmbience.tick(mc);
        while (Keys.DISPLAY.consumeClick()) {
            if (inVehicle && mc.player.getVehicle() instanceof InterceptorVehicle v) v.seatScreen(mc.player).open();
        }
    }
}
