package com.interceptors.client.gui.apps;

import com.interceptors.client.gui.DeviceScreen;
import net.minecraft.client.gui.GuiGraphics;

/** One app on a device's dock. Coordinates passed in are the app's area of the screen. */
public interface App {
    /** Language key suffix and dock label: app.interceptors.&lt;id&gt;. */
    String id();

    /** Dock icon colour. */
    int color();

    void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick);

    default void tick(DeviceScreen screen) {}

    default boolean mouseClicked(DeviceScreen screen, double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(DeviceScreen screen, double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(DeviceScreen screen, double mouseX, double mouseY, int button, double dx, double dy) {
        return false;
    }

    default boolean mouseScrolled(DeviceScreen screen, double mouseX, double mouseY, double amount) {
        return false;
    }

    default boolean keyPressed(DeviceScreen screen, int key, int scancode, int modifiers) {
        return false;
    }

    default boolean keyReleased(DeviceScreen screen, int key, int scancode, int modifiers) {
        return false;
    }

    default boolean charTyped(DeviceScreen screen, char c, int modifiers) {
        return false;
    }

    /** Called when the app is switched away from or the device closes. */
    default void close() {}
}
