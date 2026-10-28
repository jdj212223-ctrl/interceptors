package com.interceptors.registry;

import com.interceptors.Interceptors;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.StormChopper;
import com.interceptors.vehicle.VehicleSpec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Interceptors.MODID);

    /** One entity type per vehicle, keyed by its id. */
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<InterceptorVehicle>>> VEHICLES = new LinkedHashMap<>();

    static {
        for (VehicleSpec spec : VehicleSpec.ALL) {
            VEHICLES.put(spec.id(), ENTITIES.register(spec.id(),
                    () -> EntityType.Builder.<InterceptorVehicle>of((type, level) -> spec.flies()
                                    ? new StormChopper(type, level, spec) : new InterceptorVehicle(type, level, spec), MobCategory.MISC)
                            .sized(spec.width(), spec.height())
                            // The chopper is seen from far off, so its camera feed reaches the ground team.
                            .clientTrackingRange(spec.flies() ? 32 : 10)
                            .build(spec.id())));
        }
    }

    public static EntityType<InterceptorVehicle> vehicle(VehicleSpec spec) {
        return VEHICLES.get(spec.id()).get();
    }

    private ModEntities() {}
}
