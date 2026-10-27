package com.interceptors.registry;

import com.interceptors.Interceptors;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Interceptors.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HORN = register("horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_DOWN = register("anchor_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_UP = register("anchor_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_ROAR = register("wind_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAIL = register("hail");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Interceptors.id(name)));
    }

    private ModSounds() {}
}
