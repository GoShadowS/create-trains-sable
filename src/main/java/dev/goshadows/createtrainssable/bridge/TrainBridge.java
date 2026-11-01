package dev.goshadows.createtrainssable.bridge;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.goshadows.createtrainssable.config.TruckSettings;
import dev.goshadows.createtrainssable.rail.Vec;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Binds a Create train to the Sable bodies its carriages ride.
 *
 * <p>This is the seam of the whole mod. Create owns the route: the track graph, the travelling
 * points, the signals, the stations, the schedule and the user interface. Sable owns the bodies: what
 * the wheels feel, how much force the motor can put down, whether a vehicle leaves the rail. The two
 * meet at one question, asked once per tick and per carriage: <em>how far did the body actually
 * go?</em></p>
 *
 * <p>With more than one carriage there is a second question, and it is a spring: how far are two
 * neighbouring bodies from the gap the train asked for, and how fast is that gap changing. The answer
 * is a force applied to both bodies, and it is what lets a single traction truck pull a train of
 * unpowered wagons - see {@link TrainCouplings}.</p>
 *
 * <p>Nothing here writes a pose. Bridging does not teleport the train onto the bodies and the bodies
 * are never placed on the track: each carriage travels the distance its own body covered, and the
 * coupling reacts to what is actually there.</p>
 *
 * <p>The registry is weak: a train that is deleted is not kept alive by this map. Bridging refuses a
 * train whose carriage count does not match the bodies offered, because guessing which body is which
 * carriage would put the train on the wrong track.</p>
 */
public final class TrainBridge {

    /** One bridged train: its carriages, their bodies, and the springs between them. */
    public static final class Bridge {

        private final Train train;
        private final List<BridgedCarriage> carriages;
        private final List<ServerSubLevel> bodies;
        private final double[] restLengths;
        private final TrainCouplings.Report[] reports;

        private Bridge(Train train, List<BridgedCarriage> carriages, List<ServerSubLevel> bodies,
                       double[] restLengths) {
            this.train = train;
            this.carriages = carriages;
            this.bodies = bodies;
            this.restLengths = restLengths;
            this.reports = new TrainCouplings.Report[restLengths.length];
        }

        public Train train() {
            return train;
        }

        public List<BridgedCarriage> carriages() {
            return Collections.unmodifiableList(carriages);
        }

        public List<ServerSubLevel> bodies() {
            return Collections.unmodifiableList(bodies);
        }

        /** Gaps the train was coupled at, one per pair of neighbouring carriages. */
        public double[] restLengths() {
            return restLengths.clone();
        }

        /** The springs' state from the last tick, one per gap, leading gap first. */
        public List<TrainCouplings.Report> reports() {
            List<TrainCouplings.Report> copy = new ArrayList<>(reports.length);
            for (TrainCouplings.Report report : reports) {
                if (report != null) {
                    copy.add(report);
                }
            }
            return copy;
        }
    }

    private static final Map<Train, Bridge> BRIDGED = new WeakHashMap<>();

    private TrainBridge() {
    }

    /**
     * Bridges a single-carriage train to one body.
     *
     * @param train the train to bridge; it keeps living in Create's railway manager
     * @param body  the assembled body whose trucks hold it on the rail
     */
    public static Bridge bridge(Train train, ServerSubLevel body) {
        return bridge(train, List.of(body));
    }

    /**
     * Bridges every carriage of a train to a body, in order.
     *
     * @param train  the train; it must have exactly as many carriages as there are bodies
     * @param bodies one body per carriage, leading carriage first
     * @throws IllegalArgumentException when the counts do not match, a body is missing, or a body is
     *                                  already carrying another train
     */
    public static Bridge bridge(Train train, List<ServerSubLevel> bodies) {
        if (train == null) {
            throw new IllegalArgumentException("a train is needed to bridge");
        }
        if (bodies == null || bodies.isEmpty()) {
            throw new IllegalArgumentException("a body is needed to bridge");
        }
        if (bodies.size() != train.carriages.size()) {
            throw new IllegalArgumentException("one body per carriage: " + train.carriages.size()
                    + " carriages, " + bodies.size() + " bodies");
        }
        Bridge existing = BRIDGED.get(train);
        if (existing != null) {
            // Re-bridging replaces the bodies but keeps the carriage objects, so nothing about the
            // train's identity or its serialised form changes.
            detach(train);
        }
        for (ServerSubLevel body : bodies) {
            if (body == null || body.isRemoved()) {
                throw new IllegalArgumentException("a body was removed before it could be bridged");
            }
            Train other = trainOf(body);
            if (other != null && other != train) {
                throw new IllegalArgumentException("body " + body.getUniqueId()
                        + " already carries train " + other.id);
            }
        }

        List<BridgedCarriage> carriages = new ArrayList<>(bodies.size());
        for (int i = 0; i < bodies.size(); i++) {
            Carriage original = train.carriages.get(i);
            BridgedCarriage bridged;
            if (original instanceof BridgedCarriage existingCarriage) {
                existingCarriage.activate(new CarriageBody(bodies.get(i)));
                bridged = existingCarriage;
            } else {
                bridged = new BridgedCarriage(original, new CarriageBody(bodies.get(i)));
                bridged.setTrain(train);
                train.carriages.set(i, bridged);
            }
            carriages.add(bridged);
        }

        double[] rests = new double[bodies.size() - 1];
        for (int i = 0; i < rests.length; i++) {
            rests[i] = centre(bodies.get(i)).sub(centre(bodies.get(i + 1))).length();
        }

        Bridge bridge = new Bridge(train, carriages, new ArrayList<>(bodies), rests);
        BRIDGED.put(train, bridge);
        CreateTrainsSable.LOGGER.info(
                "Bridged train {}: carriages={} bodies={} rests={}", train.id, carriages.size(),
                bodies.size(), describe(rests));

        // The two numbers that describe a gap measure different things: the train's spacing is the
        // distance between the carriages' facing points along the track, the spring's rest length is
        // the distance between the bodies' centres of mass at the moment of coupling. The difference
        // is fixed by how far each vehicle's centre sits from its carriage's reference point, so it
        // is a constant per consist and it cancels in the deformation. It does not cancel in what
        // Create thinks the stress is, and a consist laid out very differently from the spacing the
        // train was built with will show a permanent stress the spring does not feel.
        for (int i = 0; i < rests.length && i < train.carriageSpacing.size(); i++) {
            double spacing = train.carriageSpacing.get(i);
            double offset = rests[i] - spacing;
            if (Math.abs(offset) > 2.0) {
                CreateTrainsSable.LOGGER.warn(
                        "Gap {} of train {}: the bodies are {} blocks apart but the train's spacing is"
                                + " {}; Create will report stress the spring does not feel",
                        i, train.id, String.format(java.util.Locale.ROOT, "%.3f", rests[i]),
                        (int) spacing);
            }
        }
        return bridge;
    }

    /** Stops driving every body and returns the carriages to Create's own movement. */
    public static void detach(Train train) {
        Bridge bridge = BRIDGED.remove(train);
        if (bridge == null) {
            return;
        }
        for (BridgedCarriage carriage : bridge.carriages) {
            carriage.deactivate();
        }
        CreateTrainsSable.LOGGER.info("Unbridged train {}: carriages={}", train.id,
                bridge.carriages.size());
    }

    public static boolean isBridged(Train train) {
        return train != null && BRIDGED.containsKey(train);
    }

    /** The bridge of a train, or {@code null} when the train is not bridged. */
    public static Bridge bridged(Train train) {
        return train == null ? null : BRIDGED.get(train);
    }

    /** The body of the leading carriage of a bridged train, or {@code null}. */
    public static CarriageBody body(Train train) {
        Bridge bridge = bridged(train);
        return bridge == null || bridge.carriages.isEmpty() ? null : bridge.carriages.getFirst().body();
    }

    /** The train carried by a body, or {@code null} when the body is not bridged. */
    public static Train trainOf(ServerSubLevel body) {
        if (body == null) {
            return null;
        }
        for (Map.Entry<Train, Bridge> entry : BRIDGED.entrySet()) {
            for (ServerSubLevel candidate : entry.getValue().bodies) {
                if (candidate == body || candidate.getUniqueId().equals(body.getUniqueId())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /** How many trains are currently bridged; used by the harness and by diagnostics. */
    public static int count() {
        return BRIDGED.size();
    }

    /**
     * One tick of every spring.
     *
     * <p>Attached to the server tick in the mod constructor. A body that disappeared takes its train
     * out of the bridge rather than being driven by a handle that no longer exists.</p>
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCouplings();
    }

    /** Runs one tick of every coupling; separate from the listener so the harness can call it. */
    public static void tickCouplings() {
        if (BRIDGED.isEmpty()) {
            return;
        }
        TruckSettings settings = CTSConfig.trucks();
        double timeStep = 1.0 / CarriageBody.TICKS_PER_SECOND;
        List<Train> broken = new ArrayList<>();
        for (Bridge bridge : BRIDGED.values()) {
            for (int i = 0; i < bridge.bodies.size(); i++) {
                if (bridge.bodies.get(i).isRemoved()) {
                    broken.add(bridge.train);
                    break;
                }
            }
            if (broken.contains(bridge.train)) {
                continue;
            }
            for (int i = 0; i < bridge.restLengths.length; i++) {
                bridge.reports[i] = couple(bridge.bodies.get(i), bridge.bodies.get(i + 1),
                        bridge.restLengths[i], settings, timeStep);
            }
        }
        for (Train train : broken) {
            CreateTrainsSable.LOGGER.warn("Train {} lost a body; unbinding it", train.id);
            detach(train);
        }
    }

    /** One spring of one gap: measure, then push both bodies by equal and opposite impulses. */
    private static TrainCouplings.Report couple(ServerSubLevel front, ServerSubLevel back,
                                                double restLength, TruckSettings settings,
                                                double timeStep) {
        RigidBodyHandle frontHandle = RigidBodyHandle.of(front);
        RigidBodyHandle backHandle = RigidBodyHandle.of(back);
        if (frontHandle == null || backHandle == null || !frontHandle.isValid()
                || !backHandle.isValid()) {
            return null;
        }
        Vec frontCentre = centre(front);
        Vec backCentre = centre(back);
        Vec frontVelocity = velocity(frontHandle);
        Vec backVelocity = velocity(backHandle);
        TrainCouplings.Report report = TrainCouplings.report(frontCentre, backCentre, restLength,
                frontVelocity, backVelocity, settings);

        Vec axis = backCentre.sub(frontCentre).normalized();
        if (axis.lengthSqr() < 0.5) {
            return report;
        }
        double frontMass = Math.max(1.0e-3, front.getMassTracker().getMass());
        double backMass = Math.max(1.0e-3, back.getMassTracker().getMass());
        double effectiveMass = frontMass * backMass / (frontMass + backMass);
        double magnitude = report.acceleration() * effectiveMass * timeStep;

        // Positive acceleration means "pull together": the leading body is pulled backwards along the
        // axis and the trailing one forwards against it. Equal and opposite, or a coupling would be a
        // free motor.
        impulse(front, frontHandle, axis.scale(magnitude));
        impulse(back, backHandle, axis.scale(-magnitude));
        return report;
    }

    private static void impulse(ServerSubLevel body, RigidBodyHandle handle, Vec worldImpulse) {
        if (worldImpulse.lengthSqr() <= 0.0) {
            return;
        }
        Vector3d localImpulse = body.logicalPose().transformNormalInverse(
                new Vector3d(worldImpulse.x(), worldImpulse.y(), worldImpulse.z()));
        Vector3dc centreOfMass = body.getMassTracker().getCenterOfMass();
        handle.applyImpulseAtPoint(
                new Vector3d(centreOfMass.x(), centreOfMass.y(), centreOfMass.z()), localImpulse);
    }

    /** World position of the centre of mass of a body. */
    public static Vec centre(ServerSubLevel body) {
        Vector3dc centreOfMass = body.getMassTracker().getCenterOfMass();
        Vector3d world = body.logicalPose().transformPosition(new Vector3d(centreOfMass), new Vector3d());
        return new Vec(world.x(), world.y(), world.z());
    }

    private static Vec velocity(RigidBodyHandle handle) {
        Vector3dc raw = handle.getLinearVelocity(new Vector3d());
        return new Vec(raw.x(), raw.y(), raw.z());
    }

    private static String describe(double[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(String.format(java.util.Locale.ROOT, "%.3f", values[i]));
        }
        return builder.append(']').toString();
    }
}
