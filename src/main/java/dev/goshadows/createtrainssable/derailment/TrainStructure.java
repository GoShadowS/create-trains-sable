package dev.goshadows.createtrainssable.derailment;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.CrashSignals;
import dev.goshadows.createtrainssable.conversion.CarriageFrame;
import dev.goshadows.createtrainssable.conversion.CarriageMaterializer;
import dev.goshadows.createtrainssable.conversion.TrackRebinder;
import dev.goshadows.createtrainssable.conversion.TrainStateSnapshot;
import dev.goshadows.createtrainssable.debug.RapierDebug;
import dev.goshadows.createtrainssable.integration.AttachedBody;
import dev.goshadows.createtrainssable.integration.ConversionRequest;
import dev.goshadows.createtrainssable.integration.PhysicsBackend;
import dev.goshadows.createtrainssable.integration.ReleaseReason;
import dev.goshadows.createtrainssable.physics.CouplingModel;
import dev.goshadows.createtrainssable.physics.DerailmentModel;
import dev.goshadows.createtrainssable.physics.MassProperties;
import dev.goshadows.createtrainssable.physics.Point3;
import dev.goshadows.createtrainssable.physics.Pose;
import dev.goshadows.createtrainssable.physics.Quat;
import dev.goshadows.createtrainssable.physics.RailController;
import dev.goshadows.createtrainssable.physics.ResyncEvaluator;
import dev.goshadows.createtrainssable.physics.Tuning;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One train, represented by Sable sub-levels instead of Create's contraption entity.
 *
 * <p>This is the replacement the whole add-on exists for: as soon as a train exists, every
 * carriage is materialised into a Sable sub-level, so the train people see, walk into and crash
 * into is a real structure in Sable's plot grid, not a moving {@code CarriageContraption}.
 * The hidden contraption entity is kept alive because it still owns the schedule, the seats and
 * the contraption inventory, and because Create's train maths live there.</p>
 *
 * <p>Two modes. {@link Mode#TRACKED} is a running train: Create still decides the route, but the
 * carriages are held on the rails by forces ({@link RailController}) and coupled to each other by
 * couplers ({@link CouplingModel}), so the bodies are simulated and are never teleported.
 * {@link Mode#FREE} is a derailment, when the rails let go and Rapier owns the wreck.</p>
 */
public final class TrainStructure {

    public enum Mode {
        /** Create decides the route; the rails hold the sub-levels on it with forces. */
        TRACKED,
        /** Rapier owns the bodies after a derailment. */
        FREE
    }

    public enum Outcome {
        ACTIVE,
        /** The train came back onto a graph; the caller re-adopts it as a tracked structure. */
        RESYNCED,
        /** The train or its carriages are gone; the structure has been released. */
        LOST
    }

    /** One carriage: its body, the offset between body and carriage anchor, and the rail state. */
    private static final class Part {

        private final Carriage carriage;
        private final AttachedBody body;
        private final Point3 offset;
        private final MassProperties mass;
        private Pose previousTarget;

        private Part(Carriage carriage, AttachedBody body, Point3 offset, MassProperties mass) {
            this.carriage = carriage;
            this.body = body;
            this.offset = offset;
            this.mass = mass;
        }

        private Carriage carriage() {
            return carriage;
        }

        private AttachedBody body() {
            return body;
        }

        private Point3 offset() {
            return offset;
        }
    }

    /** One game tick, in seconds. Rail forces are supplied per tick and spread over substeps. */
    private static final double TICK_SECONDS = 1.0 / 20.0;

    private final UUID trainId;
    private final ResourceKey<Level> dimension;
    private final PhysicsBackend backend;
    private final List<Part> parts;
    private TrainStateSnapshot snapshot;
    private final ResyncEvaluator evaluator = new ResyncEvaluator();
    private Mode mode = Mode.TRACKED;
    private DerailmentModel.Trigger trigger = DerailmentModel.Trigger.NONE;
    private double initialSpeed;
    private int ageTicks;
    private boolean released;
    private boolean resyncGivenUp;
    private int offTrackTicks;

    private TrainStructure(ServerLevel level, UUID trainId, PhysicsBackend backend,
                           TrainStateSnapshot snapshot, List<Part> parts) {
        this.trainId = trainId;
        this.dimension = level.dimension();
        this.backend = backend;
        this.snapshot = snapshot;
        this.parts = parts;
    }

    /**
     * Turns every carriage of a train into a sub-level. Carriages that Sable refuses are rolled
     * back, and a train with no usable carriage is left entirely to Create.
     */
    public static Optional<TrainStructure> attach(ServerLevel level, Train train,
                                                  PhysicsBackend backend, Tuning tuning,
                                                  TrainStateSnapshot snapshot) {
        if (train.carriages.isEmpty()) {
            return Optional.empty();
        }

        List<Part> parts = new ArrayList<>();
        int index = 0;
        for (Carriage carriage : new ArrayList<>(train.carriages)) {
            Optional<CarriageMaterializer.Result> materialised;
            try {
                materialised = CarriageMaterializer.materialize(level, carriage, tuning);
            } catch (RuntimeException e) {
                CreateTrainsSable.LOGGER.error("Could not copy carriage {} of train {} into the world",
                        carriage.id, train.id, e);
                continue;
            }
            if (materialised.isEmpty()) {
                CreateTrainsSable.LOGGER.debug("Carriage {} of train {} has no present entity yet",
                        carriage.id, train.id);
                continue;
            }

            CarriageMaterializer.Result copy = materialised.get();
            Pose carriagePose = CarriageMaterializer.carriagePose(carriage, train, 0.0);
            ConversionRequest request = new ConversionRequest(level, train.id, index++,
                    copy.anchor(), copy.positions(), copy.massProperties(), carriagePose, tuning);
            Optional<AttachedBody> attached;
            try {
                attached = backend.attach(request);
            } catch (RuntimeException e) {
                CreateTrainsSable.LOGGER.error("Sable refused carriage {} of train {}",
                        carriage.id, train.id, e);
                attached = Optional.empty();
            }
            if (attached.isEmpty()) {
                CarriageMaterializer.rollback(level, copy.positions());
                continue;
            }

            AttachedBody body = attached.get();
            body.applyMassProfile(copy.massProperties());
            // Where the assembled body ended up relative to the carriage anchor. Following the
            // train means re-applying this offset every tick, so the blocks do not drift.
            Point3 offset = body.samplePose().position().sub(carriagePose.position());
            parts.add(new Part(carriage, body, offset, copy.massProperties()));
        }

        if (parts.isEmpty()) {
            return Optional.empty();
        }

        TrainStructure structure = new TrainStructure(level, train.id, backend, snapshot, parts);
        structure.hideEntities();
        return Optional.of(structure);
    }

    public UUID trainId() {
        return trainId;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public Mode mode() {
        return mode;
    }

    public int bodyCount() {
        return parts.size();
    }

    public int ageTicks() {
        return ageTicks;
    }

    public DerailmentModel.Trigger trigger() {
        return trigger;
    }

    public double initialSpeed() {
        return initialSpeed;
    }

    public Outcome tick(ServerLevel level, Tuning tuning) {
        // A disassembled train is no longer in the railway manager, and its carriage entities are
        // gone. Keeping the sub-levels would leave a duplicate of a train that no longer exists.
        if (!Create.RAILWAYS.trains.containsKey(trainId)) {
            release(ReleaseReason.ABANDON);
            return Outcome.LOST;
        }
        ageTicks++;
        // Create respawns carriage entities when their chunks reload, in every mode. They must
        // stay hidden or the player sees the contraption and the sub-level on top of each other.
        hideEntities();
        if (mode == Mode.TRACKED) {
            if (tuning.rails().physical() && backend.supportsForces()) {
                return driveOnRails(level, tuning);
            }
            return follow(level, tuning);
        }
        return tickFree(level, tuning);
    }

    /**
     * Physical running: the rails pull each carriage towards its place on the spline and the couplers
     * tie the carriages to each other. Nothing is teleported, so the train can be pushed, can
     * overload its wheels in a curve and can lose the rails.
     */
    private Outcome driveOnRails(ServerLevel level, Tuning tuning) {
        int present = 0;
        double worstError = 0.0;
        double fastest = 0.0;
        boolean struggling = false;

        List<Pose> targets = new ArrayList<>(parts.size());
        List<Pose> states = new ArrayList<>(parts.size());
        List<Point3> railForces = new ArrayList<>(parts.size());
        List<Point3> railTorques = new ArrayList<>(parts.size());

        for (Part part : parts) {
            if (!part.body().isValid()) {
                targets.add(null);
                states.add(null);
                railForces.add(Point3.ZERO);
                railTorques.add(Point3.ZERO);
                continue;
            }
            CarriageFrame frame = CarriageFrame.of(part.carriage());
            if (frame == null) {
                targets.add(null);
                states.add(null);
                railForces.add(Point3.ZERO);
                railTorques.add(Point3.ZERO);
                continue;
            }
            present++;

            Point3 position = frame.anchor().add(frame.orientation().rotate(part.offset()));
            Pose current = part.body().samplePose();
            Pose target = targetPose(part, position, frame.orientation(), current);
            part.previousTarget = target;

            MassProperties mass = part.mass;
            Point3 inertia = new Point3(
                    mass.inertiaAlong(new Point3(1.0, 0.0, 0.0)),
                    mass.inertiaAlong(new Point3(0.0, 1.0, 0.0)),
                    mass.inertiaAlong(new Point3(0.0, 0.0, 1.0)));
            RailController.Output output = RailController.control(
                    current, target, mass.mass(), inertia, tuning);

            worstError = Math.max(worstError, output.error());
            fastest = Math.max(fastest, current.speed());
            // Saturation alone is normal (accelerating a heavy train asks for everything the wheels
            // have); what matters is whether the body is actually leaving the sleeper line.
            struggling |= output.error() > tuning.rails().lateralTolerance()
                    || output.angleError() > tuning.rails().angleTolerance();

            targets.add(target);
            states.add(current);
            railForces.add(output.force());
            railTorques.add(output.torque());
        }

        if (present == 0) {
            release(ReleaseReason.ABANDON);
            return Outcome.LOST;
        }

        for (int i = 0; i + 1 < parts.size(); i++) {
            Pose leading = targets.get(i);
            Pose trailing = targets.get(i + 1);
            Pose leadingState = states.get(i);
            Pose trailingState = states.get(i + 1);
            if (leading == null || trailing == null || leadingState == null || trailingState == null) {
                continue;
            }
            CouplingModel.Pair pair = CouplingModel.solve(
                    leadingState.position(), leadingState.linearVelocity(), parts.get(i).mass.mass(),
                    trailingState.position(), trailingState.linearVelocity(), parts.get(i + 1).mass.mass(),
                    leading.position().distanceTo(trailing.position()), tuning);
            railForces.set(i, railForces.get(i).add(pair.onFirst()));
            railForces.set(i + 1, railForces.get(i + 1).add(pair.onSecond()));
        }

        for (int i = 0; i < parts.size(); i++) {
            if (states.get(i) == null || !parts.get(i).body().isValid()) {
                continue;
            }
            parts.get(i).body().drive(railForces.get(i), railTorques.get(i));
        }

        if (tuning.debug().showRapierHitboxes() && ageTicks % 20 == 0) {
            CreateTrainsSable.LOGGER.info(
                    "Train {} on rails: bodies={}, worstError={}, speed={}, offTrackTicks={}",
                    trainId, present, String.format("%.3f", worstError),
                    String.format("%.2f", fastest), offTrackTicks);
        }

        // A body that stays away from its carriage for long enough has left the rails. Standing still
        // does not derail: a blocked train simply pushes against whatever blocks it.
        boolean moving = fastest > 1.0;
        offTrackTicks = struggling && moving ? offTrackTicks + 1 : Math.max(0, offTrackTicks - 1);
        if (offTrackTicks > tuning.rails().toleranceTicks()) {
            offTrackTicks = 0;
            Train train = Create.RAILWAYS.trains.get(trainId);
            if (train != null) {
                derail(level, train, DerailmentModel.Trigger.LEFT_TRACK, fastest, tuning);
                return Outcome.ACTIVE;
            }
        }
        return Outcome.ACTIVE;
    }

    /**
     * Builds the pose the rails want for this carriage. The velocities come from the change of the
     * target itself, so they follow Create's own speed, curves, switches and station stops without
     * this class having to know anything about Create's train maths.
     */
    private Pose targetPose(Part part, Point3 position, Quat orientation, Pose current) {
        Pose previous = part.previousTarget;
        Point3 linearVelocity = previous == null
                ? current.linearVelocity()
                : position.sub(previous.position()).scale(1.0 / TICK_SECONDS);
        Point3 angularVelocity = previous == null
                ? current.angularVelocity()
                : Quat.rotationError(previous.orientation(), orientation).scale(1.0 / TICK_SECONDS);
        return new Pose(position, orientation, linearVelocity, angularVelocity);
    }

    /** Kinematic follow: the sub-levels are put back onto the carriages Create is moving. */
    private Outcome follow(ServerLevel level, Tuning tuning) {
        int present = 0;
        for (Part part : parts) {
            if (!part.body().isValid()) {
                continue;
            }
            CarriageFrame frame = CarriageFrame.of(part.carriage());
            if (frame == null) {
                // Never follow a missing carriage with a default frame: that puts the structure
                // at the world origin and looks like the train teleporting away.
                continue;
            }
            present++;
            Point3 target = frame.anchor().add(frame.orientation().rotate(part.offset()));
            part.body().follow(new Pose(target, frame.orientation(), Point3.ZERO, Point3.ZERO));
        }
        if (present == 0) {
            release(ReleaseReason.ABANDON);
            return Outcome.LOST;
        }
        if (tuning.debug().showRapierHitboxes() && ageTicks % 20 == 0) {
            RapierDebug.logBodies(trainId, bodies(), ageTicks);
        }
        return Outcome.ACTIVE;
    }

    /** Stops following the train and gives Rapier the momentum it had when it left the rails. */
    public void derail(ServerLevel level, Train train, DerailmentModel.Trigger trigger,
                       double speedMetresPerSecond, Tuning tuning) {
        if (mode == Mode.FREE) {
            return;
        }
        this.trigger = trigger;
        this.initialSpeed = speedMetresPerSecond;
        Point3 velocity = train != null ? velocityOf(train, speedMetresPerSecond) : Point3.ZERO;

        if (train != null) {
            // The graph is still attached here; capture where the train actually is now, not
            // where it was when it was adopted, or a later resync snaps it back to that place.
            TrainStateSnapshot fresh = TrainStateSnapshot.capture(train).orElse(null);
            if (fresh != null) {
                this.snapshot = fresh;
            }
            // The crash mixin would otherwise queue this train again on the next tick.
            CrashSignals.beginConversion(trainId);
            try {
                train.crash();
            } finally {
                CrashSignals.endConversion(trainId);
            }
            train.speed = 0.0;
            for (Carriage carriage : train.carriages) {
                carriage.forEachPresentEntity(entity -> entity.ejectPassengers());
            }
        }
        mode = Mode.FREE;
        evaluator.reset();
        for (Part part : parts) {
            part.body().launch(velocity);
        }
        CreateTrainsSable.LOGGER.info(
                "Train {} left the rails: trigger={}, speed={} m/s, bodies={}",
                trainId, trigger, speedMetresPerSecond, parts.size());
    }

    private Outcome tickFree(ServerLevel level, Tuning tuning) {
        List<AttachedBody> valid = bodies().stream().filter(AttachedBody::isValid).toList();
        if (valid.isEmpty()) {
            release(ReleaseReason.ABANDON);
            return Outcome.LOST;
        }

        Pose pose = valid.get(0).samplePose();

        if (tuning.debug().showRapierHitboxes() && ageTicks % 20 == 0) {
            RapierDebug.logBodies(trainId, valid, ageTicks);
        }

        // A wreck is a finished Sable structure. Only put it back on a track when the server owner
        // asked for that, and never yank it away from where it fell.
        if (tuning.rebinding().autoResync() && !resyncGivenUp) {
            double trackDistance = TrackRebinder.distanceToNearestTrack(level, pose.position());
            evaluator.observe(pose, trackDistance, tuning);
            if (evaluator.isReady(tuning)) {
                Train train = Create.RAILWAYS.trains.get(trainId);
                if (train != null && snapshot != null
                        && TrackRebinder.rebind(level, train, snapshot, pose.position(), tuning)) {
                    release(ReleaseReason.RESYNC);
                    return Outcome.RESYNCED;
                }
                evaluator.reset();
            }
            if (ageTicks > tuning.general().sessionTimeoutTicks()) {
                resyncGivenUp = true;
                CreateTrainsSable.LOGGER.info(
                        "Train {} stayed a wreck: no track within reach after {} ticks",
                        trainId, ageTicks);
            }
        }
        return Outcome.ACTIVE;
    }

    public void release(ReleaseReason reason) {
        if (released) {
            return;
        }
        released = true;
        for (Part part : parts) {
            part.body().release(reason);
        }
        if (reason != ReleaseReason.RESYNC) {
            showEntities();
        }
    }

    private List<AttachedBody> bodies() {
        List<AttachedBody> bodies = new ArrayList<>(parts.size());
        for (Part part : parts) {
            bodies.add(part.body());
        }
        return bodies;
    }

    private void hideEntities() {
        for (Part part : parts) {
            part.carriage().forEachPresentEntity(entity -> entity.setInvisible(true));
        }
    }

    private void showEntities() {
        for (Part part : parts) {
            part.carriage().forEachPresentEntity(entity -> entity.setInvisible(false));
        }
    }

    /** Direction the train was travelling, scaled to the derailment speed. */
    private static Point3 velocityOf(Train train, double speed) {
        Vec3 direction = new Vec3(1.0, 0.0, 0.0);
        if (!train.carriages.isEmpty()) {
            Carriage leading = train.carriages.get(0);
            if (leading.getLeadingPoint() != null && leading.getLeadingPoint().edge != null) {
                direction = leading.getLeadingPoint().edge.getDirection(true);
            }
        }
        if (train.speed < 0.0) {
            direction = direction.scale(-1.0);
        }
        direction = direction.normalize().scale(speed);
        return new Point3(direction.x, direction.y, direction.z);
    }
}
