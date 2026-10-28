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
        if (device == Device.CAR_DISPLAY && v != null) return carDisplay(v);
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

    /** Each vehicle's own car display: the same apps, picked and ordered for what's really in it. */
    private static List<App> carDisplay(InterceptorVehicle v) {
        List<App> apps = new ArrayList<>();
        switch (com.interceptors.client.gui.DashTheme.of(v.spec())) {
            case DOMINATOR -> {
                apps.add(new VehicleApp());
                apps.add(new WeatherStationApp());
                apps.add(new RadarApp());
                apps.add(new InterceptApp());
                apps.add(new StormListApp());
                if (v.spec().probes()) apps.add(new ProbeApp());
                apps.add(new ChopperCamApp());
            }
            case MILITARY -> {
                apps.add(new VehicleApp());
                apps.add(new RadarApp());
                apps.add(new InterceptApp());
                apps.add(new StormListApp());
                apps.add(new ChopperCamApp());
            }
            case RADAR -> {
                apps.add(new VehicleApp());
                apps.add(new RadarConsoleApp());
                apps.add(new InterceptApp());
                apps.add(new StormListApp());
                apps.add(new ChopperCamApp());
            }
            case GAMING -> {
                apps.add(new GameApp());
                apps.add(new VehicleApp());
                apps.add(new RadarApp());
                apps.add(new InterceptApp());
                apps.add(new StormListApp());
                apps.add(new ChopperCamApp());
            }
            case STOCK -> {
                apps.add(new VehicleApp());
                apps.add(new RadioApp());
            }
            case COCKPIT -> {
                apps.add(new AvionicsApp());
                apps.add(new ChopperCamApp());
                apps.add(new RadarApp());
                apps.add(new InterceptApp());
                apps.add(new StormListApp());
            }
        }
        return apps;
    }
}
