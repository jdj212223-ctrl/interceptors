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
        // Inside the video frame when the stream overlay is on.
        int x = BroadcastHud.enabled ? 10 : 6, y = BroadcastHud.enabled ? BroadcastHud.TOP + 10 : 6, w = 168, h = 74;
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
        lexan(g, font, mc, v, blink);
        if (v.getControllingPassenger() != mc.player) {
            // Passengers: how to reach the laptop in front of them.
            Component hint = Component.translatable("hud.interceptors.passenger_hint", com.interceptors.client.Keys.DISPLAY.getTranslatedKeyMessage());
            int hx = g.guiWidth() / 2 - font.width(hint) / 2, hy = g.guiHeight() - (BroadcastHud.enabled ? BroadcastHud.BOTTOM + 40 : 62);
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

    /** Windows-health last frame, to catch the moment the glass blows in. */
    private static int lastWindows = 100;
    /** When the glass blew in (game time), for the red flash. */
    private static long burstAt = -100;

    /** The server says our windows just blew in on us. */
    public static void glassBurst() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) burstAt = mc.level.getGameTime();
    }

    /**
     * The Lexan shield: its state, a flashing RAISE LEXAN! when a tornado is close with the shield down, and a red
     * flash for the front-seat riders when the windows blow in.
     */
    private static void lexan(GuiGraphics g, Font font, Minecraft mc, InterceptorVehicle v, boolean blink) {
        long now = mc.level.getGameTime();
        int windows = v.condition(Part.WINDOWS);
        lastWindows = windows;
        if (now - burstAt < 25) {
            // A red vignette closing in from the edges, fading over about a second.
            float fade = 1 - (now - burstAt) / 25f;
            int gw = g.guiWidth(), gh = g.guiHeight(), depth = Math.min(gw, gh) / 4;
            for (int i = 0; i < depth; i += 2) {
                int alpha = (int) (170 * fade * (1 - i / (float) depth));
                int c = alpha << 24 | 0xB00808;
                g.fill(i, i, gw - i, i + 2, c);
                g.fill(i, gh - i - 2, gw - i, gh - i, c);
                g.fill(i, i + 2, i + 2, gh - i - 2, c);
                g.fill(gw - i - 2, i + 2, gw - i, gh - i - 2, c);
            }
            g.fill(0, 0, gw, gh, (int) (50 * fade) << 24 | 0xB00808);
        }
        if (!v.spec().hasLexan()) return;
        int x = BroadcastHud.enabled ? 10 : 6, y = (BroadcastHud.enabled ? BroadcastHud.TOP + 10 : 6) + 74 + 2;
        Component state = Component.translatable(v.lexanUp() ? "hud.interceptors.lexan_up" : "hud.interceptors.lexan_down",
                com.interceptors.client.Keys.LEXAN.getTranslatedKeyMessage());
        g.fill(x, y, x + font.width(state) + 8, y + 11, 0xC0101418);
        g.drawString(font, state, x + 4, y + 2, v.lexanUp() ? 0x80D080 : 0xC0C8D0);

        Storms.StormInfo t = Storms.nearestTornado(mc.level, v.position());
        boolean danger = v.lastWind().horizontalDistance() > 80 || t != null && t.distance() < 600;
        if (!v.lexanUp() && danger && !Part.failed(windows) && blink) {
            Component warn = Component.translatable("hud.interceptors.raise_lexan", com.interceptors.client.Keys.LEXAN.getTranslatedKeyMessage());
            int cx = g.guiWidth() / 2, cy = g.guiHeight() / 3;
            g.pose().pushPose();
            g.pose().translate(cx - font.width(warn), cy, 0);
            g.pose().scale(2, 2, 1);
            g.fill(-4, -3, font.width(warn) + 4, 11, 0xD0000000);
            g.drawString(font, warn, 0, 0, 0xFF3030, true);
            g.pose().popPose();
        }
    }
}
