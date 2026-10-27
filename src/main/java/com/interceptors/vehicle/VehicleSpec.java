package com.interceptors.vehicle;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Everything that makes one interceptor different from another. Wind numbers are in mph, like PMWeather's.
 *
 * @param grip        wind it shrugs off on its tires alone, before it starts to slide
 * @param anchorHold  wind it shrugs off with the anchors down
 * @param ratedWind   wind the body survives; above it, it takes damage
 * @param maxSpeed    top speed in blocks per tick (1.0 is about 45 mph)
 * @param seats       where riders sit, relative to the vehicle's feet, facing +Z
 */
public record VehicleSpec(String id, float health, float grip, float anchorHold, float ratedWind,
                          float maxSpeed, float accel, float turnRate, float width, float height,
                          List<Vec3> seats, String anchorKind) {

    // Frantic Dan's white high-roof gaming camper: fast, comfy, a PC in the back, and only leveling jacks for anchors.
    public static final VehicleSpec GAMING_VAN = new VehicleSpec("gaming_van", 60, 75, 115, 100,
            0.95f, 0.022f, 4.0f, 3.0f, 2.7f, seats(0.55, 1.15, -0.8, 4), "jacks");

    // Reed Timmer's first Dominator, an armored Tahoe. Fans call it Slide 1.
    public static final VehicleSpec DOMINATOR_1 = new VehicleSpec("dominator_1", 120, 95, 160, 150,
            0.85f, 0.020f, 3.6f, 2.8f, 2.2f, seats(0.62, 0.5, -1.2, 4), "spikes");

    // The longer, heavier Yukon XL Dominator with side skirts that drop to the road.
    public static final VehicleSpec DOMINATOR_2 = new VehicleSpec("dominator_2", 160, 110, 185, 175,
            0.80f, 0.019f, 3.4f, 3.0f, 2.3f, seats(0.66, 0.6, -1.3, 4), "skirts and spikes");

    // The big angular truck Dominator with hydraulic skirts and anchors. Fans call it Fail 3.
    public static final VehicleSpec DOMINATOR_3 = new VehicleSpec("dominator_3", 220, 130, 230, 220,
            0.75f, 0.017f, 3.0f, 3.2f, 2.5f, seats(0.75, 0.5, -1.5, 4), "skirts and spikes");

    // Sean Casey's first Tornado Intercept Vehicle: a slow armored tank on a truck chassis, louvered windows.
    public static final VehicleSpec TIV_1 = new VehicleSpec("tiv_1", 200, 120, 200, 200,
            0.60f, 0.014f, 2.8f, 3.2f, 2.6f, seats(0.8, 0.4, -1.4, 2), "spikes");

    // Doppler on Wheels: a radar truck. Lightly built, but its screens see storms much farther away.
    public static final VehicleSpec DOPPLER_ON_WHEELS = new VehicleSpec("doppler_on_wheels", 90, 85, 130, 115,
            0.70f, 0.016f, 3.0f, 3.2f, 2.6f, seats(0.75, 1.5, 1.5, 2), "stabilizer jacks");

    public static final List<VehicleSpec> ALL = List.of(GAMING_VAN, DOMINATOR_1, DOMINATOR_2, DOMINATOR_3, TIV_1, DOPPLER_ON_WHEELS);

    /** Driver front-left, passenger front-right, then two rear seats if there are four. */
    private static List<Vec3> seats(double y, double frontZ, double rearZ, int count) {
        List<Vec3> front = List.of(new Vec3(0.45, y, frontZ), new Vec3(-0.45, y, frontZ));
        if (count <= 2) return front;
        return List.of(front.get(0), front.get(1), new Vec3(0.45, y, rearZ), new Vec3(-0.45, y, rearZ));
    }

    public static VehicleSpec byId(String id) {
        for (VehicleSpec s : ALL) if (s.id.equals(id)) return s;
        return GAMING_VAN;
    }
}
