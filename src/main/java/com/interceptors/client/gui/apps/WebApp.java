package com.interceptors.client.gui.apps;

import com.interceptors.client.compat.BrowserView;
import com.interceptors.client.gui.DeviceScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * A real web browser when the optional MCEF mod is installed: back, forward, reload, home, an address bar that
 * also searches, quick links for storm sites, and mouse and keyboard straight through to the page. Without MCEF
 * it explains what to install.
 */
public class WebApp implements App {
    private static final String HOME = "https://www.google.com";
    private static final String[][] LINKS = {{"YT", "https://www.youtube.com"}, {"NWS", "https://radar.weather.gov"}, {"SPC", "https://www.spc.noaa.gov"}};
    private static final int BAR = 14;
    /** Where you were, so the page comes back when you reopen the app. */
    private static String lastUrl = HOME;

    private BrowserView view;
    private boolean addressFocused, pageFocused;
    private String address = "";
    private int bx, by, bw, bh;

    @Override
    public boolean needsInternet() {
        return true;
    }

    @Override
    public String id() {
        return "web";
    }

    @Override
    public int color() {
        return 0x2A8AE0;
    }

    @Override
    public void tick(DeviceScreen screen) {
        if (view == null && ModList.get().isLoaded("mcef")) view = BrowserView.create(lastUrl);
    }

    @Override
    public void render(GuiGraphics g, DeviceScreen screen, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        g.fill(x, y, x + w, y + h, 0xFFF2F4F7);
        if (view == null) {
            String key = ModList.get().isLoaded("mcef") ? "app.interceptors.web.starting" : "app.interceptors.web.missing";
            int line = y + 24;
            g.drawString(font, Component.translatable("app.interceptors.web.title"), x + 8, y + 8, 0x202428, false);
            for (var part : font.split(Component.translatable(key), w - 16)) {
                g.drawString(font, part, x + 8, line, 0x404850, false);
                line += 10;
            }
            return;
        }

        // Toolbar: back, forward, reload, home, address bar, quick links.
        g.fill(x, y, x + w, y + BAR, 0xFFDDE1E6);
        String[] buttons = {"<", ">", "R", "H"};
        for (int i = 0; i < buttons.length; i++) {
            int cx = x + 2 + i * 13;
            g.fill(cx, y + 2, cx + 11, y + BAR - 2, hover(mouseX, mouseY, cx, y + 2, 11, BAR - 4) ? 0xFFB8C0CA : 0xFFC8D0D8);
            g.drawString(font, buttons[i], cx + 6 - font.width(buttons[i]) / 2, y + 3, 0x202428, false);
        }
        int linksW = 0;
        for (String[] link : LINKS) linksW += font.width(link[0]) + 8;
        int ax = x + 2 + 4 * 13 + 2, aw = w - (ax - x) - linksW - 4;
        g.fill(ax, y + 2, ax + aw, y + BAR - 2, addressFocused ? 0xFFFFFFFF : 0xFFF6F8FA);
        String shown = addressFocused ? address + (Minecraft.getInstance().level.getGameTime() / 10 % 2 == 0 ? "_" : "") : view.url();
        g.enableScissor(ax + 2, y + 2, ax + aw - 2, y + BAR - 2);
        int textX = ax + 3 + Math.min(0, aw - 8 - font.width(shown));
        g.drawString(font, shown, textX, y + 3, 0x202428, false);
        g.disableScissor();
        int lx = ax + aw + 2;
        for (String[] link : LINKS) {
            int lw = font.width(link[0]) + 6;
            g.fill(lx, y + 2, lx + lw, y + BAR - 2, hover(mouseX, mouseY, lx, y + 2, lw, BAR - 4) ? 0xFF2A6AC0 : 0xFF2A8AE0);
            g.drawString(font, link[0], lx + 3, y + 3, 0xFFFFFF, false);
            lx += lw + 2;
        }

        bx = x;
        by = y + BAR;
        bw = w;
        bh = h - BAR;
        view.render(g, bx, by, bw, bh);
    }

    private static boolean hover(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean inPage(double mx, double my) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    @Override
    public boolean mouseClicked(DeviceScreen screen, double mx, double my, int button) {
        if (view == null) return false;
        if (inPage(mx, my)) {
            addressFocused = false;
            pageFocused = true;
            view.mousePressed(mx, my, button);
            return true;
        }
        pageFocused = false;
        Font font = Minecraft.getInstance().font;
        int x = screen.appX(), y = screen.appY();
        for (int i = 0; i < 4; i++) {
            if (hover(mx, my, x + 2 + i * 13, y + 2, 11, BAR - 4)) {
                switch (i) {
                    case 0 -> view.back();
                    case 1 -> view.forward();
                    case 2 -> view.reload();
                    default -> view.load(HOME);
                }
                addressFocused = false;
                return true;
            }
        }
        int linksW = 0;
        for (String[] link : LINKS) linksW += font.width(link[0]) + 8;
        int ax = x + 2 + 4 * 13 + 2, aw = screen.appW() - (ax - x) - linksW - 4;
        if (hover(mx, my, ax, y + 2, aw, BAR - 4)) {
            addressFocused = true;
            address = view.url();
            return true;
        }
        int lx = ax + aw + 2;
        for (String[] link : LINKS) {
            int lw = font.width(link[0]) + 6;
            if (hover(mx, my, lx, y + 2, lw, BAR - 4)) {
                view.load(link[1]);
                addressFocused = false;
                return true;
            }
            lx += lw + 2;
        }
        addressFocused = false;
        return false;
    }

    @Override
    public boolean mouseReleased(DeviceScreen screen, double mx, double my, int button) {
        if (view == null || !inPage(mx, my)) return false;
        view.mouseReleased(mx, my, button);
        return true;
    }

    @Override
    public void mouseMoved(DeviceScreen screen, double mx, double my) {
        if (view != null && inPage(mx, my)) view.mouseMoved(mx, my);
    }

    @Override
    public boolean mouseDragged(DeviceScreen screen, double mx, double my, int button, double dx, double dy) {
        if (view == null || !inPage(mx, my)) return false;
        view.mouseMoved(mx, my);
        return true;
    }

    @Override
    public boolean mouseScrolled(DeviceScreen screen, double mx, double my, double amount) {
        if (view == null || !inPage(mx, my)) return false;
        view.mouseScrolled(mx, my, amount);
        return true;
    }

    @Override
    public boolean keyPressed(DeviceScreen screen, int key, int scancode, int modifiers) {
        if (view == null) return false;
        if (addressFocused) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                view.load(toUrl(address));
                addressFocused = false;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !address.isEmpty()) {
                address = address.substring(0, address.length() - 1);
            } else if (key == GLFW.GLFW_KEY_V && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
                address += Minecraft.getInstance().keyboardHandler.getClipboard();
            }
            return true;
        }
        if (pageFocused) {
            view.keyPressed(key, scancode, modifiers);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(DeviceScreen screen, int key, int scancode, int modifiers) {
        if (view == null || !pageFocused) return false;
        view.keyReleased(key, scancode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(DeviceScreen screen, char c, int modifiers) {
        if (view == null) return false;
        if (addressFocused) {
            if (c >= ' ') address += c;
            return true;
        }
        if (pageFocused) {
            view.charTyped(c, modifiers);
            return true;
        }
        return false;
    }

    /** "radar.weather.gov" becomes a URL; anything with spaces or no dot becomes a Google search. */
    static String toUrl(String typed) {
        String t = typed.trim();
        if (t.contains("://")) return t;
        if (t.contains(".") && !t.contains(" ")) return "https://" + t;
        return "https://www.google.com/search?q=" + URLEncoder.encode(t, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        if (view != null) {
            lastUrl = view.url().isEmpty() ? lastUrl : view.url();
            view.close();
            view = null;
        }
        addressFocused = pageFocused = false;
    }
}
