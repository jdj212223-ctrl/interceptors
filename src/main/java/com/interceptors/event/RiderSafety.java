package com.interceptors.event;

import com.interceptors.Interceptors;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.Part;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Riders stay inside: nothing but their own choice (sneaking) or a destroyed vehicle gets them out, so even an
 * EF5 can't suck them out of a seat. Debris and getting thrown around don't reach them through intact windows.
 */
@EventBusSubscriber(modid = Interceptors.MODID)
public final class RiderSafety {
    private RiderSafety() {}

    @SubscribeEvent
    public static void dismount(EntityMountEvent event) {
        if (!event.isDismounting() || !(event.getEntityBeingMounted() instanceof InterceptorVehicle vehicle)) return;
        Entity rider = event.getEntityMounting();
        boolean wantsOut = rider instanceof Player p && p.isShiftKeyDown();
        if (!vehicle.mayLeave(rider, wantsOut)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().getVehicle() instanceof InterceptorVehicle vehicle) || vehicle.failed(Part.WINDOWS)) return;
        var source = event.getSource();
        boolean debris = source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FLY_INTO_WALL)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypeTags.IS_PROJECTILE) && source.getEntity() == null
                || source.getDirectEntity() != null && source.getDirectEntity().getClass().getName().startsWith("dev.protomanly.pmweather");
        if (debris) event.setCanceled(true);
    }
}
