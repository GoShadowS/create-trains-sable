package dev.goshadows.createtrainssable.derailment;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.conversion.TrackRebinder;
import dev.goshadows.createtrainssable.conversion.TrainStateSnapshot;
import dev.goshadows.createtrainssable.debug.RapierDebug;
import dev.goshadows.createtrainssable.integration.AttachedBody;
import dev.goshadows.createtrainssable.integration.ReleaseReason;
import dev.goshadows.createtrainssable.physics.DerailmentModel;
import dev.goshadows.createtrainssable.physics.Pose;
import dev.goshadows.createtrainssable.physics.ResyncEvaluator;
import dev.goshadows.createtrainssable.physics.Tuning;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/** One train's stay in the physics world, from conversion to resync or abandonment. */
public final class PhysicsSession {

    private final UUID trainId;
    private final ResourceKey<Level> dimension;
    private final TrainStateSnapshot snapshot;
    private final List<AttachedBody> bodies;
    private final ResyncEvaluator evaluator = new ResyncEvaluator();
    private final DerailmentModel.Trigger trigger;
    private final double initialSpeed;
    private int ageTicks;
    private boolean released;

    public PhysicsSession(ServerLevel level, UUID trainId, TrainStateSnapshot snapshot,
                          List<AttachedBody> bodies, DerailmentModel.Trigger trigger, double initialSpeed) {
        this.trainId = trainId;
        this.dimension = level.dimension();
        this.snapshot = snapshot;
        this.bodies = bodies;
        this.trigger = trigger;
        this.initialSpeed = initialSpeed;
    }

    /** @return true while the session is still active. */
    public boolean tick(ServerLevel currentLevel, Tuning tuning) {
        if (!currentLevel.dimension().equals(dimension)) {
            return true;
        }
        ageTicks++;

        List<AttachedBody> valid = bodies.stream().filter(AttachedBody::isValid).toList();
        if (valid.isEmpty()) {
            release(ReleaseReason.ABANDON);
            return false;
        }

        Pose pose = valid.get(0).samplePose();
        double trackDistance = TrackRebinder.distanceToNearestTrack(currentLevel, pose.position());
        evaluator.observe(pose, trackDistance, tuning);

        if (tuning.debug().showRapierHitboxes() && ageTicks % 20 == 0) {
            RapierDebug.logBodies(trainId, valid, ageTicks);
        }

        if (evaluator.isReady(tuning)) {
            Train train = Create.RAILWAYS.trains.get(trainId);
            if (train != null && TrackRebinder.rebind(currentLevel, train, snapshot, pose.position(), tuning)) {
                release(ReleaseReason.RESYNC);
                return false;
            }
            evaluator.reset();
        }

        if (ageTicks > tuning.general().sessionTimeoutTicks()) {
            release(ReleaseReason.ABANDON);
            return false;
        }
        return true;
    }

    public void release(ReleaseReason reason) {
        if (released) {
            return;
        }
        released = true;
        for (AttachedBody body : bodies) {
            body.release(reason);
        }
    }

    public UUID trainId() {
        return trainId;
    }

    public DerailmentModel.Trigger trigger() {
        return trigger;
    }

    public double initialSpeed() {
        return initialSpeed;
    }

    public int ageTicks() {
        return ageTicks;
    }
}
