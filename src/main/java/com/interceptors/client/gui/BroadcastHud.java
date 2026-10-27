package com.interceptors.client.gui;

import com.interceptors.compat.Storms;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * A stream-style lower third (toggle with B): LIVE tag, where you are, the wind, the tornado, and your vehicle's
 * telemetry. Built to look good when OBS captures the game window.
 */
public final class BroadcastHud {
    /** Off until B is pressed (dev runs can start with it on via -Dinterceptors.broadcast=true). */
    public static boolean enabled = Boolean.getBoolean("interceptors.broadcast");

    private BroadcastHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.options.hideGui || mc.player == null || mc.level == null) return;
        Font font = mc.font;
        int w = 250, h = 48, x = 6, y = g.guiHeight() - h - 30;
        g.fill(x, y, x + w, y + h, 0xD0101418);
        g.fill(x, y, x + 3, y + h, 0xFFE02020);

        boolean blink = mc.level.getGameTime() / 10 % 2 == 0;
        g.fill(x + 8, y + 5, x + 38, y + 15, 0xFFE02020);
        if (blink) g.fill(x + 11, y + 8, x + 15, y + 12, 0xFFFFFFFF);
        g.drawString(font, "LIVE", x + 17, y + 6, 0xFFFFFF, false);
        g.drawString(font, Component.translatable("broadcast.interceptors.title"), x + 44, y + 6, 0xFFFFFF, true);

        BlockPos pos = mc.player.blockPosition();
        Component biome = mc.level.getBiome(pos).unwrapKey().map(BroadcastHud::biomeName).orElse(Component.literal("?"));
        double wind = Storms.windAt(mc.level, mc.player.getEyePosition()).horizontalDistance();
        int ef = WindPhysics.efRating(wind);
        g.drawString(font, Component.translatable("broadcast.interceptors.where", biome, (int) wind, ef < 0 ? "" : " EF" + ef), x + 8, y + 19, 0xC8D0D8, false);

        Storms.StormInfo t = Storms.nearestTornado(mc.level, mc.player.position());
        Component tornado = t == null ? Component.translatable("broadcast.interceptors.no_tornado")
                : Component.translatable("broadcast.interceptors.tornado", t.efLabel(), t.windspeed(), (int) t.distance(), Storms.compassName(t.bearing()));
        g.drawString(font, tornado, x + 8, y + 29, t == null ? 0x9AA0A6 : 0xFF6060, false);

        if (mc.player.getVehicle() instanceof InterceptorVehicle v) {
            Component status = Component.translatable("broadcast.interceptors.vehicle", Component.translatable("entity.interceptors." + v.spec().id()),
                    Math.round(WindPhysics.toMph(Math.abs(v.getSpeed()))), Math.round(v.healthFraction() * 100), Component.translatable(v.anchorStatus().key()));
            g.drawString(font, status, x + 8, y + 39, 0x30E0FF, false);
        } else {
            g.drawString(font, Component.translatable("broadcast.interceptors.on_foot"), x + 8, y + 39, 0x30E0FF, false);
        }
    }

    private static Component biomeName(ResourceKey<Biome> key) {
        return Component.translatable(key.location().toLanguageKey("biome"));
    }
}
