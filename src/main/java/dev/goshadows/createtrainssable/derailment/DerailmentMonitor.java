package dev.goshadows.createtrainssable.derailment;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.goshadows.createtrainssable.physics.DerailmentModel;
import dev.goshadows.createtrainssable.physics.Point3;
import dev.goshadows.createtrainssable.physics.Tuning;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Reads public Train state each tick and decides whether a hand-off is due. */
public final class DerailmentMonitor {

    private final Map<UUID, CurveWindow> windows = new HashMap<>();

    public Optional<DerailmentModel.Verdict> observe(Train train, Tuning tuning, int tick) {
        if (train.graph == null || train.carriages.isEmpty()) {
            windows.remove(train.id);
            return Optional.empty();
        }
        Carriage first = train.carriages.get(0);
        TravellingPoint leading = first.getLeadingPoint();
        if (leading == null || leading.edge == null) {
            return Optional.empty();
        }

        Vec3 position = leading.getPosition(train.graph);
        // Create measures speed in blocks per tick; Sable and Rapier use m/s.
        double speed = Math.abs(train.speed) * 20.0;
        CurveWindow.Observation observation = windows
                .computeIfAbsent(train.id, id -> new CurveWindow())
                .observe(new Point3(position.x, position.y, position.z), speed, tick);

        DerailmentModel.Inputs inputs = new DerailmentModel.Inputs(speed,
                observation.radius(), observation.speedDrop(), observation.deceleration(),
                train.derailed);
        DerailmentModel.Verdict verdict = DerailmentModel.evaluate(inputs, tuning);
        return verdict.derail() ? Optional.of(verdict) : Optional.empty();
    }

    public void forget(UUID trainId) {
        windows.remove(trainId);
    }

    public void clear() {
        windows.clear();
    }
}
