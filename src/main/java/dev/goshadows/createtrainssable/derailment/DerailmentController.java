package dev.goshadows.createtrainssable.derailment;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.CrashSignals;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.goshadows.createtrainssable.conversion.CarriageMaterializer;
import dev.goshadows.createtrainssable.conversion.TrainStateSnapshot;
import dev.goshadows.createtrainssable.integration.AttachedBody;
import dev.goshadows.createtrainssable.integration.ConversionRequest;
import dev.goshadows.createtrainssable.integration.PhysicsBackend;
import dev.goshadows.createtrainssable.integration.ReleaseReason;
import dev.goshadows.createtrainssable.physics.DerailmentModel;
import dev.goshadows.createtrainssable.physics.Pose;
import dev.goshadows.createtrainssable.physics.Tuning;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-authoritative owner of the kinematic <-> rigid-body hand-off. */
public final class DerailmentController {

    private final PhysicsBackend backend;
    private final SessionRegistry sessions = new SessionRegistry();
    private final DerailmentMonitor monitor = new DerailmentMonitor();
    private boolean warnedAboutBackend;

    public DerailmentController(PhysicsBackend backend) {
        this.backend = backend;
    }

    public void tick(ServerLevel level, boolean trainsTickHere) {
        Tuning tuning = CTSConfig.snapshot();

        Iterator<Map.Entry<UUID, PhysicsSession>> iterator = sessions.iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PhysicsSession> entry = iterator.next();
            if (!entry.getValue().tick(level, tuning)) {
                monitor.forget(entry.getKey());
                iterator.remove();
            }
        }

        for (CrashSignals.Signal signal : CrashSignals.drain()) {
            Train train = Create.RAILWAYS.trains.get(signal.trainId());
            if (train == null || sessions.contains(train.id)) {
                continue;
            }
            DerailmentModel.Inputs inputs = new DerailmentModel.Inputs(signal.speed(),
                    Double.POSITIVE_INFINITY, signal.speed(), 0.0, true);
            DerailmentModel.Verdict verdict = DerailmentModel.evaluate(inputs, tuning);
            if (verdict.derail()) {
                begin(level, train, signal.snapshot(), verdict, tuning, true);
            }
        }

        if (!trainsTickHere || !tuning.general().enabled()) {
            return;
        }
        if (!backend.isAvailable()) {
            if (!warnedAboutBackend) {
                CreateTrainsSable.LOGGER.warn("Physics is enabled but no Sable backend is available: {}",
                        backend.description());
                warnedAboutBackend = true;
            }
            return;
        }

        int tick = (int) (level.getGameTime() & 0x7fffffffL);
        for (Train train : new ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (sessions.contains(train.id) || CrashSignals.isConverting(train.id)) {
                continue;
            }
            Optional<DerailmentModel.Verdict> verdict = monitor.observe(train, tuning, tick);
            if (verdict.isEmpty()) {
                continue;
            }
            Optional<TrainStateSnapshot> snapshot = TrainStateSnapshot.capture(train);
            if (snapshot.isPresent()) {
                begin(level, train, snapshot.get(), verdict.get(), tuning, false);
            }
        }
    }

    private void begin(ServerLevel level, Train train, TrainStateSnapshot snapshot,
                       DerailmentModel.Verdict verdict, Tuning tuning, boolean alreadyCrashed) {
        if (sessions.size() >= tuning.general().maxActiveTrains()) {
            CreateTrainsSable.LOGGER.debug("Skipping conversion of train {}: {} sessions already active",
                    train.id, sessions.size());
            return;
        }

        CrashSignals.beginConversion(train.id);
        try {
            List<AttachedBody> attached = new ArrayList<>();
            List<CarriageMaterializer.Result> materialised = new ArrayList<>();
            int carriageIndex = 0;
            for (Carriage carriage : new ArrayList<>(train.carriages)) {
                Optional<CarriageMaterializer.Result> result =
                        CarriageMaterializer.materialize(level, carriage, tuning);
                if (result.isEmpty()) {
                    continue;
                }
                CarriageMaterializer.Result copy = result.get();
                materialised.add(copy);
                Pose pose = CarriageMaterializer.initialPose(carriage, train, verdict.speed());
                ConversionRequest request = new ConversionRequest(level, train.id, carriageIndex++,
                        copy.anchor(), copy.positions(), copy.massProperties(), pose, tuning);
                backend.attach(request).ifPresent(body -> {
                    body.applyMassProfile(copy.massProperties());
                    attached.add(body);
                });
            }

            if (attached.isEmpty()) {
                for (CarriageMaterializer.Result copy : materialised) {
                    CarriageMaterializer.rollback(level, copy.positions());
                }
                CreateTrainsSable.LOGGER.warn("No physics body could be created for train {}; "
                        + "Create keeps full control", train.id);
                return;
            }

            if (!alreadyCrashed) {
                // Create's own crash() clears graph, marks the train derailed and updates status.
                train.crash();
            }
            train.speed = 0.0;
            for (Carriage carriage : train.carriages) {
                carriage.forEachPresentEntity(entity -> {
                    entity.ejectPassengers();
                    entity.setInvisible(true);
                });
            }

            PhysicsSession session = new PhysicsSession(level, train.id, snapshot, attached,
                    verdict.trigger(), verdict.speed());
            sessions.add(session);
            monitor.forget(train.id);
            CreateTrainsSable.LOGGER.info("Train {} handed to Rapier: trigger={}, speed={} m/s, bodies={}",
                    train.id, verdict.trigger(), verdict.speed(), attached.size());
        } finally {
            CrashSignals.endConversion(train.id);
        }
    }

    public void onServerStopped() {
        // The hidden contraption entities must be visible again next launch; wrecks are not
        // re-adopted across a restart, and Sable sub-levels are removed here to avoid orphans.
        for (PhysicsSession session : sessions.all()) {
            Train train = Create.RAILWAYS.trains.get(session.trainId());
            if (train != null) {
                for (Carriage carriage : train.carriages) {
                    carriage.forEachPresentEntity(entity -> entity.setInvisible(false));
                }
            }
        }
        sessions.clear(ReleaseReason.SERVER_STOP);
        monitor.clear();
        CrashSignals.clear();
        backend.shutdown();
    }
}
