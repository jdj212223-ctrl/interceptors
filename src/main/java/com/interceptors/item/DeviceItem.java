package com.interceptors.item;

import com.interceptors.device.Device;
import com.interceptors.registry.ModDataComponents;
import com.interceptors.vehicle.InterceptorVehicle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A handheld screen (iPad, MacBook, Rugged Laptop): right-click to open it anywhere. It runs on a battery that
 * drains while the screen is open and recharges from the charger in any interceptor you ride in.
 */
public class DeviceItem extends Item {
    /** Percent charged per charge step (every 2 seconds) while riding. */
    public static final int CHARGE_STEP = 2;

    private final Device device;

    public DeviceItem(Device device, Properties properties) {
        super(properties);
        this.device = device;
    }

    public Device device() {
        return device;
    }

    public static int battery(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BATTERY.get(), 100);
    }

    public static void setBattery(ItemStack stack, int battery) {
        stack.set(ModDataComponents.BATTERY.get(), Mth.clamp(battery, 0, 100));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (device.portable() && battery(stack) <= 0) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.interceptors.device_dead").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) device.open();
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** The vehicle charger: any interceptor tops up devices carried by its riders, drawing on its own power. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !device.portable() || entity.tickCount % 40 != 0) return;
        if (!(entity.getVehicle() instanceof InterceptorVehicle vehicle) || battery(stack) >= 100) return;
        if (vehicle.spec().solar()) {
            if (vehicle.getBattery() <= 0) return;
            vehicle.setBattery(vehicle.getBattery() - 0.2f);
        }
        setBattery(stack, battery(stack) + CHARGE_STEP);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return device.portable() && battery(stack) < 100;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * battery(stack) / 100f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(battery(stack) / 300f, 1f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (device.portable()) tooltip.add(Component.translatable("tooltip.interceptors.battery", battery(stack)).withStyle(ChatFormatting.GRAY));
    }
}
