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
 * @param anchorKind  what holds it down ("none" means it has no anchors at all)
 * @param deployTicks how long the anchors take to go down
 * @param freezeRisk  how likely its anchor computer is to freeze in a storm (1 = normal)
 * @param solar       runs its screens off a roof solar battery
 */
public record VehicleSpec(String id, float health, float grip, float anchorHold, float ratedWind,
                          float maxSpeed, float accel, float turnRate, float width, float height,
                          List<Vec3> seats, String anchorKind, int deployTicks, float freezeRisk, boolean solar) {

    // Frantic Dan's white high-roof gaming camper: fast, comfy, a PC in the back, and only leveling jacks for anchors.
    public static final VehicleSpec GAMING_VAN = new VehicleSpec("gaming_van", 60, 75, 115, 100,
            0.95f, 0.022f, 4.0f, 3.0f, 2.7f, seats(0.8, 1.0, 0.8, -2.15, 3),
            "leveling jacks", 50, 1, true);

    // Plain rental car for when the interceptor is wrecked and the team is stranded. No armor, no anchors.
    public static final VehicleSpec RENTAL_CAR = new VehicleSpec("rental_car", 30, 55, 55, 70,
            0.9f, 0.02f, 4.5f, 2.4f, 1.7f, seats(0.65, 0.2, 0.65, -0.85, 3),
            "none", 0, 0, false);

    // Reed Timmer's first Dominator, an armored Tahoe. Fans call it Slide 1.
    // Real figures: a 2007 Chevy Tahoe, 8,000 lb, rated to survive 170 mph winds; at about 155 mph it only slid.
    public static final VehicleSpec DOMINATOR_1 = new VehicleSpec("dominator_1", 130, 120, 170, 170,
            0.85f, 0.020f, 3.6f, 2.8f, 2.2f, seats(0.95, 0.5, 0.95, -1.2, 3), "spikes", 30, 1, false);

    // The longer, heavier Yukon XL Dominator with side skirts that drop to the road.
    // A GMC Yukon XL. No published wind rating (estimated between Dom 1 and Dom 3); El Reno 2013 tore its hood off.
    public static final VehicleSpec DOMINATOR_2 = new VehicleSpec("dominator_2", 160, 125, 185, 185,
            0.80f, 0.019f, 3.4f, 3.0f, 2.3f, seats(1.0, 0.6, 1.0, -1.3, 3), "skirts and spikes", 35, 1, false);

    // The big angular truck Dominator with hydraulic skirts and anchors. Fans call it Fail 3: its anchor computer
    // froze with a tornado right next to it, so it's three times as likely to freeze here.
    // Real figures: a 2013 Ford F-350, 11,000 lb, armor rated for EF5 winds of 200 mph. Head-on it deflects the
    // wind; side-on it can still tip (see WindPhysics.sideFactor).
    public static final VehicleSpec DOMINATOR_3 = new VehicleSpec("dominator_3", 220, 140, 200, 200,
            0.75f, 0.017f, 3.0f, 3.2f, 2.5f, seats(0.9, 0.5, 0.9, -1.5, 3), "skirts and spikes", 40, 3, false);

    // Sean Casey's first Tornado Intercept Vehicle: a slow armored tank on a truck chassis, louvered windows.
    public static final VehicleSpec TIV_1 = new VehicleSpec("tiv_1", 200, 120, 200, 200,
            0.60f, 0.014f, 2.8f, 3.2f, 2.6f, seats(1.15, 0.4, 1.15, -1.4, 1), "spikes", 30, 0.5f, false);

    // Doppler on Wheels: a radar truck. Lightly built, but its screens see storms much farther away.
    public static final VehicleSpec DOPPLER_ON_WHEELS = new VehicleSpec("doppler_on_wheels", 90, 85, 130, 115,
            0.70f, 0.016f, 3.0f, 3.2f, 2.6f, List.of(new Vec3(0.55, 1.1, 1.5), new Vec3(-0.55, 1.1, 1.5), new Vec3(0, 1.1, 1.5)), "stabilizer jacks", 40, 1, false);

    // TIV 2-style intercept tank, our own design: a low wedge of armor with a rotating camera turret on the roof.
    // Real figures: 7 to 9 tons on three axles, a 360-degree steel turret, wind flaps and spikes. No published
    // wind rating; the heaviest thing here, so it holds the most.
    public static final VehicleSpec TIV_2 = new VehicleSpec("tiv_2", 250, 150, 220, 220,
            0.55f, 0.013f, 2.6f, 3.2f, 2.5f, seats(1.1, 0.4, 1.1, -1.3, 1), "skirts and spikes", 30, 0.5f, false);

    // "Dom 4": a Chopper 4-style storm news helicopter that films tornadoes from the air for the ground team.
    // Light, fast, and the wind throws it around.
    public static final VehicleSpec STORM_CHOPPER = new VehicleSpec("storm_chopper", 70, 70, 70, 90,
            1.3f, 0.025f, 3.5f, 3.0f, 2.6f,
            // Bell 407-size: two crew up front and a bench of three behind.
            // Bell 407 cabin: pilot and co-pilot up front, a narrow bench of three behind.
            List.of(new Vec3(0.4, 0.55, 0.9), new Vec3(-0.4, 0.55, 0.9), new Vec3(0.38, 0.55, -0.15), new Vec3(0, 0.55, -0.15), new Vec3(-0.38, 0.55, -0.15)),
            "none", 0, 0, false);

    public static final List<VehicleSpec> ALL = List.of(GAMING_VAN, DOMINATOR_1, DOMINATOR_2, DOMINATOR_3, TIV_1, TIV_2,
            DOPPLER_ON_WHEELS, RENTAL_CAR, STORM_CHOPPER);

    /** Driver front-left, passenger front-right, then a rear row of one (middle) or three seats. */
    private static List<Vec3> seats(double y, double frontZ, double rearY, double rearZ, int rear) {
        List<Vec3> seats = new java.util.ArrayList<>(List.of(new Vec3(0.45, y, frontZ), new Vec3(-0.45, y, frontZ)));
        if (rear == 1) seats.add(new Vec3(0, rearY, rearZ));
        if (rear == 3) for (double x : new double[]{0.55, 0, -0.55}) seats.add(new Vec3(x, rearY, rearZ));
        return List.copyOf(seats);
    }

    /** Only Dominator 1 has the Lexan shield that rolls up over the windows, like at Aurora in 2009. */
    public boolean hasLexan() {
        return this == DOMINATOR_1;
    }

    public boolean flies() {
        return this == STORM_CHOPPER;
    }

    /** The Dom 3's airbag suspension drops the whole shell toward the ground as it anchors. */
    public boolean airSuspension() {
        return this == DOMINATOR_3;
    }

    /** The Dom 3 carries a launcher for weather probes ("Dorothy"-style) to fire into the tornado. */
    public boolean probes() {
        return this == DOMINATOR_3;
    }

    public boolean hasAnchors() {
        return !anchorKind.equals("none");
    }

    public static VehicleSpec byId(String id) {
        for (VehicleSpec s : ALL) if (s.id.equals(id)) return s;
        return GAMING_VAN;
    }
}
