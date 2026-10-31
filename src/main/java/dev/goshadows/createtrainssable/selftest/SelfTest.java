package dev.goshadows.createtrainssable.selftest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackGraph;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.bridge.BridgedCarriage;
import dev.goshadows.createtrainssable.bridge.CarriageBody;
import dev.goshadows.createtrainssable.bridge.TrainBridge;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.goshadows.createtrainssable.rail.TruckBlockEntity;
import dev.goshadows.createtrainssable.rail.Vec;
import dev.goshadows.createtrainssable.rail.WheelRail;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
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
 * off the rail keeps its momentum and falls, and a Create train follows the body it is bridged to.</p>
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
                new BridgeScenario());

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
        private final int z;

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
        private BridgedCarriage bridged;
        private double startCentre;
        private double travelledBody;
        private double speed;
        private double peak;
        private double lateralMax;
        private double anchorGap = Double.NaN;
        private int riseTick = -1;
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
            TrackGraph graph = rig.graph;
            CarriageBogey leading = new CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, new CompoundTag(),
                    rig.railPoint(LEADING_AXLE + 1.0), rig.railPoint(LEADING_AXLE - 1.0));
            CarriageBogey trailing = new CarriageBogey(AllBlocks.SMALL_BOGEY.get(), false, new CompoundTag(),
                    rig.railPoint(TRAILING_AXLE - 1.0), rig.railPoint(TRAILING_AXLE + 1.0));
            Carriage carriage = new Carriage(leading, trailing, 2);
            train = new Train(UUID.randomUUID(), UUID.randomUUID(), graph,
                    new ArrayList<>(List.of(carriage)), new ArrayList<>(), true, 0);
            train.targetSpeed = COMMAND;
            Create.RAILWAYS.addTrain(train);
            bridged = TrainBridge.bridge(train, rig.body);
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
            if (Create.RAILWAYS.trains.get(train.id) != train) {
                fail("the train is no longer in Create's railway manager");
                return;
            }
            if (TrainBridge.bridged(train) != bridged || train.carriages.size() != 1) {
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
            bridged = null;
            super.cleanup();
        }
    }

    /** Kept so a future scenario can list what it built without a cast. */
    static List<String> truckNames(ServerSubLevel body) {
        List<String> names = new ArrayList<>();
        for (var actor : body.getPlot().getBlockEntityActors()) {
            if (actor instanceof TruckBlockEntity truck) {
                names.add(truck.isDriven() ? "traction" : "rolling");
            }
        }
        return names;
    }
}
