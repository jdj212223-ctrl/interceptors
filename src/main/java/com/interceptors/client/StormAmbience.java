package com.interceptors.client;

import com.interceptors.compat.Storms;
import com.interceptors.registry.ModSounds;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.Part;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Two loops that follow the camera: the deafening roar of tornado wind, louder the closer you are to the core,
 * and hail hammering down (loudest on a vehicle's roof). Intact windows muffle the roar a little.
 */
public final class StormAmbience {
    private static Loop roar, hail;
    /** What the loops should be at right now, 0..1. Updated each client tick. */
    static float roarLevel, hailLevel;

    private StormAmbience() {}

    static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            roarLevel = hailLevel = exposedHail = 0;
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        double wind = Storms.windAt(mc.level, eye).horizontalDistance();
        boolean sheltered = mc.player.getVehicle() instanceof InterceptorVehicle v && !v.failed(Part.WINDOWS);
        roarLevel = Mth.clamp((float) ((wind - 55) / 165), 0, 1) * (sheltered ? 0.7f : 1f);
        float h = Storms.hailAt(mc.level, eye);
        boolean underSky = mc.level.canSeeSky(mc.player.blockPosition().above());
        hailLevel = mc.player.getVehicle() instanceof InterceptorVehicle ? h : underSky ? h * 0.7f : h * 0.2f;
        // What you can actually see: nothing indoors or behind a vehicle's glass (that shows as ice on the windows).
        exposedHail = underSky && !sheltered ? h : 0;
        roar = keep(mc, roar, ModSounds.WIND_ROAR.get(), roarLevel, true);
        hail = keep(mc, hail, ModSounds.HAIL.get(), hailLevel, false);
    }

    private static Loop keep(Minecraft mc, Loop loop, SoundEvent sound, float level, boolean isRoar) {
        if (level > 0.02f && (loop == null || loop.isStopped())) {
            loop = new Loop(sound, isRoar);
            mc.getSoundManager().play(loop);
        }
        return loop;
    }

    public static float roarLevel() {
        return roarLevel;
    }

    static float exposedHail;

    /** Hail you're standing out in, under open sky (or with the windows blown out): drives the haze and streaks. */
    public static float exposedHail() {
        return exposedHail;
    }

    public static float hailLevel() {
        return hailLevel;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final boolean isRoar;

        Loop(SoundEvent sound, boolean isRoar) {
            super(sound, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            this.isRoar = isRoar;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.01f;
        }

        @Override
        public void tick() {
            float target = isRoar ? roarLevel : hailLevel;
            volume = Mth.lerp(0.1f, volume, target * (isRoar ? 1.6f : 1.0f));
            pitch = isRoar ? 0.7f + 0.4f * target : 1.0f;
            if (target <= 0.01f && volume < 0.02f) stop();
        }
    }
}
