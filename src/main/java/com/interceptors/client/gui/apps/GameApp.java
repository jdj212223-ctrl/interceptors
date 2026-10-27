package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Tornado Dodge: steer the van left and right, don't get picked up. For long waits for storms to fire. */
public class GameApp implements App {
    private static int best;

    private final RandomSource random = RandomSource.create();
    private final List<float[]> tornadoes = new ArrayList<>(); // x, y, speed, size (all 0..1 of the play area)
    private float van = 0.5f;
    private int score, ticks;
    private boolean over, started;

    @Override
    public String id() {
        return "game";
    }

    @Override
    public int color() {
        return 0xA040F0;
    }

    private static boolean down(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
    }

    @Override
    public void tick(DeviceScreen screen) {
        if (!started || over) return;
        ticks++;
        float steer = (down(GLFW.GLFW_KEY_LEFT) || down(GLFW.GLFW_KEY_A) ? -1 : 0) + (down(GLFW.GLFW_KEY_RIGHT) || down(GLFW.GLFW_KEY_D) ? 1 : 0);
        van = Mth.clamp(van + steer * 0.035f, 0.06f, 0.94f);
        if (ticks % Math.max(5, 22 - score / 4) == 0) {
            tornadoes.add(new float[]{0.05f + random.nextFloat() * 0.9f, -0.15f, 0.008f + random.nextFloat() * 0.006f + score * 0.0003f, 0.04f + random.nextFloat() * 0.05f});
        }
        for (var it = tornadoes.iterator(); it.hasNext(); ) {
            float[] t = it.next();
            t[1] += t[2];
            t[0] += Mth.sin((ticks + t[3] * 500) * 0.12f) * 0.004f;
            if (t[1] > 0.84f && t[1] < 0.98f && Math.abs(t[0] - van) < t[3] + 0.04f) {
                over = true;
                best = Math.max(best, score);
            }
            if (t[1] > 1.1f) {
                it.remove();
                score++;
            }
        }
    }

    private void restart() {
        tornadoes.clear();
        van = 0.5f;
        score = ticks = 0;
        over = false;
        started = true;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < h; i++) {
            int c = com.interceptors.client.gui.Draw.lerpColor(0x2A3448, 0x6A7488, i / (float) h);
            g.fill(x, y + i, x + w, y + i + 1, c);
        }
        int ground = y + (int) (h * 0.82f);
        g.fill(x, ground, x + w, y + h, 0xFF3A5A2A);
        g.fill(x, ground + 6, x + w, ground + 16, 0xFF2A2A2E);
        for (int i = 0; i < w; i += 20) g.fill(x + i + (ticks * 3) % 20, ground + 10, x + i + 10 + (ticks * 3) % 20, ground + 12, 0xFFE0D040);

        for (float[] t : tornadoes) {
            int tx = x + (int) (t[0] * w), ty = y + (int) (t[1] * h), half = (int) (t[3] * w);
            for (int s = 0; s < 8; s++) {
                int sw = Math.max(1, half - s * half / 9);
                int wobble = (int) (Mth.sin((ticks + s * 3 + partialTick) * 0.5f) * 2);
                g.fill(tx - sw + wobble, ty - 30 + s * 4, tx + sw + wobble, ty - 26 + s * 4, 0xE0505860);
            }
        }

        int vx = x + (int) (van * w), vy = ground - 2;
        g.fill(vx - 10, vy - 9, vx + 10, vy + 2, 0xFFEDEFF0);
        g.fill(vx + 3, vy - 7, vx + 9, vy - 3, 0xFF22303C);
        int rgb = Mth.hsvToRgb((ticks % 60) / 60f, 0.8f, 1f);
        g.fill(vx - 10, vy, vx + 10, vy + 1, 0xFF000000 | rgb);
        g.fill(vx - 8, vy + 2, vx - 4, vy + 5, 0xFF111111);
        g.fill(vx + 4, vy + 2, vx + 8, vy + 5, 0xFF111111);

        g.drawString(font, Component.translatable("app.interceptors.game.score", score, best), x + 4, y + 4, 0xFFFFFF, true);
        Component msg = !started ? Component.translatable("app.interceptors.game.start")
                : over ? Component.translatable("app.interceptors.game.over", score) : null;
        if (msg != null) {
            g.fill(x + w / 2 - font.width(msg) / 2 - 6, y + h / 2 - 12, x + w / 2 + font.width(msg) / 2 + 6, y + h / 2 + 4, 0xC0000000);
            g.drawString(font, msg, x + w / 2 - font.width(msg) / 2, y + h / 2 - 8, 0xFFFFFF, true);
        }
    }

    @Override
    public boolean keyPressed(DeviceScreen screen, int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            if (!started || over) restart();
            return true;
        }
        return key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_D;
    }

    @Override
    public boolean mouseClicked(DeviceScreen screen, double mouseX, double mouseY, int button) {
        if (!started || over) restart();
        return true;
    }
}
