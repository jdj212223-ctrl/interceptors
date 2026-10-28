package com.interceptors.compat;

import com.interceptors.vehicle.WindPhysics;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.weather.Storm;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/** Everything Interceptors reads from ProtoManly's Weather goes through here, so one place knows its API. */
public final class Storms {
    /** Set by the client: PMWeather keeps the client's handler somewhere client-only. */
    public static Supplier<WeatherHandler> clientHandler = () -> null;

    private Storms() {}

    public static WeatherHandler handler(Level level) {
        if (level.isClientSide) return clientHandler.get();
        return GameBusEvents.MANAGERS.get(level.dimension());
    }

    /** All wind at a spot in mph (tornado, storm and background), or zero if PMWeather can't say. */
    public static Vec3 windAt(Level level, Vec3 pos) {
        try {
            if (handler(level) == null) return Vec3.ZERO;
            Vec3 wind = WindEngine.getWind(pos, level);
            return wind == null || !Double.isFinite(wind.lengthSqr()) ? Vec3.ZERO : wind;
        } catch (Throwable t) {
            return Vec3.ZERO;
        }
    }

    /** The first year of a world on PMWeather's calendar: it's 2026, like real life. */
    public static final int FIRST_YEAR = 2026;

    /**
     * Today's date on PMWeather's calendar with the year added, like "MAY 5, 2026". The year counts up every 12
     * PMWeather months. Null if the calendar can't be read.
     */
    public static String date(Level level) {
        try {
            int month = dev.protomanly.pmweather.seasons.SeasonHandler.getMonth(level);
            int day = dev.protomanly.pmweather.seasons.SeasonHandler.getDayInMonth(level);
            int year = FIRST_YEAR + dev.protomanly.pmweather.seasons.SeasonHandler.getYear(level);
            String name = dev.protomanly.pmweather.seasons.SeasonHandler.getMonthName(month);
            return (name == null ? "MONTH " + month : name.toUpperCase()) + " " + day + ", " + year;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Hail intensity at a spot, 0..1. */
    public static float hailAt(Level level, Vec3 pos) {
        try {
            WeatherHandler handler = handler(level);
            return handler == null ? 0 : handler.getHail(pos);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * One storm as seen from where you're standing.
     *
     * @param bearing  degrees clockwise from north to the storm
     * @param heading  degrees clockwise from north the storm is moving, NaN if it's still
     * @param speed    storm motion in mph
     * @param velocity storm motion in blocks per tick
     */
    public record StormInfo(String type, boolean tornado, int windspeed, int ef, int stage, float width,
                            double distance, double bearing, double heading, double speed, Vec3 position, Vec3 velocity) {
        public String efLabel() {
            return ef < 0 ? "--" : "EF" + ef;
        }

        /** How fast it's coming at {@code from}, mph; negative when it's moving away. */
        public double closingMph(Vec3 from) {
            Vec3 toYou = new Vec3(from.x - position.x, 0, from.z - position.z);
            if (toYou.lengthSqr() < 1e-6) return 0;
            return WindPhysics.toMph(toYou.normalize().dot(new Vec3(velocity.x, 0, velocity.z)));
        }
    }

    /** Live storms, nearest first; tornadoes before plain storms at the same distance. */
    public static List<StormInfo> around(Level level, Vec3 from) {
        List<StormInfo> out = new ArrayList<>();
        WeatherHandler handler = handler(level);
        if (handler == null) return out;
        try {
            for (Storm s : new ArrayList<>(handler.getStorms())) {
                if (s == null || s.dead || s.position == null) continue;
                Vec3 d = s.position.subtract(from);
                double dist = Math.sqrt(d.x * d.x + d.z * d.z);
                boolean tornado = s.isTornadic();
                Vec3 v = s.velocity == null ? Vec3.ZERO : s.velocity;
                double motion = Math.sqrt(v.x * v.x + v.z * v.z);
                out.add(new StormInfo(s.stormType == null ? "storm" : s.stormType.getId(), tornado, s.windspeed,
                        tornado ? WindPhysics.efRating(s.windspeed) : -1, s.stage, s.width, dist, compass(d.x, d.z),
                        motion < 1e-4 ? Double.NaN : compass(v.x, v.z), WindPhysics.toMph(motion), s.position, v));
            }
        } catch (Throwable ignored) {
            // A storm changing under us mid-read just means one stale frame.
        }
        out.sort(Comparator.comparing((StormInfo i) -> !i.tornado()).thenComparingDouble(StormInfo::distance));
        return out;
    }

    /** The nearest tornado, or null. */
    public static StormInfo nearestTornado(Level level, Vec3 from) {
        for (StormInfo i : around(level, from)) if (i.tornado()) return i;
        return null;
    }

    /** Degrees clockwise from north (-Z) for a flat direction. */
    public static double compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        return a < 0 ? a + 360 : a;
    }

    public static String compassName(double deg) {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[Math.floorMod((int) Math.round(deg / 45.0), 8)];
    }
}
