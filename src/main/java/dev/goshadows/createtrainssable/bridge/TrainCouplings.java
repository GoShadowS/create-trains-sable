package dev.goshadows.createtrainssable.bridge;

import dev.goshadows.createtrainssable.config.TruckSettings;
import dev.goshadows.createtrainssable.rail.Vec;

/**
 * The spring between two carriages of a bridged train.
 *
 * <p>A train is not a rigid body: the carriages are coupled, and the coupling is a spring. This class
 * is that spring, and it is the reason a train of physical vehicles can move at all: only some
 * bodies carry traction trucks, and the rest are pulled by exactly this force.</p>
 *
 * <p>Two things are measured here and nowhere else:</p>
 *
 * <ul>
 *   <li><b>deformation</b> - how far the gap between two carriages is from the gap the train was
 *       given. Positive means stretched, negative means pushed together. This is the number Create's
 *       {@code Train.stress} is supposed to be, and the harness reports both so they can be
 *       compared;</li>
 *   <li><b>force</b> - {@code stiffness * deformation + damping * separation rate}, capped at
 *       {@code maxAcceleration} of the effective mass, applied to both bodies as one impulse and its
 *       opposite. A locomotive is therefore held back by what it pulls, which is the whole difference
 *       between a coupling and two vehicles told to go the same speed.</li>
 * </ul>
 *
 * <p>The arithmetic is separated from the game so it can be tested without one: {@link #acceleration}
 * is pure, and {@link #report} only reads positions and velocities.</p>
 */
public final class TrainCouplings {

    private TrainCouplings() {
    }

    /**
     * An acceleration to give the pair, from the spring's state.
     *
     * @param extension how far the gap is from its rest length, in blocks; positive is stretched
     * @param separating rate at which the gap grows, in m/s; positive is moving apart
     * @return acceleration in m/s^2, positive meaning "pull the two together", capped by the config
     */
    public static double acceleration(double extension, double separating, TruckSettings settings) {
        double wanted = extension * settings.couplingStiffness()
                + separating * settings.couplingDamping();
        double limit = settings.couplingMaxAcceleration();
        return Math.max(-limit, Math.min(limit, wanted));
    }

    /**
     * What the spring is doing between two points, with no force applied; used for reporting.
     *
     * @param front      centre of the leading body
     * @param back       centre of the trailing body
     * @param restLength gap the pair was coupled at, in blocks
     * @param frontSpeed world velocity of the leading body, in m/s
     * @param backSpeed  world velocity of the trailing body, in m/s
     */
    public static Report report(Vec front, Vec back, double restLength, Vec frontSpeed, Vec backSpeed,
                                TruckSettings settings) {
        Vec delta = back.sub(front);
        double distance = delta.length();
        if (distance < 1.0e-4) {
            return new Report(0.0, 0.0, 0.0, 0.0);
        }
        Vec axis = delta.scale(1.0 / distance);
        double extension = distance - restLength;
        double separating = backSpeed.sub(frontSpeed).dot(axis);
        return new Report(distance, extension, separating, acceleration(extension, separating, settings));
    }

    /**
     * @param distance   gap between the two bodies, in blocks
     * @param extension  distance minus rest length, in blocks; positive is stretched
     * @param separating rate the gap grows at, in m/s
     * @param acceleration what the spring asks the pair for, in m/s^2
     */
    public record Report(double distance, double extension, double separating, double acceleration) {

        public boolean stretched() {
            return extension > 0.0;
        }
    }
}
