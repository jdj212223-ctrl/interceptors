package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.client.gui.Draw;
import com.interceptors.compat.Storms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The chaser's best friend: a big arrow to the nearest tornado, how far, how fast it's coming, and how scared to be. */
public class InterceptApp implements App {
    @Override
    public String id() {
        return "intercept";
    }

    @Override
    public int color() {
        return 0xD03030;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        g.fill(x, y, x + w, y + h, 0xFF0D1016);
        Vec3 here = screen.here();
        List<Storms.StormInfo> storms = Storms.around(mc.level, here);
        Storms.StormInfo t = storms.stream().filter(Storms.StormInfo::tornado).findFirst().orElse(null);
        Storms.StormInfo target = t != null ? t : storms.isEmpty() ? null : storms.getFirst();

        int dial = Math.min(h - 30, w / 2 - 10) / 2;
        int cx = x + 12 + dial, cy = y + 8 + dial;
        Draw.disc(g, cx, cy, dial, 0xFF161B24);
        Draw.ring(g, cx, cy, dial, 0xFF3A4458);
        g.drawString(font, "↑", cx - 2, cy - dial + 2, 0x707A8C, false);

        int tx = x + 24 + dial * 2;
        if (target == null) {
            g.drawString(font, Component.translatable("app.interceptors.intercept.none"), tx, y + 10, 0x9AA0A6, false);
            g.drawString(font, Component.translatable("app.interceptors.intercept.none_hint"), tx, y + 22, 0x6A707A, false);
            return;
        }

        // The arrow points where the storm is relative to the way you're facing: straight up means drive forward.
        double relative = target.bearing() - screen.facing();
        int arrowColor = t != null ? 0xFFFF4040 : 0xFFFFC040;
        Draw.arrow(g, cx, cy, dial * 0.7f, relative, 3, arrowColor);

        int line = y + 6;
        if (t != null) {
            g.drawString(font, Component.translatable("app.interceptors.intercept.tornado", t.efLabel()), tx, line, 0xFF5050, true);
            line += 12;
            g.drawString(font, Component.translatable("app.interceptors.intercept.wind", t.windspeed()), tx, line, 0xFFFFFF, false);
        } else {
            g.drawString(font, Component.translatable("app.interceptors.intercept.storm", pretty(target.type())), tx, line, 0xFFC040, true);
            line += 12;
            g.drawString(font, Component.translatable("app.interceptors.intercept.storm_hint"), tx, line, 0xC0C8D0, false);
        }
        line += 11;
        g.drawString(font, Component.translatable("app.interceptors.intercept.distance", distance(target.distance()),
                Storms.compassName(target.bearing()), (int) target.bearing()), tx, line, 0xFFFFFF, false);
        line += 11;
        String moving = Double.isNaN(target.heading()) ? "--" : Storms.compassName(target.heading()) + ", " + (int) target.speed() + " mph";
        g.drawString(font, Component.translatable("app.interceptors.intercept.moving", moving), tx, line, 0xC0C8D0, false);
        line += 11;
        double closing = target.closingMph(here);
        if (closing > 1) {
            double seconds = Math.max(0, target.distance() - target.width() / 2) / (closing / 2.23694);
            g.drawString(font, Component.translatable("app.interceptors.intercept.closing", (int) closing,
                    String.format("%d:%02d", (int) seconds / 60, (int) seconds % 60)), tx, line, 0xFFB040, false);
        } else {
            g.drawString(font, Component.translatable("app.interceptors.intercept.away"), tx, line, 0x80C080, false);
        }
        line += 11;
        if (t != null) g.drawString(font, Component.translatable("app.interceptors.intercept.width", (int) t.width()), tx, line, 0xC0C8D0, false);
        line += 11;
        double windHere = Storms.windAt(mc.level, mc.player.getEyePosition()).horizontalDistance();
        g.drawString(font, Component.translatable("app.interceptors.intercept.wind_here", (int) windHere), tx, line, windHere > 65 ? 0xFF6060 : 0xC0C8D0, false);

        // Status banner.
        String key;
        int color;
        double edge = target.distance() - (t != null ? t.width() / 2 : 0);
        if (t == null) {
            key = "chase";
            color = 0xFF2A5A8A;
        } else if (edge < 0) {
            key = "inside";
            color = 0xFFB01010 | ((mc.level.getGameTime() / 5) % 2 == 0 ? 0x00400000 : 0);
        } else if (edge < 250) {
            key = "danger";
            color = 0xFFC03010;
        } else if (edge < 1500) {
            key = "range";
            color = 0xFFC08010;
        } else {
            key = "chase";
            color = 0xFF2A5A8A;
        }
        g.fill(x, y + h - 16, x + w, y + h, color);
        Component banner = Component.translatable("app.interceptors.intercept.status." + key);
        g.drawString(font, banner, x + w / 2 - font.width(banner) / 2, y + h - 12, 0xFFFFFF, true);
    }

    static String distance(double meters) {
        return meters >= 1000 ? String.format("%.1f km", meters / 1000) : (int) meters + " m";
    }

    static String pretty(String type) {
        return type.isEmpty() ? type : Character.toUpperCase(type.charAt(0)) + type.substring(1).replace('_', ' ');
    }
}
