package com.interceptors.client.gui;

import com.interceptors.client.gui.apps.App;
import com.interceptors.client.Keys;
import com.interceptors.client.gui.apps.Apps;
import com.interceptors.device.Device;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.VehicleSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * StormOS: the same storm-chasing apps on every device, drawn inside that device's frame (monitor, laptop,
 * MacBook, iPad or car dashboard). It never pauses the game, so storms keep moving while you look.
 */
public class DeviceScreen extends Screen {
    private static final Map<Device, String> LAST_APP = new EnumMap<>(Device.class);
    private static final int BAR = 11, DOCK = 24;

    public final Device device;
    private final List<App> apps;
    private int current;
    /** The display area inside the bezel. */
    private int sx, sy, sw, sh;

    public DeviceScreen(Device device) {
        super(Component.translatable("device.interceptors." + device.id()));
        this.device = device;
        this.apps = Apps.forDevice(device, this);
        String last = LAST_APP.get(device);
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).id().equals(last)) current = i;
    }

    // ------------------------------------------------------------------ what apps can ask

    @Nullable
    public InterceptorVehicle vehicle() {
        return minecraft != null && minecraft.player != null && minecraft.player.getVehicle() instanceof InterceptorVehicle v ? v : null;
    }

    /** The Doppler on Wheels' dish lets every screen inside it see twice as far. */
    public double rangeBoost() {
        InterceptorVehicle v = vehicle();
        return v != null && v.spec() == VehicleSpec.DOPPLER_ON_WHEELS ? 2 : 1;
    }

    public Vec3 here() {
        return minecraft.player.position();
    }

    /** Which way the player faces, degrees clockwise from north. */
    public double facing() {
        return ((minecraft.player.getYRot() + 180) % 360 + 360) % 360;
    }

    public int appX() {
        return sx;
    }

    public int appY() {
        return sy + BAR;
    }

    public int appW() {
        return sw;
    }

    public int appH() {
        return sh - BAR - DOCK;
    }

    public boolean inApp(double mx, double my) {
        return mx >= appX() && mx < appX() + appW() && my >= appY() && my < appY() + appH();
    }

    // ------------------------------------------------------------------ layout

    private boolean hasDeck() {
        return device == Device.LAPTOP || device == Device.MACBOOK;
    }

    @Override
    protected void init() {
        float aspect = switch (device) {
            case IPAD -> 4 / 3f;
            case CAR_DISPLAY -> 2f;
            default -> 16 / 10f;
        };
        int bottomRoom = hasDeck() ? 50 : device == Device.COMPUTER ? 40 : 24;
        int maxW = Math.min(width - 40, 560), maxH = height - 24 - bottomRoom;
        sw = Math.min(maxW, (int) (maxH * aspect));
        sh = (int) (sw / aspect);
        sx = (width - sw) / 2;
        sy = Math.max(12, (height - sh - bottomRoom) / 2 + 6);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawFrame(g);
        g.fill(sx, sy, sx + sw, sy + sh, 0xFF0A0D12);

        App app = apps.get(current);
        InterceptorVehicle v = vehicle();
        if (device == Device.CAR_DISPLAY && v != null && !v.screensWork()) {
            drawDead(g, v);
            return;
        }
        g.enableScissor(appX(), appY(), appX() + appW(), appY() + appH());
        app.render(g, this, appX(), appY(), appW(), appH(), mouseX, mouseY, partialTick);
        g.disableScissor();

        drawStatusBar(g);
        drawDock(g, mouseX, mouseY);
    }

    private void drawFrame(GuiGraphics g) {
        switch (device) {
            case COMPUTER -> {
                g.fill(sx - 8, sy - 8, sx + sw + 8, sy + sh + 12, 0xFF151618);
                g.fill(sx + sw / 2 - 10, sy + sh + 12, sx + sw / 2 + 10, sy + sh + 30, 0xFF2A2C30);
                g.fill(sx + sw / 2 - 50, sy + sh + 30, sx + sw / 2 + 50, sy + sh + 35, 0xFF2A2C30);
                g.fill(sx + sw / 2 - 2, sy + sh + 4, sx + sw / 2 + 2, sy + sh + 7, 0xFF30E0FF);
            }
            case LAPTOP, MACBOOK -> {
                boolean mac = device == Device.MACBOOK;
                int bezel = mac ? 0xFF0B0B0C : 0xFF26282C, deck = mac ? 0xFFC9CDD2 : 0xFF33363B, keys = mac ? 0xFF1C1D20 : 0xFF1A1B1E;
                g.fill(sx - 6, sy - 6, sx + sw + 6, sy + sh + 6, bezel);
                if (mac) g.fill(sx + sw / 2 - 16, sy - 6, sx + sw / 2 + 16, sy + 1, bezel);
                int dy = sy + sh + 6;
                g.fill(sx - 26, dy, sx + sw + 26, dy + 40, deck);
                g.fill(sx - 26, dy + 40, sx + sw + 26, dy + 43, mac ? 0xFF9EA2A7 : 0xFF222428);
                int kx = sx + 10, kw = sw - 20, rows = 4, cols = 18;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        int x0 = kx + c * kw / cols, x1 = kx + (c + 1) * kw / cols - 2;
                        g.fill(x0, dy + 3 + r * 6, x1, dy + 8 + r * 6, keys);
                    }
                }
                g.fill(sx + sw / 2 - 30, dy + 28, sx + sw / 2 + 30, dy + 38, mac ? 0xFFB9BDC2 : 0xFF2B2D31);
            }
            case IPAD -> {
                g.fill(sx - 12, sy - 12, sx + sw + 12, sy + sh + 12, 0xFF2E3034);
                g.fill(sx - 10, sy - 10, sx + sw + 10, sy + sh + 10, 0xFF0B0B0C);
                g.fill(sx + sw / 2 - 1, sy - 7, sx + sw / 2 + 2, sy - 4, 0xFF303A48);
            }
            case CAR_DISPLAY -> {
                g.fill(sx - 30, sy - 10, sx + sw + 30, sy + sh + 16, 0xFF1D1F22);
                g.fill(sx - 6, sy - 6, sx + sw + 6, sy + sh + 6, 0xFF050506);
                for (int i = 0; i < 4; i++) {
                    g.fill(sx - 24, sy + 8 + i * 22, sx - 12, sy + 22 + i * 22, 0xFF34373C);
                    g.fill(sx + sw + 12, sy + 8 + i * 22, sx + sw + 24, sy + 22 + i * 22, 0xFF34373C);
                }
            }
        }
    }

    /** A frozen, rebooting or out-of-battery car display. */
    private void drawDead(GuiGraphics g, InterceptorVehicle v) {
        String key;
        if (v.getReboot() > 0) key = "device.interceptors.rebooting";
        else if (v.spec().solar() && v.getBattery() <= 0) key = "device.interceptors.battery_dead";
        else if (v.failed(com.interceptors.vehicle.Part.COMPUTER)) key = "device.interceptors.dead";
        else key = "device.interceptors.frozen";
        if (key.endsWith("frozen")) {
            // The last frame stays stuck on screen with a hang message over it.
            for (int i = 0; i < 6; i++) g.fill(sx, sy + i * sh / 6, sx + sw, sy + i * sh / 6 + 2, 0x4030E0FF);
        }
        Component msg = Component.translatable(key, Keys.ANCHORS.getTranslatedKeyMessage());
        int y = sy + sh / 2 - 10;
        for (var line : font.split(msg, sw - 20)) {
            g.drawString(font, line, sx + sw / 2 - font.width(line) / 2, y, 0x30E0FF, false);
            y += 10;
        }
        if (v.getReboot() > 0) {
            float p = 1 - v.getReboot() / (float) InterceptorVehicle.REBOOT_TICKS;
            g.fill(sx + sw / 4, y + 6, sx + sw * 3 / 4, y + 10, 0xFF303030);
            g.fill(sx + sw / 4, y + 6, sx + sw / 4 + (int) (sw / 2 * p), y + 10, 0xFF30A0FF);
        }
    }

    private void drawStatusBar(GuiGraphics g) {
        g.fill(sx, sy, sx + sw, sy + BAR, 0xE0101418);
        g.drawString(font, title, sx + 4, sy + 2, 0xC0C8D0, false);
        long day = minecraft.level.getDayTime() % 24000;
        int hours = (int) ((day / 1000 + 6) % 24), minutes = (int) (day % 1000 * 60 / 1000);
        String clock = String.format("%d:%02d", hours, minutes);
        g.drawString(font, clock, sx + sw / 2 - font.width(clock) / 2, sy + 2, 0xFFFFFF, false);
        if (device == Device.IPAD || device == Device.MACBOOK || device == Device.LAPTOP) {
            int bx = sx + sw - 20;
            g.fill(bx, sy + 3, bx + 14, sy + 8, 0xFF9AA0A6);
            g.fill(bx + 1, sy + 4, bx + 11, sy + 7, 0xFF40D060);
            g.fill(bx + 14, sy + 4, bx + 15, sy + 7, 0xFF9AA0A6);
        }
    }

    private void drawDock(GuiGraphics g, int mouseX, int mouseY) {
        int dy = sy + sh - DOCK;
        g.fill(sx, dy, sx + sw, sy + sh, 0xE0141820);
        int size = 18, gap = 6, total = apps.size() * size + (apps.size() - 1) * gap;
        int x = sx + (sw - total) / 2;
        Component hovered = null;
        for (int i = 0; i < apps.size(); i++) {
            App a = apps.get(i);
            int ix = x + i * (size + gap), iy = dy + 3;
            if (i == current) g.fill(ix - 2, iy - 2, ix + size + 2, iy + size + 2, 0xFF30E0FF);
            g.fill(ix, iy, ix + size, iy + size, 0xFF000000 | a.color());
            Component name = Component.translatable("app.interceptors." + a.id());
            String letter = name.getString().substring(0, 1);
            g.drawString(font, letter, ix + size / 2 - font.width(letter) / 2 + 1, iy + 5, 0xFFFFFF, true);
            if (mouseX >= ix && mouseX < ix + size && mouseY >= iy && mouseY < iy + size) hovered = name;
        }
        if (hovered != null) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ input

    private void switchTo(int index) {
        if (index == current || index < 0 || index >= apps.size()) return;
        apps.get(current).close();
        current = index;
        LAST_APP.put(device, apps.get(current).id());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int dy = sy + sh - DOCK, size = 18, gap = 6, total = apps.size() * size + (apps.size() - 1) * gap;
        int x = sx + (sw - total) / 2;
        if (mouseY >= dy && mouseY < sy + sh) {
            for (int i = 0; i < apps.size(); i++) {
                int ix = x + i * (size + gap);
                if (mouseX >= ix && mouseX < ix + size) {
                    switchTo(i);
                    return true;
                }
            }
        }
        if (inApp(mouseX, mouseY) && apps.get(current).mouseClicked(this, mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return apps.get(current).mouseReleased(this, mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return apps.get(current).mouseDragged(this, mouseX, mouseY, button, dx, dy) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return inApp(mouseX, mouseY) && apps.get(current).mouseScrolled(this, mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key != GLFW.GLFW_KEY_ESCAPE && apps.get(current).keyPressed(this, key, scancode, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_TAB) {
            switchTo((current + 1) % apps.size());
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            switchTo(key - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scancode, int modifiers) {
        return apps.get(current).keyReleased(this, key, scancode, modifiers) || super.keyReleased(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return apps.get(current).charTyped(this, c, modifiers) || super.charTyped(c, modifiers);
    }

    @Override
    public void tick() {
        apps.get(current).tick(this);
    }

    @Override
    public void removed() {
        apps.forEach(App::close);
        LAST_APP.put(device, apps.get(current).id());
    }
}
