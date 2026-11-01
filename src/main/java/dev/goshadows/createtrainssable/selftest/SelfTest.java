package dev.goshadows.createtrainssable.selftest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.bridge.BridgeTool;
import dev.goshadows.createtrainssable.bridge.BridgedCarriage;
import dev.goshadows.createtrainssable.bridge.CarriageBody;
import dev.goshadows.createtrainssable.bridge.TrainBridge;
import dev.goshadows.createtrainssable.bridge.TrainCouplings;
import dev.goshadows.createtrainssable.bridge.TrainStress;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.goshadows.createtrainssable.rail.TruckBlockEntity;
import dev.goshadows.createtrainssable.rail.Vec;
import dev.goshadows.createtrainssable.rail.WheelRail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Scripted physics session: builds a vehicle, lets the engine drive it, and reports numbers.
 *
 * <p>The harness is off unless the JVM is started with {@code -Dcts.selftest=true}, which the
 * {@code runSelfTest} Gradle task does. It runs on a dedicated server with no player, so what it
 * measures is the physics itself: where the body went, how fast it moved, which axle still held the
 * rail. Every scenario prints one line with its measurements and a final line with the totals, and
 * the continuous integration job fails unless the totals say that nothing failed.</p>
 *
 * <p>The scenarios are the contract of the mod, in order: the vehicle can be assembled and finds its
 * rail, it stays on the rail, the driven axle reaches the speed its shaft asks for, a vehicle pushed
 * off the rail keeps its momentum and falls, a Create train follows the body it is bridged to, the
 * player's bridge action binds and unbinds without damaging the train, a coupling pulls an unpowered
 * wagon behind a powered one, three vehicles hold together through two couplings, and a consist of
 * three runs for a minute without growing.</p>
 */
public final class SelfTest {

    /** JVM property that switches the harness on; nothing else reads it. */
    public static final String ENABLED_PROPERTY = "cts.selftest";

    private static Session session;

    private SelfTest() {
    }

    /** Attached by the mod constructor only when the harness is armed. */
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            return;
        }
        session = new Session(event.getServer());
        session.begin();
    }

    /** Attached by the mod constructor only when the harness is armed. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (session != null) {
            session.tick();
        }
    }

    /** One scenario after another, then a summary and a clean shutdown. */
    private static final class Session {

        private final MinecraftServer server;
        private final List<Scenario> scenarios = List.of(
                new AssemblyScenario(),
                new GuideScenario(),
                new TractionScenario(),
                new DerailScenario(),
                new BridgeScenario(),
                new EntryScenario(),
                new CoupleScenario(),
                new ChainScenario(),
                new ConductorsScenario(),
                new StationScenario("station", 1400, 2562),
                new SignalScenario(),
                new SoakScenario());

        private int index = -1;
        private int passed;
        private int failed;
        private Scenario current;

        private Session(MinecraftServer server) {
            this.server = server;
        }

        private void begin() {
            CreateTrainsSable.LOGGER.info("CTS SELFTEST begin scenarios={}", scenarios.size());
            advance();
        }

        private void tick() {
            if (current == null) {
                return;
            }
            try {
                current.tick();
            } catch (RuntimeException e) {
                current.fail("exception: " + e);
                CreateTrainsSable.LOGGER.error("CTS SELFTEST scenario={} threw", current.name, e);
            }
            if (current.finished) {
                complete();
            }
        }

        private void complete() {
            boolean ok = current.failure == null;
            CreateTrainsSable.LOGGER.info("CTS SELFTEST scenario={} {} {}", current.name,
                    ok ? "PASS" : "FAIL", current.metrics());
            if (!ok) {
                CreateTrainsSable.LOGGER.info("CTS SELFTEST scenario={} reason=\"{}\"", current.name,
                        current.failure);
            }
            try {
                current.cleanup();
            } catch (RuntimeException e) {
                CreateTrainsSable.LOGGER.error("CTS SELFTEST scenario={} could not be cleaned up",
                        current.name, e);
            }
            if (ok) {
                passed++;
            } else {
                failed++;
            }
            current = null;
            advance();
        }

        private void advance() {
            index++;
            if (index >= scenarios.size()) {
                summary();
                return;
            }
            current = scenarios.get(index);
            current.begin(server.overworld());
        }

        private void summary() {
            CreateTrainsSable.LOGGER.info("CTS SELFTEST SUMMARY scenarios={} passed={} failed={}",
                    scenarios.size(), passed, failed);
            session = null;
            server.execute(() -> server.halt(false));
        }
    }

    /**
     * One measurement of the vehicle. A scenario sets {@link #failure} to fail, or leaves it
     * {@code null} and finishes when its budget runs out.
     */
    private abstract static class Scenario {

        final String name;
        private final int budget;
        final int z;

        ServerLevel level;
        TestRig rig;
        int ticks;
        String failure;
        boolean finished;

        Scenario(String name, int budget, int z) {
            this.name = name;
            this.budget = budget;
            this.z = z;
        }

        /** Builds the rig; a failure here fails the scenario instead of throwing. */
        void begin(ServerLevel level) {
            this.level = level;
            try {
                this.rig = TestRig.assemble(level, 2000, 100, z);
            } catch (RuntimeException e) {
                CreateTrainsSable.LOGGER.error("CTS SELFTEST scenario={} could not build the rig",
                        name, e);
                fail("assembly: " + e);
                finished = true;
            }
        }

        void tick() {
            ticks++;
            try {
                step();
            } catch (RuntimeException e) {
                fail("tick " + ticks + ": " + e);
            }
            if (!finished && (failure != null || ticks >= budget)) {
                finish();
            }
        }

        /** One server tick of the scenario. */
        abstract void step();

        /** Called when the budget runs out; the scenario decides whether that is a pass. */
        abstract void finish();

        void fail(String reason) {
            if (failure == null) {
                failure = reason;
            }
            finished = true;
        }

        void pass() {
            finished = true;
        }

        abstract String metrics();

        void cleanup() {
            if (rig != null) {
                rig.drive(0.0f);
                rig.remove();
                rig = null;
            }
        }

        /** Truck states as a compact list. */
        String states() {
            return rig == null ? "[]" : rig.states();
        }

        boolean allTrucks(WheelRail.State state) {
            if (rig == null || rig.trucks.isEmpty()) {
                return false;
            }
            for (TruckBlockEntity truck : rig.trucks) {
                if (truck.state() != state) {
                    return false;
                }
            }
            return true;
        }

        String f(double value) {
            return String.format(Locale.ROOT, "%.3f", value);
        }

        /** One telemetry line; the CI report quotes every {@code CTS} line of the log. */
        void log(String values) {
            CreateTrainsSable.LOGGER.info("CTS DEBUG scenario={} t={} {}", name, ticks, values);
        }

        /** Axle zero, or the origin when there is no body. */
        Vec axle() {
            if (rig == null || rig.trucks.isEmpty()) {
                return Vec.ZERO;
            }
            return rig.axles().get(0);
        }

        Vec velocity() {
            return rig == null ? Vec.ZERO : rig.velocity();
        }
    }

    /**
     * A one-carriage Create train on a rig's rail, registered in Create's railway manager.
     *
     * <p>The two bogeys sit on the two axles of the rig, so the carriage and the body start aligned
     * and any later difference is the physics, not an offset. A standard bogey's wheel points are two
     * blocks apart, and its first point is the front of the train, so the carriage faces east the way
     * the rig's traction truck does.</p>
     */
    private static Train singleCarriageTrain(TestRig rig, double leadingAxle, double trailingAxle) {
        CarriageBogey leading = new CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, new CompoundTag(),
                rig.railPoint(leadingAxle + 1.0), rig.railPoint(leadingAxle - 1.0));
        CarriageBogey trailing = new CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, new CompoundTag(),
                rig.railPoint(trailingAxle - 1.0), rig.railPoint(trailingAxle + 1.0));
        Carriage carriage = new Carriage(leading, trailing, 2);
        Train train = new Train(UUID.randomUUID(), UUID.randomUUID(), rig.graph,
                new ArrayList<>(List.of(carriage)), new ArrayList<>(), true, 0);
        Create.RAILWAYS.addTrain(train);
        return train;
    }

    /**
     * One carriage sitting on the two axles of a rig whose deck starts at {@code x}.
     *
     * <p>A standard bogey's two wheel points are two blocks apart, and the leading bogey is the one
     * nearer the front of the train, so the names are not arbitrary: the carriage travels towards
     * increasing x the way the rig's traction truck does.</p>
     */
    private static Carriage carriageOn(TestRig rig, int x) {
        CarriageBogey leading = new CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, new CompoundTag(),
                rig.railPoint(x + 4.5), rig.railPoint(x + 2.5));
        CarriageBogey trailing = new CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, new CompoundTag(),
                rig.railPoint(x + 2.5), rig.railPoint(x + 0.5));
        return new Carriage(leading, trailing, 2);
    }

    /**
     * A Create train with one carriage per rig, ordered the way the rigs are given: leading first.
     *
     * <p>The spacing is whatever the carriages' own points measure, which is what a player's train
     * has, rather than a number chosen to make a measurement come out right.</p>
     */
    private static Train trainOf(List<TestRig> rigs, int[] deckX) {
        List<Carriage> carriages = new ArrayList<>(rigs.size());
        for (int i = 0; i < rigs.size(); i++) {
            carriages.add(carriageOn(rigs.get(i), deckX[i]));
        }
        List<Integer> spacings = new ArrayList<>(Math.max(0, rigs.size() - 1));
        for (int i = 1; i < carriages.size(); i++) {
            double gap = carriages.get(i - 1)
                    .getTrailingPoint()
                    .getPosition(rigs.get(0).graph)
                    .distanceTo(carriages.get(i).getLeadingPoint().getPosition(rigs.get(0).graph));
            spacings.add((int) Math.round(gap));
        }
        Train train = new Train(UUID.randomUUID(), UUID.randomUUID(), rigs.get(0).graph,
                new ArrayList<>(carriages), new ArrayList<>(spacings), true, 0);
        Create.RAILWAYS.addTrain(train);
        return train;
    }

    /**
     * The vehicle exists and its axles find the rail.
     *
     * <p>This is the gate for everything else: a body with the wrong mass, a truck that did not
     * survive the assembly, or a rail the contact cannot see makes every later number meaningless.
     */
    private static final class AssemblyScenario extends Scenario {

        private double mass;
        private int truckCount;

        AssemblyScenario() {
            super("assembly", 10, 2000);
        }

        @Override
        void step() {
            // Nothing to do: the assembly already happened in begin().
        }

        @Override
        void finish() {
            if (rig == null) {
                fail("no body");
                return;
            }
            mass = rig.mass();
            truckCount = rig.trucks.size();
            if (truckCount != 2) {
                fail("expected 2 trucks, found " + truckCount);
                return;
            }
            if (!(mass > 1.0)) {
                fail("mass " + mass + " is not a body");
                return;
            }
            for (TruckBlockEntity truck : rig.trucks) {
                if (truck.state() == WheelRail.State.NO_TRACK) {
                    fail("truck found no rail: " + states());
                    return;
                }
            }
            pass();
        }

        @Override
        String metrics() {
            return "blocks=" + (rig == null ? 0 : rig.blockCount)
                    + " trucks=" + truckCount
                    + " mass=" + f(mass)
                    + " states=" + states()
                    + " removed=" + (rig != null && rig.isRemoved())
                    + " ticks=" + ticks;
        }
    }

    /**
     * A vehicle that is already on the rail stays on it.
     *
     * <p>The truck is given a sideways push before the run; the flange has to bring it back and
     * keep it there without oscillating out of the gauge. The vertical offset is measured and
     * reported rather than asserted: the contact carries the weight through a spring, so a body at
     * rest sits above the rail by the sag of that spring.</p>
     */
    private static final class GuideScenario extends Scenario {

        private double worstLateral;
        private double finalLateral;
        private double finalVertical;
        private double finalSpeed;

        GuideScenario() {
            super("guide", 200, 2096);
        }

        @Override
        void step() {
            if (rig == null) {
                return;
            }
            if (ticks == 20) {
                // Half a metre per second sideways: enough to see the flange work, not enough to
                // leave the gauge.
                rig.push(new Vec(0, 0, 0.5 * rig.mass()));
            }
            worstLateral = Math.max(worstLateral, rig.lateralError());
            if (ticks % 10 == 0) {
                log("lateral=" + f(rig.lateralError())
                        + " ypr=" + rig.attitude()
                        + " axles=[" + rig.axleDetails() + "]"
                        + " fz=" + f(rig.truckForce().z())
                        + " vz=" + f(rig.velocity().z())
                        + " vx=" + f(rig.velocity().x())
                        + " y=" + f(axle().y())
                        + " " + states());
            }
            if (ticks >= 200) {
                finalLateral = rig.lateralError();
                finalVertical = rig.verticalOffset();
                finalSpeed = rig.velocity().length();
            }
        }

        @Override
        void finish() {
            if (rig == null) {
                fail("no body");
                return;
            }
            if (worstLateral > 1.2) {
                fail("lateral error " + f(worstLateral) + " left the flange");
                return;
            }
            if (Math.abs(finalLateral) > 0.2) {
                fail("lateral error " + f(finalLateral) + " did not settle");
                return;
            }
            if (finalVertical < -0.05 || finalVertical > 1.0) {
                fail("vertical offset " + f(finalVertical) + " is not a resting vehicle");
                return;
            }
            if (finalSpeed > 0.5) {
                fail("the vehicle is still moving at " + f(finalSpeed));
                return;
            }
            if (!allTrucks(WheelRail.State.GUIDING)) {
                fail("not guiding: " + states());
                return;
            }
            pass();
        }

        @Override
        String metrics() {
            return "lateral_max=" + f(worstLateral)
                    + " lateral_final=" + f(finalLateral)
                    + " vertical=" + f(finalVertical)
                    + " speed=" + f(finalSpeed)
                    + " states=" + states()
                    + " ticks=" + ticks;
        }
    }

    /**
     * The driven axle reaches the speed its shaft asks for, and the vehicle actually travels.
     *
     * <p>32 RPM at the default gearing is 1.6 m/s. The measured speed has to land near that, and
     * the body has to cover real ground: a vehicle that reports the right speed but does not move
     * would be a contact that computes without pushing.</p>
     */
    private static final class TractionScenario extends Scenario {

        private static final float RPM = 32.0f;
        private static final double TARGET = 1.6;

        private Vec start;
        private double speed;
        private double travelled;

        TractionScenario() {
            super("traction", 220, 2192);
        }

        @Override
        void step() {
            if (rig == null) {
                return;
            }
            if (ticks == 20) {
                start = rig.axles().get(0);
                rig.drive(RPM);
            }
            if (ticks % 20 == 0 && ticks >= 20) {
                log("x=" + f(axle().x())
                        + " vx=" + f(rig.velocity().x())
                        + " vz=" + f(rig.velocity().z())
                        + " lateral=" + f(rig.lateralError())
                        + " " + states());
            }
            if (ticks >= 220 && start != null) {
                speed = rig.velocity().x();
                travelled = rig.axles().get(0).x() - start.x();
            }
        }

        @Override
        void finish() {
            if (rig == null || start == null) {
                fail("the vehicle never got its shaft speed");
                return;
            }
            if (speed < TARGET * 0.8 || speed > TARGET * 1.2) {
                fail("speed " + f(speed) + " is not " + TARGET + " m/s");
                return;
            }
            if (travelled < 2.0) {
                fail("travelled " + f(travelled) + " m, which is not motion");
                return;
            }
            if (!allTrucks(WheelRail.State.GUIDING)) {
                fail("not guiding while driving: " + states());
                return;
            }
            pass();
        }

        @Override
        String metrics() {
            return "target=" + f(TARGET)
                    + " speed=" + f(speed)
                    + " travelled=" + f(travelled)
                    + " states=" + states()
                    + " ticks=" + ticks;
        }
    }

    /**
     * A vehicle pushed off the rail leaves it, keeps its momentum and falls.
     *
     * <p>This is the difference between a physics vehicle and a scripted one: the derailment is
     * not an animation, it is the contact giving up while the body carries on doing what a body
     * does.</p>
     */
    private static final class DerailScenario extends Scenario {

        /**
         * Sideways speed the harness gives the body.
         *
         * <p>Every axle can decelerate the vehicle at {@code maxGuideAcceleration}, so the vehicle
         * sheds {@code axles * 26 = 52 m/s^2} and a push stops within {@code v^2 / 104} blocks. The
         * flange limit is 1.2 blocks, so anything above {@code sqrt(2 * 52 * 1.2) = 11.2 m/s} is a
         * derailment and anything below it is absorbed. Fourteen metres per second is past that
         * boundary; that boundary is the model's, not the test's.</p>
         */
        private static final double PUSH = 14.0;

        private double horizontalSpeed;
        private double dropped;
        private boolean pushed;
        private boolean sawOffRail;

        DerailScenario() {
            super("derail", 90, 2288);
        }

        @Override
        void step() {
            if (rig == null) {
                return;
            }
            if (ticks == 20) {
                rig.push(new Vec(0, 0, PUSH * rig.mass()));
                pushed = true;
            }
            if (pushed && rig.states().contains("OFF_RAIL")) {
                sawOffRail = true;
            }
            if (ticks % 10 == 0 && ticks >= 20) {
                log("lateral=" + f(rig.lateralError())
                        + " ypr=" + rig.attitude()
                        + " axles=[" + rig.axleDetails() + "]"
                        + " fz=" + f(rig.truckForce().z())
                        + " vz=" + f(rig.velocity().z())
                        + " y=" + f(axle().y())
                        + " " + states());
            }
            if (pushed && ticks >= 90) {
                Vec velocity = rig.velocity();
                horizontalSpeed = Math.hypot(velocity.x(), velocity.z());
                List<Vec> axles = rig.axles();
                dropped = axles.get(0).y() - rig.railHeight();
            }
        }

        @Override
        void finish() {
            if (rig == null || !pushed) {
                fail("the vehicle was never pushed");
                return;
            }
            if (!sawOffRail) {
                fail("the flange never gave up after a " + PUSH + " m/s push: " + states());
                return;
            }
            if (allTrucks(WheelRail.State.GUIDING)) {
                fail("still guiding after the push: " + states());
                return;
            }
            if (horizontalSpeed < 2.0) {
                fail("the body lost its momentum: " + f(horizontalSpeed) + " m/s");
                return;
            }
            if (dropped > -0.3) {
                fail("the body did not fall: dy=" + f(-dropped));
                return;
            }
            pass();
        }

        @Override
        String metrics() {
            return "push=" + f(PUSH)
                    + " horizontal_speed=" + f(horizontalSpeed)
                    + " dropped=" + f(-dropped)
                    + " saw_off_rail=" + sawOffRail
                    + " states=" + states()
                    + " ticks=" + ticks;
        }
    }


    /**
     * A Create train with one carriage riding a physical body.
     *
     * <p>The harness builds a real {@link Train} with a real {@link Carriage} on the same track graph
     * the trucks ride, registers it in Create's railway manager, and bridges it to the rig's body.
     * From then on Create's own train loop drives this scenario: its speed is the order, the axles
     * turn it into force, and the carriage travels the distance the body actually covered.</p>
     *
     * <p>The numbers this scenario asserts are the seam itself: the carriage must not drift away from
     * the body, the body must reach the commanded speed through the wheels, the axles must stay
     * inside the flange, and the carriage's anchor must sit over the leading axle rather than over a
     * place Create's own integration would have chosen.</p>
     */
    private static final class BridgeScenario extends Scenario {

        /** Speed the train asks for, in blocks per tick: 0.08 * 20 = 1.6 m/s. */
        private static final double COMMAND = 0.08;

        /**
         * X of the two axles the rig puts under the deck.
         *
         * <p>A truck's axle sits in the middle of its block, so the leading axle is at 2001.5 and the
         * trailing one at 2003.5. The carriage's bogeys are put on the same two x, which makes the
         * reported anchor gap a real check instead of a constant half-block offset.</p>
         */
        private static final double LEADING_AXLE = 2001.5;
        private static final double TRAILING_AXLE = 2003.5;

        private static final double TARGET_METRES_PER_SECOND = COMMAND * CarriageBody.TICKS_PER_SECOND;

        private Train train;
        private TrainBridge.Bridge bridge;
        private BridgedCarriage bridged;
        private double startCentre;
        private double travelledBody;
        private double speed;
        private double peak;
        private double lateralMax;
        private double anchorGap = Double.NaN;
        private int riseTick = -1;
        private boolean inRailwayManager;
        private final List<Double> speeds = new ArrayList<>();

        BridgeScenario() {
            super("bridge", 220, 2376);
        }

        @Override
        void begin(ServerLevel level) {
            super.begin(level);
            if (rig == null) {
                return;
            }
            try {
                buildTrain();
            } catch (RuntimeException | LinkageError e) {
                fail("train: " + e);
                finished = true;
            }
        }

        /**
         * A carriage with two standard bogeys on the synthetic rail, one at each axle of the rig.
         *
         * <p>Bogey points are two blocks apart, matching the wheel point spacing of a standard bogey;
         * the leading bogey's first point is the front of the train, so the carriage faces east, the
         * way the rig's traction truck does.</p>
         */
        private void buildTrain() {
            train = singleCarriageTrain(rig, LEADING_AXLE, TRAILING_AXLE);
            train.targetSpeed = COMMAND;
            bridge = TrainBridge.bridge(train, rig.body);
            bridged = bridge.carriages().getFirst();
            startCentre = rig.centre().x();
        }

        @Override
        void step() {
            if (rig == null || train == null || bridged == null) {
                return;
            }
            // The train controls of a manual train do exactly this every tick: hold the lever,
            // mark the tick as manual, and let Create's own acceleration ramp the order up.
            // Without the flag Create would coast the order down to zero against the ramp.
            train.manualTick = true;
            train.targetSpeed = COMMAND;
            train.approachTargetSpeed(1.0f);

            speed = bridged.measuredSpeed();
            speeds.add(speed);
            peak = Math.max(peak, Math.abs(speed));
            lateralMax = Math.max(lateralMax, rig.lateralError());
            if (ticks % 20 == 0) {
                log("body=" + f(rig.centre().x() - startCentre)
                        + " train=" + f(bridged.travelToDate())
                        + " speed=" + f(speed)
                        + " ordered=" + f(train.speed * CarriageBody.TICKS_PER_SECOND)
                        + " anchor_gap=" + f(anchorGap())
                        + " lateral=" + f(rig.lateralError())
                        + " " + states());
            }
        }

        @Override
        void finish() {
            if (rig == null || train == null || bridged == null) {
                fail("no bridged train");
                return;
            }
            travelledBody = rig.centre().x() - startCentre;
            anchorGap = anchorGap();
            riseTick = rampTicks();
            inRailwayManager = Create.RAILWAYS.trains.get(train.id) == train;
            if (!inRailwayManager) {
                fail("the train is no longer in Create's railway manager");
                return;
            }
            if (TrainBridge.bridged(train) != bridge || train.carriages.size() != 1) {
                fail("the train no longer carries the bridged carriage");
                return;
            }
            if (Math.abs(travelledBody - bridged.travelToDate()) > 0.5) {
                fail("the carriage drifted from the body: body=" + f(travelledBody)
                        + " carriage=" + f(bridged.travelToDate()));
                return;
            }
            if (travelledBody < 2.0) {
                fail("the body barely moved: " + f(travelledBody));
                return;
            }
            if (speed < 0.8 * TARGET_METRES_PER_SECOND || speed > 1.2 * TARGET_METRES_PER_SECOND) {
                fail("the body does not hold the speed the train asked for: " + f(speed)
                        + " m/s against " + f(TARGET_METRES_PER_SECOND));
                return;
            }
            if (peak > 1.15 * TARGET_METRES_PER_SECOND) {
                fail("the body jumped to " + f(peak) + " m/s; a body that accelerates in one tick is"
                        + " being teleported, not driven");
                return;
            }
            if (lateralMax > CTSConfig.trucks().maxLateralError()) {
                fail("an axle left the gauge: lateral=" + f(lateralMax));
                return;
            }
            if (!(anchorGap <= 0.5)) {
                fail("the carriage anchor is " + f(anchorGap) + " blocks away from the leading axle");
                return;
            }
            pass();
        }

        /**
         * Ticks the body needed to reach nine tenths of its final speed.
         *
         * <p>A body dragged along by the train would be at full speed on the first tick. This number
         * is the time the wheels needed, and it is measured against the speed the body ended at
         * rather than against the order, because the idling axle means the two never quite meet.</p>
         */
        private int rampTicks() {
            double settled = Math.abs(speed);
            if (settled < 1.0e-3) {
                return -1;
            }
            for (int i = 0; i < speeds.size(); i++) {
                if (Math.abs(speeds.get(i)) >= 0.9 * settled) {
                    return i + 1;
                }
            }
            return -1;
        }

        /** Horizontal distance between the carriage's leading anchor and the nearest axle. */
        private double anchorGap() {
            if (bridged == null || rig == null) {
                return Double.NaN;
            }
            Vec3 anchor = bridged.leadingBogey().getAnchorPosition();
            if (anchor == null) {
                return Double.NaN;
            }
            double best = Double.MAX_VALUE;
            for (Vec axle : rig.axles()) {
                best = Math.min(best, Math.hypot(anchor.x - axle.x(), anchor.z - axle.z()));
            }
            return best;
        }

        @Override
        String metrics() {
            double travelledTrain = bridged == null ? 0.0 : bridged.travelToDate();
            return "carriages=" + (train == null ? 0 : train.carriages.size())
                    + " target=" + f(TARGET_METRES_PER_SECOND)
                    + " speed=" + f(speed)
                    + " travelled_body=" + f(travelledBody)
                    + " travelled_train=" + f(travelledTrain)
                    + " drift=" + f(travelledBody - travelledTrain)
                    + " lateral_max=" + f(lateralMax)
                    + " anchor_gap=" + f(anchorGap)
                    + " ordered=" + f(train == null ? 0.0 : train.speed * CarriageBody.TICKS_PER_SECOND)
                    + " peak=" + f(peak)
                    + " rise_ticks=" + riseTick
                    + " in_railway_manager=" + inRailwayManager
                    + " ticks=" + ticks;
        }

        @Override
        void cleanup() {
            if (train != null) {
                TrainBridge.detach(train);
                Create.RAILWAYS.removeTrain(train.id);
                train.invalid = true;
                train = null;
            }
            bridge = null;
            bridged = null;
            super.cleanup();
        }
    }


    /**
     * The player's entry point: the debug tool bridges and unbinds a train, and the train survives it.
     *
     * <p>This scenario never calls {@code TrainBridge} directly. It calls {@link BridgeTool#toggle}
     * with the position of a truck, which is the same call the assembly tool makes when a player
     * sneaks and right-clicks - so what is measured here is the path a player takes.</p>
     *
     * <p>Unbinding is the interesting half. The carriage object is kept and merely deactivated, so
     * the train's identity, its carriage ids and its serialised form do not change; the body stops
     * being driven and coasts to a halt under its own brakes. Re-binding drives it again from where
     * it stopped. The scenario also writes the carriage to NBT and reads it back, because that is
     * the thing that would silently break a train after a save.</p>
     */
    private static final class EntryScenario extends Scenario {

        /** Speed the train asks for, in blocks per tick: 0.08 * 20 = 1.6 m/s. */
        private static final double COMMAND = 0.08;

        private static final int UNBRIDGE_AT = 80;
        private static final int REBRIDGE_AT = 100;
        private static final int END = 170;

        private Train train;
        private BridgeTool.Outcome first;
        private BridgeTool.Outcome second;
        private BridgeTool.Outcome third;
        private double start;
        private double movedBefore;
        private double movedAfterUnbind;
        private double trainMovedWhileUnbound;
        private double movedAfterRebind;
        private double speedAfterUnbind;
        private double travelAtUnbind;
        private double rebindStart;
        private String nbtClass = "?";
        private int nbtSpacing = -1;
        private boolean nbtOk;
        private String nbtError = "";
        private boolean activeAfterRebind;

        EntryScenario() {
            super("entry", END, 2464);
        }

        @Override
        void begin(ServerLevel level) {
            super.begin(level);
            if (rig == null) {
                finished = true;
            }
        }

        @Override
        void step() {
            if (rig == null) {
                return;
            }
            if (ticks == 1) {
                // The train has to exist and be bridged in the same tick: a travel-less carriage
                // has no contraption data for Create to spawn, which is exactly the limitation this
                // harness works around and a real train never has.
                train = singleCarriageTrain(rig, 2001.5, 2003.5);
                train.targetSpeed = COMMAND;
                CreateTrainsSable.LOGGER.info("CTS DEBUG scenario=entry built train {} trains={}",
                        train.id, Create.RAILWAYS.trains.size());
                first = BridgeTool.toggle(level, rig.clickedPos(), null);
                start = rig.centre().x();
                if (first.status() != BridgeTool.Status.BRIDGED) {
                    fail("the tool did not bridge: " + first.status() + " " + first.detail());
                    return;
                }
            }
            if (first == null || first.status() != BridgeTool.Status.BRIDGED) {
                return;
            }
            train.manualTick = true;
            train.targetSpeed = COMMAND;
            train.approachTargetSpeed(1.0f);

            if (ticks == UNBRIDGE_AT) {
                movedBefore = rig.centre().x() - start;
                second = BridgeTool.toggle(level, rig.clickedPos(), null);
                if (train.carriages.getFirst() instanceof BridgedCarriage carriage) {
                    travelAtUnbind = carriage.travelToDate();
                }
            }
            if (ticks == REBRIDGE_AT) {
                movedAfterUnbind = rig.centre().x() - start;
                if (train.carriages.getFirst() instanceof BridgedCarriage carriage) {
                    trainMovedWhileUnbound = carriage.travelToDate() - travelAtUnbind;
                }
                speedAfterUnbind = rig.velocity().x();
                third = BridgeTool.toggle(level, rig.clickedPos(), null);
                rebindStart = rig.centre().x();
                activeAfterRebind = TrainBridge.isBridged(train)
                        && train.carriages.getFirst() instanceof BridgedCarriage bridged
                        && bridged.isActive();
            }
            if (ticks > REBRIDGE_AT) {
                movedAfterRebind = rig.centre().x() - rebindStart;
            }
            if (ticks % 20 == 0) {
                log("x=" + f(rig.centre().x() - start)
                        + " speed=" + f(rig.velocity().x())
                        + " bridged=" + TrainBridge.isBridged(train)
                        + " carriage=" + train.carriages.getFirst().getClass().getSimpleName()
                        + " " + states());
            }
        }

        @Override
        void finish() {
            if (rig == null || train == null) {
                fail("no train");
                return;
            }
            if (first == null || first.status() != BridgeTool.Status.BRIDGED) {
                fail("the tool never bridged the train");
                return;
            }
            if (second == null || second.status() != BridgeTool.Status.UNBRIDGED) {
                fail("the tool did not unbind: " + (second == null ? "never called" : second.status()));
                return;
            }
            if (third == null || third.status() != BridgeTool.Status.BRIDGED) {
                fail("the tool did not bridge again: " + (third == null ? "never called" : third.status()));
                return;
            }
            if (!activeAfterRebind) {
                fail("the train is not carrying an active bridged carriage after re-binding");
                return;
            }
            readBack();
            if (!nbtOk) {
                fail("the carriage did not survive a write and read: " + nbtError);
                return;
            }
            if (movedBefore < 2.0) {
                fail("the body barely moved while bridged: " + f(movedBefore));
                return;
            }
            if (speedAfterUnbind > 0.3) {
                fail("the body was still driven after unbinding: " + f(speedAfterUnbind) + " m/s");
                return;
            }
            if (trainMovedWhileUnbound < 0.5) {
                fail("the train stopped moving when it was unbound: " + f(trainMovedWhileUnbound));
                return;
            }
            if (movedAfterRebind < 1.0) {
                fail("the body did not move again after re-binding: " + f(movedAfterRebind));
                return;
            }
            pass();
        }

        /** Writes the bridged carriage to NBT and reads it back, as a save would. */
        private void readBack() {
            try {
                Carriage carriage = train.carriages.getFirst();
                CompoundTag tag = carriage.write(new DimensionPalette(), level.registryAccess());
                Carriage back = Carriage.read(tag, level.registryAccess(), rig.graph,
                        new DimensionPalette());
                nbtClass = back.getClass().getSimpleName();
                nbtSpacing = back.bogeySpacing;
                nbtOk = nbtSpacing == carriage.bogeySpacing;
            } catch (RuntimeException e) {
                nbtError = e.toString();
                nbtOk = false;
            }
        }

        @Override
        String metrics() {
            return "bridge=" + status(first)
                    + " unbridge=" + status(second)
                    + " rebridge=" + status(third)
                    + " active_after_rebind=" + activeAfterRebind
                    + " moved_bridged=" + f(movedBefore)
                    + " moved_while_unbound=" + f(movedAfterUnbind - movedBefore)
                    + " speed_after_unbind=" + f(speedAfterUnbind)
                    + " train_moved_unbound=" + f(trainMovedWhileUnbound)
                    + " moved_after_rebind=" + f(movedAfterRebind)
                    + " nbt_class=" + nbtClass
                    + " nbt_spacing=" + nbtSpacing
                    + " nbt_ok=" + nbtOk
                    + (nbtOk ? "" : " nbt_error=" + nbtError)
                    + " ticks=" + ticks;
        }

        private static String status(BridgeTool.Outcome outcome) {
            return outcome == null ? "-" : outcome.status().name();
        }

        @Override
        void cleanup() {
            if (train != null) {
                TrainBridge.detach(train);
                Create.RAILWAYS.removeTrain(train.id);
                train.invalid = true;
                train = null;
            }
            super.cleanup();
        }
    }

    /**
     * Two bodies, one train: the powered one has to pull the unpowered one through the coupling.
     *
     * <p>The rear vehicle is assembled with two idling trucks and nothing else, so it cannot pull
     * itself at all. Whatever speed it ends up with came through the spring, and that is the point of
     * the scenario: a train of physical bodies is held together by force, not by a shared speed.</p>
     *
     * <p>Three numbers are reported against a target, the way the bridge scenario does it: the gap
     * the train was given, the gap the bodies actually have, and the deformation between them -
     * both the spring's own measure and Create's {@code Train.stress}, which is computed from the
     * carriages' track anchors and should agree with it in sign and size.</p>
     *
     * <p>It also answers a question the first run raised: the trailing body was measured faster than
     * the leading one while the gap between them did not change. Those two statements cannot both be
     * true of a steady state, so the scenario now measures the rate the gap actually changes at, per
     * tick, over its last {@value #SETTLE_TICKS} ticks, and compares it with the instantaneous
     * difference of the two bodies' velocities. Averaged relative speed near zero with a non-zero
     * instantaneous difference is a vibration of the contact; a persistent mean is a real defect that
     * would separate the vehicles for good.</p>
     */
    private static final class CoupleScenario extends Scenario {

        private static final double COMMAND = 0.08;
        private static final double DT = 1.0 / 20.0;
        /** Ticks of settled running at the end of the scenario, used for every convergence number. */
        private static final int SETTLE_TICKS = 200;
        private static final int END = 600;
        private static final int TRAILER_X = 2000;
        private static final int LEADER_X = 2010;
        private static final int RAIL_FROM = 1960;
        private static final int RAIL_TO = 2120;

        private TestRig trailer;
        private TestRig.Rail rail;
        private Train train;
        private TrainBridge.Bridge bridge;

        private double rest;
        private double spacing;
        private double gapStart;
        private double gapEnd;
        private double extension;
        private double peakExtension;
        private double stressCreate;
        private double frontSpeed;
        private double rearSpeed;
        private double frontMoved;
        private double rearMoved;
        private double startFront;
        private double startRear;
        private boolean trailerDriven;
        private double gapDrift;
        private double meanRelative = Double.NaN;
        private double frontBiasMean = Double.NaN;
        private double rearBiasMean = Double.NaN;
        private double frontPoseMean = Double.NaN;
        private double rearPoseMean = Double.NaN;
        private double frontSolverMean = Double.NaN;
        private double rearSolverMean = Double.NaN;
        private double frontPoseSum;
        private double rearPoseSum;
        private double frontSolverSum;
        private double rearSolverSum;

        private double frontMass;
        private double rearMass;
        private double prevFrontX = Double.NaN;
        private double prevRearX = Double.NaN;
        private double gapMin = Double.MAX_VALUE;
        private double gapMax = -Double.MAX_VALUE;
        private double gapAtSettleStart = Double.NaN;
        private double relativeSum;
        private int relativeCount;
        private double relativeWorst;
        private double frontBiasSum;
        private double rearBiasSum;
        private double frontBiasWorst;
        private double rearBiasWorst;
        private int biasCount;
        private double gapAt20 = Double.NaN;
        private double gapAt40 = Double.NaN;
        private double gapAt80 = Double.NaN;
        private int ticksInContact = 1;

        CoupleScenario() {
            super("couple", END, 2552);
        }

        @Override
        void begin(ServerLevel level) {
            this.level = level;
            try {
                rail = TestRig.rail(level, RAIL_FROM, RAIL_TO, 100, z + 1.5);
                trailer = TestRig.assembleOn(rail, TRAILER_X, z, false, false);
                rig = TestRig.assembleOn(rail, LEADER_X, z, true, false);
                buildTrain();
            } catch (RuntimeException | LinkageError e) {
                CreateTrainsSable.LOGGER.error("CTS SELFTEST scenario=couple could not build", e);
                fail("build: " + e);
                finished = true;
            }
        }

        /** A two-carriage train: the powered vehicle leads, the idling one trails behind it. */
        private void buildTrain() {
            double[] frontAxles = {LEADER_X + 1.5, LEADER_X + 3.5};
            double[] rearAxles = {TRAILER_X + 1.5, TRAILER_X + 3.5};
            CarriageBogey leading = bogeyAt(rig, frontAxles[1]);
            CarriageBogey leadingRear = bogeyAt(rig, frontAxles[0]);
            CarriageBogey trailerFront = bogeyAt(trailer, rearAxles[1]);
            CarriageBogey trailerRear = bogeyAt(trailer, rearAxles[0]);

            Carriage front = new Carriage(leading, leadingRear, 2);
            Carriage back = new Carriage(trailerFront, trailerRear, 2);
            double anchorGap = front.getTrailingPoint().getPosition(rig.graph)
                    .distanceTo(back.getLeadingPoint().getPosition(rig.graph));
            spacing = Math.round(anchorGap);

            train = new Train(UUID.randomUUID(), UUID.randomUUID(), rig.graph,
                    new ArrayList<>(List.of(front, back)),
                    new ArrayList<>(List.of((int) spacing)), true, 0);
            train.targetSpeed = COMMAND;
            Create.RAILWAYS.addTrain(train);

            trailerDriven = trailer.trucks.stream().anyMatch(TruckBlockEntity::isDriven);
            bridge = TrainBridge.bridge(train, List.of(rig.body, trailer.body));
            startFront = rig.centre().x();
            startRear = trailer.centre().x();
            gapStart = Math.abs(startFront - startRear);
            rest = bridge.restLengths()[0];
            frontMass = rig.mass();
            rearMass = trailer.mass();
        }

        private static CarriageBogey bogeyAt(TestRig rig, double axleX) {
            return new CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, new CompoundTag(),
                    rig.railPoint(axleX + 1.0), rig.railPoint(axleX - 1.0));
        }

        @Override
        void step() {
            if (rig == null || trailer == null || train == null) {
                return;
            }
            train.manualTick = true;
            train.targetSpeed = COMMAND;
            train.approachTargetSpeed(1.0f);

            List<TrainCouplings.Report> reports = bridge.reports();
            if (!reports.isEmpty()) {
                extension = reports.getFirst().extension();
                peakExtension = Math.max(peakExtension, Math.abs(extension));
            }

            // How fast the gap is really changing, tick by tick, against what the two handles say
            // the speeds are right now. The two must agree; if they do not, one of them is lying.
            double frontX = rig.centre().x();
            double rearX = trailer.centre().x();
            double gapNow = Math.abs(frontX - rearX);
            if (ticks == 20) {
                gapAt20 = gapNow;
            }
            if (ticks == 40) {
                gapAt40 = gapNow;
            }
            if (ticks == 80) {
                gapAt80 = gapNow;
            }
            if (ticks > END - SETTLE_TICKS) {
                if (Double.isNaN(gapAtSettleStart)) {
                    gapAtSettleStart = gapNow;
                }
                gapMin = Math.min(gapMin, gapNow);
                gapMax = Math.max(gapMax, gapNow);
                if (!Double.isNaN(prevFrontX)) {
                    double frontByPosition = (frontX - prevFrontX) / DT;
                    double rearByPosition = (rearX - prevRearX) / DT;
                    double relative = rearByPosition - frontByPosition;
                    relativeSum += relative;
                    relativeCount++;
                    relativeWorst = Math.max(relativeWorst, Math.abs(relative));

                    // How far the handle's own velocity is from what the body actually did in the
                    // tick. A body cannot move at one speed and displace at another; if these two
                    // disagree, the velocity read is the one to distrust.
                    frontPoseSum += rig.poseVelocity().x();
                    rearPoseSum += trailer.poseVelocity().x();
                    frontSolverSum += rig.velocity().x();
                    rearSolverSum += trailer.velocity().x();
                    double frontBias = rig.velocity().x() - frontByPosition;
                    double rearBias = trailer.velocity().x() - rearByPosition;
                    frontBiasSum += frontBias;
                    rearBiasSum += rearBias;
                    frontBiasWorst = Math.max(frontBiasWorst, Math.abs(frontBias));
                    rearBiasWorst = Math.max(rearBiasWorst, Math.abs(rearBias));
                    biasCount++;
                }
            }
            prevFrontX = frontX;
            prevRearX = rearX;
            if (!rig.states().contains("NO_TRACK") && !rig.states().contains("OFF_RAIL")) {
                ticksInContact++;
            }
            if (ticks % 20 == 0) {
                log("front=" + f(rig.centre().x() - startFront)
                        + " rear=" + f(trailer.centre().x() - startRear)
                        + " gap=" + f(gap())
                        + " extension=" + f(extension)
                        + " train_deformation=" + f(trainDeformation())
                        + " v_front=" + f(rig.velocity().x())
                        + " v_rear=" + f(trailer.velocity().x())
                        + " " + states());
            }
        }

        private double gap() {
            return TrainBridge.centre(rig.body).sub(TrainBridge.centre(trailer.body)).length();
        }

        /**
         * The deformation the train itself computes for this pair.
         *
         * <p>{@code Train.stress} is not public, so this applies the same formula Create uses - the
         * spacing the train was built with minus the distance between the two carriages' facing
         * points - to the travelling points, which are public. Positive means the carriages are
         * closer together than they should be, the opposite sign to the spring's extension.</p>
         */
        private double trainDeformation() {
            if (train == null || train.carriages.size() < 2 || rig == null) {
                return 0.0;
            }
            Carriage front = train.carriages.get(0);
            Carriage back = train.carriages.get(1);
            double actual = back.getLeadingPoint().getPosition(rig.graph)
                    .distanceTo(front.getTrailingPoint().getPosition(rig.graph));
            return spacing - actual;
        }

        @Override
        void finish() {
            if (rig == null || trailer == null || train == null) {
                fail("no train");
                return;
            }
            gapEnd = gap();
            gapDrift = gapEnd - gapAtSettleStart;
            meanRelative = relativeCount == 0 ? Double.NaN : relativeSum / relativeCount;
            frontBiasMean = biasCount == 0 ? Double.NaN : frontBiasSum / biasCount;
            rearBiasMean = biasCount == 0 ? Double.NaN : rearBiasSum / biasCount;
            frontPoseMean = biasCount == 0 ? Double.NaN : frontPoseSum / biasCount;
            rearPoseMean = biasCount == 0 ? Double.NaN : rearPoseSum / biasCount;
            frontSolverMean = biasCount == 0 ? Double.NaN : frontSolverSum / biasCount;
            rearSolverMean = biasCount == 0 ? Double.NaN : rearSolverSum / biasCount;
            frontSpeed = rig.velocity().x();
            rearSpeed = trailer.velocity().x();
            frontMoved = rig.centre().x() - startFront;
            rearMoved = trailer.centre().x() - startRear;
            stressCreate = trainDeformation();

            if (trailerDriven) {
                fail("the trailing vehicle has a traction truck; it could pull itself");
                return;
            }
            if (rig.isRemoved() || trailer.isRemoved()) {
                fail("a body was removed");
                return;
            }
            if (train.derailed) {
                fail("Create derailed the train: the coupling deformed past its limit");
                return;
            }
            if (frontMoved < 2.0) {
                fail("the leading body barely moved: " + f(frontMoved));
                return;
            }
            if (rearMoved < 0.75 * frontMoved) {
                fail("the coupling did not pull the unpowered vehicle: front=" + f(frontMoved)
                        + " rear=" + f(rearMoved));
                return;
            }
            if (rearSpeed < 0.75 * Math.abs(frontSpeed)) {
                fail("the unpowered vehicle lags: front=" + f(frontSpeed) + " rear=" + f(rearSpeed));
                return;
            }
            if (!(Math.abs(meanRelative) <= 0.02)) {
                fail("the gap between the bodies keeps changing: " + f(meanRelative) + " m/s over "
                        + SETTLE_TICKS + " ticks");
                return;
            }
            if (!(Math.abs(gapDrift) <= 0.05)) {
                fail("the gap drifted " + f(gapDrift) + " blocks over " + SETTLE_TICKS + " ticks");
                return;
            }
            if (ticksInContact < 0.9 * ticks) {
                fail("the bodies left the rail for " + (ticks - ticksInContact) + " ticks");
                return;
            }
            if (Math.abs(extension) > 1.0) {
                fail("the coupling deformed more than a block: " + f(extension));
                return;
            }
            // Create counts a positive stress as "these two should move apart"; the spring counts a
            // positive extension as "they are too far apart". Same quantity, opposite sign.
            if (Math.abs(stressCreate + extension) > 0.5) {
                fail("the train's deformation and the spring disagree: train=" + f(stressCreate)
                        + " spring=" + f(extension));
                return;
            }
            pass();
        }

        @Override
        String metrics() {
            return "spacing=" + f(spacing)
                    + " rest=" + f(rest)
                    + " rest_minus_spacing=" + f(rest - spacing)
                    + " gap_start=" + f(gapStart)
                    + " gap_end=" + f(gapEnd)
                    + " extension=" + f(extension)
                    + " peak_extension=" + f(peakExtension)
                    + " gap_at_20=" + f(gapAt20) + " gap_at_40=" + f(gapAt40)
                    + " gap_at_80=" + f(gapAt80)
                    + " gap_min=" + f(gapMin) + " gap_max=" + f(gapMax)
                    + " gap_drift=" + f(gapDrift)
                    + " rel_speed_mean=" + f(meanRelative)
                    + " rel_speed_worst=" + f(relativeWorst)
                    + " front_v_bias=" + f(frontBiasMean) + " rear_v_bias=" + f(rearBiasMean)
                    + " v_solver_front=" + f(frontSolverMean) + " v_pose_front=" + f(frontPoseMean)
                    + " v_solver_rear=" + f(rearSolverMean) + " v_pose_rear=" + f(rearPoseMean)
                    + " front_v_bias_worst=" + f(frontBiasWorst)
                    + " rear_v_bias_worst=" + f(rearBiasWorst)
                    + " m_front=" + f(frontMass) + " m_rear=" + f(rearMass)
                    + " train_deformation=" + f(stressCreate)
                    + " v_front=" + f(frontSpeed)
                    + " v_rear=" + f(rearSpeed)
                    + " front_moved=" + f(frontMoved)
                    + " rear_moved=" + f(rearMoved)
                    + " trailer_driven=" + trailerDriven
                    + " derailed=" + (train != null && train.derailed)
                    + " ticks=" + ticks;
        }

        @Override
        void cleanup() {
            if (train != null) {
                TrainBridge.detach(train);
                Create.RAILWAYS.removeTrain(train.id);
                train.invalid = true;
                train = null;
            }
            if (rig != null) {
                rig.remove();
                rig = null;
            }
            if (trailer != null) {
                trailer.remove();
                trailer = null;
            }
            if (rail != null) {
                rail.remove();
                rail = null;
            }
        }
    }


    /**
     * A bridged consist with a driver in it, and one without.
     *
     * <p>Create will not navigate a train whose carriages report no conductor, and a bridged carriage
     * has no contraption to report one from - which is why a bridged train could be driven by hand but
     * never run a schedule. The bridge answers that question from the blocks in the body, so this
     * scenario builds a three-carriage consist where only the middle vehicle has a cab, and checks
     * that exactly that carriage reports a forward driver and that nobody reports a backward one.</p>
     */
    private static final class ConductorsScenario extends Scenario {

        private static final int END = 40;
        private static final int REAR_X = 2000;
        private static final int MIDDLE_X = 2010;
        private static final int FRONT_X = 2020;
        private static final int RAIL_FROM = 1960;
        private static final int RAIL_TO = 2100;

        private TestRig middle;
        private TestRig rear;
        private TestRig.Rail rail;
        private Train train;
        private final boolean[] perCarriageForward = new boolean[3];
        private final boolean[] perCarriageBackward = new boolean[3];
        private boolean forward;
        private boolean backward;
        private boolean checked;
        private String probe = "";

        ConductorsScenario() {
            super("conductors", END, 2552);
        }

        @Override
        void begin(ServerLevel level) {
            this.level = level;
            try {
                rail = TestRig.rail(level, RAIL_FROM, RAIL_TO, 100, z + 1.5);
                rear = TestRig.assembleOn(rail, REAR_X, z, false, false);
                middle = TestRig.assembleOn(rail, MIDDLE_X, z, false, false, Direction.EAST);
                rig = TestRig.assembleOn(rail, FRONT_X, z, true, false);
                List<TestRig> bodies = List.of(rig, middle, rear);
                train = trainOf(bodies, new int[]{FRONT_X, MIDDLE_X, REAR_X});
                train.targetSpeed = 0.0;
                TrainBridge.bridge(train, List.of(rig.body, middle.body, rear.body));
            } catch (RuntimeException | LinkageError e) {
                CreateTrainsSable.LOGGER.error("CTS SELFTEST scenario=conductors could not build", e);
                fail("build: " + e);
                finished = true;
            }
        }

        @Override
        void step() {
            if (train == null) {
                return;
            }
            train.manualTick = true;
            train.targetSpeed = 0.0;
            if (ticks < 5) {
                return;
            }
            if (probe.isEmpty() && !train.carriages.isEmpty()
                    && train.carriages.get(1) instanceof BridgedCarriage middleCarriage) {
                // The consist faces east, which is the direction a carriage travels in.
                probe = dev.goshadows.createtrainssable.bridge.BodyConductors.probe(
                        middleCarriage.body().body(), new Vec3(1, 0, 0));
            }
            forward = train.hasForwardConductor();
            backward = train.hasBackwardConductor();
            for (int i = 0; i < train.carriages.size() && i < perCarriageForward.length; i++) {
                Carriage carriage = train.carriages.get(i);
                perCarriageForward[i] = carriage.presentConductors.getFirst();
                perCarriageBackward[i] = carriage.presentConductors.getSecond();
            }
            checked = true;
            log("forward=" + forward + " backward=" + backward
                    + " per_carriage_forward=" + Arrays.toString(perCarriageForward)
                    + " per_carriage_backward=" + Arrays.toString(perCarriageBackward));
            if (!forward) {
                fail("no carriage reported a driver, although one vehicle carries a lit burner and "
                        + "a controls block facing forward");
                return;
            }
            if (backward) {
                fail("a carriage reported a driver facing backward, there is none");
                return;
            }
            if (perCarriageForward[0] || perCarriageForward[2]) {
                fail("a vehicle without a cab reported a driver: "
                        + Arrays.toString(perCarriageForward));
                return;
            }
            if (!perCarriageForward[1]) {
                fail("the vehicle with the cab did not report its driver");
                return;
            }
            pass();
        }

        @Override
        void finish() {
            if (!checked) {
                fail("the train never reported a conductor either way");
            }
        }

        @Override
        String metrics() {
            return "carriages=" + (train == null ? 0 : train.carriages.size())
                    + " drivers_in_bodies=1"
                    + " forward=" + forward + " backward=" + backward
                    + " carriage_forward=" + Arrays.toString(perCarriageForward)
                    + " carriage_backward=" + Arrays.toString(perCarriageBackward)
                    + " probe=\"" + probe + "\""
                    + " ticks=" + ticks;
        }

        @Override
        void cleanup() {
            if (train != null) {
                TrainBridge.detach(train);
                Create.RAILWAYS.removeTrain(train.id);
                train.invalid = true;
                train = null;
            }
            for (TestRig body : new TestRig[]{rig, middle, rear}) {
                if (body != null) {
                    body.remove();
                }
            }
            rig = null;
            middle = null;
            rear = null;
            if (rail != null) {
                rail.remove();
                rail = null;
            }
        }
    }

    /**
     * Three vehicles walking to a real station on Create's own schedule.
     *
     * <p>The bridge answers "is there a driver" and "where is the body" but says nothing about
     * stations, so this scenario is the proof that Create's route still works when the body is
     * physical: a real station block beside a real track block, a real schedule with one destination,
     * and a consist whose only cab is in the middle vehicle. Nothing in the harness asks the train to
     * stop - Create decides where the station is, how fast to approach it and when to stand still.</p>
     *
     * <p>What is measured is the part a schedule cannot tell you: how far the consist rolled while it
     * braked, where the front of the train came to rest relative to the station, and whether it came to
     * rest at all rather than creeping past.</p>
     */
    private static class StationScenario extends Scenario {

        static final double RAIL_FROM = 1960.5;
        static final int RAIL_TO = 2400;
        static final int RAIL_Y = 100;
        static final double STATION_NODE = 2200.5;
        static final String STATION_NAME = "harness";
        static final int REAR_X = 2000;
        static final int MIDDLE_X = 2010;
        static final int FRONT_X = 2020;

        final boolean wantsSignal;

        TestRig middle;
        TestRig rear;
        TestRig.Rail rail;
        Train train;
        StationBlockEntity station;
        StationBlockEntity stationB;
        SignalBlockEntity signalBlock;
        BlockPos stationTrack;
        BlockPos stationPos;
        BlockPos stationBTrack;
        BlockPos stationBPos;
        BlockPos signalTrack;
        BlockPos signalPos;
        GlobalStation point;
        GlobalStation pointB;
        double arrivedAt = Double.NaN;
        boolean named;
        boolean arrived;
        boolean sawRedSignal;
        boolean waiting;
        double frontStart = Double.NaN;
        double frontX = Double.NaN;
        double topSpeed;
        double brakeFromX = Double.NaN;
        double stopX = Double.NaN;
        double peakBeforeBrake;
        double closestToSignal = Double.MAX_VALUE;
        double distanceToDestination = Double.NaN;
        int movingTicks;
        int stopTick = -1;
        int waitedTicks;
        String scheduleState = "";

        StationScenario(String name, int budget, int z) {
            this(name, budget, z, false);
        }

        StationScenario(String name, int budget, int z, boolean wantsSignal) {
            super(name, budget, z);
            this.wantsSignal = wantsSignal;
        }

        /** The node a signal would stand on; only a scenario that wants one asks for it. */
        double signalNode() {
            return Double.NaN;
        }

        @Override
        void begin(ServerLevel level) {
            this.level = level;
            try {
                rail = TestRig.rail(level, RAIL_FROM, RAIL_TO, RAIL_Y, z + 1.5);
                rear = TestRig.assembleOn(rail, REAR_X, z, false, false);
                middle = TestRig.assembleOn(rail, MIDDLE_X, z, false, false, Direction.EAST);
                rig = TestRig.assembleOn(rail, FRONT_X, z, true, false);
                List<TestRig> bodies = List.of(rig, middle, rear);
                train = trainOf(bodies, new int[]{FRONT_X, MIDDLE_X, REAR_X});
                TrainBridge.bridge(train, List.of(rig.body, middle.body, rear.body));

                stationTrack = TestRig.trackBlock(STATION_NODE, RAIL_Y, z);
                TestRig.placeStraightTrack(level, stationTrack);
                stationPos = stationTrack.above();
                // The station block entity has to tick for the station to exist, and nothing else
                // asks the server to keep this chunk loaded, so the scenario holds its own ticket.
                level.setChunkForced(stationPos.getX() >> 4, stationPos.getZ() >> 4, true);
                station = TestRig.placeStation(level, stationPos, stationTrack, true);

                // The same station facing the other way, one rail edge further east. Create decides
                // which node of the track block a station belongs to from the way it was placed, and
                // the harness must not guess: the run reports which of the two the route can reach.
                stationBTrack = TestRig.trackBlock(STATION_NODE + 40, RAIL_Y, z);
                TestRig.placeStraightTrack(level, stationBTrack);
                stationBPos = stationBTrack.above();
                level.setChunkForced(stationBPos.getX() >> 4, stationBPos.getZ() >> 4, true);
                stationB = TestRig.placeStation(level, stationBPos, stationBTrack, false);

                double signalNode = signalNode();
                if (!Double.isNaN(signalNode)) {
                    signalTrack = TestRig.trackBlock(signalNode, RAIL_Y, z);
                    TestRig.placeStraightTrack(level, signalTrack);
                    signalPos = signalTrack.above();
                    level.setChunkForced(signalPos.getX() >> 4, signalPos.getZ() >> 4, true);
                    signalBlock = TestRig.placeSignal(level, signalPos, signalTrack, true);
                }

                DestinationInstruction instruction = new DestinationInstruction();
                instruction.getData().putString("Text", STATION_NAME);
                ScheduleEntry entry = new ScheduleEntry(instruction, new ArrayList<>());
                Schedule schedule = new Schedule(new ArrayList<>(List.of(entry)), false, 0);
                train.runtime.setSchedule(schedule, false);
                frontStart = front();
            } catch (RuntimeException | LinkageError e) {
                CreateTrainsSable.LOGGER.error("CTS SELFTEST scenario={} could not build", name, e);
                fail("build: " + e);
                finished = true;
            }
        }

        /** The x of the track block a station stands beside, which is where a train stops. */
        double stationX(StationBlockEntity station) {
            return station == null ? Double.NaN : station.edgePoint.getGlobalPosition().getX() + 0.5;
        }

        /** Whether Create can route this train to that station, asked once, without moving it. */
        String reachable(GlobalStation station) {
            if (station == null) {
                return "none";
            }
            return train.navigation.findPathTo(station, 1.0e6) == null ? "no" : "yes";
        }

        /** The station point as Create stored it: both nodes and which side it can be entered from. */
        String describe(GlobalStation station, StationBlockEntity blockEntity) {
            if (station == null) {
                return "none";
            }
            var first = rail.graph.locateNode(station.edgeLocation.getFirst());
            var second = rail.graph.locateNode(station.edgeLocation.getSecond());
            return station.name + "@" + f(stationX(blockEntity))
                    + " pos=" + f(station.position)
                    + " from_first=" + (first != null && station.canApproachFrom(first))
                    + " from_second=" + (second != null && station.canApproachFrom(second));
        }

        /** The x of the station the train is meant to stop at. */
        double stopTargetX() {
            return Double.isNaN(arrivedAt) ? STATION_NODE : arrivedAt;
        }

        /** Where the front of the train is on the rail, in world x. */
        double front() {
            if (train == null || train.carriages.isEmpty()) {
                return Double.NaN;
            }
            return train.carriages.get(0).getLeadingPoint().getPosition(rail.graph).x;
        }

        @Override
        void step() {
            if (train == null) {
                return;
            }
            frontX = front();
            double speed = Math.abs(train.speed);
            topSpeed = Math.max(topSpeed, speed);
            distanceToDestination = train.navigation.distanceToDestination;
            scheduleState = train.runtime.state + "/" + train.runtime.currentEntry
                    + (train.runtime.paused ? "(paused)" : "");
            waiting = train.navigation.waitingForSignal != null;
            sawRedSignal |= waiting;
            waitedTicks = waiting ? waitedTicks + 1 : 0;

            if (point == null && station != null && station.getStation() != null) {
                point = station.getStation();
                point.name = STATION_NAME;
                named = true;
                log("station_ready id=" + point.id + " x=" + f(stationX(station)));
            }
            if (pointB == null && stationB != null && stationB.getStation() != null) {
                pointB = stationB.getStation();
                pointB.name = STATION_NAME;
                log("station_b_ready id=" + pointB.id + " x=" + f(stationX(stationB)));
            }

            if (speed > 0.002) {
                movingTicks++;
            } else if (movingTicks > 20 && stopTick < 0) {
                stopTick = ticks;
                stopX = frontX;
            }

            // Braking starts on the first tick the speed falls after having risen above half of the
            // best speed seen so far; that is the tick a station approach begins to bite, not the tick
            // the train first moves.
            if (Double.isNaN(brakeFromX) && topSpeed > 0.02 && speed < peakBeforeBrake * 0.999
                    && peakBeforeBrake > topSpeed * 0.5) {
                brakeFromX = frontX;
            }
            peakBeforeBrake = Math.max(peakBeforeBrake == 0 ? speed : peakBeforeBrake * 0.999, speed);

            if (waiting) {
                double signalNode = signalNode();
                if (!Double.isNaN(signalNode)) {
                    closestToSignal = Math.min(closestToSignal, signalNode - frontX);
                }
            }

            if (train.getCurrentStation() != null && !arrived) {
                arrived = true;
                arrivedAt = train.getCurrentStation() == point ? stationX(station)
                        : train.getCurrentStation() == pointB ? stationX(stationB) : Double.NaN;
                log("arrived front=" + f(frontX) + " station=" + f(STATION_NODE)
                        + " speed=" + f(train.speed) + " tick=" + ticks);
            }

            if (ticks % 20 == 0) {
                log("front=" + f(frontX) + " speed=" + f(train.speed)
                        + " top=" + f(topSpeed) + " destination=" + f(distanceToDestination)
                        + " signal_wait=" + waiting + " state=" + scheduleState
                        + " station=" + (point != null));
            }
            if (ticks == 40) {
                log("diag conductor=" + train.hasForwardConductor()
                        + " status_nav=" + train.status.navigation
                        + " status_conductor=" + train.status.conductor
                        + " paused=" + train.runtime.paused
                        + " path_a=" + reachable(point)
                        + " path_b=" + reachable(pointB)
                        + " a=" + describe(point, station)
                        + " b=" + describe(pointB, stationB));
            }
            if (ticks == 4 && point == null) {
                log("station_block=" + (station != null)
                        + " target=" + (station == null ? "?" : station.edgePoint.getGlobalPosition())
                        + " valid_track=" + (station != null && station.edgePoint.hasValidTrack()));
            }
        }

        @Override
        void finish() {
            if (train == null) {
                return;
            }
            double restDistance = stopTargetX() - stopX;
            if (wantsSignal) {
                if (!sawRedSignal) {
                    fail("the train never saw the red signal; it should have stopped for it");
                    return;
                }
                if (stopTick < 0 || topSpeed < 0.02) {
                    fail("the train never came to rest in front of the signal");
                    return;
                }
                if (closestToSignal < 0) {
                    fail("the train passed the red signal by " + f(-closestToSignal) + " blocks");
                    return;
                }
                if (arrived) {
                    fail("the train reached the station through a red signal");
                    return;
                }
                pass();
                return;
            }
            if (!arrived) {
                fail("the schedule never brought the train to the station: state=" + scheduleState
                        + " destination=" + f(distanceToDestination) + " front=" + f(frontX));
                return;
            }
            if (stopTick < 0) {
                fail("the train arrived but never came to rest");
                return;
            }
            if (restDistance < -1.5) {
                fail("the train overshot the station by " + f(-restDistance) + " blocks");
                return;
            }
            pass();
        }

        @Override
        String metrics() {
            double restDistance = stopTargetX() - stopX;
            double brakeDistance = Double.isNaN(brakeFromX) || Double.isNaN(stopX)
                    ? Double.NaN : stopX - brakeFromX;
            return "carriages=" + (train == null ? 0 : train.carriages.size())
                    + " front_start=" + f(frontStart)
                    + " station_node=" + f(STATION_NODE)
                    + " station_at=" + f(stopTargetX())
                    + " top_speed=" + f(topSpeed)
                    + " moving_ticks=" + movingTicks
                    + " brake_from=" + f(brakeFromX)
                    + " stop_at=" + f(stopX)
                    + " stop_error=" + f(restDistance)
                    + " brake_distance=" + f(brakeDistance)
                    + " signal_node=" + f(signalNode())
                    + " closest_to_signal=" + (closestToSignal == Double.MAX_VALUE
                            ? "none" : f(closestToSignal))
                    + " waited=" + sawRedSignal
                    + " wait_ticks=" + waitedTicks
                    + " arrived=" + arrived
                    + " station=" + (point != null)
                    + " state=" + scheduleState
                    + " ticks=" + ticks;
        }

        @Override
        void cleanup() {
            if (train != null) {
                TrainBridge.detach(train);
                train.navigation.cancelNavigation();
                Create.RAILWAYS.removeTrain(train.id);
                train.invalid = true;
                train = null;
            }
            for (TestRig body : new TestRig[]{rig, middle, rear}) {
                if (body != null) {
                    body.remove();
                }
            }
            rig = null;
            middle = null;
            rear = null;
            if (rail != null) {
                for (GlobalStation station : new GlobalStation[]{point, pointB}) {
                    if (station != null) {
                        rail.graph.removePoint(
                                com.simibubi.create.content.trains.graph.EdgePointType.STATION,
                                station.id);
                    }
                }
            }
            point = null;
            pointB = null;
            for (BlockPos pos : new BlockPos[]{stationPos, stationBPos, signalPos}) {
                if (pos != null) {
                    level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR
                            .defaultBlockState());
                    level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
                }
            }
            for (BlockPos pos : new BlockPos[]{stationTrack, stationBTrack, signalTrack}) {
                if (pos != null) {
                    TestRig.clearBlock(level, pos);
                }
            }
            station = null;
            stationB = null;
            signalBlock = null;
            stationPos = null;
            stationBPos = null;
            stationBTrack = null;
            signalPos = null;
            stationTrack = null;
            signalTrack = null;
            if (rail != null) {
                rail.remove();
                rail = null;
            }
        }
    }

    /**
     * The same run, with a red signal between the train and its station.
     *
     * <p>Signals are the other half of Create's route, and they are the half a physical body can break
     * without anyone noticing: the signal is obeyed by the navigation code, which reads the distance
     * from the leading travelling point, and a bridged carriage whose point is driven by a body that
     * lags or overshoots would see the signal too late. The scenario forces a real signal block red,
     * lets Create drive the same schedule, and asks how close the train came before it stood still.</p>
     */
    private static final class SignalScenario extends StationScenario {

        static final double SIGNAL_NODE = 2100.5;

        SignalScenario() {
            super("signal", 1400, 2572, true);
        }

        @Override
        double signalNode() {
            return SIGNAL_NODE;
        }
    }

    /**
     * Three vehicles, two springs, one traction truck: the consist a claim about trains has to cover.
     *
     * <p>The pair scenario proves that one coupling holds a wagon. That is not the same claim as
     * "a train works": in a chain the middle body is pulled from the front and pulls the rear one in
     * turn, so an error that a single spring absorbs - a gap that creeps, a damper that fights itself,
     * a coupling indexed against the wrong neighbour - shows up here and nowhere else. Both gaps are
     * measured separately, and both are compared with what the bridge publishes into Create's own
     * deformation array, so the number Create would show is checked against the gap it describes.</p>
     */
    private static final class ChainScenario extends Scenario {

        private static final double COMMAND = 0.08;
        /** Ticks of settled running at the end of the scenario, used for every convergence number. */
        private static final int SETTLE_TICKS = 200;
        private static final int END = 600;
        private static final int REAR_X = 2000;
        private static final int MIDDLE_X = 2010;
        private static final int FRONT_X = 2020;
        private static final int RAIL_FROM = 1960;
        private static final int RAIL_TO = 2200;

        private TestRig middle;
        private TestRig rear;
        private TestRig.Rail rail;
        private Train train;
        private TrainBridge.Bridge bridge;

        private final double[] spacing = new double[2];
        private final double[] rest = new double[2];
        private final double[] gapStart = new double[2];
        private final double[] gapEnd = new double[2];
        private final double[] gapMin = {Double.MAX_VALUE, Double.MAX_VALUE};
        private final double[] gapMax = {-Double.MAX_VALUE, -Double.MAX_VALUE};
        private final double[] gapAtSettleStart = {Double.NaN, Double.NaN};
        private final double[] extension = new double[2];
        private final double[] published = new double[2];
        private final double[] moved = new double[3];
        private final double[] startX = new double[3];
        private double drift01;
        private double drift12;
        private int ticksInContact;
        private boolean stressPublished;
        private boolean stressAvailable;

        ChainScenario() {
            super("chain", END, 2596);
        }

        @Override
        void begin(ServerLevel level) {
            this.level = level;
            try {
                rail = TestRig.rail(level, RAIL_FROM, RAIL_TO, 100, z + 1.5);
                rear = TestRig.assembleOn(rail, REAR_X, z, false, false);
                middle = TestRig.assembleOn(rail, MIDDLE_X, z, false, false);
                rig = TestRig.assembleOn(rail, FRONT_X, z, true, false);
                buildTrain();
            } catch (RuntimeException | LinkageError e) {
                CreateTrainsSable.LOGGER.error("CTS SELFTEST scenario=chain could not build", e);
                fail("build: " + e);
                finished = true;
            }
        }

        /** One powered vehicle in front, two idling ones behind it, two couplings between them. */
        private void buildTrain() {
            List<TestRig> rigs = List.of(rig, middle, rear);
            int[] decks = {FRONT_X, MIDDLE_X, REAR_X};
            train = trainOf(rigs, decks);
            train.targetSpeed = COMMAND;

            bridge = TrainBridge.bridge(train, List.of(rig.body, middle.body, rear.body));
            rest[0] = bridge.restLengths()[0];
            rest[1] = bridge.restLengths()[1];
            spacing[0] = train.carriageSpacing.get(0);
            spacing[1] = train.carriageSpacing.get(1);

            startX[0] = rig.centre().x();
            startX[1] = middle.centre().x();
            startX[2] = rear.centre().x();
            gapStart[0] = Math.abs(startX[0] - startX[1]);
            gapStart[1] = Math.abs(startX[1] - startX[2]);
        }

        @Override
        void step() {
            if (rig == null || middle == null || rear == null || train == null) {
                return;
            }
            train.manualTick = true;
            train.targetSpeed = COMMAND;
            train.approachTargetSpeed(1.0f);

            List<TrainCouplings.Report> reports = bridge.reports();
            if (reports.size() == 2) {
                extension[0] = reports.get(0).extension();
                extension[1] = reports.get(1).extension();
            }
            stressAvailable = TrainStress.available();
            double[] current = TrainStress.read(train);
            if (current != null && current.length == 2) {
                published[0] = current[0];
                published[1] = current[1];
                stressPublished = true;
            }

            double frontX = rig.centre().x();
            double middleX = middle.centre().x();
            double rearX = rear.centre().x();
            double gap01 = Math.abs(frontX - middleX);
            double gap12 = Math.abs(middleX - rearX);
            if (ticks > END - SETTLE_TICKS) {
                if (Double.isNaN(gapAtSettleStart[0])) {
                    gapAtSettleStart[0] = gap01;
                    gapAtSettleStart[1] = gap12;
                }
                gapMin[0] = Math.min(gapMin[0], gap01);
                gapMax[0] = Math.max(gapMax[0], gap01);
                gapMin[1] = Math.min(gapMin[1], gap12);
                gapMax[1] = Math.max(gapMax[1], gap12);
            }
            if (!rig.states().contains("NO_TRACK") && !rig.states().contains("OFF_RAIL")) {
                ticksInContact++;
            }
            if (ticks % 20 == 0) {
                log("front=" + f(frontX - startX[0])
                        + " middle=" + f(middleX - startX[1])
                        + " rear=" + f(rearX - startX[2])
                        + " gap01=" + f(gap01) + " gap12=" + f(gap12)
                        + " ext01=" + f(extension[0]) + " ext12=" + f(extension[1])
                        + " stress01=" + f(published[0]) + " stress12=" + f(published[1]));
            }
        }

        @Override
        void finish() {
            if (rig == null || middle == null || rear == null || train == null) {
                fail("no train");
                return;
            }
            gapEnd[0] = Math.abs(rig.centre().x() - middle.centre().x());
            gapEnd[1] = Math.abs(middle.centre().x() - rear.centre().x());
            drift01 = gapEnd[0] - (Double.isNaN(gapAtSettleStart[0]) ? gapEnd[0] : gapAtSettleStart[0]);
            drift12 = gapEnd[1] - (Double.isNaN(gapAtSettleStart[1]) ? gapEnd[1] : gapAtSettleStart[1]);
            moved[0] = rig.centre().x() - startX[0];
            moved[1] = middle.centre().x() - startX[1];
            moved[2] = rear.centre().x() - startX[2];

            if (rig.isRemoved() || middle.isRemoved() || rear.isRemoved()) {
                fail("a body was removed");
                return;
            }
            if (!stressAvailable) {
                fail("Create's deformation array could not be reached, so nothing was published");
                return;
            }
            if (!stressPublished) {
                fail("the bridge never published a deformation into Create's array");
                return;
            }
            if (train.derailed) {
                fail("Create derailed the train: the couplings deformed past the limit");
                return;
            }
            if (moved[0] < 2.0) {
                fail("the leading body barely moved: " + f(moved[0]));
                return;
            }
            if (moved[1] < 0.75 * moved[0]) {
                fail("the middle body lags: front=" + f(moved[0]) + " middle=" + f(moved[1]));
                return;
            }
            if (moved[2] < 0.75 * moved[0]) {
                fail("the rear body lags: front=" + f(moved[0]) + " rear=" + f(moved[2]));
                return;
            }
            if (Math.abs(drift01) > 0.05) {
                fail("the first gap drifted " + f(drift01) + " blocks over " + SETTLE_TICKS + " ticks");
                return;
            }
            if (Math.abs(drift12) > 0.05) {
                fail("the second gap drifted " + f(drift12) + " blocks over " + SETTLE_TICKS + " ticks");
                return;
            }
            if (Math.abs(extension[0]) > 1.0 || Math.abs(extension[1]) > 1.0) {
                fail("a coupling deformed more than a block: " + f(extension[0]) + ", "
                        + f(extension[1]));
                return;
            }
            // What Create publishes must be the spring's own number with Create's sign, or the
            // number a player reads in the train's state describes a gap that does not exist.
            if (Math.abs(published[0] + extension[0]) > 0.05) {
                fail("Create's stress and the spring disagree on the first gap: stress="
                        + f(published[0]) + " extension=" + f(extension[0]));
                return;
            }
            if (Math.abs(published[1] + extension[1]) > 0.05) {
                fail("Create's stress and the spring disagree on the second gap: stress="
                        + f(published[1]) + " extension=" + f(extension[1]));
                return;
            }
            if (ticksInContact < 0.9 * ticks) {
                fail("the bodies left the rail for " + (ticks - ticksInContact) + " ticks");
                return;
            }
            pass();
        }

        @Override
        String metrics() {
            return "carriages=3 bodies=3"
                    + " spacing_01=" + f(spacing[0]) + " spacing_12=" + f(spacing[1])
                    + " rest_01=" + f(rest[0]) + " rest_12=" + f(rest[1])
                    + " gap_01_start=" + f(gapStart[0]) + " gap_01_end=" + f(gapEnd[0])
                    + " gap_12_start=" + f(gapStart[1]) + " gap_12_end=" + f(gapEnd[1])
                    + " gap_01_min=" + f(gapMin[0]) + " gap_01_max=" + f(gapMax[0])
                    + " gap_12_min=" + f(gapMin[1]) + " gap_12_max=" + f(gapMax[1])
                    + " drift_01=" + f(drift01) + " drift_12=" + f(drift12)
                    + " ext_01=" + f(extension[0]) + " ext_12=" + f(extension[1])
                    + " stress_01=" + f(published[0]) + " stress_12=" + f(published[1])
                    + " stress_published=" + stressPublished
                    + " moved_front=" + f(moved[0]) + " moved_middle=" + f(moved[1])
                    + " moved_rear=" + f(moved[2])
                    + " derailed=" + (train != null && train.derailed)
                    + " ticks=" + ticks;
        }

        @Override
        void cleanup() {
            if (train != null) {
                TrainBridge.detach(train);
                Create.RAILWAYS.removeTrain(train.id);
                train.invalid = true;
                train = null;
            }
            for (TestRig body : new TestRig[]{rig, middle, rear}) {
                if (body != null) {
                    body.remove();
                }
            }
            rig = null;
            middle = null;
            rear = null;
            if (rail != null) {
                rail.remove();
                rail = null;
            }
        }
    }


    /**
     * A bridged train left running for a minute of game time, watched for cost.
     *
     * <p>Suppressing a carriage's contraption entity is the one architectural choice in the bridge
     * that could cost something over a long session, so this scenario measures instead of asserting
     * from reading the code: how many entities the level holds before and after, whether any
     * {@link CarriageContraptionEntity} exists at all, how much heap the run grew by, whether the
     * carriage still follows the body, and for how many ticks every axle held its rail.</p>
     *
     * <p>A minute of game time is not hours, and the JVM's heap number is indicative rather than
     * exact; both are reported as measurements and only the structural claims are asserted. The
     * longer question - a session of several hours with chunk unloads - is recorded in the README as
     * open rather than answered here.</p>
     */
    private static final class SoakScenario extends Scenario {

        /** Slow enough that a minute of running stays inside the rig's rail. */
        private static final double COMMAND = 0.02;
        private static final int END = 1200;
        private static final int REAR_X = 2000;
        private static final int MIDDLE_X = 2010;
        private static final int FRONT_X = 2020;
        private static final int RAIL_FROM = 1960;
        private static final int RAIL_TO = 2300;

        private TestRig middle;
        private TestRig rear;
        private TestRig.Rail rail;
        private Train train;
        private List<TestRig> bodies = new ArrayList<>(3);
        private final List<BridgedCarriage> bridged = new ArrayList<>(3);
        private final double[] startX = new double[3];
        private final double[] travelled = new double[3];
        private final double[] carriageTravelled = new double[3];
        private Entity marker;
        private boolean markerAlive;
        private int entitiesStart = -1;
        private int entitiesEnd;
        private int contraptions;
        private long heapStart;
        private long heapEnd;
        private double drift;
        private double lateralMax;
        private int ticksHolding;

        SoakScenario() {
            super("soak", END, 2640);
        }

        @Override
        void begin(ServerLevel level) {
            this.level = level;
            try {
                rail = TestRig.rail(level, RAIL_FROM, RAIL_TO, 100, z + 1.5);
                rear = TestRig.assembleOn(rail, REAR_X, z, false, false);
                middle = TestRig.assembleOn(rail, MIDDLE_X, z, false, false);
                rig = TestRig.assembleOn(rail, FRONT_X, z, true, false);
                bodies = List.of(rig, middle, rear);
                train = trainOf(bodies, new int[]{FRONT_X, MIDDLE_X, REAR_X});
                train.targetSpeed = COMMAND;
                TrainBridge.Bridge bridge =
                        TrainBridge.bridge(train, List.of(rig.body, middle.body, rear.body));
                bridged.addAll(bridge.carriages());
                for (int i = 0; i < bodies.size(); i++) {
                    startX[i] = bodies.get(i).centre().x();
                }
                spawnMarker();
            } catch (RuntimeException | LinkageError e) {
                fail("train: " + e);
                finished = true;
            }
        }

        @Override
        void step() {
            if (rig == null || train == null || bridged.size() != bodies.size()) {
                return;
            }
            if (entitiesStart < 0) {
                // The baseline is taken on the first tick that can see the reference entity: an
                // entity added during this scenario's begin() is still pending, and a count of zero
                // at both ends of the run is a check that cannot fail.
                int live = countEntities();
                if (live >= 1) {
                    entitiesStart = live;
                    heapStart = usedHeap();
                }
            }
            train.manualTick = true;
            train.targetSpeed = COMMAND;
            train.approachTargetSpeed(1.0f);
            for (TestRig body : bodies) {
                lateralMax = Math.max(lateralMax, body.lateralError());
                if (body.states().contains("GUIDING")) {
                    ticksHolding++;
                }
            }
            if (ticks % 200 == 0) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < bodies.size(); i++) {
                    if (i > 0) {
                        line.append(' ');
                    }
                    line.append('x').append(i).append('=')
                            .append(f(bodies.get(i).centre().x() - startX[i]));
                    line.append(" v").append(i).append('=')
                            .append(f(bridged.get(i).measuredSpeed()));
                }
                log(line + " entities=" + countEntities() + " heap_mb=" + (usedHeap() >> 20));
            }
        }

        @Override
        void finish() {
            if (rig == null || train == null || bridged.size() != bodies.size()) {
                fail("no train");
                return;
            }
            entitiesEnd = countEntities();
            contraptions = countContraptions();
            markerAlive = marker != null && marker.isAlive();
            heapEnd = usedHeap();
            drift = 0.0;
            for (int i = 0; i < bodies.size(); i++) {
                travelled[i] = bodies.get(i).centre().x() - startX[i];
                carriageTravelled[i] = bridged.get(i).travelToDate();
                drift = Math.max(drift, Math.abs(travelled[i] - carriageTravelled[i]));
            }

            for (TestRig body : bodies) {
                if (body.isRemoved()) {
                    fail("a body was removed during the run");
                    return;
                }
            }
            if (!TrainBridge.isBridged(train)) {
                fail("the bridge did not survive the run");
                return;
            }
            for (BridgedCarriage carriage : bridged) {
                if (!carriage.isActive()) {
                    fail("a carriage was detached during the run");
                    return;
                }
            }
            if (contraptions != 0) {
                fail(contraptions + " carriage contraption entities exist for a bridged train");
                return;
            }
            if (entitiesStart < 1) {
                fail("the level never held a reference entity, so the leak check measured nothing");
                return;
            }
            if (!markerAlive) {
                fail("the reference entity did not survive the run, so the count cannot be trusted");
                return;
            }
            if (entitiesEnd > entitiesStart) {
                fail("the level gained entities while nothing was spawned: " + entitiesStart
                        + " -> " + entitiesEnd);
                return;
            }
            if (drift > 0.5) {
                fail("a carriage drifted from its body over the run: " + f(drift));
                return;
            }
            if (ticksHolding < 0.95 * ticks * bodies.size()) {
                fail("the axles held the rail for only " + ticksHolding + " of "
                        + ticks * bodies.size() + " axle-ticks");
                return;
            }
            pass();
        }

        /**
         * Puts one harmless entity in the level before the count starts.
         *
         * <p>The entity count is what makes "nothing grew over the run" a measurement instead of a
         * constant: in a session that never spawned an entity the level reports zero before and zero
         * after, and a leak cannot be seen in a count that is empty at both ends. A dropped stick is
         * a cheap reference entity that no part of the mod touches.</p>
         */
        private void spawnMarker() {
            if (rig == null) {
                return;
            }
            marker = new ItemEntity(level, startX[0], rig.railHeight() + 0.5, rig.centre().z(),
                    new ItemStack(Items.STICK));
            marker.setNoGravity(true);
            level.addFreshEntity(marker);
        }

        private int countEntities() {
            int count = 0;
            for (var ignored : level.getEntities().getAll()) {
                count++;
            }
            return count;
        }

        private int countContraptions() {
            int count = 0;
            for (var entity : level.getEntities().getAll()) {
                if (entity instanceof CarriageContraptionEntity) {
                    count++;
                }
            }
            return count;
        }

        private static long usedHeap() {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        }

        @Override
        String metrics() {
            return "ticks=" + ticks
                    + " carriages=3 bodies=3"
                    + " travelled_front=" + f(travelled[0])
                    + " travelled_middle=" + f(travelled[1])
                    + " travelled_rear=" + f(travelled[2])
                    + " carriage_front=" + f(carriageTravelled[0])
                    + " carriage_middle=" + f(carriageTravelled[1])
                    + " carriage_rear=" + f(carriageTravelled[2])
                    + " drift_max=" + f(drift)
                    + " entities_start=" + entitiesStart
                    + " entities_end=" + entitiesEnd
                    + " reference_entity_alive=" + markerAlive
                    + " contraption_entities=" + contraptions
                    + " heap_start_mb=" + (heapStart >> 20)
                    + " heap_end_mb=" + (heapEnd >> 20)
                    + " lateral_max=" + f(lateralMax)
                    + " ticks_holding=" + ticksHolding
                    + " axle_ticks=" + (ticks * bodies.size())
                    + " bridged=" + (train != null && TrainBridge.isBridged(train));
        }

        @Override
        void cleanup() {
            if (train != null) {
                TrainBridge.detach(train);
                Create.RAILWAYS.removeTrain(train.id);
                train.invalid = true;
                train = null;
            }
            bridged.clear();
            for (TestRig body : new TestRig[]{rig, middle, rear}) {
                if (body != null) {
                    body.remove();
                }
            }
            rig = null;
            middle = null;
            rear = null;
            if (rail != null) {
                rail.remove();
                rail = null;
            }
        }
    }

}
