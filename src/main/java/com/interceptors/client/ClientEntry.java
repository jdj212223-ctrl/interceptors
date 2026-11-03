package com.interceptors.client;

import com.interceptors.Interceptors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only start-up: the Config button on the Mods screen, with every setting named through the lang files. */
@Mod(value = Interceptors.MODID, dist = Dist.CLIENT)
public final class ClientEntry {
    public ClientEntry(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
