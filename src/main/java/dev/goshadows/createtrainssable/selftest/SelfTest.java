package dev.goshadows.createtrainssable.selftest;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.rail.TruckBlockEntity;
import dev.goshadows.createtrainssable.rail.Vec;
import dev.goshadows.createtrainssable.rail.WheelRail;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scripted physics session: builds a vehicle, lets the engine drive it, and reports numbers.
 *
 * <p>The harness is off unless the JVM is started with {@code -Dcts.selftest=true}, which the
 * {@code runSelfTest} Gradle task does. It runs on a dedicated server with no player, so what it
 * measures is the physics itself: where the body went, how fast it moved, which axle still held the
 * rail. Every scenario prints one line with its measurements and a final line with the totals, and
 * the continuous integration job fails unless the totals say that nothing failed.</p>
 *
 * <p>The scenarios are the contract of the rail model, in order: the vehicle can be assembled and
 * finds its rail, it stays on the rail, the driven axle reaches the speed its shaft asks for, and a
 * vehicle pushed off the rail keeps its momentum and falls.</p>
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
                new DerailScenario());

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
