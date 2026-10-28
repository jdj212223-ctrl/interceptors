package com.interceptors.compat;

import com.interceptors.Interceptors;
import com.interceptors.client.StormAmbience;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simple Voice Chat add-on (only loaded when Voice Chat is installed, which finds it by its annotation): voices
 * you hear get wrecked by the wind near a tornado, crunchy, crackling and fluttering like a phone held into the
 * gale, more the louder the roar around you.
 */
@ForgeVoicechatPlugin
public class VoiceWindPlugin implements VoicechatPlugin {
    /** Roar level (0..1) below which voices come through clean. */
    private static final float CLEAN_BELOW = 0.15f;

    @Override
    public String getPluginId() {
        return Interceptors.MODID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, VoiceWindPlugin::distort);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, VoiceWindPlugin::distort);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, VoiceWindPlugin::distort);
    }

    private static void distort(ClientReceiveSoundEvent event) {
        float wind = StormAmbience.roarLevel();
        if (wind < CLEAN_BELOW) return;
        float amount = Math.min(1, (wind - CLEAN_BELOW) / (1 - CLEAN_BELOW));
        short[] in = event.getRawAudio();
        if (in == null) return;
        short[] out = new short[in.length];
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int crush = 2 + Math.round(amount * 9); // fewer bits: crunchier
        double phase = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < in.length; i++) {
            int s = (in[i] >> crush) << crush;
            double flutter = 1 - amount * 0.6 * (0.5 + 0.5 * Math.sin(phase + i * 0.004));
            s = (int) (s * flutter + rng.nextGaussian() * 1800 * amount);
            if (rng.nextFloat() < amount * 0.002) s = rng.nextBoolean() ? 22000 : -22000; // crackle
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
        }
        event.setRawAudio(out);
    }
}
