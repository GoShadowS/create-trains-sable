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
import dev.goshadows.createtrainssable.physics.DerailmentModel;
import dev.goshadows.createtrainssable.physics.Point3;
import dev.goshadows.createtrainssable.physics.Pose;
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
 * <p>Two modes: {@link Mode#TRACKED} while the train runs on its TrackGraph - the sub-levels are
 * teleported onto the carriages every tick, so Create keeps deciding where the train goes -
 * and {@link Mode#FREE} after a derailment, when Rapier owns the bodies and the mod only reacts
 * to where they end up.</p>
 */
public final class TrainStructure {

    public enum Mode {
        /** Create drives the train; the sub-levels follow the carriages. */
        TRACKED,
        /** Rapier owns the bodies after a derailment. */
        FREE
    }

    public enum Outcome {
        ACTIVE,
        RESYNCED,
        ABANDONED
    }

    private record Part(Carriage carriage, AttachedBody body, Point3 offset) {
    }

    private final UUID trainId;
    private final ResourceKey<Level> dimension;
    private final List<Part> parts;
    private final TrainStateSnapshot snapshot;
    private final ResyncEvaluator evaluator = new ResyncEvaluator();
    private Mode mode = Mode.TRACKED;
    private DerailmentModel.Trigger trigger = DerailmentModel.Trigger.NONE;
    private double initialSpeed;
    private int ageTicks;
    private boolean released;

    private TrainStructure(ServerLevel level, UUID trainId, TrainStateSnapshot snapshot,
                           List<Part> parts) {
        this.trainId = trainId;
        this.dimension = level.dimension();
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
            parts.add(new Part(carriage, body, offset));
        }

        if (parts.isEmpty()) {
            return Optional.empty();
        }

        TrainStructure structure = new TrainStructure(level, train.id, snapshot, parts);
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
        ageTicks++;
        if (mode == Mode.TRACKED) {
            follow(level, tuning);
            return Outcome.ACTIVE;
        }
        return tickFree(level, tuning);
    }

    /** Kinematic follow: the sub-levels are put back onto the carriages Create is moving. */
    private void follow(ServerLevel level, Tuning tuning) {
        for (Part part : parts) {
            if (!part.body().isValid()) {
                continue;
            }
            CarriageFrame frame = CarriageFrame.of(part.carriage());
            Point3 target = frame.anchor().add(frame.orientation().rotate(part.offset()));
            part.body().follow(new Pose(target, frame.orientation(), Point3.ZERO, Point3.ZERO));
        }
        // Create respawns carriage entities when their chunks reload; they must stay hidden or
        // the player would see the contraption and the sub-level on top of each other.
        hideEntities();
        if (tuning.debug().showRapierHitboxes() && ageTicks % 20 == 0) {
            RapierDebug.logBodies(trainId, bodies(), ageTicks);
        }
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
            return Outcome.ABANDONED;
        }

        Pose pose = valid.get(0).samplePose();
        double trackDistance = TrackRebinder.distanceToNearestTrack(level, pose.position());
        evaluator.observe(pose, trackDistance, tuning);

        if (tuning.debug().showRapierHitboxes() && ageTicks % 20 == 0) {
            RapierDebug.logBodies(trainId, valid, ageTicks);
        }

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
            release(ReleaseReason.ABANDON);
            return Outcome.ABANDONED;
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
