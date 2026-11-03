package com.interceptors.client.gui;

import com.interceptors.client.InjuryState;
import com.interceptors.client.Keys;
import com.interceptors.event.Injuries;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A warning while a hailstone is stuck in you (leave it!), and a red one while you're bleeding. */
public final class InjuryHud {
    private InjuryHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        InjuryState.Injury injury = InjuryState.of(mc.player);
        if (injury == null) return;
        long now = mc.level.getGameTime();
        Font font = mc.font;
        int y = g.guiHeight() / 2 + 30;
        if (InjuryState.lodged(mc.player, now)) {
            int secs = (int) ((injury.lodgedUntil() - now) / 20);
            Component part = Component.translatable("injury.interceptors." + Injuries.PARTS[injury.lodgedPart()]);
            line(g, font, Component.translatable("hud.interceptors.hail_lodged", part, secs), y, 0xA0E8FF);
            line(g, font, Component.translatable("hud.interceptors.hail_leave_it", Keys.PULL_HAIL.getTranslatedKeyMessage()), y + 12, 0xFFC040);
        } else if (InjuryState.bleeding(mc.player, now) && now / 8 % 2 == 0) {
            line(g, font, Component.translatable("hud.interceptors.bleeding"), y, 0xFF4040);
        }
    }

    private static void line(GuiGraphics g, Font font, Component text, int y, int color) {
        int x = g.guiWidth() / 2 - font.width(text) / 2;
        g.fill(x - 4, y - 2, x + font.width(text) + 4, y + 10, 0xB0101418);
        g.drawString(font, text, x, y, color);
    }
}
