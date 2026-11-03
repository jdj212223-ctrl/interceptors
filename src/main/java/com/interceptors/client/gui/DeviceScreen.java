package com.interceptors.client.gui;

import com.interceptors.client.gui.apps.App;
import com.interceptors.client.Keys;
import com.interceptors.client.ObsLink;
import com.interceptors.item.DeviceItem;
import com.interceptors.network.DeviceUsePayload;
import net.neoforged.neoforge.network.PacketDistributor;
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
 * MacBook, iPad or car dashboard). It opens on a desktop with a stormy wallpaper and app icons, with a taskbar
 * (home button, open app, signal, battery, date and clock) along the bottom; apps fade in as they open. It never
 * pauses the game, so storms keep moving while you look.
 */
public class DeviceScreen extends Screen {
    private static final Map<Device, String> LAST_APP = new EnumMap<>(Device.class);
    /** Height of the taskbar along the bottom of the display. */
    private static final int TASKBAR = 16;
    /** "No app open": the desktop with its icons. */
    private static final int HOME = -1;
    /** How long (ticks) a newly opened app takes to fade in. */
    private static final float FADE_TICKS = 6;

    public final Device device;
    private final List<App> apps;
    private int current = HOME;
    /** Game time (with partial ticks) the current app was opened, for the fade-in. */
    private float openedAt = -100;
    private int useTicks;
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
        return sy;
    }

    public int appW() {
        return sw;
    }

    public int appH() {
        return sh - TASKBAR;
    }

    public boolean inApp(double mx, double my) {
        return mx >= appX() && mx < appX() + appW() && my >= appY() && my < appY() + appH();
    }

    // ------------------------------------------------------------------ layout

    private boolean hasDeck() {
        return device == Device.LAPTOP || device == Device.MACBOOK || device == Device.RUGGED_LAPTOP || device == Device.PASSENGER_LAPTOP;
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

    @Nullable
    private App app() {
        return current == HOME ? null : apps.get(current);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawFrame(g);
        DashTheme theme = theme();
        g.fill(sx, sy, sx + sw, sy + sh, theme == null ? 0xFF0A0D12 : theme.background);

        App app = app();
        InterceptorVehicle v = vehicle();
        if (device.portable() && battery() <= 0) {
            drawMessage(g, Component.translatable("device.interceptors.battery_empty"), 0xFF6060);
            drawTaskbar(g, mouseX, mouseY);
            return;
        }
        boolean hung = hung(v);
        if (device == Device.CAR_DISPLAY && v != null && !v.screensWork() && !hung) {
            drawDead(g, v);
            return;
        }
        g.enableScissor(appX(), appY(), appX() + appW(), appY() + appH());
        if (app == null) {
            drawDesktop(g, mouseX, mouseY);
        } else if (app.needsInternet() && !online()) {
            drawWallpaper(g);
            drawMessage(g, Component.translatable("device.interceptors.no_signal"), 0xFFC040);
        } else {
            app.render(g, this, appX(), appY(), appW(), appH(), mouseX, mouseY, partialTick);
        }
        // Each app fades in as it opens, like switching windows.
        float age = minecraft.level.getGameTime() + partialTick - openedAt;
        if (age < FADE_TICKS) g.fill(appX(), appY(), appX() + appW(), appY() + appH(), (int) (230 * (1 - age / FADE_TICKS)) << 24 | 0x0A0D12);
        g.disableScissor();
        drawTaskbar(g, mouseX, mouseY);
        if (hung) drawHang(g, v);
    }

    /** The car computer has hung (not rebooting, not dead): the picture sticks and nothing answers. */
    private boolean hung(@Nullable InterceptorVehicle v) {
        return device == Device.CAR_DISPLAY && v != null && v.isFrozen() && v.getReboot() == 0
                && !v.failed(com.interceptors.vehicle.Part.COMPUTER) && !(v.spec().solar() && v.getBattery() <= 0);
    }

    /**
     * A hung screen, like a real one: washed out, torn bands where the picture stopped mid-redraw, a frozen
     * spinner, and a "not responding" box on top. Clicks do nothing until it's rebooted.
     */
    private void drawHang(GuiGraphics g, InterceptorVehicle v) {
        g.fill(sx, sy, sx + sw, sy + sh, 0x70E8ECF0);
        // Torn bands: the same few every time this freeze is looked at, flickering only now and then.
        java.util.Random tear = new java.util.Random(v.getId() * 31L + (minecraft.level.getGameTime() / 37));
        for (int i = 0; i < 5; i++) {
            int y = sy + tear.nextInt(Math.max(1, sh - 12)), hgt = 2 + tear.nextInt(9), shift = tear.nextInt(30) - 15;
            g.fill(sx, y, sx + sw, y + hgt, 0x90000000 | (tear.nextBoolean() ? 0x30E0FF : 0xC8CCD0));
            g.fill(Math.max(sx, sx + shift), y + hgt / 2, Math.min(sx + sw, sx + sw + shift), y + hgt / 2 + 1, 0xC0FFFFFF);
        }
        int bw = Math.min(sw - 20, 220), bh = 70, bx = sx + sw / 2 - bw / 2, by = sy + sh / 2 - bh / 2;
        g.fill(bx + 3, by + 3, bx + bw + 3, by + bh + 3, 0x80000000);
        g.fill(bx, by, bx + bw, by + bh, 0xFFF0F0F0);
        g.fill(bx, by, bx + bw, by + 14, 0xFF3A6EA5);
        g.drawString(font, Component.translatable("device.interceptors.hang_title"), bx + 5, by + 3, 0xFFFFFF, false);
        int y = by + 20;
        for (var line : font.split(Component.translatable("device.interceptors.hang_body", Keys.ANCHORS.getTranslatedKeyMessage()), bw - 34)) {
            g.drawString(font, line, bx + 28, y, 0x202020, false);
            y += 10;
        }
        // The stuck spinner: one frame, never moving.
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int px = bx + 14 + (int) Math.round(Math.cos(a) * 6), py = by + 34 + (int) Math.round(Math.sin(a) * 6);
            g.fill(px - 1, py - 1, px + 1, py + 1, k < 3 ? 0xFF3A6EA5 : 0xFFB0B4B8);
        }
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
            case RUGGED_LAPTOP, PASSENGER_LAPTOP -> {
                // Toughbook look: thick silver magnesium case, black rubber corner bumpers, latch, carry handle.
                g.fill(sx - 12, sy - 12, sx + sw + 12, sy + sh + 10, 0xFFC4C8CC);
                g.fill(sx + sw / 2 - 14, sy - 16, sx + sw / 2 + 14, sy - 9, 0xFF202020);
                g.fill(sx - 4, sy - 4, sx + sw + 4, sy + sh + 4, 0xFF1A1B1D);
                int dy = sy + sh + 10;
                g.fill(sx - 30, dy, sx + sw + 30, dy + 44, 0xFFB4B8BC);
                for (int r = 0; r < 4; r++) {
                    for (int c = 0; c < 16; c++) {
                        int kw = (sw + 20) / 16;
                        g.fill(sx - 10 + c * kw, dy + 4 + r * 7, sx - 10 + (c + 1) * kw - 2, dy + 9 + r * 7, 0xFF202124);
                    }
                }
                g.fill(sx + sw / 2 - 26, dy + 32, sx + sw / 2 + 26, dy + 41, 0xFF46494C);
                g.fill(sx + sw / 2 - 40, dy + 44, sx + sw / 2 + 40, dy + 52, 0xFF2A2B2D);
                g.fill(sx + sw / 2 - 34, dy + 44, sx + sw / 2 + 34, dy + 48, 0xFFB4B8BC);
                for (int[] c : new int[][]{{sx - 16, sy - 16}, {sx + sw + 4, sy - 16}, {sx - 34, dy + 30}, {sx + sw + 18, dy + 30}}) {
                    g.fill(c[0], c[1], c[0] + 16, c[1] + 16, 0xFF141414);
                }
            }
            case IPAD -> {
                g.fill(sx - 12, sy - 12, sx + sw + 12, sy + sh + 12, 0xFF2E3034);
                g.fill(sx - 10, sy - 10, sx + sw + 10, sy + sh + 10, 0xFF0B0B0C);
                g.fill(sx + sw / 2 - 1, sy - 7, sx + sw / 2 + 2, sy - 4, 0xFF303A48);
            }
            case CAR_DISPLAY -> drawCarFrame(g, theme());
        }
    }

    /** The car display's look in the vehicle you're in, or null away from one (or on any other device). */
    @Nullable
    public DashTheme theme() {
        InterceptorVehicle v = vehicle();
        return device == Device.CAR_DISPLAY && v != null ? DashTheme.of(v.spec()) : null;
    }

    /** Each vehicle's own dash unit around the screen. */
    private void drawCarFrame(GuiGraphics g, @Nullable DashTheme t) {
        int frame = t == null ? 0xFF1D1F22 : t.frame, accent = t == null ? 0xFF34373C : t.accent(minecraft.level.getGameTime());
        g.fill(sx - 30, sy - 10, sx + sw + 30, sy + sh + 16, frame);
        g.fill(sx - 6, sy - 6, sx + sw + 6, sy + sh + 6, 0xFF050506);
        if (t == null) return;
        switch (t) {
            case DOMINATOR -> {
                // Rugged mount: black rubber corners and a carry bar.
                for (int[] c : new int[][]{{sx - 30, sy - 10}, {sx + sw + 18, sy - 10}, {sx - 30, sy + sh + 4}, {sx + sw + 18, sy + sh + 4}}) {
                    g.fill(c[0], c[1], c[0] + 12, c[1] + 12, 0xFF141414);
                }
                g.fill(sx + sw / 2 - 30, sy - 16, sx + sw / 2 + 30, sy - 10, 0xFF202020);
                g.fill(sx - 6, sy + sh + 8, sx + sw + 6, sy + sh + 10, accent);
            }
            case MILITARY -> {
                // Bolted olive housing with chunky switches.
                for (int i = 0; i < 6; i++) {
                    Draw.dot(g, sx - 24, sy + 4 + i * (sh / 5f), 4, 0xFF6A7060);
                    Draw.dot(g, sx + sw + 24, sy + 4 + i * (sh / 5f), 4, 0xFF6A7060);
                }
                for (int i = 0; i < 4; i++) g.fill(sx + 10 + i * 30, sy + sh + 8, sx + 30 + i * 30, sy + sh + 14, 0xFF202418);
            }
            case RADAR -> {
                // Console with knobs and a status strip.
                for (int i = 0; i < 4; i++) {
                    Draw.disc(g, sx - 18, sy + 12 + i * 22, 5, 0xFF56606C);
                    Draw.disc(g, sx + sw + 18, sy + 12 + i * 22, 5, 0xFF56606C);
                }
                g.fill(sx - 6, sy + sh + 8, sx + sw + 6, sy + sh + 12, accent);
            }
            case GAMING -> {
                // RGB light strip all the way round.
                long time = minecraft.level.getGameTime();
                for (int i = 0; i < 4; i++) {
                    int c = 0xFF000000 | net.minecraft.util.Mth.hsvToRgb(((time + i * 30) % 120) / 120f, 0.8f, 1f);
                    int[][] sides = {{sx - 8, sy - 8, sx + sw + 8, sy - 6}, {sx + sw + 6, sy - 8, sx + sw + 8, sy + sh + 8},
                            {sx - 8, sy + sh + 6, sx + sw + 8, sy + sh + 8}, {sx - 8, sy - 8, sx - 6, sy + sh + 8}};
                    g.fill(sides[i][0], sides[i][1], sides[i][2], sides[i][3], c);
                }
            }
            case STOCK -> {
                // Plain head unit: a volume knob and two buttons.
                Draw.disc(g, sx - 16, sy + sh / 2f, 8, 0xFF303030);
                g.fill(sx + sw + 10, sy + 10, sx + sw + 24, sy + 22, 0xFF303030);
                g.fill(sx + sw + 10, sy + 28, sx + sw + 24, sy + 40, 0xFF303030);
            }
            case COCKPIT -> {
                // Avionics bezel: rows of soft keys down both sides.
                for (int i = 0; i < 6; i++) {
                    g.fill(sx - 26, sy + 4 + i * (sh / 6), sx - 12, sy + 4 + i * (sh / 6) + 8, 0xFF3A3E44);
                    g.fill(sx + sw + 12, sy + 4 + i * (sh / 6), sx + sw + 26, sy + 4 + i * (sh / 6) + 8, 0xFF3A3E44);
                }
            }
        }
    }

    /** Online on the road only with a satellite dish on the vehicle; at home (not riding) there's always a connection. */
    public boolean online() {
        InterceptorVehicle v = vehicle();
        return v == null || v.hasDish();
    }

    /** Charge of the handheld in the player's hand, 0..100 (plugged-in devices are always 100). */
    private int battery() {
        if (!device.portable() || minecraft == null || minecraft.player == null) return 100;
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() instanceof DeviceItem item && item.device() == device) return DeviceItem.battery(stack);
        }
        return 100;
    }

    private void drawMessage(GuiGraphics g, Component msg, int color) {
        int y = sy + sh / 2 - 10;
        for (var line : font.split(msg, sw - 30)) {
            g.drawString(font, line, sx + sw / 2 - font.width(line) / 2, y, color, false);
            y += 10;
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

    // ------------------------------------------------------------------ desktop

    /** A stormy sky: deep blue to teal, a supercell's anvil, a funnel touching down, and the StormOS watermark. */
    private void drawWallpaper(GuiGraphics g) {
        int x = appX(), y = appY(), w = appW(), h = appH();
        DashTheme t = theme();
        if (t != null) {
            // The vehicle's own look behind its icons.
            for (int i = 0; i < h; i += 2) g.fill(x, y + i, x + w, y + i + 2, Draw.lerpColor(t.background, t.panel, i / (float) h));
            int accent = t.accent(minecraft.level.getGameTime());
            switch (t) {
                case MILITARY -> {
                    for (int i = 0; i < h; i += 3) g.fill(x, y + i, x + w, y + i + 1, 0x18000000);
                    for (int k = 1; k < 6; k++) g.fill(x, y + k * h / 6, x + w, y + k * h / 6 + 1, 0x2033FF66);
                }
                case RADAR -> {
                    for (int k = 1; k <= 4; k++) Draw.ring(g, x + w / 2f, y + h / 2f, Math.min(w, h) * k / 9f, 0x3030E0FF);
                }
                case COCKPIT -> {
                    g.fill(x, y + h / 2, x + w, y + h, 0x407A5030);
                    g.fill(x, y + h / 2, x + w, y + h / 2 + 1, 0xFFFFD020);
                }
                case DOMINATOR -> {
                    for (int k = -h; k < w; k += 24) Draw.line(g, x + k, y + h, x + k + h / 3f, y, 3, 0x20FF8C1A);
                }
                case GAMING -> g.fill(x, y + h - 3, x + w, y + h, accent);
                default -> {}
            }
            String name = t.format(Component.translatable("entity.interceptors." + vehicle().spec().id()).getString());
            g.drawString(font, name, x + w - font.width(name) - 6, y + h - 12, accent & 0x90FFFFFF, false);
            return;
        }
        for (int i = 0; i < h; i += 2) g.fill(x, y + i, x + w, y + i + 2, Draw.lerpColor(0x0C1630, 0x2A5A60, i / (float) h));
        float cx = x + w * 0.62f, base = y + h * 0.42f;
        for (int k = 0; k < 5; k++) Draw.disc(g, cx - w * 0.25f + k * w * 0.12f, base - (k % 2) * 6, w * 0.11f, 0x60101820);
        g.fill(x + (int) (w * 0.3f), (int) base, x + (int) (w * 0.95f), (int) base + 6, 0x70101820);
        for (int sIdx = 0; sIdx < 14; sIdx++) {
            float f = sIdx / 14f;
            int half = (int) (w * 0.05f * (1 - f) + 2);
            int fy = (int) (base + 6 + f * (h * 0.48f));
            g.fill((int) cx - half + (int) (Math.sin(f * 3) * 6), fy, (int) cx + half + (int) (Math.sin(f * 3) * 6), fy + (int) (h * 0.035f) + 1, 0x80202830);
        }
        g.fill(x, y + h - 8, x + w, y + h, 0x60203018);
        String mark = "StormOS";
        g.drawString(font, mark, x + w - font.width(mark) - 6, y + h - 18, 0x60FFFFFF, false);
    }

    private static final int ICON = 26, CELL_W = 58, CELL_H = 46;

    /** The desktop: wallpaper and a grid of app icons with their names. Click one, or press its letter. */
    private void drawDesktop(GuiGraphics g, int mouseX, int mouseY) {
        drawWallpaper(g);
        int cols = Math.max(1, (appW() - 8) / CELL_W);
        for (int i = 0; i < apps.size(); i++) {
            int[] r = iconRect(i, cols);
            boolean hover = mouseX >= r[0] - 4 && mouseX < r[0] + ICON + 4 && mouseY >= r[1] - 2 && mouseY < r[1] + ICON + 12;
            if (hover) g.fill(r[0] - 14, r[1] - 3, r[0] + ICON + 14, r[1] + ICON + 13, 0x40FFFFFF);
            drawIcon(g, apps.get(i), r[0], r[1], ICON);
            String name = Component.translatable("app.interceptors." + apps.get(i).id()).getString();
            if (font.width(name) > CELL_W - 4) name = font.plainSubstrByWidth(name, CELL_W - 10) + ".";
            g.drawString(font, name, r[0] + ICON / 2 - font.width(name) / 2, r[1] + ICON + 3, 0xFFFFFF, true);
        }
    }

    private int[] iconRect(int i, int cols) {
        int gridW = cols * CELL_W, left = appX() + (appW() - gridW) / 2;
        return new int[]{left + (i % cols) * CELL_W + (CELL_W - ICON) / 2, appY() + 10 + (i / cols) * CELL_H};
    }

    /** A rounded app tile in the app's colour with a little white picture of what it does. */
    private void drawIcon(GuiGraphics g, App a, int x, int y, int s) {
        int c = 0xFF000000 | a.color(), dark = Draw.lerpColor(a.color(), 0x000000, 0.35f);
        g.fill(x + 2, y, x + s - 2, y + s, c);
        g.fill(x, y + 2, x + s, y + s - 2, c);
        g.fill(x + 2, y + s / 2, x + s - 2, y + s, dark);
        g.fill(x, y + s / 2, x + s, y + s - 2, dark);
        float cx = x + s / 2f, cy = y + s / 2f, r = s * 0.32f;
        int w = 0xFFFFFFFF;
        switch (a.id()) {
            case "vehicle" -> {
                g.fill((int) (cx - r), (int) (cy - 2), (int) (cx + r), (int) (cy + 4), w);
                g.fill((int) (cx - r * 0.5f), (int) (cy - 6), (int) (cx + r * 0.6f), (int) (cy - 1), w);
                Draw.disc(g, cx - r * 0.6f, cy + 5, 2.5f, 0xFF202020);
                Draw.disc(g, cx + r * 0.6f, cy + 5, 2.5f, 0xFF202020);
            }
            case "radar" -> {
                Draw.ring(g, cx, cy, r, w);
                Draw.ring(g, cx, cy, r * 0.5f, 0xA0FFFFFF);
                Draw.line(g, cx, cy, cx + r * 0.7f, cy - r * 0.7f, 1, w);
                Draw.dot(g, cx - r * 0.4f, cy + r * 0.2f, 3, 0xFFFF4040);
            }
            case "intercept" -> Draw.arrow(g, cx, cy + 1, r, 35, 2, w);
            case "storms" -> {
                for (int k = 0; k < 3; k++) {
                    Draw.dot(g, cx - r, cy - 5 + k * 5, 3, k == 0 ? 0xFFFF4040 : w);
                    g.fill((int) (cx - r + 4), (int) (cy - 6 + k * 5), (int) (cx + r), (int) (cy - 4 + k * 5), w);
                }
            }
            case "chopper" -> {
                Draw.disc(g, cx - 2, cy + 1, r * 0.55f, w);
                g.fill((int) cx, (int) cy, (int) (cx + r + 2), (int) cy + 2, w);
                g.fill((int) (cx - r - 2), (int) (cy - r * 0.6f - 2), (int) (cx + r + 2), (int) (cy - r * 0.6f - 1), w);
                g.fill((int) cx - 3, (int) (cy + r * 0.6f + 1), (int) cx + 3, (int) (cy + r * 0.6f + 2), w);
            }
            case "probes" -> {
                g.fill((int) cx - 2, (int) (cy - r + 2), (int) cx + 3, (int) (cy + r - 3), w);
                Draw.line(g, cx - 2, cy - r + 2, cx, cy - r - 2, 1, w);
                Draw.line(g, cx + 2, cy - r + 2, cx, cy - r - 2, 1, w);
                g.fill((int) cx - 1, (int) (cy + r - 3), (int) cx + 2, (int) (cy + r + 1), 0xFFFFC040);
            }
            case "game" -> {
                for (int k = 0; k < 5; k++) {
                    int half = (int) (r * (1 - k * 0.17f));
                    g.fill((int) cx - half + (k % 2), (int) (cy - r + k * 3.5f), (int) cx + half + (k % 2), (int) (cy - r + k * 3.5f + 2.5f), w);
                }
            }
            case "web" -> {
                Draw.ring(g, cx, cy, r, w);
                Draw.line(g, cx - r, cy, cx + r, cy, 1, w);
                Draw.line(g, cx, cy - r, cx, cy + r, 1, w);
                Draw.ring(g, cx, cy, r * 0.45f, 0xB0FFFFFF);
            }
            default -> {
                String letter = String.valueOf(shortcut(a));
                g.drawString(font, letter, (int) cx - font.width(letter) / 2, (int) cy - 4, w, true);
            }
        }
    }

    /** Along the bottom: the StormOS home button, the open app, then signal, battery, date and clock. */
    private void drawTaskbar(GuiGraphics g, int mouseX, int mouseY) {
        int y = sy + sh - TASKBAR;
        DashTheme t = theme();
        int accent = t == null ? 0xFF30E0FF : t.accent(minecraft.level.getGameTime());
        g.fill(sx, y, sx + sw, sy + sh, t == null ? 0xF0101418 : t.panel);
        g.fill(sx, y, sx + sw, y + 1, t == null ? 0x4030E0FF : accent);
        boolean homeHover = mouseX >= sx && mouseX < sx + 52 && mouseY >= y;
        int homeColor = t == null ? 0xFF1E4A8A : Draw.lerpColor(accent & 0xFFFFFF, 0x000000, 0.45f);
        g.fill(sx + 2, y + 2, sx + 50, sy + sh - 2, homeHover ? accent : homeColor);
        // a little house for "home"
        g.fill(sx + 6, y + 8, sx + 13, y + 13, 0xFFFFFFFF);
        Draw.line(g, sx + 5, y + 8, sx + 9.5f, y + 4, 1, 0xFFFFFFFF);
        Draw.line(g, sx + 14, y + 8, sx + 9.5f, y + 4, 1, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("device.interceptors.home"), sx + 17, y + 4, 0xFFFFFF, false);
        App a = app();
        Component open = a == null ? title : Component.translatable("app.interceptors." + a.id());
        g.drawString(font, t == null ? open : Component.literal(t.format(open.getString())), sx + 56, y + 4, t == null ? 0xC0C8D0 : t.text, false);

        int rx = sx + sw - 4;
        long day = minecraft.level.getDayTime() % 24000;
        int hours = (int) ((day / 1000 + 6) % 24), minutes = (int) (day % 1000 * 60 / 1000);
        String date = com.interceptors.compat.Storms.date(minecraft.level);
        String clock = (date == null ? "" : date + "  ") + String.format("%d:%02d", hours, minutes);
        rx -= font.width(clock);
        g.drawString(font, clock, rx, y + 4, 0xFFFFFF, false);
        if (device.portable()) {
            int charge = battery();
            rx -= 22;
            g.fill(rx, y + 5, rx + 14, y + 11, 0xFF9AA0A6);
            g.fill(rx + 1, y + 6, rx + 1 + Math.round(12 * charge / 100f), y + 10, charge > 20 ? 0xFF40D060 : 0xFFE03030);
            g.fill(rx + 14, y + 7, rx + 15, y + 9, 0xFF9AA0A6);
            String pct = charge + "%";
            rx -= font.width(pct) + 3;
            g.drawString(font, pct, rx, y + 4, 0xC0C8D0, false);
        }
        if (vehicle() != null) {
            Component net = Component.translatable(online() ? "device.interceptors.sat_ok" : "device.interceptors.sat_none");
            rx -= font.width(net) + 8;
            g.drawString(font, net, rx, y + 4, online() ? 0x80D080 : 0xFFC040, false);
        }
    }

    // ------------------------------------------------------------------ input

    /** The letter shown on an app's icon, which is also its keyboard shortcut. */
    private static char shortcut(App app) {
        return Component.translatable("app.interceptors." + app.id()).getString().charAt(0);
    }

    private void switchTo(int index) {
        if (index == current || index < HOME || index >= apps.size()) return;
        App old = app();
        if (old != null) old.close();
        current = index;
        openedAt = minecraft.level.getGameTime();
        LAST_APP.put(device, current == HOME ? "" : apps.get(current).id());
        ObsLink.appOpened(current == HOME ? null : apps.get(current).id());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hung(vehicle())) return true;
        int y = sy + sh - TASKBAR;
        if (mouseY >= y && mouseY < sy + sh && mouseX >= sx && mouseX < sx + 52) {
            switchTo(HOME);
            return true;
        }
        if (current == HOME && inApp(mouseX, mouseY)) {
            int cols = Math.max(1, (appW() - 8) / CELL_W);
            for (int i = 0; i < apps.size(); i++) {
                int[] r = iconRect(i, cols);
                if (mouseX >= r[0] - 14 && mouseX < r[0] + ICON + 14 && mouseY >= r[1] - 3 && mouseY < r[1] + ICON + 13) {
                    switchTo(i);
                    return true;
                }
            }
        }
        App a = app();
        if (a != null && inApp(mouseX, mouseY) && a.mouseClicked(this, mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        App a = app();
        if (a != null) a.mouseMoved(this, mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        App a = app();
        return a != null && a.mouseReleased(this, mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        App a = app();
        return a != null && a.mouseDragged(this, mouseX, mouseY, button, dx, dy) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        App a = app();
        return a != null && inApp(mouseX, mouseY) && a.mouseScrolled(this, mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key != GLFW.GLFW_KEY_ESCAPE && hung(vehicle())) return true;
        App a = app();
        if (key != GLFW.GLFW_KEY_ESCAPE && a != null && a.keyPressed(this, key, scancode, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_H) {
            switchTo(HOME);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            switchTo((current + 1) % apps.size());
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            switchTo(key - GLFW.GLFW_KEY_1);
            return true;
        }
        // Letter shortcuts: each app's icon letter (V)ehicle, (R)adar, (I)ntercept, (S)torms, (C)hopper, (P)robes...
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char letter = (char) ('A' + key - GLFW.GLFW_KEY_A);
            for (int i = 0; i < apps.size(); i++) {
                if (Character.toUpperCase(shortcut(apps.get(i))) == letter) {
                    switchTo(i);
                    return true;
                }
            }
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scancode, int modifiers) {
        App a = app();
        return a != null && a.keyReleased(this, key, scancode, modifiers) || super.keyReleased(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        App a = app();
        return a != null && a.charTyped(this, c, modifiers) || super.charTyped(c, modifiers);
    }

    @Override
    public void tick() {
        // Handhelds use battery while their screen is on: one percent every five seconds.
        if (device.portable() && ++useTicks % 100 == 0) PacketDistributor.sendToServer(new DeviceUsePayload(device.ordinal()));
        App a = app();
        if (a != null) a.tick(this);
    }

    @Override
    public void removed() {
        apps.forEach(App::close);
        LAST_APP.put(device, current == HOME ? "" : apps.get(current).id());
        ObsLink.appOpened(null);
    }
}
