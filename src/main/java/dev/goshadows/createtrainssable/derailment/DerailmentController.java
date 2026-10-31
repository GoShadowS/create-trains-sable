package dev.goshadows.createtrainssable.derailment;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.CrashSignals;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.goshadows.createtrainssable.conversion.TrainStateSnapshot;
import dev.goshadows.createtrainssable.integration.PhysicsBackend;
import dev.goshadows.createtrainssable.integration.ReleaseReason;
import dev.goshadows.createtrainssable.physics.DerailmentModel;
import dev.goshadows.createtrainssable.physics.Tuning;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative owner of the train representation.
 *
 * <p>Every train that exists in the world is represented by Sable sub-levels: the mod adopts a
 * train as soon as its carriages are present, hides Create's contraption entity and drives the
 * sub-levels along the carriage path. A derailment switches that structure to free physics, and
 * a settled wreck is handed back to Create's {@code TrackGraph}.</p>
 */
public final class DerailmentController {

    /** Ticks between attempts to adopt a train whose carriages were not loaded yet. */
    private static final int ADOPTION_RETRY_TICKS = 200;

    private final PhysicsBackend backend;
    private final Map<UUID, TrainStructure> structures = new LinkedHashMap<>();
    private final Map<UUID, Integer> adoptionRetryAt = new HashMap<>();
    private final DerailmentMonitor monitor = new DerailmentMonitor();
    private boolean warnedAboutBackend;

    public DerailmentController(PhysicsBackend backend) {
        this.backend = backend;
    }

    public void tick(ServerLevel level, boolean trainsTickHere) {
        Tuning tuning = CTSConfig.snapshot();
        if (!tuning.general().enabled()) {
            releaseAll(ReleaseReason.ABANDON);
            return;
        }
        if (!backend.isAvailable()) {
            if (!warnedAboutBackend) {
                CreateTrainsSable.LOGGER.warn("No physics backend is available: {}",
                        backend.description());
                warnedAboutBackend = true;
            }
            return;
        }

        int tick = (int) (level.getGameTime() & 0x7fffffffL);
        tickStructures(level, tuning, tick);

        if (!trainsTickHere) {
            return;
        }

        drainCrashSignals(level, tuning);
        if (CTSConfig.convertOnAssembly()) {
            adoptTrains(level, tuning, tick);
        }
        scanForDerailments(level, tuning, tick);
    }

    private void tickStructures(ServerLevel level, Tuning tuning, int tick) {
        for (Map.Entry<UUID, TrainStructure> entry : new ArrayList<>(structures.entrySet())) {
            TrainStructure structure = entry.getValue();
            if (!structure.dimension().equals(level.dimension())) {
                continue;
            }
            TrainStructure.Outcome outcome = structure.tick(level, tuning);
            if (outcome == TrainStructure.Outcome.RESYNCED) {
                structures.remove(entry.getKey());
                monitor.forget(entry.getKey());
                Train train = Create.RAILWAYS.trains.get(entry.getKey());
                if (train != null) {
                    adopt(level, train, tuning, tick, "resynced");
                }
            } else if (outcome == TrainStructure.Outcome.LOST) {
                structures.remove(entry.getKey());
                adoptionRetryAt.remove(entry.getKey());
                monitor.forget(entry.getKey());
            }
        }
    }

    /**
     * Create keeps one Train object per assembled train, whether it was just built in a station
     * or loaded from a save, so "has no structure yet" is the whole condition here.
     */
    private void adoptTrains(ServerLevel level, Tuning tuning, int tick) {
        for (Train train : new ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (train.derailed || structures.containsKey(train.id) || CrashSignals.isConverting(train.id)) {
                continue;
            }
            Integer retryAt = adoptionRetryAt.get(train.id);
            if (retryAt != null && tick < retryAt) {
                continue;
            }
            if (adopt(level, train, tuning, tick, "assembled")) {
                adoptionRetryAt.remove(train.id);
            } else {
                adoptionRetryAt.put(train.id, tick + ADOPTION_RETRY_TICKS);
            }
        }
    }

    private boolean adopt(ServerLevel unusedLevel, Train train, Tuning tuning, int tick, String reason) {
        ServerLevel level = levelOf(train);
        if (level == null) {
            CreateTrainsSable.LOGGER.debug("Train {} has no loaded carriage yet", train.id);
            return false;
        }
        TrainStateSnapshot snapshot = TrainStateSnapshot.capture(train).orElse(null);
        Optional<TrainStructure> structure = TrainStructure.attach(level, train, backend, tuning, snapshot);
        if (structure.isEmpty()) {
            CreateTrainsSable.LOGGER.debug("Train {} has no carriage that can become a sub-level yet",
                    train.id);
            return false;
        }
        structures.put(train.id, structure.get());
        CreateTrainsSable.LOGGER.info("Train {} is now a Sable structure ({} sub-level(s), {})",
                train.id, structure.get().bodyCount(), reason);
        return true;
    }

    private void drainCrashSignals(ServerLevel level, Tuning tuning) {
        for (CrashSignals.Signal signal : CrashSignals.drain()) {
            Train train = Create.RAILWAYS.trains.get(signal.trainId());
            if (train == null) {
                continue;
            }
            ServerLevel carriageLevel = levelOf(train);
            if (carriageLevel == null) {
                continue;
            }
            TrainStructure existing = structures.get(signal.trainId());
            if (existing != null) {
                existing.derail(carriageLevel, train, DerailmentModel.Trigger.CREATE_CRASH,
                        signal.speed(), tuning);
                continue;
            }
            Optional<TrainStructure> structure =
                    TrainStructure.attach(carriageLevel, train, backend, tuning, signal.snapshot());
            structure.ifPresent(created -> {
                structures.put(train.id, created);
                created.derail(carriageLevel, train, DerailmentModel.Trigger.CREATE_CRASH,
                        signal.speed(), tuning);
            });
        }
    }

    private void scanForDerailments(ServerLevel level, Tuning tuning, int tick) {
        for (Train train : new ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (CrashSignals.isConverting(train.id)) {
                continue;
            }
            Optional<DerailmentModel.Verdict> verdict = monitor.observe(train, tuning, tick);
            if (verdict.isEmpty()) {
                continue;
            }
            DerailmentModel.Verdict result = verdict.get();
            ServerLevel carriageLevel = levelOf(train);
            if (carriageLevel == null) {
                continue;
            }
            TrainStructure structure = structures.get(train.id);
            if (structure != null) {
                structure.derail(carriageLevel, train, result.trigger(), result.speed(), tuning);
                monitor.forget(train.id);
                continue;
            }

            Optional<TrainStateSnapshot> snapshot = TrainStateSnapshot.capture(train);
            if (snapshot.isEmpty()) {
                continue;
            }
            Optional<TrainStructure> created =
                    TrainStructure.attach(carriageLevel, train, backend, tuning, snapshot.get());
            created.ifPresent(attached -> {
                structures.put(train.id, attached);
                attached.derail(carriageLevel, train, result.trigger(), result.speed(), tuning);
                monitor.forget(train.id);
            });
        }
    }

    /** A train can be anywhere; its own carriage entity knows which server level that is. */
    private static ServerLevel levelOf(Train train) {
        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity entity = carriage.anyAvailableEntity();
            if (entity != null && entity.level() instanceof ServerLevel level) {
                return level;
            }
        }
        return null;
    }

    private void releaseAll(ReleaseReason reason) {
        for (TrainStructure structure : new ArrayList<>(structures.values())) {
            structure.release(reason);
        }
        structures.clear();
        adoptionRetryAt.clear();
        monitor.clear();
    }

    public void onServerStopped() {
        // Every sub-level is removed with the level; the contraption entities must be visible
        // again so the next launch starts from Create's own state.
        releaseAll(ReleaseReason.SERVER_STOP);
        CrashSignals.clear();
    }
}
