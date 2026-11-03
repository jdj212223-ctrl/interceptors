package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.client.gui.Draw;
import com.interceptors.compat.Storms;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * The Dominators' weather station: what the roof mast measures right now. Wind and gusts, where it's blowing from,
 * temperature and dew point for the biome, pressure that falls as a tornado gets close, rain and hail, and a
 * minute of wind history.
 */
public class WeatherStationApp implements App {
    private static final int HISTORY = 60;
    private final float[] history = new float[HISTORY];
    private int ticks;

    @Override
    public String id() {
        return "station";
    }

    @Override
    public int color() {
        return 0xFF8C1A;
    }

    private static Vec3 mast(DeviceScreen screen) {
        InterceptorVehicle v = screen.vehicle();
        return v != null ? v.position().add(0, v.spec().height() + 0.8, 0) : screen.here().add(0, 2, 0);
    }

    @Override
    public void tick(DeviceScreen screen) {
        if (++ticks % 20 != 0) return;
        System.arraycopy(history, 1, history, 0, HISTORY - 1);
        history[HISTORY - 1] = (float) Storms.windAt(Minecraft.getInstance().level, mast(screen)).horizontalDistance();
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        g.fill(x, y, x + w, y + h, 0xFF101216);
        g.fill(x, y, x + w, y + 12, 0xFFFF8C1A);
        g.drawString(font, Component.translatable("app.interceptors.station.title"), x + 4, y + 2, 0xFF101216, false);

        Vec3 at = mast(screen);
        Vec3 wind = Storms.windAt(mc.level, at);
        double mph = wind.horizontalDistance();
        float gust = 0;
        for (float f : history) gust = Math.max(gust, f);
        gust = Math.max(gust, (float) mph);
        double from = Storms.compass(-wind.x, -wind.z);
        Biome biome = mc.level.getBiome(net.minecraft.core.BlockPos.containing(at)).value();
        double tempF = 32 + biome.getBaseTemperature() * 60;
        double dewF = tempF - (1 - Math.min(1, biome.getModifiedClimateSettings().downfall())) * 30;
        Storms.StormInfo t = Storms.nearestTornado(mc.level, at);
        double pressure = 29.92 - (t == null ? 0 : Math.max(0, 1 - t.distance() / 2000) * 2.2);
        float rain = 0, hail = Storms.hailAt(mc.level, at);
        var handler = Storms.handler(mc.level);
        try {
            if (handler != null) rain = handler.getPrecipitation(at);
        } catch (Throwable ignored) {
        }

        // Readout tiles.
        String[][] tiles = {
                {"wind", Math.round(mph) + " mph"}, {"gust", Math.round(gust) + " mph"},
                {"from", Storms.compassLabel(from) + " " + Math.round(from) + "°"}, {"temp", Math.round(tempF) + "°F"},
                {"dew", Math.round(dewF) + "°F"}, {"pressure", String.format("%.2f inHg", pressure)},
                {"rain", Math.round(rain * 100) + "%"}, {"hail", hail > 0.05f ? Math.round(hail * 100) + "%" : Component.translatable("app.interceptors.station.none").getString()}};
        int cols = 4, tw = (w - 12) / cols, th = 24, ty = y + 16;
        for (int i = 0; i < tiles.length; i++) {
            int tx = x + 4 + (i % cols) * tw, yy = ty + (i / cols) * (th + 3);
            g.fill(tx, yy, tx + tw - 3, yy + th, 0xFF1C2026);
            g.drawString(font, Component.translatable("app.interceptors.station." + tiles[i][0]), tx + 3, yy + 2, 0xFFFF8C1A, false);
            int color = i < 2 && mph > 65 ? 0xFFFF5050 : 0xFFF0F0F0;
            g.drawString(font, tiles[i][1], tx + 3, yy + 13, color, false);
        }

        // Wind dial and a minute of history.
        int dialY = ty + 2 * (th + 3) + 4, dial = Math.min(h - (dialY - y) - 6, 50) / 2;
        int cx = x + 6 + dial, cy = dialY + dial;
        Draw.ring(g, cx, cy, dial, 0xFF505860);
        g.drawString(font, "N", cx - 2, cy - dial + 2, 0xFF808890, false);
        if (mph > 1) Draw.arrow(g, cx, cy, dial * 0.8f, (from + 180) % 360, 2, 0xFFFF8C1A);
        int gx = cx + dial + 10, gw = x + w - 6 - gx, gh = dial * 2, gy = dialY;
        g.fill(gx, gy, gx + gw, gy + gh, 0xFF14181C);
        float top = Math.max(60, gust * 1.1f);
        for (int i = 1; i < HISTORY; i++) {
            float x0 = gx + (i - 1) * gw / (float) (HISTORY - 1), x1 = gx + i * gw / (float) (HISTORY - 1);
            Draw.line(g, x0, gy + gh - history[i - 1] / top * gh, x1, gy + gh - history[i] / top * gh, 1, 0xFFFF8C1A);
        }
        int ef = WindPhysics.efRating(gust);
        g.drawString(font, Component.translatable("app.interceptors.station.graph").getString() + (ef < 0 ? "" : "  " + Component.translatable("app.interceptors.station.peak", "EF" + ef).getString()), gx + 3, gy + 2, 0xFF808890, false);
    }
}
