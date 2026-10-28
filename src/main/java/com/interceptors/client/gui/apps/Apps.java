package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import com.interceptors.device.Device;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.VehicleSpec;

import java.util.ArrayList;
import java.util.List;

public final class Apps {
    private Apps() {}

    /** Which apps a device gets. The car display leads with the vehicle; games are for computers and the gaming van. */
    public static List<App> forDevice(Device device, DeviceScreen screen) {
        List<App> apps = new ArrayList<>();
        InterceptorVehicle v = screen.vehicle();
        if (device == Device.CAR_DISPLAY || device == Device.PASSENGER_LAPTOP) apps.add(new VehicleApp());
        apps.add(new RadarApp());
        apps.add(new InterceptApp());
        apps.add(new StormListApp());
        apps.add(new ChopperCamApp());
        apps.add(new ProbeApp());
        if (device != Device.CAR_DISPLAY || v != null && v.spec() == VehicleSpec.GAMING_VAN) apps.add(new GameApp());
        if (device != Device.CAR_DISPLAY) apps.add(new WebApp());
        return apps;
    }
}
