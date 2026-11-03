package com.interceptors.client.render;

import com.interceptors.Interceptors;
import com.interceptors.client.InjuryState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Hail injuries on a player: a lump of ice stuck in a body part, and after it's pulled out, a blocky pixel
 * wound on that part. Everyone who can see the player sees them.
 */
public class InjuryLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Interceptors.id("wound"), "main");
    private static final ResourceLocation WOUND = Interceptors.id("textures/entity/wound.png");
    private static final ResourceLocation WHITE = Interceptors.id("textures/entity/white.png");
    /** Where the stone sits on each part's front, in model pixels (x, y, z), matching the wound in wound.png. */
    private static final float[][] SPOTS = {{1.5f, -4.5f, -4f}, {-1.5f, 4.5f, -2f}, {-1f, 4.5f, -2f}, {1f, 4.5f, -2f}, {0, 4.5f, -2f}, {0, 4.5f, -2f}};

    private final HumanoidModel<AbstractClientPlayer> model;

    public InjuryLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new HumanoidModel<>(models.bakeLayer(LAYER));
    }

    public static LayerDefinition layer() {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.05f), 0f), 64, 64);
    }

    private static ModelPart part(HumanoidModel<?> m, int part) {
        return switch (part) {
            case 0 -> m.head;
            case 1 -> m.body;
            case 2 -> m.rightArm;
            case 3 -> m.leftArm;
            case 4 -> m.rightLeg;
            default -> m.leftLeg;
        };
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        InjuryState.Injury injury = InjuryState.of(player);
        if (injury == null || player.isInvisible()) return;
        long now = player.level().getGameTime();
        if (InjuryState.lodged(player, now)) {
            pose.pushPose();
            part(getParentModel(), injury.lodgedPart()).translateAndRotate(pose);
            float[] s = SPOTS[injury.lodgedPart()];
            // The stone shrinks as it melts.
            float melt = Math.min(1, (injury.lodgedUntil() - now) / 400f), size = (1.2f + 0.6f * melt) / 16f;
            pose.translate(s[0] / 16f, s[1] / 16f, s[2] / 16f);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
            ice(pose.last().pose(), vc, size, light);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(35));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(25));
            ice(pose.last().pose(), vc, size * 0.8f, light);
            pose.popPose();
        }
        long scabLeft = injury.bleedUntil() + InjuryState.SCAB_TICKS - now;
        if (scabLeft > 0 && InjuryState.blood()) {
            int alpha = (int) (255 * Math.min(1, scabLeft / 200f));
            getParentModel().copyPropertiesTo(model);
            ModelPart wounded = part(model, injury.woundPart());
            wounded.render(pose, buffers.getBuffer(RenderType.entityTranslucent(WOUND)), light, OverlayTexture.NO_OVERLAY, alpha << 24 | 0xFFFFFF);
        }
    }

    /** A cube of cloudy ice, centred on the spot, lighter on top like real lumps of hail. */
    private static void ice(Matrix4f m, VertexConsumer vc, float r, int light) {
        float[][] faces = {
                {0, -1, 0, 0xC8D4DE}, {0, 1, 0, 0xF4F8FC}, {0, 0, -1, 0xE4ECF4}, {0, 0, 1, 0xD8E2EA}, {-1, 0, 0, 0xDCE6EE}, {1, 0, 0, 0xDCE6EE}};
        for (float[] f : faces) {
            int c = (int) f[3];
            float nx = f[0], ny = f[1], nz = f[2];
            // Two axes across the face.
            float ux = ny != 0 ? 1 : nz != 0 ? 1 : 0, uy = 0, uz = nx != 0 ? 1 : 0;
            float vx = 0, vy = ny != 0 ? 0 : 1, vz = ny != 0 ? 1 : 0;
            float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            if (nx + ny + nz < 0) corners = new float[][]{{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            for (float[] k : corners) {
                float x = (nx + ux * k[0] + vx * k[1]) * r, y = (ny + uy * k[0] + vy * k[1]) * r, z = (nz + uz * k[0] + vz * k[1]) * r;
                vc.addVertex(m, x, y, z).setColor(c >> 16 & 255, c >> 8 & 255, c & 255, 225).setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
            }
        }
    }
}
