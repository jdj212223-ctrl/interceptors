package com.interceptors.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Lines, rings and arrows out of 1-pixel fills, since GuiGraphics only draws rectangles. */
public final class Draw {
    private Draw() {}

    public static void dot(GuiGraphics g, float x, float y, int size, int color) {
        int ix = Math.round(x - size / 2f), iy = Math.round(y - size / 2f);
        g.fill(ix, iy, ix + size, iy + size, color);
    }

    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int width, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = (int) Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        for (int i = 0; i <= steps; i++) dot(g, x0 + dx * i / steps, y0 + dy * i / steps, width, color);
    }

    public static void ring(GuiGraphics g, float cx, float cy, float r, int color) {
        int steps = (int) Math.max(12, r * 6.3f);
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            dot(g, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 1, color);
        }
    }

    public static void disc(GuiGraphics g, float cx, float cy, float r, int color) {
        for (int y = (int) -r; y <= r; y++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - y * y));
            g.fill(Math.round(cx) - half, Math.round(cy) + y, Math.round(cx) + half + 1, Math.round(cy) + y + 1, color);
        }
    }

    /** An arrow from the centre pointing {@code degrees} clockwise from screen-up. */
    public static void arrow(GuiGraphics g, float cx, float cy, float length, double degrees, int width, int color) {
        double a = Math.toRadians(degrees);
        float tx = cx + (float) Math.sin(a) * length, ty = cy - (float) Math.cos(a) * length;
        float bx = cx - (float) Math.sin(a) * length * 0.6f, by = cy + (float) Math.cos(a) * length * 0.6f;
        line(g, bx, by, tx, ty, width, color);
        for (int side : new int[]{-1, 1}) {
            double h = a + Math.PI + side * 0.5;
            line(g, tx, ty, tx + (float) Math.sin(h) * length * 0.45f, ty - (float) Math.cos(h) * length * 0.45f, width, color);
        }
    }

    /** Classic radar reflectivity colours for 0..1 rain intensity, transparent when dry. */
    public static int reflectivity(float v) {
        if (v < 0.04f) return 0;
        if (v < 0.15f) return 0xA000C850;
        if (v < 0.3f) return 0xB0008C30;
        if (v < 0.45f) return 0xC0F0E000;
        if (v < 0.6f) return 0xC0F09000;
        if (v < 0.75f) return 0xD0E81010;
        if (v < 0.9f) return 0xD0A00000;
        return 0xE0E040E0;
    }

    public static int lerpColor(int a, int b, float t) {
        int r = (int) ((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int gr = (int) ((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }
}
