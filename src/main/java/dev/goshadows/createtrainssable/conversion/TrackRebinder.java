package dev.goshadows.createtrainssable.conversion;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
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
 * <p>First it asks Create's own TrainMigration objects to resolve the old travelling points
 * against the original graph. If the wreck came to rest somewhere else, it looks for a
 * straight edge long enough for the whole train and re-seeds every point on that edge.
 * The Train object is never removed, so schedules, signals and station reservations survive.</p>
 */
public final class TrackRebinder {

    private record StraightEdge(TrackGraph graph, TrackNodeLocation first,
                                TrackNodeLocation second, double length) {
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

    public static boolean rebind(ServerLevel level, Train train, TrainStateSnapshot snapshot,
                                 Point3 settled, Tuning tuning) {
        TrackGraph original = snapshot.graph();
        if (original != null && Create.RAILWAYS.trackNetworks.containsValue(original)
                && tryOriginalGraph(snapshot, original)) {
            restoreTrain(level, train, original);
            return true;
        }

        Vec3 target = new Vec3(settled.x(), settled.y(), settled.z());
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            StraightEdge edge = findStraightEdge(graph, level, target,
                    snapshot.totalLength(), tuning.rebinding().maxTrackDistance());
            if (edge == null || !reseedPoints(snapshot, edge)) {
                continue;
            }
            restoreTrain(level, train, edge.graph());
            return true;
        }
        return false;
    }

    private static boolean tryOriginalGraph(TrainStateSnapshot snapshot, TrackGraph graph) {
        List<TrackGraphLocation> resolved = new ArrayList<>();
        for (List<TrainStateSnapshot.PointState> carriage : snapshot.carriages()) {
            for (TrainStateSnapshot.PointState state : carriage) {
                TrackGraphLocation location = state.migration().tryMigratingTo(graph);
                if (location == null) {
                    return false;
                }
                resolved.add(location);
            }
        }
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

    private static StraightEdge findStraightEdge(TrackGraph graph, ServerLevel level, Vec3 target,
                                                 double requiredLength, double maxDistance) {
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
                return new StraightEdge(graph, firstLocation, connection.getKey().getLocation(),
                        edge.getLength());
            }
        }
        return null;
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
