package com.interceptors.vehicle;

import com.interceptors.compat.Storms;
import com.interceptors.registry.ModItems;
import com.interceptors.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A drivable storm-chasing vehicle. Like a boat, whoever is driving simulates it on their own computer and the
 * server follows; with nobody driving the server simulates it, so a parked van still gets blown around.
 */
public class InterceptorVehicle extends Entity {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ANCHORED = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> HURT_TIME = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.INT);

    private final VehicleSpec spec;
    /** Forward speed in blocks per tick; negative is reversing. */
    private float speed;
    /** Sideways/vertical shove left over from the wind, so a gust carries on for a moment. */
    private Vec3 drift = Vec3.ZERO;
    private float deltaRotation;
    /** Wind the vehicle last felt, mph. Read by the dashboard. */
    private Vec3 lastWind = Vec3.ZERO;
    /** GameTests set this to fake a tornado; null means ask PMWeather. */
    @Nullable
    public Vec3 forcedWind;

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot, lerpXRot;

    public InterceptorVehicle(EntityType<? extends InterceptorVehicle> type, Level level, VehicleSpec spec) {
        super(type, level);
        this.spec = spec;
        this.blocksBuilding = true;
    }

    public VehicleSpec spec() {
        return spec;
    }

    // ------------------------------------------------------------------ synced state

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 0f);
        builder.define(ANCHORED, false);
        builder.define(HURT_TIME, 0);
    }

    public float getDamage() {
        return entityData.get(DAMAGE);
    }

    public void setDamage(float damage) {
        entityData.set(DAMAGE, Math.max(0, damage));
    }

    public float healthFraction() {
        return Mth.clamp(1 - getDamage() / spec.health(), 0, 1);
    }

    public boolean isAnchored() {
        return entityData.get(ANCHORED);
    }

    public void setAnchored(boolean anchored) {
        entityData.set(ANCHORED, anchored);
    }

    public int getHurtTime() {
        return entityData.get(HURT_TIME);
    }

    public float getSpeed() {
        return speed;
    }

    public Vec3 lastWind() {
        return lastWind;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setDamage(tag.getFloat("Damage"));
        setAnchored(tag.getBoolean("Anchored"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Damage", getDamage());
        tag.putBoolean("Anchored", isAnchored());
    }

    // ------------------------------------------------------------------ actions

    /** Drop or raise the anchors. Only works when stopped (you can't spike the road at 40 mph). */
    public boolean toggleAnchors() {
        if (!isAnchored() && Math.abs(speed) > 0.08) return false;
        setAnchored(!isAnchored());
        speed = 0;
        level().playSound(null, getX(), getY(), getZ(), isAnchored() ? ModSounds.ANCHOR_DOWN.get() : ModSounds.ANCHOR_UP.get(),
                SoundSource.NEUTRAL, 1.0f, 1.0f);
        gameEvent(GameEvent.BLOCK_CHANGE);
        return true;
    }

    public void honk() {
        level().playSound(null, getX(), getY(), getZ(), ModSounds.HORN.get(), SoundSource.NEUTRAL, 2.0f, 1.0f);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.IRON_INGOT) && getDamage() > 0) {
            if (!level().isClientSide) {
                setDamage(getDamage() - spec.health() / 6f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6f, 1.4f);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive() || !canAddPassenger(player)) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (level().isClientSide || isRemoved()) return true;
        if (source.getEntity() instanceof Player player && !hasPassenger(player)) {
            // Sneak-punch packs the vehicle back into its item; creative players always pick it up.
            if (player.isShiftKeyDown() || player.getAbilities().instabuild) {
                if (!player.getAbilities().instabuild) spawnAtLocation(getPickResult());
                discard();
                return true;
            }
        }
        entityData.set(HURT_TIME, 10);
        setDamage(getDamage() + amount);
        gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
        if (getDamage() >= spec.health()) wreck();
        return true;
    }

    /** Destroyed: throw everyone out, smoke, scrap. */
    public void wreck() {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        ejectPassengers();
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 6, 1.2, 0.6, 1.2, 0);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1.2, 0.8, 1.2, 0.05);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.5f, 0.8f);
        spawnAtLocation(new ItemStack(Items.IRON_INGOT, 6));
        kill();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.vehicle(spec));
    }

    // ------------------------------------------------------------------ riders

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < spec.seats().size();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int seat = Math.max(0, getPassengers().indexOf(passenger));
        return spec.seats().get(Math.min(seat, spec.seats().size() - 1)).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);
        passenger.setYRot(passenger.getYRot() + deltaRotation);
        passenger.setYHeadRot(passenger.getYHeadRot() + deltaRotation);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Step out the side door, not onto the roof.
        Vec3 side = new Vec3(spec.width() / 2 + 0.6, 0, 0).yRot(-getYRot() * Mth.DEG_TO_RAD);
        for (Vec3 out : new Vec3[]{position().add(side), position().subtract(side)}) {
            AABB box = passenger.getDimensions(passenger.getPose()).makeBoundingBox(out);
            if (level().noCollision(passenger, box)) return out;
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ------------------------------------------------------------------ physics

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return (other.canBeCollidedWith() || other.isPushable()) && !isPassengerOfSameVehicle(other);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** The hitbox is a square, but vans are long: don't vanish when the hitbox leaves the screen. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(3, 0.5, 3);
    }

    @Override
    public float maxUpStep() {
        return 1.0f;
    }

    @Override
    public void tick() {
        if (getHurtTime() > 0) entityData.set(HURT_TIME, getHurtTime() - 1);
        super.tick();
        tickLerp();
        if (isControlledByLocalInstance()) {
            LivingEntity driver = getControllingPassenger();
            simulate(driver == null ? 0 : driver.zza, driver == null ? 0 : driver.xxa);
        } else {
            deltaRotation = 0;
        }
        if (!level().isClientSide && tickCount % 10 == 0) stormDamage();
    }

    /** One tick of driving plus wind. {@code forward} and {@code left} are the driver's keys, -1..1. */
    public void simulate(float forward, float left) {
        deltaRotation = 0;
        if (isAnchored()) {
            speed = 0;
        } else {
            if (forward > 0) speed += spec.accel() * forward;
            else if (forward < 0) speed += (speed > 0 ? spec.accel() * 2.5f : spec.accel() * 0.6f) * forward;
            else speed *= 0.97f;
            if (!onGround()) speed *= 0.995f;
            if (horizontalCollision) speed *= 0.6f;
            speed = Mth.clamp(speed, -spec.maxSpeed() * 0.35f, spec.maxSpeed());
            if (Math.abs(speed) < 0.002f && forward == 0) speed = 0;
            if (onGround() && Math.abs(speed) > 0.01f) {
                float grip = Mth.clamp(Math.abs(speed) / 0.25f, 0.25f, 1f);
                deltaRotation = -left * spec.turnRate() * grip * Math.signum(speed);
                setYRot(getYRot() + deltaRotation);
            }
        }

        Vec3 wind = forcedWind != null ? forcedWind : Storms.windAt(level(), position().add(0, spec.height() / 2, 0));
        lastWind = wind;
        Vec3 push = WindPhysics.push(spec, isAnchored(), wind);
        drift = new Vec3(drift.x * (onGround() ? 0.8 : 0.98), 0, drift.z * (onGround() ? 0.8 : 0.98)).add(push.x, 0, push.z);

        Vec3 facing = Vec3.directionFromRotation(0, getYRot());
        double vy = (getDeltaMovement().y - WindPhysics.GRAVITY) * 0.98 + push.y;
        setDeltaMovement(facing.x * speed + drift.x, vy, facing.z * speed + drift.z);
        move(MoverType.SELF, getDeltaMovement());
        if (onGround() && getDeltaMovement().y < 0) setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
    }

    private void stormDamage() {
        Vec3 wind = forcedWind != null ? forcedWind : Storms.windAt(level(), position().add(0, spec.height() / 2, 0));
        if (!isControlledByLocalInstance()) lastWind = wind;
        float perSecond = WindPhysics.damagePerSecond(spec, wind.horizontalDistance());
        if (perSecond <= 0) return;
        setDamage(getDamage() + perSecond / 2);
        entityData.set(HURT_TIME, 6);
        if (getDamage() >= spec.health()) wreck();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (fallDistance > 4 && !level().isClientSide) {
            float hit = (fallDistance - 4) * 3;
            hurt(damageSources().fall(), hit);
            for (Entity rider : getPassengers()) rider.hurt(damageSources().fall(), hit / 4);
        }
        return false;
    }

    // ------------------------------------------------------------------ smooth movement on everyone else's screen

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = 10;
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return lerpSteps > 0 ? (float) lerpYRot : getYRot();
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, lerpXRot);
            lerpSteps--;
        }
    }
}
