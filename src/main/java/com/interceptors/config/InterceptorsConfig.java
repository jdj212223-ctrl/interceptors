package com.interceptors.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings, in config/interceptors-server.toml inside each world (or defaultconfigs for new worlds). */
public final class InterceptorsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FAILURES;
    public static final ModConfigSpec.DoubleValue WEAR_RATE;
    public static final ModConfigSpec.DoubleValue FREEZE_RATE;
    public static final ModConfigSpec.BooleanValue HAIL_INJURIES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("failures");
        FAILURES = b.comment("Parts (hydraulics, tires, engine, windows, anchor computer) wear out and fail in storms.")
                .define("enabled", true);
        WEAR_RATE = b.comment("How fast parts wear in strong wind and hail. 0.5 = half as fast, 2 = twice as fast.")
                .defineInRange("wearRate", 1.0, 0.0, 5.0);
        FREEZE_RATE = b.comment("How often the anchor computer freezes in a storm. 0 turns freezing off.")
                .defineInRange("freezeRate", 1.0, 0.0, 5.0);
        b.pop();
        b.push("injuries");
        HAIL_INJURIES = b.comment("Giant hail can lodge in a player caught outside. It melts out by itself; pulling it out early makes it bleed.")
                .define("hailInjuries", true);
        b.pop();
        SPEC = b.build();
    }

    /** Reads safely even before the config has loaded (GameTests, early ticks). */
    public static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static boolean get(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private InterceptorsConfig() {}
}
