package dev.goshadows.createtrainssable.rail;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.goshadows.createtrainssable.config.TruckSettings;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
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
 * One axle of a physical vehicle.
 *
 * <p>The truck does not move its vehicle. It reports what the wheel feels - the flange pushing back
 * onto the rail, the motor pulling along it, the brake slowing it - as a force at the axle, and the
 * physics engine integrates that. As a result a train can be blocked, pushed, overloaded or thrown
 * off a curve, and the truck has no way to cheat: it can only ask.</p>
 *
 * <p>The state machine is deliberately small. An axle is either <em>guiding</em> (its wheel is on the
 * rail and the flange can hold it) or <em>free</em> (it is not). Leaving the guide state is the
 * derailment of that axle, and it happens for a physical reason: the axle is further from the rail
 * than the flange allows, or the curve needs more lateral force than the flange can supply.</p>
 *
 * <p>Symbols: every length is in metres, every speed in m/s, every force in newtons.</p>
 */
public class TruckBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {

    /** Sub-ticks between two searches of the track graph; the contact follows the edge in between. */
    private RailTrack track;
    private long nextSearchTick = Long.MIN_VALUE;
    private long firstPhysicsTick = -1L;
    private WheelRail.State state = WheelRail.State.NO_TRACK;
    private Vec lastAxle = Vec.ZERO;
    private double lastSpeed;
    private double lastLateralError;
    private WheelRail.State loggedState = WheelRail.State.NO_TRACK;
    /** Shaft speed forced by the physics harness; {@code null} in a normal game. */
    private Float forcedSpeed;

    public TruckBlockEntity(BlockPos pos, BlockState state) {
        super(CTSBlockEntities.TRUCK.get(), pos, state);
    }

    /** True for a truck that turns rotation into motion. */
    public boolean isDriven() {
        return getBlockState().getBlock() instanceof TruckBlock truck && truck.driven();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // A truck is a wheel, not a machine with a menu.
    }

    /**
     * Looks for the rail once per game tick. The physics runs several times per tick and does not
     * need to walk Create's graph that often; it only needs a contact to hold on to.
     */
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (level == null || level.isClientSide()) {
            return;
        }
        TruckSettings settings = CTSConfig.trucks();
        long now = level.getGameTime();
        if (now < nextSearchTick && track != null) {
            return;
        }
        nextSearchTick = now + settings.trackRecheckTicks();
        Vec axle = axlePosition(subLevel);
        // The truck's facing decides which of the two parallel edges of a rail it rides.
        Optional<RailTrack> found = RailTrack.find(subLevel.getLevel(), axle, track,
                settings.maxLateralError() * 2.0 + 1.0, settings.scanRadius(), forward(subLevel));
        track = found.orElse(null);
        if (track == null && state != WheelRail.State.NO_TRACK) {
            log(WheelRail.State.NO_TRACK);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide() || handle == null || !handle.isValid()) {
            return;
        }
        TruckSettings settings = CTSConfig.trucks();
        long now = level.getGameTime();
        if (firstPhysicsTick < 0L) {
            firstPhysicsTick = now;
        }
        double secondsSinceAssembly = (now - firstPhysicsTick) / 20.0;

        Vec axle = axlePosition(subLevel);
        Vector3dc rawVelocity = handle.getLinearVelocity(new Vector3d());
        Vec velocity = new Vec(rawVelocity.x(), rawVelocity.y(), rawVelocity.z());
        Vector3dc rawSpin = handle.getAngularVelocity(new Vector3d());
        Vec spin = new Vec(rawSpin.x(), rawSpin.y(), rawSpin.z());

        // Between searches the contact rides along the edge under the wheel.
        if (track != null) {
            double along = track.speedAlong(velocity);
            RailTrack moved = track.advanced(along * timeStep, axle);
            if (moved != null) {
                track = moved;
            }
        }

        int trucks = countTrucks(subLevel, false);
        int driven = countTrucks(subLevel, true);
        double mass = vehicleMass(subLevel);
        float shaftSpeed = forcedSpeed != null ? forcedSpeed : getSpeed();
        double target = isDriven()
                ? WheelRail.targetSpeed(shaftSpeed, facingSign(), settings)
                : 0.0;

        WheelRail.Output output = WheelRail.solve(track, axle, velocity, forward(subLevel), spin,
                target, isDriven(), trucks, driven, mass, gravity(subLevel, settings),
                secondsSinceAssembly, settings);
        state = output.state();
        lastAxle = axle;
        lastSpeed = track == null ? 0.0 : track.speedAlong(velocity);
        lastLateralError = output.lateralError();

        if (output.guiding()) {
            apply(subLevel, handle, axle, output, timeStep);
        }
        if (output.state() != loggedState) {
            log(output.state());
        }
    }

    /** Turns the contact force into an impulse at the axle, and the steering into angular velocity. */
    private void apply(ServerSubLevel subLevel, RigidBodyHandle handle, Vec axle,
                       WheelRail.Output output, double timeStep) {
        Vec worldImpulse = output.force().scale(timeStep);
        if (worldImpulse.lengthSqr() > 0.0) {
            // The handle takes plot-local vectors: the pipeline rotates them by the body orientation.
            Vector3d localImpulse = subLevel.logicalPose().transformNormalInverse(
                    new Vector3d(worldImpulse.x(), worldImpulse.y(), worldImpulse.z()));
            Vector3d axleInPlot = subLevel.logicalPose().transformPositionInverse(
                    new Vector3d(axle.x(), axle.y(), axle.z()), new Vector3d());
            handle.applyImpulseAtPoint(axleInPlot, localImpulse);
        }
        Vec angular = output.angularAcceleration();
        if (angular.lengthSqr() > 0.0) {
            Vec delta = angular.scale(timeStep);
            handle.addLinearAndAngularVelocity(new Vector3d(),
                    new Vector3d(delta.x(), delta.y(), delta.z()));
        }
    }

    /** World direction the truck faces: the axle runs across it. */
    private Vec forward(ServerSubLevel subLevel) {
        Direction facing = getBlockState().getValue(TruckBlock.FACING);
        Vec3 local = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        return Vec.of(subLevel.logicalPose().transformNormal(local));
    }

    /** Which way the vehicle goes when the shaft turns forwards. */
    private double facingSign() {
        Direction facing = getBlockState().getValue(TruckBlock.FACING);
        return facing.getStepX() + facing.getStepZ() >= 0 ? 1.0 : -1.0;
    }

    /** World position of the axle: the point that has to stay on the rail. */
    public Vec axlePosition(ServerSubLevel subLevel) {
        Vector3d local = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.25,
                worldPosition.getZ() + 0.5);
        Vector3d world = subLevel.logicalPose().transformPosition(local, new Vector3d());
        return new Vec(world.x(), world.y(), world.z());
    }

    /**
     * Gravity the engine actually applies to this vehicle.
     *
     * <p>Sable gives every body the gravity of its dimension, so the rail has to carry exactly that
     * amount and not a number from a config file. The configured value is only the fallback for a
     * dimension whose data is missing.</p>
     */
    private static double gravity(ServerSubLevel subLevel, TruckSettings settings) {
        try {
            double gravity = DimensionPhysicsData.getGravity(subLevel.getLevel()).length();
            if (Double.isFinite(gravity) && gravity > 0.1) {
                return gravity;
            }
        } catch (RuntimeException e) {
            CreateTrainsSable.LOGGER.debug("No dimension gravity available; using the config", e);
        }
        return settings.gravity();
    }

    private static double vehicleMass(ServerSubLevel subLevel) {
        double mass = subLevel.getMassTracker().getMass();
        return Double.isFinite(mass) && mass > 1.0e-3 ? mass : 1.0;
    }

    private static int countTrucks(ServerSubLevel subLevel, boolean onlyDriven) {
        int count = 0;
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof TruckBlockEntity truck && (!onlyDriven || truck.isDriven())) {
                count++;
            }
        }
        return count;
    }

    private void log(WheelRail.State next) {
        loggedState = next;
        if (!CTSConfig.trucks().debugLog()) {
            return;
        }
        switch (next) {
            case GUIDING -> CreateTrainsSable.LOGGER.info(
                    "Truck {} holds the rail: lateral={}, speed={}", worldPosition,
                    String.format("%.2f", lastLateralError), String.format("%.2f", lastSpeed));
            case NO_TRACK -> CreateTrainsSable.LOGGER.info(
                    "Truck {} found no rail within reach; the vehicle is free", worldPosition);
            case OFF_RAIL -> CreateTrainsSable.LOGGER.info(
                    "Truck {} left the rail: lateral={}, at {}", worldPosition,
                    String.format("%.2f", lastLateralError), lastAxle);
            case OVERSPEED -> CreateTrainsSable.LOGGER.info(
                    "Truck {} is too fast for the curve: speed={}, at {}", worldPosition,
                    String.format("%.2f", lastSpeed), lastAxle);
        }
    }

    /**
     * Forces the shaft speed, for the physics harness only.
     *
     * <p>A dedicated server built by a script has no kinetic network to take rotation from, so the
     * harness sets the speed the truck would otherwise read from its shaft. Nothing in a normal
     * game calls this, and {@code null} restores the real shaft.</p>
     */
    public void selfTestSpeed(Float rpm) {
        this.forcedSpeed = rpm;
    }

    /** Current contact state of this axle; used by diagnostics and by tests. */
    public WheelRail.State state() {
        return state;
    }

    @Override
    public void invalidate() {
        track = null;
        super.invalidate();
    }
}
