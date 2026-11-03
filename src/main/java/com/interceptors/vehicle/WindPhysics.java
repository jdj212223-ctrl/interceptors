package com.interceptors.vehicle;

import net.minecraft.world.phys.Vec3;

/** How tornado wind shoves and hurts a vehicle. Pure math, so GameTests can check it without a real storm. */
public final class WindPhysics {
    /** Gravity on a vehicle, blocks per tick per tick. Lift has to beat this to get it airborne. */
    public static final double GRAVITY = 0.08;

    private WindPhysics() {}

    /** The wind a vehicle can stand before it starts moving: tire grip, or the anchors when they're down. */
    public static double hold(VehicleSpec spec, boolean anchored) {
        return anchored ? spec.anchorHold() : spec.grip();
    }

    /**
     * Extra velocity this tick from a wind vector in mph. Below the hold nothing happens; above it the vehicle
     * slides downwind, and at about one and a half times the hold it starts to lift.
     */
    public static Vec3 push(VehicleSpec spec, boolean anchored, Vec3 wind) {
        Vec3 flat = new Vec3(wind.x, 0, wind.z);
        double mph = flat.length();
        double hold = hold(spec, anchored);
        if (mph <= hold) return Vec3.ZERO;
        double excess = Math.min((mph - hold) / hold, 2.0);
        Vec3 slide = flat.normalize().scale(excess * 0.06);
        double lift = excess > 0.5 ? Math.min(excess - 0.5, 1.5) * 0.07 : 0;
        return slide.add(0, lift, 0);
    }

    /**
     * Wind from the side shoves a vehicle harder than wind on the nose, which its shape deflects: 0.8 head-on or
     * tail-on, up to 1.3 broadside. {@code yaw} is the vehicle's facing.
     */
    public static double sideFactor(Vec3 wind, float yaw) {
        Vec3 flat = new Vec3(wind.x, 0, wind.z);
        if (flat.lengthSqr() < 1e-6) return 1;
        Vec3 facing = Vec3.directionFromRotation(0, yaw);
        double along = Math.abs(flat.normalize().dot(facing));
        return 0.8 + 0.5 * (1 - along);
    }

    /** Body damage per second from wind over the vehicle's rating. Anchors don't help here: they hold, they don't armor. */
    public static float damagePerSecond(VehicleSpec spec, double mph) {
        if (mph <= spec.ratedWind()) return 0;
        return (float) ((mph - spec.ratedWind()) / spec.ratedWind() * 8.0);
    }

    /** Enhanced Fujita rating for a wind speed in mph, -1 below EF0. */
    public static int efRating(double mph) {
        if (mph < 65) return -1;
        if (mph < 86) return 0;
        if (mph < 111) return 1;
        if (mph < 136) return 2;
        if (mph < 166) return 3;
        if (mph < 201) return 4;
        return 5;
    }

    /** Blocks per tick to mph (a block is a meter, a second is 20 ticks). */
    public static double toMph(double blocksPerTick) {
        return blocksPerTick * 20 * 2.23694;
    }
}
