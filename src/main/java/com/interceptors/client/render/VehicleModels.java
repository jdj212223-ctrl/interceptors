package com.interceptors.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Each vehicle built from boxes, in blocks, standing on y=0 and facing +Z; +X is the driver's side. Every box is
 * made of a realistic {@link Mat}erial (painted metal, riveted armor, rubber, glass...), tinted by its colour when
 * the material allows, and vehicles carry painted lettering ({@link Decal}s) on their sides.
 *
 * <p>A seated rider's eyes end up about 1.02 above their seat, so every cab keeps that band clear of solid boxes:
 * only see-through glass around the driver, or you'd be staring at the inside of a wall.
 */
public final class VehicleModels {
    /** Fullbright. */
    public static final int GLOW = 1;
    /** Anchor hardware: slides down out of the body as the anchors deploy. */
    public static final int ANCHOR = 2;
    /** Colour-cycling gaming lights, switched with the lights key. */
    public static final int RGB = 4;
    /** See-through window glass. */
    public static final int CLEAR = 8;
    /** Drops to the ground with the anchors down (hydraulic skirts). */
    public static final int SKIRT = 16;
    /** Spins around the first spinning box's centre (a radar dish). */
    public static final int SPIN = 32;

    /**
     * A box whose top face can be pulled in to make slopes: {@code insetFront/Back/Side} shrink the top face from
     * the front (+Z), back and both sides, and {@code dropFront} lowers its front edge. All zero is a plain box;
     * with them you get raked windshields, sloping hoods and faceted armor shells.
     */
    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int flags, Mat mat,
                      float insetFront, float insetBack, float insetSide, float dropFront) {
        /** The eight corners: bottom (x0z0, x1z0, x1z1, x0z1), then top in the same order. */
        public float[][] corners() {
            float tx0 = x0 + insetSide, tx1 = x1 - insetSide, tz0 = z0 + insetBack, tz1 = z1 - insetFront;
            return new float[][]{
                    {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
                    {tx0, y1, tz0}, {tx1, y1, tz0}, {tx1, y1 - dropFront, tz1}, {tx0, y1 - dropFront, tz1}};
        }
    }

    /** Lettering painted on both sides at height {@code y} (top of the text), centred on {@code z}. */
    public record Decal(String text, int rgb, float halfWidth, float y, float z, float scale) {}

    public record Model(List<Box> boxes, List<Decal> decals) {}

    private static final Map<String, Model> MODELS = new HashMap<>();

    /** Tints for materials that take a colour: near-white keeps the texture's own look. */
    static final int WHITE = 0xF4F6F7, BLACK_ARMOR = 0xD8D8D8, NEUTRAL = 0xFFFFFF, RED = 0xC8261E, STEEL_TINT = 0xD0D4D8;

    static {
        MODELS.put("gaming_van", gamingVan());
        MODELS.put("dominator_1", dominator1());
        MODELS.put("dominator_2", dominator2());
        MODELS.put("dominator_3", dominator3());
        MODELS.put("tiv_1", tiv1());
        MODELS.put("doppler_on_wheels", dopplerOnWheels());
        MODELS.put("rental_car", rentalCar());
    }

    public static List<Box> get(String id) {
        Model m = MODELS.get(id);
        return m == null ? List.of() : m.boxes();
    }

    public static List<Decal> decals(String id) {
        Model m = MODELS.get(id);
        return m == null ? List.of() : m.decals();
    }

    public static boolean has(String id) {
        return MODELS.containsKey(id);
    }

    /** Collects boxes in the current material; {@code pair} adds a box and its mirror across x=0. */
    private static final class Builder {
        final List<Box> boxes = new ArrayList<>();
        final List<Decal> decals = new ArrayList<>();
        Mat mat = Mat.PAINT;

        Builder mat(Mat mat) {
            this.mat = mat;
            return this;
        }

        Builder box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, int flags) {
            boxes.add(new Box((float) Math.min(x0, x1), (float) y0, (float) z0, (float) Math.max(x0, x1), (float) y1, (float) z1, rgb, flags, mat, 0, 0, 0, 0));
            return this;
        }

        /** A sloped box (see {@link Box}). */
        Builder wedge(double x0, double y0, double z0, double x1, double y1, double z1, int rgb,
                      double insetFront, double insetBack, double insetSide, double dropFront) {
            boxes.add(new Box((float) Math.min(x0, x1), (float) y0, (float) z0, (float) Math.max(x0, x1), (float) y1, (float) z1, rgb, 0, mat,
                    (float) insetFront, (float) insetBack, (float) insetSide, (float) dropFront));
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

        /** Runs {@code body} in another material, then goes back. */
        Builder in(Mat m, java.util.function.Consumer<Builder> body) {
            Mat old = mat;
            mat = m;
            body.accept(this);
            mat = old;
            return this;
        }

        /**
         * A cab you can see out of: pillars and a roof slab in the current material, clear glass on the sides and
         * front, between heights {@code y0}..{@code y1} and from {@code zb} (back) to {@code zf} (windshield).
         */
        Builder cab(double hw, double y0, double y1, double zb, double zf, int body) {
            pair(hw - 0.06, y0, zb, hw, y1, zb + 0.1, body);
            pair(hw - 0.06, y0, zf - 0.12, hw, y1, zf, body);
            box(-hw, y1, zb, hw, y1 + 0.12, zf, body);
            return in(Mat.GLASS, b -> b
                    .pair(hw - 0.04, y0, zb + 0.1, hw, y1, zf - 0.12, NEUTRAL, CLEAR)
                    .box(-hw + 0.06, y0, zf - 0.08, hw - 0.06, y1, zf, NEUTRAL, CLEAR));
        }

        /** Opaque tinted glass (from outside you can't see in). */
        Builder window(double x0, double y0, double z0, double x1, double y1, double z1) {
            return in(Mat.GLASS, b -> b.box(x0, y0, z0, x1, y1, z1, NEUTRAL));
        }

        Builder windows(double x0, double y0, double z0, double x1, double y1, double z1) {
            return in(Mat.GLASS, b -> b.pair(x0, y0, z0, x1, y1, z1, NEUTRAL));
        }

        /** Four wheels: rubber tire plus alloy rim, front axle at {@code zf}, rear at {@code zr}. */
        Builder wheels(double halfTrack, double radius, double zf, double zr) {
            for (double z : new double[]{zf, zr}) {
                in(Mat.TIRE, b -> b.pair(halfTrack - 0.28, 0, z - radius, halfTrack + 0.02, radius * 2, z + radius, NEUTRAL));
                in(Mat.RIM, b -> b.pair(halfTrack + 0.02, radius * 0.2, z - radius * 0.8, halfTrack + 0.04, radius * 1.8, z + radius * 0.8, NEUTRAL));
            }
            return this;
        }

        /** A ground spike that punches down with the anchors: hydraulic ram plus a foot. */
        Builder spike(double x, double z, double bodyBottom) {
            return in(Mat.STEEL, b -> b
                    .pair(x - 0.07, -0.55, z - 0.07, x + 0.07, bodyBottom, z + 0.07, STEEL_TINT, ANCHOR)
                    .pair(x - 0.03, -0.75, z - 0.03, x + 0.03, -0.55, z + 0.03, 0x707070, ANCHOR));
        }

        /** Headlights at the front face {@code z}, taillights at the back face {@code zb}. */
        Builder lights(double hw, double y, double z, double zb) {
            in(Mat.HEADLIGHT, b -> b.pair(hw - 0.32, y, z, hw - 0.04, y + 0.22, z + 0.05, NEUTRAL, GLOW));
            return in(Mat.TAILLIGHT, b -> b.pair(hw - 0.2, y, zb - 0.03, hw, y + 0.45, zb, NEUTRAL, GLOW));
        }

        /** Amber LED chase light bar across the roof. */
        Builder lightbar(double hw, double y, double z0, double z1) {
            return in(Mat.LIGHTBAR, b -> b.box(-hw, y, z0, hw, y + 0.15, z1, NEUTRAL, GLOW));
        }

        Builder decal(String text, int rgb, double halfWidth, double y, double z, double scale) {
            decals.add(new Decal(text, rgb, (float) halfWidth, (float) y, (float) z, (float) scale));
            return this;
        }

        Model build() {
            return new Model(List.copyOf(boxes), List.copyOf(decals));
        }
    }

    /**
     * Frantic Dan-style clean white high-roof camper van. The camper box is hollow so you can see the conversion from
     * the back seats: wood paneling, gaming desk with a big monitor and RGB tower, sofa bed, kitchenette and fridge.
     */
    private static Model gamingVan() {
        return new Builder()
                .box(-1.05, 0.45, -2.6, 1.05, 1.15, 1.75, WHITE)
                .wedge(-1.0, 0.45, 1.75, 1.0, 1.1, 2.7, WHITE, 0.12, 0, 0.03, 0.22)
                // camper box: thin walls, roof and back door, so the inside is a room
                .pair(1.0, 1.15, -2.6, 1.05, 2.6, 0.3, WHITE)
                .box(-1.05, 2.55, -2.6, 1.05, 2.6, 0.3, WHITE)
                .box(-1.05, 1.15, -2.6, 1.05, 2.55, -2.55, WHITE)
                .cab(1.0, 1.15, 2.15, 0.3, 1.75, WHITE)
                .wedge(-1.0, 2.27, 0.3, 1.0, 2.6, 1.35, WHITE, 0.35, 0, 0.04, 0)
                // inside: vinyl floor, wood walls and ceiling, sofa bed across the back
                .in(Mat.VINYL, b -> b.box(-1.0, 1.15, -2.55, 1.0, 1.17, 0.3, NEUTRAL))
                .in(Mat.WOOD, b -> b
                        .pair(0.97, 1.17, -2.55, 1.0, 2.52, 0.3, NEUTRAL)
                        .box(-1.0, 2.52, -2.55, 1.0, 2.55, 0.3, NEUTRAL)
                        .box(-1.0, 1.17, -2.55, 1.0, 2.52, -2.52, NEUTRAL)
                        // gaming desk and its legs, kitchenette counter and overhead cabinets
                        .box(-0.97, 1.72, -1.65, -0.45, 1.77, -0.45, 0x8A6A50)
                        .box(-0.95, 1.17, -1.6, -0.9, 1.72, -1.55, 0x8A6A50)
                        .box(-0.95, 1.17, -0.55, -0.9, 1.72, -0.5, 0x8A6A50)
                        .box(0.6, 1.17, -1.8, 0.97, 1.7, -0.95, NEUTRAL)
                        .box(0.6, 2.2, -1.8, 0.97, 2.5, -0.45, NEUTRAL))
                .in(Mat.FABRIC, b -> b
                        .box(-0.95, 1.17, -2.5, 0.95, 1.45, -1.85, 0x5068A0)
                        .box(-0.95, 1.45, -2.52, 0.95, 1.95, -2.32, 0x5068A0)
                        .box(-0.55, 1.17, -1.2, -0.3, 1.5, -0.9, 0x404040))
                // big monitor, RGB tower, headphones, mic arm, sink, mini fridge
                .in(Mat.PLASTIC, b -> b
                        .box(-0.97, 1.85, -1.55, -0.92, 2.35, -0.55, NEUTRAL)
                        .box(-0.96, 1.17, -0.42, -0.66, 1.68, -0.12, NEUTRAL)
                        .box(-0.85, 1.77, -0.7, -0.7, 1.8, -0.6, NEUTRAL))
                .in(Mat.SCREEN, b -> b.box(-0.92, 1.88, -1.52, -0.91, 2.32, -0.58, NEUTRAL, GLOW))
                .in(Mat.PLAIN, b -> b.box(-0.66, 1.2, -0.4, -0.65, 1.65, -0.38, 0xFF3080, GLOW | RGB))
                .in(Mat.STEEL, b -> b
                        .box(-0.95, 1.77, -1.62, -0.93, 2.05, -1.6, STEEL_TINT)
                        .box(0.65, 1.7, -1.6, 0.85, 1.72, -1.3, STEEL_TINT)
                        .box(0.59, 1.75, -0.85, 0.6, 1.8, -0.5, STEEL_TINT)
                        // sliding door rail on the passenger side
                        .box(-1.05, 0.55, -0.1, -1.07, 2.1, -0.06, STEEL_TINT))
                .box(0.6, 1.17, -0.9, 0.97, 1.85, -0.45, 0xE6E9EC)
                // outside: camper windows, rear door windows
                .windows(1.05, 1.75, -2.0, 1.08, 2.25, -0.55)
                .windows(0.08, 1.7, -2.63, 0.8, 2.25, -2.6)
                // bumpers, grille, lights, mirrors
                .in(Mat.PLASTIC, b -> b
                        .box(-1.08, 0.3, 2.7, 1.08, 0.6, 2.88, NEUTRAL)
                        .box(-1.08, 0.3, -2.76, 1.08, 0.6, -2.6, NEUTRAL)
                        .pair(1.0, 1.4, 1.55, 1.28, 1.65, 1.65, NEUTRAL)
                        .pair(0.9, 2.6, -2.45, 0.96, 2.72, 1.05, NEUTRAL))
                .in(Mat.GRILLE, b -> b.box(-0.62, 0.62, 2.7, 0.62, 1.02, 2.73, NEUTRAL))
                .lights(1.0, 0.75, 2.7, -2.6)
                // roof: solar panels and the amber chase light bar
                .in(Mat.SOLAR, b -> b.box(-0.85, 2.6, -2.35, 0.85, 2.66, 0.95, NEUTRAL))
                .lightbar(0.7, 2.6, 1.1, 1.3)
                // underglow and the strip under the camper windows (U toggles them)
                .in(Mat.PLAIN, b -> b
                        .pair(1.05, 0.46, -2.5, 1.07, 0.54, 1.6, 0x2080FF, GLOW | RGB)
                        .pair(1.05, 1.68, -2.0, 1.07, 1.72, -0.55, 0x30E0FF, GLOW | RGB)
                        .box(-1.0, 0.25, -2.4, 1.0, 0.3, 2.4, 0x2060FF, GLOW | RGB))
                .wheels(1.05, 0.38, 1.95, -1.75)
                // leveling jacks
                .in(Mat.STEEL, b -> b
                        .pair(0.7, -0.05, 2.1, 0.88, 0.45, 2.28, STEEL_TINT, ANCHOR)
                        .pair(0.7, -0.05, -2.4, 0.88, 0.45, -2.22, STEEL_TINT, ANCHOR)
                        .pair(0.6, -0.08, 2.0, 0.98, -0.02, 2.38, 0x606060, ANCHOR)
                        .pair(0.6, -0.08, -2.5, 0.98, -0.02, -2.12, 0x606060, ANCHOR))
                .decal("SUBSCRIBE", 0xE01818, 1.08, 1.58, -1.25, 0.032)
                .build();
    }

    /** Dominator 1 ("Slide 1"): armored matte black Tahoe, red stripe, push bar, weather mast. */
    private static Model dominator1() {
        int body = BLACK_ARMOR;
        return new Builder()
                .mat(Mat.ARMOR)
                .box(-1.0, 0.55, -2.45, 1.0, 1.3, 1.2, body)
                .wedge(-0.97, 0.55, 1.2, 0.97, 1.28, 2.45, body, 0.1, 0, 0.03, 0.12)
                .box(-1.0, 1.3, -2.45, 1.0, 2.12, -0.2, body)
                .cab(1.0, 1.3, 2.0, -0.2, 1.2, body)
                // armor cage over the glass, push bar
                .in(Mat.STEEL, b -> b
                        .pair(1.0, 1.62, -0.1, 1.05, 1.67, 1.1, 0x909498)
                        .box(-0.94, 1.62, 1.2, 0.94, 1.67, 1.25, 0x909498)
                        .pair(1.0, 1.3, 0.45, 1.05, 2.0, 0.52, 0x909498)
                        .box(-1.05, 0.3, 2.45, 1.05, 0.9, 2.75, 0x909498)
                        .box(-0.04, 2.12, -1.6, 0.04, 2.9, -1.52, STEEL_TINT)
                        .box(-0.28, 2.9, -1.68, 0.28, 2.95, -1.44, STEEL_TINT))
                .in(Mat.PAINT, b -> b.pair(1.0, 1.0, -2.45, 1.03, 1.1, 2.45, RED))
                .in(Mat.PLASTIC, b -> b.box(-1.0, 0.4, -2.6, 1.0, 0.7, -2.45, NEUTRAL))
                .in(Mat.GRILLE, b -> b.box(-0.6, 0.8, 2.45, 0.6, 1.2, 2.47, NEUTRAL))
                .lights(0.97, 0.9, 2.45, -2.45)
                .lightbar(0.7, 2.12, 0.8, 1.0)
                .wheels(1.0, 0.42, 1.65, -1.6)
                .spike(0.55, 1.0, 0.55).spike(0.55, -1.9, 0.55)
                .decal("DOMINATOR", 0xE8E8E8, 1.03, 1.95, -1.3, 0.024)
                .build();
    }

    /** Dominator 2: longer gunmetal Yukon XL with orange stripes and hydraulic side skirts. */
    private static Model dominator2() {
        int body = 0xBCC6D0, orange = 0xFF6A13;
        return new Builder()
                .mat(Mat.ARMOR)
                .box(-1.05, 0.55, -2.75, 1.05, 1.35, 1.3, body)
                .wedge(-1.0, 0.55, 1.3, 1.0, 1.32, 2.5, body, 0.15, 0, 0.04, 0.15)
                .wedge(-1.05, 1.35, -2.75, 1.05, 2.17, -0.1, body, 0, 0.25, 0.1, 0)
                .cab(1.05, 1.35, 2.05, -0.1, 1.3, body)
                .in(Mat.PAINT, b -> b
                        .pair(1.05, 1.1, -2.75, 1.08, 1.22, 2.5, orange)
                        .pair(1.05, 1.8, -2.6, 1.08, 1.9, -0.3, orange))
                .in(Mat.STEEL, b -> b
                        .pair(1.05, 1.66, 0.0, 1.1, 1.7, 1.2, 0x909498)
                        .box(-1.08, 0.3, 2.5, 1.08, 0.95, 2.8, 0x909498)
                        .box(-0.5, 2.17, -2.2, 0.5, 2.35, -1.2, STEEL_TINT)
                        .box(-0.04, 2.35, -1.75, 0.04, 3.0, -1.67, STEEL_TINT)
                        .pair(1.06, 0.15, -2.6, 1.12, 1.0, 2.35, 0xB0B4B8, SKIRT))
                .in(Mat.PLASTIC, b -> b.box(-1.05, 0.4, -2.9, 1.05, 0.72, -2.75, NEUTRAL))
                .in(Mat.GRILLE, b -> b.box(-0.6, 0.8, 2.5, 0.6, 1.2, 2.52, NEUTRAL))
                .lights(1.0, 0.92, 2.5, -2.75)
                .lightbar(0.75, 2.17, 0.9, 1.12)
                .wheels(1.02, 0.45, 1.75, -1.85)
                .spike(0.55, 1.1, 0.55).spike(0.55, -2.2, 0.55)
                .decal("DOMINATOR 2", 0xFF8A3A, 1.09, 2.08, -1.4, 0.022)
                .build();
    }

    /**
     * Dominator 3 ("Fail 3"): low, wide, faceted matte black shell on an F-350 chassis (fans say it looks like
     * Darth Vader's helmet), narrow Lexan window slits, armored bed, hydraulic skirts, roof anemometer mast.
     */
    private static Model dominator3() {
        int body = 0xB4B4B4;
        return new Builder()
                .mat(Mat.ARMOR)
                // lower hull and the faceted sloping nose
                .wedge(-1.15, 0.6, -3.0, 1.15, 1.5, 2.0, body, 0, 0, 0.05, 0)
                .wedge(-1.1, 0.6, 2.0, 1.1, 1.5, 3.05, body, 0.5, 0, 0.15, 0.45)
                // armor band under the slits, the slits themselves, and the helmet over the cab
                .box(-1.1, 1.5, -0.4, 1.1, 1.62, 1.55, body)
                .wedge(-1.1, 1.5, 1.55, 1.1, 1.62, 2.0, body, 0.3, 0, 0.1, 0.1)
                .cab(1.05, 1.62, 1.92, -0.4, 1.55, body)
                .wedge(-1.1, 2.04, -0.6, 1.1, 2.45, 1.6, body, 0.55, 0.1, 0.3, 0.12)
                // faceted armored bed behind the cab
                .wedge(-1.15, 1.5, -3.0, 1.15, 2.3, -0.4, body, 0, 0.35, 0.25, 0)
                // silver chevrons, red accent stripe
                .in(Mat.STEEL, b -> b
                        .pair(1.15, 1.3, -2.8, 1.18, 1.38, -0.6, 0xE0E4E8)
                        .box(-1.12, 0.3, 2.9, 1.12, 0.9, 3.2, 0x909498)
                        .box(-0.04, 2.45, -0.2, 0.04, 3.2, -0.12, STEEL_TINT)
                        .box(-0.3, 3.2, -0.3, 0.3, 3.25, -0.02, STEEL_TINT)
                        .pair(1.16, 0.15, -2.9, 1.22, 1.05, 2.8, 0xB0B4B8, SKIRT))
                .in(Mat.PAINT, b -> b.pair(1.1, 1.1, -3.0, 1.13, 1.2, 2.0, RED))
                .in(Mat.PLASTIC, b -> b.box(-1.1, 0.4, -3.15, 1.1, 0.75, -3.0, NEUTRAL))
                .in(Mat.GRILLE, b -> b.box(-0.55, 0.7, 3.0, 0.55, 1.0, 3.03, NEUTRAL))
                .lights(1.0, 0.75, 3.0, -3.0)
                .lightbar(0.55, 2.33, 0.85, 1.02)
                .wheels(1.08, 0.5, 2.0, -2.1)
                .spike(0.6, 1.3, 0.6).spike(0.6, -0.8, 0.6).spike(0.6, -2.6, 0.6)
                .decal("DOMINATOR 3", 0xE8E8E8, 1.1, 1.08, -1.7, 0.022)
                .build();
    }

    /** TIV 1: slab-sided armored truck in dark olive, dented and rusty, louvered glass, camera pod. */
    private static Model tiv1() {
        int body = 0xC8CCA8;
        Builder b = new Builder()
                .mat(Mat.ARMOR_DENTED)
                .box(-1.15, 0.65, -3.0, 1.15, 1.5, 1.4, body)
                .wedge(-1.1, 0.65, 1.4, 1.1, 1.55, 2.95, body, 0.55, 0, 0.05, 0.35)
                .wedge(-1.15, 1.5, -3.0, 1.15, 2.47, -0.6, body, 0, 0.25, 0.12, 0)
                .cab(1.15, 1.5, 2.35, -0.6, 1.4, body)
                .box(-1.15, 2.35, 1.3, 1.15, 2.47, 1.9, body)
                .box(-1.1, 1.55, 1.4, 1.1, 1.75, 1.9, body)
                .in(Mat.STEEL, s -> s
                        .pair(1.15, 0.2, -2.8, 1.21, 0.95, 2.7, 0x8A8E80)
                        .box(-1.15, 0.35, 2.95, 1.15, 1.0, 3.15, 0x8A8E80))
                .in(Mat.PLASTIC, s -> s.box(-0.3, 2.47, 0.3, 0.3, 2.8, 0.9, NEUTRAL))
                .window(-0.12, 2.56, 0.9, 0.12, 2.72, 0.95)
                .lights(1.1, 1.05, 2.95, -3.0)
                .wheels(1.12, 0.55, 2.0, -2.0)
                .spike(0.6, 1.2, 0.65).spike(0.6, -2.4, 0.65)
                .decal("T.I.V.", 0xE8E2C8, 1.16, 2.25, -1.8, 0.05);
        // steel louvers across the glass: armor you can still peek through
        for (double y : new double[]{1.78, 2.0, 2.2}) {
            b.in(Mat.STEEL, s -> s.pair(1.15, y, -0.5, 1.2, y + 0.05, 1.3, 0x8A8E80).box(-1.1, y, 1.4, 1.1, y + 0.05, 1.45, 0x8A8E80));
        }
        return b.build();
    }

    /** Doppler on Wheels: white cab, flatbed, and a big spinning radar dish. */
    private static Model dopplerOnWheels() {
        return new Builder()
                .box(-1.1, 0.6, 0.6, 1.1, 1.45, 2.3, WHITE)
                .wedge(-1.05, 0.6, 2.3, 1.05, 1.45, 3.2, WHITE, 0.1, 0, 0.03, 0.18)
                .box(-1.1, 1.45, 0.55, 1.1, 2.47, 0.62, WHITE)
                .cab(1.1, 1.45, 2.35, 0.62, 2.3, WHITE)
                .box(-1.1, 1.1, -0.4, 1.1, 2.0, 0.5, WHITE)
                .box(-1.1, 1.6, -0.4, -1.12, 1.9, 0.4, 0x2050A0)
                .in(Mat.STEEL, b -> b
                        .box(-1.0, 0.5, -3.2, 1.0, 0.9, 0.6, STEEL_TINT)
                        .box(-1.15, 0.9, -3.2, 1.15, 1.1, 0.55, 0x9A9EA4)
                        .box(-0.3, 1.1, -2.2, 0.3, 2.3, -1.6, STEEL_TINT)
                        .box(-0.3, 2.3, -2.2, 0.3, 2.5, -1.6, STEEL_TINT, SPIN)
                        .box(-0.08, 3.1, -1.85, 0.08, 3.2, -1.25, STEEL_TINT, SPIN)
                        .box(-0.2, 3.05, -1.3, 0.2, 3.25, -1.2, STEEL_TINT, SPIN)
                        .pair(0.65, -0.05, -3.0, 0.83, 0.5, -2.82, STEEL_TINT, ANCHOR)
                        .pair(0.65, -0.05, 0.2, 0.83, 0.5, 0.38, STEEL_TINT, ANCHOR))
                .in(Mat.DISH, b -> b.box(-1.3, 2.4, -2.05, 1.3, 3.9, -1.85, NEUTRAL, SPIN))
                .in(Mat.PLASTIC, b -> b.box(-1.1, 0.35, 3.2, 1.1, 0.75, 3.35, NEUTRAL))
                .in(Mat.GRILLE, b -> b.box(-0.6, 0.8, 3.2, 0.6, 1.3, 3.22, NEUTRAL))
                .lights(1.05, 0.95, 3.2, -3.2)
                .lightbar(0.8, 2.47, 1.6, 1.85)
                .wheels(1.05, 0.5, 2.5, -2.3)
                .decal("DOPPLER ON WHEELS", 0x1E3C82, 1.11, 1.38, 1.45, 0.016)
                .build();
    }

    /** A plain silver rental sedan, for when the interceptor is out of action. */
    private static Model rentalCar() {
        int silver = 0xC4CAD2;
        return new Builder()
                .wedge(-0.95, 0.35, -2.2, 0.95, 1.0, 2.1, silver, 0.15, 0.1, 0.04, 0.12)
                .box(-0.92, 1.0, -2.2, 0.92, 1.1, -1.25, silver)
                .cab(0.92, 1.0, 1.65, -1.25, 0.75, silver)
                .pair(0.92, 1.1, 0.55, 1.12, 1.25, 0.65, silver)
                .in(Mat.PLASTIC, b -> b
                        .box(-0.97, 0.3, 2.1, 0.97, 0.55, 2.22, NEUTRAL)
                        .box(-0.97, 0.3, -2.3, 0.97, 0.55, -2.2, NEUTRAL))
                .in(Mat.GRILLE, b -> b.box(-0.5, 0.6, 2.1, 0.5, 0.85, 2.13, NEUTRAL))
                .in(Mat.PLAIN, b -> b.box(-0.3, 0.75, -2.23, 0.3, 0.9, -2.21, 0xF2E6A0))
                .lights(0.95, 0.65, 2.1, -2.2)
                .wheels(0.95, 0.32, 1.4, -1.4)
                .build();
    }

    private VehicleModels() {}
}
