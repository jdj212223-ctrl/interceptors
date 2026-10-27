package com.interceptors.registry;

import com.interceptors.Interceptors;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Interceptors.MODID);

    /** Charge of a handheld device (MacBook, iPad, Rugged Laptop), 0..100. Missing means full. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BATTERY = COMPONENTS.registerComponentType("battery",
            b -> b.persistent(Codec.intRange(0, 100)).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {}
}
