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
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;
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

    /**
     * A survival player that isn't connected to anything: enough to sit in seats and click. Not a networked
     * ServerPlayer, so PMWeather never tries to sync storms to it.
     */
    private static Player player(GameTestHelper helper) {
        return new Player(helper.getLevel(), helper.absolutePos(new BlockPos(8, 2, 8)), 0, new GameProfile(UUID.randomUUID(), "test-chaser")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
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

    /** The driver's dash screen is never buried inside the dashboard: nothing solid in front of its face. */
    @GameTest(template = "pad")
    public static void dashScreensAreVisible(GameTestHelper helper) {
        for (VehicleSpec spec : VehicleSpec.ALL) {
            Vec3 seat = spec.seats().get(0);
            double z = seat.z + VehicleModels.DASH_AHEAD - 0.034, y = seat.y + (VehicleModels.DASH_BOTTOM + VehicleModels.DASH_TOP) / 2;
            for (VehicleModels.Box b : VehicleModels.get(spec.id())) {
                if ((b.flags() & (VehicleModels.CLEAR | VehicleModels.ANCHOR)) != 0) continue;
                boolean buried = 0 > b.x0() && 0 < b.x1() && y > b.y0() && y < b.y1() && z > b.z0() && z < b.z1();
                helper.assertTrue(!buried, spec.id() + " dash screen is buried in " + b);
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

    /** Right-clicking from inside lets the item in your hand work (iPad, laptop) instead of re-entering the vehicle. */
    @GameTest(template = "pad")
    public static void ridersCanUseHandheldsInside(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        Player rider = player(helper);
        rider.moveTo(dom.position());
        helper.assertTrue(rider.startRiding(dom, true), "fake player couldn't get in");
        rider.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.IPAD.get()));
        var result = dom.interact(rider, net.minecraft.world.InteractionHand.MAIN_HAND);
        rider.stopRiding();
        rider.discard();
        helper.assertTrue(result == net.minecraft.world.InteractionResult.PASS, "clicking from inside was eaten by the vehicle: " + result);
        helper.succeed();
    }

    /** Drivers reach the car display from their seat; every passenger reaches the mounted passenger laptop. */
    @GameTest(template = "pad")
    public static void passengersGetTheLaptop(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        Player driver = player(helper), passenger = player(helper);
        driver.moveTo(dom.position());
        passenger.moveTo(dom.position());
        helper.assertTrue(driver.startRiding(dom, true) && passenger.startRiding(dom, true), "couldn't seat both");
        boolean ok = dom.seatScreen(driver) == com.interceptors.device.Device.CAR_DISPLAY
                && dom.seatScreen(passenger) == com.interceptors.device.Device.PASSENGER_LAPTOP;
        var empty = dom.interact(passenger, net.minecraft.world.InteractionHand.MAIN_HAND);
        driver.stopRiding();
        passenger.stopRiding();
        driver.discard();
        passenger.discard();
        helper.assertTrue(ok, "wrong screens for the seats");
        helper.assertTrue(empty.consumesAction(), "empty-hand click from the passenger seat did nothing: " + empty);
        helper.succeed();
    }

    /** When the driver gets out, nobody shuffles seats: the driver's seat stays empty until someone takes it. */
    @GameTest(template = "pad")
    public static void ridersKeepTheirSeats(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        Player driver = player(helper), front = player(helper), back = player(helper), late = player(helper);
        for (Player p : new Player[]{driver, front, back}) helper.assertTrue(p.startRiding(dom, true), "couldn't get in");
        helper.assertTrue(dom.seatOf(driver) == 0 && dom.seatOf(front) == 1 && dom.seatOf(back) == 2, "boarding seats wrong");
        driver.stopRiding();
        boolean kept = dom.seatOf(front) == 1 && dom.seatOf(back) == 2 && dom.getControllingPassenger() == null;
        late.startRiding(dom, true);
        boolean newDriver = dom.seatOf(late) == 0 && dom.getControllingPassenger() == late;
        for (Player p : new Player[]{front, back, late}) p.stopRiding();
        helper.assertTrue(kept, "riders moved seats, or someone kept driving, after the driver got out");
        helper.assertTrue(newDriver, "the next rider in didn't take the empty driver's seat");
        helper.succeed();
    }

    /** Aurora, 2009: an EF2+ passes over with the Lexan shield down, and the windows blow in on the front seats. */
    @GameTest(template = "pad", timeoutTicks = 60)
    public static void windowsBlowOutWithoutLexan(GameTestHelper helper) {
        InterceptorVehicle open = park(helper, VehicleSpec.DOMINATOR_1);
        net.minecraft.world.entity.npc.Villager driver = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        driver.moveTo(open.position());
        helper.getLevel().addFreshEntity(driver);
        driver.startRiding(open, true);
        float health = driver.getHealth();
        open.setAnchored(true);
        open.forcedWind = new Vec3(0, 0, 130);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(open.failed(Part.WINDOWS), "windows survived an EF2 with the Lexan down");
            helper.assertTrue(driver.getHealth() < health, "flying glass didn't hurt the driver");
            open.forcedWind = Vec3.ZERO;
            helper.succeed();
        });
    }

    @GameTest(template = "pad", timeoutTicks = 60)
    public static void lexanShieldSavesTheWindows(GameTestHelper helper) {
        InterceptorVehicle shielded = park(helper, VehicleSpec.DOMINATOR_1);
        helper.assertTrue(shielded.toggleLexan() == null && shielded.lexanUp(), "couldn't raise the Lexan");
        shielded.setAnchored(true);
        shielded.forcedWind = new Vec3(0, 0, 130);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(!shielded.failed(Part.WINDOWS), "windows blew out with the Lexan up");
            for (VehicleSpec other : VehicleSpec.ALL) {
                if (other != VehicleSpec.DOMINATOR_1) helper.assertTrue(!other.hasLexan(), other.id() + " shouldn't have Lexan: only Dom 1 does");
            }
            shielded.forcedWind = Vec3.ZERO;
            helper.succeed();
        });
    }

    /** Sneaking or being pulled at never turns the driver into a passenger. */
    @GameTest(template = "pad")
    public static void driverStaysDriverThroughShift(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        Player driver = player(helper), passenger = player(helper);
        driver.startRiding(dom, true);
        passenger.startRiding(dom, true);
        dom.forcedWind = new Vec3(0, 0, 200);
        driver.stopRiding(); // pulled at by the storm, not sneaking: stays put
        boolean heldInStorm = driver.getVehicle() == dom && dom.seatOf(driver) == 0 && dom.getControllingPassenger() == driver;
        dom.forcedWind = new Vec3(0, 0, 5);
        driver.setShiftKeyDown(true);
        driver.stopRiding();
        boolean outOnCalmDay = driver.getVehicle() == null && dom.seatOf(passenger) == 1 && dom.getControllingPassenger() == null;
        driver.setShiftKeyDown(false);
        driver.startRiding(dom, true);
        boolean backAsDriver = dom.seatOf(driver) == 0 && dom.getControllingPassenger() == driver
                && dom.seatScreen(driver) == com.interceptors.device.Device.CAR_DISPLAY;
        driver.stopRiding();
        passenger.stopRiding();
        helper.assertTrue(heldInStorm, "the storm moved the driver out of the driver's seat");
        helper.assertTrue(outOnCalmDay, "sneaking out on a calm day didn't empty the driver's seat cleanly");
        helper.assertTrue(backAsDriver, "getting back in didn't make them the driver with the car display");
        helper.succeed();
    }

    @GameTest(template = "pad")
    public static void repairKitFullyFixesTheWorstPart(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        dom.setCondition(Part.WINDOWS, 0);
        helper.assertTrue(dom.repairWorstPart() == Part.WINDOWS && dom.condition(Part.WINDOWS) == 100, "windows not fully fixed");
        helper.succeed();
    }

    @GameTest(template = "pad", timeoutTicks = 120)
    public static void vehiclesFloatInDeepWater(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int y = 2; y < 5; y++) for (int z = 0; z < 17; z++) {
            helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.WATER);
        }
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        double floor = dom.getY();
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(dom.getY() > floor + 1.2, "Dom 3 sank in 3 blocks of water: rose only " + (dom.getY() - floor));
            helper.succeed();
        });
    }

    /** Dates read like "MAY 5, 2026": PMWeather's month and day, with our year starting at 2026. */
    @GameTest(template = "pad")
    public static void calendarShowsTheYear(GameTestHelper helper) {
        String date = Storms.date(helper.getLevel());
        helper.assertTrue(date != null && date.matches("[A-Z ]+ \\d{1,2}, 20\\d\\d"), "date looks wrong: " + date);
        int year = Integer.parseInt(date.substring(date.length() - 4));
        helper.assertTrue(year >= Storms.FIRST_YEAR, "year before 2026: " + date);
        helper.succeed();
    }

    /** Nobody flying: the Storm Chopper autorotates down instead of hanging in the air. */
    @GameTest(template = "pad", timeoutTicks = 80)
    public static void chopperSinksWithoutAPilot(GameTestHelper helper) {
        InterceptorVehicle chopper = park(helper, VehicleSpec.STORM_CHOPPER);
        helper.assertTrue(chopper instanceof com.interceptors.vehicle.StormChopper, "the chopper isn't a helicopter");
        chopper.moveTo(chopper.getX(), chopper.getY() + 2.5, chopper.getZ());
        double start = chopper.getY();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(chopper.getY() < start - 0.5, "unpiloted chopper didn't come down: " + (start - chopper.getY()));
            helper.succeed();
        });
    }

    @GameTest(template = "pad")
    public static void probeLauncherOnlyOnTheDom3(GameTestHelper helper) {
        InterceptorVehicle dom = park(helper, VehicleSpec.DOMINATOR_3);
        helper.assertTrue(dom.probesLeft() == InterceptorVehicle.MAX_PROBES, "Dom 3 should start with a full launcher");
        helper.assertTrue("message.interceptors.probe_range".equals(dom.launchProbes()), "launched with no tornado in range");
        helper.assertTrue(dom.probesLeft() == InterceptorVehicle.MAX_PROBES, "probes used up with nothing to probe");
        for (VehicleSpec other : VehicleSpec.ALL) {
            if (other != VehicleSpec.DOMINATOR_3) helper.assertTrue(!other.probes() && !other.airSuspension(), other.id() + " shouldn't have Dom 3 gear");
        }
        helper.assertTrue("message.interceptors.no_launcher".equals(park(helper, VehicleSpec.DOMINATOR_1).launchProbes()), "Dom 1 launched probes");
        helper.succeed();
    }

    /** The OBS WebSocket login answer matches the example in the obs-websocket protocol docs. */
    @GameTest(template = "pad")
    public static void obsLoginMatchesTheProtocol(GameTestHelper helper) {
        String answer = com.interceptors.compat.ObsAuth.answer("supersecretpassword", "lM1GncleQOaCu9lT1yeUZhFYnqhsLLP1G5lAGo3ixaI=",
                "+IxH4CnCiqpX1rM9scsNynZzbOe4KhDeYcTNS3PDaeY=");
        helper.assertTrue(answer.equals("1Ct943GAT+6YQUUX47Ia/ncufilbe6+oD6lY+5kaCu4="), "OBS login answer wrong: " + answer);
        helper.succeed();
    }

    /** A hailstone left alone melts out: no bleeding, no extra harm. */
    @GameTest(template = "pad")
    public static void lodgedHailMeltsOutSafely(GameTestHelper helper) {
        Player p = player(helper);
        com.interceptors.event.Injuries.lodge(p, 1);
        helper.assertTrue(com.interceptors.event.Injuries.lodgedPart(p) == 1, "the stone didn't stick");
        float health = p.getHealth();
        for (int i = 0; i < com.interceptors.event.Injuries.MELT_TICKS; i++) com.interceptors.event.Injuries.tick(p);
        helper.assertTrue(com.interceptors.event.Injuries.lodgedPart(p) == -1, "the stone never melted");
        helper.assertTrue(com.interceptors.event.Injuries.bleedTicks(p) == 0 && p.getHealth() == health, "melting out shouldn't hurt");
        helper.succeed();
    }

    /** Pulling a stuck hailstone out is the big mistake: the wound bleeds for a while. */
    @GameTest(template = "pad")
    public static void pullingHailOutBleeds(GameTestHelper helper) {
        Player p = player(helper);
        com.interceptors.event.Injuries.lodge(p, 2);
        com.interceptors.event.Injuries.pullOut(p);
        helper.assertTrue(com.interceptors.event.Injuries.lodgedPart(p) == -1, "the stone is still in");
        helper.assertTrue(com.interceptors.event.Injuries.bleedTicks(p) == com.interceptors.event.Injuries.BLEED_TICKS, "it isn't bleeding");
        for (int i = 0; i < com.interceptors.event.Injuries.BLEED_TICKS; i++) {
            p.invulnerableTime = 0;
            com.interceptors.event.Injuries.tick(p);
        }
        helper.assertTrue(p.getHealth() <= p.getMaxHealth() - 8, "bleeding should hurt a lot, health is " + p.getHealth());
        helper.assertTrue(com.interceptors.event.Injuries.bleedTicks(p) == 0, "the bleeding never stopped");
        helper.succeed();
    }

    /** What the research says: Dom 3 gull-wing doors, a three-axle TIV 2, a Bell 407-size rotor, wind worse side-on. */
    @GameTest(template = "pad")
    public static void researchedBuildsMatchTheRealOnes(GameTestHelper helper) {
        var dom3 = com.interceptors.client.render.VehicleModels.get(VehicleSpec.DOMINATOR_3.id());
        helper.assertTrue(dom3.stream().anyMatch(b -> (b.flags() & com.interceptors.client.render.VehicleModels.DOOR) != 0 && b.x0() > 0)
                && dom3.stream().anyMatch(b -> (b.flags() & com.interceptors.client.render.VehicleModels.DOOR) != 0 && b.x1() < 0), "Dom 3 needs gull-wing doors on both sides");
        for (VehicleSpec spec : VehicleSpec.ALL) {
            if (spec == VehicleSpec.DOMINATOR_3) continue;
            helper.assertTrue(com.interceptors.client.render.VehicleModels.get(spec.id()).stream().noneMatch(b -> (b.flags() & com.interceptors.client.render.VehicleModels.DOOR) != 0),
                    spec.id() + " shouldn't have gull-wing doors");
        }
        long tires = com.interceptors.client.render.VehicleModels.get(VehicleSpec.TIV_2.id()).stream()
                .filter(b -> b.mat() == com.interceptors.client.render.Mat.TIRE && (b.flags() & com.interceptors.client.render.VehicleModels.WHEEL) != 0).count();
        helper.assertTrue(tires == 6, "TIV 2 rides on three axles, found " + tires + " tires");
        float rotor = 0;
        for (var b : com.interceptors.client.render.VehicleModels.get(VehicleSpec.STORM_CHOPPER.id())) {
            if ((b.flags() & com.interceptors.client.render.VehicleModels.SPIN) != 0) rotor = Math.max(rotor, Math.max(b.x1() - b.x0(), b.z1() - b.z0()));
        }
        helper.assertTrue(rotor > 10 && rotor < 11.5, "a Bell 407 rotor is about 10.7 m across, got " + rotor);
        net.minecraft.world.phys.Vec3 wind = new net.minecraft.world.phys.Vec3(0, 0, 100);
        double headOn = com.interceptors.vehicle.WindPhysics.sideFactor(wind, 0), sideOn = com.interceptors.vehicle.WindPhysics.sideFactor(wind, 90);
        helper.assertTrue(sideOn > headOn * 1.4, "side-on wind should push much harder: " + headOn + " vs " + sideOn);
        helper.succeed();
    }

    /** Not every car has the same dash: each vehicle family gets its own look. */
    @GameTest(template = "pad")
    public static void everyVehicleHasItsOwnDash(GameTestHelper helper) {
        java.util.Map<VehicleSpec, com.interceptors.client.gui.DashTheme> want = java.util.Map.of(
                VehicleSpec.DOMINATOR_1, com.interceptors.client.gui.DashTheme.DOMINATOR,
                VehicleSpec.DOMINATOR_3, com.interceptors.client.gui.DashTheme.DOMINATOR,
                VehicleSpec.TIV_1, com.interceptors.client.gui.DashTheme.MILITARY,
                VehicleSpec.TIV_2, com.interceptors.client.gui.DashTheme.MILITARY,
                VehicleSpec.DOPPLER_ON_WHEELS, com.interceptors.client.gui.DashTheme.RADAR,
                VehicleSpec.GAMING_VAN, com.interceptors.client.gui.DashTheme.GAMING,
                VehicleSpec.RENTAL_CAR, com.interceptors.client.gui.DashTheme.STOCK,
                VehicleSpec.STORM_CHOPPER, com.interceptors.client.gui.DashTheme.COCKPIT);
        want.forEach((spec, theme) -> helper.assertTrue(com.interceptors.client.gui.DashTheme.of(spec) == theme,
                spec.id() + " has the " + com.interceptors.client.gui.DashTheme.of(spec) + " dash, wanted " + theme));
        helper.succeed();
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

    /** Czech, German, Austrian German and Croatian each translate every English line, with the same blanks to fill. */
    @GameTest(template = "pad")
    public static void everyLanguageHasEveryLine(GameTestHelper helper) {
        java.util.Map<String, String> english = lang("en_us");
        java.util.regex.Pattern blank = java.util.regex.Pattern.compile("%(\\d+\\$)?[-+0-9.]*[sdf%]");
        for (String code : new String[]{"cs_cz", "de_de", "de_at", "hr_hr"}) {
            java.util.Map<String, String> other = lang(code);
            for (var e : english.entrySet()) {
                String line = other.get(e.getKey());
                helper.assertTrue(line != null, code + " is missing " + e.getKey());
                helper.assertTrue(blank.matcher(line).results().map(m -> m.group()).sorted().toList()
                        .equals(blank.matcher(e.getValue()).results().map(m -> m.group()).sorted().toList()), code + " " + e.getKey() + " has different blanks");
            }
            helper.assertTrue(english.keySet().containsAll(other.keySet()), code + " has lines English doesn't");
        }
        helper.succeed();
    }

    private static java.util.Map<String, String> lang(String code) {
        try (var in = InterceptorsGameTests.class.getResourceAsStream("/assets/interceptors/lang/" + code + ".json")) {
            if (in == null) throw new IllegalStateException("no " + code + ".json");
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            java.util.Map<String, String> out = new java.util.HashMap<>();
            for (var e : json.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
            return out;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
