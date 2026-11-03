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

    /** Clean stream mode: with the stream overlay on, the game's own HUD bits that would clutter the capture hide. */
    @SubscribeEvent
    public static void cleanHud(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e) {
        if (!BroadcastHud.enabled || !com.interceptors.config.ClientConfig.get(com.interceptors.config.ClientConfig.CLEAN_HUD)) return;
        var id = e.getName();
        if (CLEAN.contains(id)) e.setCanceled(true);
    }

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> CLEAN = java.util.Set.of(
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.ARMOR_LEVEL, net.neoforged.neoforge.client.gui.VanillaGuiLayers.FOOD_LEVEL,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.AIR_LEVEL, net.neoforged.neoforge.client.gui.VanillaGuiLayers.EXPERIENCE_BAR,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.EXPERIENCE_LEVEL, net.neoforged.neoforge.client.gui.VanillaGuiLayers.SELECTED_ITEM_NAME,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, net.neoforged.neoforge.client.gui.VanillaGuiLayers.VEHICLE_HEALTH,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.JUMP_METER);

    /** Hail closes the view right in: thick fog that grows with the hail. */
    @SubscribeEvent
    public static void fog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog e) {
        float hail = StormAmbience.exposedHail();
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
        while (Keys.PROBES.consumeClick()) if (inVehicle) PacketDistributor.sendToServer(new VehicleActionPayload(VehicleActionPayload.PROBES));
        while (Keys.LIGHTS.consumeClick()) if (inVehicle) PacketDistributor.sendToServer(new VehicleActionPayload(VehicleActionPayload.LIGHTS));
        while (Keys.BROADCAST.consumeClick()) BroadcastHud.enabled = !BroadcastHud.enabled;
        while (Keys.PULL_HAIL.consumeClick()) {
            if (mc.player != null && mc.level != null && InjuryState.lodged(mc.player, mc.level.getGameTime())) PacketDistributor.sendToServer(new com.interceptors.network.PullHailPayload());
        }
        StormAmbience.tick(mc);
        InjuryState.tick(mc);
        ObsLink.tick();
        while (Keys.DISPLAY.consumeClick()) {
            if (inVehicle && mc.player.getVehicle() instanceof InterceptorVehicle v) v.seatScreen(mc.player).open();
        }
    }
}
