package com.interceptors.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Each vehicle drawn as a list of coloured boxes, in blocks, standing on y=0 and facing +Z; +X is the driver's side.
 * No texture painting needed: the renderer tints a white texture with each box's colour.
 *
 * <p>A seated rider's eyes end up about 1.02 above their seat, so every cab keeps that band clear of solid boxes:
 * only see-through glass around the driver, or you'd be staring at the inside of a wall.
 */
public final class VehicleModels {
    /** Fullbright. */
    public static final int GLOW = 1;
    /** Only drawn with the anchors down. */
    public static final int ANCHOR = 2;
    /** Colour-cycling gaming lights. */
    public static final int RGB = 4;
    /** See-through window glass. */
    public static final int CLEAR = 8;
    /** Drops to the ground with the anchors down (hydraulic skirts). */
    public static final int SKIRT = 16;
    /** Spins around the first spinning box's centre (a radar dish). */
    public static final int SPIN = 32;

    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int flags) {}

    private static final Map<String, List<Box>> MODELS = new HashMap<>();

    static final int WHITE = 0xEDEFF0, BLACK = 0x1B1C1E, TIRE = 0x161616, HUB = 0x9A9EA3, GLASS = 0x22303C,
            CLEAR_GLASS = 0x9FC4D8, HEADLIGHT = 0xFFF6D8, TAIL = 0xE0201A, AMBER = 0xFFA51F, STEEL = 0x6B7075,
            SOLAR = 0x1E2E5A, RED = 0xC0201A;

    static {
        MODELS.put("gaming_van", gamingVan());
        MODELS.put("dominator_1", dominator1());
        MODELS.put("dominator_2", dominator2());
        MODELS.put("dominator_3", dominator3());
        MODELS.put("tiv_1", tiv1());
        MODELS.put("doppler_on_wheels", dopplerOnWheels());
    }

    public static List<Box> get(String id) {
        return MODELS.getOrDefault(id, List.of());
    }

    public static boolean has(String id) {
        return MODELS.containsKey(id);
    }

    /** Collects boxes; {@code pair} adds a box and its mirror across x=0. */
    private static final class Builder {
        final List<Box> boxes = new ArrayList<>();

        Builder box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, int flags) {
            boxes.add(new Box((float) Math.min(x0, x1), (float) y0, (float) z0, (float) Math.max(x0, x1), (float) y1, (float) z1, rgb, flags));
            return this;
        }

        Builder box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
            return box(x0, y0, z0, x1, y1, z1, rgb, 0);
        }

        Builder pair(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, int flags) {
            box(x0, y0, z0, x1, y1, z1, rgb, flags);
            return box(-x0, y0, z0, -x1, y1, z1, rgb, flags);
        }

        Builder pair(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
            return pair(x0, y0, z0, x1, y1, z1, rgb, 0);
        }

        /**
         * A cab you can see out of: pillars, a roof slab, and clear glass on the sides and front, between heights
         * {@code y0}..{@code y1} and from {@code zb} (back) to {@code zf} (windshield). Half-width {@code hw}.
         */
        Builder cab(double hw, double y0, double y1, double zb, double zf, int body) {
            pair(hw - 0.06, y0, zb, hw, y1, zb + 0.1, body);
            pair(hw - 0.06, y0, zf - 0.12, hw, y1, zf, body);
            pair(hw - 0.04, y0, zb + 0.1, hw, y1, zf - 0.12, CLEAR_GLASS, CLEAR);
            box(-hw + 0.06, y0, zf - 0.08, hw - 0.06, y1, zf, CLEAR_GLASS, CLEAR);
            return box(-hw, y1, zb, hw, y1 + 0.12, zf, body);
        }

        /** Four wheels: tire plus hub, front axle at {@code zf}, rear at {@code zr}. */
        Builder wheels(double halfTrack, double radius, double zf, double zr) {
            for (double z : new double[]{zf, zr}) {
                pair(halfTrack - 0.28, 0, z - radius, halfTrack + 0.02, radius * 2, z + radius, TIRE);
                pair(halfTrack + 0.02, radius - 0.15, z - 0.15, halfTrack + 0.05, radius + 0.15, z + 0.15, HUB);
            }
            return this;
        }

        /** A ground spike that punches down with the anchors: hydraulic ram plus a foot. */
        Builder spike(double x, double z, double bodyBottom) {
            pair(x - 0.07, -0.55, z - 0.07, x + 0.07, bodyBottom, z + 0.07, STEEL, ANCHOR);
            return pair(x - 0.03, -0.75, z - 0.03, x + 0.03, -0.55, z + 0.03, 0x3A3D40, ANCHOR);
        }

        /** Headlights at the front face {@code z}, taillights at the back face {@code zb}. */
        Builder lights(double hw, double y, double z, double zb) {
            pair(hw - 0.32, y, z, hw - 0.04, y + 0.22, z + 0.05, HEADLIGHT, GLOW);
            return pair(hw - 0.2, y, zb - 0.03, hw, y + 0.45, zb, TAIL, GLOW);
        }

        List<Box> build() {
            return List.copyOf(boxes);
        }
    }

    /** Frantic Dan-style white high-roof camper van with the gaming rig glow. */
    private static List<Box> gamingVan() {
        return new Builder()
                .box(-1.05, 0.45, -2.6, 1.05, 1.15, 1.75, WHITE)
                .box(-1.0, 0.45, 1.75, 1.0, 1.1, 2.7, WHITE)
                .box(-1.05, 1.15, -2.6, 1.05, 2.6, 0.3, WHITE)
                .cab(1.0, 1.15, 2.15, 0.3, 1.75, WHITE)
                .box(-1.0, 2.27, 0.3, 1.0, 2.6, 1.35, WHITE)
                // camper windows, rear door windows, sliding door seam
                .pair(1.05, 1.75, -2.0, 1.08, 2.25, -0.55, GLASS)
                .pair(0.08, 1.7, -2.63, 0.8, 2.25, -2.6, GLASS)
                .box(1.05, 0.55, -0.1, 1.07, 2.1, -0.06, STEEL)
                // bumpers, grille, lights, mirrors
                .box(-1.08, 0.3, 2.7, 1.08, 0.6, 2.88, BLACK)
                .box(-1.08, 0.3, -2.76, 1.08, 0.6, -2.6, BLACK)
                .box(-0.62, 0.62, 2.7, 0.62, 1.02, 2.73, BLACK)
                .lights(1.0, 0.75, 2.7, -2.6)
                .pair(1.0, 1.4, 1.55, 1.28, 1.65, 1.65, BLACK)
                // roof: solar panels, rack rails, amber chase light bar
                .box(-0.85, 2.6, -2.35, 0.85, 2.66, 0.95, SOLAR)
                .pair(0.9, 2.6, -2.45, 0.96, 2.72, 1.05, BLACK)
                .box(-0.7, 2.6, 1.1, 0.7, 2.78, 1.3, AMBER, GLOW)
                // the gaming setup shows through: RGB underglow and a strip under the camper windows
                .pair(1.05, 0.46, -2.5, 1.07, 0.54, 1.6, 0xB040FF, GLOW | RGB)
                .pair(1.05, 1.68, -2.0, 1.07, 1.72, -0.55, 0x30E0FF, GLOW | RGB)
                .box(-1.0, 0.25, -2.4, 1.0, 0.3, 2.4, 0x7020FF, GLOW | RGB)
                .wheels(1.05, 0.38, 1.95, -1.75)
                // leveling jacks
                .pair(0.7, -0.05, 2.1, 0.88, 0.45, 2.28, STEEL, ANCHOR)
                .pair(0.7, -0.05, -2.4, 0.88, 0.45, -2.22, STEEL, ANCHOR)
                .pair(0.6, -0.08, 2.0, 0.98, -0.02, 2.38, BLACK, ANCHOR)
                .pair(0.6, -0.08, -2.5, 0.98, -0.02, -2.12, BLACK, ANCHOR)
                .build();
    }

    /** Dominator 1 ("Slide 1"): armored black Tahoe, red stripe, push bar, weather mast. */
    private static List<Box> dominator1() {
        int body = 0x202326;
        return new Builder()
                .box(-1.0, 0.55, -2.45, 1.0, 1.3, 1.2, body)
                .box(-0.97, 0.55, 1.2, 0.97, 1.28, 2.45, body)
                .box(-1.0, 1.3, -2.45, 1.0, 2.12, -0.2, body)
                .cab(1.0, 1.3, 2.0, -0.2, 1.2, body)
                // armor cage over the glass
                .pair(1.0, 1.62, -0.1, 1.05, 1.67, 1.1, STEEL)
                .box(-0.94, 1.62, 1.2, 0.94, 1.67, 1.25, STEEL)
                .pair(1.0, 1.3, 0.45, 1.05, 2.0, 0.52, STEEL)
                .pair(1.0, 1.0, -2.45, 1.03, 1.1, 2.45, RED)
                .box(-1.05, 0.3, 2.45, 1.05, 0.9, 2.75, STEEL)
                .box(-1.0, 0.4, -2.6, 1.0, 0.7, -2.45, BLACK)
                .lights(0.97, 0.9, 2.45, -2.45)
                // roof: light bar, anemometer mast
                .box(-0.7, 2.12, 0.8, 0.7, 2.27, 1.0, AMBER, GLOW)
                .box(-0.04, 2.12, -1.6, 0.04, 2.9, -1.52, STEEL)
                .box(-0.28, 2.9, -1.68, 0.28, 2.95, -1.44, STEEL)
                .wheels(1.0, 0.42, 1.65, -1.6)
                .spike(0.55, 1.0, 0.55).spike(0.55, -1.9, 0.55)
                .build();
    }

    /** Dominator 2: longer gunmetal Yukon XL with orange stripes and hydraulic side skirts. */
    private static List<Box> dominator2() {
        int body = 0x2B2F33, orange = 0xFF6A13;
        return new Builder()
                .box(-1.05, 0.55, -2.75, 1.05, 1.35, 1.3, body)
                .box(-1.0, 0.55, 1.3, 1.0, 1.32, 2.5, body)
                .box(-1.05, 1.35, -2.75, 1.05, 2.17, -0.1, body)
                .cab(1.05, 1.35, 2.05, -0.1, 1.3, body)
                .pair(1.05, 1.66, 0.0, 1.1, 1.7, 1.2, STEEL)
                .pair(1.05, 1.1, -2.75, 1.08, 1.22, 2.5, orange)
                .pair(1.05, 1.8, -2.6, 1.08, 1.9, -0.3, orange)
                .box(-1.08, 0.3, 2.5, 1.08, 0.95, 2.8, STEEL)
                .box(-1.05, 0.4, -2.9, 1.05, 0.72, -2.75, BLACK)
                .lights(1.0, 0.92, 2.5, -2.75)
                .box(-0.75, 2.17, 0.9, 0.75, 2.32, 1.12, AMBER, GLOW)
                .box(-0.5, 2.17, -2.2, 0.5, 2.35, -1.2, STEEL)
                .box(-0.04, 2.35, -1.75, 0.04, 3.0, -1.67, STEEL)
                .wheels(1.02, 0.45, 1.75, -1.85)
                .pair(1.06, 0.15, -2.6, 1.12, 1.0, 2.35, STEEL, SKIRT)
                .spike(0.55, 1.1, 0.55).spike(0.55, -2.2, 0.55)
                .build();
    }

    /** Dominator 3 ("Fail 3"): big angular armored truck, sloped visor, armored bed, hydraulic skirts. */
    private static List<Box> dominator3() {
        int body = 0x17181A, trim = 0xC9CDD2;
        return new Builder()
                .box(-1.1, 0.6, -3.0, 1.1, 1.45, 1.4, body)
                .box(-1.05, 0.6, 1.4, 1.05, 1.55, 2.4, body)
                .box(-1.05, 0.6, 2.4, 1.05, 1.35, 2.95, body)
                .box(-1.1, 1.45, -3.0, 1.1, 2.25, -0.4, body)
                .cab(1.1, 1.45, 2.25, -0.4, 1.4, body)
                .box(-1.1, 2.25, 1.3, 1.1, 2.37, 1.75, body)
                // silver chevrons on the armored bed, red accents
                .pair(1.1, 1.6, -2.7, 1.13, 1.7, -0.7, trim)
                .pair(1.1, 1.9, -2.4, 1.13, 2.0, -1.0, trim)
                .pair(1.1, 1.1, -3.0, 1.13, 1.2, 2.95, RED)
                .box(-1.12, 0.3, 2.95, 1.12, 1.0, 3.2, STEEL)
                .box(-1.1, 0.4, -3.15, 1.1, 0.75, -3.0, BLACK)
                .lights(1.05, 1.0, 2.95, -3.0)
                // roof instrument turret and mast
                .box(-0.45, 2.37, -0.3, 0.45, 2.62, 0.6, STEEL)
                .box(-0.04, 2.62, 0.1, 0.04, 3.3, 0.18, STEEL)
                .box(-0.3, 3.3, 0.0, 0.3, 3.35, 0.28, STEEL)
                .box(-0.8, 2.37, 0.9, 0.8, 2.5, 1.15, AMBER, GLOW)
                .wheels(1.08, 0.5, 2.0, -2.1)
                .pair(1.12, 0.15, -2.9, 1.18, 1.05, 2.8, STEEL, SKIRT)
                .spike(0.6, 1.3, 0.6).spike(0.6, -0.8, 0.6).spike(0.6, -2.6, 0.6)
                .build();
    }

    /** TIV 1: slab-sided armored truck in dark olive, louvered glass, camera pod. */
    private static List<Box> tiv1() {
        int body = 0x3A3D33;
        Builder b = new Builder()
                .box(-1.15, 0.65, -3.0, 1.15, 1.5, 1.4, body)
                .box(-1.1, 0.65, 1.4, 1.1, 1.55, 2.95, body)
                .box(-1.15, 1.5, -3.0, 1.15, 2.47, -0.6, body)
                .cab(1.15, 1.5, 2.35, -0.6, 1.4, body)
                .box(-1.15, 2.35, 1.3, 1.15, 2.47, 1.9, body)
                .box(-1.1, 1.55, 1.4, 1.1, 1.75, 1.9, body)
                .pair(1.15, 0.2, -2.8, 1.21, 0.95, 2.7, STEEL)
                .box(-1.15, 0.35, 2.95, 1.15, 1.0, 3.15, STEEL)
                .lights(1.1, 1.05, 2.95, -3.0)
                .box(-0.3, 2.47, 0.3, 0.3, 2.8, 0.9, BLACK)
                .box(-0.12, 2.56, 0.9, 0.12, 2.72, 0.95, CLEAR_GLASS)
                .wheels(1.12, 0.55, 2.0, -2.0)
                .spike(0.6, 1.2, 0.65).spike(0.6, -2.4, 0.65);
        // steel louvers across the glass: armor you can still peek through
        for (double y : new double[]{1.78, 2.0, 2.2}) {
            b.pair(1.15, y, -0.5, 1.2, y + 0.05, 1.3, STEEL).box(-1.1, y, 1.4, 1.1, y + 0.05, 1.45, STEEL);
        }
        return b.build();
    }

    /** Doppler on Wheels: white cab, flatbed, and a big spinning radar dish. */
    private static List<Box> dopplerOnWheels() {
        return new Builder()
                .box(-1.1, 0.6, 0.6, 1.1, 1.45, 2.3, WHITE)
                .box(-1.05, 0.6, 2.3, 1.05, 1.45, 3.2, WHITE)
                .box(-1.1, 1.45, 0.55, 1.1, 2.47, 0.62, WHITE)
                .cab(1.1, 1.45, 2.35, 0.62, 2.3, WHITE)
                .box(-1.0, 0.5, -3.2, 1.0, 0.9, 0.6, STEEL)
                .box(-1.15, 0.9, -3.2, 1.15, 1.1, 0.55, 0x4A4F55)
                .box(-1.1, 1.1, -0.4, 1.1, 2.0, 0.5, WHITE)
                .box(-1.1, 1.6, -0.4, -1.12, 1.9, 0.4, 0x2050A0)
                .box(-1.1, 0.35, 3.2, 1.1, 0.75, 3.35, BLACK)
                .lights(1.05, 0.95, 3.2, -3.2)
                .box(-0.8, 2.47, 1.6, 0.8, 2.6, 1.85, AMBER, GLOW)
                // radar: pedestal, then the spinning base, dish and feed horn
                .box(-0.3, 1.1, -2.2, 0.3, 2.3, -1.6, STEEL)
                .box(-0.3, 2.3, -2.2, 0.3, 2.5, -1.6, STEEL, SPIN)
                .box(-1.3, 2.4, -2.05, 1.3, 3.9, -1.85, 0xF4F4F4, SPIN)
                .box(-0.08, 3.1, -1.85, 0.08, 3.2, -1.25, STEEL, SPIN)
                .box(-0.2, 3.05, -1.3, 0.2, 3.25, -1.2, STEEL, SPIN)
                .wheels(1.05, 0.5, 2.5, -2.3)
                .pair(0.65, -0.05, -3.0, 0.83, 0.5, -2.82, STEEL, ANCHOR)
                .pair(0.65, -0.05, 0.2, 0.83, 0.5, 0.38, STEEL, ANCHOR)
                .build();
    }

    private VehicleModels() {}
}
