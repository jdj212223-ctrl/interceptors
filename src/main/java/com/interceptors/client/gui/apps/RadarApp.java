package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.client.gui.Draw;
import com.interceptors.compat.Storms;
import dev.protomanly.pmweather.weather.WeatherHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * A live radar scope: PMWeather's rain and hail painted as reflectivity, storms, and tornadoes with their
 * forecast track. North is up, you're in the middle. Scroll to zoom.
 */
public class RadarApp implements App {
    private static final int[] RANGES = {250, 500, 1000, 2000, 4000};
    private static final int CELLS = 48;
    /** Remembered between openings, like a real app. */
    private static int rangeIndex = 2;

    private final float[] rain = new float[CELLS * CELLS];
    private final float[] hail = new float[CELLS * CELLS];
    private int age = Integer.MAX_VALUE;
    private double sampledRange;

    @Override
    public String id() {
        return "radar";
    }

    @Override
    public int color() {
        return 0x1E9E5A;
    }

    private static double range(DeviceScreen screen) {
        return RANGES[rangeIndex] * screen.rangeBoost();
    }

    @Override
    public void tick(DeviceScreen screen) {
        // A real radar sweeps every few seconds; resampling twice a second is plenty and cheap.
        if (++age < 10 && sampledRange == range(screen)) return;
        age = 0;
        sampledRange = range(screen);
        Minecraft mc = Minecraft.getInstance();
        WeatherHandler handler = Storms.handler(mc.level);
        if (handler == null) return;
        Vec3 c = screen.here();
        try {
            for (int j = 0; j < CELLS; j++) {
                for (int i = 0; i < CELLS; i++) {
                    double x = c.x + ((i + 0.5) / CELLS * 2 - 1) * sampledRange;
                    double z = c.z + ((j + 0.5) / CELLS * 2 - 1) * sampledRange;
                    Vec3 p = new Vec3(x, c.y, z);
                    rain[j * CELLS + i] = handler.getPrecipitation(p);
                    hail[j * CELLS + i] = handler.getHail(p);
                }
            }
        } catch (Throwable ignored) {
            // Storm list changed mid-sweep; next sweep will catch up.
        }
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        g.fill(x, y, x + w, y + h, 0xFF06120C);
        int size = Math.min(w, h) - 6;
        int left = x + (w - size) / 2, top = y + (h - size) / 2;
        float cx = left + size / 2f, cy = top + size / 2f, radius = size / 2f;
        double range = range(screen);
        if (age == Integer.MAX_VALUE) tick(screen);

        float cell = size / (float) CELLS;
        for (int j = 0; j < CELLS; j++) {
            for (int i = 0; i < CELLS; i++) {
                float v = Math.max(rain[j * CELLS + i], hail[j * CELLS + i] > 0.05f ? 0.95f : 0);
                int color = Draw.reflectivity(v);
                if (color != 0) {
                    g.fill((int) (left + i * cell), (int) (top + j * cell), (int) (left + (i + 1) * cell) + 1, (int) (top + (j + 1) * cell) + 1, color);
                }
            }
        }

        for (int r = 1; r <= 4; r++) Draw.ring(g, cx, cy, radius * r / 4, 0x5040C080);
        Draw.line(g, cx, top, cx, top + size, 1, 0x3040C080);
        Draw.line(g, left, cy, left + size, cy, 1, 0x3040C080);
        g.drawString(font, "N", (int) cx - 2, top + 2, 0x80FF80, false);

        Vec3 here = screen.here();
        for (Storms.StormInfo s : Storms.around(Minecraft.getInstance().level, here)) {
            float px = cx + (float) ((s.position().x - here.x) / range * radius);
            float py = cy + (float) ((s.position().z - here.z) / range * radius);
            if (!s.tornado()) {
                if (Math.abs(px - cx) < radius && Math.abs(py - cy) < radius) Draw.ring(g, px, py, 3, 0x90FFFFFF);
                continue;
            }
            // Forecast track: where it'll be over the next two minutes.
            Vec3 ahead = s.velocity().scale(20 * 120);
            Draw.line(g, px, py, px + (float) (ahead.x / range * radius), py + (float) (ahead.z / range * radius), 1, 0xC0FF9020);
            float r = Math.max(3, (float) (s.width() / 2 / range * radius));
            Draw.disc(g, px, py, r, 0xE0FF2020);
            Draw.ring(g, px, py, r + 2, 0xFFFFFFFF);
            g.drawString(font, s.efLabel() + " " + s.windspeed(), (int) px + (int) r + 4, (int) py - 4, 0xFFFF6060, true);
        }

        Draw.arrow(g, cx, cy, 6, screen.facing(), 2, 0xFF30E0FF);

        String label = range >= 1000 ? String.format("%.1f km", range / 1000) : (int) range + " m";
        g.drawString(font, Component.translatable("app.interceptors.radar.range", label), x + 4, y + 4, 0xC0FFC0, false);
        if (screen.rangeBoost() > 1) g.drawString(font, Component.translatable("app.interceptors.radar.dow"), x + 4, y + 14, 0xFFC040, false);
        g.drawString(font, Component.translatable("app.interceptors.radar.zoom"), x + 4, y + h - 11, 0x608060, false);
    }

    @Override
    public boolean mouseScrolled(DeviceScreen screen, double mouseX, double mouseY, double amount) {
        rangeIndex = Math.clamp(rangeIndex - (int) Math.signum(amount), 0, RANGES.length - 1);
        return true;
    }

    @Override
    public boolean keyPressed(DeviceScreen screen, int key, int scancode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD) return mouseScrolled(screen, 0, 0, 1);
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT) return mouseScrolled(screen, 0, 0, -1);
        return false;
    }
}
