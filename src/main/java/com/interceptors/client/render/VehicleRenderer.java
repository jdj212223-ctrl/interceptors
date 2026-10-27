package com.interceptors.client.render;

import com.interceptors.compat.Storms;
import com.interceptors.vehicle.AnchorStatus;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.Part;
import com.interceptors.vehicle.VehicleSpec;
import com.interceptors.vehicle.WindPhysics;
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
import net.minecraft.world.phys.Vec3;

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

    private static Map<Mat, List<VehicleModels.Box>> grouped(VehicleSpec spec) {
        return BY_MATERIAL.computeIfAbsent(spec.id(), key -> {
            Map<Mat, List<VehicleModels.Box>> map = new EnumMap<>(Mat.class);
            for (VehicleModels.Box b : VehicleModels.furnished(spec)) map.computeIfAbsent(b.mat(), m -> new ArrayList<>()).add(b);
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
        Map<Mat, List<VehicleModels.Box>> groups = grouped(vehicle.spec());
        float health = vehicle.healthFraction();

        // Solid boxes first, then the see-through glass on top of them.
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<Mat, List<VehicleModels.Box>> group : groups.entrySet()) {
                Mat mat = worn(group.getKey(), vehicle, health);
                if (mat == null) continue;
                VertexConsumer vc = null;
                for (VehicleModels.Box b : group.getValue()) {
                    int f = b.flags();
                    if ((f & VehicleModels.CLEAR) != 0 != (pass == 1)) continue;
                    if ((f & VehicleModels.ANCHOR) != 0 && extension <= 0) continue;
                    if ((f & VehicleModels.RGB) != 0 && !lights) continue;
                    if (health < TORN_BELOW && tornOff(b)) continue;
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
        if (health >= TORN_BELOW) decals(id, pose, buffers, light);
        if (vehicle.hasInstalledDish()) dish(vehicle.spec(), pose, buffers, light);
        dashboard(vehicle, pose, buffers);
        pose.popPose();
        super.render(vehicle, yaw, partialTick, pose, buffers, light);
    }

    /** Below this much armor left, trim and outer panels have been ripped off. Below DENTED_BELOW the body is dented. */
    private static final float TORN_BELOW = 0.3f, DENTED_BELOW = 0.6f;

    /**
     * What a material looks like after the storm: cracked glass when the windows are worn, none at all once
     * they've shattered, and dented paint and armor as the body takes damage.
     */
    private static Mat worn(Mat mat, InterceptorVehicle v, float health) {
        if (mat == Mat.GLASS) {
            int windows = v.condition(Part.WINDOWS);
            if (Part.failed(windows)) return null;
            if (Part.warning(windows)) return Mat.GLASS_CRACKED;
        }
        if (health < DENTED_BELOW && mat == Mat.PAINT) return Mat.PAINT_DENTED;
        if (health < DENTED_BELOW && mat == Mat.ARMOR) return Mat.ARMOR_DENTED;
        return mat;
    }

    /** Thin outer trim (stripes, cage bars, chevrons): the first things to be torn away. */
    private static boolean tornOff(VehicleModels.Box b) {
        if ((b.flags() & (VehicleModels.SKIRT | VehicleModels.ANCHOR | VehicleModels.GLOW)) != 0) return false;
        boolean outerTrim = b.x1() - b.x0() < 0.07f && Math.abs(b.x1()) >= 0.9f;
        return outerTrim && (b.mat() == Mat.STEEL || b.mat() == Mat.PAINT);
    }

    private static final Map<String, float[]> ROOF = new ConcurrentHashMap<>();

    /**
     * A Starlink-style flat white dish on a short mast, on the roof toward the back: highest point of the body
     * along the middle of the vehicle.
     */
    private static void dish(VehicleSpec spec, PoseStack pose, MultiBufferSource buffers, int light) {
        float[] at = ROOF.computeIfAbsent(spec.id(), id -> {
            List<VehicleModels.Box> boxes = VehicleModels.get(id);
            float minZ = 0;
            for (VehicleModels.Box b : boxes) minZ = Math.min(minZ, b.z0());
            float z = minZ * 0.45f, top = 0;
            for (VehicleModels.Box b : boxes) {
                if ((b.flags() & (VehicleModels.SPIN | VehicleModels.ANCHOR | VehicleModels.GLOW)) != 0) continue;
                if (b.x0() <= 0 && b.x1() >= 0 && b.z0() <= z && b.z1() >= z) top = Math.max(top, b.y1());
            }
            return new float[]{z, top};
        });
        float z = at[0], y = at[1];
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(Mat.STEEL.texture));
        box(pose.last(), steel, new VehicleModels.Box(-0.05f, y, z - 0.05f, 0.05f, y + 0.22f, z + 0.05f, 0xD0D4D8, 0, Mat.STEEL, 0, 0, 0, 0),
                Mat.STEEL, 0xD0D4D8, light);
        VertexConsumer paint = buffers.getBuffer(RenderType.entityCutoutNoCull(Mat.PAINT.texture));
        box(pose.last(), paint, new VehicleModels.Box(-0.3f, y + 0.22f, z - 0.24f, 0.3f, y + 0.3f, z + 0.24f, 0xF4F6F7, 0, Mat.PAINT, 0, 0, 0.02f, 0.06f),
                Mat.PAINT, 0xF4F6F7, light);
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

    /**
     * The live dash screen in front of the driver: speed, wind, the nearest tornado, anchor status and the
     * hydraulics. Goes dark with the computer, like the rest of the dash.
     */
    private void dashboard(InterceptorVehicle v, PoseStack pose, MultiBufferSource buffers) {
        Vec3 seat = v.spec().seats().get(0);
        float z = (float) (seat.z + VehicleModels.DASH_AHEAD) - 0.034f, top = (float) (seat.y + VehicleModels.DASH_TOP);
        float halfWidth = (float) VehicleModels.DASH_HALF_WIDTH, height = (float) (VehicleModels.DASH_TOP - VehicleModels.DASH_BOTTOM);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(Mat.PLAIN.texture));
        Face face = new Face(pose.last(), vc, 8, 14, 22, FULL_BRIGHT);
        face.quad(0, 0, -1, new float[]{halfWidth, top, z}, new float[]{-halfWidth, top, z}, new float[]{-halfWidth, top - height, z},
                new float[]{halfWidth, top - height, z}, new float[]{0, 0, 1, 0, 1, 1, 0, 1});

        Font font = getFont();
        float s = 0.0034f;
        pose.pushPose();
        pose.translate(halfWidth - 0.02f, top - 0.02f, z - 0.002f);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(s, -s, s);
        List<Component> lines = new ArrayList<>();
        boolean blink = v.tickCount / 6 % 2 == 0;
        if (!v.screensWork()) {
            if (blink) lines.add(Component.translatable(v.getReboot() > 0 ? "dash.interceptors.rebooting" : "dash.interceptors.not_responding").withStyle(ChatFormatting.AQUA));
        } else {
            double mph = WindPhysics.toMph(Math.abs(v.getSpeed()));
            double wind = v.lastWind().horizontalDistance();
            lines.add(Component.literal(String.format("%d MPH   WIND %d", Math.round(mph), Math.round(wind))).withStyle(ChatFormatting.WHITE));
            Storms.StormInfo t = Storms.nearestTornado(v.level(), v.position());
            lines.add(t == null ? Component.translatable("dash.interceptors.no_tornado").withStyle(ChatFormatting.GRAY)
                    : Component.literal(String.format("%s %dM %s", t.efLabel(), Math.round(t.distance()), Storms.compassName(t.bearing()))).withStyle(ChatFormatting.RED));
            AnchorStatus status = v.anchorStatus();
            if (status != AnchorStatus.NONE) {
                lines.add(Component.translatable("dash.interceptors.anchor", Component.translatable(status.key()))
                        .withStyle(st -> st.withColor(status.color())));
                lines.add(Component.literal(String.format("HYD %d%%  MUD %d%%", v.condition(Part.HYDRAULICS), Math.round(v.getGrime())))
                        .withStyle(v.getGrime() >= InterceptorVehicle.GRIME_WARN ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            }
            lines.add(Component.literal(String.format("ARMOR %d%%", Math.round(v.healthFraction() * 100))).withStyle(ChatFormatting.GREEN));
        }
        int y = 0;
        for (Component line : lines) {
            font.drawInBatch(line, 0, y, 0xFFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
            y += 10;
        }
        pose.popPose();
    }

    /** Gaming-rig colour cycle, offset per strip by its base colour so they don't all match. */
    private static int rainbow(float time, int seed) {
        float hue = (time / 120f + (seed & 0xFF) / 255f) % 1f;
        return Mth.hsvToRgb(hue, 0.8f, 1f);
    }

    /** Faces of a box as corner indices (see {@link VehicleModels.Box#corners()}), each listed counter-clockwise from outside. */
    private static final int[][] FACES = {{0, 1, 2, 3}, {7, 6, 5, 4}, {4, 5, 1, 0}, {3, 2, 6, 7}, {0, 3, 7, 4}, {5, 6, 2, 1}};
    /** Stretch UVs for those corners: each face shows the whole texture once, upright. */
    private static final float[][] STRETCH_UV = {{0, 0, 1, 0, 1, 1, 0, 1}, {0, 0, 1, 0, 1, 1, 0, 1}, {0, 0, 1, 0, 1, 1, 0, 1},
            {0, 1, 1, 1, 1, 0, 0, 0}, {0, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 0, 1, 1, 0, 1}};

    /**
     * Six faces with texture coordinates: STRETCH materials fit each face; WORLD materials are laid on at true
     * scale (64 pixels per block) so they tile seamlessly, with the texture's bottom edge at the road. Sloped
     * faces are mapped by whichever axis they mostly face.
     */
    private static void box(PoseStack.Pose p, VertexConsumer vc, VehicleModels.Box b, Mat mat, int rgb, int light) {
        float[][] c = b.corners();
        float su = Mat.PIXELS_PER_BLOCK / (float) mat.width, sv = Mat.PIXELS_PER_BLOCK / (float) mat.height, top = mat.heightBlocks();
        Face face = new Face(p, vc, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, light);
        float cx = (b.x0() + b.x1()) / 2, cy = (b.y0() + b.y1()) / 2, cz = (b.z0() + b.z1()) / 2;
        for (int f = 0; f < 6; f++) {
            int[] idx = FACES[f];
            float[] a = c[idx[0]], q1 = c[idx[1]], q3 = c[idx[3]];
            // Normal from the face's two edges, flipped to point away from the box's middle.
            float ux = q1[0] - a[0], uy = q1[1] - a[1], uz = q1[2] - a[2], vx = q3[0] - a[0], vy = q3[1] - a[1], vz = q3[2] - a[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-6f) continue;
            nx /= len;
            ny /= len;
            nz /= len;
            float mx = 0, my = 0, mz = 0;
            for (int i : idx) {
                mx += c[i][0] / 4;
                my += c[i][1] / 4;
                mz += c[i][2] / 4;
            }
            if (nx * (mx - cx) + ny * (my - cy) + nz * (mz - cz) < 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float[] uv = new float[8];
            for (int k = 0; k < 4; k++) {
                float[] v = c[idx[k]];
                if (mat.stretch) {
                    uv[k * 2] = STRETCH_UV[f][k * 2];
                    uv[k * 2 + 1] = STRETCH_UV[f][k * 2 + 1];
                } else if (Math.abs(ny) > 0.7f) {
                    // Roofs and hoods: only the clean upper part of the texture (the mud lives at its bottom).
                    uv[k * 2] = v[0] * su;
                    uv[k * 2 + 1] = Math.min(0.55f, (v[2] - b.z0()) * sv);
                } else {
                    uv[k * 2] = (Math.abs(nx) > Math.abs(nz) ? v[2] : v[0]) * su;
                    uv[k * 2 + 1] = (top - v[1]) * sv;
                }
            }
            face.quad(nx, ny, nz, c[idx[0]], c[idx[1]], c[idx[2]], c[idx[3]], uv);
        }
    }

    private record Face(PoseStack.Pose p, VertexConsumer vc, int r, int g, int b, int light) {
        void quad(float nx, float ny, float nz, float[] a, float[] b, float[] c, float[] d, float[] uv) {
            vertex(a, uv[0], uv[1], nx, ny, nz);
            vertex(b, uv[2], uv[3], nx, ny, nz);
            vertex(c, uv[4], uv[5], nx, ny, nz);
            vertex(d, uv[6], uv[7], nx, ny, nz);
        }

        private void vertex(float[] pos, float u, float v, float nx, float ny, float nz) {
            vc.addVertex(p, pos[0], pos[1], pos[2]).setColor(r, g, this.b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(InterceptorVehicle vehicle) {
        return Mat.PAINT.texture;
    }
}
