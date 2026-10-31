package dev.goshadows.createtrainssable.conversion;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainMigration;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dev.goshadows.createtrainssable.compat.AeronauticsCompat;
import dev.goshadows.createtrainssable.physics.Point3;
import dev.goshadows.createtrainssable.physics.Tuning;
import net.createmod.catnip.data.Couple;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Returns a settled wreck to Create's kinematic mode.
 *
 * <p>Only used when {@code [rebinding] autoResync} is enabled. Create's own
 * {@link TrainMigration} resolves the travelling points, but it resolves them where the train
 * was when it left the rails, so every candidate is distance-checked against the wreck first.
 * If the original graph cannot hold the train where it fell, the closest straight edge long
 * enough for the whole train is used instead. The {@link Train} object is never removed, so
 * schedules, signals and station reservations survive.</p>
 */
public final class TrackRebinder {

    private record StraightEdge(TrackGraph graph, TrackNodeLocation first,
                                TrackNodeLocation second, double length, double distance) {
    }

    private TrackRebinder() {
    }

    public static double distanceToNearestTrack(ServerLevel level, Point3 position) {
        Vec3 point = new Vec3(position.x(), position.y(), position.z());
        if (AeronauticsCompat.isInsideSubLevel(level, point)) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3 projected = AeronauticsCompat.projectToWorld(level, point);
        double bestSquared = Double.POSITIVE_INFINITY;
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (TrackNodeLocation node : graph.getNodes()) {
                if (!level.dimension().equals(node.getDimension())) {
                    continue;
                }
                bestSquared = Math.min(bestSquared, projected.distanceToSqr(node.getLocation()));
            }
        }
        return Double.isFinite(bestSquared) ? Math.sqrt(bestSquared) : Double.POSITIVE_INFINITY;
    }

    /**
     * Puts a settled wreck back on a graph, but only where the wreck actually is.
     *
     * <p>Create's {@link TrainMigration} happily resolves the travelling points a train had before
     * it derailed, which is the assembly station, not the wreck. Restoring those points teleported
     * the whole train back to where it was built. Every candidate is therefore checked against the
     * settled position before any travelling point is touched.</p>
     */
    public static boolean rebind(ServerLevel level, Train train, TrainStateSnapshot snapshot,
                                 Point3 settled, Tuning tuning) {
        Vec3 target = new Vec3(settled.x(), settled.y(), settled.z());
        double tolerance = Math.max(6.0, tuning.rebinding().maxTrackDistance() * 4.0);

        TrackGraph original = snapshot.graph();
        if (original != null && Create.RAILWAYS.trackNetworks.containsValue(original)) {
            List<TrackGraphLocation> resolved = resolve(snapshot, original);
            if (resolved != null && isNear(original, resolved, target, tolerance)
                    && apply(snapshot, resolved)) {
                restoreTrain(level, train, original);
                return true;
            }
        }

        StraightEdge edge = findNearestStraightEdge(level, target,
                snapshot.totalLength(), tuning.rebinding().maxTrackDistance());
        if (edge != null && reseedPoints(snapshot, edge)) {
            restoreTrain(level, train, edge.graph());
            return true;
        }
        return false;
    }

    private static List<TrackGraphLocation> resolve(TrainStateSnapshot snapshot, TrackGraph graph) {
        List<TrackGraphLocation> resolved = new ArrayList<>();
        for (List<TrainStateSnapshot.PointState> carriage : snapshot.carriages()) {
            for (TrainStateSnapshot.PointState state : carriage) {
                TrackGraphLocation location = state.migration().tryMigratingTo(graph);
                if (location == null) {
                    return null;
                }
                resolved.add(location);
            }
        }
        return resolved.isEmpty() ? null : resolved;
    }

    private static boolean apply(TrainStateSnapshot snapshot, List<TrackGraphLocation> resolved) {
        int index = 0;
        for (List<TrainStateSnapshot.PointState> carriage : snapshot.carriages()) {
            for (TrainStateSnapshot.PointState state : carriage) {
                List<TrackGraphLocation> locations = new ArrayList<>();
                locations.add(resolved.get(index++));
                state.point().migrateTo(locations);
            }
        }
        return true;
    }

    /** True when at least one resolved point lies close to the wreck. */
    private static boolean isNear(TrackGraph graph, List<TrackGraphLocation> locations,
                                  Vec3 target, double tolerance) {
        for (TrackGraphLocation location : locations) {
            if (positionOf(graph, location).distanceTo(target) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 positionOf(TrackGraph graph, TrackGraphLocation location) {
        TrackNode first = graph.locateNode(location.edge.getFirst());
        TrackNode second = graph.locateNode(location.edge.getSecond());
        if (first == null || second == null) {
            return Vec3.ZERO;
        }
        TrackEdge edge = graph.getConnectionsFrom(first).get(second);
        if (edge == null) {
            return Vec3.ZERO;
        }
        double length = edge.getLength();
        double t = length > 0.0 ? Math.min(1.0, Math.max(0.0, location.position / length)) : 0.0;
        return edge.getPosition(graph, t);
    }

    private static void restoreTrain(ServerLevel level, Train train, TrackGraph graph) {
        train.graph = graph;
        train.derailed = false;
        train.speed = 0.0;
        train.navigation.cancelNavigation();
        train.status.trackOK();
        for (Carriage carriage : train.carriages) {
            carriage.updateContraptionAnchors();
            carriage.alignEntity(level);
            carriage.forEachPresentEntity(entity -> entity.setInvisible(false));
        }
        Create.RAILWAYS.markTracksDirty();
    }

    /** Closest straight edge in the whole network that can hold the train. */
    private static StraightEdge findNearestStraightEdge(ServerLevel level, Vec3 target,
                                                        double requiredLength, double maxDistance) {
        StraightEdge best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (StraightEdge candidate : straightEdges(graph, level, target, requiredLength, maxDistance)) {
                double distance = candidate.distance();
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static List<StraightEdge> straightEdges(TrackGraph graph, ServerLevel level, Vec3 target,
                                                    double requiredLength, double maxDistance) {
        List<StraightEdge> edges = new ArrayList<>();
        for (TrackNodeLocation firstLocation : graph.getNodes()) {
            if (!level.dimension().equals(firstLocation.getDimension())) {
                continue;
            }
            TrackNode first = graph.locateNode(firstLocation);
            if (first == null) {
                continue;
            }
            for (Map.Entry<TrackNode, TrackEdge> connection : graph.getConnectionsFrom(first).entrySet()) {
                TrackEdge edge = connection.getValue();
                if (edge.isTurn() || edge.isInterDimensional() || edge.getLength() < requiredLength) {
                    continue;
                }
                Vec3 middle = edge.getPosition(graph, 0.5);
                double horizontal = Math.hypot(middle.x - target.x, middle.z - target.z);
                if (horizontal > maxDistance || Math.abs(middle.y - target.y) > maxDistance + 2.0) {
                    continue;
                }
                if (AeronauticsCompat.isInsideSubLevel(level, middle)) {
                    continue;
                }
                edges.add(new StraightEdge(graph, firstLocation, connection.getKey().getLocation(),
                        edge.getLength(), middle.distanceTo(target)));
            }
        }
        return edges;
    }

    private static boolean reseedPoints(TrainStateSnapshot snapshot, StraightEdge edge) {
        int pointCount = 0;
        for (List<TrainStateSnapshot.PointState> carriage : snapshot.carriages()) {
            pointCount += carriage.size();
        }
        if (pointCount == 0) {
            return false;
        }

        double spacing = pointCount > 1
                ? Math.min(1.0, (edge.length() - 0.5) / (pointCount - 1)) : 0.0;
        double start = (edge.length() - spacing * (pointCount - 1)) * 0.5;
        int index = 0;
        for (List<TrainStateSnapshot.PointState> carriage : snapshot.carriages()) {
            for (TrainStateSnapshot.PointState state : carriage) {
                TrackGraphLocation location = new TrackGraphLocation();
                location.graph = edge.graph();
                location.edge = Couple.create(edge.first(), edge.second());
                location.position = Math.max(0.0,
                        Math.min(edge.length(), start + spacing * index));
                List<TrackGraphLocation> locations = new ArrayList<>();
                locations.add(location);
                state.point().migrateTo(locations);
                index++;
            }
        }
        return true;
    }
}
