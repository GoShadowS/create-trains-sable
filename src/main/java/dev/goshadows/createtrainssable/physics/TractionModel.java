package dev.goshadows.createtrainssable.physics;

/** Wheel/rail contact model used for the forces applied to a derailed carriage. */
public final class TractionModel {

    /** throttle and brake are 0..1 demands; speed is signed, in m/s. */
    public record Demand(double throttle, double brake, double speed, double mass) {
    }

    /** force is signed along the body's forward axis, in newtons (kpg*m/s^2). */
    public record Result(double force, double slipRatio, boolean slipping) {
    }

    private TractionModel() {
    }

    public static Result evaluate(Demand demand, Tuning tuning) {
        Tuning.Traction t = tuning.traction();
        double normalForce = Math.max(0.0, demand.mass()) * t.gravity();
        double adhesionLimit = t.wheelRailFriction() * normalForce;
        double requested = (clamp01(demand.throttle()) - clamp01(demand.brake()) * t.brakeForceMultiplier())
                * adhesionLimit;
        double slipRatio = Math.max(0.0, Math.abs(requested) / Math.max(adhesionLimit, 1.0e-9) - 1.0);

        // Tyres lose grip progressively past the adhesion limit instead of snapping to zero.
        double transmitted = Math.signum(requested) * Math.min(Math.abs(requested),
                adhesionLimit * (1.0 - 0.5 * Math.min(slipRatio, 1.0)));
        if (demand.brake() > demand.throttle() && Math.abs(demand.speed()) < 0.05) {
            transmitted = 0.0;
        }
        return new Result(transmitted, slipRatio, slipRatio > t.slipThreshold());
    }

    /** Always opposes motion; returns a signed force. */
    public static double rollingResistanceForce(double mass, double speed, Tuning tuning) {
        if (Math.abs(speed) < 1.0e-6) {
            return 0.0;
        }
        Tuning.Traction t = tuning.traction();
        return -Math.signum(speed) * t.rollingResistance() * mass * t.gravity();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
