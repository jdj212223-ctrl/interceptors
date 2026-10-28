package com.interceptors.client.gui;

import com.interceptors.vehicle.VehicleSpec;

/**
 * Each vehicle's own dash look, matching what's really in it: the same apps underneath, but the car display's
 * frame, colours and lettering, the dashboard overlay, and the screen in the cab change per vehicle.
 */
public enum DashTheme {
    /** Dominators: rugged mounted laptops and a weather-station readout. Toughbook silver, orange accents. */
    DOMINATOR(0xFFB8BCC0, 0xFF14161A, 0xFF22262C, 0xFFFF8C1A, 0xFFF0F0F0, false),
    /** TIVs: military green phosphor on black. */
    MILITARY(0xFF3A4030, 0xFF020602, 0xFF0A140A, 0xFF33FF66, 0xFF33FF66, true),
    /** Doppler on Wheels: a radar operator's console. */
    RADAR(0xFF2A3440, 0xFF061024, 0xFF0C1A34, 0xFF30E0FF, 0xFFD0F0FF, false),
    /** Frantic Dan's van: the RGB gaming setup. */
    GAMING(0xFF101010, 0xFF140A1E, 0xFF22122E, 0xFFB040FF, 0xFFFFFFFF, false),
    /** Rental car: a plain stock infotainment screen. */
    STOCK(0xFF1A1A1A, 0xFF1E2430, 0xFF2A303C, 0xFFDDE4EE, 0xFFE8ECF0, false),
    /** Storm Chopper 4: a helicopter glass cockpit. */
    COCKPIT(0xFF24272B, 0xFF050608, 0xFF101418, 0xFF30FF80, 0xFFFFFFFF, false);

    /** Bezel/frame, screen background, panel, accent, text colours (ARGB). */
    public final int frame, background, panel, accent, text;
    /** All caps, phosphor style. */
    public final boolean military;

    DashTheme(int frame, int background, int panel, int accent, int text, boolean military) {
        this.frame = frame;
        this.background = background;
        this.panel = panel;
        this.accent = accent;
        this.text = text;
        this.military = military;
    }

    public static DashTheme of(VehicleSpec spec) {
        String id = spec.id();
        if (id.startsWith("dominator")) return DOMINATOR;
        if (id.startsWith("tiv")) return MILITARY;
        if (spec == VehicleSpec.DOPPLER_ON_WHEELS) return RADAR;
        if (spec == VehicleSpec.GAMING_VAN) return GAMING;
        if (spec == VehicleSpec.RENTAL_CAR) return STOCK;
        if (spec.flies()) return COCKPIT;
        return DOMINATOR;
    }

    /** The accent for this frame: the gaming van's cycles through the rainbow. */
    public int accent(long time) {
        if (this != GAMING) return accent;
        return 0xFF000000 | net.minecraft.util.Mth.hsvToRgb((time % 120) / 120f, 0.75f, 1f);
    }

    public String format(String s) {
        return military ? s.toUpperCase(java.util.Locale.ROOT) : s;
    }
}
