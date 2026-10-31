package dev.goshadows.createtrainssable.config;

/**
 * Every number the rail contact uses, in SI units, in one place.
 *
 * <p>The physics works in metres, seconds and newtons: an acceleration is {@code m/s^2}, a speed is
 * {@code m/s} and a stiffness is {@code 1/s^2}. Create reports speed in blocks per tick and shows
 * RPM on a shaft; the conversion happens exactly once, where those values are read.</p>
 */
public record TruckSettings(
        // Traction
        double speedPerRpm,
        double maxSpeed,
        double traction,
        double brake,
        double friction,
        // Guidance
        double guideStiffness,
        double guideDamping,
        double contactStiffness,
        double contactDamping,
        double maxGuideAcceleration,
        // Limits
        double maxLateralError,
        double maxVerticalError,
        double curveLateralLimit,
        // Track search
        double scanRadius,
        int trackRecheckTicks,
        int attachTicks,
        double attachRadius,
        // Couplings
        double couplingRange,
        double couplingStiffness,
        double couplingDamping,
        double couplingMaxAcceleration,
        // World
        double gravity,
        boolean debugLog) {

    public static TruckSettings defaults() {
        return new TruckSettings(
                0.05, 60.0,
                1.2, 1.6, 0.45,
                18.0, 6.0, 220.0, 24.0, 26.0,
                1.2, 0.9, 2.6,
                24.0, 2, 40, 3.0,
                2.5, 6.0, 4.0, 14.0,
                9.81, false);
    }

    /** Clamps anything that would divide by zero, invert a sign or shake the vehicle apart. */
    public TruckSettings validated() {
        return new TruckSettings(
                clamp(speedPerRpm, 0.0, 5.0),
                clamp(maxSpeed, 0.1, 200.0),
                clamp(traction, 0.0, 100.0),
                clamp(brake, 0.0, 100.0),
                clamp(friction, 0.0, 2.0),
                clamp(guideStiffness, 0.0, 500.0),
                clamp(guideDamping, 0.0, 200.0),
                clamp(contactStiffness, 1.0, 2000.0),
                clamp(contactDamping, 0.0, 500.0),
                clamp(maxGuideAcceleration, 0.1, 500.0),
                clamp(maxLateralError, 0.05, 8.0),
                clamp(maxVerticalError, 0.05, 8.0),
                clamp(curveLateralLimit, 0.1, 100.0),
                clamp(scanRadius, 2.0, 128.0),
                (int) clamp(trackRecheckTicks, 1, 40),
                (int) clamp(attachTicks, 0, 400),
                clamp(attachRadius, 0.0, 16.0),
                clamp(couplingRange, 0.5, 16.0),
                clamp(couplingStiffness, 0.0, 200.0),
                clamp(couplingDamping, 0.0, 200.0),
                clamp(couplingMaxAcceleration, 0.0, 200.0),
                clamp(gravity, 0.0, 50.0),
                debugLog);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
