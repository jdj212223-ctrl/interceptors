package com.interceptors.client.render;

import com.interceptors.vehicle.InterceptorVehicle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleRenderer extends EntityRenderer<InterceptorVehicle> {
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Each model's boxes sorted by material, so every texture is bound once per vehicle. */
    private static final Map<String, Map<Mat, List<VehicleModels.Box>>> BY_MATERIAL = new ConcurrentHashMap<>();

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.4f;
    }

    private static Map<Mat, List<VehicleModels.Box>> grouped(String id) {
        return BY_MATERIAL.computeIfAbsent(id, key -> {
            Map<Mat, List<VehicleModels.Box>> map = new EnumMap<>(Mat.class);
            for (VehicleModels.Box b : VehicleModels.get(key)) map.computeIfAbsent(b.mat(), m -> new ArrayList<>()).add(b);
            return map;
        });
    }

    @Override
    public void render(InterceptorVehicle vehicle, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        float hurt = vehicle.getHurtTime() - partialTick;
        if (hurt > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(hurt) * hurt * 0.25f));

        float extension = vehicle.anchorExtension();
        boolean lights = vehicle.lightsOn();
        float time = vehicle.tickCount + partialTick;
        String id = vehicle.spec().id();
        VehicleModels.Box pivot = VehicleModels.get(id).stream().filter(b -> (b.flags() & VehicleModels.SPIN) != 0).findFirst().orElse(null);
        Map<Mat, List<VehicleModels.Box>> groups = grouped(id);

        // Solid boxes first, then the see-through glass on top of them.
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<Mat, List<VehicleModels.Box>> group : groups.entrySet()) {
                Mat mat = group.getKey();
                VertexConsumer vc = null;
                for (VehicleModels.Box b : group.getValue()) {
                    int f = b.flags();
                    if ((f & VehicleModels.CLEAR) != 0 != (pass == 1)) continue;
                    if ((f & VehicleModels.ANCHOR) != 0 && extension <= 0) continue;
                    if ((f & VehicleModels.RGB) != 0 && !lights) continue;
                    if (vc == null) vc = buffers.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(mat.texture) : RenderType.entityTranslucent(mat.texture));
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
                    int rgb = (f & VehicleModels.RGB) != 0 ? rainbow(time, b.rgb()) : mat.tinted ? b.rgb() : 0xFFFFFF;
                    box(pose.last(), vc, b, mat, rgb, (f & VehicleModels.GLOW) != 0 ? FULL_BRIGHT : light);
                    pose.popPose();
                }
            }
        }
        decals(id, pose, buffers, light);
        pose.popPose();
        super.render(vehicle, yaw, partialTick, pose, buffers, light);
    }

    /** Painted lettering on both sides of the body. */
    private void decals(String id, PoseStack pose, MultiBufferSource buffers, int light) {
        Font font = getFont();
        for (VehicleModels.Decal d : VehicleModels.decals(id)) {
            Component text = Component.literal(d.text()).withStyle(ChatFormatting.BOLD);
            for (int side : new int[]{1, -1}) {
                pose.pushPose();
                pose.translate(side * (d.halfWidth() + 0.004), d.y(), d.z());
                pose.mulPose(Axis.YP.rotationDegrees(side * 90));
                pose.scale(d.scale(), -d.scale(), d.scale());
                font.drawInBatch(text, -font.width(text) / 2f, 0, d.rgb(), false, pose.last().pose(), buffers,
                        Font.DisplayMode.POLYGON_OFFSET, 0, light);
                pose.popPose();
            }
        }
    }

    /** Gaming-rig colour cycle, offset per strip by its base colour so they don't all match. */
    private static int rainbow(float time, int seed) {
        float hue = (time / 120f + (seed & 0xFF) / 255f) % 1f;
        return Mth.hsvToRgb(hue, 0.8f, 1f);
    }

    /**
     * Six faces with texture coordinates: STRETCH materials fit each face; WORLD materials are laid on at true
     * scale (64 pixels per block) so they tile seamlessly, with the texture's bottom edge at the road.
     */
    private static void box(PoseStack.Pose p, VertexConsumer vc, VehicleModels.Box b, Mat mat, int rgb, int light) {
        float x0 = b.x0(), y0 = b.y0(), z0 = b.z0(), x1 = b.x1(), y1 = b.y1(), z1 = b.z1();
        float su = Mat.PIXELS_PER_BLOCK / (float) mat.width, sv = Mat.PIXELS_PER_BLOCK / (float) mat.height, top = mat.heightBlocks();
        Face face = new Face(p, vc, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, light);
        if (mat.stretch) {
            face.quad(0, -1, 0, x0, y0, z0, 0, 0, x1, y0, z0, 1, 0, x1, y0, z1, 1, 1, x0, y0, z1, 0, 1);
            face.quad(0, 1, 0, x0, y1, z1, 0, 0, x1, y1, z1, 1, 0, x1, y1, z0, 1, 1, x0, y1, z0, 0, 1);
            face.quad(0, 0, -1, x0, y1, z0, 0, 0, x1, y1, z0, 1, 0, x1, y0, z0, 1, 1, x0, y0, z0, 0, 1);
            face.quad(0, 0, 1, x0, y0, z1, 0, 1, x1, y0, z1, 1, 1, x1, y1, z1, 1, 0, x0, y1, z1, 0, 0);
            face.quad(-1, 0, 0, x0, y0, z0, 0, 1, x0, y0, z1, 1, 1, x0, y1, z1, 1, 0, x0, y1, z0, 0, 0);
            face.quad(1, 0, 0, x1, y1, z0, 0, 0, x1, y1, z1, 1, 0, x1, y0, z1, 1, 1, x1, y0, z0, 0, 1);
            return;
        }
        // Side faces: u runs along the face, v = (top - y) so y=0 is the texture's bottom row.
        float vy0 = (top - y0) * sv, vy1 = (top - y1) * sv;
        face.quad(0, 0, -1, x0, y1, z0, x0 * su, vy1, x1, y1, z0, x1 * su, vy1, x1, y0, z0, x1 * su, vy0, x0, y0, z0, x0 * su, vy0);
        face.quad(0, 0, 1, x0, y0, z1, x0 * su, vy0, x1, y0, z1, x1 * su, vy0, x1, y1, z1, x1 * su, vy1, x0, y1, z1, x0 * su, vy1);
        face.quad(-1, 0, 0, x0, y0, z0, z0 * su, vy0, x0, y0, z1, z1 * su, vy0, x0, y1, z1, z1 * su, vy1, x0, y1, z0, z0 * su, vy1);
        face.quad(1, 0, 0, x1, y1, z0, z0 * su, vy1, x1, y1, z1, z1 * su, vy1, x1, y0, z1, z1 * su, vy0, x1, y0, z0, z0 * su, vy0);
        // Top and bottom: only the clean upper part of the texture (the mud lives at its bottom), laid flat.
        float tv = Math.min(0.55f, (z1 - z0) * sv);
        face.quad(0, -1, 0, x0, y0, z0, x0 * su, 0, x1, y0, z0, x1 * su, 0, x1, y0, z1, x1 * su, tv, x0, y0, z1, x0 * su, tv);
        face.quad(0, 1, 0, x0, y1, z1, x0 * su, tv, x1, y1, z1, x1 * su, tv, x1, y1, z0, x1 * su, 0, x0, y1, z0, x0 * su, 0);
    }

    private record Face(PoseStack.Pose p, VertexConsumer vc, int r, int g, int b, int light) {
        void quad(float nx, float ny, float nz,
                  float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
                  float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
            vertex(ax, ay, az, au, av, nx, ny, nz);
            vertex(bx, by, bz, bu, bv, nx, ny, nz);
            vertex(cx, cy, cz, cu, cv, nx, ny, nz);
            vertex(dx, dy, dz, du, dv, nx, ny, nz);
        }

        private void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            vc.addVertex(p, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(InterceptorVehicle vehicle) {
        return Mat.PAINT.texture;
    }
}
