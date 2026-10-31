package dev.goshadows.createtrainssable.bridge;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import dev.goshadows.createtrainssable.rail.Vec;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A carriage that follows a physical body instead of a computed path.
 *
 * <p>Create's train loop asks every carriage to travel a distance derived from {@code Train.speed}.
 * This carriage ignores that number for its own movement and travels the distance its body has
 * actually covered along the rail; everything else - the travelling points, the anchors, the edge
 * points, the signals and the stations - stays Create's code and keeps working.</p>
 *
 * <p>The class is a subclass rather than a patch of Create's class: the only thing the physics
 * engine needs to own is the <em>distance</em>, and a subclass can supply it without touching a
 * single line of Create.</p>
 *
 * <p>A bridged carriage never spawns a {@code CarriageContraptionEntity}: the body in the world is
 * the carriage, and a contraption entity drawn on top of it would be a second copy of the same
 * vehicle.</p>
 */
public final class BridgedCarriage extends Carriage {

    /** Below this squared length a direction is noise, in the same units as {@link Vec}. */
    private static final double MIN_DIRECTION = 0.25;

    private final CarriageBody body;
    private double travelToDate;
    private double lastMoved;

    public BridgedCarriage(Carriage original, CarriageBody body) {
        super(original.bogeys.getFirst(), original.bogeys.getSecond(), original.bogeySpacing);
        this.id = original.id;
        this.body = body;
    }

    public CarriageBody body() {
        return body;
    }

    /** Distance this carriage has travelled since it was bridged, in blocks. */
    public double travelToDate() {
        return travelToDate;
    }

    /** Distance of the last travel step, in blocks. */
    public double lastMoved() {
        return lastMoved;
    }

    /** Speed the body actually has along the carriage's forward direction, in m/s. */
    public double measuredSpeed() {
        return body.measuredSpeed(train == null ? null : forward(train.graph));
    }

    @Override
    public double travel(Level level, TrackGraph graph, double distance, TravellingPoint toFollowForward,
                         TravellingPoint toFollowBackward, int type) {

        if (graph == null || body.body().isRemoved()) {
            // Nothing to measure: fall back to Create's own integration rather than stand still.
            lastMoved = super.travel(level, graph, distance, toFollowForward, toFollowBackward, type);
            travelToDate += lastMoved;
            return lastMoved;
        }

        Vec forward = forward(graph);
        if (train != null) {
            // The train's speed is the order; the axles turn it into force, and the engine decides.
            body.command(train.speed, forward);
        }
        double fromBody = body.advance(forward);
        lastMoved = super.travel(level, graph, fromBody, toFollowForward, toFollowBackward, type);
        travelToDate += lastMoved;
        return lastMoved;
    }

    /**
     * The body is the carriage, so Create must not create a contraption entity for it.
     *
     * <p>Anchors are still updated inside {@code travel}; only the entity side is skipped. Passengers
     * and cargo stay where they are until the body carries them itself.</p>
     */
    @Override
    public void manageEntities(Level level) {
        // Intentionally empty: see the class comment.
    }

    /**
     * Unit forward direction of the carriage, in the world.
     *
     * <p>The leading bogey's two travelling points are a solid baseline: the first point leads when
     * the train travels forwards, so the direction between them is the direction a positive distance
     * moves the carriage.</p>
     */
    private Vec forward(TrackGraph graph) {
        CarriageBogey leading = leadingBogey();
        TravellingPoint front = leading.leading();
        TravellingPoint back = leading.trailing();
        if (front.edge == null || back.edge == null) {
            return Vec.ZERO;
        }
        Vec3 from = back.getPosition(graph);
        Vec3 to = front.getPosition(graph);
        Vec direction = Vec.of(to.subtract(from));
        return direction.lengthSqr() < MIN_DIRECTION ? Vec.ZERO : direction.normalized();
    }
}
