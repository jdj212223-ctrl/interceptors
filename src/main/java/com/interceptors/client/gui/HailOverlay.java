package com.interceptors.client.gui;

import com.interceptors.client.StormAmbience;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Pelting hail: a white-out haze and streaks of falling stones across the view, thicker as the hail grows. */
public final class HailOverlay {
    private HailOverlay() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        float hail = StormAmbience.exposedHail();
        if (hail < 0.05f || mc.options.hideGui || mc.level == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, (int) (Mth.clamp(hail, 0, 1) * 110) << 24 | 0xC8CED4);
        long time = mc.level.getGameTime();
        int stones = (int) (hail * 140);
        for (int i = 0; i < stones; i++) {
            // Each stone falls on its own fixed lane, at its own speed, so the field looks random but steady.
            int seed = i * 7919;
            float x = (seed % 997) / 997f * w;
            float speed = 9 + (seed % 13);
            float y = ((time * speed + seed % 509) % (h + 40)) - 20;
            int size = 1 + seed % 3;
            Draw.line(g, x, y, x - 4, y - 9, size, 0xC0F4F6F8);
        }
    }
}
