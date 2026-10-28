package com.interceptors.client.gui;

import com.interceptors.compat.Storms;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.Part;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The basic car display, top left: speed, armor, wind and the nearest tornado, plus warning lights for parts
 * that are wearing out. The anchor overlay is {@link DropAnchorHud}, top right.
 */
public final class VehicleHud {
    private VehicleHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !(mc.player.getVehicle() instanceof InterceptorVehicle v)) return;
        Font font = mc.font;
        boolean blink = mc.level.getGameTime() / 6 % 2 == 0;
        int x = 6, y = 6, w = 168, h = 74;
        g.fill(x, y, x + w, y + h, 0xB0101418);
        g.fill(x, y, x + w, y + 1, 0xFF30E0FF);

        g.drawString(font, Component.translatable("entity.interceptors." + v.spec().id()), x + 5, y + 4, 0xFFFFFF);
        int mph = (int) Math.round(WindPhysics.toMph(Math.abs(v.getSpeed())));
        g.drawString(font, mph + " mph", x + w - 5 - font.width(mph + " mph"), y + 4, 0x30E0FF);

        float hp = v.healthFraction();
        g.fill(x + 5, y + 16, x + w - 5, y + 20, 0xFF303030);
        g.fill(x + 5, y + 16, x + 5 + (int) ((w - 10) * hp), y + 20, hp > 0.5f ? 0xFF40D060 : hp > 0.25f ? 0xFFE0B020 : 0xFFE03030);

        if (!v.screensWork()) {
            // Frozen or dead computer, or a flat battery: the dash shows nothing useful, just like the real thing.
            Component why = Component.translatable(v.spec().solar() && v.getBattery() <= 0 ? "hud.interceptors.battery_dead" : "hud.interceptors.not_responding");
            g.fill(x + 3, y + 24, x + w - 3, y + h - 3, 0xFF06080A);
            if (blink || v.getReboot() > 0) g.drawString(font, why, x + w / 2 - font.width(why) / 2, y + 44, 0x30E0FF);
        } else {
            double wind = v.lastWind().horizontalDistance();
            int ef = WindPhysics.efRating(wind);
            int windColor = wind > WindPhysics.hold(v.spec(), v.isAnchored()) ? 0xFF4040 : wind > 50 ? 0xFFC040 : 0xC0C8D0;
            g.drawString(font, Component.translatable("hud.interceptors.wind", (int) wind, ef < 0 ? "" : " (EF" + ef + ")"), x + 5, y + 26, windColor);

            Storms.StormInfo t = Storms.nearestTornado(mc.level, v.position());
            if (t == null) {
                g.drawString(font, Component.translatable("hud.interceptors.no_tornado"), x + 5, y + 39, 0x9AA0A6);
            } else {
                g.drawString(font, Component.translatable("hud.interceptors.tornado", t.efLabel(), t.windspeed(), (int) t.distance(),
                        Storms.compassName(t.bearing())), x + 5, y + 39, 0xFF5050);
                String motion = Double.isNaN(t.heading()) ? "--" : Storms.compassName(t.heading()) + " " + (int) t.speed() + " mph";
                g.drawString(font, Component.translatable("hud.interceptors.motion", motion, (int) t.width()), x + 5, y + 50, 0xC0C8D0);
            }
            if (v.spec().solar()) {
                g.drawString(font, Component.translatable("hud.interceptors.battery", (int) v.getBattery()), x + 5, y + 62,
                        v.getBattery() < 20 ? 0xFF6060 : 0x80D080);
            }
        }

        warnings(g, font, v, x, y + h + 4, blink);
        if (v.getControllingPassenger() != mc.player) {
            // Passengers: how to reach the laptop in front of them.
            Component hint = Component.translatable("hud.interceptors.passenger_hint", com.interceptors.client.Keys.DISPLAY.getTranslatedKeyMessage());
            int hx = g.guiWidth() / 2 - font.width(hint) / 2, hy = g.guiHeight() - 62;
            g.fill(hx - 4, hy - 2, hx + font.width(hint) + 4, hy + 10, 0xA0101418);
            g.drawString(font, hint, hx, hy, 0x30E0FF);
        }
    }

    /** One warning light per part that's wearing out or broken. */
    private static void warnings(GuiGraphics g, Font font, InterceptorVehicle v, int x, int y, boolean blink) {
        int line = 0;
        if (v.getGrime() >= InterceptorVehicle.GRIME_WARN) {
            boolean jammed = v.getGrime() >= InterceptorVehicle.GRIME_JAM;
            Component name = Component.translatable(jammed ? "hud.interceptors.grime_jammed" : "hud.interceptors.grime", (int) v.getGrime());
            g.fill(x, y, x + font.width(name) + 14, y + 10, 0xC0101418);
            if (!jammed || blink) g.fill(x + 2, y + 2, x + 8, y + 8, jammed ? 0xFFFF3030 : 0xFFB07030);
            g.drawString(font, name, x + 11, y + 1, jammed ? 0xFF3030 : 0xD09050);
            line++;
        }
        for (Part p : Part.values()) {
            int c = v.condition(p);
            if (!Part.warning(c)) continue;
            boolean failed = Part.failed(c);
            int color = failed ? 0xFF3030 : 0xFFB020;
            Component name = Component.translatable(failed ? "hud.interceptors.failed" : "hud.interceptors.worn", Component.translatable("part.interceptors." + p.id()));
            int ly = y + line * 11;
            g.fill(x, ly, x + font.width(name) + 14, ly + 10, 0xC0101418);
            if (!failed || blink) g.fill(x + 2, ly + 2, x + 8, ly + 8, 0xFF000000 | color);
            g.drawString(font, name, x + 11, ly + 1, color);
            line++;
        }
    }
}
