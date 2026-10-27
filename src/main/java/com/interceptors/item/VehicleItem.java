package com.interceptors.item;

import com.interceptors.registry.ModEntities;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.VehicleSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/** Right-click the ground to park this vehicle there, facing the way you look. */
public class VehicleItem extends Item {
    private final VehicleSpec spec;

    public VehicleItem(VehicleSpec spec, Properties properties) {
        super(properties);
        this.spec = spec;
    }

    public VehicleSpec spec() {
        return spec;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos above = context.getClickedPos().relative(context.getClickedFace());
        InterceptorVehicle vehicle = ModEntities.vehicle(spec).create(level);
        if (vehicle == null) return InteractionResult.FAIL;
        Player player = context.getPlayer();
        vehicle.moveTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, player == null ? 0 : player.getYRot(), 0);
        if (!level.noCollision(vehicle, vehicle.getBoundingBox())) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            level.addFreshEntity(vehicle);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, above);
            if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.interceptors.vehicle.stats", (int) spec.health(), (int) spec.grip(),
                (int) spec.anchorHold(), (int) spec.ratedWind()).withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.interceptors." + spec.id()).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
    }
}
