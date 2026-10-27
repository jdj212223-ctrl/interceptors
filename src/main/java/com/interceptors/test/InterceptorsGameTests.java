package com.interceptors.test;

import com.interceptors.Interceptors;
import com.interceptors.client.render.VehicleModels;
import com.interceptors.compat.Storms;
import com.interceptors.registry.ModEntities;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.VehicleSpec;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Run with ./gradlew runGameTestServer (also part of ./gradlew build). */
@GameTestHolder(Interceptors.MODID)
@PrefixGameTestTemplate(false)
public class InterceptorsGameTests {

    /** Where vehicles park: on top of the pad's floor, which sits one block above the test origin. */
    private static Vec3 centre(GameTestHelper helper) {
        return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 2, 8)));
    }

    /** Parks a vehicle in the middle of the pad, facing +Z (south). */
    private static InterceptorVehicle park(GameTestHelper helper, VehicleSpec spec) {
        ServerLevel level = helper.getLevel();
        InterceptorVehicle v = ModEntities.vehicle(spec).create(level);
        Vec3 at = centre(helper);
        v.moveTo(at.x, at.y, at.z, 0, 0);
        level.addFreshEntity(v);
        return v;
    }

    @GameTest(template = "pad")
    public static void calmWindDoesNothing(GameTestHelper helper) {
        for (VehicleSpec spec : VehicleSpec.ALL) {
            helper.assertTrue(WindPhysics.push(spec, false, new Vec3(40, 0, 0)).equals(Vec3.ZERO), spec.id() + " slid in a 40 mph breeze");
            helper.assertTrue(WindPhysics.damagePerSecond(spec, 40) == 0, spec.id() + " was hurt by a 40 mph breeze");
        }
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void anchorsHoldWhatTiresCannot(GameTestHelper helper) {
        for (VehicleSpec spec : VehicleSpec.ALL) {
            helper.assertTrue(spec.anchorHold() > spec.grip(), spec.id() + " anchors are weaker than its tires");
            Vec3 wind = new Vec3(0, 0, (spec.grip() + spec.anchorHold()) / 2);
            helper.assertTrue(WindPhysics.push(spec, true, wind).equals(Vec3.ZERO), spec.id() + " slid with anchors down");
            helper.assertTrue(WindPhysics.push(spec, false, wind).z > 0, spec.id() + " didn't slide downwind with anchors up");
        }
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void violentWindLiftsVehicles(GameTestHelper helper) {
        for (VehicleSpec spec : VehicleSpec.ALL) {
            Vec3 push = WindPhysics.push(spec, false, new Vec3(spec.grip() * 3, 0, 0));
            helper.assertTrue(push.y > WindPhysics.GRAVITY, spec.id() + " stays on the ground at 3x its grip: " + push);
        }
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void efScaleAndCompass(GameTestHelper helper) {
        helper.assertTrue(WindPhysics.efRating(60) == -1 && WindPhysics.efRating(70) == 0 && WindPhysics.efRating(150) == 3
                && WindPhysics.efRating(210) == 5, "EF scale is off");
        helper.assertTrue(Math.abs(Storms.compass(0, -1)) < 1e-6, "north isn't 0");
        helper.assertTrue(Math.abs(Storms.compass(1, 0) - 90) < 1e-6, "east isn't 90");
        helper.assertTrue(Storms.compassName(225).equals("SW"), "225 isn't SW");
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void vanDrivesForwardAndBrakes(GameTestHelper helper) {
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        double startZ = van.getZ();
        for (int i = 0; i < 20; i++) van.simulate(1, 0);
        helper.assertTrue(van.getZ() - startZ > 2, "van only went " + (van.getZ() - startZ) + " blocks");
        helper.assertTrue(Math.abs(van.getX() - centre(helper).x) < 0.01, "van drifted sideways");
        float fast = van.getSpeed();
        for (int i = 0; i < 5; i++) van.simulate(-1, 0);
        helper.assertTrue(van.getSpeed() < fast, "brakes didn't slow the van");
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void steeringTurnsTheVan(GameTestHelper helper) {
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        for (int i = 0; i < 10; i++) van.simulate(1, 0);
        float yaw = van.getYRot();
        for (int i = 0; i < 10; i++) van.simulate(1, 1);
        helper.assertTrue(van.getYRot() < yaw - 5, "steering left didn't turn left: " + yaw + " -> " + van.getYRot());
        helper.succeed();
    }

    @GameTest(template = "pad", timeoutTicks = 60)
    public static void anchoredVanHoldsWhereLooseVanSlides(GameTestHelper helper) {
        VehicleSpec spec = VehicleSpec.GAMING_VAN;
        Vec3 wind = new Vec3((spec.grip() + spec.anchorHold()) / 2, 0, 0);
        InterceptorVehicle anchored = park(helper, spec);
        anchored.setAnchored(true);
        anchored.forcedWind = wind;
        Vec3 start = anchored.position();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(anchored.position().distanceTo(start) < 0.05, "anchored van moved " + anchored.position().distanceTo(start));
            anchored.setAnchored(false);
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(anchored.getX() - start.x > 1, "van with anchors up only slid " + (anchored.getX() - start.x));
                helper.succeed();
            });
        });
    }

    @GameTest(template = "pad", timeoutTicks = 200)
    public static void violentTornadoWrecksTheVan(GameTestHelper helper) {
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        van.setAnchored(true);
        van.forcedWind = new Vec3(0, 0, 260);
        helper.succeedWhen(() -> helper.assertTrue(van.isRemoved(), "van survived a 260 mph tornado, damage " + van.getDamage()));
    }

    @GameTest(template = "pad")
    public static void damageAndAnchorsSurviveSaving(GameTestHelper helper) {
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        van.setAnchored(true);
        van.setDamage(17);
        CompoundTag tag = van.saveWithoutId(new CompoundTag());
        InterceptorVehicle copy = ModEntities.vehicle(VehicleSpec.GAMING_VAN).create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.isAnchored() && copy.getDamage() == 17, "lost state: anchored " + copy.isAnchored() + " damage " + copy.getDamage());
        helper.succeed();
    }

    /** Every vehicle has a model, and nothing solid sits where any seated rider's eyes are. */
    @GameTest(template = "pad")
    public static void everyoneCanSeeOutOfTheCab(GameTestHelper helper) {
        for (VehicleSpec spec : VehicleSpec.ALL) {
            helper.assertTrue(VehicleModels.has(spec.id()), spec.id() + " has no model");
            // Only the front seats need a view; rear passengers ride in the back like cargo.
            for (Vec3 seat : spec.seats().subList(0, 2)) {
                Vec3 eye = seat.add(0, EYE_ABOVE_SEAT, 0);
                for (VehicleModels.Box b : VehicleModels.get(spec.id())) {
                    if ((b.flags() & (VehicleModels.CLEAR | VehicleModels.ANCHOR)) != 0) continue;
                    double r = 0.12;
                    boolean inside = eye.x > b.x0() - r && eye.x < b.x1() + r && eye.y > b.y0() - r && eye.y < b.y1() + r
                            && eye.z > b.z0() - r && eye.z < b.z1() + r;
                    helper.assertTrue(!inside, spec.id() + ": rider's eyes at " + eye + " are inside " + b);
                }
            }
        }
        helper.succeed();
    }

    /** Seat height to eye height: a player rides 0.6 below the seat point and their eyes are 1.62 above their feet. */
    private static final double EYE_ABOVE_SEAT = 1.62 - 0.6;

    @GameTest(template = "pad")
    public static void readsRealPMWeather(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(Storms.handler(level) != null, "no PMWeather weather handler for the test level");
        Vec3 wind = Storms.windAt(level, centre(helper));
        helper.assertTrue(Double.isFinite(wind.length()), "wind isn't a number");
        Storms.around(level, centre(helper));
        helper.succeed();
    }
}
