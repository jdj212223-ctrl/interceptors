package com.interceptors.client.render;

import com.interceptors.Interceptors;
import net.minecraft.resources.ResourceLocation;

/**
 * Realistic surface materials the vehicle boxes are made of (textures from tools/gen_assets.py, 64 pixels per
 * block). WORLD materials repeat across a box at true scale with the road at the bottom of the texture, so mud
 * sits low and panel seams line up; STRETCH materials fit each face exactly once (a rim, a headlight, the dish).
 */
public enum Mat {
    PAINT(256, 192, true, false),
    ARMOR(256, 192, true, false),
    ARMOR_DENTED(256, 192, true, false),
    STEEL(128, 128, true, false),
    PLASTIC(128, 128, false, false),
    GLASS(128, 128, false, false),
    TIRE(128, 128, false, false),
    RIM(128, 128, false, true),
    HEADLIGHT(64, 64, false, true),
    TAILLIGHT(64, 64, false, true),
    LIGHTBAR(128, 16, false, false),
    SOLAR(128, 128, false, false),
    WOOD(128, 128, false, false),
    FABRIC(64, 64, true, false),
    VINYL(128, 128, false, false),
    GRILLE(64, 64, false, true),
    DISH(256, 256, false, true),
    SCREEN(64, 64, false, true),
    /** Worn variants swapped in as the vehicle takes damage. */
    GLASS_CRACKED(128, 128, false, false),
    PAINT_DENTED(256, 192, true, false),
    /** What's left of a blown-out window: jagged teeth of glass around the frame. */
    SHARDS(128, 128, false, true),
    /** See-through mud caked over the body as grime builds up (and blown inside once the glass is gone). */
    MUD_LIGHT(256, 192, false, false),
    MUD_HEAVY(256, 192, false, false),
    /** Hail chips stuck to the outside of the glass. */
    ICE(128, 128, false, true),
    /** Plain white, for glowing strips and small coloured bits. */
    PLAIN(16, 16, true, true);

    public static final int PIXELS_PER_BLOCK = 64;

    public final ResourceLocation texture;
    public final int width, height;
    /** Tinted by the box's colour; otherwise the texture's own colours are used as painted. */
    public final boolean tinted;
    public final boolean stretch;

    Mat(int width, int height, boolean tinted, boolean stretch) {
        String name = name().toLowerCase(java.util.Locale.ROOT);
        this.texture = name.equals("plain") ? Interceptors.id("textures/entity/white.png")
                : Interceptors.id("textures/entity/material/" + name + ".png");
        this.width = width;
        this.height = height;
        this.tinted = tinted;
        this.stretch = stretch;
    }

    /** Texture height in blocks: where the road sits for WORLD materials. */
    public float heightBlocks() {
        return height / (float) PIXELS_PER_BLOCK;
    }
}
