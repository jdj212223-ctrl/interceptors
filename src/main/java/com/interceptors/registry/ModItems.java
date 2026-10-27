package com.interceptors.registry;

import com.interceptors.Interceptors;
import com.interceptors.item.VehicleItem;
import com.interceptors.vehicle.VehicleSpec;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Interceptors.MODID);

    public static final Map<String, DeferredItem<VehicleItem>> VEHICLES = new LinkedHashMap<>();

    static {
        for (VehicleSpec spec : VehicleSpec.ALL) {
            VEHICLES.put(spec.id(), ITEMS.register(spec.id(), () -> new VehicleItem(spec, new Item.Properties().stacksTo(1))));
        }
    }

    public static Item vehicle(VehicleSpec spec) {
        return VEHICLES.get(spec.id()).get();
    }

    private ModItems() {}
}
