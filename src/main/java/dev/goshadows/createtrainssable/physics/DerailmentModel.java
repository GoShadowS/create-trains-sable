package dev.goshadows.createtrainssable.physics;

/**
 * Decides when a Create train must leave its TrackGraph and become a rigid body.
 *
 * <p>{@link Trigger#LEFT_TRACK} is not computed here: it is reported by the rail controller after
 * the body has been unable to follow its carriage for long enough, which is what happens when the
 * train hits something that does not move.</p>
 *
 * <p>The curve limit combines two classical criteria: overturning (lateral acceleration
 * exceeds {@code g * halfGauge / centreOfGravityHeight}) and flange climb (lateral force
 * exceeds {@code g * flangeCoefficient}). {@code cantReliefFactor} accounts for the banking
 * Create applies to turns. All distances are blocks, treated as metres.</p>
 */
public final class DerailmentModel {

    public enum Trigger {
        NONE,
        CURVE_OVERSPEED,
        COLLISION,
        HARD_BRAKING,
        CREATE_CRASH,
        /** The body stopped following its carriage: the wheels left the rails under a load. */
        LEFT_TRACK,
        CONFIG_ALWAYS
    }

    /** Speeds in m/s, radius in blocks, deceleration in m/s^2. */
    public record Inputs(double speed, double curveRadius, double impactSpeed, double deceleration,
                         boolean createCrash) {
    }

    public record Verdict(boolean derail, Trigger trigger, double safeSpeed, double lateralAcceleration,
                          double margin, double speed, double curveRadius) {

        public static Verdict none(double safeSpeed, double lateral, double margin,
                                   double speed, double curveRadius) {
            return new Verdict(false, Trigger.NONE, safeSpeed, lateral, margin, speed, curveRadius);
        }
    }

    private DerailmentModel() {
    }

    public static double lateralAcceleration(double speed, double curveRadius) {
        return Double.isFinite(curveRadius) && curveRadius > 0.0 ? speed * speed / curveRadius : 0.0;
    }

    public static double accelerationLimit(Tuning tuning) {
        Tuning.Derailment d = tuning.derailment();
        double gravity = tuning.traction().gravity();
        double overturning = gravity * d.halfGauge() / d.centreOfGravityHeight();
        double wheelClimb = gravity * d.flangeCoefficient();
        return d.cantReliefFactor() * Math.min(overturning, wheelClimb);
    }

    public static double safeSpeed(double curveRadius, Tuning tuning) {
        if (!Double.isFinite(curveRadius) || curveRadius <= 0.0
                || curveRadius > tuning.derailment().maxCurveRadius()) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(accelerationLimit(tuning) * curveRadius);
    }

    public static double kineticEnergy(double mass, double speed) {
        return 0.5 * mass * speed * speed;
    }

    public static Verdict evaluate(Inputs inputs, Tuning tuning) {
        double speed = Math.max(0.0, inputs.speed());
        double radius = inputs.curveRadius();
        double safeSpeed = safeSpeed(radius, tuning);
        double lateral = lateralAcceleration(speed, radius);
        double margin = Double.isFinite(safeSpeed) && safeSpeed > 0.0 ? speed / safeSpeed : 0.0;

        Tuning.ConversionMode mode = tuning.general().mode();
        if (!tuning.general().enabled() || mode == Tuning.ConversionMode.DISABLED) {
            return Verdict.none(safeSpeed, lateral, margin, speed, radius);
        }

        Trigger trigger = Trigger.NONE;
        if (mode == Tuning.ConversionMode.ALWAYS) {
            trigger = Trigger.CONFIG_ALWAYS;
        } else if (inputs.createCrash()) {
            trigger = Trigger.CREATE_CRASH;
        } else if (inputs.impactSpeed() > tuning.derailment().impactSpeedThreshold()) {
            trigger = Trigger.COLLISION;
        } else if (inputs.deceleration() > tuning.derailment().hardBrakeDeceleration()) {
            trigger = Trigger.HARD_BRAKING;
        } else if (Double.isFinite(safeSpeed)
                && speed > safeSpeed * tuning.derailment().overspeedGrace()) {
            trigger = Trigger.CURVE_OVERSPEED;
        }

        if (trigger == Trigger.NONE) {
            return Verdict.none(safeSpeed, lateral, margin, speed, radius);
        }
        return new Verdict(true, trigger, safeSpeed, lateral, margin, speed, radius);
    }
}
