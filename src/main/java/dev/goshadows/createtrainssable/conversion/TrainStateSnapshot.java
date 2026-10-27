package dev.goshadows.createtrainssable.conversion;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainMigration;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The minimum Create state needed to put a train back on a TrackGraph. TrainMigration is
 * Create's own mechanism for re-seeding travelling points after a graph change.
 */
public final class TrainStateSnapshot {

    public record PointState(TravellingPoint point, TrainMigration migration) {
    }

    private final UUID trainId;
    private final TrackGraph graph;
    private final double speed;
    private final boolean derailed;
    private final List<List<PointState>> carriages;
    private final double totalLength;

    private TrainStateSnapshot(UUID trainId, TrackGraph graph, double speed, boolean derailed,
                               List<List<PointState>> carriages, double totalLength) {
        this.trainId = trainId;
        this.graph = graph;
        this.speed = speed;
        this.derailed = derailed;
        this.carriages = carriages;
        this.totalLength = totalLength;
    }

    public static Optional<TrainStateSnapshot> capture(Train train) {
        if (train == null || train.graph == null || train.carriages.isEmpty()) {
            return Optional.empty();
        }

        List<List<PointState>> snapshots = new ArrayList<>();
        double length = 0.0;
        for (Carriage carriage : train.carriages) {
            List<PointState> points = new ArrayList<>();
            CarriageBogey leading = carriage.leadingBogey();
            addPoint(points, leading.leading());
            addPoint(points, leading.trailing());
            if (carriage.isOnTwoBogeys()) {
                CarriageBogey trailing = carriage.trailingBogey();
                addPoint(points, trailing.leading());
                addPoint(points, trailing.trailing());
            }
            if (points.isEmpty()) {
                return Optional.empty();
            }
            snapshots.add(points);
            length += carriage.bogeySpacing;
        }
        for (int spacing : train.carriageSpacing) {
            length += spacing;
        }

        return Optional.of(new TrainStateSnapshot(train.id, train.graph, train.speed, train.derailed,
                snapshots, Math.max(2.0, length)));
    }

    private static void addPoint(List<PointState> points, TravellingPoint point) {
        if (point == null || point.edge == null || point.node1 == null || point.node2 == null) {
            return;
        }
        points.add(new PointState(point, new TrainMigration(point)));
    }

    public UUID trainId() {
        return trainId;
    }

    public TrackGraph graph() {
        return graph;
    }

    public double speed() {
        return speed;
    }

    public boolean derailed() {
        return derailed;
    }

    public List<List<PointState>> carriages() {
        return carriages;
    }

    /** Approximate train length in blocks, used to find a rebinding edge that fits. */
    public double totalLength() {
        return totalLength;
    }
}
