package dev.goshadows.createtrainssable.rail;

/**
 * Tuning for the rail trucks and couplings, in the units the physics engine uses.
 *
 * <p>Everything is an acceleration rather than a force on purpose: a heavier vehicle should
 * accelerate exactly like a light one, and an acceleration is what the physics pipeline can apply
 * without a mass lookup.</p>
 */
public record TruckSettings(double speedPerRpm, double tractionAcceleration, double brakeAcceleration,
                            double guideStiffness, double guideDamping, double maxGuideAcceleration,
                            double alignGain, double alignDamping, double maxAngularAcceleration,
                            double maxLateralError, double maxVerticalError, double curveLateralLimit,
                            double scanRadius, int trackRecheckTicks, double couplingRange,
                            double couplingStiffness, double couplingDamping,
                            double couplingMaxAcceleration, double gravity) {

    public static TruckSettings defaults() {
        return new TruckSettings(
                0.05,
                1.2,
                1.6,
                18.0,
                6.0,
                26.0,
                3.0,
                1.5,
                8.0,
                1.2,
                0.9,
                2.6,
                24.0,
                2,
                2.5,
                6.0,
                4.0,
                14.0,
                9.81);
    }

    /** Clamps anything that would divide by zero or shake the vehicle apart. */
    public TruckSettings validated() {
        return new TruckSettings(
                clamp(speedPerRpm, 0.0, 5.0),
                clamp(tractionAcceleration, 0.0, 100.0),
                clamp(brakeAcceleration, 0.0, 100.0),
                clamp(guideStiffness, 0.0, 500.0),
                clamp(guideDamping, 0.0, 200.0),
                clamp(maxGuideAcceleration, 0.1, 500.0),
                clamp(alignGain, 0.0, 100.0),
                clamp(alignDamping, 0.0, 100.0),
                clamp(maxAngularAcceleration, 0.1, 100.0),
                clamp(maxLateralError, 0.05, 8.0),
                clamp(maxVerticalError, 0.05, 8.0),
                clamp(curveLateralLimit, 0.1, 100.0),
                clamp(scanRadius, 2.0, 128.0),
                (int) clamp(trackRecheckTicks, 1, 40),
                clamp(couplingRange, 0.5, 16.0),
                clamp(couplingStiffness, 0.0, 200.0),
                clamp(couplingDamping, 0.0, 200.0),
                clamp(couplingMaxAcceleration, 0.0, 200.0),
                clamp(gravity, 0.0, 50.0));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
