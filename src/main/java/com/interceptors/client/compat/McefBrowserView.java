package com.interceptors.client.compat;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * An MCEF (Chromium) browser shown inside a StormOS screen. GUI coordinates are converted to real window pixels
 * for the browser, which renders off-screen into a texture we draw as a quad.
 */
final class McefBrowserView implements BrowserView {
    private final MCEFBrowser browser;
    private int x, y, pixelW, pixelH;

    private McefBrowserView(MCEFBrowser browser) {
        this.browser = browser;
    }

    static BrowserView create(String url) {
        if (!MCEF.isInitialized()) return null;
        MCEFBrowser browser = MCEF.createBrowser(url, false);
        return browser == null ? null : new McefBrowserView(browser);
    }

    private static double scale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    private int px(double guiX) {
        return (int) ((guiX - x) * scale());
    }

    private int py(double guiY) {
        return (int) ((guiY - y) * scale());
    }

    @Override
    public void render(GuiGraphics g, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        int pw = Math.max(1, (int) (w * scale())), ph = Math.max(1, (int) (h * scale()));
        if (pw != pixelW || ph != pixelH) {
            pixelW = pw;
            pixelH = ph;
            browser.resize(pw, ph);
        }
        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.addVertex(m, x, y + h, 0).setUv(0, 1).setColor(255, 255, 255, 255);
        buf.addVertex(m, x + w, y + h, 0).setUv(1, 1).setColor(255, 255, 255, 255);
        buf.addVertex(m, x + w, y, 0).setUv(1, 0).setColor(255, 255, 255, 255);
        buf.addVertex(m, x, y, 0).setUv(0, 0).setColor(255, 255, 255, 255);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }

    @Override
    public void mouseMoved(double mx, double my) {
        browser.sendMouseMove(px(mx), py(my));
    }

    @Override
    public void mousePressed(double mx, double my, int button) {
        browser.setFocus(true);
        browser.sendMousePress(px(mx), py(my), button);
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        browser.sendMouseRelease(px(mx), py(my), button);
    }

    @Override
    public void mouseScrolled(double mx, double my, double amount) {
        browser.sendMouseWheel(px(mx), py(my), amount, 0);
    }

    @Override
    public void keyPressed(int key, int scancode, int modifiers) {
        browser.setFocus(true);
        browser.sendKeyPress(key, scancode, modifiers);
    }

    @Override
    public void keyReleased(int key, int scancode, int modifiers) {
        browser.sendKeyRelease(key, scancode, modifiers);
    }

    @Override
    public void charTyped(char c, int modifiers) {
        if (c != 0) browser.sendKeyTyped(c, modifiers);
    }

    @Override
    public void load(String url) {
        browser.loadURL(url);
    }

    @Override
    public void back() {
        if (browser.canGoBack()) browser.goBack();
    }

    @Override
    public void forward() {
        browser.goForward();
    }

    @Override
    public void reload() {
        browser.reload();
    }

    @Override
    public String url() {
        String u = browser.getURL();
        return u == null ? "" : u;
    }

    @Override
    public void close() {
        browser.close();
    }
}
