package com.interceptors.network;

import com.interceptors.Interceptors;
import com.interceptors.vehicle.InterceptorVehicle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.interceptors.item.DeviceItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
        r.playToClient(GlassBurstPayload.TYPE, GlassBurstPayload.CODEC, (p, ctx) -> ctx.enqueueWork(com.interceptors.client.gui.VehicleHud::glassBurst));
        r.playToServer(DeviceUsePayload.TYPE, DeviceUsePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) drain(sp, p.device());
        }));
    }

    /** One tick of battery use for the matching device in either hand. */
    private static void drain(ServerPlayer player, int deviceOrdinal) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof DeviceItem item && item.device().ordinal() == deviceOrdinal) {
                DeviceItem.setBattery(stack, DeviceItem.battery(stack) - 1);
                return;
            }
        }
    }

    private static void say(ServerPlayer player, String key) {
        if (key != null) player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD), true);
    }

    private static void handle(ServerPlayer player, InterceptorVehicle vehicle, int action) {
        switch (action) {
            case VehicleActionPayload.ANCHORS -> say(player, vehicle.toggleAnchors());
            case VehicleActionPayload.REBOOT -> say(player, vehicle.reboot());
            case VehicleActionPayload.HORN -> vehicle.honk();
            case VehicleActionPayload.LIGHTS -> vehicle.toggleLights();
            case VehicleActionPayload.LEXAN -> say(player, vehicle.toggleLexan());
            case VehicleActionPayload.PROBES -> say(player, vehicle.launchProbes());
            default -> {}
        }
    }
}
