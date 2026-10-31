package dev.goshadows.createtrainssable.selftest;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.goshadows.createtrainssable.rail.CTSBlocks;
import dev.goshadows.createtrainssable.rail.TruckBlock;
import dev.goshadows.createtrainssable.rail.TruckBlockEntity;
import dev.goshadows.createtrainssable.rail.Vec;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * A vehicle on a rail, built by code instead of by a player.
 *
 * <p>The rig is the smallest thing that can be measured: a five by three deck with two truck
 * blocks under it, a straight rail registered in Create's own graph, and the whole thing assembled
 * into a Sable sub-level. It exists only for the self-test harness, so every number a scenario
 * reports comes from the same code paths a player would drive.</p>
 *
 * <p>The rail is a real {@link TrackGraph} with a real {@link TrackEdge}; only its geometry is
 * synthesised, because placing track blocks by code means dealing with curve selection, bearings
 * and the propagator, none of which the contact model depends on.</p>
 */
final class TestRig {

    /** How many blocks the rig is allowed to gather; far above what the rig places. */
    private static final int CAPACITY = 4096;

    /** Blocks of straight rail on each side of the vehicle. */
    private static final int RAIL_BEFORE = 40;
    private static final int RAIL_AFTER = 120;

    /**
     * Distance between two rail nodes.
     *
     * <p>Create places a node every couple of blocks, and the contact only looks at edges attached
     * to a node within {@code scanRadius} of the axle, so a rail made of one long edge is invisible
     * to it. Eight blocks is comfortably inside the default scan radius of 24.</p>
     */
    private static final int NODE_SPACING = 8;

    final ServerLevel level;
    final ServerSubLevelContainer container;
    final ServerSubLevel body;
    final TrackGraph graph;
    final List<TruckBlockEntity> trucks;
    final double railZ;
    final int blockCount;

    private TestRig(ServerLevel level, ServerSubLevelContainer container, ServerSubLevel body,
                    TrackGraph graph, List<TruckBlockEntity> trucks, double railZ, int blockCount) {
        this.level = level;
        this.container = container;
        this.body = body;
        this.graph = graph;
        this.trucks = trucks;
        this.railZ = railZ;
        this.blockCount = blockCount;
    }

    /**
     * Places the rig, assembles it and hands back the body.
     *
     * @param x western end of the deck
     * @param y height of the truck blocks; the deck is one block above them
     * @param z northern edge of the deck
     */
    static TestRig assemble(ServerLevel level, int x, int y, int z) {
        // The axle of a truck sits in the centre of its block, so the rail has to run under that
        // centre line for the contact to see no lateral error at all.
        double railZ = z + 1.5;
        TrackGraph graph = buildRail(level, x - RAIL_BEFORE, x + RAIL_AFTER, y, railZ);

        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                level.setBlockAndUpdate(new BlockPos(x + dx, y + 1, z + dz),
                        Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        placeTruck(level, new BlockPos(x + 1, y, z + 1), CTSBlocks.TRACTION_TRUCK.get());
        placeTruck(level, new BlockPos(x + 3, y, z + 1), CTSBlocks.ROLLING_TRUCK.get());

        BlockPos anchor = new BlockPos(x + 2, y + 1, z + 1);
        SubLevelAssemblyHelper.GatherResult gathered =
                SubLevelAssemblyHelper.gatherConnectedBlocks(anchor, level, CAPACITY, null);
        if (gathered.blocks() == null) {
            throw new IllegalStateException("gather failed: " + gathered.assemblyState());
        }
        ServerSubLevel body = SubLevelAssemblyHelper.assembleBlocks(level, anchor,
                gathered.blocks(), gathered.boundingBox());
        if (body == null) {
            throw new IllegalStateException("assemble returned no sub-level");
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("no sub-level container for the level");
        }
        // A player standing next to the vehicle keeps its plot loaded through an inhabited-chunk
        // ticket. A scripted server has no player, so the harness takes the same forced ticket
        // Sable's own command uses; without it the plot unloads and the body is removed.
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);

        List<TruckBlockEntity> trucks = new ArrayList<>(2);
        for (var actor : body.getPlot().getBlockEntityActors()) {
            if (actor instanceof TruckBlockEntity truck) {
                trucks.add(truck);
            }
        }
        return new TestRig(level, container, body, graph, trucks, railZ, gathered.blocks().size());
    }

    private static void placeTruck(ServerLevel level, BlockPos pos, TruckBlock block) {
        BlockState state = block.defaultBlockState().setValue(TruckBlock.FACING, Direction.EAST);
        level.setBlockAndUpdate(pos, state);
    }

    /** A straight rail, made of short edges the way a built one is, registered in Create's graph. */
    private static TrackGraph buildRail(ServerLevel level, int fromX, int toX, int y, double z) {
        TrackGraph graph = new TrackGraph();
        TrackNode previous = null;
        for (int x = fromX; x <= toX; x += NODE_SPACING) {
            TrackNode node = new TrackNode(new TrackNodeLocation(x, y, z).in(level.dimension()),
                    TrackGraph.nextNodeId(), new Vec3(0, 1, 0));
            graph.addNode(node);
            if (previous != null) {
                graph.putConnection(previous, node,
                        new TrackEdge(previous, node, (BezierConnection) null, TrackMaterial.ANDESITE));
                graph.putConnection(node, previous,
                        new TrackEdge(node, previous, (BezierConnection) null, TrackMaterial.ANDESITE));
            }
            previous = node;
        }
        Create.RAILWAYS.putGraph(graph);
        return graph;
    }

    /** The physics handle of the body, or {@code null} when the engine has none yet. */
    RigidBodyHandle handle() {
        return RigidBodyHandle.of(body);
    }

    double mass() {
        return body.getMassTracker().getMass();
    }

    /** Axle positions in logical world coordinates, one per truck. */
    List<Vec> axles() {
        List<Vec> positions = new ArrayList<>(trucks.size());
        for (TruckBlockEntity truck : trucks) {
            positions.add(truck.axlePosition(body));
        }
        return positions;
    }

    /** Largest sideways distance of any axle from the rail centre line. */
    double lateralError() {
        double worst = 0.0;
        for (Vec axle : axles()) {
            worst = Math.max(worst, Math.abs(axle.z() - railZ));
        }
        return worst;
    }

    /** Height of the axles above the rail head; positive is above. */
    double verticalOffset() {
        double sum = 0.0;
        List<Vec> axles = axles();
        for (Vec axle : axles) {
            sum += axle.y() - railHeight();
        }
        return axles.isEmpty() ? 0.0 : sum / axles.size();
    }

    /** World height of the rail head. */
    double railHeight() {
        return graph.getNodes().iterator().next().getLocation().y;
    }

    /** World linear velocity of the body, or zero when there is no handle. */
    Vec velocity() {
        RigidBodyHandle handle = handle();
        if (handle == null) {
            return Vec.ZERO;
        }
        Vector3dc raw = handle.getLinearVelocity(new Vector3d());
        return new Vec(raw.x(), raw.y(), raw.z());
    }

    /** Applies a world-space impulse at the body's centre of mass. */
    void push(Vec worldImpulse) {
        RigidBodyHandle handle = handle();
        if (handle == null) {
            throw new IllegalStateException("no physics handle for the body");
        }
        Vector3d local = body.logicalPose().transformNormalInverse(
                new Vector3d(worldImpulse.x(), worldImpulse.y(), worldImpulse.z()));
        handle.applyLinearImpulse(local);
    }

    /** States of the trucks, comma separated, for the log line. */
    String states() {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < trucks.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(trucks.get(i).state());
        }
        return builder.append(']').toString();
    }

    /** Gives every truck a shaft speed, standing in for a kinetic network. */
    void drive(float rpm) {
        for (TruckBlockEntity truck : trucks) {
            if (truck.isDriven()) {
                truck.selfTestSpeed(rpm);
            }
        }
    }

    /** Removes the body and the synthetic rail, so the next scenario starts clean. */
    void remove() {
        container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        if (!body.isRemoved()) {
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        Create.RAILWAYS.removeGraph(graph);
    }

    /** Yaw of the body in degrees; a body that is turning instead of moving shows up here. */
    double yawDegrees() {
        Vector3d euler = body.logicalPose().orientation().getEulerAnglesYXZ(new Vector3d());
        return Math.toDegrees(euler.y);
    }

    /** Force the first truck asked for on its last physics tick. */
    Vec truckForce() {
        return trucks.isEmpty() ? Vec.ZERO : trucks.get(0).lastForce();
    }

    /** True once the engine has dropped the body; a scenario that sees this has a bug to report. */
    boolean isRemoved() {
        return body.isRemoved();
    }
}
