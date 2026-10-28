package com.interceptors.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Your own settings, in config/interceptors-client.toml in the game folder. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CLEAN_HUD;
    public static final ModConfigSpec.BooleanValue OBS_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> OBS_HOST;
    public static final ModConfigSpec.IntValue OBS_PORT;
    public static final ModConfigSpec.ConfigValue<String> OBS_PASSWORD;
    public static final ModConfigSpec.ConfigValue<String> OBS_CHOPPER_SCENE;
    public static final ModConfigSpec.ConfigValue<String> OBS_MAIN_SCENE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("stream");
        CLEAN_HUD = b.comment("While the stream overlay (B) is on, hide the hotbar, health, hunger, XP and crosshair so the capture looks clean.")
                .define("cleanHud", true);
        b.pop();
        b.push("obs");
        b.comment("Optional link to OBS Studio's WebSocket server (OBS: Tools > WebSocket Server Settings).",
                "Interceptors only talks to OBS on this computer: it never streams by itself and never sees a stream key.");
        OBS_ENABLED = b.comment("Turn the OBS link on.").define("enabled", false);
        OBS_HOST = b.comment("Where OBS runs. Leave as localhost.").define("host", "localhost");
        OBS_PORT = b.comment("OBS WebSocket port (OBS's default is 4455).").defineInRange("port", 4455, 1, 65535);
        OBS_PASSWORD = b.comment("The OBS WebSocket server password (not your stream key!). Empty if you turned authentication off.")
                .define("password", "");
        OBS_CHOPPER_SCENE = b.comment("OBS scene to switch to when you open the Chopper Cam app. Empty = don't switch.")
                .define("chopperScene", "");
        OBS_MAIN_SCENE = b.comment("OBS scene to switch back to when you leave the Chopper Cam app. Empty = don't switch back.")
                .define("mainScene", "");
        b.pop();
        SPEC = b.build();
    }

    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private ClientConfig() {}
}
