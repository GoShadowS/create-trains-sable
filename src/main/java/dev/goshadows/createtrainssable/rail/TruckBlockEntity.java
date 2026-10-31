package dev.goshadows.createtrainssable.rail;

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

import java.util.List;
import java.util.Optional;

/**
 * The physics of one rail truck: find the rail under the axle, hold the axle on it, and pull the
 * vehicle along it with as much force as the wheels have grip for.
 *
 * <p>Everything runs on Sable's physics substeps, not on the game tick, and everything is applied as
 * a change in velocity. That makes the model mass independent - a heavy train is not slower than a
 * light one - and it means the body is never teleported, so a collision, a curve taken too fast or a
 * wall the train runs into all behave the way the engine says they should. Leaving the rail is not an
 * event this class raises; it is what happens when guidance stops being applied because the axle is
 * no longer over a rail.</p>
 */
public class TruckBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {

    /** Highest speed a driving truck is allowed to command, in m/s. */
    private static final double HARD_SPEED_CAP = 60.0;

    private RailPath rail;
    private long railStamp = Long.MIN_VALUE;
    private boolean reportedDerailment;
    private Vec3 lastVelocity = Vec3.ZERO;

    public TruckBlockEntity(BlockPos pos, BlockState state) {
        super(CTSBlockEntities.TRUCK.get(), pos, state);
    }

    public boolean isPowered() {
        return getBlockState().getBlock() instanceof TruckBlock truck && truck.isDriven();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // None: a truck is a wheel, not a machine with a menu.
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide()) {
            return;
        }
        TruckSettings settings = CTSConfig.trucks();
        Vec3 axle = axlePosition(subLevel);

        rail = refreshRail(subLevel, axle, settings);

        Vector3dc rawVelocity = handle.getLinearVelocity(new Vector3d());
        Vec3 velocity = new Vec3(rawVelocity.x(), rawVelocity.y(), rawVelocity.z());
        lastVelocity = velocity;

        double targetSpeed = targetSpeed();
        int trucks = countTrucks(subLevel);
        int driven = countDrivenTrucks(subLevel);
        Vec3 deltaVelocity = Vec3.ZERO;
        Vec3 deltaAngular = Vec3.ZERO;

        if (rail != null) {
            double lateral = rail.lateralError(axle);
            double vertical = rail.verticalError(axle);
            double speed = rail.speedAlong(velocity);

            boolean offRail = Math.abs(lateral) > settings.maxLateralError()
                    || Math.abs(vertical) > settings.maxVerticalError();
            boolean tooFast = rail.isOverspeed(Math.abs(speed), settings.curveLateralLimit());

            if (offRail || tooFast) {
                // The axle is not on the rail any more, or the curve cannot hold the vehicle.
                // Guidance simply stops; the body keeps its momentum and becomes a wreck.
                if (!reportedDerailment) {
                    reportedDerailment = true;
                    CreateTrainsSable.LOGGER.info(
                            "Rail truck at {} left the rails: lateral={}, vertical={}, speed={}, "
                                    + "curvature={}",
                            worldPosition, String.format("%.2f", lateral),
                            String.format("%.2f", vertical), String.format("%.2f", speed),
                            String.format("%.4f", rail.curvature()));
                }
            } else {
                reportedDerailment = false;
                deltaVelocity = guidance(rail, velocity, axle, settings, trucks, timeStep)
                        .add(rail.tangent().scale(
                                longitudinal(speed, targetSpeed, settings, driven) * timeStep));
                deltaAngular = alignment(subLevel, rail, handle, settings, timeStep);
            }
        } else {
            reportedDerailment = false;
        }

        if (deltaVelocity.lengthSqr() > 0.0 || deltaAngular.lengthSqr() > 0.0) {
            handle.addLinearAndAngularVelocity(
                    new Vector3d(deltaVelocity.x, deltaVelocity.y, deltaVelocity.z),
                    new Vector3d(deltaAngular.x, deltaAngular.y, deltaAngular.z));
        }
    }

    /**
     * Lateral and vertical hold, with gravity cancelled so the truck sits on the rail head.
     *
     * <p>Gravity is divided between the trucks of the vehicle: each one corrects its own axle, but
     * the weight of the vehicle is one weight, and a vehicle with four trucks would otherwise be
     * pushed up four times as hard as it is pulled down.</p>
     */
    private Vec3 guidance(RailPath rail, Vec3 velocity, Vec3 axle, TruckSettings settings,
                          int trucks, double timeStep) {
        double lateralError = rail.lateralError(axle);
        double verticalError = rail.verticalError(axle);
        double lateralSpeed = velocity.dot(rail.lateral());
        double verticalSpeed = velocity.dot(rail.normal());

        double lateral = -settings.guideStiffness() * lateralError
                - settings.guideDamping() * lateralSpeed;
        double vertical = -settings.guideStiffness() * verticalError
                - settings.guideDamping() * verticalSpeed
                + settings.gravity() / Math.max(1, trucks);

        double magnitude = Math.sqrt(lateral * lateral + vertical * vertical);
        double limit = settings.maxGuideAcceleration();
        if (magnitude > limit) {
            double scale = limit / magnitude;
            lateral *= scale;
            vertical *= scale;
        }

        return rail.lateral().scale(lateral * timeStep).add(rail.normal().scale(vertical * timeStep));
    }

    /**
     * Traction and braking along the rail, capped by the grip of the wheels.
     *
     * <p>The demand is shared between the driving trucks of the vehicle for the same reason gravity
     * is: two driven trucks pull twice as hard as one, and the point of putting more trucks under a
     * heavy train is reliability, not doubling its acceleration.</p>
     */
    private double longitudinal(double speed, double targetSpeed, TruckSettings settings,
                                int driven) {
        if (!isPowered()) {
            // A rolling truck only coasts: rolling resistance, and the brake when it is asked to hold.
            return clamp(-speed * 0.8, -settings.brakeAcceleration(), 0.0);
        }
        double difference = targetSpeed - speed;
        double demand = difference * 4.0 / Math.max(1, driven);
        return clamp(demand, -settings.brakeAcceleration(), settings.tractionAcceleration());
    }

    private static int countTrucks(ServerSubLevel subLevel) {
        int count = 0;
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof TruckBlockEntity) {
                count++;
            }
        }
        return count;
    }

    private static int countDrivenTrucks(ServerSubLevel subLevel) {
        int count = 0;
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof TruckBlockEntity truck && truck.isPowered()) {
                count++;
            }
        }
        return count;
    }

    /** Turns the vehicle so that its facing follows the rail. */
    private Vec3 alignment(ServerSubLevel subLevel, RailPath rail, RigidBodyHandle handle,
                           TruckSettings settings, double timeStep) {
        Direction facing = getBlockState().getValue(TruckBlock.FACING);
        Vector3dc local = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vector3d worldNormal = subLevel.logicalPose().transformNormal(local);
        Vec3 forward = new Vec3(worldNormal.x(), worldNormal.y(), worldNormal.z());
        if (forward.lengthSqr() < 1.0e-8) {
            return Vec3.ZERO;
        }
        forward = forward.normalize();

        double sign = forward.dot(rail.tangent()) >= 0.0 ? 1.0 : -1.0;
        Vec3 wanted = rail.tangent().scale(sign);
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
        Vector3d local = new Vector3d(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.25,
                worldPosition.getZ() + 0.5);
        Vector3d world = subLevel.logicalPose().transformPosition(local, new Vector3d());
        return new Vec3(world.x(), world.y(), world.z());
    }

    /** Speed the truck tries to reach, in m/s, signed by the facing and by the rotation. */
    private double targetSpeed() {
        if (!isPowered()) {
            return 0.0;
        }
        float rpm = getSpeed();
        if (Math.abs(rpm) < 1.0e-3) {
            return 0.0;
        }
        Direction facing = getBlockState().getValue(TruckBlock.FACING);
        double speed = rpm * CTSConfig.trucks().speedPerRpm();
        // The facing decides which way the vehicle goes when the shaft turns one way, exactly like
        // the direction of a car's wheels would.
        double sign = facing.getStepX() + facing.getStepZ() >= 0 ? 1.0 : -1.0;
        return clamp(speed * sign, -HARD_SPEED_CAP, HARD_SPEED_CAP);
    }

    private RailPath refreshRail(ServerSubLevel subLevel, Vec3 axle, TruckSettings settings) {
        long now = subLevel.getLevel().getGameTime();
        if (rail != null && now - railStamp < settings.trackRecheckTicks()) {
            return rail;
        }
        railStamp = now;
        Optional<RailPath> found = RailPath.nearest(subLevel.getLevel(), axle,
                settings.maxLateralError() * 2.0 + 1.0, settings.scanRadius());
        return found.orElse(null);
    }

    /** Last measured velocity of the vehicle. */
    public Vec3 lastVelocity() {
        return lastVelocity;
    }

    @Override
    public void invalidate() {
        rail = null;
        super.invalidate();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
