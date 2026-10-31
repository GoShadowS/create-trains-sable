package dev.goshadows.createtrainssable.physics;

/**
 * Holds a carriage on the rails with forces instead of teleporting it.
 *
 * <p>This is the difference between a train that <em>moves along</em> a spline and a train that is
 * <em>attached to</em> it. Create's train maths still decide where the carriage should be, but the
 * body is never moved: a proportional-derivative controller computes the force and the torque that
 * would take it there, and the physics engine decides what actually happens. A collision, a heavy
 * load in the wagon or a curve taken too fast can therefore win against the rails, which is exactly
 * what a derailment is.</p>
 *
 * <p>The controller output is clamped. The clamp is not a detail: it is the physical limit of the
 * wheel/rail contact, and {@link Output#saturated()} is how the caller learns that the rails are
 * asking for more than they can deliver. A sustained saturation is a derailment.</p>
 */
public final class RailController {

    /**
     * @param force     force in kpg*m/s^2, at the centre of mass, world space
     * @param torque    torque in kpg*m^2/s^2, world space
     * @param error     distance between the body and where the rails want it, in blocks
     * @param angleError orientation error in radians
     * @param saturated true when the clamp was hit, i.e. the rails cannot supply the demand
     */
    public record Output(Point3 force, Point3 torque, double error, double angleError,
                         boolean saturated) {

        public static final Output NONE = new Output(Point3.ZERO, Point3.ZERO, 0.0, 0.0, false);
    }

    private RailController() {
    }

    /**
     * @param current   pose sampled from physics
     * @param target    pose the rails want, including the velocity it should have there
     * @param mass      body mass; the controller works in accelerations, so heavier bodies get
     *                  proportionally more force and behave identically
     * @param inertia   inertia along the three world axes, from {@link MassProperties}
     * @param tuning    live configuration
     */
    public static Output control(Pose current, Pose target, double mass, Point3 inertia,
                                 Tuning tuning) {
        Tuning.Rails rails = tuning.rails();
        if (mass <= 0.0 || !Double.isFinite(mass)) {
            return Output.NONE;
        }
        Point3 positionError = target.position().sub(current.position());
        double error = positionError.length();
        Point3 velocityError = target.linearVelocity().sub(current.linearVelocity());
        Point3 acceleration = positionError.scale(rails.positionGain())
                .add(velocityError.scale(rails.velocityGain()))
                .add(Point3.UP.scale(tuning.traction().gravity()));

        boolean saturated = false;
        double maxAcceleration = rails.maxAcceleration();
        if (acceleration.length() > maxAcceleration) {
            acceleration = acceleration.normalized().scale(maxAcceleration);
            saturated = true;
        }

        Point3 angleError = Quat.rotationError(current.orientation(), target.orientation());
        double angle = angleError.length();
        Point3 angularVelocityError = target.angularVelocity().sub(current.angularVelocity());
        Point3 angularAcceleration = angleError.scale(rails.angularGain())
                .add(angularVelocityError.scale(rails.angularVelocityGain()));

        double maxAngular = rails.maxAngularAcceleration();
        if (angularAcceleration.length() > maxAngular) {
            angularAcceleration = angularAcceleration.normalized().scale(maxAngular);
            saturated = true;
        }

        Point3 force = acceleration.scale(mass);
        Point3 torque = new Point3(
                angularAcceleration.x() * axisInertia(inertia.x(), mass),
                angularAcceleration.y() * axisInertia(inertia.y(), mass),
                angularAcceleration.z() * axisInertia(inertia.z(), mass));

        return new Output(force, torque, error, angle, saturated);
    }

    /** Falls back to a sphere of the body's own size when the tensor has no usable entry. */
    private static double axisInertia(double inertia, double mass) {
        if (Double.isFinite(inertia) && inertia > 1.0e-6) {
            return inertia;
        }
        return Math.max(1.0e-6, mass * 0.25);
    }
}
