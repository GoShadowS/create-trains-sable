package dev.goshadows.createtrainssable.bogey;

/**
 * Tuning for the physics bogeys and couplers, in the units the physics engine uses.
 *
 * <p>Everything is an acceleration rather than a force on purpose: a heavier vehicle should
 * accelerate exactly like a light one, and an acceleration is what the physics pipeline can apply
 * without a mass lookup.</p>
 */
public record BogeySettings(double speedPerRpm, double tractionAcceleration, double brakeAcceleration,
                            double guideStiffness, double guideDamping, double maxGuideAcceleration,
                            double alignGain, double alignDamping, double maxAngularAcceleration,
                            double maxLateralError, double maxVerticalError, double curveLateralLimit,
                            double scanRadius, int trackRecheckTicks, double couplerRange,
                            double couplerStiffness, double couplerDamping,
                            double couplerMaxAcceleration, double gravity) {

    public static BogeySettings defaults() {
        return new BogeySettings(
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
    public BogeySettings validated() {
        return new BogeySettings(
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
                clamp(couplerRange, 0.5, 16.0),
                clamp(couplerStiffness, 0.0, 200.0),
                clamp(couplerDamping, 0.0, 200.0),
                clamp(couplerMaxAcceleration, 0.0, 200.0),
                clamp(gravity, 0.0, 50.0));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
