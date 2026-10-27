package com.interceptors.device;

/** The screens you can chase storms on. They all run the same apps, in different frames. */
public enum Device {
    COMPUTER("computer"),
    LAPTOP("laptop"),
    MACBOOK("macbook"),
    IPAD("ipad"),
    CAR_DISPLAY("car_display");

    private final String id;

    Device(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /**
     * Opens a device's screen on this computer. Set by the client; a no-op on a dedicated server, which never
     * gets asked anyway because blocks and items only call it on the client side.
     */
    public static java.util.function.Consumer<Device> opener = device -> {};

    public void open() {
        opener.accept(this);
    }
}
