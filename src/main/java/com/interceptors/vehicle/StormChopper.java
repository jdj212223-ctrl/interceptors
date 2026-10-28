package com.interceptors.vehicle;

import com.interceptors.compat.Storms;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Storm Chopper 4: a news helicopter that flies the storm for the ground team. W/S fly forward and back, A/D turn,
 * Space climbs and the descend key drops. Light and exposed: strong wind shoves it and shakes it, it's damaged
 * like any vehicle in a tornado, and with nobody flying (or the engine gone) it autorotates down.
 */
public class StormChopper extends InterceptorVehicle {
    private static final float CLIMB = 0.3f, AUTOROTATE = -0.12f, TURN = 3.5f;
    /** Vertical speed, blocks per tick. */
    private float climb;

    public StormChopper(EntityType<? extends InterceptorVehicle> type, Level level, VehicleSpec spec) {
        super(type, level, spec);
    }

    @Override
    public void simulate(float forward, float left) {
        boolean piloted = getControllingPassenger() != null && !failed(Part.ENGINE);
        boolean up = piloted && level().isClientSide && FlightControls.ascend.getAsBoolean();
        boolean down = piloted && level().isClientSide && FlightControls.descend.getAsBoolean();
        float target = up ? CLIMB : down ? -CLIMB : piloted ? 0 : AUTOROTATE;
        climb += (target - climb) * 0.15f;

        if (piloted) {
            speed = Mth.clamp((speed + spec().accel() * forward) * 0.985f, -0.4f, spec().maxSpeed());
            deltaRotation = -left * TURN;
            setYRot(getYRot() + deltaRotation);
        } else {
            speed *= 0.95f;
            deltaRotation = 0;
        }
        if (onGround() && climb <= 0) speed *= 0.6f;

        // Wind shoves a helicopter much harder than a truck, and near a tornado it bucks up and down.
        Vec3 wind = forcedWind != null ? forcedWind : Storms.windAt(level(), position().add(0, 1, 0));
        lastWind = wind;
        double mph = wind.horizontalDistance();
        Vec3 flat = new Vec3(wind.x, 0, wind.z);
        Vec3 push = mph > 30 ? flat.normalize().scale((mph - 30) * 0.0004) : Vec3.ZERO;
        drift = new Vec3(drift.x * 0.95, 0, drift.z * 0.95).add(push);
        if (mph > 70) climb += (random.nextFloat() - 0.5f) * 0.1f * (float) ((mph - 70) / 50);

        Vec3 facing = Vec3.directionFromRotation(0, getYRot());
        setDeltaMovement(facing.x * speed + drift.x, climb, facing.z * speed + drift.z);
        move(MoverType.SELF, getDeltaMovement());
    }

    /** Hovering down gently is fine; dropping out of the sky without a pilot or an engine is not. */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (getControllingPassenger() != null && !failed(Part.ENGINE)) return false;
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    /** For the renderer: nose dips forward with speed. */
    public float tilt() {
        return speed * 10;
    }
}
