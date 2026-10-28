package com.interceptors.vehicle;

import java.util.function.BooleanSupplier;

/** Climb and descend keys for whoever is flying, set by the client (Space to climb, X to descend by default). */
public final class FlightControls {
    public static BooleanSupplier ascend = () -> false;
    public static BooleanSupplier descend = () -> false;

    private FlightControls() {}
}
