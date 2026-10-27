package dev.goshadows.createtrainssable.physics;

/**
 * Immutable snapshot of the server configuration used by the physics code.
 *
 * <p>Keeping this type free of Minecraft and NeoForge classes makes the derailment model
 * testable without a running game. CTSConfig builds one snapshot per read.</p>
 */
public record Tuning(General general, Traction traction, Derailment derailment,
                     Rebinding rebinding, Debug debug) {

    public enum ConversionMode {
        /** Convert only when a trigger fires. */
        THRESHOLD,
        /** Convert every moving train, useful when tuning thresholds. */
        ALWAYS,
        /** Leave Create's kinematics untouched. */
        DISABLED
    }

    public record General(boolean enabled, ConversionMode mode, int maxActiveTrains, int sessionTimeoutTicks) {
    }

    public record Traction(double blockMassScale, double minimumBlockMass, double wheelRailFriction,
                           double rollingResistance, double brakeForceMultiplier, double slipThreshold,
                           double gravity) {
    }

    public record Derailment(double cantReliefFactor, double flangeCoefficient, double halfGauge,
                             double centreOfGravityHeight, double maxCurveRadius, double overspeedGrace,
                             double impactSpeedThreshold, double hardBrakeDeceleration) {
    }

    public record Rebinding(double maxSpeed, double maxAngularSpeed, double maxTiltDegrees,
                            double maxTrackDistance, int stableTicks) {
    }

    public record Debug(boolean showRapierHitboxes) {
    }

    public static Tuning defaults() {
        return new Tuning(
                new General(true, ConversionMode.THRESHOLD, 10, 12000),
                new Traction(0.5, 0.1, 0.45, 0.02, 1.0, 0.18, 9.81),
                new Derailment(1.8, 0.45, 0.75, 1.1, 64.0, 1.15, 4.0, 18.0),
                new Rebinding(0.25, 0.10, 12.0, 1.5, 40),
                new Debug(false));
    }

    /** Clamps values that would otherwise produce a NaN pose or an immediate re-conversion. */
    public Tuning validated() {
        General g = new General(
                general.enabled(),
                general.mode() == null ? ConversionMode.THRESHOLD : general.mode(),
                (int) clamp(general.maxActiveTrains(), 1, 64),
                (int) clamp(general.sessionTimeoutTicks(), 200, 200000));

        Traction t = new Traction(
                clamp(traction.blockMassScale(), 0.01, 20.0),
                clamp(traction.minimumBlockMass(), 0.001, 10.0),
                clamp(traction.wheelRailFriction(), 0.01, 2.0),
                clamp(traction.rollingResistance(), 0.0, 1.0),
                clamp(traction.brakeForceMultiplier(), 0.0, 5.0),
                clamp(traction.slipThreshold(), 0.01, 1.0),
                clamp(traction.gravity(), 0.1, 50.0));

        Derailment d = new Derailment(
                clamp(derailment.cantReliefFactor(), 0.1, 10.0),
                clamp(derailment.flangeCoefficient(), 0.01, 2.0),
                clamp(derailment.halfGauge(), 0.1, 5.0),
                clamp(derailment.centreOfGravityHeight(), 0.1, 10.0),
                clamp(derailment.maxCurveRadius(), 1.0, 1024.0),
                clamp(derailment.overspeedGrace(), 1.0, 5.0),
                clamp(derailment.impactSpeedThreshold(), 0.0, 100.0),
                clamp(derailment.hardBrakeDeceleration(), 0.0, 200.0));

        Rebinding r = new Rebinding(
                clamp(rebinding.maxSpeed(), 0.0, 10.0),
                clamp(rebinding.maxAngularSpeed(), 0.0, 10.0),
                clamp(rebinding.maxTiltDegrees(), 0.0, 180.0),
                clamp(rebinding.maxTrackDistance(), 0.0, 32.0),
                (int) clamp(rebinding.stableTicks(), 1, 1000));

        return new Tuning(g, t, d, r, new Debug(debug.showRapierHitboxes()));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
