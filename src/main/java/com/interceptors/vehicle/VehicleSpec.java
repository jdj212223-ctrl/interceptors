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
            0.95f, 0.022f, 4.0f, 3.0f, 2.7f,
            List.of(new Vec3(0.45, 0.55, 1.2), new Vec3(-0.45, 0.55, 1.2), new Vec3(0.45, 0.55, -0.6), new Vec3(-0.45, 0.55, -0.6)),
            "jacks");

    public static final List<VehicleSpec> ALL = List.of(GAMING_VAN);

    public static VehicleSpec byId(String id) {
        for (VehicleSpec s : ALL) if (s.id.equals(id)) return s;
        return GAMING_VAN;
    }
}
