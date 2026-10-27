package com.interceptors.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Each vehicle drawn as a list of coloured boxes, in blocks, standing on y=0 and facing +Z.
 * No texture painting needed: the renderer tints a white texture with each box's colour.
 */
public final class VehicleModels {
    public static final int GLOW = 1, ANCHOR = 2, RGB = 4;

    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int flags) {}

    private static final Map<String, List<Box>> MODELS = new HashMap<>();

    static final int WHITE = 0xEDEFF0, BLACK = 0x1B1C1E, TIRE = 0x161616, HUB = 0x9A9EA3, GLASS = 0x22303C,
            HEADLIGHT = 0xFFF6D8, TAIL = 0xE0201A, AMBER = 0xFFA51F, STEEL = 0x6B7075, SOLAR = 0x1E2E5A;

    static {
        MODELS.put("gaming_van", gamingVan());
    }

    public static List<Box> get(String id) {
        return MODELS.getOrDefault(id, List.of());
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

        /** Four wheels: tire plus hub, front axle at {@code zf}, rear at {@code zr}. */
        Builder wheels(double halfTrack, double radius, double zf, double zr) {
            for (double z : new double[]{zf, zr}) {
                pair(halfTrack - 0.25, 0, z - radius, halfTrack + 0.02, radius * 2, z + radius, TIRE);
                pair(halfTrack + 0.02, radius - 0.15, z - 0.15, halfTrack + 0.05, radius + 0.15, z + 0.15, HUB);
            }
            return this;
        }

        List<Box> build() {
            return List.copyOf(boxes);
        }
    }

    /** Frantic Dan-style white high-roof camper van with the gaming rig glow. */
    private static List<Box> gamingVan() {
        return new Builder()
                // body: lower cabin + cargo box, high roof, hood
                .box(-1.05, 0.45, -2.6, 1.05, 1.6, 1.7, WHITE)
                .box(-1.0, 1.6, -2.6, 1.0, 2.6, 1.1, WHITE)
                .box(-1.0, 0.45, 1.7, 1.0, 1.15, 2.7, WHITE)
                // windshield and the slope up to the roof
                .box(-0.95, 1.15, 1.1, 0.95, 2.25, 1.72, GLASS)
                .box(-1.0, 2.25, 1.1, 1.0, 2.6, 1.3, WHITE)
                // cab door windows, camper windows, rear door windows
                .pair(1.05, 1.2, 0.45, 1.08, 1.58, 1.55, GLASS)
                .pair(1.0, 1.75, -2.0, 1.03, 2.25, -0.55, GLASS)
                .pair(0.08, 1.7, -2.63, 0.8, 2.25, -2.6, GLASS)
                // sliding door seam and handles
                .box(1.05, 0.55, -0.1, 1.07, 1.6, -0.06, STEEL)
                .pair(1.05, 1.1, 0.35, 1.1, 1.16, 0.5, BLACK)
                // bumpers, grille, lights
                .box(-1.08, 0.3, 2.7, 1.08, 0.6, 2.88, BLACK)
                .box(-1.08, 0.3, -2.76, 1.08, 0.6, -2.6, BLACK)
                .box(-0.62, 0.65, 2.7, 0.62, 1.08, 2.73, BLACK)
                .pair(0.66, 0.82, 2.7, 0.96, 1.05, 2.75, HEADLIGHT, GLOW)
                .pair(0.82, 0.9, -2.63, 1.0, 1.45, -2.6, TAIL, GLOW)
                // mirrors
                .pair(1.05, 1.45, 1.4, 1.3, 1.7, 1.5, BLACK)
                // roof: solar panels, rack rails, amber chase light bar
                .box(-0.85, 2.6, -2.35, 0.85, 2.66, 0.75, SOLAR)
                .pair(0.9, 2.6, -2.45, 0.96, 2.72, 0.95, BLACK)
                .box(-0.7, 2.6, 0.82, 0.7, 2.78, 1.0, AMBER, GLOW)
                // the gaming setup shows through: RGB underglow and a strip under the camper windows
                .pair(1.05, 0.46, -2.5, 1.07, 0.54, 1.6, 0xB040FF, GLOW | RGB)
                .pair(1.03, 1.68, -2.0, 1.05, 1.72, -0.55, 0x30E0FF, GLOW | RGB)
                .box(-1.0, 0.25, -2.4, 1.0, 0.3, 2.4, 0x7020FF, GLOW | RGB)
                .wheels(1.05, 0.38, 1.95, -1.75)
                // leveling jacks, only out when anchored
                .pair(0.7, -0.05, 2.1, 0.88, 0.45, 2.28, STEEL, ANCHOR)
                .pair(0.7, -0.05, -2.4, 0.88, 0.45, -2.22, STEEL, ANCHOR)
                .pair(0.6, -0.08, 2.0, 0.98, -0.02, 2.38, BLACK, ANCHOR)
                .pair(0.6, -0.08, -2.5, 0.98, -0.02, -2.12, BLACK, ANCHOR)
                .build();
    }

    private VehicleModels() {}
}
