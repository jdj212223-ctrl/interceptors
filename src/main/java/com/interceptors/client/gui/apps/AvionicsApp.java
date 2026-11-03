package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.client.gui.Draw;
import com.interceptors.compat.Storms;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.StormChopper;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Storm Chopper's glass cockpit: artificial horizon (pitch and bank), an airspeed tape on the left, altitude
 * above the ground on the right, a heading tape across the top and the climb rate.
 */
public class AvionicsApp implements App {
    @Override
    public String id() {
        return "avionics";
    }

    @Override
    public int color() {
        return 0x208040;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        g.fill(x, y, x + w, y + h, 0xFF050608);
        InterceptorVehicle v = screen.vehicle();
        float pitch = v instanceof StormChopper c ? c.tilt() : 0, bank = v != null ? v.steer(partialTick) * 0.8f : 0;
        double speed = v != null ? WindPhysics.toMph(v.getDeltaMovement().horizontalDistance()) : 0;
        double climb = v != null ? v.getDeltaMovement().y * 20 : 0;
        double heading = ((mc.player.getYRot() + 180) % 360 + 360) % 360;
        if (v != null) heading = ((v.getYRot() + 180) % 360 + 360) % 360;
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) screen.here().x, (int) screen.here().z);
        int alt = v != null ? Math.max(0, (int) (v.getY() - ground)) : 0;

        // Artificial horizon: sky above, ground below, tilted by bank and moved by pitch.
        int r = Math.min(w / 3, h / 2 - 12);
        float cx = x + w / 2f, cy = y + h / 2f + 6;
        double b = Math.toRadians(bank);
        float shift = pitch * 2;
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            for (int dx = -half; dx <= half; dx += 2) {
                double up = -dx * Math.sin(b) + dy * Math.cos(b) - shift;
                g.fill((int) cx + dx, (int) cy + dy, (int) cx + dx + 2, (int) cy + dy + 1, up < 0 ? 0xFF2A70C8 : 0xFF7A5030);
            }
        }
        Draw.line(g, cx - 20, cy, cx - 6, cy, 2, 0xFFFFD020);
        Draw.line(g, cx + 6, cy, cx + 20, cy, 2, 0xFFFFD020);
        Draw.dot(g, cx, cy, 3, 0xFFFFD020);
        Draw.ring(g, cx, cy, r, 0xFF808890);

        // Airspeed (left) and altitude (right) tapes.
        int tapeW = 34;
        tape(g, font, x + 6, y + 18, tapeW, h - 24, "IAS", Math.round(speed) + "", 0xFF30FF80);
        tape(g, font, x + w - tapeW - 6, y + 18, tapeW, h - 24, "ALT m", alt + "", 0xFF30FF80);
        // Heading tape.
        g.fill(x + tapeW + 12, y + 2, x + w - tapeW - 12, y + 14, 0xFF101418);
        String hdg = String.format("HDG %03d° %s", Math.round(heading), Storms.compassLabel(heading));
        g.drawString(font, hdg, (int) cx - font.width(hdg) / 2, y + 4, 0xFFFFFFFF, false);
        String vs = String.format("VS %+.1f m/s", climb);
        g.drawString(font, vs, (int) cx - font.width(vs) / 2, (int) cy + r + 4, climb < -4 ? 0xFFFF5050 : 0xFF30FF80, false);
        double wind = v != null ? v.lastWind().horizontalDistance() : 0;
        String turbulence = Component.translatable("app.interceptors.avionics.turbulence").getString();
        if (wind > 70) g.drawString(font, turbulence, (int) cx - font.width(turbulence) / 2, (int) cy - r - 12, 0xFFFF5050, false);
    }

    private static void tape(GuiGraphics g, Font font, int x, int y, int w, int h, String label, String value, int color) {
        g.fill(x, y, x + w, y + h, 0xFF101418);
        for (int i = 0; i < h; i += 8) g.fill(x + w - 6, y + i, x + w, y + i + 1, 0xFF404850);
        g.drawString(font, label, x + 2, y + 2, 0xFF808890, false);
        g.fill(x, y + h / 2 - 6, x + w, y + h / 2 + 6, 0xFF000000);
        g.drawString(font, value, x + w / 2 - font.width(value) / 2, y + h / 2 - 4, color, false);
    }
}
