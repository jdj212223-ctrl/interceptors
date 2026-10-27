package com.interceptors.vehicle;

/**
 * Parts that wear out in a storm and can fail, each with a condition from 0 to 100.
 *
 * @param stormWear how fast storm wind wears it, relative to the others
 */
public enum Part {
    /** Drives the anchors and skirts. Failed: they won't deploy, and pop back up if they were down. */
    HYDRAULICS(1.0f),
    /** Failed: a flat, so it crawls. */
    TIRES(0.6f),
    /** Warning: sputters. Failed: stalls. */
    ENGINE(0.5f),
    /** Hail cracks them too. Failed: the wind gets in and hurts the riders. */
    WINDOWS(1.0f),
    /** The anchor computer and the dash screens. Warning: freezes more. Failed: dead until repaired. */
    COMPUTER(0.7f);

    public static final int WARN_BELOW = 60, FAIL_BELOW = 25;

    private final float stormWear;

    Part(float stormWear) {
        this.stormWear = stormWear;
    }

    public float stormWear() {
        return stormWear;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static boolean failed(int condition) {
        return condition < FAIL_BELOW;
    }

    public static boolean warning(int condition) {
        return condition < WARN_BELOW;
    }
}
