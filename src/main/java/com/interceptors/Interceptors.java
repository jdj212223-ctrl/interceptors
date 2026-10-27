package com.interceptors;

import com.interceptors.registry.ModBlocks;
import com.interceptors.registry.ModCreativeTabs;
import com.interceptors.registry.ModEntities;
import com.interceptors.registry.ModItems;
import com.interceptors.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Interceptors.MODID)
public class Interceptors {
    public static final String MODID = "interceptors";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Interceptors(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
