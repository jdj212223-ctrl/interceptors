package com.interceptors.test;

import com.interceptors.Interceptors;
import com.interceptors.client.render.VehicleModels;
import com.interceptors.compat.Storms;
import com.interceptors.registry.ModEntities;
import com.interceptors.item.DeviceItem;
import com.interceptors.registry.ModItems;
import com.interceptors.vehicle.AnchorStatus;
import com.interceptors.vehicle.InterceptorVehicle;
import com.interceptors.vehicle.Part;
import com.interceptors.vehicle.VehicleSpec;
import com.interceptors.vehicle.WindPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.ItemStack;
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
            if (!spec.hasAnchors()) continue;
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
        van.setCondition(Part.TIRES, 33);
        van.setBattery(42);
        van.freeze();
        van.toggleLights();
        CompoundTag tag = van.saveWithoutId(new CompoundTag());
        InterceptorVehicle copy = ModEntities.vehicle(VehicleSpec.GAMING_VAN).create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.isAnchored() && copy.getDamage() == 17, "lost state: anchored " + copy.isAnchored() + " damage " + copy.getDamage());
        helper.assertTrue(copy.condition(Part.TIRES) == 33 && copy.condition(Part.ENGINE) == 100, "lost part conditions");
        helper.assertTrue(copy.isFrozen() && copy.getBattery() == 42 && !copy.lightsOn(), "lost frozen/battery/lights");
        helper.succeed();
    }

    @GameTest(template = "pad", timeoutTicks = 100)
    public static void anchorsTakeTimeToDeploy(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        helper.assertTrue(dom.anchorStatus() == AnchorStatus.READY, "parked Dom 3 isn't READY: " + dom.anchorStatus());
        helper.assertTrue(dom.toggleAnchors() == null, "couldn't start dropping anchors");
        helper.assertTrue(dom.anchorStatus() == AnchorStatus.DEPLOYING && !dom.isAnchored(), "anchors snapped down instantly");
        helper.runAfterDelay(VehicleSpec.DOMINATOR_3.deployTicks() + 2, () -> {
            helper.assertTrue(dom.isAnchored() && dom.anchorStatus() == AnchorStatus.ANCHORED, "anchors never finished: " + dom.anchorStatus());
            helper.assertTrue(dom.toggleAnchors() == null && !dom.isAnchored(), "couldn't raise anchors");
            helper.assertTrue(dom.anchorStatus() == AnchorStatus.RETRACTING, "not retracting: " + dom.anchorStatus());
            helper.succeed();
        });
    }

    @GameTest(template = "pad", timeoutTicks = 160)
    public static void frozenComputerBlocksAnchorsUntilRebooted(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        dom.freeze();
        helper.assertTrue(dom.anchorStatus() == AnchorStatus.FROZEN && !dom.screensWork(), "freeze didn't show");
        helper.assertTrue("message.interceptors.frozen".equals(dom.toggleAnchors()), "anchors moved with a frozen computer");
        dom.reboot();
        helper.assertTrue(dom.anchorStatus() == AnchorStatus.REBOOTING, "not rebooting: " + dom.anchorStatus());
        helper.runAfterDelay(InterceptorVehicle.REBOOT_TICKS + 2, () -> {
            helper.assertTrue(!dom.isFrozen() && dom.screensWork(), "still frozen after the reboot");
            helper.assertTrue(dom.toggleAnchors() == null, "anchors still blocked after the reboot");
            helper.succeed();
        });
    }

    @GameTest(template = "pad")
    public static void brokenHydraulicsDropTheAnchors(GameTestHelper helper) {
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        van.setAnchored(true);
        van.damagePart(Part.HYDRAULICS, 100);
        helper.assertTrue(!van.isAnchored() && van.anchorExtension() > 0, "anchors didn't start popping up");
        helper.assertTrue(van.anchorStatus() == AnchorStatus.FAILED, "status isn't FAILED: " + van.anchorStatus());
        helper.runAfterDelay(InterceptorVehicle.RETRACT_TICKS + 1, () -> {
            helper.assertTrue("message.interceptors.hydraulics_failed".equals(van.toggleAnchors()), "anchors deployed on dead hydraulics");
            helper.assertTrue(van.repairWorstPart() == Part.HYDRAULICS && !van.failed(Part.HYDRAULICS), "repair kit didn't fix the hydraulics");
            helper.succeed();
        });
    }

    @GameTest(template = "pad", timeoutTicks = 120)
    public static void tornadoWearsPartsDown(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        dom.setAnchored(true);
        dom.forcedWind = new Vec3(0, 0, 200);
        helper.runAfterDelay(100, () -> {
            int worn = 0;
            for (Part p : Part.values()) if (dom.condition(p) < 100) worn++;
            helper.assertTrue(worn >= 3, "a 200 mph tornado barely touched the parts: " + worn + " worn");
            helper.succeed();
        });
    }

    @GameTest(template = "pad")
    public static void flatTireCrawls(GameTestHelper helper) {
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        van.setCondition(Part.TIRES, 0);
        for (int i = 0; i < 80; i++) van.simulate(1, 0);
        helper.assertTrue(van.getSpeed() <= VehicleSpec.GAMING_VAN.maxSpeed() * 0.4f + 1e-4, "flat tire still does " + van.getSpeed());
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void stalledEngineWontDrive(GameTestHelper helper) {
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        van.setCondition(Part.ENGINE, 0);
        for (int i = 0; i < 40; i++) van.simulate(1, 0);
        helper.assertTrue(van.getSpeed() <= 0, "stalled engine accelerated to " + van.getSpeed());
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void rentalCarHasNoAnchors(GameTestHelper helper) {
        InterceptorVehicle car = park(helper, VehicleSpec.RENTAL_CAR);
        helper.assertTrue("message.interceptors.no_anchors".equals(car.toggleAnchors()), "rental car dropped anchors");
        car.freeze();
        helper.assertTrue(!car.isFrozen() && car.anchorStatus() == AnchorStatus.NONE, "rental car has an anchor computer");
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void deadBatteryKillsTheVanScreens(GameTestHelper helper) {
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        helper.assertTrue(van.screensWork(), "fresh van's screens are off");
        van.setBattery(0);
        helper.assertTrue(!van.screensWork(), "screens run on an empty battery");
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_1);
        dom.setBattery(0);
        helper.assertTrue(dom.screensWork(), "Dom 1 has no solar battery, so it shouldn't care");
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
                for (VehicleModels.Box b : VehicleModels.furnished(spec)) {
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

    /**
     * Nothing solid cuts through a seated rider, in any seat of any vehicle: the space from their hips up past the
     * top of their head is clear of walls, roofs, floors and furniture (their own seat cushion aside).
     */
    @GameTest(template = "pad")
    public static void seatedRidersFitInsideEveryVehicle(GameTestHelper helper) {
        for (VehicleSpec spec : VehicleSpec.ALL) {
            for (int i = 0; i < spec.seats().size(); i++) {
                Vec3 seat = spec.seats().get(i);
                // A sitting player: hips about 0.15 above the seat point, head top 1.2 above it.
                double x0 = seat.x - 0.2, x1 = seat.x + 0.2, y0 = seat.y + 0.2, y1 = seat.y + 1.2, z0 = seat.z - 0.2, z1 = seat.z + 0.15;
                for (VehicleModels.Box b : VehicleModels.furnished(spec)) {
                    if ((b.flags() & (VehicleModels.CLEAR | VehicleModels.ANCHOR)) != 0 || b.mat() == com.interceptors.client.render.Mat.FABRIC) continue;
                    boolean hit = x0 < b.x1() && x1 > b.x0() && y0 < b.y1() && y1 > b.y0() && z0 < b.z1() && z1 > b.z0();
                    helper.assertTrue(!hit, spec.id() + " seat " + i + " " + seat + " cuts through " + b);
                }
            }
        }
        helper.succeed();
    }

    /** Seat height to eye height: a player rides 0.6 below the seat point and their eyes are 1.62 above their feet. */
    private static final double EYE_ABOVE_SEAT = 1.62 - 0.6;

    @GameTest(template = "pad")
    public static void mudJamsTheHydraulicsAndWaterWashesIt(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        dom.setGrime(InterceptorVehicle.GRIME_JAM);
        helper.assertTrue(dom.hydraulicsDown() && dom.anchorStatus() == AnchorStatus.FAILED, "mud didn't jam the hydraulics");
        helper.assertTrue("message.interceptors.grime_jammed".equals(dom.toggleAnchors()), "anchors moved through jammed hydraulics");
        dom.setGrime(0);
        helper.assertTrue(!dom.hydraulicsDown() && dom.toggleAnchors() == null, "clean hydraulics still jammed");
        helper.succeed();
    }

    @GameTest(template = "pad", timeoutTicks = 200)
    public static void drivingThroughDirtFillsTheHydraulics(GameTestHelper helper) {
        // A dirt track across the pad, and a van driving down it.
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.MUD);
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        van.moveTo(van.getX(), van.getY() - 0.125, helper.absoluteVec(new Vec3(0, 0, 1.5)).z, 0, 0);
        helper.onEachTick(() -> {
            if (van.getZ() < helper.absoluteVec(new Vec3(0, 0, 14)).z) van.simulate(1, 0);
        });
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(van.getGrime() > 0.5f, "driving through mud left the hydraulics clean: " + van.getGrime());
            helper.succeed();
        });
    }

    @GameTest(template = "pad")
    public static void ridersStayInDuringATornado(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        net.minecraft.world.entity.npc.Villager rider = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        rider.moveTo(dom.position());
        helper.getLevel().addFreshEntity(rider);
        helper.assertTrue(rider.startRiding(dom), "villager couldn't sit in the Dom 3");
        dom.forcedWind = new Vec3(260, 0, 0);
        rider.stopRiding();
        helper.assertTrue(rider.getVehicle() == dom, "an EF5 pulled the rider out of their seat");
        helper.assertTrue(dom.mayLeave(rider, true), "rider can't choose to get out");
        dom.forcedWind = new Vec3(5, 0, 0);
        rider.stopRiding();
        helper.assertTrue(rider.getVehicle() == null, "rider stuck in the seat on a calm day");
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void everyVehicleSeatsAFewPeople(GameTestHelper helper) {
        for (VehicleSpec spec : VehicleSpec.ALL) helper.assertTrue(spec.seats().size() >= 3, spec.id() + " only seats " + spec.seats().size());
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void satelliteDishInstallsAndSaves(GameTestHelper helper) {
        InterceptorVehicle van = park(helper, VehicleSpec.GAMING_VAN);
        helper.assertTrue(!van.hasDish(), "a new van already has a dish");
        van.setDish(true);
        CompoundTag tag = van.saveWithoutId(new CompoundTag());
        InterceptorVehicle copy = ModEntities.vehicle(VehicleSpec.GAMING_VAN).create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.hasDish(), "dish lost on save");
        helper.assertTrue(park(helper, VehicleSpec.DOPPLER_ON_WHEELS).hasDish(), "the DOW's radar should count as a dish");
        helper.succeed();
    }

    @GameTest(template = "pad", timeoutTicks = 100)
    public static void devicesChargeWhileRiding(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_1);
        net.minecraft.world.entity.npc.Villager rider = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        rider.moveTo(dom.position());
        helper.getLevel().addFreshEntity(rider);
        rider.startRiding(dom);
        ItemStack ipad = new ItemStack(ModItems.IPAD.get());
        DeviceItem.setBattery(ipad, 10);
        ItemStack loose = new ItemStack(ModItems.MACBOOK.get());
        DeviceItem.setBattery(loose, 10);
        helper.onEachTick(() -> {
            ipad.inventoryTick(helper.getLevel(), rider, 0, false);
            loose.inventoryTick(helper.getLevel(), dom, 0, false);
        });
        helper.runAfterDelay(81, () -> {
            helper.assertTrue(DeviceItem.battery(ipad) > 10, "iPad didn't charge while riding: " + DeviceItem.battery(ipad));
            helper.assertTrue(DeviceItem.battery(loose) == 10, "a device charged without anyone riding");
            helper.assertTrue(DeviceItem.battery(new ItemStack(ModItems.MACBOOK.get())) == 100, "new devices should start full");
            helper.succeed();
        });
    }

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
