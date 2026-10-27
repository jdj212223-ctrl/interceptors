package com.interceptors.item;

import com.interceptors.device.Device;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A handheld screen (iPad, MacBook): right-click to open it anywhere, even riding shotgun. */
public class DeviceItem extends Item {
    private final Device device;

    public DeviceItem(Device device, Properties properties) {
        super(properties);
        this.device = device;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) device.open();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
