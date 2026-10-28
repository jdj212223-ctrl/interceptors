package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.client.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** The Doppler on Wheels operator's console: the same radar, with the dish's rotating sweep and its readouts. */
public class RadarConsoleApp extends RadarApp {
    @Override
    public String id() {
        return "console";
    }

    @Override
    public int color() {
        return 0x1080C0;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        super.render(g, screen, x, y, w, h, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        int size = Math.min(w, h) - 6;
        float cx = x + w / 2f, cy = y + h / 2f, r = size / 2f;
        float az = ((mc.level.getGameTime() + partialTick) * 6) % 360;
        // The sweep and its fading trail, like the dish turning.
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(az - k * 3);
            int alpha = (int) (200 * (1 - k / 12f));
            Draw.line(g, cx, cy, cx + (float) Math.sin(a) * r, cy - (float) Math.cos(a) * r, k == 0 ? 2 : 1, alpha << 24 | 0x30E0FF);
        }
        String readout = String.format("DOW  PPI  AZ %03d°  EL 1.0°  PRF 1500", Math.round(az));
        g.fill(x + w - mc.font.width(readout) - 8, y + 2, x + w - 2, y + 12, 0xC0061024);
        g.drawString(mc.font, readout, x + w - mc.font.width(readout) - 5, y + 3, 0xFF30E0FF, false);
    }
}
