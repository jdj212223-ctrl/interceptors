package com.interceptors.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Plain key mappings, so Vivecraft can bind them to controller buttons like any other key. */
public final class Keys {
    private static final String CATEGORY = "key.categories.interceptors";
    public static final KeyMapping ANCHORS = new KeyMapping("key.interceptors.anchors", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping HORN = new KeyMapping("key.interceptors.horn", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private Keys() {}
}
