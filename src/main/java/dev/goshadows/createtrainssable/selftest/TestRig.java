package dev.goshadows.createtrainssable.selftest;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.TravellingPoint;
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
 * <p>The rig is the smallest thing that can be measured: a five by three deck with two truck blocks
 * under it, a straight rail registered in Create's own graph, and the whole thing assembled into a
 * Sable sub-level. It exists only for the self-test harness, so every number a scenario reports comes
 * from the same code paths a player would drive.</p>
 *
 * <p>A rail can be shared by several rigs ({@link #rail}), which is what a train of more than one
 * body needs: two decks on one track, each assembled on its own, so that the only thing holding them
 * together is the coupling and not a shared sub-level.</p>
 *
 * <p>The rail is a real {@link TrackGraph} with a real {@link TrackEdge}; only its geometry is
 * synthesised, because placing track blocks by code means dealing with curve selection, bearings and
 * the propagator, none of which the contact model depends on.</p>
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

    /** A straight rail that one or more vehicles can ride. */
    static final class Rail {

        final ServerLevel level;
        final TrackGraph graph;
        final double z;
        final double y;
        final int fromX;

        private Rail(ServerLevel level, TrackGraph graph, double z, double y, int fromX) {
            this.level = level;
            this.graph = graph;
            this.z = z;
            this.y = y;
            this.fromX = fromX;
        }

        void remove() {
            Create.RAILWAYS.removeGraph(graph);
        }
    }

    final ServerLevel level;
    final ServerSubLevelContainer container;
    final ServerSubLevel body;
    final TrackGraph graph;
    final List<TruckBlockEntity> trucks;
    final double railZ;
    /** Y of the rail nodes: the height of the rail head. */
    final double railY;
    /** X of the first rail node; every node is {@link #NODE_SPACING} further east. */
    final double railFromX;
    final int blockCount;
    /** Where the trucks were placed, in the plot's own coordinates; a tool needs a position. */
    final List<BlockPos> truckPositions;
    private final Rail rail;
    private final boolean ownsRail;

    private TestRig(ServerLevel level, ServerSubLevelContainer container, ServerSubLevel body,
                    Rail rail, List<TruckBlockEntity> trucks, int blockCount, boolean ownsRail,
                    List<BlockPos> truckPositions) {
        this.level = level;
        this.container = container;
        this.body = body;
        this.graph = rail.graph;
        this.trucks = trucks;
        this.railZ = rail.z;
        this.railY = rail.y;
        this.railFromX = rail.fromX;
        this.blockCount = blockCount;
        this.rail = rail;
        this.ownsRail = ownsRail;
        this.truckPositions = truckPositions;
    }

    /**
     * Places a rail for several vehicles to share.
     *
     * @param fromX western end of the rail
     * @param toX   eastern end of the rail
     * @param y     height of the rail head
     * @param z     centre line of the rail
     */
    static Rail rail(ServerLevel level, int fromX, int toX, int y, double z) {
        return new Rail(level, buildRail(level, fromX, toX, y, z), z, y, fromX);
    }

    /**
     * Places a vehicle with a traction truck, assembles it and returns it, on a rail of its own.
     *
     * @param x western end of the deck
     * @param y height of the truck blocks; the deck is one block above them
     * @param z northern edge of the deck
     */
    static TestRig assemble(ServerLevel level, int x, int y, int z) {
        Rail rail = rail(level, x - RAIL_BEFORE, x + RAIL_AFTER, y, z + 1.5);
        return assembleOn(rail, x, z, true, true);
    }

    /** Places a vehicle on an existing rail: driven or not, owning the rail or not. */
    static TestRig assembleOn(Rail rail, int x, int z, boolean driven, boolean ownsRail) {
        ServerLevel level = rail.level;
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                level.setBlockAndUpdate(new BlockPos(x + dx, (int) rail.y + 1, z + dz),
                        Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        List<BlockPos> truckPositions = new ArrayList<>(2);
        BlockPos firstTruck = new BlockPos(x + 1, (int) rail.y, z + 1);
        BlockPos secondTruck = new BlockPos(x + 3, (int) rail.y, z + 1);
        TruckBlock first = driven ? CTSBlocks.TRACTION_TRUCK.get() : CTSBlocks.ROLLING_TRUCK.get();
        placeTruck(level, firstTruck, first);
        placeTruck(level, secondTruck, CTSBlocks.ROLLING_TRUCK.get());
        truckPositions.add(firstTruck);
        truckPositions.add(secondTruck);

        BlockPos anchor = new BlockPos(x + 2, (int) rail.y + 1, z + 1);
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
        return new TestRig(level, container, body, rail, trucks, gathered.blocks().size(), ownsRail,
                truckPositions);
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

    /**
     * A travelling point on the synthetic rail at the given x, pointing east.
     *
     * <p>Used to give a carried carriage its two bogeys on the same rail the trucks ride: the point
     * and the axle meet at the same x, so the harness can compare where the train thinks it is with
     * where the body is.</p>
     */
    TravellingPoint railPoint(double x) {
        double start = railFromX + Math.floor((x - railFromX) / NODE_SPACING) * NODE_SPACING;
        TrackNode first = graph.locateNode(new TrackNodeLocation(start, railY, railZ).in(level.dimension()));
        TrackNode second = graph.locateNode(
                new TrackNodeLocation(start + NODE_SPACING, railY, railZ).in(level.dimension()));
        if (first == null || second == null) {
            throw new IllegalStateException("no rail node at x=" + start);
        }
        TrackEdge edge = graph.getConnectionsFrom(first).get(second);
        if (edge == null) {
            throw new IllegalStateException("no rail edge at x=" + start);
        }
        return new TravellingPoint(first, second, edge, x - start, false);
    }

    /** World position of the centre of mass of the body. */
    Vec centre() {
        Vector3dc centreOfMass = body.getMassTracker().getCenterOfMass();
        Vector3d world = body.logicalPose().transformPosition(new Vector3d(centreOfMass), new Vector3d());
        return new Vec(world.x(), world.y(), world.z());
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
        return railY;
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

    /** Removes the body, and the rail when this rig is the one that placed it. */
    void remove() {
        container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        if (!body.isRemoved()) {
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        if (ownsRail) {
            rail.remove();
        }
    }

    /** Yaw, pitch and roll of the body in degrees, in that order. */
    String attitude() {
        Vector3d euler = body.logicalPose().orientation().getEulerAnglesYXZ(new Vector3d());
        return String.format(java.util.Locale.ROOT, "%.1f/%.1f/%.1f",
                Math.toDegrees(euler.y), Math.toDegrees(euler.x), Math.toDegrees(euler.z));
    }

    /** Lateral and vertical position of every axle, for a body that may be turning rather than drifting. */
    String axleDetails() {
        StringBuilder builder = new StringBuilder();
        for (Vec axle : axles()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(String.format(java.util.Locale.ROOT, "z=%.2f y=%.2f", axle.z(), axle.y()));
        }
        return builder.toString();
    }

    /** Force the first truck asked for on its last physics tick. */
    Vec truckForce() {
        return trucks.isEmpty() ? Vec.ZERO : trucks.get(0).lastForce();
    }

    /** True once the engine has dropped the body; a scenario that sees this has a bug to report. */
    boolean isRemoved() {
        return body.isRemoved();
    }

    /** A block of this vehicle, for a tool that has to be handed a position. */
    BlockPos clickedPos() {
        return truckPositions.isEmpty() ? BlockPos.ZERO : truckPositions.get(0);
    }
}
