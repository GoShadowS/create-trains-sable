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
 * <p>A carriage can be deactivated. A deactivated carriage is still the same object with the same
 * id, so the train's serialisation and network identity do not change; it simply goes back to
 * Create's own integration and spawns its contraption entity again. That is what "unbridge" does,
 * and it is why unbinding does not have to rebuild the train.</p>
 */
public final class BridgedCarriage extends Carriage {

    /** Below this squared length a direction is noise, in the same units as {@link Vec}. */
    private static final double MIN_DIRECTION = 0.25;

    private CarriageBody body;
    private boolean active;
    private double travelToDate;
    private double lastMoved;

    public BridgedCarriage(Carriage original, CarriageBody body) {
        super(original.bogeys.getFirst(), original.bogeys.getSecond(), original.bogeySpacing);
        this.id = original.id;
        this.body = body;
        this.active = body != null;
        // Create initialises a carriage's cargo storage when it sets up the contraption. A bridged
        // carriage never gets that call, and an uninitialised manager throws the moment the train is
        // written to NBT - the harness caught it as "MountedStorageManager is uninitialized" on the
        // first save-shaped round trip. The storage is empty here because cargo is disabled while a
        // carriage is bridged, but it has to exist.
        this.storage.initialize();
    }

    /** The body this carriage follows, or {@code null} while it is deactivated. */
    public CarriageBody body() {
        return body;
    }

    public boolean isActive() {
        return active && body != null && !body.body().isRemoved();
    }

    /** Points this carriage at a body again, without replacing it in the train. */
    public void activate(CarriageBody body) {
        this.body = body;
        this.active = body != null;
        if (body != null) {
            body.forget();
        }
    }

    /** Returns this carriage to Create's own movement. The body is left alone. */
    public void deactivate() {
        if (body != null) {
            body.clearDrive();
        }
        this.active = false;
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
        return isActive() ? body.measuredSpeed(train == null ? null : forward(train.graph)) : 0.0;
    }

    @Override
    public double travel(Level level, TrackGraph graph, double distance, TravellingPoint toFollowForward,
                         TravellingPoint toFollowBackward, int type) {

        if (graph == null || !isActive()) {
            // Nothing to measure: Create's own integration, exactly as an unbridged carriage.
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
     * A bridged carriage never lets Create create a contraption entity for it.
     *
     * <p>Anchors are still updated inside {@code travel}; only the entity side is skipped. This is
     * not conditional on being active, and the reason is a failure the harness caught: the moment
     * Create is allowed to run its normal path it finds no serialised contraption in a carriage that
     * was built by the bridge, cannot create the entity, sets {@code Train.invalid} and the train is
     * removed from the railway manager on the next tick. A carriage whose blocks live in a Sable body
     * has nothing to hand back to Create, so the suppression has to last as long as the carriage
     * does - including after it is unbridged.</p>
     */
    @Override
    public void manageEntities(Level level) {
        // Intentionally empty: see the method comment.
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
