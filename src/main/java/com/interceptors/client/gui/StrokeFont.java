package com.interceptors.client.gui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

/**
 * A squared-off techno lettering drawn as line strokes, like the "DROP ANCHOR" overlay in the Dominator videos.
 * Each glyph lives on a 4x6 grid; drawn thin it's the outline title, drawn thick it's the bold status word.
 */
public final class StrokeFont {
    private static final Map<Character, int[]> GLYPHS = new HashMap<>();

    static {
        // Each glyph is a list of segments x0,y0,x1,y1 on the 4-wide, 6-tall grid.
        put('A', 0, 6, 0, 1, 0, 1, 1, 0, 1, 0, 3, 0, 3, 0, 4, 1, 4, 1, 4, 6, 0, 3, 4, 3);
        put('B', 0, 0, 0, 6, 0, 0, 3, 0, 3, 0, 4, 1, 4, 1, 4, 2, 4, 2, 3, 3, 0, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 5, 4, 5, 3, 6, 3, 6, 0, 6);
        put('C', 4, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 5, 0, 5, 1, 6, 1, 6, 4, 6);
        put('D', 0, 0, 0, 6, 0, 0, 3, 0, 3, 0, 4, 1, 4, 1, 4, 5, 4, 5, 3, 6, 3, 6, 0, 6);
        put('E', 4, 0, 0, 0, 0, 0, 0, 6, 0, 6, 4, 6, 0, 3, 3, 3);
        put('F', 4, 0, 0, 0, 0, 0, 0, 6, 0, 3, 3, 3);
        put('G', 4, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 5, 0, 5, 1, 6, 1, 6, 4, 6, 4, 6, 4, 3, 4, 3, 2, 3);
        put('H', 0, 0, 0, 6, 4, 0, 4, 6, 0, 3, 4, 3);
        put('I', 1, 0, 3, 0, 2, 0, 2, 6, 1, 6, 3, 6);
        put('K', 0, 0, 0, 6, 4, 0, 0, 3, 0, 3, 4, 6);
        put('L', 0, 0, 0, 6, 0, 6, 4, 6);
        put('M', 0, 6, 0, 0, 0, 0, 2, 3, 2, 3, 4, 0, 4, 0, 4, 6);
        put('N', 0, 6, 0, 0, 0, 0, 4, 6, 4, 6, 4, 0);
        put('O', 1, 0, 3, 0, 3, 0, 4, 1, 4, 1, 4, 5, 4, 5, 3, 6, 3, 6, 1, 6, 1, 6, 0, 5, 0, 5, 0, 1, 0, 1, 1, 0);
        put('P', 0, 6, 0, 0, 0, 0, 3, 0, 3, 0, 4, 1, 4, 1, 4, 2, 4, 2, 3, 3, 3, 3, 0, 3);
        put('R', 0, 6, 0, 0, 0, 0, 3, 0, 3, 0, 4, 1, 4, 1, 4, 2, 4, 2, 3, 3, 3, 3, 0, 3, 2, 3, 4, 6);
        put('S', 4, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 2, 0, 2, 1, 3, 1, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 5, 4, 5, 3, 6, 3, 6, 0, 6);
        put('T', 0, 0, 4, 0, 2, 0, 2, 6);
        put('U', 0, 0, 0, 5, 0, 5, 1, 6, 1, 6, 3, 6, 3, 6, 4, 5, 4, 5, 4, 0);
        put('V', 0, 0, 2, 6, 2, 6, 4, 0);
        put('W', 0, 0, 0, 6, 0, 6, 2, 3, 2, 3, 4, 6, 4, 6, 4, 0);
        put('X', 0, 0, 4, 6, 4, 0, 0, 6);
        put('Y', 0, 0, 2, 3, 4, 0, 2, 3, 2, 3, 2, 6);
        put('Z', 0, 0, 4, 0, 4, 0, 0, 6, 0, 6, 4, 6);
        put('%', 0, 6, 4, 0, 0, 0, 1, 0, 3, 6, 4, 6);
        put('-', 1, 3, 3, 3);
    }

    private static void put(char c, int... segments) {
        GLYPHS.put(c, segments);
    }

    private StrokeFont() {}

    /** Width in pixels of {@code text} at {@code unit} pixels per grid step. */
    public static float width(String text, float unit) {
        return text.length() * unit * 5.5f - unit * 1.5f;
    }

    public static void draw(GuiGraphics g, String text, float x, float y, float unit, int thickness, int color) {
        float cx = x;
        for (char c : text.toCharArray()) {
            int[] s = GLYPHS.get(Character.toUpperCase(c));
            if (s != null) {
                for (int i = 0; i < s.length; i += 4) {
                    Draw.line(g, cx + s[i] * unit, y + s[i + 1] * unit, cx + s[i + 2] * unit, y + s[i + 3] * unit, thickness, color);
                }
            }
            cx += unit * 5.5f;
        }
    }

    public static void drawCentered(GuiGraphics g, String text, float centerX, float y, float unit, int thickness, int color) {
        draw(g, text, centerX - width(text, unit) / 2, y, unit, thickness, color);
    }
}
