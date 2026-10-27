package com.interceptors.vehicle;

/** What the "DROP ANCHOR" panel says, like the overlay in Reed Timmer's videos. */
public enum AnchorStatus {
    /** Moving: can't drop yet. */
    STANDBY(0x9AA0A6),
    /** Stopped and ready to drop. */
    READY(0x40D060),
    DEPLOYING(0xFFC040),
    ANCHORED(0xFF8040),
    RETRACTING(0xFFC040),
    /** Hydraulics broken. */
    FAILED(0xFF3030),
    /** The anchor computer locked up: hold the anchor key to reboot. */
    FROZEN(0x30E0FF),
    REBOOTING(0x30A0FF),
    /** The vehicle has no anchors at all. */
    NONE(0x6A707A);

    private final int color;

    AnchorStatus(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public String key() {
        return "anchor.interceptors." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
