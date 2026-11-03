package com.interceptors.client.render;

import com.interceptors.Interceptors;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Cuts from flying glass, drawn as a blocky blood layer just over a player's head and face (so it shows in third
 * person and to other players). It lasts a couple of minutes, fades out at the end, and clears once they're healed.
 */
public class CutLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Interceptors.id("cut"), "main");
    private static final ResourceLocation TEXTURE = Interceptors.id("textures/entity/cut.png");
    /** Entity id to the game time the cut is gone, on this computer. */
    private static final Map<Integer, Long> CUTS = new HashMap<>();
    private static final int FADE_TICKS = 400;

    private final HumanoidModel<AbstractClientPlayer> model;

    public CutLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new HumanoidModel<>(models.bakeLayer(LAYER));
    }

    public static LayerDefinition layer() {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.04f), 0f), 64, 64);
    }

    public static void cut(int entityId, int ticks, boolean splatter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        CUTS.put(entityId, mc.level.getGameTime() + ticks);
        net.minecraft.world.entity.Entity e = mc.level.getEntity(entityId);
        if (splatter && e != null) com.interceptors.client.InjuryState.splatter(e);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Long until = CUTS.get(player.getId());
        if (until == null || !com.interceptors.client.InjuryState.blood()) return;
        long left = until - player.level().getGameTime();
        if (left <= 0 || player.getHealth() >= player.getMaxHealth() || !player.isAlive()) {
            CUTS.remove(player.getId());
            return;
        }
        int alpha = (int) (255 * Math.min(1, left / (float) FADE_TICKS));
        getParentModel().copyPropertiesTo(model);
        model.head.render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, OverlayTexture.NO_OVERLAY, alpha << 24 | 0xFFFFFF);
    }
}
