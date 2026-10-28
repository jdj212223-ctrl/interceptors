package com.interceptors.vehicle;

import com.interceptors.compat.Storms;
import com.interceptors.registry.ModItems;
import com.interceptors.registry.ModSounds;
import com.interceptors.config.InterceptorsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
    /** Every part's condition, 0..100, packed 8 bits each in {@link Part} order. */
    private static final EntityDataAccessor<Long> PARTS = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.LONG);
    /** Anchors moving: ticks left going down (positive) or coming up (negative). */
    private static final EntityDataAccessor<Integer> DEPLOY = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FROZEN = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Ticks left of a computer reboot. */
    private static final EntityDataAccessor<Integer> REBOOT = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BATTERY = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LIGHTS = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Mud and grit packed into the hydraulics, 0..100. At 100 they jam. */
    private static final EntityDataAccessor<Float> GRIME = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.FLOAT);
    public static final float GRIME_WARN = 60, GRIME_JAM = 100;
    /** A satellite dish on the roof: the screens inside get online apps. */
    private static final EntityDataAccessor<Boolean> DISH = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.BOOLEAN);
    /** The Lexan window shield is rolled up over the glass (armored vehicles only). */
    private static final EntityDataAccessor<Boolean> LEXAN = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Wind (mph) that blows out unshielded windows: an EF2 passing over, like Dominator 1 at Aurora in 2009. */
    public static final double SHATTER_MPH = 111;
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> FLYING_GLASS =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, com.interceptors.Interceptors.id("flying_glass"));
    /** Who sits where: "s0".."s4" hold the UUID of the rider in that seat. Seat 0 is the driver's. */
    private static final EntityDataAccessor<CompoundTag> SEATS = SynchedEntityData.defineId(InterceptorVehicle.class, EntityDataSerializers.COMPOUND_TAG);
    /** Soft ground fills the hydraulics: full speed through mud jams them in about two minutes, rain doubles it. */
    private static final float GRIME_PER_TICK = 0.04f, GRIME_PER_SOFT_DEPLOY = 8, WASH_PER_TICK = 1.5f;

    public static final int RETRACT_TICKS = 20, REBOOT_TICKS = 100;
    /** How deep in water a vehicle settles: wheels and lower body under, cab above the surface. */
    public static final double WADING_DEPTH = 0.8;
    /** Solar battery: a full charge runs the screens for about 17 in-game hours, and the sun fills it in about 7. */
    private static final float DRAIN_PER_TICK = 100f / 17000, CHARGE_PER_TICK = 100f / 7000;

    private final VehicleSpec spec;
    /** Forward speed in blocks per tick; negative is reversing. */
    private float speed;
    /** Sideways/vertical shove left over from the wind, so a gust carries on for a moment. */
    private Vec3 drift = Vec3.ZERO;
    private float deltaRotation;
    /** Client only: how far the wheels have turned (radians) and the front wheels' steering angle (degrees). */
    private float wheelSpin, prevWheelSpin, steer, prevSteer;
    /** Wheel radius used to turn distance travelled into rotation. */
    private static final float WHEEL_RADIUS = 0.42f;
    /** Set while being destroyed, so riders can be thrown clear. */
    private boolean wrecking;
    /** Wind above which riders are held in their seats. */
    public static final double STORM_HOLD_MPH = 40;
    /** How far it actually moved last tick, on every side (the server doesn't know the driver's speed). */
    private double groundSpeed;
    @Nullable
    private Vec3 lastTickPos;
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
        builder.define(PARTS, packAll(100));
        builder.define(DEPLOY, 0);
        builder.define(FROZEN, false);
        builder.define(REBOOT, 0);
        builder.define(BATTERY, 100f);
        builder.define(LIGHTS, true);
        builder.define(GRIME, 0f);
        builder.define(DISH, false);
        builder.define(SEATS, new CompoundTag());
        builder.define(LEXAN, false);
    }

    public boolean lexanUp() {
        return entityData.get(LEXAN);
    }

    public void setLexan(boolean up) {
        entityData.set(LEXAN, up);
    }

    /** Roll the Lexan shield up or down. Returns null if it worked, or why not. */
    @Nullable
    public String toggleLexan() {
        if (!spec.hasLexan()) return "message.interceptors.no_lexan";
        setLexan(!lexanUp());
        playVehicleSound(lexanUp() ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN);
        return null;
    }

    /**
     * The windows blow out: glass everywhere, and the riders up front, next to the windshield and door glass,
     * get cut by flying glass.
     */
    public void shatterWindows() {
        if (failed(Part.WINDOWS)) return;
        setCondition(Part.WINDOWS, 0);
        level().playSound(null, getX(), getY() + 1.5, getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 2.0f, 0.8f);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()),
                    getX(), getY() + spec.height() * 0.7, getZ(), 60, spec.width() / 3, 0.4, spec.width() / 3, 0.2);
            for (int seat = 0; seat < 2; seat++) {
                Entity rider = riderIn(seat);
                if (rider == null) continue;
                rider.hurt(damageSources().source(FLYING_GLASS), 5.0f);
                server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.75f, 0.05f, 0.05f), 1.4f),
                        rider.getX(), rider.getEyeY() - 0.2, rider.getZ(), 8, 0.2, 0.15, 0.2, 0);
            }
        }
        tellRiders("message.interceptors.windows_blown");
    }

    // ------------------------------------------------------------------ fixed seats

    /** The seat this rider sits in, which never changes while they ride; -1 if they aren't in one. */
    public int seatOf(Entity rider) {
        CompoundTag seats = entityData.get(SEATS);
        for (int i = 0; i < spec.seats().size(); i++) {
            if (seats.hasUUID("s" + i) && seats.getUUID("s" + i).equals(rider.getUUID())) return i;
        }
        return -1;
    }

    /** Whoever sits in seat {@code seat}, or null if it's empty. */
    @Nullable
    public Entity riderIn(int seat) {
        CompoundTag seats = entityData.get(SEATS);
        if (!seats.hasUUID("s" + seat)) return null;
        java.util.UUID id = seats.getUUID("s" + seat);
        for (Entity e : getPassengers()) if (e.getUUID().equals(id)) return e;
        return null;
    }

    /** Getting in takes the first free seat, the driver's first. Nobody else moves. */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (level().isClientSide || seatOf(passenger) >= 0) return;
        CompoundTag seats = entityData.get(SEATS).copy();
        for (int i = 0; i < spec.seats().size(); i++) {
            if (!seats.hasUUID("s" + i) || riderIn(i) == null) {
                seats.putUUID("s" + i, passenger.getUUID());
                break;
            }
        }
        entityData.set(SEATS, seats);
    }

    /** Getting out just empties that seat: an empty driver's seat means nobody is driving. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (level().isClientSide) return;
        int seat = seatOf(passenger);
        if (seat < 0) return;
        CompoundTag seats = entityData.get(SEATS).copy();
        seats.remove("s" + seat);
        entityData.set(SEATS, seats);
    }

    /** Online apps work inside: a roof dish is installed, or it's the Doppler on Wheels with its radar. */
    public boolean hasDish() {
        return entityData.get(DISH) || spec == VehicleSpec.DOPPLER_ON_WHEELS;
    }

    public boolean hasInstalledDish() {
        return entityData.get(DISH);
    }

    public void setDish(boolean dish) {
        entityData.set(DISH, dish);
    }

    public float getGrime() {
        return entityData.get(GRIME);
    }

    public void setGrime(float grime) {
        float before = getGrime(), after = Mth.clamp(grime, 0, GRIME_JAM);
        entityData.set(GRIME, after);
        if (before < GRIME_WARN && after >= GRIME_WARN && after < GRIME_JAM) tellRiders("message.interceptors.grime_warning");
        if (before < GRIME_JAM && after >= GRIME_JAM) {
            tellRiders("message.interceptors.grime_jammed");
            playVehicleSound(SoundEvents.PISTON_CONTRACT);
        }
    }

    /** Hydraulics that can't move: broken, or jammed with mud. */
    public boolean hydraulicsDown() {
        return failed(Part.HYDRAULICS) || getGrime() >= GRIME_JAM;
    }

    /** Dirt, mud, sand, gravel, grass, farmland: anything that gets into the rams. */
    private boolean onSoftGround() {
        BlockState below = level().getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ()));
        return below.is(BlockTags.DIRT) || below.is(BlockTags.SAND) || below.is(Blocks.GRAVEL) || below.is(Blocks.MUD)
                || below.is(Blocks.FARMLAND) || below.is(Blocks.DIRT_PATH) || below.is(BlockTags.SNOW);
    }

    private static long packAll(int condition) {
        long packed = 0;
        for (Part p : Part.values()) packed |= (long) condition << (p.ordinal() * 8);
        return packed;
    }

    public int condition(Part part) {
        return (int) (entityData.get(PARTS) >> (part.ordinal() * 8) & 0xFF);
    }

    public void setCondition(Part part, int condition) {
        long packed = entityData.get(PARTS) & ~(0xFFL << (part.ordinal() * 8));
        entityData.set(PARTS, packed | (long) Mth.clamp(condition, 0, 100) << (part.ordinal() * 8));
    }

    public boolean failed(Part part) {
        return Part.failed(condition(part));
    }

    public int getDeploy() {
        return entityData.get(DEPLOY);
    }

    public boolean isFrozen() {
        return entityData.get(FROZEN);
    }

    public int getReboot() {
        return entityData.get(REBOOT);
    }

    public float getBattery() {
        return entityData.get(BATTERY);
    }

    public void setBattery(float battery) {
        entityData.set(BATTERY, Mth.clamp(battery, 0, 100));
    }

    /** Screens and dash work: computer alive and not frozen, and (on solar vehicles) battery left. */
    public boolean screensWork() {
        return !isFrozen() && getReboot() == 0 && !failed(Part.COMPUTER) && (!spec.solar() || getBattery() > 0);
    }

    public boolean lightsOn() {
        return entityData.get(LIGHTS);
    }

    public void toggleLights() {
        entityData.set(LIGHTS, !lightsOn());
    }

    /** 0 = anchors up, 1 = fully down; in between while they move. */
    public float anchorExtension() {
        int d = getDeploy();
        int down = Math.max(1, deployTicks());
        if (d > 0) return 1 - d / (float) down;
        if (d < 0) return -d / (float) RETRACT_TICKS;
        return isAnchored() ? 1 : 0;
    }

    private int deployTicks() {
        return spec.deployTicks() * (Part.warning(condition(Part.HYDRAULICS)) || getGrime() >= GRIME_WARN ? 2 : 1);
    }

    public AnchorStatus anchorStatus() {
        if (!spec.hasAnchors()) return AnchorStatus.NONE;
        if (getReboot() > 0) return AnchorStatus.REBOOTING;
        if (isFrozen() || failed(Part.COMPUTER)) return AnchorStatus.FROZEN;
        if (hydraulicsDown()) return AnchorStatus.FAILED;
        int d = getDeploy();
        if (d > 0) return AnchorStatus.DEPLOYING;
        if (d < 0) return AnchorStatus.RETRACTING;
        if (isAnchored()) return AnchorStatus.ANCHORED;
        return Math.max(Math.abs(speed), groundSpeed) > 0.08 ? AnchorStatus.STANDBY : AnchorStatus.READY;
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
        CompoundTag parts = tag.getCompound("Parts");
        for (Part p : Part.values()) setCondition(p, parts.contains(p.id()) ? parts.getInt(p.id()) : 100);
        entityData.set(FROZEN, tag.getBoolean("Frozen"));
        setBattery(tag.contains("Battery") ? tag.getFloat("Battery") : 100);
        entityData.set(LIGHTS, !tag.contains("Lights") || tag.getBoolean("Lights"));
        entityData.set(GRIME, tag.getFloat("Grime"));
        entityData.set(DISH, tag.getBoolean("Dish"));
        entityData.set(SEATS, tag.getCompound("Seats"));
        entityData.set(LEXAN, tag.getBoolean("Lexan"));
        entityData.set(DEPLOY, tag.getInt("Deploy"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Damage", getDamage());
        tag.putBoolean("Anchored", isAnchored());
        CompoundTag parts = new CompoundTag();
        for (Part p : Part.values()) parts.putInt(p.id(), condition(p));
        tag.put("Parts", parts);
        tag.putBoolean("Frozen", isFrozen());
        tag.putFloat("Battery", getBattery());
        tag.putBoolean("Lights", lightsOn());
        tag.putFloat("Grime", getGrime());
        tag.putBoolean("Dish", entityData.get(DISH));
        tag.put("Seats", entityData.get(SEATS).copy());
        tag.putBoolean("Lexan", lexanUp());
        tag.putInt("Deploy", getDeploy());
    }

    // ------------------------------------------------------------------ actions

    /**
     * Drop or raise the anchors. They take a moment to go down, and only start when you're stopped (you can't
     * spike the road at 40 mph). Returns null if it worked, or the language key of why not.
     */
    @Nullable
    public String toggleAnchors() {
        if (!spec.hasAnchors()) return "message.interceptors.no_anchors";
        if (getReboot() > 0) return "message.interceptors.rebooting";
        if (isFrozen()) return "message.interceptors.frozen";
        if (failed(Part.COMPUTER)) return "message.interceptors.computer_dead";
        if (getDeploy() != 0) return "message.interceptors.busy";
        if (isAnchored()) {
            setAnchored(false);
            entityData.set(DEPLOY, -RETRACT_TICKS);
            playVehicleSound(ModSounds.ANCHOR_UP.get());
            return null;
        }
        if (failed(Part.HYDRAULICS)) return "message.interceptors.hydraulics_failed";
        if (getGrime() >= GRIME_JAM) return "message.interceptors.grime_jammed";
        if (Math.max(Math.abs(speed), groundSpeed) > 0.08) return "message.interceptors.stop_first";
        entityData.set(DEPLOY, deployTicks());
        speed = 0;
        // Spikes driven into soft ground pack mud up into the rams.
        if (onSoftGround() && InterceptorsConfig.get(InterceptorsConfig.FAILURES)) setGrime(getGrime() + GRIME_PER_SOFT_DEPLOY);
        playVehicleSound(ModSounds.ANCHOR_DOWN.get());
        return null;
    }

    /** Hold the anchor key while frozen: the computer restarts after a few seconds. */
    @Nullable
    public String reboot() {
        if (failed(Part.COMPUTER)) return "message.interceptors.computer_dead";
        if (!isFrozen() || getReboot() > 0) return null;
        entityData.set(REBOOT, REBOOT_TICKS);
        playVehicleSound(SoundEvents.BEACON_POWER_SELECT);
        return null;
    }

    /** The anchor computer locks up. Anchors stay exactly where they are, even halfway down. */
    public void freeze() {
        if (isFrozen() || !spec.hasAnchors()) return;
        entityData.set(FROZEN, true);
        entityData.set(DEPLOY, 0);
        playVehicleSound(SoundEvents.BEACON_DEACTIVATE);
        tellRiders("message.interceptors.froze");
    }

    private void playVehicleSound(SoundEvent sound) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, 1.0f, 1.0f);
        gameEvent(GameEvent.BLOCK_CHANGE);
    }

    private void tellRiders(String key, Object... args) {
        for (Entity rider : getPassengers()) {
            if (rider instanceof Player p) p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        }
    }

    /** Takes {@code amount} off a part (fractions become a chance of a whole point) and reacts if it just broke. */
    public void damagePart(Part part, double amount) {
        int whole = (int) amount;
        if (random.nextDouble() < amount - whole) whole++;
        if (whole <= 0) return;
        int before = condition(part), after = Math.max(0, before - whole);
        setCondition(part, after);
        if (!Part.failed(before) && Part.failed(after)) {
            tellRiders("message.interceptors.part_failed", Component.translatable("part.interceptors." + part.id()));
            playVehicleSound(SoundEvents.ITEM_BREAK);
            if (part == Part.HYDRAULICS && (isAnchored() || getDeploy() > 0)) {
                // Hydraulic pressure gone: the spikes and skirts pop back up.
                setAnchored(false);
                entityData.set(DEPLOY, -RETRACT_TICKS);
            }
        } else if (!Part.warning(before) && Part.warning(after)) {
            tellRiders("message.interceptors.part_warning", Component.translatable("part.interceptors." + part.id()));
        }
    }

    /** Fully fixes the worst part. Returns the part fixed, or null if nothing needed it. */
    @Nullable
    public Part repairWorstPart() {
        Part worst = null;
        for (Part p : Part.values()) if (condition(p) < 100 && (worst == null || condition(p) < condition(worst))) worst = p;
        if (worst == null) return null;
        setCondition(worst, 100);
        if (worst == Part.COMPUTER && !failed(Part.COMPUTER)) entityData.set(FROZEN, false);
        return worst;
    }

    /**
     * Whether a rider may leave. Tornado wind never pulls anyone out of an intact vehicle: you only get out by
     * choosing to (sneak) or because the vehicle is gone.
     */
    public boolean mayLeave(Entity rider, boolean wantsOut) {
        if (wantsOut || wrecking || isRemoved() || !isAlive() || !rider.isAlive() || rider.isRemoved()) return true;
        if (rider instanceof net.minecraft.server.level.ServerPlayer sp && sp.hasDisconnected()) return true;
        // Outside a storm, let commands, portals and the like move people as usual.
        Vec3 wind = forcedWind != null ? forcedWind : lastWind;
        return wind.horizontalDistance() < STORM_HOLD_MPH;
    }

    public void honk() {
        level().playSound(null, getX(), getY(), getZ(), ModSounds.HORN.get(), SoundSource.NEUTRAL, 2.0f, 1.0f);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModItems.REPAIR_KIT.get())) {
            if (!level().isClientSide) {
                boolean cleaned = getGrime() > 0;
                if (cleaned) setGrime(0);
                Part fixed = repairWorstPart();
                if (fixed == null && cleaned) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    player.displayClientMessage(Component.translatable("message.interceptors.cleaned").withStyle(ChatFormatting.GREEN), true);
                } else if (fixed == null) {
                    player.displayClientMessage(Component.translatable("message.interceptors.nothing_to_fix"), true);
                } else {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    player.displayClientMessage(Component.translatable("message.interceptors.repaired",
                            Component.translatable("part.interceptors." + fixed.id()), condition(fixed)).withStyle(ChatFormatting.GREEN), true);
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.NEUTRAL, 0.8f, 1.2f);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.is(ModItems.SATELLITE_DISH.get()) && !hasDish()) {
            if (!level().isClientSide) {
                setDish(true);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.displayClientMessage(Component.translatable("message.interceptors.dish_installed").withStyle(ChatFormatting.GREEN), true);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 1.0f, 1.2f);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.is(Items.IRON_INGOT) && getDamage() > 0) {
            if (!level().isClientSide) {
                setDamage(getDamage() - spec.health() / 6f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6f, 1.4f);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        // Riders right-clicking inside are using what's in their hand (an iPad, a laptop), not getting in again.
        if (hasPassenger(player)) {
            // Empty hand from inside: the driver gets the car display, everyone else the mounted passenger laptop.
            // With something in hand, let that item work (an iPad, a laptop).
            if (!stack.isEmpty()) return InteractionResult.PASS;
            if (level().isClientSide) seatScreen(player).open();
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive() || !canAddPassenger(player)) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /** The screen a rider reaches from their seat: car display for the driver, the passenger laptop for the rest. */
    public com.interceptors.device.Device seatScreen(Entity rider) {
        return getControllingPassenger() == rider ? com.interceptors.device.Device.CAR_DISPLAY : com.interceptors.device.Device.PASSENGER_LAPTOP;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (level().isClientSide || isRemoved()) return true;
        if (source.getEntity() instanceof Player player && !hasPassenger(player)) {
            // Sneak-punch packs the vehicle back into its item; creative players always pick it up.
            if (player.isShiftKeyDown() || player.getAbilities().instabuild) {
                if (!player.getAbilities().instabuild) {
                    spawnAtLocation(getPickResult());
                    if (hasInstalledDish()) spawnAtLocation(new ItemStack(ModItems.SATELLITE_DISH.get()));
                }
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
        wrecking = true;
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
        return riderIn(0) instanceof Player p ? p : null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int seat = seatOf(passenger);
        // Until the seat list reaches this computer, fall back to the boarding order.
        if (seat < 0) seat = Math.max(0, getPassengers().indexOf(passenger));
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
        // Measured against our own last position: the level resets xo/zo right before every tick.
        if (lastTickPos != null) groundSpeed = Math.sqrt((getX() - lastTickPos.x) * (getX() - lastTickPos.x) + (getZ() - lastTickPos.z) * (getZ() - lastTickPos.z));
        lastTickPos = position();
        if (getHurtTime() > 0) entityData.set(HURT_TIME, getHurtTime() - 1);
        if (!level().isClientSide) serverTick();
        super.tick();
        tickLerp();
        if (level().isClientSide) turnWheels();
        if (isControlledByLocalInstance()) {
            LivingEntity driver = getControllingPassenger();
            simulate(driver == null ? 0 : driver.zza, driver == null ? 0 : driver.xxa);
        } else {
            deltaRotation = 0;
        }
        if (!level().isClientSide && tickCount % 10 == 0) stormDamage();
    }

    private void serverTick() {
        int d = getDeploy();
        if (d > 0) {
            entityData.set(DEPLOY, d - 1);
            if (d == 1) setAnchored(true);
        } else if (d < 0) {
            entityData.set(DEPLOY, d + 1);
        }
        int reboot = getReboot();
        if (reboot > 0) {
            entityData.set(REBOOT, reboot - 1);
            if (reboot == 1) {
                entityData.set(FROZEN, false);
                tellRiders("message.interceptors.rebooted");
            }
        }
        if (isInWater()) {
            if (getGrime() > 0) setGrime(getGrime() - WASH_PER_TICK);
        } else if (groundSpeed > 0.05 && onGround() && InterceptorsConfig.get(InterceptorsConfig.FAILURES) && onSoftGround()) {
            float rate = (float) (InterceptorsConfig.get(InterceptorsConfig.WEAR_RATE) * GRIME_PER_TICK * Math.min(1, groundSpeed / spec.maxSpeed()));
            setGrime(getGrime() + rate * (level().isRainingAt(blockPosition().above()) ? 2 : 1));
        }
        if (spec.solar() && tickCount % 20 == 0) {
            float battery = getBattery();
            if (isVehicle()) battery -= DRAIN_PER_TICK * 20;
            if (level().isDay() && level().canSeeSky(BlockPos.containing(getX(), getY() + spec.height(), getZ()))) battery += CHARGE_PER_TICK * 20;
            setBattery(battery);
        }
    }

    /**
     * Rolls the wheels by however far the vehicle actually moved this tick (so they match its speed on every
     * screen, driver or not), and turns the front wheels with the rate of turn. Client only, purely visual.
     */
    private void turnWheels() {
        prevWheelSpin = wheelSpin;
        prevSteer = steer;
        Vec3 facing = Vec3.directionFromRotation(0, getYRot());
        double moved = (getX() - xo) * facing.x + (getZ() - zo) * facing.z;
        wheelSpin += (float) (moved / WHEEL_RADIUS);
        float yawRate = Mth.wrapDegrees(getYRot() - yRotO);
        float target = Math.abs(moved) < 0.01 ? steer : Mth.clamp(-yawRate * 7 * Math.signum((float) moved), -32, 32);
        steer += (target - steer) * 0.35f;
        // Passengers' dashes read the wind here too; the driver's screen gets it from simulate().
        if (!isControlledByLocalInstance() && tickCount % 5 == 0) lastWind = Storms.windAt(level(), position().add(0, spec.height() / 2, 0));
    }

    public float wheelSpin(float partialTick) {
        return Mth.lerp(partialTick, prevWheelSpin, wheelSpin);
    }

    public float steer(float partialTick) {
        return Mth.lerp(partialTick, prevSteer, steer);
    }

    /** One tick of driving plus wind. {@code forward} and {@code left} are the driver's keys, -1..1. */
    public void simulate(float forward, float left) {
        deltaRotation = 0;
        if (isAnchored() || getDeploy() != 0) {
            speed = 0;
        } else {
            int engine = condition(Part.ENGINE);
            if (Part.failed(engine)) forward = Math.min(forward, 0) * 0.3f; // stalled: you can only roll and brake
            else if (Part.warning(engine) && random.nextFloat() < 0.03f) speed *= 0.6f; // sputter
            float top = spec.maxSpeed() * (failed(Part.TIRES) ? 0.4f : Part.warning(condition(Part.TIRES)) ? 0.8f : 1f);
            if (forward > 0) speed += spec.accel() * forward;
            else if (forward < 0) speed += (speed > 0 ? spec.accel() * 2.5f : spec.accel() * 0.6f) * forward;
            else speed *= 0.97f;
            if (!onGround()) speed *= 0.995f;
            if (horizontalCollision) speed *= 0.6f;
            speed = Mth.clamp(speed, -top * 0.35f, top);
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
        // Heavy, but sealed: in deep water a vehicle bobs up to wading depth instead of sinking out of sight.
        double depth = getFluidHeight(net.minecraft.tags.FluidTags.WATER);
        if (depth > WADING_DEPTH) vy = Math.max(vy * 0.6, Math.min(0.12, (depth - WADING_DEPTH) * 0.15));
        setDeltaMovement(facing.x * speed + drift.x, vy, facing.z * speed + drift.z);
        move(MoverType.SELF, getDeltaMovement());
        if (onGround() && getDeltaMovement().y < 0) setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
    }

    private void stormDamage() {
        Vec3 wind = forcedWind != null ? forcedWind : Storms.windAt(level(), position().add(0, spec.height() / 2, 0));
        if (!isControlledByLocalInstance()) lastWind = wind;
        double mph = wind.horizontalDistance();
        stormWear(mph);
        // No Lexan up when a strong tornado passes over: the windows blow in.
        if (spec.hasLexan() && !lexanUp() && mph >= SHATTER_MPH && InterceptorsConfig.get(InterceptorsConfig.FAILURES)) shatterWindows();
        if (failed(Part.WINDOWS) && mph > 80) {
            // Busted windows: wind and debris come straight in.
            for (Entity rider : getPassengers()) rider.hurt(damageSources().generic(), 1.0f);
        }
        // Baseball-size hail dents the body.
        float hailHere = forcedWind != null ? 0 : Storms.hailAt(level(), position().add(0, spec.height(), 0));
        if (hailHere > 0.3f) setDamage(getDamage() + hailHere * 0.6f);
        float perSecond = WindPhysics.damagePerSecond(spec, mph);
        if (perSecond <= 0) return;
        setDamage(getDamage() + perSecond / 2);
        entityData.set(HURT_TIME, 6);
        if (getDamage() >= spec.health()) wreck();
    }

    /** Strong wind and hail wear the parts down, and can freeze the anchor computer. Runs twice a second. */
    private void stormWear(double mph) {
        if (!InterceptorsConfig.get(InterceptorsConfig.FAILURES)) return;
        double rate = InterceptorsConfig.get(InterceptorsConfig.WEAR_RATE);
        double stress = (mph - spec.ratedWind() * 0.6) / spec.ratedWind();
        if (stress > 0) {
            for (Part part : Part.values()) {
                double wear = random.nextDouble() * stress * 6 * rate * part.stormWear();
                if (part == Part.HYDRAULICS && isAnchored()) wear *= 1.5;
                damagePart(part, wear);
            }
        }
        float hail = forcedWind != null ? 0 : Storms.hailAt(level(), position().add(0, spec.height(), 0));
        if (hail > 0.1f) damagePart(Part.WINDOWS, hail * 4 * rate * random.nextDouble());

        double freezeRate = InterceptorsConfig.get(InterceptorsConfig.FREEZE_RATE);
        if (!isFrozen() && spec.hasAnchors() && freezeRate > 0 && mph > spec.ratedWind() * 0.4) {
            double chance = 0.004 * freezeRate * spec.freezeRisk() * (1 + Math.max(stress, 0) * 4)
                    * (Part.warning(condition(Part.COMPUTER)) ? 2 : 1);
            if (random.nextDouble() < chance) freeze();
        }
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
