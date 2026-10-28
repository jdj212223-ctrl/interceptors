package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.compat.Storms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * The rental car's stock radio: a handful of FM stations to flip through, and the emergency alert breaking in
 * when a tornado is close. Not much of a storm tool, which is the point.
 */
public class RadioApp implements App {
    private static final String[][] STATIONS = {
            {"88.1", "KSTM Weather Radio", "Forecast for the plains..."}, {"94.7", "Country 94.7", "Dust on the Dashboard"},
            {"101.5", "Classic Rock", "Ridin' the Storm Out"}, {"105.3", "Chase Talk Live", "callers: 'where's the hook?'"}};
    private static int station;

    @Override
    public String id() {
        return "radio";
    }

    @Override
    public int color() {
        return 0x606A78;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        g.fill(x, y, x + w, y + h, 0xFF1E2430);
        String[] s = STATIONS[station];
        g.drawString(font, "FM", x + 8, y + 8, 0xFF9AA4B4, false);
        g.pose().pushPose();
        g.pose().translate(x + 26, y + 6, 0);
        g.pose().scale(3, 3, 1);
        g.drawString(font, s[0], 0, 0, 0xFFE8ECF0, false);
        g.pose().popPose();
        g.drawString(font, s[1], x + 8, y + 36, 0xFFE8ECF0, false);
        g.drawString(font, "♪ " + s[2], x + 8, y + 48, 0xFF9AA4B4, false);
        for (int i = 0; i < STATIONS.length; i++) {
            int bx = x + 8 + i * ((w - 16) / STATIONS.length), bw = (w - 16) / STATIONS.length - 4;
            g.fill(bx, y + h - 26, bx + bw, y + h - 8, i == station ? 0xFFDDE4EE : 0xFF2A303C);
            g.drawString(font, STATIONS[i][0], bx + bw / 2 - font.width(STATIONS[i][0]) / 2, y + h - 21, i == station ? 0xFF1E2430 : 0xFFDDE4EE, false);
        }
        Storms.StormInfo t = Storms.nearestTornado(mc.level, screen.here());
        if (t != null && t.distance() < 5000 && mc.level.getGameTime() / 10 % 2 == 0) {
            String alert = "EMERGENCY ALERT: TORNADO WARNING. TAKE SHELTER NOW.";
            g.fill(x, y + 60, x + w, y + 74, 0xFFC01818);
            g.drawString(font, alert, x + w / 2 - font.width(alert) / 2, y + 63, 0xFFFFFFFF, false);
        }
    }

    private void tune(int to) {
        station = Math.floorMod(to, STATIONS.length);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.3f));
    }

    @Override
    public boolean mouseClicked(DeviceScreen screen, double mx, double my, int button) {
        int x = screen.appX(), w = screen.appW(), y = screen.appY() + screen.appH();
        if (my < y - 26 || my > y - 8) return false;
        int i = (int) ((mx - x - 8) / ((w - 16) / STATIONS.length));
        if (i < 0 || i >= STATIONS.length) return false;
        tune(i);
        return true;
    }

    @Override
    public boolean keyPressed(DeviceScreen screen, int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            tune(station + (key == GLFW.GLFW_KEY_RIGHT ? 1 : -1));
            return true;
        }
        return false;
    }
}
