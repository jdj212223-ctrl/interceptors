package com.interceptors.client.compat;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A live web page drawn into part of a screen. Only {@link McefBrowserView} implements it, and that class is only
 * loaded when MCEF is installed, so Interceptors still runs without it.
 */
public interface BrowserView {
    /** Draws the page into the GUI rectangle, resizing the browser to match it. */
    void render(GuiGraphics g, int x, int y, int w, int h);

    void mouseMoved(double x, double y);

    void mousePressed(double x, double y, int button);

    void mouseReleased(double x, double y, int button);

    void mouseScrolled(double x, double y, double amount);

    void keyPressed(int key, int scancode, int modifiers);

    void keyReleased(int key, int scancode, int modifiers);

    void charTyped(char c, int modifiers);

    void load(String url);

    void back();

    void forward();

    void reload();

    String url();

    void close();

    /** Makes a browser if MCEF is installed and ready, else null. */
    static BrowserView create(String url) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mcef")) return null;
        return McefBrowserView.create(url);
    }
}
