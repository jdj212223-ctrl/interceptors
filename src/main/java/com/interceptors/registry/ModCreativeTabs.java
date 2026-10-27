package com.interceptors.registry;

import com.interceptors.Interceptors;
import com.interceptors.vehicle.VehicleSpec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Interceptors.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.interceptors"))
            .icon(() -> new ItemStack(ModItems.vehicle(VehicleSpec.GAMING_VAN)))
            .displayItems((params, out) -> ModItems.ITEMS.getEntries().forEach(item -> out.accept(item.get())))
            .build());

    private ModCreativeTabs() {}
}
