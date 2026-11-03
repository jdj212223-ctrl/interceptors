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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The storm-chase livestream overlay (toggle with B), laid out like a real chase stream: an orange top bar, the
 * game framed in orange and black with the crew name and the watch/warning box, a right column with velocity
 * radar, follower count and viewer chat, and a black bottom strip with the watch number and countdown, the date
 * and time, and a scrolling ticker. Everything is filled from live PMWeather data.
 */
public final class BroadcastHud {
    /** Off until B is pressed (dev runs can start with it on via -Dinterceptors.broadcast=true). */
    public static boolean enabled = Boolean.getBoolean("interceptors.broadcast");

    static final int TOP = 16, BOTTOM = 34;
    private static final int ORANGE = 0xFFF07A12, BLACK = 0xFF0A0A0A, RED = 0xFFD81E1E, WHITE = 0xFFFFFFFF;
    /** A tornado this close turns the watch into a warning. */
    private static final double WARNING_RANGE = 3000;

    private static final String[] CHATTERS = {"StormFan42", "chaser_mom", "TornadoTim", "wx_nerd", "PlainsKid", "HailYeah", "DopplerDan", "funnelcloud9"};
    /** Viewer chat lines are lang keys broadcast.interceptors.chat.calm.0..8 and .tornado.0..8. */
    private static final int CALM_CHAT = 9, TORNADO_CHAT = 9;

    private BroadcastHud() {}

    /** Width of the right column, so other overlays can stay inside the video frame. */
    public static int columnWidth(int guiWidth) {
        return Math.max(110, guiWidth / 4);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.options.hideGui || mc.player == null || mc.level == null) return;
        Font font = mc.font;
        Level level = mc.level;
        int w = g.guiWidth(), h = g.guiHeight(), col = columnWidth(w);
        long time = level.getGameTime();
        Vec3 here = mc.player.position();
        Storms.StormInfo t = Storms.nearestTornado(level, here);
        List<Storms.StormInfo> storms = Storms.around(level, here);
        boolean warning = t != null && t.distance() < WARNING_RANGE;
        boolean watch = warning || t != null || !storms.isEmpty();

        topBar(g, font, mc.player.getName().getString(), w);
        frame(g, w - col, h);

        // Crew label, bottom-left of the video.
        String crew = crewName(mc);
        int vy = h - BOTTOM - 4;
        g.fill(8, vy - 14, 14 + font.width(crew) + 6, vy - 1, BLACK);
        g.drawString(font, crew, 12, vy - 11, WHITE, false);

        // Watch / warning box with the "counties" (biomes) under it, bottom-right of the video.
        if (watch) {
            String title = tr(warning ? "broadcast.interceptors.warning" : "broadcast.interceptors.watch");
            int bw = Math.max(120, (int) (font.width(title) * 1.5f) + 12), bx = w - col - bw - 8;
            List<String> counties = counties(level, here, t);
            int listH = counties.size() * 9 + 4, by = vy - 22 - listH;
            g.fill(bx, by, bx + bw, by + 20, warning ? RED : ORANGE);
            bigText(g, font, title, bx + bw / 2, by + 3, 1.5f);
            g.fill(bx, by + 20, bx + bw, by + 20 + listH, 0xE0101010);
            int ly = by + 23;
            for (String c : counties) {
                g.drawString(font, c, bx + 5, ly, 0xFFE0E0E0, false);
                ly += 9;
            }
        }

        rightColumn(g, font, level, here, storms, t, w - col, col, h, time);
        bottomStrip(g, font, level, here, t, storms, w, h, time, warning);
    }

    private static void topBar(GuiGraphics g, Font font, String player, int w) {
        g.fill(0, 0, w, TOP, ORANGE);
        g.fill(0, TOP - 2, w, TOP, BLACK);
        String[] boxes = {"@" + player.toUpperCase(), tr("broadcast.interceptors.discord"), tr("broadcast.interceptors.merch")};
        int x = 6;
        for (String b : boxes) {
            int bw = font.width(b) + 12;
            g.fill(x, 2, x + bw, TOP - 3, BLACK);
            g.fill(x + 3, TOP - 5, x + bw - 3, TOP - 4, 0xFF808080);
            g.drawString(font, b, x + 6, 4, WHITE, false);
            x += bw + 6;
        }
        // With the OBS link on, the tag tells the truth: LIVE while streaming, REC while recording, else OFFLINE.
        boolean obs = com.interceptors.client.ObsLink.connected();
        boolean offline = obs && !com.interceptors.client.ObsLink.streaming() && !com.interceptors.client.ObsLink.recording();
        String live = tr(!obs || com.interceptors.client.ObsLink.streaming() ? "broadcast.interceptors.live" : offline ? "broadcast.interceptors.offline" : "broadcast.interceptors.rec");
        int tag = offline ? 0xFF505050 : RED;
        int lx = w - font.width(live) - 18;
        g.fill(lx - 4, 2, w - 4, TOP - 3, tag);
        g.drawString(font, live, lx + 6, 4, WHITE, false);
        g.fill(lx, 6, lx + 4, 10, WHITE);
    }

    /** Thick orange and black frame around the game view. */
    private static void frame(GuiGraphics g, int right, int h) {
        int top = TOP, bottom = h - BOTTOM;
        int[][] rects = {{0, top, right, top + 4}, {0, bottom - 4, right, bottom}, {0, top, 4, bottom}, {right - 4, top, right, bottom}};
        for (int[] r : rects) g.fill(r[0], r[1], r[2], r[3], ORANGE);
        int[][] inner = {{4, top + 4, right - 4, top + 6}, {4, bottom - 6, right - 4, bottom - 4}, {4, top + 4, 6, bottom - 4}, {right - 6, top + 4, right - 4, bottom - 4}};
        for (int[] r : inner) g.fill(r[0], r[1], r[2], r[3], BLACK);
    }

    private static String crewName(Minecraft mc) {
        String team;
        if (!(mc.player.getVehicle() instanceof InterceptorVehicle v)) team = "chaser";
        else if (v.spec().id().startsWith("dominator")) team = "dominator";
        else if (v.spec().id().startsWith("tiv")) team = "tiv";
        else if (v.spec().id().equals("doppler_on_wheels")) team = "dow";
        else if (v.spec().id().equals("gaming_van")) team = "van";
        else team = "rental";
        return tr("broadcast.interceptors.team." + team);
    }

    /** Biomes standing in for counties: where you are, where the tornado is, and in between. */
    private static List<String> counties(Level level, Vec3 here, Storms.StormInfo t) {
        Set<String> names = new LinkedHashSet<>();
        names.add(biome(level, here));
        if (t != null) {
            names.add(biome(level, here.add(t.position().subtract(here).scale(0.5))));
            names.add(biome(level, t.position()));
        }
        List<String> out = new ArrayList<>();
        for (String n : names) out.add(tr("broadcast.interceptors.county", upper(n)));
        return out;
    }

    private static String biome(Level level, Vec3 pos) {
        return level.getBiome(BlockPos.containing(pos.x, 64, pos.z)).unwrapKey()
                .map(k -> Component.translatable(k.location().toLanguageKey("biome")).getString()).orElse(tr("broadcast.interceptors.unknown"));
    }

    private static void rightColumn(GuiGraphics g, Font font, Level level, Vec3 here, List<Storms.StormInfo> storms, Storms.StormInfo t,
                                    int x, int col, int h, long time) {
        int y = TOP, bottom = h - BOTTOM;
        g.fill(x, y, x + col, bottom, BLACK);

        // Velocity-style radar: storms as reflectivity blobs, tornadoes as tight red/green rotation couplets.
        int size = col - 8, rx = x + 4, ry = y + 4;
        g.fill(rx, ry, rx + size, ry + size, 0xFF0C1410);
        float cx = rx + size / 2f, cy = ry + size / 2f, range = 3000;
        g.enableScissor(rx, ry, rx + size, ry + size);
        for (Storms.StormInfo s : storms) {
            float px = cx + (float) ((s.position().x - here.x) / range * size / 2), py = cy + (float) ((s.position().z - here.z) / range * size / 2);
            if (s.tornado()) {
                // Inbound (green) on one side, outbound (red) on the other, spinning.
                double spin = time * 0.15;
                float r = Math.max(4, size / 14f);
                Draw.disc(g, px + (float) Math.cos(spin) * r * 0.6f, py + (float) Math.sin(spin) * r * 0.6f, r, 0xE020D040);
                Draw.disc(g, px - (float) Math.cos(spin) * r * 0.6f, py - (float) Math.sin(spin) * r * 0.6f, r, 0xE0E02020);
                Draw.ring(g, px, py, r * 2, 0xFFFFFFFF);
            } else {
                Draw.disc(g, px, py, Math.max(6, size / 8f), 0x6030A040);
                Draw.disc(g, px, py, Math.max(3, size / 16f), 0x80C0A020);
            }
        }
        g.disableScissor();
        Draw.ring(g, cx, cy, size / 2f - 1, 0x60FFFFFF);
        Draw.dot(g, cx, cy, 3, 0xFF30E0FF);
        g.drawString(font, tr("broadcast.interceptors.velocity"), rx + 3, ry + 3, 0xFFB0FFB0, false);
        y = ry + size + 6;

        // Follower counter: grows over the stream, faster when there's a tornado on the ground.
        long followers = 48_210 + time / 40 + (t != null ? time / 8 % 5_000 : 0);
        g.drawString(font, tr("broadcast.interceptors.followers"), x + 6, y, 0xFFA0A0A0, false);
        bigText(g, font, String.format("%,d", followers), x + col / 2, y + 10, 2f);
        y += 32;

        // Viewer chat, a new line every three seconds, excited when there's a tornado.
        g.fill(x + 4, y, x + col - 4, bottom - 4, 0xFF141414);
        g.drawString(font, tr("broadcast.interceptors.chat"), x + 8, y + 3, 0xFFF07A12, false);
        int lines = (bottom - 4 - (y + 14)) / 10;
        long tick = time / 60;
        int ly = y + 14;
        for (int i = lines - 1; i >= 0; i--) {
            long n = tick - i;
            String who = CHATTERS[(int) Math.floorMod(n * 7, CHATTERS.length)];
            boolean excited = t != null && n % 3 != 0;
            int pool = excited ? TORNADO_CHAT : CALM_CHAT;
            String what = tr("broadcast.interceptors.chat." + (excited ? "tornado." : "calm.") + Math.floorMod(n * 13, pool), t == null ? "" : t.efLabel());
            g.enableScissor(x + 6, ly, x + col - 6, ly + 10);
            g.drawString(font, who + ":", x + 8, ly, 0xFF30C0FF, false);
            g.drawString(font, what, x + 8 + font.width(who + ": "), ly, 0xFFE0E0E0, false);
            g.disableScissor();
            ly += 10;
        }
    }

    private static void bottomStrip(GuiGraphics g, Font font, Level level, Vec3 here, Storms.StormInfo t, List<Storms.StormInfo> storms,
                                    int w, int h, long time, boolean warning) {
        int y = h - BOTTOM;
        g.fill(0, y, w, h, BLACK);
        g.fill(0, y, w, y + 1, ORANGE);

        // Sponsor logo.
        g.fill(6, y + 4, 52, y + 20, ORANGE);
        g.drawString(font, tr("broadcast.interceptors.sponsor.1"), 10, y + 5, BLACK, false);
        g.drawString(font, tr("broadcast.interceptors.sponsor.2"), 10, y + 12, BLACK, false);

        // Watch number and countdown to the end of the in-game day.
        long day = level.getDayTime() / 24000;
        long left = 24000 - level.getDayTime() % 24000;
        long secs = left * 36 / 10; // one tick is 3.6 in-game seconds
        String watchNo = tr(warning ? "broadcast.interceptors.warning_no" : "broadcast.interceptors.watch_no", 300 + day);
        g.drawString(font, watchNo, 60, y + 4, 0xFFFF3030, false);
        g.drawString(font, tr("broadcast.interceptors.expires", String.format("%02d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60)), 60, y + 13, WHITE, false);

        // Chevron logo with the date and time above it.
        int mid = w / 2;
        long dayTime = level.getDayTime() % 24000;
        int hours = (int) ((dayTime / 1000 + 6) % 24), minutes = (int) (dayTime % 1000 * 60 / 1000);
        String date = Storms.date(level);
        String when = (date == null ? tr("broadcast.interceptors.day", day + 1) : date) + String.format("  %d:%02d", hours, minutes);
        g.drawString(font, when, mid - font.width(when) / 2, y + 3, WHITE, false);
        Draw.line(g, mid - 9, y + 13, mid, y + 19, 2, WHITE);
        Draw.line(g, mid, y + 19, mid + 9, y + 13, 2, WHITE);
        Draw.line(g, mid - 9, y + 17, mid, y + 23, 1, 0xFFF07A12);
        Draw.line(g, mid, y + 23, mid + 9, y + 17, 1, 0xFFF07A12);

        String thanks = tr("broadcast.interceptors.thanks");
        g.drawString(font, thanks, w - font.width(thanks) - 8, y + 8, WHITE, false);

        // Scrolling ticker along the very bottom.
        int ty = h - 10;
        g.fill(0, ty - 1, w, h, 0xFF1A1A1A);
        String ticker = ticker(level, here, t, storms, hours);
        int tw = font.width(ticker);
        int offset = (int) (time * 2 % (tw + w));
        g.drawString(font, ticker, w - offset, ty, WHITE, false);
    }

    private static String ticker(Level level, Vec3 here, Storms.StormInfo t, List<Storms.StormInfo> storms, int hour) {
        double wind = Storms.windAt(level, here.add(0, 1.6, 0)).horizontalDistance();
        StringBuilder s = new StringBuilder(tr("broadcast.interceptors.ticker.timeline", hour, (hour + 4) % 24));
        s.append(storms.isEmpty() ? tr("broadcast.interceptors.ticker.quiet") : tr("broadcast.interceptors.ticker.storms", storms.size()));
        if (t != null) {
            s.append("  ///  ").append(tr("broadcast.interceptors.ticker.tornado", t.efLabel(), t.windspeed(),
                    t.distance() >= 1000 ? String.format("%.1f KM", t.distance() / 1000) : (int) t.distance() + " M",
                    Storms.compassLabel(t.bearing()), Double.isNaN(t.heading()) ? "--" : Storms.compassLabel(t.heading()), (int) t.speed()));
        }
        int ef = WindPhysics.efRating(wind);
        s.append("  ///  ").append(tr("broadcast.interceptors.ticker.wind", Math.round(wind), ef < 0 ? "" : " (EF" + ef + ")"));
        s.append("  ///  ").append(upper(biome(level, here)));
        return s.toString();
    }

    /** Text drawn larger, centred on {@code centerX}. */
    private static void bigText(GuiGraphics g, Font font, String text, int centerX, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(centerX - font.width(text) * scale / 2, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, WHITE, true);
        g.pose().popPose();
    }

    private static String tr(String key, Object... args) {
        return net.minecraft.client.resources.language.I18n.get(key, args);
    }

    private static String upper(String s) {
        return s.toUpperCase(java.util.Locale.ROOT);
    }
}
