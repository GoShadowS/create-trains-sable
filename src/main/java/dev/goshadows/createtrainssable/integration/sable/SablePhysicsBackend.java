package dev.goshadows.createtrainssable.integration.sable;

import com.mojang.logging.LogUtils;
import dev.goshadows.createtrainssable.integration.AttachedBody;
import dev.goshadows.createtrainssable.integration.ConversionRequest;
import dev.goshadows.createtrainssable.integration.PhysicsBackend;
import dev.goshadows.createtrainssable.integration.ReleaseReason;
import dev.goshadows.createtrainssable.physics.MassProperties;
import dev.goshadows.createtrainssable.physics.Point3;
import dev.goshadows.createtrainssable.physics.Pose;
import dev.goshadows.createtrainssable.physics.Quat;
import dev.goshadows.createtrainssable.physics.TractionModel;
import dev.goshadows.createtrainssable.physics.Tuning;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rapier-backed implementation. This class is loaded by name from BackendLoader, so it can
 * reference Sable's internal API freely without making Sable a hard runtime dependency.
 *
 * <p>Sable owns the body and its pose sync. While Create still drives the train the body is
 * teleported to the carriage every tick, so the train *is* a Sable structure: its blocks live in
 * a plot, collide with the world and with entities, and are rendered by Sable. Once the train
 * derails the body is released to Rapier with the train's momentum and this class only adds wheel
 * forces on Sable's physics substeps.</p>
 */
public final class SablePhysicsBackend implements PhysicsBackend {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, SableBody> bodies = new LinkedHashMap<>();

    public SablePhysicsBackend() {
        SableEventPlatform.INSTANCE.onPhysicsTick((physicsSystem, timeStep) -> onPhysicsTick(timeStep));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supportsForces() {
        return true;
    }

    @Override
    public String description() {
        return "Sable / Rapier (server authoritative, Sable pose sync)";
    }

    @Override
    public Optional<AttachedBody> attach(ConversionRequest request) {
        BoundingBox3i bounds = BoundingBox3i.from(request.worldPositions());
        if (bounds == null) {
            LOGGER.warn("No bounds for carriage {} of train {}; nothing to assemble",
                    request.carriageIndex(), request.trainId());
            return Optional.empty();
        }
        if (SubLevelContainer.getContainer(request.level()) == null) {
            LOGGER.warn("Level {} has no Sable sub-level container; cannot assemble train {}",
                    request.level().dimension().location(), request.trainId());
            return Optional.empty();
        }

        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                request.level(), request.anchor(), request.worldPositions(), bounds.expand(1, 1, 1));
        if (subLevel == null) {
            LOGGER.warn("Sable returned no sub-level for carriage {} of train {}",
                    request.carriageIndex(), request.trainId());
            return Optional.empty();
        }

        CompoundTag userData = new CompoundTag();
        userData.putUUID("TrainId", request.trainId());
        userData.putInt("CarriageIndex", request.carriageIndex());
        subLevel.setUserDataTag(userData);
        subLevel.setName("cts-" + request.trainId().toString().substring(0, 8)
                + "-" + request.carriageIndex());

        // The body keeps the pose Sable assembled it with; TrainStructure reads that pose once
        // and re-applies the offset between it and the carriage on every following tick.
        SableBody body = new SableBody(subLevel, request.massProperties(), request.tuning());
        bodies.put(body.id(), body);

        LOGGER.info("Assembled carriage {} of train {} as Sable sub-level {} ({} blocks)",
                request.carriageIndex(), request.trainId(), body.id(), request.worldPositions().size());
        return Optional.of(body);
    }

    private void onPhysicsTick(double timeStep) {
        for (SableBody body : new ArrayList<>(bodies.values())) {
            body.applyWheelForces(timeStep);
        }
    }

    @Override
    public void shutdown() {
        bodies.clear();
    }

    private final class SableBody implements AttachedBody {

        private final ServerSubLevel subLevel;
        private final Tuning tuning;
        private MassProperties massProfile;
        private boolean drivenByTrain;
        private final Vector3d queuedForce = new Vector3d();
        private final Vector3d queuedTorque = new Vector3d();

        private SableBody(ServerSubLevel subLevel, MassProperties massProfile, Tuning tuning) {
            this.subLevel = subLevel;
            this.massProfile = massProfile;
            this.tuning = tuning;
        }

        @Override
        public String id() {
            return subLevel.getUniqueId().toString();
        }

        @Override
        public boolean isValid() {
            if (subLevel.isRemoved()) {
                return false;
            }
            SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
            return container != null && container.getSubLevel(subLevel.getUniqueId()) != null;
        }

        @Override
        public Pose samplePose() {
            Vector3d linear = new Vector3d(subLevel.latestLinearVelocity);
            Vector3d angular = new Vector3d(subLevel.latestAngularVelocity);
            RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            if (handle != null && handle.isValid()) {
                handle.getLinearVelocity(linear);
                handle.getAngularVelocity(angular);
            }
            Vector3dc position = subLevel.logicalPose().position();
            Quaterniond orientation = subLevel.logicalPose().orientation();
            return new Pose(
                    new Point3(position.x(), position.y(), position.z()),
                    new Quat(orientation.x(), orientation.y(), orientation.z(), orientation.w()),
                    new Point3(linear.x, linear.y, linear.z),
                    new Point3(angular.x, angular.y, angular.z));
        }

        @Override
        public void applyMassProfile(MassProperties properties) {
            this.massProfile = properties;
        }

        /**
         * Rail forces for a running train. They are queued here and applied on every physics substep
         * as an impulse, then cleared: Sable runs several substeps per tick and would otherwise apply
         * a whole tick's worth of force at once, which is what makes a light carriage oscillate.
         */
        @Override
        public void drive(Point3 force, Point3 torque) {
            drivenByTrain = false;
            queuedForce.set(force.x(), force.y(), force.z());
            queuedTorque.set(torque.x(), torque.y(), torque.z());
        }

        @Override
        public void launch(Point3 linearVelocity) {
            drivenByTrain = false;
            RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            if (handle == null || !handle.isValid()) {
                return;
            }
            handle.addLinearAndAngularVelocity(
                    new Vector3d(linearVelocity.x(), linearVelocity.y(), linearVelocity.z()),
                    new Vector3d());
        }

        @Override
        public void release(ReleaseReason reason) {
            SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
            if (container != null && !subLevel.isRemoved()) {
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            }
            bodies.remove(id());
            LOGGER.info("Released sub-level {} ({})", id(), reason);
        }

        /**
         * Wheel forces are applied on the physics substep, not on the game tick: Sable runs several
         * substeps per tick and resets queued forces in between. A train that is still driven by
         * Create gets no forces, or the two controllers would fight each other.
         */
        private void applyWheelForces(double timeStep) {
            if (drivenByTrain) {
                return;
            }
            RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            if (handle == null || !handle.isValid()) {
                return;
            }

            // Rail forces are applied at the centre of mass, so no plot-space conversion is needed:
            // Sable takes world-space vectors here and applies them to the body origin.
            if (queuedForce.lengthSquared() > 0.0 || queuedTorque.lengthSquared() > 0.0) {
                handle.applyLinearAndAngularImpulse(
                        new Vector3d(queuedForce).mul(timeStep),
                        new Vector3d(queuedTorque).mul(timeStep));
                queuedForce.set(0.0);
                queuedTorque.set(0.0);
            }

            Vector3d velocity = handle.getLinearVelocity(new Vector3d());
            double speed = velocity.length();
            if (speed < 1.0e-4) {
                return;
            }

            double rolling = TractionModel.rollingResistanceForce(massProfile.mass(), speed,
                    tuning);
            TractionModel.Result brake = TractionModel.evaluate(
                    new TractionModel.Demand(0.0, 1.0, speed, massProfile.mass()), tuning);
            double signedForce = rolling + brake.force();
            Vector3d impulse = new Vector3d(velocity).normalize().mul(signedForce * timeStep);
            handle.applyLinearAndAngularImpulse(impulse, new Vector3d());
        }

        /**
         * Kinematic fallback: the body is moved by hand. Only used when the server disables
         * physical rails, because it cancels momentum and therefore cannot derail.
         */
        @Override
        public void follow(Pose pose) {
            drivenByTrain = true;
            releaseQueuedForces();
            RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            if (handle == null || !handle.isValid()) {
                return;
            }
            Vector3d linear = handle.getLinearVelocity(new Vector3d());
            Vector3d angular = handle.getAngularVelocity(new Vector3d());
            handle.addLinearAndAngularVelocity(linear.negate(), angular.negate());
            handle.teleport(
                    new Vector3d(pose.position().x(), pose.position().y(), pose.position().z()),
                    new Quaterniond(pose.orientation().x(), pose.orientation().y(),
                            pose.orientation().z(), pose.orientation().w()));
        }

        private void releaseQueuedForces() {
            queuedForce.set(0.0);
            queuedTorque.set(0.0);
        }
    }
}
