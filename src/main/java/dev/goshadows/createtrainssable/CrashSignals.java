package dev.goshadows.createtrainssable;

import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.conversion.TrainStateSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bridge between the Train.crash() mixin and the next server tick. The mixin runs while
 * Create is still mutating travelling points, so only a snapshot crosses that boundary.
 */
public final class CrashSignals {

    public record Signal(UUID trainId, TrainStateSnapshot snapshot, double speed) {
    }

    private static final Queue<Signal> PENDING = new ConcurrentLinkedQueue<>();
    private static final Set<UUID> CONVERTING = ConcurrentHashMap.newKeySet();

    private CrashSignals() {
    }

    public static void onCreateCrash(Train train) {
        if (train == null || train.id == null || isConverting(train.id)) {
            return;
        }
        TrainStateSnapshot.capture(train).ifPresent(snapshot ->
                PENDING.add(new Signal(train.id, snapshot, Math.abs(train.speed) * 20.0)));
    }

    public static List<Signal> drain() {
        List<Signal> signals = new ArrayList<>();
        Signal signal;
        while ((signal = PENDING.poll()) != null) {
            signals.add(signal);
        }
        return signals;
    }

    public static void beginConversion(UUID trainId) {
        CONVERTING.add(trainId);
    }

    public static void endConversion(UUID trainId) {
        CONVERTING.remove(trainId);
    }

    public static boolean isConverting(UUID trainId) {
        return CONVERTING.contains(trainId);
    }

    public static void clear() {
        PENDING.clear();
        CONVERTING.clear();
    }
}
