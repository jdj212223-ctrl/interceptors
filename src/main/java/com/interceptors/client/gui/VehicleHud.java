package com.interceptors.client.gui;

import com.interceptors.compat.Storms;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The basic car display: speed, armor, anchors, wind, and where the nearest tornado is. */
public final class VehicleHud {
    private VehicleHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !(mc.player.getVehicle() instanceof InterceptorVehicle v)) return;
        Font font = mc.font;
        int x = 6, y = 6, w = 168, h = 74;
        g.fill(x, y, x + w, y + h, 0xB0101418);
        g.fill(x, y, x + w, y + 1, 0xFF30E0FF);

        g.drawString(font, Component.translatable("entity.interceptors." + v.spec().id()), x + 5, y + 4, 0xFFFFFF);
        int mph = (int) Math.round(WindPhysics.toMph(Math.abs(v.getSpeed())));
        g.drawString(font, mph + " mph", x + w - 5 - font.width(mph + " mph"), y + 4, 0x30E0FF);

        // armor bar
        float hp = v.healthFraction();
        g.fill(x + 5, y + 16, x + w - 5, y + 20, 0xFF303030);
        g.fill(x + 5, y + 16, x + 5 + (int) ((w - 10) * hp), y + 20, hp > 0.5f ? 0xFF40D060 : hp > 0.25f ? 0xFFE0B020 : 0xFFE03030);

        Component anchors = Component.translatable(v.isAnchored() ? "hud.interceptors.anchored" : "hud.interceptors.not_anchored");
        g.drawString(font, anchors, x + 5, y + 24, v.isAnchored() ? 0xFF8040 : 0x9AA0A6);

        double wind = v.lastWind().horizontalDistance();
        int ef = WindPhysics.efRating(wind);
        int windColor = wind > WindPhysics.hold(v.spec(), v.isAnchored()) ? 0xFF4040 : wind > 50 ? 0xFFC040 : 0xC0C8D0;
        g.drawString(font, Component.translatable("hud.interceptors.wind", (int) wind, ef < 0 ? "" : " (EF" + ef + ")"), x + 5, y + 35, windColor);

        Storms.StormInfo t = Storms.nearestTornado(mc.level, v.position());
        if (t == null) {
            g.drawString(font, Component.translatable("hud.interceptors.no_tornado"), x + 5, y + 48, 0x9AA0A6);
        } else {
            g.drawString(font, Component.translatable("hud.interceptors.tornado", t.efLabel(), t.windspeed(), (int) t.distance(),
                    Storms.compassName(t.bearing())), x + 5, y + 48, 0xFF5050);
            String motion = Double.isNaN(t.heading()) ? "--" : Storms.compassName(t.heading()) + " " + (int) t.speed() + " mph";
            g.drawString(font, Component.translatable("hud.interceptors.motion", motion, (int) t.width()), x + 5, y + 59, 0xC0C8D0);
        }
    }
}
