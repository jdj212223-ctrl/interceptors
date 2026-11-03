package com.interceptors.client;

import com.interceptors.Interceptors;
import com.interceptors.client.gui.BroadcastHud;
import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.client.gui.HailOverlay;
import com.interceptors.client.gui.DropAnchorHud;
import com.interceptors.client.gui.VehicleHud;
import com.interceptors.client.render.VehicleRenderer;
import com.interceptors.compat.Storms;
import com.interceptors.device.Device;
import com.interceptors.registry.ModEntities;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = Interceptors.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        Storms.clientHandler = () -> GameBusClientEvents.weatherHandler;
        Device.opener = device -> Minecraft.getInstance().setScreen(new DeviceScreen(device));
        com.interceptors.vehicle.FlightControls.ascend = () -> Minecraft.getInstance().options.keyJump.isDown();
        com.interceptors.vehicle.FlightControls.descend = Keys.DESCEND::isDown;
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(com.interceptors.client.render.CutLayer.LAYER, com.interceptors.client.render.CutLayer::layer);
        e.registerLayerDefinition(com.interceptors.client.render.InjuryLayer.LAYER, com.interceptors.client.render.InjuryLayer::layer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
                renderer.addLayer(new com.interceptors.client.render.CutLayer(renderer, e.getEntityModels()));
                renderer.addLayer(new com.interceptors.client.render.InjuryLayer(renderer, e.getEntityModels()));
            }
        }
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        ModEntities.VEHICLES.values().forEach(type -> e.registerEntityRenderer(type.get(), VehicleRenderer::new));
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(Keys.ANCHORS);
        e.register(Keys.HORN);
        e.register(Keys.DISPLAY);
        e.register(Keys.LIGHTS);
        e.register(Keys.BROADCAST);
        e.register(Keys.LEXAN);
        e.register(Keys.PROBES);
        e.register(Keys.DESCEND);
        e.register(Keys.PULL_HAIL);
    }

    @SubscribeEvent
    public static void gui(RegisterGuiLayersEvent e) {
        e.registerAboveAll(Interceptors.id("hail"), HailOverlay::render);
        e.registerAboveAll(Interceptors.id("vehicle_hud"), VehicleHud::render);
        e.registerAboveAll(Interceptors.id("broadcast"), BroadcastHud::render);
        e.registerAboveAll(Interceptors.id("drop_anchor"), DropAnchorHud::render);
        e.registerAboveAll(Interceptors.id("injury"), com.interceptors.client.gui.InjuryHud::render);
    }
}
