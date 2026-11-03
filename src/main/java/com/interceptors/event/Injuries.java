package com.interceptors.event;

import com.interceptors.Interceptors;
import com.interceptors.config.InterceptorsConfig;
import com.interceptors.network.InjuryPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Giant hail that lodges in you. Left alone, the stone melts out on its own in a minute. Pull it out early and
 * the wound it was plugging bleeds: a big mistake.
 */
@EventBusSubscriber(modid = Interceptors.MODID)
public final class Injuries {
    public static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(Registries.DAMAGE_TYPE, Interceptors.id("bleeding"));
    /** Body parts, in the order of the player model: head, chest, right arm, left arm, right leg, left leg. */
    public static final String[] PARTS = {"head", "chest", "right_arm", "left_arm", "right_leg", "left_leg"};
    /** Hail intensity (0..1) from which stones are big enough to stick in you, and the chance per hit. */
    public static final float LODGES_ABOVE = 0.75f, LODGE_CHANCE = 0.2f;
    public static final int MELT_TICKS = 1200, BLEED_TICKS = 400, BLEED_EVERY = 40;
    private static final String TAG = Interceptors.MODID + ":injury";

    private Injuries() {}

    private static CompoundTag data(Player player) {
        return player.getPersistentData().getCompound(TAG);
    }

    private static void save(Player player, CompoundTag tag) {
        player.getPersistentData().put(TAG, tag);
        sync(player);
    }

    public static int lodgedPart(Player player) {
        CompoundTag t = data(player);
        return t.getInt("LodgedTicks") > 0 ? t.getInt("Lodged") : -1;
    }

    public static int bleedTicks(Player player) {
        return data(player).getInt("BleedTicks");
    }

    /** A hailstone hit {@code player} hard enough that it might stick in them. */
    public static void hailHit(Player player, float hail) {
        if (!InterceptorsConfig.get(InterceptorsConfig.HAIL_INJURIES) || hail < LODGES_ABOVE) return;
        if (lodgedPart(player) >= 0 || bleedTicks(player) > 0 || player.getRandom().nextFloat() >= LODGE_CHANCE) return;
        lodge(player, player.getRandom().nextInt(PARTS.length));
    }

    /** Sticks a stone in {@code part}. */
    public static void lodge(Player player, int part) {
        CompoundTag t = data(player);
        t.putInt("Lodged", part);
        t.putInt("LodgedTicks", MELT_TICKS);
        save(player, t);
        player.displayClientMessage(Component.translatable("message.interceptors.hail_lodged",
                Component.translatable("injury.interceptors." + PARTS[part])).withStyle(ChatFormatting.AQUA), true);
    }

    /** The player yanks the stone out: now it bleeds. */
    public static void pullOut(Player player) {
        int part = lodgedPart(player);
        if (part < 0) return;
        CompoundTag t = data(player);
        t.putInt("LodgedTicks", 0);
        t.putInt("Wound", part);
        t.putInt("BleedTicks", BLEED_TICKS);
        save(player, t);
        player.hurt(player.damageSources().source(BLEEDING), 2);
        player.displayClientMessage(Component.translatable("message.interceptors.hail_pulled").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide) tick(event.getEntity());
    }

    /** One tick of melting and bleeding. */
    public static void tick(Player player) {
        CompoundTag t = data(player);
        int lodged = t.getInt("LodgedTicks"), bleed = t.getInt("BleedTicks");
        if (lodged <= 0 && bleed <= 0) return;
        if (lodged > 0) {
            // A chunk of ice in you slows you down a little until it melts out by itself.
            if (lodged % 20 == 0) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
            t.putInt("LodgedTicks", lodged - 1);
            if (lodged == 1) {
                player.displayClientMessage(Component.translatable("message.interceptors.hail_melted").withStyle(ChatFormatting.AQUA), true);
                save(player, t);
                return;
            }
        }
        if (bleed > 0) {
            if (bleed % BLEED_EVERY == 0) player.hurt(player.damageSources().source(BLEEDING), 1);
            t.putInt("BleedTicks", bleed - 1);
            if (bleed == 1) {
                save(player, t);
                return;
            }
        }
        player.getPersistentData().put(TAG, t);
    }

    public static void sync(Player player) {
        CompoundTag t = data(player);
        InjuryPayload p = new InjuryPayload(player.getId(), lodgedPart(player), t.getInt("LodgedTicks"), t.getInt("Wound"), t.getInt("BleedTicks"));
        if (player instanceof ServerPlayer) PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, p);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer watcher && data(target).getInt("LodgedTicks") + bleedTicks(target) > 0) {
            CompoundTag t = data(target);
            PacketDistributor.sendToPlayer(watcher, new InjuryPayload(target.getId(), lodgedPart(target), t.getInt("LodgedTicks"), t.getInt("Wound"), t.getInt("BleedTicks")));
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !data(player).isEmpty()) save(player, new CompoundTag());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }
}
