package com.interceptors;

import com.interceptors.config.InterceptorsConfig;
import com.interceptors.registry.ModBlocks;
import com.interceptors.registry.ModCreativeTabs;
import com.interceptors.registry.ModDataComponents;
import com.interceptors.registry.ModEntities;
import com.interceptors.registry.ModItems;
import com.interceptors.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Interceptors.MODID)
public class Interceptors {
    public static final String MODID = "interceptors";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Interceptors(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, InterceptorsConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, com.interceptors.config.ClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
