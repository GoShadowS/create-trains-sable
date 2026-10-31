package dev.goshadows.createtrainssable.physics;

/**
 * Immutable snapshot of the server configuration used by the physics code.
 *
 * <p>Keeping this type free of Minecraft and NeoForge classes makes the derailment model
 * testable without a running game. CTSConfig builds one snapshot per read.</p>
 */
public record Tuning(General general, Traction traction, Derailment derailment,
                     Rails rails, Rebinding rebinding, Debug debug) {

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

    /**
     * How a train is held on its rails.
     *
     * <p>{@code physical} selects the real simulation: the rails apply forces through a
     * proportional-derivative controller and the body is never moved by hand. {@code maxAcceleration}
     * is the grip the wheel/rail contact can deliver; when the demand exceeds it the rails are
     * overloaded, which is what turns a curve taken too fast into a derailment.</p>
     */
    public record Rails(boolean physical, double positionGain, double velocityGain,
                        double maxAcceleration, double angularGain, double angularVelocityGain,
                        double maxAngularAcceleration, double angleTolerance,
                        boolean couplingEnabled, double couplingStiffness, double couplingDamping,
                        double couplingMaxAcceleration, double lateralTolerance, int toleranceTicks) {
    }

    public record Rebinding(double maxSpeed, double maxAngularSpeed, double maxTiltDegrees,
                            double maxTrackDistance, int stableTicks, boolean autoResync) {
    }

    public record Debug(boolean showRapierHitboxes) {
    }

    public static Tuning defaults() {
        return new Tuning(
                new General(true, ConversionMode.THRESHOLD, 10, 12000),
                new Traction(0.5, 0.1, 0.45, 0.02, 1.0, 0.18, 9.81),
                new Derailment(1.8, 0.45, 0.75, 1.1, 64.0, 1.15, 4.0, 18.0),
                new Rails(true, 10.0, 4.0, 24.0, 5.0, 2.0, 12.0, 0.35, true, 8.0, 4.0, 20.0,
                        1.5, 10),
                new Rebinding(0.25, 0.10, 12.0, 1.5, 40, false),
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

        Rails rails_ = new Rails(
                rails.physical(),
                clamp(rails.positionGain(), 0.0, 100.0),
                clamp(rails.velocityGain(), 0.0, 100.0),
                clamp(rails.maxAcceleration(), 0.1, 500.0),
                clamp(rails.angularGain(), 0.0, 100.0),
                clamp(rails.angularVelocityGain(), 0.0, 100.0),
                clamp(rails.maxAngularAcceleration(), 0.1, 500.0),
                clamp(rails.angleTolerance(), 0.0, Math.PI),
                rails.couplingEnabled(),
                clamp(rails.couplingStiffness(), 0.0, 10000.0),
                clamp(rails.couplingDamping(), 0.0, 10000.0),
                clamp(rails.couplingMaxAcceleration(), 0.0, 500.0),
                clamp(rails.lateralTolerance(), 0.0, 16.0),
                (int) clamp(rails.toleranceTicks(), 1, 200));

        Rebinding r = new Rebinding(
                clamp(rebinding.maxSpeed(), 0.0, 10.0),
                clamp(rebinding.maxAngularSpeed(), 0.0, 10.0),
                clamp(rebinding.maxTiltDegrees(), 0.0, 180.0),
                clamp(rebinding.maxTrackDistance(), 0.0, 32.0),
                (int) clamp(rebinding.stableTicks(), 1, 1000),
                rebinding.autoResync());

        return new Tuning(g, t, d, rails_, r, new Debug(debug.showRapierHitboxes()));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
