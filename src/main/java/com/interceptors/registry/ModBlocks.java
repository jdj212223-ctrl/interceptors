package com.interceptors.registry;

import com.interceptors.Interceptors;
import com.interceptors.block.DeviceBlock;
import com.interceptors.device.Device;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Interceptors.MODID);

    public static final DeferredBlock<DeviceBlock> COMPUTER = BLOCKS.register("computer",
            () -> new DeviceBlock(Device.COMPUTER, Block.box(2, 0, 2, 14, 14, 14), props()));
    public static final DeferredBlock<DeviceBlock> LAPTOP = BLOCKS.register("laptop",
            () -> new DeviceBlock(Device.LAPTOP, Block.box(2, 0, 2, 14, 9, 14), props()));

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 4);
    }

    private ModBlocks() {}
}
