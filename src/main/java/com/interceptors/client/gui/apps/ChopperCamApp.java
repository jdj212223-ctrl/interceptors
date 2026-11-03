package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.client.gui.Draw;
import com.interceptors.compat.Storms;
import com.interceptors.vehicle.StormChopper;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Storm Chopper's live feed for the ground team: a top-down sky view around the nearest chopper with the
 * storms, tornadoes and you on it, plus the chopper's altitude, speed and heading.
 */
public class ChopperCamApp implements App {
    private static final double RANGE = 1500;

    @Override
    public boolean needsInternet() {
        return true;
    }

    @Override
    public String id() {
        return "chopper";
    }

    @Override
    public int color() {
        return 0x2050C0;
    }

    static StormChopper nearestChopper(Minecraft mc) {
        StormChopper best = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof StormChopper c && (best == null || c.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) best = c;
        }
        return best;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        g.fill(x, y, x + w, y + h, 0xFF16201A);
        StormChopper c = nearestChopper(mc);
        if (c == null) {
            for (var line : font.split(Component.translatable("app.interceptors.chopper.none"), w - 16)) {
                g.drawString(font, line, x + 8, y + 10, 0x9AA0A6, false);
                y += 10;
            }
            return;
        }
        Vec3 at = c.position();
        int size = Math.min(w, h) - 24, left = x + (w - size) / 2, top = y + 14;
        float cx = left + size / 2f, cy = top + size / 2f, r = size / 2f;
        // Grainy sky-cam ground, with grid lines every 250 m.
        long time = mc.level.getGameTime();
        for (int i = 0; i < 160; i++) {
            int seed = (int) (i * 7919 + time / 2 * 31);
            Draw.dot(g, left + Math.floorMod(seed, size), top + Math.floorMod(seed / 7, size), 1, 0x30FFFFFF);
        }
        for (int k = -6; k <= 6; k++) {
            float off = (float) (k * 250 / RANGE * r);
            Draw.line(g, cx + off, top, cx + off, top + size, 1, 0x18FFFFFF);
            Draw.line(g, left, cy + off, left + size, cy + off, 1, 0x18FFFFFF);
        }
        for (Storms.StormInfo s : Storms.around(mc.level, at)) {
            float px = cx + (float) ((s.position().x - at.x) / RANGE * r), py = cy + (float) ((s.position().z - at.z) / RANGE * r);
            if (Math.abs(px - cx) > r || Math.abs(py - cy) > r) continue;
            if (s.tornado()) {
                Draw.disc(g, px, py, Math.max(4, (float) (s.width() / 2 / RANGE * r)), 0xD0FF3030);
                g.drawString(font, s.efLabel(), (int) px + 6, (int) py - 4, 0xFF6060, true);
                Draw.line(g, cx, cy, px, py, 1, 0x80FFC040);
            } else {
                Draw.ring(g, px, py, 6, 0x90C0FFC0);
            }
        }
        Vec3 you = mc.player.position();
        float yx = cx + (float) ((you.x - at.x) / RANGE * r), yy = cy + (float) ((you.z - at.z) / RANGE * r);
        if (Math.abs(yx - cx) < r && Math.abs(yy - cy) < r) Draw.dot(g, yx, yy, 4, 0xFF30E0FF);
        Draw.arrow(g, cx, cy, 7, ((c.getYRot() + 180) % 360 + 360) % 360, 2, 0xFFFFFFFF);

        boolean blink = time / 10 % 2 == 0;
        g.drawString(font, Component.translatable("app.interceptors.chopper.live"), x + 6, y + 3, blink ? 0xFF3030 : 0xFFFFFF, true);
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) at.x, (int) at.z);
        String tele = net.minecraft.client.resources.language.I18n.get("app.interceptors.chopper.telemetry", Math.max(0, (int) (at.y - ground)),
                Math.round(WindPhysics.toMph(c.getDeltaMovement().horizontalDistance())),
                Storms.compassLabel(((c.getYRot() + 180) % 360 + 360) % 360), (int) at.distanceTo(you));
        g.drawString(font, tele, x + 6, y + h - 10, 0xE0E0E0, false);
        Storms.StormInfo t = Storms.nearestTornado(mc.level, at);
        if (t != null) {
            String feed = net.minecraft.client.resources.language.I18n.get("app.interceptors.chopper.feed", t.efLabel(), (int) t.distance(), Storms.compassLabel(t.bearing()));
            g.drawString(font, feed, x + w - font.width(feed) - 6, y + 3, 0xFF8080, false);
        }
    }
}
