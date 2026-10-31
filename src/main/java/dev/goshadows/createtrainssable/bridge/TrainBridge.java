package dev.goshadows.createtrainssable.bridge;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Binds a Create train to a Sable body.
 *
 * <p>This is the seam of the whole mod. Create owns the route: the track graph, the travelling
 * points, the signals, the stations, the schedule and the user interface. Sable owns the body: what
 * the wheels feel, how much force the motor can put down, whether the vehicle leaves the rail.
 * The two meet at one question, asked once per tick and per carriage: <em>how far did the body
 * actually go?</em></p>
 *
 * <p>Bridging replaces the carriage of a train with a {@link BridgedCarriage} and hands it the body.
 * It does not write the pose of the body from the train, and it does not teleport the train onto the
 * body: the carriage travels the measured distance along its track, which is what keeps every part
 * of Create's train machinery - edge points, signal boundaries, station stops - seeing a train that
 * moves at the speed the physics gave it.</p>
 *
 * <p>The registry is weak: a train that is deleted should not be kept alive by this map. Bridging is
 * refused for trains with more than one carriage, because a multi-carriage train needs the spring
 * couplings between bodies to decide who pulls whom; that is the next phase, not this one.</p>
 */
public final class TrainBridge {

    private static final Map<Train, BridgedCarriage> BRIDGED = new WeakHashMap<>();

    private TrainBridge() {
    }

    /**
     * Bridges a single-carriage train to a body.
     *
     * @param train the train to bridge; it keeps living in Create's railway manager
     * @param body  the assembled body whose trucks hold it on the rail
     * @return the carriage now following the body
     * @throws IllegalArgumentException when the train is null, has no body, or has more carriages
     *                                  than this phase can couple
     */
    public static BridgedCarriage bridge(Train train, ServerSubLevel body) {
        if (train == null) {
            throw new IllegalArgumentException("a train is needed to bridge");
        }
        if (body == null) {
            throw new IllegalArgumentException("a body is needed to bridge");
        }
        if (train.carriages.size() != 1) {
            throw new IllegalArgumentException(
                    "one carriage per body: this train has " + train.carriages.size());
        }
        if (BRIDGED.containsKey(train)) {
            return BRIDGED.get(train);
        }
        Carriage original = train.carriages.getFirst();
        BridgedCarriage bridged = new BridgedCarriage(original, new CarriageBody(body));
        bridged.setTrain(train);
        train.carriages.set(0, bridged);
        BRIDGED.put(train, bridged);
        CreateTrainsSable.LOGGER.info("Bridged carriage {} of train {} to body {}",
                bridged.id, train.id, body.getUniqueId());
        return bridged;
    }

    public static boolean isBridged(Train train) {
        return train != null && BRIDGED.containsKey(train);
    }

    /** The bridged carriage of a train, or {@code null} when the train is not bridged. */
    public static BridgedCarriage bridged(Train train) {
        return train == null ? null : BRIDGED.get(train);
    }

    /** The body of a bridged train, or {@code null} when the train is not bridged. */
    public static CarriageBody body(Train train) {
        BridgedCarriage bridged = bridged(train);
        return bridged == null ? null : bridged.body();
    }

    /**
     * Stops driving the body and forgets the binding.
     *
     * <p>The caller removes the train from Create's railway manager; this only makes sure no axle is
     * left with a speed the train no longer asks for.</p>
     */
    public static void detach(Train train) {
        BridgedCarriage bridged = BRIDGED.remove(train);
        if (bridged == null) {
            return;
        }
        bridged.body().clearDrive();
        CreateTrainsSable.LOGGER.info("Unbridged carriage {} of train {}", bridged.id, train.id);
    }

    /** How many trains are currently bridged; used by the harness and by diagnostics. */
    public static int count() {
        return BRIDGED.size();
    }
}
