package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/**
 * Real web pages need a real browser, which only the optional MCEF mod brings. Without it this app explains
 * what to install, instead of quietly missing.
 */
public class WebApp implements App {
    @Override
    public boolean needsInternet() {
        return true;
    }

    @Override
    public String id() {
        return "web";
    }

    @Override
    public int color() {
        return 0x2A8AE0;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        g.fill(x, y, x + w, y + h, 0xFFF2F4F7);
        boolean mcef = ModList.get().isLoaded("mcef");
        int line = y + 8;
        g.drawString(font, Component.translatable("app.interceptors.web.title"), x + 8, line, 0x202428, false);
        line += 16;
        String key = mcef ? "app.interceptors.web.soon" : "app.interceptors.web.missing";
        for (var part : font.split(Component.translatable(key), w - 16)) {
            g.drawString(font, part, x + 8, line, 0x404850, false);
            line += 10;
        }
    }
}
