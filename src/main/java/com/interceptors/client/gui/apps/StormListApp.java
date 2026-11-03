package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.compat.Storms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Every storm PMWeather knows about, tornadoes first, nearest first. */
public class StormListApp implements App {
    private int scroll;

    @Override
    public boolean needsInternet() {
        return true;
    }

    @Override
    public String id() {
        return "storms";
    }

    @Override
    public int color() {
        return 0x3A6FD8;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        g.fill(x, y, x + w, y + h, 0xFF0E1218);
        int[] cols = {x + 4, x + w * 30 / 100, x + w * 48 / 100, x + w * 64 / 100, x + w * 76 / 100};
        String[] heads = {"type", "strength", "distance", "dir", "moving"};
        for (int i = 0; i < cols.length; i++) {
            g.drawString(font, Component.translatable("app.interceptors.storms." + heads[i]), cols[i], y + 4, 0x8090A8, false);
        }
        g.fill(x + 2, y + 14, x + w - 2, y + 15, 0xFF2A3240);

        List<Storms.StormInfo> storms = Storms.around(Minecraft.getInstance().level, screen.here());
        if (storms.isEmpty()) {
            g.drawString(font, Component.translatable("app.interceptors.storms.empty"), x + 4, y + 20, 0x9AA0A6, false);
            return;
        }
        int rows = (h - 18) / 11;
        scroll = Math.clamp(scroll, 0, Math.max(0, storms.size() - rows));
        for (int i = 0; i < rows && i + scroll < storms.size(); i++) {
            Storms.StormInfo s = storms.get(i + scroll);
            int ry = y + 18 + i * 11;
            int color = s.tornado() ? 0xFF6060 : 0xD0D8E0;
            g.drawString(font, s.tornado() ? Component.translatable("app.interceptors.storms.tornado").getString() : InterceptApp.pretty(s.type()), cols[0], ry, color, false);
            g.drawString(font, s.tornado() ? s.efLabel() + " " + s.windspeed() + " mph" : Component.translatable("app.interceptors.storms.stage", s.stage()).getString(), cols[1], ry, color, false);
            g.drawString(font, InterceptApp.distance(s.distance()), cols[2], ry, 0xD0D8E0, false);
            g.drawString(font, Storms.compassLabel(s.bearing()), cols[3], ry, 0xD0D8E0, false);
            g.drawString(font, Double.isNaN(s.heading()) ? "--" : Storms.compassLabel(s.heading()) + " " + (int) s.speed(), cols[4], ry, 0xA0A8B0, false);
        }
    }

    @Override
    public boolean mouseScrolled(DeviceScreen screen, double mouseX, double mouseY, double amount) {
        scroll -= (int) Math.signum(amount);
        return true;
    }
}
