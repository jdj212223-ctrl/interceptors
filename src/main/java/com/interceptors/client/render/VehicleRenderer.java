package com.interceptors.client.render;

import com.interceptors.Interceptors;
import com.interceptors.vehicle.InterceptorVehicle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

public class VehicleRenderer extends EntityRenderer<InterceptorVehicle> {
    private static final ResourceLocation WHITE = Interceptors.id("textures/entity/white.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.4f;
    }

    @Override
    public void render(InterceptorVehicle vehicle, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        float hurt = vehicle.getHurtTime() - partialTick;
        if (hurt > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(hurt) * hurt * 0.25f));

        boolean anchored = vehicle.isAnchored();
        float extension = vehicle.anchorExtension();
        boolean lights = vehicle.lightsOn();
        float time = vehicle.tickCount + partialTick;
        List<VehicleModels.Box> boxes = VehicleModels.get(vehicle.spec().id());
        VehicleModels.Box pivot = boxes.stream().filter(b -> (b.flags() & VehicleModels.SPIN) != 0).findFirst().orElse(null);
        // Solid boxes first, then the see-through glass on top of them.
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buffers.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(WHITE) : RenderType.entityTranslucent(WHITE));
            for (VehicleModels.Box b : boxes) {
                int f = b.flags();
                if ((f & VehicleModels.CLEAR) != 0 != (pass == 1)) continue;
                if ((f & VehicleModels.ANCHOR) != 0 && extension <= 0) continue;
                if ((f & VehicleModels.RGB) != 0 && !lights) continue;
                pose.pushPose();
                // Spikes and jacks start tucked up in the body and slide down as they deploy.
                if ((f & VehicleModels.ANCHOR) != 0) pose.translate(0, (1 - extension) * 0.55f, 0);
                if ((f & VehicleModels.SKIRT) != 0) pose.translate(0, 0.15f - 0.27f * extension, 0);
                if ((f & VehicleModels.SPIN) != 0 && pivot != null) {
                    float px = (pivot.x0() + pivot.x1()) / 2, pz = (pivot.z0() + pivot.z1()) / 2;
                    pose.translate(px, 0, pz);
                    pose.mulPose(Axis.YP.rotationDegrees(time * 3f));
                    pose.translate(-px, 0, -pz);
                }
                int rgb = (f & VehicleModels.RGB) != 0 ? rainbow(time, b.rgb()) : b.rgb();
                box(pose.last(), vc, b, rgb, pass == 1 ? 110 : 255, (f & VehicleModels.GLOW) != 0 ? FULL_BRIGHT : light);
                pose.popPose();
            }
        }
        if (vehicle.spec().id().equals("gaming_van")) subscribe(pose, buffers, light);
        pose.popPose();
        super.render(vehicle, yaw, partialTick, pose, buffers, light);
    }

    private static final Component SUBSCRIBE = Component.literal("SUBSCRIBE").withStyle(ChatFormatting.BOLD);

    /** Big red graffiti-style lettering down both sides of the camper, like on Frantic Dan's channel banner. */
    private void subscribe(PoseStack pose, MultiBufferSource buffers, int light) {
        Font font = getFont();
        float scale = 0.032f;
        for (int side : new int[]{1, -1}) {
            pose.pushPose();
            pose.translate(side * 1.081, 1.58, -1.25);
            pose.mulPose(Axis.YP.rotationDegrees(side * 90));
            pose.scale(scale, -scale, scale);
            font.drawInBatch(SUBSCRIBE, -font.width(SUBSCRIBE) / 2f, 0, 0xE01818, false, pose.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, light);
            pose.popPose();
        }
    }

    /** Gaming-rig colour cycle, offset per strip by its base colour so they don't all match. */
    private static int rainbow(float time, int seed) {
        float hue = (time / 120f + (seed & 0xFF) / 255f) % 1f;
        return Mth.hsvToRgb(hue, 0.8f, 1f);
    }

    private static void box(PoseStack.Pose p, VertexConsumer vc, VehicleModels.Box b, int rgb, int alpha, int light) {
        float x0 = b.x0(), y0 = b.y0(), z0 = b.z0(), x1 = b.x1(), y1 = b.y1(), z1 = b.z1();
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, bl = rgb & 0xFF, a = alpha;
        quad(p, vc, r, g, bl, a, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(p, vc, r, g, bl, a, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(p, vc, r, g, bl, a, light, 0, 0, -1, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
        quad(p, vc, r, g, bl, a, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(p, vc, r, g, bl, a, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(p, vc, r, g, bl, a, light, 1, 0, 0, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, int r, int g, int b, int a, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(p, vc, r, g, b, a, light, nx, ny, nz, ax, ay, az, 0, 0);
        vertex(p, vc, r, g, b, a, light, nx, ny, nz, bx, by, bz, 1, 0);
        vertex(p, vc, r, g, b, a, light, nx, ny, nz, cx, cy, cz, 1, 1);
        vertex(p, vc, r, g, b, a, light, nx, ny, nz, dx, dy, dz, 0, 1);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, int r, int g, int b, int a, int light, float nx, float ny, float nz,
                               float x, float y, float z, float u, float v) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(InterceptorVehicle vehicle) {
        return WHITE;
    }
}
