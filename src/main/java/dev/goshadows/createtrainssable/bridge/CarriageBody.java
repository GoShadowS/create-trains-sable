package dev.goshadows.createtrainssable.bridge;

import dev.goshadows.createtrainssable.rail.TruckBlockEntity;
import dev.goshadows.createtrainssable.rail.Vec;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * The physical half of a carriage: a Sable body whose trucks hold it on the rail.
 *
 * <p>The body is the part that moves. This class never writes a position or a velocity; it only
 * hands the speed the train asks for to the axles, and reports how far the body actually went. A
 * carriage asks that question once per tick and travels exactly that far along its track, which is
 * the whole of the connection between Create's train and the physics engine: Create keeps the
 * route, the physics keeps the body.</p>
 */
public final class CarriageBody {

    /** Create measures speed in blocks per tick; the contact model works in metres per second. */
    public static final double TICKS_PER_SECOND = 20.0;

    /**
     * Ceiling for the distance one carriage may follow the body within one tick.
     *
     * <p>Create's own top speed is 28 m/s, which is 1.4 blocks per tick. A body that appears to move
     * further than this has been teleported, and a carriage must not follow a teleport: the coupling
     * between the two would be a copy rather than a measurement.</p>
     */
    private static final double MAX_ADVANCE_PER_TICK = 2.0;

    private final ServerSubLevel body;
    private Vec lastCentre;

    public CarriageBody(ServerSubLevel body) {
        this.body = body;
        this.lastCentre = centre();
    }

    public ServerSubLevel body() {
        return body;
    }

    /**
     * Hands the commanded speed to the axles of the body.
     *
     * @param blocksPerTick speed the train asks for, signed along {@code forward}
     * @param forward       unit forward direction of the carriage in the world
     */
    public void command(double blocksPerTick, Vec forward) {
        if (forward == null || forward.lengthSqr() < 0.5) {
            return;
        }
        double metresPerSecond = blocksPerTick * TICKS_PER_SECOND;
        for (TruckBlockEntity truck : trucks()) {
            // An idler still has to be told, or it would keep whatever the last command was.
            truck.driveAlong(forward, truck.isDriven() ? metresPerSecond : 0.0);
        }
    }

    /**
     * How far the body has moved along {@code forward} since the last call, in blocks.
     *
     * <p>The measurement is taken against the previous call rather than against the previous tick,
     * so it stays correct no matter where in the tick the train decides to travel.</p>
     */
    public double advance(Vec forward) {
        Vec centre = centre();
        double moved = forward == null || forward.lengthSqr() < 0.5
                ? 0.0
                : centre.sub(lastCentre).dot(forward);
        lastCentre = centre;
        return Mth.clamp(moved, -MAX_ADVANCE_PER_TICK, MAX_ADVANCE_PER_TICK);
    }

    /**
     * Speed of the body along {@code forward}, in m/s, as the solver reports it.
     *
     * <p>Diagnostics only. The solver's velocity is read inside the physics substep, before the
     * pipeline integrates that substep, so it is a state sample and not an average over the tick:
     * the harness measured it 0.045 m/s away from the speed the body's own displacement implies.
     * Anything that needs a true speed must use displacement, as {@link #advance} does.</p>
     */
    public double measuredSpeed(Vec forward) {
        RigidBodyHandle handle = RigidBodyHandle.of(body);
        if (handle == null || !handle.isValid() || forward == null) {
            return 0.0;
        }
        Vector3dc raw = handle.getLinearVelocity(new Vector3d());
        return new Vec(raw.x(), raw.y(), raw.z()).dot(forward);
    }

    /** Stops driving: the axles go back to reading their shafts, or to free rolling. */
    public void clearDrive() {
        for (TruckBlockEntity truck : trucks()) {
            truck.clearDrive();
        }
    }

    /** Forgets where the body was, so the next {@link #advance} starts from the new place. */
    public void forget() {
        lastCentre = centre();
    }

    /** World position of the centre of mass. */
    public Vec centre() {
        Vector3dc centreOfMass = body.getMassTracker().getCenterOfMass();
        Vector3d world = body.logicalPose().transformPosition(new Vector3d(centreOfMass), new Vector3d());
        return new Vec(world.x(), world.y(), world.z());
    }

    private List<TruckBlockEntity> trucks() {
        List<TruckBlockEntity> found = new ArrayList<>();
        for (BlockEntitySubLevelActor actor : body.getPlot().getBlockEntityActors()) {
            if (actor instanceof TruckBlockEntity truck) {
                found.add(truck);
            }
        }
        return found;
    }
}
