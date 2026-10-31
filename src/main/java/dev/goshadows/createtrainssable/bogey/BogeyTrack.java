package dev.goshadows.createtrainssable.bogey;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;

/**
 * The piece of Create track a bogey is currently riding on.
 *
 * <p>A physics vehicle does not follow the track because something moves it along a spline, it
 * follows the track because there is a rail under each axle and the axle is held onto it. To hold it
 * on, the mod has to know where the rail is: this finds the nearest edge of Create's
 * {@code TrackGraph}, with the tangent and the normal at that exact point. Straight track and a
 * curved {@code BezierConnection} are handled by the same code because
 * {@link TrackEdge#getPosition} and {@link TrackEdge#getNormal} already know about turns.</p>
 */
public record BogeyTrack(TrackGraph graph, TrackEdge edge, double t, Vec3 position, Vec3 tangent,
                         Vec3 normal, Vec3 lateral, double distance, double curvature) {

    /** Samples used per edge when looking for the closest point. */
    private static final int SAMPLES = 12;

    /**
     * @param probe       world position of the axle
     * @param maxDistance how far from a rail the axle may be and still be considered on it
     * @param scanRadius  how far away to look for track nodes before giving up
     */
    public static Optional<BogeyTrack> nearest(ServerLevel level, Vec3 probe, double maxDistance,
                                               double scanRadius) {
        BogeyTrack best = null;
        double bestDistance = maxDistance * maxDistance;
        double nodeRange = scanRadius * scanRadius;

        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (TrackNodeLocation location : graph.getNodes()) {
                if (!location.getDimension().equals(level.dimension())) {
                    continue;
                }
                if (location.getLocation().distanceToSqr(probe) > nodeRange) {
                    continue;
                }
                TrackNode node = graph.locateNode(location);
                if (node == null) {
                    continue;
                }
                Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
                if (connections == null) {
                    continue;
                }
                for (TrackEdge edge : connections.values()) {
                    if (edge.isInterDimensional()) {
                        continue;
                    }
                    // Every edge is reachable from both of its nodes; only look at it once.
                    if (edge.node1.getNetId() > edge.node2.getNetId()) {
                        continue;
                    }
                    BogeyTrack candidate = sample(graph, edge, probe);
                    if (candidate != null && candidate.distance() < bestDistance) {
                        bestDistance = candidate.distance();
                        best = candidate;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static BogeyTrack sample(TrackGraph graph, TrackEdge edge, Vec3 probe) {
        double bestT = 0.0;
        double bestDistance = Double.MAX_VALUE;
        Vec3 bestPosition = null;

        for (int i = 0; i <= SAMPLES; i++) {
            double t = i / (double) SAMPLES;
            Vec3 position = edge.getPosition(graph, t);
            double distance = position.distanceToSqr(probe);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestT = t;
                bestPosition = position;
            }
        }
        if (bestPosition == null) {
            return null;
        }

        // A second, finer pass around the coarse hit: a wrong point would put the virtual axle on the
        // wrong side of the rail and the guidance force would push the vehicle off it.
        double span = 1.0 / SAMPLES;
        for (int i = -4; i <= 4; i++) {
            double t = Math.min(1.0, Math.max(0.0, bestT + span * i / 4.0));
            Vec3 position = edge.getPosition(graph, t);
            double distance = position.distanceToSqr(probe);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestT = t;
                bestPosition = position;
            }
        }

        Vec3 tangent = edge.getDirectionAt(bestT);
        if (tangent.lengthSqr() < 1.0e-8) {
            return null;
        }
        tangent = tangent.normalize();
        Vec3 normal = edge.getNormal(graph, bestT);
        if (normal.lengthSqr() < 1.0e-8) {
            normal = new Vec3(0.0, 1.0, 0.0);
        }
        normal = normal.normalize();
        Vec3 lateral = tangent.cross(normal);
        if (lateral.lengthSqr() < 1.0e-8) {
            return null;
        }
        lateral = lateral.normalize();

        return new BogeyTrack(graph, edge, bestT, bestPosition, tangent, normal, lateral,
                Math.sqrt(bestDistance), curvature(graph, edge, bestT));
    }

    /** Curvature in 1/blocks, from the angle between two tangents a little way apart. */
    private static double curvature(TrackGraph graph, TrackEdge edge, double t) {
        double step = 0.02;
        double before = Math.max(0.0, t - step);
        double after = Math.min(1.0, t + step);
        if (after - before < 1.0e-6) {
            return 0.0;
        }
        Vec3 a = edge.getDirectionAt(before);
        Vec3 b = edge.getDirectionAt(after);
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, a.normalize().dot(b.normalize()))));
        double distance = edge.getPosition(graph, after).distanceTo(edge.getPosition(graph, before));
        return distance < 1.0e-6 ? 0.0 : angle / distance;
    }

    /** True when the vehicle is going too fast for the curve the rail is in. */
    public boolean isOverspeed(double speed, double lateralLimit) {
        return curvature * speed * speed > lateralLimit;
    }

    /** Signed speed along the rail. */
    public double speedAlong(Vec3 velocity) {
        return velocity.dot(tangent);
    }

    /** Distance from {@code point} to the rail, signed, along the lateral axis. */
    public double lateralError(Vec3 point) {
        return point.subtract(position).dot(lateral);
    }

    /** Distance from {@code point} to the rail head, signed, along the up axis. */
    public double verticalError(Vec3 point) {
        return point.subtract(position).dot(normal);
    }
}
