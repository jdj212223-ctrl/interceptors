package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.client.gui.Draw;
import com.interceptors.compat.Storms;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.Part;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The car display's home screen: speedometer, compass, armor, anchors and what the wind is doing to you. */
public class VehicleApp implements App {
    @Override
    public String id() {
        return "vehicle";
    }

    @Override
    public int color() {
        return 0x30A0C0;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        com.interceptors.client.gui.DashTheme t = screen.theme();
        int accent = t == null ? 0xFF30E0FF : t.accent(Minecraft.getInstance().level.getGameTime());
        g.fill(x, y, x + w, y + h, t == null ? 0xFF0B0F14 : t.background);
        InterceptorVehicle v = screen.vehicle();
        if (v == null) {
            g.drawString(font, Component.translatable("app.interceptors.vehicle.none"), x + 6, y + 6, 0x9AA0A6, false);
            return;
        }
        int dial = Math.min(h - 14, w / 3) / 2;
        int cx = x + 8 + dial, cy = y + 6 + dial;
        Draw.ring(g, cx, cy, dial, t == null ? 0xFF3A4458 : accent & 0x80FFFFFF);
        double mph = WindPhysics.toMph(Math.abs(v.getSpeed()));
        double topMph = WindPhysics.toMph(v.spec().maxSpeed());
        // Needle sweeps 240 degrees, like a real speedometer.
        Draw.arrow(g, cx, cy, dial * 0.8f, -120 + 240 * Math.min(1, mph / topMph), 2, accent);
        String speed = (int) Math.round(mph) + "";
        g.drawString(font, speed, cx - font.width(speed) / 2, cy + dial / 2, 0xFFFFFF, true);
        g.drawString(font, "mph", cx - font.width("mph") / 2, cy + dial / 2 + 10, 0x8090A8, false);

        int tx = x + 20 + dial * 2, line = y + 6;
        String name = Component.translatable("entity.interceptors." + v.spec().id()).getString();
        g.drawString(font, t == null ? name : t.format(name), tx, line, t == null ? 0xFFFFFF : t.text, true);
        line += 13;
        double heading = ((v.getYRot() + 180) % 360 + 360) % 360;
        g.drawString(font, Component.translatable("app.interceptors.vehicle.heading", Storms.compassLabel(heading), (int) heading), tx, line, 0xC0C8D0, false);
        line += 12;
        float hp = v.healthFraction();
        g.drawString(font, Component.translatable("app.interceptors.vehicle.armor", (int) (hp * 100)), tx, line, 0xC0C8D0, false);
        g.fill(tx + 70, line + 2, tx + 150, line + 6, 0xFF303030);
        g.fill(tx + 70, line + 2, tx + 70 + (int) (80 * hp), line + 6, hp > 0.5f ? 0xFF40D060 : hp > 0.25f ? 0xFFE0B020 : 0xFFE03030);
        line += 12;
        double wind = v.lastWind().horizontalDistance();
        double hold = WindPhysics.hold(v.spec(), v.isAnchored());
        g.drawString(font, Component.translatable("app.interceptors.vehicle.wind", (int) wind, (int) hold), tx, line,
                wind > hold ? 0xFF4040 : wind > hold * 0.7 ? 0xFFC040 : 0xC0C8D0, false);
        line += 12;
        StringBuilder parts = new StringBuilder();
        for (Part p : Part.values()) {
            int c = v.condition(p);
            if (parts.length() > 0) parts.append("  ");
            parts.append(Component.translatable("part.interceptors." + p.id()).getString().charAt(0)).append(' ').append(c).append('%');
        }
        g.drawString(font, Component.translatable("app.interceptors.vehicle.parts", parts.toString()), tx, line, 0xA0A8B0, false);
        line += 12;
        g.drawString(font, Component.translatable("app.interceptors.vehicle.anchors", Component.translatable(v.anchorStatus().key()), Component.translatable("anchor_kind.interceptors." + v.spec().anchorKind().replace(" ", "_"))),
                tx, line, v.anchorStatus().color(), false);
        line += 12;
        if (wind > v.spec().ratedWind()) {
            g.drawString(font, Component.translatable("app.interceptors.vehicle.damage"), tx, line, 0xFF4040, true);
        } else if (wind > hold) {
            g.drawString(font, Component.translatable(v.isAnchored() ? "app.interceptors.vehicle.slipping" : "app.interceptors.vehicle.drop_anchors"), tx, line, 0xFF8040, true);
        }
    }
}
