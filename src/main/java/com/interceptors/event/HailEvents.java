package com.interceptors.event;

import com.interceptors.Interceptors;
import com.interceptors.compat.Storms;
import com.interceptors.vehicle.InterceptorVehicle;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Baseball-size hail hurts anyone caught out under open sky. Get in a vehicle (or under a roof). */
@EventBusSubscriber(modid = Interceptors.MODID)
public final class HailEvents {
    public static final ResourceKey<DamageType> HAIL = ResourceKey.create(Registries.DAMAGE_TYPE, Interceptors.id("hail"));
    /** Hail intensity (0..1) from which stones are big enough to hurt. */
    public static final float HURTS_ABOVE = 0.5f;

    private HailEvents() {}

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 40 != 0 || player.isCreative() || player.isSpectator()) return;
        if (player.getVehicle() instanceof InterceptorVehicle) return;
        if (!player.level().canSeeSky(player.blockPosition().above())) return;
        float hail = Storms.hailAt(player.level(), player.getEyePosition());
        if (hail < HURTS_ABOVE) return;
        player.hurt(player.level().damageSources().source(HAIL), 1 + hail * 2);
    }
}
