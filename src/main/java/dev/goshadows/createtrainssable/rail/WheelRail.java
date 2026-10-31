package dev.goshadows.createtrainssable.rail;

import dev.goshadows.createtrainssable.config.TruckSettings;
/**
 * The wheel/rail contact, as pure arithmetic.
 *
 * <p>An axle carries one wheel set. The contact has three jobs and no state of its own:</p>
 *
 * <ol>
 *   <li><b>Hold the axle on the rail.</b> A spring-damper in the plane perpendicular to the rail
 *       pushes the axle back onto the rail head. Its strength is the flange, and it is clamped: the
 *       rail can only push so hard before the flange climbs over it.</li>
 *   <li><b>Pull or brake along the rail.</b> A driven axle tries to reach the speed its shaft asks
 *       for; an idler only rolls. Both are capped by the adhesion of the wheel, which is what makes
 *       a heavy train accelerate slowly instead of magically.</li>
 *   <li><b>Turn the body.</b> The axle steers the structure so its facing follows the tangent of the
 *       rail, which is what a bogey does mechanically.</li>
 * </ol>
 *
 * <p>Everything is returned as an acceleration and a force, never as a velocity or a position: the
 * caller turns it into an impulse, and the physics engine decides what the body actually does. That
 * separation is the whole difference between a train that is simulated and a train that is animated
 * along a spline.</p>
 */
public final class WheelRail {

    /** Why the contact gave up, for logging; {@link #GUIDING} means it is holding. */
    public enum State {
        GUIDING,
        /** No rail within reach. */
        NO_TRACK,
        /** The axle is further from the rail than the flange allows. */
        OFF_RAIL,
        /** The curve cannot hold the vehicle at this speed. */
        OVERSPEED
    }

    /**
     * One substep of one axle.
     *
     * @param force              world force in newtons, to be applied at the axle
     * @param angularAcceleration world angular acceleration in rad/s^2, to be applied to the body
     * @param state              whether the contact is holding the vehicle
     * @param lateralError       measured lateral error in blocks, for logging
     * @param verticalError      measured vertical error in blocks, for logging
     * @param longitudinalError  difference between the commanded and the actual speed, in m/s
     */
    public record Output(Vec force, Vec angularAcceleration, State state, double lateralError,
                         double verticalError, double longitudinalError) {

        public boolean guiding() {
            return state == State.GUIDING;
        }
    }

    private static final Output IDLE =
            new Output(Vec.ZERO, Vec.ZERO, State.NO_TRACK, 0.0, 0.0, 0.0);

    private WheelRail() {
    }

    public static Output idle(State state) {
        return new Output(Vec.ZERO, Vec.ZERO, state, 0.0, 0.0, 0.0);
    }

    /**
     * @param track        the rail under the axle, or {@code null} when there is none
     * @param axle         world position of the axle
     * @param velocity     world velocity of the body at the axle
     * @param forward      world direction the truck faces
     * @param spin         world angular velocity of the body
     * @param targetSpeed  speed the shaft asks for, signed along the rail
     * @param driven       true for a traction truck
     * @param truckCount   trucks on the vehicle, for sharing the weight
     * @param drivenCount  driven trucks on the vehicle, for sharing the demand
     * @param mass         mass of the whole vehicle, in kpg
     * @param gravity      gravity of the dimension the vehicle is in, in m/s^2
     * @param secondsSinceAssembly how long ago the vehicle was assembled, for the attach window
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Output solve(RailFrame track, Vec axle, Vec velocity, Vec forward, Vec spin,
                               double targetSpeed, boolean driven, int truckCount, int drivenCount,
                               double mass, double gravity, double secondsSinceAssembly,
                               TruckSettings settings) {
        if (track == null) {
            return IDLE;
        }
        Vec offset = axle.sub(track.point());
        double lateralError = offset.dot(track.lateral());
        double verticalError = offset.dot(track.up());
        double lateralSpeed = velocity.dot(track.lateral());
        double verticalSpeed = velocity.dot(track.up());
        double along = velocity.dot(track.tangent());

        boolean offRail = Math.abs(lateralError) > settings.maxLateralError()
                || Math.abs(verticalError) > settings.maxVerticalError();
        boolean overspeed = track.isOverspeed(Math.abs(along), settings.curveLateralLimit());
        boolean attaching = secondsSinceAssembly * 20.0 < settings.attachTicks()
                && Math.abs(lateralError) <= settings.attachRadius()
                && Math.abs(verticalError) <= settings.attachRadius();

        if (overspeed) {
            return new Output(Vec.ZERO, Vec.ZERO, State.OVERSPEED, lateralError, verticalError,
                    targetSpeed - along);
        }
        if (offRail && !attaching) {
            return new Output(Vec.ZERO, Vec.ZERO, State.OFF_RAIL, lateralError, verticalError,
                    targetSpeed - along);
        }

        // 1. Hold the axle on the rail, and carry this axle's share of the vehicle's weight.
        double lateralAcceleration = -settings.guideStiffness() * lateralError
                - settings.guideDamping() * lateralSpeed;
        // The engine already pulls the vehicle down; the rail has to carry it, or the vehicle
        // would sink through the track.
        double verticalAcceleration = -settings.guideStiffness() * verticalError
                - settings.guideDamping() * verticalSpeed
                + gravity / Math.max(1, truckCount);

        double guide = Math.hypot(lateralAcceleration, verticalAcceleration);
        if (guide > settings.maxGuideAcceleration()) {
            double scale = settings.maxGuideAcceleration() / guide;
            lateralAcceleration *= scale;
            verticalAcceleration *= scale;
        }
        Vec support = track.lateral().scale(lateralAcceleration)
                .add(track.up().scale(verticalAcceleration));

        // 2. Traction along the rail, capped by what the wheel can actually transmit.
        double demand;
        int share = driven ? Math.max(1, drivenCount) : 1;
        if (driven) {
            demand = (targetSpeed - along) * 4.0 / share;
            demand = clamp(demand, -settings.brake(), settings.traction());
        } else {
            // An idler does not pull: it only takes energy out of the vehicle.
            demand = clamp(-along * 0.8 / Math.max(1, truckCount), -settings.brake(), 0.0);
        }
        double adhesion = settings.friction() * gravity / share;
        demand = clamp(demand, -Math.min(settings.brake(), adhesion),
                Math.min(settings.traction(), adhesion));

        double forceScale = mass / Math.max(1, driven ? drivenCount : truckCount);
        Vec force = support.add(track.tangent().scale(demand)).scale(forceScale);

        // 3. Steer the body along the rail.
        Vec angularAcceleration = alignment(forward, track.tangent(), spin, settings);

        return new Output(force, angularAcceleration, State.GUIDING, lateralError, verticalError,
                targetSpeed - along);
    }

    /** Angular acceleration that turns {@code forward} towards {@code wanted}, with damping. */
    public static Vec alignment(Vec forward, Vec wanted, Vec spin, TruckSettings settings) {
        if (forward.lengthSqr() < 1.0e-8) {
            return Vec.ZERO;
        }
        Vec forwardUnit = forward.normalized();
        double sign = forwardUnit.dot(wanted) >= 0.0 ? 1.0 : -1.0;
        Vec axis = forwardUnit.cross(wanted.scale(sign));
        Vec delta = axis.scale(settings.alignGain()).sub(spin.scale(settings.alignDamping()));
        double limit = settings.maxAngularAcceleration();
        double magnitude = delta.length();
        if (magnitude > limit) {
            delta = delta.scale(limit / magnitude);
        }
        return delta;
    }

    /** Speed a driven truck asks for, in m/s, from the shaft and the way the truck faces. */
    public static double targetSpeed(float rpm, double facingSign, TruckSettings settings) {
        if (Math.abs(rpm) < 1.0e-3) {
            return 0.0;
        }
        return clamp(rpm * settings.speedPerRpm() * facingSign, -settings.maxSpeed(),
                settings.maxSpeed());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
