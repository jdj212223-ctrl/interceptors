package com.interceptors.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Your own settings, in config/interceptors-client.toml in the game folder. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CLEAN_HUD;
    public static final ModConfigSpec.BooleanValue BLOOD;
    public static final ModConfigSpec.BooleanValue OBS_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> OBS_HOST;
    public static final ModConfigSpec.IntValue OBS_PORT;
    public static final ModConfigSpec.ConfigValue<String> OBS_PASSWORD;
    public static final ModConfigSpec.ConfigValue<String> OBS_CHOPPER_SCENE;
    public static final ModConfigSpec.ConfigValue<String> OBS_MAIN_SCENE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.translation("interceptors.configuration.gore").push("gore");
        BLOOD = b.comment("Show blocky pixel blood: glass cuts, hail wounds, drips and the red flash. Off = no blood at all (you still get hurt).")
                .translation("interceptors.configuration.gore.blood").define("blood", true);
        b.pop();
        b.translation("interceptors.configuration.stream").push("stream");
        CLEAN_HUD = b.comment("While the stream overlay (B) is on, hide the hotbar, health, hunger, XP and crosshair so the capture looks clean.")
                .translation("interceptors.configuration.stream.cleanHud").define("cleanHud", true);
        b.pop();
        b.translation("interceptors.configuration.obs").push("obs");
        b.comment("Optional link to OBS Studio's WebSocket server (OBS: Tools > WebSocket Server Settings).",
                "Interceptors only talks to OBS on this computer: it never streams by itself and never sees a stream key.");
        OBS_ENABLED = b.comment("Turn the OBS link on.").translation("interceptors.configuration.obs.enabled").define("enabled", false);
        OBS_HOST = b.comment("Where OBS runs. Leave as localhost.").translation("interceptors.configuration.obs.host").define("host", "localhost");
        OBS_PORT = b.comment("OBS WebSocket port (OBS's default is 4455).").translation("interceptors.configuration.obs.port").defineInRange("port", 4455, 1, 65535);
        OBS_PASSWORD = b.comment("The OBS WebSocket server password (not your stream key!). Empty if you turned authentication off.")
                .translation("interceptors.configuration.obs.password").define("password", "");
        OBS_CHOPPER_SCENE = b.comment("OBS scene to switch to when you open the Chopper Cam app. Empty = don't switch.")
                .translation("interceptors.configuration.obs.chopperScene").define("chopperScene", "");
        OBS_MAIN_SCENE = b.comment("OBS scene to switch back to when you leave the Chopper Cam app. Empty = don't switch back.")
                .translation("interceptors.configuration.obs.mainScene").define("mainScene", "");
        b.pop();
        SPEC = b.build();
    }

    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private ClientConfig() {}
}
