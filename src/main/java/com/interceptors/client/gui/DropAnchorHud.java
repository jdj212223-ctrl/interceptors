package com.interceptors.client.gui;

import com.interceptors.client.ClientEvents;
import com.interceptors.client.Keys;
import com.interceptors.client.render.Mat;
import com.interceptors.client.render.VehicleModels;
import com.interceptors.vehicle.AnchorStatus;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.Part;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The "DROP ANCHOR" overlay from the Dominator 3 videos, top right: outline title, big status word, a white
 * wireframe side view of the vehicle (nose to the left) and arrows at the anchor points. When the anchors go down
 * the spikes grow out of the body and drive into the ground. Yellow STANDBY, green READY, red ERROR.
 */
public final class DropAnchorHud {
    private static final int W = 200, H = 124;
    private static final int YELLOW = 0xFFF2F020, GREEN = 0xFF40F040, RED = 0xFFF02020, CYAN = 0xFF30C0FF, WHITE = 0xFFE8E8E8;

    /** A vehicle's side profile, worked out once from its model. */
    private record Profile(List<float[][]> outlines, List<float[]> wheels, float[] anchors, float minZ, float maxZ, float maxY, float bottom) {}

    private static final Map<String, Profile> PROFILES = new ConcurrentHashMap<>();

    private DropAnchorHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !(mc.player.getVehicle() instanceof InterceptorVehicle v)) return;
        AnchorStatus status = v.anchorStatus();
        if (status == AnchorStatus.NONE) return;
        boolean blink = mc.level.getGameTime() / 6 % 2 == 0;
        int x = g.guiWidth() - W - 6, y = 6;
        if (BroadcastHud.enabled) {
            x = g.guiWidth() - BroadcastHud.columnWidth(g.guiWidth()) - W - 10;
            y = BroadcastHud.TOP + 10;
        }
        g.fill(x, y, x + W, y + H, 0xFF000000);
        g.fill(x, y, x + W, y + 1, 0xFF202020);

        String word;
        int color;
        String detail = null;
        switch (status) {
            case READY -> { word = "READY"; color = GREEN; }
            case DEPLOYING -> { word = "DEPLOYING"; color = YELLOW; }
            case ANCHORED -> { word = "ANCHORED"; color = GREEN; }
            case RETRACTING -> { word = "RETRACTING"; color = YELLOW; }
            case REBOOTING -> { word = "REBOOTING"; color = CYAN; }
            case FAILED -> {
                word = "ERROR";
                color = RED;
                detail = v.failed(Part.HYDRAULICS) ? "hud.interceptors.error.hydraulics" : "hud.interceptors.error.mud";
            }
            case FROZEN -> {
                word = "ERROR";
                color = RED;
                detail = v.failed(Part.COMPUTER) ? "hud.interceptors.error.computer_dead" : "hud.interceptors.error.frozen";
            }
            default -> { word = "STANDBY"; color = YELLOW; }
        }
        boolean error = color == RED;
        StrokeFont.drawCentered(g, "DROP ANCHOR", x + W / 2f, y + 5, 1.6f, 1, error ? RED : WHITE);
        StrokeFont.drawCentered(g, word, x + W / 2f, y + 18, 3.2f, 3, color);

        Profile p = PROFILES.computeIfAbsent(v.spec().id(), DropAnchorHud::profile);
        float ground = y + H - 32;
        float scale = Math.min((W - 20) / (p.maxZ - p.minZ), (ground - (y + 42)) / p.maxY);
        float left = x + (W - (p.maxZ - p.minZ) * scale) / 2;
        // Nose to the left, like the overlay: screen x grows toward the back of the vehicle.
        java.util.function.BiFunction<Float, Float, float[]> at = (z, h) -> new float[]{left + (p.maxZ - z) * scale, ground - h * scale};

        for (float[][] poly : p.outlines) {
            for (int i = 0; i < poly.length; i++) {
                float[] a = at.apply(poly[i][0], poly[i][1]), b = at.apply(poly[(i + 1) % poly.length][0], poly[(i + 1) % poly.length][1]);
                Draw.line(g, a[0], a[1], b[0], b[1], 1, WHITE);
            }
        }
        for (float[] wheel : p.wheels) {
            float[] c = at.apply(wheel[0], wheel[1]);
            float r = wheel[2] * scale;
            for (int i = 0; i < 24; i += 2) {
                double a0 = Math.PI * 2 * i / 24, a1 = Math.PI * 2 * (i + 1) / 24;
                Draw.line(g, c[0] + (float) Math.cos(a0) * r, c[1] + (float) Math.sin(a0) * r,
                        c[0] + (float) Math.cos(a1) * r, c[1] + (float) Math.sin(a1) * r, 1, 0xFFB0B0B0);
            }
        }

        // Anchor shafts and arrows. As the anchors deploy the spikes grow out of the body and drive into the ground.
        float ext = v.anchorExtension();
        for (float z : p.anchors) {
            float[] top = at.apply(z, p.bottom);
            for (float dy = top[1]; dy < ground; dy += 3) Draw.dot(g, top[0], dy, 1, 0xFF909090);
            if (ext > 0) {
                float tip = ground + 9 * ext;
                Draw.line(g, top[0], top[1], top[0], tip, 3, color);
                Draw.line(g, top[0] - 3, tip - 3, top[0], tip, 1, color);
                Draw.line(g, top[0] + 3, tip - 3, top[0], tip, 1, color);
            } else {
                float bob = status == AnchorStatus.READY ? (mc.level.getGameTime() % 20) / 10f : 0;
                float ay = ground + 4 + bob;
                Draw.line(g, top[0], ay, top[0], ay + 9, 2, color);
                Draw.line(g, top[0] - 4, ay + 5, top[0], ay + 9, 2, color);
                Draw.line(g, top[0] + 4, ay + 5, top[0], ay + 9, 2, color);
            }
        }
        if (ext >= 1) {
            for (float gx = x + 8; gx < x + W - 8; gx += 6) Draw.line(g, gx, ground + 1, gx + 3, ground + 4, 1, 0xFF606060);
        }

        if (detail != null) {
            Component d = Component.translatable(detail, Keys.ANCHORS.getTranslatedKeyMessage());
            g.drawString(mc.font, d, x + W / 2 - mc.font.width(d) / 2, y + H - 9, error && !blink ? 0xFF8080 : 0xFF4040, false);
        }
        if (status == AnchorStatus.REBOOTING || status == AnchorStatus.FROZEN && ClientEvents.rebootHoldProgress() > 0) {
            float f = status == AnchorStatus.REBOOTING ? 1 - v.getReboot() / (float) InterceptorVehicle.REBOOT_TICKS : ClientEvents.rebootHoldProgress();
            g.fill(x + 20, y + H - 3, x + W - 20, y + H - 1, 0xFF303030);
            g.fill(x + 20, y + H - 3, x + 20 + (int) ((W - 40) * f), y + H - 1, CYAN);
        }
    }

    /** The outer side panels of the model seen from the driver's side, its wheels, and where its anchors are. */
    private static Profile profile(String id) {
        List<float[][]> outlines = new ArrayList<>();
        List<float[]> wheels = new ArrayList<>();
        TreeSet<Float> anchors = new TreeSet<>();
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE, maxY = 0, bottom = 0.5f;
        for (VehicleModels.Box b : VehicleModels.get(id)) {
            int f = b.flags();
            if ((f & VehicleModels.ANCHOR) != 0) {
                anchors.add(Math.round((b.z0() + b.z1()) / 2 * 10) / 10f);
                continue;
            }
            if ((f & VehicleModels.RGB) != 0 || b.x1() < 0.8f) continue;
            if (b.mat() == Mat.TIRE) {
                wheels.add(new float[]{(b.z0() + b.z1()) / 2, (b.y0() + b.y1()) / 2, (b.y1() - b.y0()) / 2});
                continue;
            }
            if (b.mat() == Mat.RIM || (b.z1() - b.z0()) < 0.08f && (b.y1() - b.y0()) < 0.08f) continue;
            float[][] c = b.corners();
            // The +X side face: bottom back, bottom front, top front, top back.
            outlines.add(new float[][]{{c[1][2], c[1][1]}, {c[2][2], c[2][1]}, {c[6][2], c[6][1]}, {c[5][2], c[5][1]}});
            minZ = Math.min(minZ, b.z0());
            maxZ = Math.max(maxZ, b.z1());
            maxY = Math.max(maxY, b.y1());
        }
        float[] a = new float[anchors.size()];
        int i = 0;
        for (float z : anchors) a[i++] = z;
        return new Profile(outlines, wheels, a, minZ, maxZ, Math.max(maxY, 1), bottom);
    }
}
