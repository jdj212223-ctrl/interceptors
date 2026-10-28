package com.interceptors.client.gui.apps;

import com.interceptors.client.Keys;
import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Readings from the Dom 3's weather probes: what the probes measured inside the tornado on the last launch. */
public class ProbeApp implements App {
    @Override
    public String id() {
        return "probes";
    }

    @Override
    public int color() {
        return 0xE08020;
    }

    /** The launcher you're riding in, or else the nearest one around. */
    private static InterceptorVehicle launcher(Minecraft mc) {
        if (mc.player.getVehicle() instanceof InterceptorVehicle v && v.spec().probes()) return v;
        InterceptorVehicle best = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof InterceptorVehicle v && v.spec().probes() && (best == null || v.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) best = v;
        }
        return best;
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        g.fill(x, y, x + w, y + h, 0xFF14100C);
        InterceptorVehicle v = launcher(mc);
        int line = y + 6;
        g.drawString(font, Component.translatable("app.interceptors.probes.title"), x + 6, line, 0xFFB050, true);
        line += 14;
        if (v == null) {
            g.drawString(font, Component.translatable("app.interceptors.probes.none"), x + 6, line, 0x9AA0A6, false);
            return;
        }
        g.drawString(font, Component.translatable("app.interceptors.probes.left", v.probesLeft(), InterceptorVehicle.MAX_PROBES), x + 6, line, 0xE0E0E0, false);
        for (int i = 0; i < InterceptorVehicle.MAX_PROBES; i++) {
            int px = x + 6 + i * 10;
            g.fill(px, line + 11, px + 7, line + 19, i < v.probesLeft() ? 0xFFE08020 : 0xFF3A3028);
        }
        line += 26;
        CompoundTag data = v.probeData();
        if (data.isEmpty()) {
            g.drawString(font, Component.translatable("app.interceptors.probes.hint", Keys.PROBES.getTranslatedKeyMessage(), (int) InterceptorVehicle.PROBE_RANGE), x + 6, line, 0xC0C0C0, false);
            return;
        }
        int peak = data.getInt("Peak"), ef = WindPhysics.efRating(peak);
        long minutes = (mc.level.getGameTime() - data.getLong("Time")) / 1200;
        g.drawString(font, Component.translatable("app.interceptors.probes.peak", peak, ef < 0 ? "" : " (EF" + ef + ")"), x + 6, line, 0xFF6060, true);
        line += 12;
        g.drawString(font, Component.translatable("app.interceptors.probes.average", data.getInt("Average"), data.getInt("Count")), x + 6, line, 0xE0E0E0, false);
        line += 12;
        g.drawString(font, Component.translatable("app.interceptors.probes.when", data.getInt("Distance"), minutes), x + 6, line, 0xA0A0A0, false);
        line += 14;
        g.drawString(font, Component.translatable("app.interceptors.probes.hint", Keys.PROBES.getTranslatedKeyMessage(), (int) InterceptorVehicle.PROBE_RANGE), x + 6, line, 0x808080, false);
    }
}
