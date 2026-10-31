package dev.goshadows.createtrainssable.physics;

/**
 * Physical coupling between two neighbouring carriages.
 *
 * <p>Create models a train as carriages held together by its own maths. As soon as the carriages are
 * separate rigid bodies, nothing holds them together unless the mod does it: this is the spring and
 * damper that a coupler would be. It is deliberately soft - a coupler absorbs slack, and a stiff
 * spring would explode on a sudden brake - and it is one-directional in the sense that it can only
 * push as hard as the coupler is rated for.</p>
 *
 * <p>Both carriages are pulled towards the distance Create says they should have, so the chain
 * follows the route but the momentum of one carriage is genuinely transferred to the next.</p>
 */
public final class CouplingModel {

    /** Force pair acting on the two ends of one coupler, world space, in kpg*m/s^2. */
    public record Pair(Point3 onFirst, Point3 onSecond, double extension, double force) {

        public static final Pair NONE = new Pair(Point3.ZERO, Point3.ZERO, 0.0, 0.0);
    }

    private CouplingModel() {
    }

    /**
     * @param firstPosition  centre of mass of the leading carriage
     * @param firstVelocity  its velocity
     * @param firstMass      its mass
     * @param secondPosition centre of mass of the trailing carriage
     * @param secondVelocity its velocity
     * @param secondMass     its mass
     * @param restLength     distance Create wants between the two centres
     * @param tuning         live configuration
     * @return forces, already reduced to the coupler's rating
     */
    public static Pair solve(Point3 firstPosition, Point3 firstVelocity, double firstMass,
                             Point3 secondPosition, Point3 secondVelocity, double secondMass,
                             double restLength, Tuning tuning) {
        Tuning.Rails rails = tuning.rails();
        if (firstMass <= 0.0 || secondMass <= 0.0 || !rails.couplingEnabled()) {
            return Pair.NONE;
        }

        Point3 delta = secondPosition.sub(firstPosition);
        double distance = delta.length();
        if (distance < 1.0e-6) {
            return Pair.NONE;
        }
        Point3 axis = delta.scale(1.0 / distance);
        double extension = distance - restLength;
        double closingSpeed = secondVelocity.sub(firstVelocity).dot(axis);

        // Stiffness is expressed as an acceleration per block of extension, so a heavy train is not
        // coupled any more softly than a light one.
        double reducedMass = firstMass * secondMass / (firstMass + secondMass);
        double force = (extension * rails.couplingStiffness()
                + closingSpeed * rails.couplingDamping()) * reducedMass;
        // The coupler is symmetric: it resists both stretching and compression, up to its rating.
        double limit = rails.couplingMaxAcceleration() * reducedMass;
        if (Math.abs(force) > limit) {
            force = Math.copySign(limit, force);
        }

        // A stretched coupler (positive extension) pulls both carriages towards each other.
        Point3 onSecond = axis.scale(-force);
        return new Pair(onSecond.scale(-1.0), onSecond, extension, force);
    }
}
