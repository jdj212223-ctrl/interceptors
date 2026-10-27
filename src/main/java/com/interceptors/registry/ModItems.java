package com.interceptors.registry;

import com.interceptors.Interceptors;
import com.interceptors.device.Device;
import com.interceptors.item.DeviceItem;
import com.interceptors.item.VehicleItem;
import com.interceptors.vehicle.VehicleSpec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Interceptors.MODID);

    public static final Map<String, DeferredItem<VehicleItem>> VEHICLES = new LinkedHashMap<>();

    public static final DeferredItem<BlockItem> COMPUTER = ITEMS.registerSimpleBlockItem(ModBlocks.COMPUTER);
    public static final DeferredItem<BlockItem> LAPTOP = ITEMS.registerSimpleBlockItem(ModBlocks.LAPTOP);
    public static final DeferredItem<DeviceItem> MACBOOK = ITEMS.register("macbook", () -> new DeviceItem(Device.MACBOOK, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> REPAIR_KIT = ITEMS.register("repair_kit", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<DeviceItem> IPAD = ITEMS.register("ipad", () -> new DeviceItem(Device.IPAD, new Item.Properties().stacksTo(1)));

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
