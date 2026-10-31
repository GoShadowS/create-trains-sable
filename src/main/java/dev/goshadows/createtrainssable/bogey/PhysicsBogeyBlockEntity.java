package dev.goshadows.createtrainssable.bogey;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Optional;

/**
 * The physics of one bogey: find the rail under it, hold the axle on that rail, and pull the
 * vehicle along it with as much force as the wheels have grip for.
 *
 * <p>Everything here runs on Sable's physics substeps, not on the game tick, and everything is
 * applied as a change in velocity. That makes the model mass independent - a heavy train is not
 * slower than a light one, it is just as fast - and it means the body is never teleported, so a
 * collision, a too fast curve or a wall the train runs into all behave the way the engine says they
 * should. Losing the rail is not an event this class raises; it is what happens when the guidance
 * stops being applied because the axle is no longer over a rail.</p>
 */
public class PhysicsBogeyBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {

    /** Highest speed the bogey's own wheels are allowed to command, in m/s. */
    private static final double HARD_SPEED_CAP = 60.0;

    private BogeyTrack track;
    private long trackStamp = Long.MIN_VALUE;
    private boolean reportedDerailment;
    private Vec3 lastVelocity = Vec3.ZERO;

    public PhysicsBogeyBlockEntity(BlockPos pos, BlockState state) {
        super(CTSBlockEntities.PHYSICS_BOGEY.get(), pos, state);
    }

    public boolean isPowered() {
        return getBlockState().getBlock() instanceof PhysicsBogeyBlock bogey && bogey.isPowered();
    }

    @Override
    public void addBehaviours(java.util.List<BlockEntityBehaviour> behaviours) {
        // No behaviours: this block is a wheel, not a machine with a GUI.
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BogeySettings settings = CTSConfig.bogey();
        Vec3 axle = axlePosition(subLevel);
        if (axle == null) {
            return;
        }

        track = refreshTrack(subLevel, axle, settings);

        Vector3dc rawVelocity = handle.getLinearVelocity(new Vector3d());
        Vec3 velocity = new Vec3(rawVelocity.x(), rawVelocity.y(), rawVelocity.z());
        lastVelocity = velocity;

        double targetSpeed = targetSpeed();
        int bogeys = countBogeys(subLevel);
        int powered = countPoweredBogeys(subLevel);
        Vec3 deltaVelocity = Vec3.ZERO;
        Vec3 deltaAngular = Vec3.ZERO;

        if (track != null) {
            double lateral = track.lateralError(axle);
            double vertical = track.verticalError(axle);
            double speed = track.speedAlong(velocity);

            boolean offRail = Math.abs(lateral) > settings.maxLateralError()
                    || Math.abs(vertical) > settings.maxVerticalError();
            boolean tooFast = track.isOverspeed(Math.abs(speed), settings.curveLateralLimit());

            if (offRail || tooFast) {
                // The wheels are not on the rail any more, or the curve cannot hold the vehicle.
                // Guidance simply stops; the body keeps its momentum and becomes a wreck.
                if (!reportedDerailment) {
                    reportedDerailment = true;
                    CreateTrainsSable.LOGGER.info(
                            "Bogey at {} left the rails: lateral={}, vertical={}, speed={}, curvature={}",
                            worldPosition, String.format("%.2f", lateral),
                            String.format("%.2f", vertical), String.format("%.2f", speed),
                            String.format("%.4f", track.curvature()));
                }
            } else {
                reportedDerailment = false;
                deltaVelocity = guidance(track, velocity, axle, settings, bogeys, timeStep)
                        .add(track.tangent().scale(
                                longitudinal(speed, targetSpeed, settings, powered) * timeStep));
                deltaAngular = alignment(subLevel, track, handle, velocity, settings, timeStep);
            }
        } else if (reportedDerailment) {
            reportedDerailment = false;
        }

        if (deltaVelocity.lengthSqr() > 0.0 || deltaAngular.lengthSqr() > 0.0) {
            handle.addLinearAndAngularVelocity(
                    new Vector3d(deltaVelocity.x, deltaVelocity.y, deltaVelocity.z),
                    new Vector3d(deltaAngular.x, deltaAngular.y, deltaAngular.z));
        }
    }

    /**
     * Lateral and vertical hold, with gravity cancelled so the bogey sits on the rail head.
     *
     * <p>Gravity is divided between the bogeys of the vehicle: every bogey corrects its own axle,
     * but the weight of the vehicle is one weight, and a vehicle with four bogeys would otherwise
     * be pushed up four times as hard as it is pulled down.</p>
     */
    private Vec3 guidance(BogeyTrack track, Vec3 velocity, Vec3 axle, BogeySettings settings,
                          int bogeys, double timeStep) {
        double lateralError = track.lateralError(axle);
        double verticalError = track.verticalError(axle);
        double lateralSpeed = velocity.dot(track.lateral());
        double verticalSpeed = velocity.dot(track.normal());

        double lateral = -settings.guideStiffness() * lateralError
                - settings.guideDamping() * lateralSpeed;
        double vertical = -settings.guideStiffness() * verticalError
                - settings.guideDamping() * verticalSpeed
                + settings.gravity() / Math.max(1, bogeys);

        double magnitude = Math.sqrt(lateral * lateral + vertical * vertical);
        double limit = settings.maxGuideAcceleration();
        if (magnitude > limit) {
            double scale = limit / magnitude;
            lateral *= scale;
            vertical *= scale;
        }

        return track.lateral().scale(lateral * timeStep).add(track.normal().scale(vertical * timeStep));
    }

    /**
     * Traction and braking along the rail, capped by the grip of the wheels.
     *
     * <p>The demand is shared between the driven bogeys of the vehicle for the same reason gravity
     * is: two motorised bogeys pull twice as hard as one, and the point of putting more bogeys under
     * a heavy train is reliability, not doubling its acceleration.</p>
     */
    private double longitudinal(double speed, double targetSpeed, BogeySettings settings,
                                int powered) {
        if (!isPowered()) {
            // An unpowered bogey only rolls: rolling resistance, and the brake when it is asked to hold.
            return clamp(-speed * 0.8, -settings.brakeAcceleration(), 0.0);
        }
        double difference = targetSpeed - speed;
        double demand = difference * 4.0 / Math.max(1, powered);
        return clamp(demand, -settings.brakeAcceleration(), settings.tractionAcceleration());
    }

    /** Bogey count of the vehicle this bogey belongs to. */
    private static int countBogeys(ServerSubLevel subLevel) {
        int count = 0;
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof PhysicsBogeyBlockEntity) {
                count++;
            }
        }
        return count;
    }

    private static int countPoweredBogeys(ServerSubLevel subLevel) {
        int count = 0;
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof PhysicsBogeyBlockEntity bogey && bogey.isPowered()) {
                count++;
            }
        }
        return count;
    }

    /** Turns the vehicle so that its facing aligns with the rail. */
    private Vec3 alignment(ServerSubLevel subLevel, BogeyTrack track, RigidBodyHandle handle,
                           Vec3 velocity, BogeySettings settings, double timeStep) {
        Direction facing = getBlockState().getValue(PhysicsBogeyBlock.FACING);
        Vector3dc normal = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vector3d worldNormal = subLevel.logicalPose().transformNormal(normal);
        Vec3 forward = new Vec3(worldNormal.x(), worldNormal.y(), worldNormal.z());
        if (forward.lengthSqr() < 1.0e-8) {
            return Vec3.ZERO;
        }
        forward = forward.normalize();

        double sign = forward.dot(track.tangent()) >= 0.0 ? 1.0 : -1.0;
        Vec3 wanted = track.tangent().scale(sign);
        Vec3 axis = forward.cross(wanted);

        Vector3dc angular = handle.getAngularVelocity(new Vector3d());
        Vec3 spin = new Vec3(angular.x(), angular.y(), angular.z());

        Vec3 angularDelta = axis.scale(settings.alignGain())
                .subtract(spin.scale(settings.alignDamping()));
        double magnitude = angularDelta.length();
        double limit = settings.maxAngularAcceleration();
        if (magnitude > limit) {
            angularDelta = angularDelta.scale(limit / magnitude);
        }
        return angularDelta.scale(timeStep);
    }

    /** World position of the axle, i.e. the point that has to stay on the rail. */
    private Vec3 axlePosition(ServerSubLevel subLevel) {
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5;
        Vector3d local = new Vector3d(x, y, z);
        Vector3d world = subLevel.logicalPose().transformPosition(local, new Vector3d());
        return new Vec3(world.x(), world.y(), world.z());
    }

    /** Speed the bogey tries to reach, in m/s, signed by the facing and by the rotation. */
    private double targetSpeed() {
        if (!isPowered()) {
            return 0.0;
        }
        float rpm = getSpeed();
        if (Math.abs(rpm) < 1.0e-3) {
            return 0.0;
        }
        Direction facing = getBlockState().getValue(PhysicsBogeyBlock.FACING);
        double speed = rpm * CTSConfig.bogey().speedPerRpm();
        // The facing decides which way the vehicle goes when the shaft turns one way, exactly like
        // the direction of a car's wheels would.
        double sign = facing.getStepX() + facing.getStepZ() >= 0 ? 1.0 : -1.0;
        return clamp(speed * sign, -HARD_SPEED_CAP, HARD_SPEED_CAP);
    }

    private BogeyTrack refreshTrack(ServerSubLevel subLevel, Vec3 axle, BogeySettings settings) {
        long now = subLevel.getLevel().getGameTime();
        if (track != null && now - trackStamp < settings.trackRecheckTicks()) {
            return track;
        }
        trackStamp = now;
        Optional<BogeyTrack> found = BogeyTrack.nearest(subLevel.getLevel(), axle,
                settings.maxLateralError() * 2.0 + 1.0, settings.scanRadius());
        return found.orElse(null);
    }

    /** Last measured velocity of the vehicle, used by the coupler to damp relative motion. */
    public Vec3 lastVelocity() {
        return lastVelocity;
    }

    @Override
    public void invalidate() {
        track = null;
        super.invalidate();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
