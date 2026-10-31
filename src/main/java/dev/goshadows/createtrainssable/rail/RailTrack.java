package dev.goshadows.createtrainssable.rail;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The piece of rail an axle is standing on, with the frame of the rail at that exact point.
 *
 * <p>Create's tracks are a graph of straight and Bezier edges. This class answers one question -
 * "where is the rail nearest this point, and which way does it go there" - and it answers it in a
 * way that is cheap enough to run for every axle of a train: a coarse scan over the edges attached
 * to nearby nodes, then a bounded refinement of the best hit, then the curvature from the chord of
 * two neighbouring points.</p>
 *
 * <p>Nothing here decides whether the vehicle is on the rail; that is
 * {@link WheelRail}'s job. A {@code RailTrack} exists only when there is a rail to talk about.</p>
 */
public final class RailTrack implements RailFrame {

    /** Points sampled along an edge before the hit is refined. */
    private static final int COARSE_SAMPLES = 8;

    /** Refinement iterations; each one halves the bracket, so this is about 1/4000 of an edge. */
    private static final int REFINE_STEPS = 12;

    /** Half-width of the curvature window, as a fraction of an edge. */
    private static final double CURVATURE_WINDOW = 0.03;

    private final TrackGraph graph;
    private final TrackEdge edge;
    private final double t;
    private final Vec3 point;
    private final Vec3 tangent;
    private final Vec3 up;
    private final Vec3 lateral;
    private final double curvature;
    private final double distance;

    private RailTrack(TrackGraph graph, TrackEdge edge, double t, Vec3 point, Vec3 tangent, Vec3 up,
                      Vec3 lateral, double curvature, double distance) {
        this.graph = graph;
        this.edge = edge;
        this.t = t;
        this.point = point;
        this.tangent = tangent;
        this.up = up;
        this.lateral = lateral;
        this.curvature = curvature;
        this.distance = distance;
    }

    public TrackGraph graph() {
        return graph;
    }

    public TrackEdge edge() {
        return edge;
    }

    public double t() {
        return t;
    }

    @Override
    public Vec point() {
        return Vec.of(point);
    }

    @Override
    public Vec tangent() {
        return Vec.of(tangent);
    }

    /** Up direction of the rail: the normal of the track, not of the world. */
    @Override
    public Vec up() {
        return Vec.of(up);
    }

    /** Sideways direction of the rail, {@code tangent x up}. */
    @Override
    public Vec lateral() {
        return Vec.of(lateral);
    }

    /** Curvature in 1/blocks; zero on straight track. */
    @Override
    public double curvature() {
        return curvature;
    }

    /** Distance from the probe to the rail head. */
    public double distance() {
        return distance;
    }

    /** Signed speed of a velocity along this rail, in m/s. */
    public double speedAlong(Vec velocity) {
        return velocity.dot(tangent());
    }

    /** Distance of a point from the rail head, signed along the lateral axis. */
    public double lateralError(Vec probe) {
        return probe.sub(point()).dot(lateral());
    }

    /** Distance of a point from the rail head, signed along the up axis. */
    public double verticalError(Vec probe) {
        return probe.sub(point()).dot(up());
    }

    /**
     * Moves the contact point along the rail by a distance, without touching the graph.
     *
     * <p>Between two graph searches the axle travels a few centimetres; following the edge with the
     * cached parameter keeps the contact point under the wheel instead of leaving it where the axle
     * was when the last search ran.</p>
     *
     * @return the moved contact, or {@code null} when the axle has left this edge and a search is due
     */
    public RailTrack advanced(double travelled, Vec probe) {
        if (edge.getLength() < 1.0e-4) {
            return null;
        }
        double next = t + travelled / edge.getLength();
        if (next < 0.0 || next > 1.0) {
            return null;
        }
        Vec3 moved = edge.getPosition(graph, next);
        return sampleAt(graph, edge, next, moved.distanceTo(new Vec3(probe.x(), probe.y(), probe.z())));
    }

    /**
     * Finds the rail nearest a point.
     *
     * @param previous the contact the axle had, used to prefer a connected edge so a switch or a
     *                 Bezier joint does not teleport the contact to a neighbouring line
     */
    public static Optional<RailTrack> find(ServerLevel level, Vec probe, RailTrack previous,
                                           double maxDistance, double scanRadius) {
        if (previous != null) {
            RailTrack continued = findNear(previous.graph, previous.edge, toVec3(probe), maxDistance);
            if (continued != null) {
                return Optional.of(continued);
            }
        }
        return findGlobally(level, toVec3(probe), maxDistance, scanRadius);
    }

    private static Optional<RailTrack> findGlobally(ServerLevel level, Vec3 probe, double maxDistance,
                                                    double scanRadius) {
        // Create mutates this map while its railway manager ticks, so it is copied before use.
        List<TrackGraph> graphs = new ArrayList<>(Create.RAILWAYS.trackNetworks.values());
        RailTrack best = null;
        double bestSquared = maxDistance * maxDistance;
        double nodeRange = scanRadius * scanRadius;
        Set<Long> visited = new HashSet<>();

        for (TrackGraph graph : graphs) {
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
                    if (!visited.add(edgeKey(edge))) {
                        continue;
                    }
                    RailTrack candidate = search(graph, edge, probe, bestSquared);
                    if (candidate != null && candidate.distance * candidate.distance < bestSquared) {
                        bestSquared = candidate.distance * candidate.distance;
                        best = candidate;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Prefers an edge connected to the one the axle was on: that is how a train follows a switch. */
    private static Vec3 toVec3(Vec vec) {
        return new Vec3(vec.x(), vec.y(), vec.z());
    }

    private static RailTrack findNear(TrackGraph graph, TrackEdge edge, Vec3 probe, double maxDistance) {
        RailTrack best = null;
        double bestSquared = maxDistance * maxDistance;
        for (TrackNode node : new TrackNode[]{edge.node1, edge.node2}) {
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
            if (connections == null) {
                continue;
            }
            for (TrackEdge candidate : connections.values()) {
                RailTrack track = search(graph, candidate, probe, bestSquared);
                if (track != null && track.distance * track.distance < bestSquared) {
                    bestSquared = track.distance * track.distance;
                    best = track;
                }
            }
        }
        return best;
    }

    private static long edgeKey(TrackEdge edge) {
        long a = edge.node1.getNetId();
        long b = edge.node2.getNetId();
        return a < b ? (a << 32) | (b & 0xFFFFFFFFL) : (b << 32) | (a & 0xFFFFFFFFL);
    }

    private static RailTrack search(TrackGraph graph, TrackEdge edge, Vec3 probe, double limitSquared) {
        if (edge.isInterDimensional() || edge.getLength() < 1.0e-3) {
            return null;
        }
        double bestT = 0.0;
        double bestSquared = Double.MAX_VALUE;
        for (int i = 0; i <= COARSE_SAMPLES; i++) {
            double t = i / (double) COARSE_SAMPLES;
            double squared = edge.getPosition(graph, t).distanceToSqr(probe);
            if (squared < bestSquared) {
                bestSquared = squared;
                bestT = t;
            }
        }

        // Ternary refinement: along one edge the distance to the probe is unimodal near the hit.
        double span = 1.0 / COARSE_SAMPLES;
        double left = Math.max(0.0, bestT - span);
        double right = Math.min(1.0, bestT + span);
        for (int i = 0; i < REFINE_STEPS; i++) {
            double third = (right - left) / 3.0;
            double midLeft = left + third;
            double midRight = right - third;
            double leftSquared = edge.getPosition(graph, midLeft).distanceToSqr(probe);
            double rightSquared = edge.getPosition(graph, midRight).distanceToSqr(probe);
            if (leftSquared < rightSquared) {
                right = midRight;
                bestT = midLeft;
                bestSquared = leftSquared;
            } else {
                left = midLeft;
                bestT = midRight;
                bestSquared = rightSquared;
            }
        }
        if (bestSquared > limitSquared) {
            return null;
        }
        return sampleAt(graph, edge, bestT, Math.sqrt(bestSquared));
    }

    /** Builds a contact from a parameter on an edge. */
    private static RailTrack sampleAt(TrackGraph graph, TrackEdge edge, double t, double distance) {
        Vec3 point = edge.getPosition(graph, t);
        Vec3 tangent = edge.getDirectionAt(t);
        if (tangent.lengthSqr() < 1.0e-8) {
            return null;
        }
        tangent = tangent.normalize();
        Vec3 up = edge.getNormal(graph, t);
        if (up.lengthSqr() < 1.0e-8) {
            up = new Vec3(0.0, 1.0, 0.0);
        }
        up = up.normalize();
        Vec3 lateral = tangent.cross(up);
        if (lateral.lengthSqr() < 1.0e-8) {
            return null;
        }
        lateral = lateral.normalize();
        return new RailTrack(graph, edge, t, point, tangent, up, lateral,
                curvature(graph, edge, t), distance);
    }

    /** Curvature from the circle through three points of the rail. */
    private static double curvature(TrackGraph graph, TrackEdge edge, double t) {
        double before = Math.max(0.0, t - CURVATURE_WINDOW);
        double after = Math.min(1.0, t + CURVATURE_WINDOW);
        if (after - before < 1.0e-5) {
            return 0.0;
        }
        Vec3 a = edge.getPosition(graph, before);
        Vec3 b = edge.getPosition(graph, t);
        Vec3 c = edge.getPosition(graph, after);
        Vec3 ab = b.subtract(a);
        Vec3 bc = c.subtract(b);
        double abLength = ab.length();
        double bcLength = bc.length();
        double caLength = c.distanceTo(a);
        if (abLength < 1.0e-6 || bcLength < 1.0e-6 || caLength < 1.0e-6) {
            return 0.0;
        }
        double twiceArea = ab.cross(bc).length();
        return 2.0 * twiceArea / (abLength * bcLength * caLength);
    }
}
