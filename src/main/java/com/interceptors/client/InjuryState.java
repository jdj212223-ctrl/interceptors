package com.interceptors.client;

import com.interceptors.config.ClientConfig;
import com.interceptors.network.InjuryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Hail injuries on this computer: who has a stone stuck in them, who is bleeding, and the blood drips. */
public final class InjuryState {
    /** Game times are when each thing ends. The wound scab stays a little after the bleeding stops. */
    public record Injury(int lodgedPart, long lodgedUntil, int woundPart, long bleedUntil) {}

    public static final int SCAB_TICKS = 600;
    private static final Map<Integer, Injury> INJURIES = new HashMap<>();
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.62f, 0.03f, 0.03f), 1.1f);

    private InjuryState() {}

    public static void update(InjuryPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        INJURIES.put(p.entityId(), new Injury(p.lodgedTicks() > 0 ? p.lodgedPart() : -1, now + p.lodgedTicks(), p.woundPart(), now + p.bleedTicks()));
    }

    public static Injury of(Entity e) {
        return INJURIES.get(e.getId());
    }

    public static boolean lodged(Entity e, long now) {
        Injury i = of(e);
        return i != null && i.lodgedPart >= 0 && now < i.lodgedUntil;
    }

    public static boolean bleeding(Entity e, long now) {
        Injury i = of(e);
        return i != null && now < i.bleedUntil;
    }

    public static boolean blood() {
        return ClientConfig.get(ClientConfig.BLOOD);
    }

    /** Where a body part roughly is in the world, for drips. */
    public static Vec3 partPosition(Player p, int part) {
        double yaw = Math.toRadians(p.yBodyRot);
        double side = part == 2 || part == 4 ? -0.32 : part == 3 || part == 5 ? 0.32 : 0;
        if (part >= 4) side *= 0.4;
        double y = switch (part) {
            case 0 -> p.getEyeY() - 0.1;
            case 1 -> p.getY() + 1.1;
            case 2, 3 -> p.getY() + 1.15;
            default -> p.getY() + 0.5;
        };
        // Sideways in the body's frame, a little in front.
        double x = -Math.cos(yaw) * side - Math.sin(yaw) * 0.15, z = -Math.sin(yaw) * side + Math.cos(yaw) * 0.15;
        return new Vec3(p.getX() + x, y, p.getZ() + z);
    }

    static void tick(Minecraft mc) {
        if (mc.level == null) {
            INJURIES.clear();
            return;
        }
        long now = mc.level.getGameTime();
        INJURIES.entrySet().removeIf(e -> now > e.getValue().bleedUntil + SCAB_TICKS && now > e.getValue().lodgedUntil);
        if (!blood() || now % 3 != 0) return;
        for (Map.Entry<Integer, Injury> e : INJURIES.entrySet()) {
            if (now >= e.getValue().bleedUntil || !(mc.level.getEntity(e.getKey()) instanceof Player p)) continue;
            Vec3 at = partPosition(p, e.getValue().woundPart);
            mc.level.addParticle(BLOOD, at.x, at.y, at.z, 0, -0.05, 0);
        }
    }

    /** A spray of blocky blood around someone's face, when the glass cuts them. */
    public static void splatter(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !blood()) return;
        Vec3 face = e.getEyePosition().add(e.getLookAngle().scale(0.4));
        DustParticleOptions big = new DustParticleOptions(new Vector3f(0.8f, 0.04f, 0.04f), 1.6f);
        for (int i = 0; i < 24; i++) {
            mc.level.addParticle(big, face.x + (Math.random() - 0.5) * 0.5, face.y - 0.15 + (Math.random() - 0.5) * 0.4,
                    face.z + (Math.random() - 0.5) * 0.5, 0, 0, 0);
        }
    }
}
